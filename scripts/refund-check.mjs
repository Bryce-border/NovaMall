// Local MySQL/RabbitMQ/API integration. Test buyers, orders and refund audit records are retained;
// only this run's products/coupon campaigns are hidden afterwards. Never uses catalog stock.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const base=process.env.NOVAMALL_GATEWAY||'http://127.0.0.1:18080';
if(!['127.0.0.1','localhost'].includes(new URL(base).hostname))throw Error('Local verification only');
const parse=t=>JSON.parse(t.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,v=>/^-?\d+$/.test(v)&&!Number.isSafeInteger(Number(v))?`"${v}"`:v));
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const report={startedAt:new Date().toISOString(),checks:[],concurrency:[],products:[],orders:[],refunds:[]};
fs.mkdirSync('logs',{recursive:true});
function save(){fs.writeFileSync('logs/refund-integration-result.json',JSON.stringify(report,null,2));}
function pass(name){report.checks.push(name);save();console.log('PASS '+name);}
function sql(text){const r=spawnSync('docker',['exec','-i','novamall-mysql','sh','-c','exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -N -B mall'],{input:text,windowsHide:true,maxBuffer:10*1024*1024});if(r.status!==0)throw Error(r.stderr.toString());return r.stdout.toString().trim();}
function id(value){assert.match(String(value),/^\d+$/);return String(value);}
function refundId(value){assert.match(value,/^RF\d+$/);assert.ok(report.refunds.includes(value));return value;}
async function raw(method,path,body,actor,internal=false){
 for(let i=0;i<12;i++){
  const headers={'Content-Type':'application/json',...(internal?{'X-User-Id':String(actor.id),'X-User-Name':actor.username,'X-User-Role':actor.role||'USER'}:actor?.token?{Authorization:'Bearer '+actor.token}:{})};
  const response=await fetch((internal?'http://127.0.0.1:8085':base)+'/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(25000)});
  if(response.status===429){await pause(500+i*50);continue;}
  const text=await response.text();let value;try{value=parse(text);}catch{throw Error(`${method} ${path}: ${response.status} ${text.slice(0,150)}`);}
  return {...value,httpStatus:response.status};
 }throw Error('Gateway rate limit remained active');
}
async function api(method,path,body,actor,internal=false){const r=await raw(method,path,body,actor,internal);if(!r.success)throw Error(`${method} ${path}: ${r.httpStatus} ${r.message}`);return r.data;}
async function bad(method,path,body,actor,internal=false){const r=await raw(method,path,body,actor,internal);assert.ok(r.httpStatus>=400&&r.httpStatus<500,JSON.stringify(r));return r;}
async function until(fn,label,timeout=70000){const end=Date.now()+timeout;while(Date.now()<end){const value=await fn();if(value)return value;await pause(700);}throw Error('Timeout: '+label);}
const suffix=Date.now().toString(),password=randomUUID(),buyers=[],products=[],campaigns=[];
let admin;
async function order(buyer,quantity=1,product=products[0],couponId){
 const result=await api('POST','/orders',{items:[{skuId:product.skuId,quantity}],couponId},buyer,true);
 report.orders.push(result.orderNo);save();await api('POST',`/orders/${result.orderNo}/pay`,undefined,buyer,true);return result;
}
async function draft(buyer,o,quantity,reason='联调退款'){
 const eligible=await api('GET',`/refunds/orders/${o.orderNo}/eligibility`,undefined,buyer,true);
 assert.equal(eligible.blockedReason,'');
 const items=[{orderItemId:eligible.items[0].order_item_id,quantity}];
 const quote=await api('POST',`/refunds/orders/${o.orderNo}/quote`,{items},buyer,true);
 return {requestKey:randomUUID(),reason,expectedAmount:quote.amount,expectedMode:quote.mode,items};
}
async function apply(buyer,o,body){const r=await api('POST',`/refunds/orders/${o.orderNo}`,body,buyer,true);if(!report.refunds.includes(r.refund_no))report.refunds.push(r.refund_no);save();return r;}
const review=(no,approve=true)=>api('POST',`/refunds/admin/${no}/review`,{approve,note:'联调审核',receiverName:'测试退货仓',receiverPhone:'13800000000',returnAddress:'模拟地址，请勿实际寄件'},admin,true);
const get=(buyer,no)=>api('GET',`/refunds/${no}`,undefined,buyer,true);
const success=(buyer,no)=>until(async()=>{const r=await get(buyer,no);return r.status==='SUCCESS'?r:false;},'refund '+no);
async function ship(buyer,o){return api('POST',`/orders/${o.orderNo}/ship`,undefined,buyer,true);}
async function complete(buyer,o){return api('POST',`/orders/${o.orderNo}/complete`,undefined,buyer,true);}
async function logistics(buyer,no,extra={}){const r=await get(buyer,no);return api('POST',`/refunds/${no}/return-logistics`,{carrier:'模拟顺丰',trackingNo:'SF'+suffix,version:r.returnInfo.version,...extra},buyer,true);}
const inspect=(no,body)=>api('POST',`/refunds/admin/${no}/inspect`,body,admin,true);
function inspection(r,accepted=true,quantity=0){return {accepted,note:accepted?'全部收齐，按可再售数量补库':'数量待核实',version:r.returnInfo.version,restockItems:accepted?r.items.map(i=>({orderItemId:i.order_item_id,quantity})):[]};}
async function concurrent(label,requests){const started=performance.now();const results=await Promise.all(requests.map(fn=>fn()));report.concurrency.push({label,requests:requests.length,elapsedMs:Math.round(performance.now()-started)});save();return results;}
async function publish(key,payload){const r=await fetch('http://127.0.0.1:15672/api/exchanges/%2F/mall.events/publish',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Basic '+Buffer.from((process.env.RABBITMQ_USERNAME||'novamall')+':'+(process.env.RABBITMQ_PASSWORD||'novamall')).toString('base64')},body:JSON.stringify({properties:{delivery_mode:2},routing_key:key,payload,payload_encoding:'string'})});assert.ok(r.ok);assert.equal((await r.json()).routed,true);}
try{
 const login=await api('POST','/auth/login',{username:process.env.NOVAMALL_ADMIN_USERNAME||'admin',password:process.env.NOVAMALL_ADMIN_PASSWORD||'AdminPass123!'});
 admin={token:login.accessToken,id:sql("SELECT id FROM mall_user WHERE username='admin'"),username:'admin',role:'ADMIN'};
 for(let i=0;i<12;i++){
  const username='rf'+suffix+'_'+i;
  const user=await api('POST','/users/register',{username,password,nickname:'退款联调账号'});
  const session=await api('POST','/auth/login',{username,password});buyers.push({id:user.id,username,token:session.accessToken});
 }
 const [a,b]=buyers;
 const category=(await api('GET','/categories')).items[0];
 for(const price of [10,0]){
  const p=await api('POST','/products',{categoryId:category.id,name:'退款联调 '+suffix+' '+price,subtitle:'自动化测试隔离商品',price,status:1},admin);
  const sku=(await api('GET',`/products/${p.id}/skus`))[0];
  await api('POST','/inventory',{productId:p.id,skuId:sku.skuId,quantity:1000},admin);
  products.push({...p,skuId:sku.skuId});report.products.push(p.id);save();
 }
 await bad('GET','/refunds/admin',undefined,a);pass('网关阻止买家访问商家退款接口');

 const o=await order(a,3),body=await draft(a,o,1),r=await apply(a,o,body);
 const duplicate=await apply(a,o,body);assert.equal(r.refund_no,duplicate.refund_no);
 await bad('POST',`/refunds/orders/${o.orderNo}`,{...body,reason:'变更内容'},a,true);
 await bad('POST',`/refunds/orders/${o.orderNo}`,{...body,requestKey:randomUUID(),expectedAmount:'100'},a,true);
 await bad('POST',`/refunds/orders/${o.orderNo}/quote`,{items:[{orderItemId:body.items[0].orderItemId,quantity:4}]},a,true);
 await bad('POST',`/refunds/orders/${o.orderNo}/quote`,{items:[body.items[0],body.items[0]]},a,true);
 await bad('GET',`/refunds/${r.refund_no}`,undefined,b);
 await bad('GET',`/refunds/orders/${o.orderNo}/eligibility`,undefined,b);
 await bad('POST',`/orders/${o.orderNo}/ship`,undefined,a,true);
 const original=await api('GET',`/refunds/admin/${r.refund_no}/order`,undefined,admin);assert.equal(original.orderNo,o.orderNo);
 pass('退款幂等、内容冲突、金额与数量校验、原订单追溯及账号隔离');
 await api('POST',`/refunds/${r.refund_no}/withdraw`,undefined,a,true);
 const rejected=await apply(a,o,await draft(a,o,1));await review(rejected.refund_no,false);
 const final=await apply(a,o,await draft(a,o,3));await review(final.refund_no);await success(a,final.refund_no);
 await bad('POST',`/orders/${o.orderNo}/ship`,undefined,a,true);
 pass('撤回和拒绝释放额度，全部退款后禁止发货');

 const many=[];for(const buyer of buyers){const o=await order(buyer,2);many.push({buyer,o,body:await draft(buyer,o,2)});}
 const mass=await concurrent('12 位买家经网关并发提交，每人重复 2 次',many.flatMap(t=>[0,1].map(()=>()=>raw('POST',`/refunds/orders/${t.o.orderNo}`,t.body,t.buyer))));
 mass.forEach(r=>assert.ok(r.success,JSON.stringify(r)));const numbers=[...new Set(mass.map(r=>r.data.refund_no))];assert.equal(numbers.length,12);report.refunds.push(...numbers);
 await concurrent('24 个并发审核请求，重复审批同一批退款',numbers.flatMap(no=>[0,1].map(()=>()=>review(no))));
 await Promise.all(many.map(t=>success(t.buyer,mass.find(r=>r.data.order_no===t.o.orderNo).data.refund_no)));
 assert.equal(Number(sql(`SELECT COUNT(*) FROM mall_mock_refund_receipt WHERE refund_no IN (${numbers.map(n=>"'"+refundId(n)+"'").join(',')})`)),12);
 pass('12 人同时退款与重复审核：12 张退款单、12 张渠道成功回执');

 const last=await order(a),lastBody=await draft(a,last,1);
 const race=await concurrent('同一订单 20 个请求争抢最后 1 件可退额度（订单服务）',Array.from({length:20},()=>()=>raw('POST',`/refunds/orders/${last.orderNo}`,{...lastBody,requestKey:randomUUID()},a,true)));
 assert.equal(race.filter(x=>x.success).length,1);race.filter(x=>!x.success).forEach(x=>assert.equal(x.httpStatus,400));
 const winner=race.find(x=>x.success).data;report.refunds.push(winner.refund_no);
 const reviews=await concurrent('同单同意与拒绝竞争',[
  ()=>raw('POST',`/refunds/admin/${winner.refund_no}/review`,{approve:true,note:'同意'},admin,true),
  ()=>raw('POST',`/refunds/admin/${winner.refund_no}/review`,{approve:false,note:'拒绝'},admin,true)]);
 assert.equal(reviews.filter(x=>x.success).length,1);
 if((await get(a,winner.refund_no)).status==='REJECTED'){const again=await apply(a,last,await draft(a,last,1));await review(again.refund_no);await success(a,again.refund_no);}else await success(a,winner.refund_no);
 pass('同单争抢不会超退，冲突审核只允许一个结论');

 for(let i=0;i<4;i++){
  const o=await order(a),body=await draft(a,o,1);
  const results=await concurrent('退款申请与发货竞争 '+i,[()=>raw('POST',`/refunds/orders/${o.orderNo}`,body,a,true),()=>raw('POST',`/orders/${o.orderNo}/ship`,undefined,a,true)]);
  assert.equal(results.filter(x=>x.success).length,1);
  if(results[0].success){const no=results[0].data.refund_no;report.refunds.push(no);await api('POST',`/refunds/${no}/withdraw`,undefined,a,true);}
 }
 pass('发货与未发货退款互斥，旧报价不能悄悄变成退货申请');

 const raceOrder=await order(b),raceRefund=await apply(b,raceOrder,await draft(b,raceOrder,1));
 const withdrawal=await concurrent('撤回与审核竞争',[()=>raw('POST',`/refunds/${raceRefund.refund_no}/withdraw`,undefined,b,true),()=>raw('POST',`/refunds/admin/${raceRefund.refund_no}/review`,{approve:true,note:'同意'},admin,true)]);
 assert.equal(withdrawal.filter(x=>x.success).length,1);pass('买家撤回与商家审核竞争保持状态一致');

 // Real coupon discount: 30 - 1 = 29; the final two units receive the rounding remainder.
 const bj=ms=>new Date(ms+8*3600000).toISOString().slice(0,19),now=Date.now();
 const campaign=await api('POST','/coupons/admin',{name:'退款券联调 '+suffix,kind:'FREE',thresholdAmount:20,discountAmount:1,packPrice:0,packSize:1,totalQuantity:3,opensAt:bj(now-60000),closesAt:bj(now+3600000),expiresAt:bj(now+7200000)},admin);campaigns.push(campaign);
 const coupon=(await api('POST',`/coupons/campaigns/${campaign}/claim`,{},a))[0];
 const discounted=await order(a,3,products[0],coupon.id),partBody=await draft(a,discounted,1);assert.equal(partBody.expectedAmount,'9.66');
 const part=await apply(a,discounted,partBody);await review(part.refund_no);await success(a,part.refund_no);
 assert.equal(sql(`SELECT status FROM mall_user_coupon WHERE id=${id(coupon.id)}`),'USED');
 const restBody=await draft(a,discounted,2);assert.equal(restBody.expectedAmount,'19.34');
 const rest=await apply(a,discounted,restBody);await review(rest.refund_no);await success(a,rest.refund_no);
 assert.equal(sql(`SELECT status FROM mall_user_coupon WHERE id=${id(coupon.id)}`),'AVAILABLE');
 pass('优惠分摊尾差：9.66 + 19.34 = 29；部分退款不返券，全退返券');
 const expiringCoupon=(await api('POST',`/coupons/campaigns/${campaign}/claim`,{},b))[0];
 const expiryOrder=await order(b,2,products[0],expiringCoupon.id);
 sql(`UPDATE mall_user_coupon SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=${id(expiringCoupon.id)}`);
 const expiryRefund=await apply(b,expiryOrder,await draft(b,expiryOrder,2));await review(expiryRefund.refund_no);await success(b,expiryRefund.refund_no);
 assert.equal((await api('GET','/coupons/mine',undefined,b)).find(c=>String(c.id)===String(expiringCoupon.id)).display_status,'EXPIRED');
 pass('全部退款退回过期券，仍按已过期处理而不延长有效期');
 const zero=await order(a,1,products[1]),zeroRefund=await apply(a,zero,await draft(a,zero,1));await review(zeroRefund.refund_no);assert.equal((await success(a,zeroRefund.refund_no)).amount,'0.00');
 pass('零元商品按数量退款，不丢失库存返还');

 const returnedOrder=await order(a,3);await ship(a,returnedOrder);await complete(a,returnedOrder);
 const returned=await apply(a,returnedOrder,await draft(a,returnedOrder,2));assert.equal(returned.mode,'RETURN_REFUND');
 await bad('POST',`/refunds/admin/${returned.refund_no}/review`,{approve:true,note:'缺少退货地址'},admin,true);
 const approved=await review(returned.refund_no);assert.equal(approved.status,'WAITING_RETURN');
 await bad('POST',`/refunds/admin/${returned.refund_no}/inspect`,inspection(approved),admin,true);
 await bad('POST',`/refunds/${returned.refund_no}/return-logistics`,{carrier:'模拟',trackingNo:'X',version:approved.returnInfo.version},a,true);
 await bad('POST',`/refunds/${returned.refund_no}/return-logistics`,{carrier:'模拟',trackingNo:'SF123456',version:approved.returnInfo.version},b,true);
 const sent=await logistics(a,returned.refund_no);
 const waitingVersion=sent.returnInfo.version;
 assert.equal((await api('POST',`/refunds/${returned.refund_no}/return-logistics`,{carrier:'模拟顺丰',trackingNo:'SF'+suffix,version:approved.returnInfo.version},a,true)).status,'WAITING_INSPECTION');
 await bad('POST',`/refunds/${returned.refund_no}/withdraw`,undefined,a,true);
 await bad('POST',`/refunds/admin/${returned.refund_no}/inspect`,inspection(sent,true,3),admin,true);
 const failed=await inspect(returned.refund_no,inspection(sent,false));assert.equal(failed.status,'INSPECTION_REJECTED');
 assert.equal(Number(sql(`SELECT COUNT(*) FROM mall_mock_refund_receipt WHERE refund_no='${refundId(returned.refund_no)}'`)),0);
 const reconsidered=await api('POST',`/refunds/${returned.refund_no}/recheck`,{note:'补充说明，请复核',version:failed.returnInfo.version},a,true);
 const accepted=inspection(reconsidered,true,1);
 await concurrent('同一退货 12 个重复验收请求',Array.from({length:12},()=>()=>inspect(returned.refund_no,accepted)));
 await success(a,returned.refund_no);
 assert.equal(sql(`SELECT COUNT(*) FROM mall_refund_history WHERE refund_no='${refundId(returned.refund_no)}' AND actor_type='ADMIN' AND status='QUEUED'`),'1');
 await until(()=>Number(sql(`SELECT COALESCE(SUM(quantity),0) FROM mall_inventory_refund WHERE refund_no='${refundId(returned.refund_no)}'`))===1,'partial restock');
 assert.equal(sql(`SELECT restock_quantity FROM mall_refund_return_item WHERE refund_no='${refundId(returned.refund_no)}'`),'1');
 pass('退货地址、物流校验、验收拒绝与复核、12 次重复验收及逐项部分补库');

 const finalReturn=await apply(a,returnedOrder,await draft(a,returnedOrder,1));await review(finalReturn.refund_no);
 const finalSent=await logistics(a,finalReturn.refund_no);await inspect(finalReturn.refund_no,inspection(finalSent,true,0));await success(a,finalReturn.refund_no);
 assert.equal(Number(sql(`SELECT COUNT(*) FROM mall_inventory_refund WHERE refund_no='${refundId(finalReturn.refund_no)}'`)),0);
 const paidDetail=await api('GET',`/orders/${returnedOrder.orderNo}/detail`,undefined,a,true);assert.equal(paidDetail.refund.fullyRefunded,true);assert.equal(paidDetail.payments[0].amount,'30.00');
 await bad('POST',`/reviews/${products[0].id}`,{rating:5,content:'已全部退货'},a);
 pass('验收可不补库，全退保留原支付快照且不能评价已退商品');

 const modifyOrder=await order(b,2);await ship(b,modifyOrder);
 const modify=await apply(b,modifyOrder,await draft(b,modifyOrder,1));await review(modify.refund_no);const ms=await logistics(b,modify.refund_no);
 await bad('POST',`/orders/${modifyOrder.orderNo}/complete`,undefined,b,true);
 const correctionRace=await concurrent('物流更正与商家验收竞争',[
  ()=>raw('POST',`/refunds/${modify.refund_no}/return-logistics`,{carrier:'模拟中通',trackingNo:'ZT'+suffix,version:ms.returnInfo.version},b,true),
  ()=>raw('POST',`/refunds/admin/${modify.refund_no}/inspect`,inspection(ms,true,1),admin,true)]);
 assert.equal(correctionRace.filter(x=>x.success).length,1);
 const latest=await get(b,modify.refund_no);if(latest.status==='WAITING_INSPECTION')await inspect(modify.refund_no,inspection(latest,true,1));await success(b,modify.refund_no);
 await complete(b,modifyOrder);pass('退货中不能确认收货，物流更新与验收版本互斥，部分退货后可收货剩余商品');

 const sendOrder=await order(b);await ship(b,sendOrder);const sendRefund=await apply(b,sendOrder,await draft(b,sendOrder,1));const sendReview=await review(sendRefund.refund_no);
 const sending=await concurrent('寄回物流与撤回申请竞争',[
  ()=>raw('POST',`/refunds/${sendRefund.refund_no}/return-logistics`,{carrier:'模拟',trackingNo:'RETURN'+suffix,version:sendReview.returnInfo.version},b,true),
  ()=>raw('POST',`/refunds/${sendRefund.refund_no}/withdraw`,undefined,b,true)]);
 assert.equal(sending.filter(x=>x.success).length,1);
 if(sending[0].success){const sent=await get(b,sendRefund.refund_no);const decisions=await concurrent('验收同意与拒绝竞争',[
   ()=>raw('POST',`/refunds/admin/${sendRefund.refund_no}/inspect`,inspection(sent,true,1),admin,true),
   ()=>raw('POST',`/refunds/admin/${sendRefund.refund_no}/inspect`,inspection(sent,false),admin,true)]);
   assert.equal(decisions.filter(x=>x.success).length,1);
 }
 pass('寄回后不能被并发撤回，验收冲突不会覆盖先提交的结果');

 const expiredOrder=await order(b);await ship(b,expiredOrder);await complete(b,expiredOrder);
 sql(`UPDATE mall_order SET completed_at=DATE_SUB(NOW(),INTERVAL 8 DAY) WHERE order_no='${id(expiredOrder.orderNo)}'`);
 const expired=await api('GET',`/refunds/orders/${expiredOrder.orderNo}/eligibility`,undefined,b,true);assert.match(expired.blockedReason,/7 天/);pass('确认收货后超过 7 天不能新申请退货');

 // Seed only this run's provider receipt to model a committed remote refund with a lost response.
 const unknownOrder=await order(b),unknown=await apply(b,unknownOrder,await draft(b,unknownOrder,1));
 sql(`INSERT INTO mall_mock_refund_receipt(refund_no,payment_no,amount,channel_refund_no,created_at) VALUES('${refundId(unknown.refund_no)}','${unknown.payment_no}',9.99,'FAULT-${unknown.refund_no}',NOW(3))`);
 await review(unknown.refund_no);await until(async()=> (await get(b,unknown.refund_no)).status==='UNKNOWN','mismatched channel receipt');
 await bad('POST',`/refunds/admin/${unknown.refund_no}/retry`,{},admin,true);await bad('POST',`/orders/${unknownOrder.orderNo}/ship`,undefined,b,true);
 sql(`UPDATE mall_mock_refund_receipt SET amount=10.00 WHERE refund_no='${refundId(unknown.refund_no)}'; UPDATE mall_refund SET next_attempt_at=NOW(3) WHERE refund_no='${refundId(unknown.refund_no)}'`);
 await success(b,unknown.refund_no);assert.equal(sql(`SELECT COUNT(*) FROM mall_mock_refund_receipt WHERE refund_no='${refundId(unknown.refund_no)}'`),'1');
 pass('渠道金额不符保持结果待确认并冻结额度，核对成功后只完成一次');

 const recoveryOrder=await order(b),recovery=await apply(b,recoveryOrder,await draft(b,recoveryOrder,1));
 sql(`START TRANSACTION; SELECT id FROM mall_order WHERE order_no='${id(recoveryOrder.orderNo)}' FOR UPDATE; UPDATE mall_refund SET status='PROCESSING',attempts=1,lease_token='abandoned-test',lease_until=DATE_SUB(NOW(3),INTERVAL 1 SECOND),next_attempt_at=NOW(3) WHERE refund_no='${refundId(recovery.refund_no)}'; INSERT INTO mall_mock_refund_receipt(refund_no,payment_no,amount,channel_refund_no,created_at) VALUES('${recovery.refund_no}','${recovery.payment_no}',10.00,'RECOVER-${recovery.refund_no}',NOW(3)); COMMIT;`);
 await success(b,recovery.refund_no);assert.equal(sql(`SELECT COUNT(*) FROM mall_mock_refund_receipt WHERE refund_no='${refundId(recovery.refund_no)}'`),'1');
 pass('模拟进程中断：租约过期后查回已成功渠道回执，不重复退款');

 const paging=await api('GET','/refunds?size=3&page=1',undefined,a);assert.equal(paging.items.length,3);assert.ok(paging.hasNext);
 const page2=await api('GET','/refunds?size=3&page=2',undefined,a);assert.ok(page2.items.every(r=>!paging.items.some(p=>p.refund_no===r.refund_no)));
 const filter=await api('GET',`/refunds/admin?buyer=${a.username}&orderNo=${returnedOrder.orderNo}`,undefined,admin);assert.equal(filter.items.length,2);
 pass('多订单分页不重复，商家按买家及原订单精确筛选');

 // Redeliver original stock/sales events including an older sales version after the latest one.
 await until(()=>sql(`SELECT COUNT(*) FROM mall_order_outbox WHERE aggregate_id IN ('${refundId(returned.refund_no)}','${id(returnedOrder.orderNo)}') AND status<>'PUBLISHED'`)==='0','outbox publish');
 const stockPayload=sql(`SELECT payload FROM mall_order_outbox WHERE aggregate_id='${returned.refund_no}' AND event_type='REFUND_SUCCEEDED' LIMIT 1`);
 const salesPayloads=sql(`SELECT payload FROM mall_order_outbox WHERE aggregate_id='${returnedOrder.orderNo}' AND event_type='ORDER_SALES_UPDATED' ORDER BY id DESC`).split('\n');
 for(let i=0;i<3;i++)await publish('refund.succeeded',stockPayload);
 for(const payload of [...salesPayloads,...salesPayloads])await publish('order.sales-updated',payload);
 await pause(3000);
 assert.equal(sql(`SELECT SUM(quantity) FROM mall_inventory_refund WHERE refund_no='${returned.refund_no}'`),'1');
 assert.equal(sql(`SELECT quantity FROM mall_product_sales_line WHERE order_no='${returnedOrder.orderNo}' AND product_id=${id(products[0].id)}`),'0');
 pass('库存重复消息只补一次，销量新旧事件乱序重放仍为正确净销量');

 await until(()=>sql(`SELECT COUNT(*) FROM mall_order_outbox WHERE aggregate_id IN (${report.orders.map(n=>"'"+id(n)+"'").concat(report.refunds.map(n=>"'"+refundId(n)+"'")).join(',')}) AND status<>'PUBLISHED'`)==='0','all fixture events');
 await pause(1500);
 for(const p of products){
  const sku=id(p.skuId),product=id(p.id);
  const expected=Number(sql(`SELECT 1000-COALESCE(SUM(quantity),0) FROM mall_inventory_reservation WHERE sku_id=${sku} AND status='CONFIRMED'`))+Number(sql(`SELECT COALESCE(SUM(f.quantity),0) FROM mall_inventory_refund f JOIN mall_inventory_reservation r ON r.id=f.reservation_id WHERE r.sku_id=${sku}`));
  const stock=await api('GET',`/inventory/skus/${sku}`);assert.equal(stock.totalStock,expected);assert.equal(stock.availableStock+stock.lockedStock,expected);
  const sales=Number(sql(`SELECT COALESCE(SUM(i.quantity-COALESCE(r.qty,0)),0) FROM mall_order_item i JOIN mall_order o ON o.order_no=i.order_no LEFT JOIN (SELECT ri.order_item_id,SUM(ri.quantity) qty FROM mall_refund_item ri JOIN mall_refund r ON r.refund_no=ri.refund_no WHERE r.status='SUCCESS' GROUP BY ri.order_item_id) r ON r.order_item_id=i.id WHERE o.status='COMPLETED' AND i.product_id=${product}`));
  const counts=await api('GET',`/products/sales-counts?productIds=${product}`);assert.equal(Number(counts[0].soldQuantity),sales);
 }
 const nos=report.orders.map(n=>"'"+id(n)+"'").join(',');
 assert.equal(sql(`SELECT COUNT(*) FROM mall_order o JOIN (SELECT order_no,SUM(amount) amount FROM mall_refund WHERE status NOT IN ('REJECTED','WITHDRAWN') GROUP BY order_no) r ON r.order_no=o.order_no WHERE o.order_no IN (${nos}) AND r.amount>o.total_amount`),'0');
 assert.equal(sql(`SELECT COUNT(*) FROM mall_order_item i JOIN (SELECT ri.order_item_id,SUM(ri.quantity) qty FROM mall_refund_item ri JOIN mall_refund r ON r.refund_no=ri.refund_no WHERE r.status NOT IN ('REJECTED','WITHDRAWN') GROUP BY ri.order_item_id) r ON r.order_item_id=i.id WHERE i.order_no IN (${nos}) AND r.qty>i.quantity`),'0');
 pass('最终库存台账、原预占数量、退款金额与净销量一致');
 report.status='PASSED';
}catch(error){report.status='FAILED';report.error=error.stack;console.error(error);process.exitCode=1;}
finally{
 for(const p of products)try{await api('DELETE',`/products/${p.id}`,undefined,admin);}catch(e){report.cleanupError=e.message;process.exitCode=1;}
 for(const c of campaigns)try{await api('PUT',`/coupons/admin/${c}/status?status=0`,undefined,admin);}catch(e){report.cleanupError=e.message;process.exitCode=1;}
 report.finishedAt=new Date().toISOString();save();console.log(JSON.stringify({status:report.status,checks:report.checks.length,concurrency:report.concurrency.length,report:'logs/refund-integration-result.json'}));
}
