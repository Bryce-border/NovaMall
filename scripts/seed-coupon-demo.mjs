// Seeds three demo campaigns without resetting existing issuance or user wallets.
import fs from 'node:fs';
const base=process.env.NOVAMALL_GATEWAY||'http://127.0.0.1:18080';
const parse=t=>JSON.parse(t.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,v=>/^-?\d+$/.test(v)&&!Number.isSafeInteger(Number(v))?`"${v}"`:v));
let token='';
async function api(method,p,body){const r=await fetch(base+'/api'+p,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});const j=parse(await r.text());if(!r.ok||!j.success)throw Error(p+' '+j.message);return j.data;}
token=(await api('POST','/auth/login',{username:process.env.NOVAMALL_ADMIN_USERNAME||'admin',password:process.env.NOVAMALL_ADMIN_PASSWORD||'AdminPass123!'})).accessToken;
const existing=await api('GET','/coupons/admin');
const bj=ms=>new Date(ms+8*3600000).toISOString().slice(0,19),now=Date.now();
const templates=[
 {name:'新人好礼 · 满 20 减 5',kind:'FREE',packPrice:0,packSize:1,totalQuantity:200,opensAt:bj(now-60000)},
 {name:'19.9 元五张满减券包',kind:'PACK',packPrice:19.9,packSize:5,totalQuantity:100,opensAt:bj(now-60000)},
 {name:'整点福利 · 限量满 20 减 5',kind:'FREE',packPrice:0,packSize:1,totalQuantity:50,opensAt:bj(Math.ceil((now+60000)/3600000)*3600000)}
];
for(const template of templates){if(existing.some(c=>c.name===template.name)){console.log('Kept: '+template.name);continue;}await api('POST','/coupons/admin',{thresholdAmount:20,discountAmount:5,closesAt:bj(now+7*86400000),expiresAt:bj(now+30*86400000),...template});console.log('Created: '+template.name);}
