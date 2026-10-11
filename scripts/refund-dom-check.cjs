// Executes the real storefront scripts in jsdom. APIs are fixtures; real concurrency is covered by refund-check.mjs.
const fs=require('node:fs'),assert=require('node:assert/strict');
const {JSDOM,VirtualConsole}=require('../logs/ui-tools/node_modules/jsdom');
const html=fs.readFileSync('mall-gateway/src/main/resources/static/shop/index.html','utf8');
const clone=x=>JSON.parse(JSON.stringify(x));
const checks=[];
const original={id:'1001',orderNo:'9001',status:'SHIPPED',createdAt:'2026-10-11T10:00:00',items:[{productId:'500',skuId:'501',productName:'测试手机',skuName:'银色',quantity:2,unitPrice:'10.00',amount:'20.00',refundedQuantity:0,fulfillmentQuantity:2}],productAmount:'20.00',discountAmount:'0.00',freightAmount:'0.00',totalAmount:'20.00',refund:{refundedAmount:'0.00',pendingAmount:'0.00',pendingCount:0,count:0,fullyRefunded:false},actions:[],history:[],payments:[],shipments:[]};
const seed=()=>({refund_no:'RF100',order_no:'9001',user_name:'buyer',payment_no:'MOCK9001',amount:'10.00',status:'PENDING_REVIEW',reason:'需要退货',mode:'RETURN_REFUND',created_at:'2026-10-11T10:00:00',items:[{order_item_id:'2001',product_name:'测试手机',sku_name:'银色',quantity:1,amount:'10.00'}],history:[],returnInfo:{version:0},restockItems:[]});
async function page(admin,hash,refund=seed()) {
 const errors=[],calls=[],vc=new VirtualConsole();vc.on('jsdomError',e=>errors.push(e.message));
 const dom=new JSDOM(html,{url:'http://localhost/shop/index.html'+hash,runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
 const w=dom.window;
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
 w.HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new w.Event('close'));};
 w.scrollTo=()=>{};w.IntersectionObserver=class{observe(){}disconnect(){}};
 w.sessionStorage.setItem('nova.token','x.'+Buffer.from(JSON.stringify({role:admin?'ADMIN':'USER'})).toString('base64url')+'.x');w.sessionStorage.setItem('nova.username',admin?'admin':'buyer');
 w.fetch=async(url,options={})=>{
  const u=new URL(url,'http://localhost'),method=options.method||'GET',body=options.body?JSON.parse(options.body):undefined;
  calls.push({path:u.pathname,method,body});let data;
  if(u.pathname==='/api/categories')data={items:[]};
  else if(u.pathname==='/api/users/cart')data=[];
  else if(u.pathname==='/api/orders/9001/detail'||u.pathname==='/api/refunds/admin/RF100/order')data=original;
  else if(u.pathname==='/api/refunds/orders/9001/eligibility')data={blockedReason:'',mode:'RETURN_REFUND',items:[{order_item_id:'2001',product_name:'测试手机',sku_name:'银色',remaining_quantity:2,remaining_amount:'20.00'}]};
  else if(u.pathname==='/api/refunds/orders/9001/quote')data={mode:'RETURN_REFUND',amount:'10.00',items:[{orderItemId:'2001',productName:'测试手机',quantity:1,amount:'10.00'}]};
  else if(u.pathname==='/api/refunds/orders/9001'&&method==='POST')data=refund;
  else if(u.pathname==='/api/refunds/admin/RF100/review'){
   refund.status='WAITING_RETURN';Object.assign(refund.returnInfo,{receiver_name:body.receiverName,receiver_phone:body.receiverPhone,return_address:body.returnAddress,version:1});data=refund;
  }else if(u.pathname==='/api/refunds/RF100/return-logistics'){
   refund.status='WAITING_INSPECTION';Object.assign(refund.returnInfo,{carrier:body.carrier,tracking_no:body.trackingNo,version:2});data=refund;
  }else if(u.pathname==='/api/refunds/admin/RF100/inspect'){
   refund.status=body.accepted?'QUEUED':'INSPECTION_REJECTED';refund.returnInfo.inspection_result=body.accepted?'ACCEPTED':'REJECTED';refund.returnInfo.inspection_note=body.note;refund.returnInfo.version++;data=refund;
  }else if(u.pathname==='/api/refunds/RF100/recheck'){refund.status='WAITING_INSPECTION';refund.returnInfo.version++;data=refund;}
  else if(u.pathname==='/api/refunds/RF100'||u.pathname==='/api/refunds/admin/RF100')data=refund;
  else if(u.pathname==='/api/refunds/admin'||u.pathname==='/api/refunds')data={items:[refund],total:1,page:1,hasNext:false};
  else throw Error('Unmocked '+method+' '+u.pathname);
  return {ok:true,status:200,text:async()=>JSON.stringify({success:true,data:clone(data)})};
 };
 w.eval(fs.readFileSync('mall-gateway/src/main/resources/static/shop/refunds.js','utf8'));
 w.eval(fs.readFileSync('mall-gateway/src/main/resources/static/shop/shop.js','utf8'));
 const q=s=>w.document.querySelector(s);
 const wait=async fn=>{for(let i=0;i<200;i++){if(fn())return;await new Promise(r=>setTimeout(r,10));}throw Error('DOM timeout '+hash+' '+q('#app').textContent.slice(0,200));};
 const submit=id=>q(id).dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
 await wait(()=>!q('.loading'));
 return {w,q,wait,submit,calls,refund,close(){assert.deepEqual(errors,[]);dom.window.close();}};
}
(async()=>{
 let p=await page(false,'#order?no=9001');
 assert.equal(p.q('[data-action="refund-open"]').textContent,'申请退货退款');p.q('[data-action="refund-open"]').click();await p.wait(()=>p.q('#refund-quote-form'));
 p.q('[name=item_2001]').value='1';p.q('[name=reason]').value='需要退货';p.submit('#refund-quote-form');await p.wait(()=>p.q('#refund-apply-form'));
 assert.match(p.q('#workspace-content').textContent,/需寄回商品/);p.submit('#refund-apply-form');await p.wait(()=>p.w.location.hash.startsWith('#refund?'));
 const submitted=p.calls.find(c=>c.path==='/api/refunds/orders/9001'&&c.method==='POST').body;assert.equal(submitted.expectedMode,'RETURN_REFUND');assert.equal(submitted.expectedAmount,'10.00');assert.equal(submitted.items[0].quantity,1);p.close();checks.push('订单退货入口、报价方式确认和提交表单事件');

 p=await page(true,'#admin?view=refunds&no=RF100');p.q('[data-action="refund-review-open"][data-approve="true"]').click();await p.wait(()=>p.q('#refund-review-form'));
 for(const [key,value]of Object.entries({receiverName:'模拟仓',receiverPhone:'13800000000',returnAddress:'模拟退货地址'}))p.q(`[name=${key}]`).value=value;
 p.submit('#refund-review-form');await p.wait(()=>p.q('#app').textContent.includes('待买家寄回'));
 assert.equal(p.calls.find(c=>c.path.endsWith('/review')).body.returnAddress,'模拟退货地址');const returned=clone(p.refund);p.close();checks.push('商家审核地址输入与提交');

 p=await page(false,'#refund?no=RF100',returned);p.q('[data-action="return-logistics-open"]').click();await p.wait(()=>p.q('#return-logistics-form'));
 p.q('[name=carrier]').value='顺丰';p.q('[name=trackingNo]').value='SF1234567';p.submit('#return-logistics-form');await p.wait(()=>p.q('#app').textContent.includes('待商家验收'));
 const sent=clone(p.refund);assert.equal(p.calls.find(c=>c.path.endsWith('/return-logistics')).body.version,1);p.close();checks.push('买家填写物流，携带版本号并显示物流');

 p=await page(true,'#admin?view=refunds&no=RF100',sent);p.q('[data-action="return-inspect-open"]').click();await p.wait(()=>p.q('#return-inspect-form'));
 p.q('[name=note]').value='商品完好';p.submit('#return-inspect-form');await p.wait(()=>p.q('#workspace-error'));
 assert.match(p.q('#workspace-error').textContent,/确认已收到/);assert.ok(!p.calls.some(c=>c.path.endsWith('/inspect')));
 p.q('[name=received]').checked=true;p.q('[name=restock_2001]').value='1';p.submit('#return-inspect-form');await p.wait(()=>p.q('#app').textContent.includes('等待退款'));
 const inspection=p.calls.find(c=>c.path.endsWith('/inspect')).body;assert.equal(inspection.version,2);assert.equal(inspection.restockItems[0].quantity,1);p.close();checks.push('商家验收确认、逐项补库和结果提交');

 const rejected=clone(sent);rejected.status='INSPECTION_REJECTED';rejected.returnInfo.inspection_result='REJECTED';rejected.returnInfo.inspection_note='请核对数量';rejected.returnInfo.version=3;
 p=await page(false,'#refund?no=RF100',rejected);p.q('[data-action="return-recheck-open"]').click();await p.wait(()=>p.q('#return-recheck-form'));
 p.q('[name=note]').value='已寄齐，请复核';p.submit('#return-recheck-form');await p.wait(()=>p.q('#app').textContent.includes('待商家验收'));p.close();checks.push('验收未通过显示原因并允许买家复核');

 p=await page(true,'#admin?view=refunds');assert.ok(p.q('select[name=status] option[value=WAITING_RETURN]'));assert.ok(p.q('[href="#admin?view=refunds"]'));p.close();checks.push('商家导航与退货状态筛选');
 fs.writeFileSync('logs/refund-dom-result.json',JSON.stringify({status:'PASSED',checks},null,2));console.log(JSON.stringify({status:'PASSED',checks}));
})().catch(e=>{console.error(e);process.exitCode=1;});
