function auth(){
 if(S.stage==='code'){
  set(shell('<div class="small">Ingresa la licencia entregada por Sistema Halcón. También se aceptan códigos APP antiguos.</div><label class="field"><span>Licencia / código de activación</span><input id="code" autocomplete="one-time-code" placeholder="HALCON-..."></label><button class="primary" id="activate">Validar activación</button><div id="status" class="statusLine"></div>'));
  el('activate').onclick=function(){var c=el('code').value.trim().toUpperCase();if(c.length<8){alert('Ingresa una licencia o código válido.');return}busy(this,true,'Validando…');el('status').textContent='Comprobando empresa y licencia…';try{S.activationCode=c;localStorage.setItem('activationCode',c);HalconNative.activateCode(c)}catch(e){busy(this,false);alert('No fue posible iniciar la validación.')}};
  return;
 }
 if(S.stage==='email'){
  set(shell('<div class="small">Empresa validada. Ahora identifica tu cuenta.</div><label class="field"><span>Correo registrado</span><input id="email" type="email" value="'+esc(S.email)+'"></label><button class="primary" id="continueEmail">Continuar</button><button class="helpBtn" id="helpOpen">¿Necesitas ayuda para ingresar?</button><div class="helpBox" id="helpBox"><p>Escribe tu correo registrado y presiona Pedir ayuda.</p><button class="primary" id="helpSend">Pedir ayuda</button><div class="waRow"><button class="waBtn" id="wa1">WhatsApp 1</button><button class="waBtn" id="wa2">WhatsApp 2</button></div></div>'));
  el('continueEmail').onclick=function(){var e=el('email').value.trim();if(e.indexOf('@')<1){alert('Ingresa el correo registrado.');return}S.email=e;localStorage.setItem('email',e);busy(this,true,'Consultando…');HalconNative.accountState(e)};
  el('helpOpen').onclick=function(){el('helpBox').className=el('helpBox').className.indexOf('on')>=0?'helpBox':'helpBox on'};
  el('helpSend').onclick=function(){var e=el('email').value.trim();if(e.indexOf('@')<1){alert('Ingresa primero tu correo registrado.');return}HalconNative.help(e)};
  el('wa1').onclick=function(){HalconNative.openSupport(0)};el('wa2').onclick=function(){HalconNative.openSupport(1)};
  return;
 }
 if(S.stage==='existing'){
  set(shell('<div class="small">La cuenta ya está activa. Ingresa tu contraseña actual.</div><div class="emailBadge">'+esc(S.email)+'</div><label class="field"><span>Contraseña</span><div class="pass"><input id="pw" type="password"><button id="eye" class="eyeBtn" type="button"></button></div></label><button class="primary" id="login">Ingresar</button><button class="link" id="forgot">¿Olvidaste tu contraseña?</button><button class="backCentered" id="backEmail">Cambiar correo</button>'));
  hold('pw','eye');el('login').onclick=function(){var p=el('pw').value;if(!p){alert('Ingresa tu contraseña.');return}busy(this,true,'Ingresando…');HalconNative.login(JSON.stringify({email:S.email,password:p}))};
  el('forgot').onclick=function(){HalconNative.forgot(S.email)};el('backEmail').onclick=function(){S.stage='email';render()};return;
 }
 if(S.stage==='newpass'){
  set(shell('<div class="small">Primera activación. Crea tu contraseña definitiva para usarla en computador y aplicación.</div><div class="emailBadge">'+esc(S.email)+'</div><label class="field"><span>Nueva contraseña</span><div class="pass"><input id="np" type="password"><button id="ne" class="eyeBtn" type="button"></button></div></label><label class="field"><span>Repetir contraseña</span><input id="cp" type="password"></label><button class="primary" id="savepw">Crear contraseña</button>'));
  hold('np','ne');el('savepw').onclick=function(){var p=el('np').value,q=el('cp').value;if(p.length<8||p!==q){alert('Usa al menos 8 caracteres y repite la misma contraseña.');return}busy(this,true,'Guardando…');HalconNative.changePassword(JSON.stringify({password:p,passwordConfirm:q}))};return;
 }
 S.stage='code';render();
}