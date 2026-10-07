// Dependency-free DOM harness: tests request wiring; not a browser rendering test.
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.style={};this.value='';this.files=[];this.checked=false;this.textContent='';}
 set innerHTML(_){throw Error('Unsafe HTML path');}
 append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}
 setAttribute(){}showModal(){this.open=true;}close(){this.open=false;}reset(){}
}
const body=new Element('body'),top=new Element('header'),classic=new Element('div');
const calls=[],links=[];let response={ok:true,themes:[],next_cursor:null};
const document={body,createElement:t=>new Element(t),querySelector:()=>top,getElementById:()=>classic};
const context={document,AbortController,setTimeout,clearTimeout,URL,location:{origin:'https://example.invalid'},
 window:{Telegram:{WebApp:{initData:'test-init',openTelegramLink:u=>links.push(u),openLink:u=>links.push(u)}},open:()=>{throw Error('Unexpected browser fallback');}},
 fetch:async(url,opts)=>{calls.push({url,opts});return {ok:true,json:async()=>response};}};
vm.runInNewContext(fs.readFileSync(__dirname+'/community-themes.js','utf8'),context);
const all=(n)=>[n,...n.children.flatMap(all)];const button=(name)=>all(body).find(n=>n.tag==='button'&&n.textContent===name);
const tick=()=>new Promise(r=>setImmediate(r));
(async()=>{
 assert.equal(calls.length,0,'No background fetch before opening');
 response={ok:true,themes:[{id:'a'.repeat(32),title:'<img src=x onerror=alert(1)>',type:'telegram_link',telegram_slug:'safe_theme',status:'approved'}]};
 top.children[0].onclick();await tick();assert.match(calls[0].url,/\?limit=20$/);
 assert.ok(all(body).some(x=>x.textContent.includes('<img src=')),'Untrusted title is literal text');
 button('Ver').onclick();assert.equal(links.pop(),'https://t.me/addtheme/safe_theme');
 response={ok:true,themes:[{id:'a'.repeat(32),title:'Palette',type:'attheme',colors:{chat_inBubble:'url(evil)'},status:'approved'}]};
 button('Actualizar catálogo').onclick();await tick();button('Ver').onclick();assert.equal(links.pop(),'https://example.invalid/community-theme?id='+'a'.repeat(32));
 const form=all(body).find(n=>n.tag==='form');const fields=all(form).filter(n=>n.tag==='input');
 fields[0].value='My theme';fields[1].value='https://t.me/addtheme/safe_theme';
 const count=calls.length;await form.onsubmit({preventDefault(){}});assert.equal(calls.length,count,'Consent required');
 fields[5].checked=true;response={ok:true,themes:[],is_owner:false};await form.onsubmit({preventDefault(){}});
 const submit=calls.find(c=>c.url.endsWith('/submit'));const payload=JSON.parse(submit.opts.body);
 assert.equal(payload.telegram_slug,'safe_theme');assert.equal(payload.license_ack,true);assert.equal(payload.alias_public,false);
 assert.equal(button('Aprobar'),undefined);
 response={ok:true,is_owner:true,themes:[{id:'b'.repeat(32),type:'attheme',title:'Pending',status:'pending'}]};
 await button('Ver mis propuestas').onclick();assert.ok(button('Aprobar'));assert.ok(button('Rechazar'));
 console.log('Hub theme frontend: deferred fetch, literal text, safe links, bridge, consent, submit, owner controls PASS');
})().catch(e=>{console.error(e);process.exitCode=1;});
