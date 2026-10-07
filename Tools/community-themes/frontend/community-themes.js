/* Same-origin Hub module. No tracking, theme application, or automatic submissions. */
(() => {
 'use strict';
 const API='/api/public/community-themes', tg=window.Telegram?.WebApp;
 const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
 const button=(text,fn)=>{const b=el('button',text);b.type='button';b.onclick=fn;return b;};
 const dialog=el('dialog',undefined,'community-themes');dialog.setAttribute('aria-label','Temas comunitarios');
 const title=el('h2','Temas comunitarios'),close=button('Cerrar',()=>dialog.close());
 const status=el('p');status.setAttribute('role','status');status.setAttribute('aria-live','polite');
 const cards=el('div',undefined,'ct-grid'), mine=el('div'), form=el('form');let cursor=null,busy=false;
 async function api(path='',body){
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),15000);
  try{const r=await fetch(API+path,{method:body?'POST':'GET',credentials:'same-origin',cache:'no-store',signal:controller.signal,
   headers:body?{'Content-Type':'application/json'}:undefined,body:body?JSON.stringify(body):undefined});
   if(!r.ok){
    let problem;try{problem=await r.json();}catch(_){}
    const known={unsupported_keys:'El tema contiene opciones no compatibles. Solo se admiten colores; no fondos, imágenes ni medidas.',opaque_colors_required:'Los colores deben ser opacos, sin transparencia.',invalid_attheme:'No se reconoce el archivo .attheme.',invalid_color:'El archivo contiene un color no válido.',file_too_large:'El archivo supera el límite permitido.',invalid_title:'El nombre debe tener entre 1 y 80 caracteres.',invalid_alias:'El alias puede tener hasta 40 caracteres.',license_required:'Confirma que tienes permiso para compartir el tema.'};
    throw Error(known[problem?.error]||(r.status===401?'Abre el Hub desde Telegram para identificarte.':r.status===403?'No tienes permiso para esta acción.':r.status===429?'Has enviado demasiadas propuestas. Inténtalo más tarde.':'No se pudo completar la petición.'));
   }
   const data=await r.json();if(data.ok===false)throw Error('No se pudo completar la petición.');return data;
  }finally{clearTimeout(timeout);}
 }
 const auth=()=>{if(!tg?.initData)throw Error('Abre el Hub desde Telegram para identificarte.');return {initData:tg.initData};};
 const color=(colors,key,fallback)=>/^#[0-9a-f]{8}$/i.test(colors?.[key]||'')?'#'+colors[key].slice(3):fallback;
 function preview(item){
  const p=el('div',undefined,'ct-preview');p.setAttribute('aria-label','Muestra ilustrativa de colores; no contiene conversaciones reales');
  const c=item.colors||{};p.style.backgroundColor=color(c,'windowBackgroundWhite','#eef3f6');
  const bar=el('div','Vista previa de colores');bar.style.backgroundColor=color(c,'actionBarDefault','#527da3');bar.style.color=color(c,'actionBarDefaultTitle','#ffffff');
  const incoming=el('p','Mensaje de ejemplo'),outgoing=el('p','Así se ve la respuesta');
  incoming.style.backgroundColor=color(c,'chat_inBubble','#ffffff');outgoing.style.backgroundColor=color(c,'chat_outBubble','#daf5c8');
  incoming.style.color=color(c,'chat_messageTextIn','#222222');outgoing.style.color=color(c,'chat_messageTextOut','#222222');
  p.append(bar,incoming,outgoing);return p;
 }
 function openPreview(item){
  if(item.type==='telegram_link'){
   if(!/^[A-Za-z0-9_-]{1,128}$/.test(item.telegram_slug||''))throw Error('Enlace de tema no válido.');
   const url='https://t.me/addtheme/'+item.telegram_slug;if(tg?.openTelegramLink)tg.openTelegramLink(url);else window.open(url,'_blank','noopener');
  }else{
   if(!/^[a-f0-9]{32}$/i.test(item.id||''))throw Error('Identificador no válido.');
   const url=new URL('/community-theme?id='+encodeURIComponent(item.id),location.origin).href;
   if(tg?.openLink)tg.openLink(url);else window.open(url,'_blank','noopener');
  }
 }
 function card(item,owner=false){
  const box=el('article');box.append(el('h3',item.title||'Tema sin título'));
  if(item.author_alias)box.append(el('p','Por '+item.author_alias));
  if(item.type==='attheme')box.append(preview(item));else box.append(el('p','Tema de Telegram: la vista previa se abrirá en la aplicación.'));
  if(item.status&&item.status!=='approved')box.append(el('p',({pending:'Pendiente de revisión',rejected:'Rechazado'})[item.status]||'No publicado'));
  else box.append(button('Ver',()=>{try{openPreview(item);}catch(e){status.textContent=e.message;}}));
  if(owner&&item.status==='pending')for(const [action,label] of [['approve','Aprobar'],['reject','Rechazar']])box.append(button(label,async event=>{
   event.currentTarget.disabled=true;
   try{await api('/moderate',{...auth(),id:item.id,action});await loadMine();await load(false);}catch(e){status.textContent=e.message;event.currentTarget.disabled=false;}
  }));return box;
 }
 const more=button('Cargar más',()=>load(true));more.hidden=true;
 async function load(append=false){
  if(busy)return;busy=true;more.disabled=true;status.textContent='Cargando temas…';
  try{const data=await api('?limit=20'+(append&&cursor?'&cursor='+encodeURIComponent(cursor):''));if(!append)cards.replaceChildren();
   for(const item of (data.themes||[]).slice(0,20))cards.append(card(item));cursor=data.next_cursor||null;more.hidden=!cursor;
   status.textContent=cards.children.length?'Solo se muestran temas aprobados.':'Todavía no hay temas aprobados.';
  }catch(e){status.textContent=e.message;}finally{busy=false;more.disabled=false;}
 }
 async function loadMine(){try{const data=await api('/mine',auth());mine.replaceChildren(el('h3',data.is_owner?'Tus temas y pendientes de moderación':'Mis propuestas'));
  for(const item of (data.themes||[]).slice(0,100))mine.append(card(item,data.is_owner===true));if(!data.themes?.length)mine.append(el('p','Todavía no has enviado temas.'));
 }catch(e){status.textContent=e.message;}}
 const field=(label,type='text')=>{const wrap=el('label',label),input=el('input');input.type=type;wrap.append(input);form.append(wrap);return input;};
 form.append(el('h3','Proponer un tema'),el('p','Comparte un enlace de tema Telegram o un archivo .attheme. Se revisará antes de publicarlo. La paleta no importa imágenes, fondos ni código.'));
 const name=field('Nombre del tema');name.required=true;name.maxLength=80;
 const link=field('Enlace https://t.me/addtheme/…');link.maxLength=200;
 const file=field('O archivo .attheme','file');file.accept='.attheme';
 const alias=field('Alias público opcional');alias.maxLength=40;
 const publicAlias=field('Mostrar este alias al publicar','checkbox');
 const license=field('Tengo permiso para compartir este tema','checkbox');license.required=true;
 const submit=el('button','Enviar a revisión');submit.type='submit';form.append(submit);
 form.onsubmit=async event=>{event.preventDefault();submit.disabled=true;
  try{if(!license.checked)throw Error('Confirma que tienes permiso para compartir el tema.');
   const body={...auth(),title:name.value.trim(),alias:alias.value.trim(),alias_public:publicAlias.checked,license_ack:true};
   if(Boolean(link.value.trim())===Boolean(file.files.length))throw Error('Elige un enlace o un archivo, no ambos.');
   if(file.files.length){const f=file.files[0];if(!/\.attheme$/i.test(f.name)||f.size>65536)throw Error('Selecciona un .attheme de hasta 64 KiB.');body.attheme_text=await f.text();}
   else{const match=/^https:\/\/t\.me\/addtheme\/([A-Za-z0-9_-]{1,128})$/.exec(link.value.trim());if(!match)throw Error('Introduce un enlace completo https://t.me/addtheme/…');body.telegram_slug=match[1];}
   await api('/submit',body);form.reset();await loadMine();status.textContent='Propuesta enviada. Pendiente de aprobación.';
  }catch(e){status.textContent=e.message;}finally{submit.disabled=false;}
 };
 dialog.append(title,close,status,button('Actualizar catálogo',()=>load(false)),cards,more,form,button('Ver mis propuestas',loadMine),mine);document.body.append(dialog);
 function open(){if(!dialog.open)dialog.showModal();load(false);}
 const entry=button('Temas comunitarios',open);entry.className='btn';
 const anchor=document.querySelector('.topbar');if(anchor)anchor.append(entry);
 const classic=document.getElementById('c-services');if(classic)classic.append(button('Temas comunitarios',open));
 window.CommunityThemes={open};
})();
