(function(){
  if(window.__halconMobileExperienceInstalled)return;
  window.__halconMobileExperienceInstalled=true;

  var N=window.HalconNative||null;
  var root=document.documentElement;
  root.classList.add('halcon-apk-mobile');

  function parse(raw,fallback){try{return JSON.parse(raw||'')}catch(e){return fallback}}
  function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
  function visible(el){if(!el)return false;var r=el.getBoundingClientRect(),s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'}
  function codeOf(m){return String((m&&(m.code||m.id||m.module_code||m.slug))||'').trim()}
  function nameOf(m){return String((m&&(m.name||m.label||m.title||m.code))||'Módulo').trim()}
  function moduleByCode(code){for(var i=0;i<modules.length;i++)if(codeOf(modules[i])===code)return modules[i];return null}
  function normalizeModules(rows){
    var out=[],seen={};
    (rows||[]).forEach(function(m){var c=codeOf(m);if(!c||seen[c])return;seen[c]=1;out.push(m)});
    return out;
  }

  var bootstrap=parse(N&&N.getModulesJson?N.getModulesJson():'',{})||{};
  var modules=normalizeModules(bootstrap.modules||[]);
  var current='';
  try{current=N&&N.getCurrentModule?String(N.getCurrentModule()||''):''}catch(e){}
  if(!current){
    try{current=new URL(location.href).searchParams.get('halcon_tab')||new URL(location.href).searchParams.get('module')||''}catch(e){}
  }
  if(current&&!moduleByCode(current))modules.unshift({code:current,name:current});

  var storedShortcuts=[];
  try{storedShortcuts=parse(N&&N.getBottomShortcuts?N.getBottomShortcuts():'',[])||[]}catch(e){}
  storedShortcuts=storedShortcuts.filter(function(c){return !!moduleByCode(String(c))}).slice(0,4);
  modules.forEach(function(m){if(storedShortcuts.length<4&&storedShortcuts.indexOf(codeOf(m))<0)storedShortcuts.push(codeOf(m))});

  var theme=parse(localStorage.getItem('halconAppThemeV1'),{h:266,s:48,l:50})||{h:266,s:48,l:50};
  theme.h=isFinite(theme.h)?+theme.h:266;theme.s=isFinite(theme.s)?+theme.s:48;theme.l=isFinite(theme.l)?+theme.l:50;

  var style=document.createElement('style');
  style.id='halcon-mobile-experience-style';
  style.textContent=
  ':root{--halcon-app-accent:hsl('+theme.h+','+theme.s+'%,'+theme.l+'%);--halcon-app-gap:12px}'+
  'html.halcon-apk-mobile,html.halcon-apk-mobile body{max-width:100%!important;overflow-x:hidden!important}'+
  'html.halcon-apk-mobile body{padding-bottom:94px!important;box-sizing:border-box!important}'+
  'html.halcon-apk-mobile main,html.halcon-apk-mobile .site-main,html.halcon-apk-mobile .entry-content,html.halcon-apk-mobile .halcon-content,html.halcon-apk-mobile .halcon-app-content,html.halcon-apk-mobile #halcon-app{min-width:0!important;max-width:100%!important;overflow-wrap:anywhere!important;box-sizing:border-box!important}'+
  '@media(max-width:720px){html.halcon-apk-mobile main,html.halcon-apk-mobile .site-main,html.halcon-apk-mobile .entry-content,html.halcon-apk-mobile .halcon-content,html.halcon-apk-mobile .halcon-app-content,html.halcon-apk-mobile #halcon-app{padding-left:max(var(--halcon-app-gap),env(safe-area-inset-left))!important;padding-right:max(var(--halcon-app-gap),env(safe-area-inset-right))!important}html.halcon-apk-mobile img,html.halcon-apk-mobile video,html.halcon-apk-mobile iframe{max-width:100%!important;height:auto}html.halcon-apk-mobile table{max-width:100%!important;display:block;overflow-x:auto}html.halcon-apk-mobile input,html.halcon-apk-mobile textarea,html.halcon-apk-mobile select,html.halcon-apk-mobile button{max-width:100%;box-sizing:border-box}}'+
  '#halconAppBottom{position:fixed;z-index:2147483000;left:8px;right:8px;bottom:8px;height:70px;border-radius:18px;background:rgba(255,255,255,.97);box-shadow:0 6px 28px rgba(0,0,0,.18);display:flex;align-items:stretch;gap:4px;padding:6px;backdrop-filter:blur(12px)}'+
  '#halconAppBottom button{appearance:none;border:0;background:transparent;color:#5f5968;border-radius:13px;min-width:0;flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;padding:4px 2px;font-size:10px;line-height:1.05;overflow:hidden}'+
  '#halconAppBottom button svg{width:22px;height:22px;display:block;stroke:currentColor;fill:none;stroke-width:1.8}'+
  '#halconAppBottom button .label{display:block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}'+
  '#halconAppBottom button.active{background:var(--halcon-app-accent);color:#fff}'+
  '#halconAppBottom button.pressing{transform:scale(.96);background:#eee}'+
  '#halconTempHandle{position:fixed;z-index:2147482998;left:0;top:42%;width:26px;height:62px;border-radius:0 16px 16px 0;background:var(--halcon-app-accent);color:#fff;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 16px rgba(0,0,0,.2);font-size:11px;font-weight:700;writing-mode:vertical-rl;transform:rotate(180deg)}'+
  '#halconTempBackdrop{position:fixed;z-index:2147483100;inset:0;background:rgba(0,0,0,.35);display:none}#halconTempBackdrop.open{display:block}'+
  '#halconTempDrawer{position:absolute;left:0;top:0;bottom:0;width:min(88vw,390px);background:#f6f6f8;transform:translateX(-102%);transition:transform .22s ease;box-shadow:8px 0 30px rgba(0,0,0,.18);display:flex;flex-direction:column}#halconTempBackdrop.open #halconTempDrawer{transform:translateX(0)}'+
  '#halconTempHead{padding:18px 16px 12px;background:#fff;border-bottom:1px solid #e8e8ec;display:flex;align-items:center;gap:10px}#halconTempHead b{flex:1}'+
  '#halconTempHead button,.halconMiniBtn{border:0;border-radius:10px;background:#ece8f4;color:#4d3572;padding:8px 10px;font-size:12px}'+
  '#halconTempList{padding:12px;overflow:auto;flex:1}.halconTempCard{background:#fff;border-radius:14px;padding:12px;margin-bottom:10px;box-shadow:0 2px 10px rgba(0,0,0,.05)}.halconTempMeta{font-size:10px;color:#777;margin-bottom:6px}.halconTempText{white-space:pre-wrap;font-size:13px;line-height:1.4}.halconTempActions{display:none;gap:7px;margin-top:10px}.halconTempCard.actions .halconTempActions{display:flex}.halconTempActions button{flex:1;border:0;border-radius:10px;padding:9px;font-size:11px}.halconTempActions .move{background:var(--halcon-app-accent);color:#fff}.halconTempActions .del{background:#f4e8ea;color:#8a2534}'+
  '.halconAppModal{position:fixed;z-index:2147483200;inset:0;background:rgba(0,0,0,.35);display:flex;align-items:flex-end;justify-content:center;padding:10px}.halconAppSheet{width:min(100%,520px);max-height:82vh;overflow:auto;background:#fff;border-radius:20px;padding:16px;box-shadow:0 10px 30px rgba(0,0,0,.22)}.halconAppSheet h3{margin:2px 0 12px}.halconAppGrid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.halconAppGrid button{border:1px solid #e1dbe8;background:#fff;border-radius:12px;padding:10px;text-align:left}.halconAppRange{display:grid;grid-template-columns:92px 1fr 38px;gap:8px;align-items:center;margin:10px 0;font-size:12px}.halconAppRange input{width:100%}.halconAppSheet .close{width:100%;border:0;border-radius:12px;background:#eee;padding:11px;margin-top:12px}';
  document.head.appendChild(style);

  function hslToHex(h,s,l){
    s/=100;l/=100;var c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs((h/60)%2-1)),m=l-c/2,r=0,g=0,b=0;
    if(h<60){r=c;g=x}else if(h<120){r=x;g=c}else if(h<180){g=c;b=x}else if(h<240){g=x;b=c}else if(h<300){r=x;b=c}else{r=c;b=x}
    function hx(v){var z=Math.round((v+m)*255).toString(16);return z.length<2?'0'+z:z}
    return '#'+hx(r)+hx(g)+hx(b)
  }
  function applyTheme(){
    root.style.setProperty('--halcon-app-accent','hsl('+theme.h+','+theme.s+'%,'+theme.l+'%)');
    localStorage.setItem('halconAppThemeV1',JSON.stringify(theme));
    try{if(N&&N.setSystemBarColor)N.setSystemBarColor(hslToHex(theme.h,theme.s,theme.l))}catch(e){}
  }
  applyTheme();

  function iconSvg(code){
    code=String(code||'').toLowerCase();
    if(/quote|cotiza/.test(code))return '<svg viewBox="0 0 24 24"><path d="M6 3h9l3 3v15H6z"/><path d="M9 9h6M9 13h6M9 17h4"/></svg>';
    if(/note|nota/.test(code))return '<svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>';
    if(/service|servicio/.test(code))return '<svg viewBox="0 0 24 24"><path d="M14 6a4 4 0 0 0-5 5L4 16l4 4 5-5a4 4 0 0 0 5-5l-3 3-4-4z"/></svg>';
    if(/agenda|calendar|cita/.test(code))return '<svg viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4M16 3v4M4 10h16"/></svg>';
    if(/market/.test(code))return '<svg viewBox="0 0 24 24"><path d="M4 17V7l10-3v16z"/><path d="M14 8h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-4"/></svg>';
    if(/order|pedido/.test(code))return '<svg viewBox="0 0 24 24"><path d="M6 3h12v18H6z"/><path d="M9 8h6M9 12h6M9 16h4"/></svg>';
    if(/product/.test(code))return '<svg viewBox="0 0 24 24"><path d="M4 7l8-4 8 4-8 4z"/><path d="M4 7v10l8 4 8-4V7M12 11v10"/></svg>';
    if(/message|mensaje/.test(code))return '<svg viewBox="0 0 24 24"><path d="M4 5h16v12H8l-4 4z"/></svg>';
    return '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg>';
  }

  function saveShortcuts(){
    try{if(N&&N.saveBottomShortcuts)N.saveBottomShortcuts(JSON.stringify(storedShortcuts))}catch(e){}
  }
  function openModule(code){try{if(N&&N.openWebApp)N.openWebApp(code)}catch(e){}}

  var bar=document.createElement('nav');bar.id='halconAppBottom';bar.setAttribute('aria-label','Accesos de Sistema Halcón');document.body.appendChild(bar);
  var pressTimer=null,pressTriggered=false;
  function attachLongPress(btn,slot){
    function start(){pressTriggered=false;btn.classList.add('pressing');pressTimer=setTimeout(function(){pressTriggered=true;btn.classList.remove('pressing');openShortcutPicker(slot)},550)}
    function end(ev){btn.classList.remove('pressing');if(pressTimer){clearTimeout(pressTimer);pressTimer=null}if(pressTriggered){ev&&ev.preventDefault();ev&&ev.stopPropagation()}}
    btn.addEventListener('pointerdown',start);btn.addEventListener('pointerup',end);btn.addEventListener('pointercancel',end);btn.addEventListener('pointerleave',end);
  }
  function renderBar(){
    bar.innerHTML='';
    storedShortcuts.slice(0,4).forEach(function(code,slot){
      var m=moduleByCode(code)||{code:code,name:code};
      var b=document.createElement('button');b.type='button';b.dataset.code=code;b.innerHTML=iconSvg(code)+'<span class="label">'+esc(nameOf(m))+'</span>';
      if(code===current)b.classList.add('active');
      b.onclick=function(ev){if(pressTriggered){ev.preventDefault();return}openModule(code)};
      attachLongPress(b,slot);bar.appendChild(b)
    });
    var more=document.createElement('button');more.type='button';more.innerHTML=iconSvg('grid')+'<span class="label">Más</span>';more.onclick=openMore;bar.appendChild(more)
  }

  function closeModal(){var m=document.querySelector('.halconAppModal');if(m)m.remove()}
  function modal(title,body){
    closeModal();var m=document.createElement('div');m.className='halconAppModal';m.innerHTML='<div class="halconAppSheet"><h3>'+esc(title)+'</h3>'+body+'<button class="close" type="button">Cerrar</button></div>';document.body.appendChild(m);m.addEventListener('click',function(e){if(e.target===m)closeModal()});m.querySelector('.close').onclick=closeModal;return m
  }
  function openShortcutPicker(slot){
    var body='<p style="font-size:12px;color:#666">Elige qué módulo quieres dejar en este acceso rápido.</p><div class="halconAppGrid">';
    modules.forEach(function(m){body+='<button type="button" data-pick="'+esc(codeOf(m))+'">'+iconSvg(codeOf(m))+' '+esc(nameOf(m))+'</button>'});body+='</div>';
    var mm=modal('Cambiar acceso rápido',body);
    mm.querySelectorAll('[data-pick]').forEach(function(b){b.onclick=function(){var code=this.getAttribute('data-pick');var other=storedShortcuts.indexOf(code);if(other>=0&&other!==slot){var old=storedShortcuts[slot];storedShortcuts[other]=old}storedShortcuts[slot]=code;saveShortcuts();renderBar();closeModal()}})
  }
  function openMore(){
    var body='<div class="halconAppGrid">';
    modules.forEach(function(m){body+='<button type="button" data-open="'+esc(codeOf(m))+'">'+iconSvg(codeOf(m))+' '+esc(nameOf(m))+'</button>'});body+='</div>';
    body+='<hr style="border:0;border-top:1px solid #eee;margin:16px 0"><h4 style="margin:0 0 8px">Apariencia de la aplicación</h4>'+
      '<div class="halconAppRange"><span>Matiz</span><input id="halconHue" type="range" min="0" max="360" value="'+theme.h+'"><b id="halconHueV">'+theme.h+'</b></div>'+
      '<div class="halconAppRange"><span>Saturación</span><input id="halconSat" type="range" min="20" max="100" value="'+theme.s+'"><b id="halconSatV">'+theme.s+'</b></div>'+
      '<div class="halconAppRange"><span>Luz</span><input id="halconLight" type="range" min="28" max="72" value="'+theme.l+'"><b id="halconLightV">'+theme.l+'</b></div>'+
      '<button type="button" id="halconResetTheme" class="close">Restaurar color</button>';
    var mm=modal('Más',body);
    mm.querySelectorAll('[data-open]').forEach(function(b){b.onclick=function(){openModule(this.getAttribute('data-open'));closeModal()}});
    function bindRange(id,key,out){var x=mm.querySelector(id),v=mm.querySelector(out);if(!x)return;x.oninput=function(){theme[key]=+this.value;if(v)v.textContent=this.value;applyTheme()}};
    bindRange('#halconHue','h','#halconHueV');bindRange('#halconSat','s','#halconSatV');bindRange('#halconLight','l','#halconLightV');
    var reset=mm.querySelector('#halconResetTheme');if(reset)reset.onclick=function(){theme={h:266,s:48,l:50};applyTheme();closeModal();openMore()}
  }

  function isQuoteModule(){return /quote|cotiza/i.test(current||location.href||'')}
  var handle=document.createElement('button');handle.id='halconTempHandle';handle.type='button';handle.textContent='Mensajes';handle.style.display=isQuoteModule()?'flex':'none';document.body.appendChild(handle);
  var backdrop=document.createElement('div');backdrop.id='halconTempBackdrop';backdrop.innerHTML='<aside id="halconTempDrawer"><div id="halconTempHead"><b>Mensajes temporales</b><button id="halconTempClose" type="button">Cerrar</button></div><div id="halconTempList"></div></aside>';document.body.appendChild(backdrop);
  function getTemp(){try{return parse(N&&N.getTemporaryMessages?N.getTemporaryMessages():'',[])||[]}catch(e){return []}}
  function sourceLabel(s){s=String(s||'').toLowerCase();if(s==='whatsapp')return 'WhatsApp';if(s==='telegram')return 'Telegram';if(s==='messenger')return 'Messenger';if(s==='instagram')return 'Instagram';if(s==='tiktok')return 'TikTok';return 'Compartido'}
  function removeTemp(id){try{if(N&&N.removeTemporaryMessage)N.removeTemporaryMessage(String(id))}catch(e){}renderTemp()}
  function candidateFields(){
    var qs='textarea,[contenteditable="true"],input[name*="detalle" i],input[name*="descripcion" i],input[name*="description" i],input[name*="detail" i]';
    return Array.prototype.slice.call(document.querySelectorAll(qs)).filter(visible)
  }
  function candidateItems(){
    var qs='[data-item-index],[data-item],.quote-item,.cotizacion-item,.halcon-quote-item,.quote-line,.item-row,.item-card';
    var arr=Array.prototype.slice.call(document.querySelectorAll(qs)).filter(visible);
    if(arr.length)return arr;
    return candidateFields()
  }
  function appendToTarget(target,text){
    var field=target.matches&&target.matches('textarea,input,[contenteditable="true"]')?target:target.querySelector('textarea,[contenteditable="true"],input[name*="detalle" i],input[name*="descripcion" i],input[name*="description" i],input[name*="detail" i]');
    if(!field)return false;
    if(field.isContentEditable){field.textContent=(field.textContent?field.textContent+'\n':'')+text}
    else{field.value=(field.value?field.value+'\n':'')+text}
    field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));field.focus();return true
  }
  function moveMessage(msg){
    var items=candidateItems();
    if(!items.length){
      try{navigator.clipboard&&navigator.clipboard.writeText(msg.text||'')}catch(e){}
      alert('No encontré un campo de ítem visible. Dejé el mensaje listo para copiar y pegar.');return
    }
    var body='<p style="font-size:12px;color:#666">Selecciona el ítem al que quieres enviar este mensaje.</p><div class="halconAppGrid">';
    items.slice(0,20).forEach(function(_,i){body+='<button type="button" data-item="'+i+'">Ítem '+(i+1)+'</button>'});body+='</div>';
    var mm=modal('Trasladar a ítem',body);
    mm.querySelectorAll('[data-item]').forEach(function(b){b.onclick=function(){var i=+this.getAttribute('data-item');if(appendToTarget(items[i],msg.text||'')){removeTemp(msg.id);closeModal();closeTemp()}else alert('No encontré el campo de descripción de ese ítem.')}})
  }
  function renderTemp(){
    var list=backdrop.querySelector('#halconTempList'),rows=getTemp();list.innerHTML='';
    if(!rows.length){list.innerHTML='<div style="padding:24px 10px;text-align:center;color:#777;font-size:13px">No hay mensajes temporales.</div>';return}
    rows.forEach(function(msg){
      var card=document.createElement('article');card.className='halconTempCard';
      card.innerHTML='<div class="halconTempMeta">'+esc(sourceLabel(msg.source))+(msg.sender?' · '+esc(msg.sender):'')+'</div><div class="halconTempText">'+esc(msg.text||'Archivo compartido')+'</div><button type="button" class="halconMiniBtn" style="margin-top:9px">•••</button><div class="halconTempActions"><button type="button" class="move">Pasar a ítem</button><button type="button" class="del">Eliminar</button></div>';
      card.querySelector('.halconMiniBtn').onclick=function(){card.classList.toggle('actions')};
      card.querySelector('.move').onclick=function(){moveMessage(msg)};
      card.querySelector('.del').onclick=function(){if(confirm('¿Eliminar este mensaje temporal?'))removeTemp(msg.id)};
      list.appendChild(card)
    })
  }
  function openTemp(){if(!isQuoteModule())return;renderTemp();backdrop.classList.add('open')}
  function closeTemp(){backdrop.classList.remove('open')}
  handle.onclick=openTemp;backdrop.querySelector('#halconTempClose').onclick=closeTemp;backdrop.addEventListener('click',function(e){if(e.target===backdrop)closeTemp()});

  var sx=0,sy=0,tracking=false;
  document.addEventListener('touchstart',function(e){if(!isQuoteModule()||!e.touches||e.touches.length!==1)return;sx=e.touches[0].clientX;sy=e.touches[0].clientY;tracking=true},{passive:true});
  document.addEventListener('touchend',function(e){if(!tracking||!e.changedTouches||!e.changedTouches.length)return;tracking=false;var dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;if(dx>70&&Math.abs(dy)<55)openTemp()},{passive:true});

  window.HalconAppMobile={refreshTemporary:function(){renderTemp();if(isQuoteModule()){handle.style.display='flex';var n=getTemp().length;handle.textContent=n?'Mensajes '+n:'Mensajes'}}};

  function updateTitle(){
    var m=moduleByCode(current);if(!m)return;
    var selectors='[data-app-title],.halcon-app-title,.app-module-title,.module-title';
    document.querySelectorAll(selectors).forEach(function(el){if(visible(el))el.textContent=nameOf(m)})
  }

  renderBar();renderTemp();window.HalconAppMobile.refreshTemporary();updateTitle();
})();