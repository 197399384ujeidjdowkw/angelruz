var API='https://halconimpresiones.cl/wp-json/halcon-app/v1/';
var S={stage:localStorage.getItem('stage')||'login',email:localStorage.getItem('email')||'',token:getNativeToken(),modules:[],messages:[],items:[{open:true}]};

function getNativeToken(){try{return window.HalconNative?HalconNative.getToken():'';}catch(e){return '';}}
function esc(v){v=v==null?'':String(v);return v.replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function byId(id){return document.getElementById(id);}
function setHtml(html){var a=byId('app');if(a)a.innerHTML=html;}
function fail(msg){setHtml('<div class="screen"><div class="card"><div class="logo">SISTEMA HALCÓN</div><div class="sub">No se pudo cargar la interfaz.</div><div style="font-size:12px;line-height:1.5">'+esc(msg)+'</div><button class="primary" onclick="location.reload()">Reintentar</button></div></div>');}

function render(){
 try{
  if(!S.token||S.stage==='login'||S.stage==='code'||S.stage==='password'){auth();return;}
  home();
 }catch(e){fail(e&&e.message?e.message:String(e));}
}

function auth(){
 setHtml('<div class="screen"><div class="card"><div class="logo">SISTEMA HALCÓN</div><div class="sub">Gestiona · Vende · Conecta</div><div id="auth"></div><div class="brandline"></div><div class="sub foot"><a href="https://halconimpresiones.cl/empresas">Sistema Halcón</a> · Gestiona · Vende · Conecta<br><a href="https://carper.cl">Carper Chile</a> · Ejecución Comercial</div></div></div>');
 var a=byId('auth');
 if(S.stage==='login'){
  a.innerHTML='<label class="field"><span>Correo</span><input id="email" type="email" value="'+esc(S.email)+'"></label><label class="field"><span>Contraseña</span><div class="pass"><input id="pw" type="password"><button id="eye" type="button">◉</button></div></label><button class="link" id="forgot" type="button">¿Olvidaste tu contraseña?</button><button class="primary" id="go" type="button">Ingresar</button>';
  holdPassword('pw','eye');
  byId('go').onclick=function(){
   var email=byId('email').value.replace(/^\s+|\s+$/g,'');
   var pw=byId('pw').value;
   if(!email||!pw){alert('Completa correo y contraseña.');return;}
   S.email=email;S.pw=pw;localStorage.setItem('email',email);S.stage='code';render();
  };
  byId('forgot').onclick=function(){try{HalconNative.forgot(S.email||byId('email').value);}catch(e){alert('No fue posible iniciar la recuperación.');}};
 }else if(S.stage==='code'){
  a.innerHTML='<div class="small">Ingresa el código entregado por Sistema Halcón. La app identificará automáticamente la empresa correspondiente.</div><label class="field"><span>Código de activación</span><input id="code" autocomplete="one-time-code" placeholder="APP-XXXX-XXXX-XXXX-XXXX"></label><button class="primary" id="activate" type="button">Validar acceso</button><button class="link" id="back" type="button">Volver</button>';
  byId('activate').onclick=function(){
   var c=byId('code').value.toUpperCase().replace(/^\s+|\s+$/g,'');
   if(!/^APP-[A-Z0-9]{4}(-[A-Z0-9]{4}){3}$/.test(c)){alert('Revisa el código de acceso.');return;}
   try{HalconNative.login(JSON.stringify({email:S.email,password:S.pw,controlToken:c}));}catch(e){alert('No fue posible conectar con Android.');}
  };
  byId('back').onclick=function(){S.stage='login';render();};
 }else{
  a.innerHTML='<div class="small">Crea tu contraseña definitiva.</div><label class="field"><span>Nueva contraseña</span><div class="pass"><input id="np" type="password"><button id="ne" type="button">◉</button></div></label><label class="field"><span>Repetir contraseña</span><input id="cp" type="password"></label><button class="primary" id="savepw" type="button">Crear contraseña</button>';
  holdPassword('np','ne');
  byId('savepw').onclick=function(){
   var p=byId('np').value,q=byId('cp').value;
   if(p.length<8||p!==q){alert('Usa al menos 8 caracteres y repite la misma contraseña.');return;}
   try{HalconNative.changePassword(JSON.stringify({password:p,passwordConfirm:q}));}catch(e){alert('No fue posible guardar la contraseña.');}
  };
 }
}

function holdPassword(inputId,buttonId){
 var x=byId(inputId),b=byId(buttonId);
 function show(){x.type='text';} function hide(){x.type='password';}
 b.onmousedown=show;b.ontouchstart=show;b.onpointerdown=show;
 b.onmouseup=hide;b.onmouseleave=hide;b.ontouchend=hide;b.onpointerup=hide;b.onpointercancel=hide;
}

function home(){
 setHtml('<div class="top"><button id="settingsBtn">⚙</button><div class="moduleSwitch"><button id="prev">‹</button><div class="title" id="title">Cotizaciones</div><button id="next">›</button></div><button id="profileBtn">Perfil</button></div><div class="content" id="content"></div><div class="bottom" id="nav"></div>');
 byId('settingsBtn').onclick=function(){alert('Los ajustes administrativos se gestionan desde la web.');};
 byId('profileBtn').onclick=function(){if(confirm('¿Cerrar sesión?')){try{HalconNative.logout();}catch(e){}localStorage.clear();location.reload();}};
 buildNav();quotes();
 try{HalconNative.bootstrap();}catch(e){}
}

function buildNav(){
 var n=byId('nav'); if(!n)return; n.innerHTML='';
 var lim=Math.min(4,S.modules.length),i;
 for(i=0;i<lim;i++){(function(m){
  var b=document.createElement('button');b.className='nav';b.innerHTML='<b>'+esc(m.icon||'▦')+'</b><span>'+esc(m.name||'Módulo')+'</span>';
  b.onclick=function(){byId('title').innerHTML=esc(m.name||'Módulo');quotes();};n.appendChild(b);
 })(S.modules[i]);}
 var more=document.createElement('button');more.className='nav';more.innerHTML='<b>▦</b><span>Más</span>';more.onclick=function(){alert('Módulos: '+(S.modules.length?S.modules.map(function(x){return x.name;}).join(', '):'sincronizando...'));};n.appendChild(more);
}

function quotes(){
 var c=byId('content');if(!c)return;
 var html='<div class="swipeHint">Desliza dentro de Cotizaciones para abrir Mensajes temporales</div><div class="section"><div class="row"><button class="primary compact" id="newItemBtn">＋ Nuevo ítem</button><button class="primary compact" id="productsBtn">▦ Agregar producto</button></div></div>';
 var i;
 for(i=0;i<S.items.length;i++){
  html+='<div class="item"><div class="itemhead" data-i="'+i+'">Ítem '+(i+1)+' <span>'+(S.items[i].open?'⌃':'⌄')+'</span></div>';
  if(S.items[i].open){html+='<div class="itembody"><div class="row"><input placeholder="Producto"><input placeholder="Cantidad"></div><textarea class="detail" placeholder="Detalle / descripción"></textarea><div class="itemactions"><button>Duplicar</button><button>Eliminar</button><button>＋</button></div></div>';}
  html+='</div>';
 }
 html+='<button class="saveFloat" id="saveQuoteBtn">Guardar</button>';
 c.innerHTML=html;
 byId('newItemBtn').onclick=function(){S.items.push({open:true});quotes();};
 byId('productsBtn').onclick=function(){alert('Aquí abrirá el catálogo real de productos.');};
 byId('saveQuoteBtn').onclick=function(){alert('Guardado de cotización en preparación para conexión final.');};
 var heads=c.getElementsByClassName('itemhead');
 for(i=0;i<heads.length;i++){heads[i].onclick=function(){var k=parseInt(this.getAttribute('data-i'),10);S.items[k].open=!S.items[k].open;quotes();};}
}

window.__api=function(name,status,raw){
 try{
  var d={};try{d=JSON.parse(raw||'{}');}catch(e){}
  if(status>=200&&status<300&&d.ok){
   if(name==='login'){
    if(d.token)S.token=d.token;
    S.stage=d.forcePasswordChange?'password':'ready';
    localStorage.setItem('stage',S.stage);render();return;
   }
   if(name==='change-password'){if(d.token)S.token=d.token;S.stage='ready';localStorage.setItem('stage','ready');render();return;}
   if(name==='bootstrap'){
    S.modules=[];
    var ms=d.modules||[],i;
    for(i=0;i<ms.length;i++)S.modules.push({name:ms[i].name||ms[i].label||ms[i].code||'Módulo',icon:'▦'});
    buildNav();return;
   }
  }
  alert(d.message||('Error de conexión ('+status+')'));
 }catch(e){alert('Respuesta inválida del servidor.');}
};
window.__incoming=function(text,count){S.messages.push({text:text||'',count:count||1});alert('Contenido recibido en Sistema Halcón.');};

window.onerror=function(msg,src,line){fail('Error de interfaz: '+msg+' (línea '+line+')');return true;};
render();