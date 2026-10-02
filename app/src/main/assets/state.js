var S={stage:localStorage.getItem('stage')||'code',email:localStorage.getItem('email')||'',token:getToken(),modules:[],company:null,messages:[],items:[{open:true}],clientName:'',clientPhone:''};
function getToken(){try{return window.HalconNative?HalconNative.getToken():''}catch(e){return''}}
function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
function el(id){return document.getElementById(id)}
function set(html){el('app').innerHTML=html}
function busy(btn,on,label){if(!btn)return;btn.disabled=!!on;if(on){btn.dataset.old=btn.textContent;btn.textContent=label||'Procesando…'}else btn.textContent=btn.dataset.old||btn.textContent}
function hold(i,b){var x=el(i),y=el(b);function a(){x.type='text'}function z(){x.type='password'}y.onmousedown=a;y.ontouchstart=a;y.onpointerdown=a;y.onmouseup=z;y.onmouseleave=z;y.ontouchend=z;y.onpointerup=z;y.onpointercancel=z}
function shell(body){return '<div class="screen"><div class="card"><div class="logo">SISTEMA HALCÓN</div><div class="sub">Gestiona · Vende · Conecta</div>'+body+'<div class="brandline"></div><div class="sub foot">Sistema Halcón · Gestiona · Vende · Conecta<br>Carper Chile · Ejecución Comercial</div></div></div>'}
function labelSource(s){return ({whatsapp:'WhatsApp',telegram:'Telegram',messenger:'Messenger',instagram:'Instagram',tiktok:'TikTok'})[s]||'Otra aplicación'}
function render(){if(S.token&&S.stage==='ready'){home();return}auth()}