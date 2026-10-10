// Local integration check. Creates isolated buyers/products/campaigns and removes
// test campaigns from the visible directory when done; order records are retained.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const base=process.env.NOVAMALL_GATEWAY||'http://127.0.0.1:18080';
if(!['127.0.0.1','localhost'].includes(new URL(base).hostname))throw Error('Local environment only');
const parse=t=>JSON.parse(t.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,v=>/^-?\d+$/.test(v)&&!Number.isSafeInteger(Number(v))?`"${v}"`:v));
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function raw(method,p,body,token=''){
 for(let n=0;n<4;n++){
  const r=await fetch(base+'/api'+p,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  if(r.status===429){await pause(700);continue;}
  const j=parse(await r.text());return {status:r.status,...j};
 }throw Error('Rate limit remained active');
}
async function api(method,p,body,token){const r=await raw(method,p,body,token);if(!r.success)throw Error(method+' '+p+' '+r.status+' '+r.message);return r.data;}
async function rejected(method,p,body,token){const r=await raw(method,p,body,token);assert.ok(r.status>=400&&r.status<500,JSON.stringify(r));}
function sql(text){const r=spawnSync('docker',['exec','-i','novamall-mysql','sh','-c','exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -N -B mall'],{input:text,windowsHide:true});if(r.status!==0)throw Error(r.stderr.toString());return r.stdout.toString();}
const suffix=Date.now().toString(),campaignIds=[],checks=[],orderNos=[];
const admin=(await api('POST','/auth/login',{username:process.env.NOVAMALL_ADMIN_USERNAME||'admin',password:process.env.NOVAMALL_ADMIN_PASSWORD||'AdminPass123!'})).accessToken;
const pass=randomUUID();const buyers=[];
for(let i=0;i<2;i++){const username='coupon'+suffix+i;const user=await api('POST','/users/register',{username,password:pass,nickname:'Coupon check'});const login=await api('POST','/auth/login',{username,password:pass});buyers.push({id:user.id,token:login.accessToken});}
const [a,b]=buyers;const bj=ms=>new Date(ms+8*3600000).toISOString().slice(0,19);
async function campaign(extra={}){const now=Date.now();const id=await api('POST','/coupons/admin',{name:'check-'+suffix+'-'+campaignIds.length,kind:'FREE',thresholdAmount:20,discountAmount:5,packPrice:0,packSize:1,totalQuantity:10,opensAt:bj(now-60000),closesAt:bj(now+3600000),expiresAt:bj(now+7200000),...extra},admin);campaignIds.push(id);return id;}
let product;
try{
 await rejected('GET','/coupons/admin',undefined,a.token);checks.push('buyer cannot administer campaigns');
 const future=await campaign({opensAt:bj(Date.now()+1800000)});await rejected('POST',`/coupons/campaigns/${future}/claim`,{},a.token);checks.push('future campaign cannot be claimed');
 const limited=await campaign({totalQuantity:1});
 const race=await Promise.all(buyers.map(buyer=>raw('POST',`/coupons/campaigns/${limited}/claim`,{},buyer.token)));
 assert.equal(race.filter(r=>r.success).length,1);checks.push('concurrent last-unit claim issues once');
 const pack=await campaign({kind:'PACK',packPrice:19.9,packSize:5});
 await rejected('POST',`/coupons/campaigns/${pack}/claim`,{confirmMockPayment:false},a.token);
 const first=await api('POST',`/coupons/campaigns/${pack}/claim`,{confirmMockPayment:true},a.token);
 const retry=await api('POST',`/coupons/campaigns/${pack}/claim`,{confirmMockPayment:true},a.token);
 assert.equal(first.length,5);assert.deepEqual(retry.map(c=>c.id),first.map(c=>c.id));
 assert.equal(Number(sql(`SELECT paid_amount FROM mall_coupon_claim WHERE campaign_id=${pack} AND user_id=${a.id};`)),19.9);
 checks.push('paid pack requires confirmation; retry returns same five coupons and one receipt');
 const category=(await api('GET','/categories')).items[0];
 product=await api('POST','/products',{categoryId:category.id,name:'Coupon check '+suffix,subtitle:'isolated verification',price:25,status:1},admin);
 const sku=(await api('GET',`/products/${product.id}/skus`))[0];
 await api('POST','/inventory',{productId:product.id,skuId:sku.skuId,quantity:20},admin);
 const address=await api('POST','/users/addresses',{receiverName:'验证账号',receiverPhone:'13800000000',province:'浙江省',city:'杭州市',district:'西湖区',detailAddress:'测试地址',isDefault:true},a.token);
 const direct=(couponId,quantity=1)=>({items:[{skuId:sku.skuId,quantity}],addressId:address.id,couponId});
 await rejected('POST',`/reviews/${product.id}`,{rating:5,content:'unverified'},a.token);checks.push('reviews require completed purchase');
 const bAddress=await api('POST','/users/addresses',{receiverName:'B',receiverPhone:'13800000001',province:'浙江省',city:'杭州市',district:'西湖区',detailAddress:'测试地址'},b.token);
 await rejected('POST','/orders',{...direct(first[0].id),addressId:bAddress.id},b.token);checks.push('another buyer cannot use coupon');
 const high=await campaign({thresholdAmount:100,discountAmount:10});const highCoupon=(await api('POST',`/coupons/campaigns/${high}/claim`,{},a.token))[0];
 await rejected('POST','/orders',direct(highCoupon.id),a.token);checks.push('below-threshold coupon rejected');
 sql(`UPDATE mall_user_coupon SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=${first[4].id};`);
 await rejected('POST','/orders',direct(first[4].id),a.token);
 assert.equal((await api('GET','/coupons/mine',undefined,a.token)).find(c=>c.id===first[4].id).display_status,'EXPIRED');checks.push('expired coupon rejected and shown as expired');
 await api('POST','/users/cart/items',{skuId:sku.skuId,quantity:1},a.token);
 const key=randomUUID(),request={requestKey:key,addressId:address.id,couponId:first[0].id};
 const order=await api('POST','/orders/checkout',request,a.token);orderNos.push(order.orderNo);assert.equal(Number(order.totalAmount),20);
 const repeated=await api('POST','/orders/checkout',request,a.token);assert.equal(order.orderNo,repeated.orderNo);
 await rejected('POST','/orders/checkout',{...request,couponId:first[1].id},a.token);
 assert.equal((await api('GET','/coupons/mine',undefined,a.token)).find(c=>c.id===first[0].id).status,'LOCKED');
 await rejected('POST','/orders',direct(first[0].id),a.token);checks.push('checkout deducts 5; retry stable; locked coupon cannot be reused or swapped');
 await api('POST',`/orders/${order.orderNo}/cancel`,undefined,a.token);
 assert.equal((await api('GET','/coupons/mine',undefined,a.token)).find(c=>c.id===first[0].id).status,'AVAILABLE');checks.push('cancel releases coupon');
 const paid=await api('POST','/orders',direct(first[0].id),a.token);orderNos.push(paid.orderNo);
 await api('POST',`/orders/${paid.orderNo}/pay`,undefined,a.token);
 assert.equal((await api('GET','/coupons/mine',undefined,a.token)).find(c=>c.id===first[0].id).status,'USED');
 const detail=await api('GET',`/orders/${paid.orderNo}/detail`,undefined,a.token);
 assert.equal(Number(detail.discountAmount),5);assert.equal(Number(detail.payments[0].amount),20);checks.push('payment consumes coupon and payment record reflects discounted amount');
 await api('POST',`/orders/${paid.orderNo}/ship`,undefined,a.token);await api('POST',`/orders/${paid.orderNo}/complete`,undefined,a.token);
 await api('POST',`/reviews/${product.id}`,{rating:5,content:'优惠券链路验证'},a.token);
 await rejected('POST',`/reviews/${product.id}`,{rating:4,content:'duplicate'},a.token);
 assert.equal((await api('GET',`/reviews/${product.id}`)).length,1);checks.push('completed buyer can review once');
 const timeout=await api('POST','/orders',direct(first[1].id),a.token);orderNos.push(timeout.orderNo);
 sql(`UPDATE mall_order SET created_at=DATE_SUB(NOW(),INTERVAL 35 MINUTE) WHERE order_no='${timeout.orderNo}';`);
 for(let i=0;i<16;i++){await pause(5000);const o=await api('GET',`/orders/${timeout.orderNo}/detail`,undefined,a.token);if(o.status==='CANCELLED')break;if(i===15)throw Error('timeout scheduler did not cancel');}
 assert.equal((await api('GET','/coupons/mine',undefined,a.token)).find(c=>c.id===first[1].id).status,'AVAILABLE');checks.push('payment timeout releases coupon');
 console.log(JSON.stringify({passed:checks.length,checks},null,2));
 fs.writeFileSync('logs/commerce-integration-result.json',JSON.stringify({passed:checks.length,checks,orderNos,productId:product.id,campaignIds},null,2));
}finally{
 if(product)await api('DELETE',`/products/${product.id}`,undefined,admin);
 if(campaignIds.length){assert.ok(campaignIds.every(id=>/^\d+$/.test(id)));const ids=campaignIds.join(',');sql(`DELETE FROM mall_user_coupon WHERE campaign_id IN (${ids}); DELETE FROM mall_coupon_claim WHERE campaign_id IN (${ids}); DELETE FROM mall_coupon_campaign WHERE id IN (${ids});`);}
 console.log('Isolated test campaigns removed; test product soft-deleted.');
}
