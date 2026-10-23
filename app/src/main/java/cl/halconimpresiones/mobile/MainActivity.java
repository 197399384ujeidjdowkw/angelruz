package cl.halconimpresiones.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.*;

public class MainActivity extends Activity {
  WebView w;
  FrameLayout root;
  View splash;
  HalconAlertRuntime halconAlerts;
  static final String MASTER="https://halconimpresiones.cl/wp-json/halcon-control/v3/app/";
  static final String P="halcon";
  static final int HALCON_CREATE_DOCUMENT=50621;
  final Object fileLock=new Object();
  final java.util.Map<String,PendingFile> pendingFiles=new java.util.HashMap<>();
  File legacyFile=null;
  String legacyName="";
  String legacyMime="";

  static class PendingFile {
    final File file;
    final String name;
    final String mime;
    PendingFile(File f,String n,String m){file=f;name=n;mime=m;}
  }

  static final String LOG_KEY="diagnostic_events";
  static final String PENDING_SHARES_KEY="pending_shares_v1";

  public void onCreate(Bundle b){
    super.onCreate(b);

    root=new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);
    getWindow().setStatusBarColor(Color.rgb(112,72,184));
    getWindow().setNavigationBarColor(Color.rgb(243,244,247));
    if(Build.VERSION.SDK_INT>=30){
      root.setOnApplyWindowInsetsListener((v,insets)->{
        android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
        v.setPadding(0,bars.top,0,bars.bottom);
        return insets;
      });
    }

    w=new WebView(this);
    w.setBackgroundColor(Color.WHITE);
    w.setAlpha(0f);
    WebSettings s=w.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDefaultTextEncodingName("utf-8");
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);

    w.addJavascriptInterface(new Bridge(),"HalconNative");
    w.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this).setTitle("Sistema Halcón")
          .setMessage(message).setPositiveButton("Aceptar",(dialog,which)->result.confirm())
          .setOnCancelListener(dialog->result.cancel()).show();
        return true;
      }
      @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this).setTitle("Sistema Halcón")
          .setMessage(message).setNegativeButton("Cancelar",(dialog,which)->result.cancel())
          .setPositiveButton("Aceptar",(dialog,which)->result.confirm())
          .setOnCancelListener(dialog->result.cancel()).show();
        return true;
      }
      @Override public boolean onJsPrompt(WebView view,String url,String message,String value,JsPromptResult result){
        EditText field=new EditText(MainActivity.this);field.setSingleLine(true);field.setText(value==null?"":value);
        new AlertDialog.Builder(MainActivity.this).setTitle("Sistema Halcón")
          .setMessage(message).setView(field)
          .setNegativeButton("Cancelar",(dialog,which)->result.cancel())
          .setPositiveButton("Aceptar",(dialog,which)->result.confirm(field.getText().toString()))
          .setOnCancelListener(dialog->result.cancel()).show();
        return true;
      }
    });
    w.setDownloadListener((url,userAgent,disposition,mime,length)->downloadHttpFile(url,userAgent,disposition,mime));

    halconAlerts=HalconAlertRuntime.attach(this,w);

    LinearLayout loading=new LinearLayout(this);
    loading.setOrientation(LinearLayout.VERTICAL);
    loading.setGravity(Gravity.CENTER);
    loading.setPadding(40,40,40,40);
    loading.setBackgroundColor(Color.WHITE);

    ImageView logo=new ImageView(this);
    logo.setImageResource(R.drawable.icon_halcon);
    logo.setAdjustViewBounds(true);
    LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(240,240);
    logo.setLayoutParams(lpLogo);

    TextView title=new TextView(this);
    title.setText("SISTEMA HALCÓN");
    title.setTextSize(24);
    title.setTextColor(Color.rgb(112,72,184));
    title.setGravity(Gravity.CENTER);
    title.setPadding(0,24,0,6);

    TextView sub=new TextView(this);
    sub.setText("Gestiona · Vende · Conecta");
    sub.setTextSize(13);
    sub.setTextColor(Color.DKGRAY);
    sub.setGravity(Gravity.CENTER);

    loading.addView(logo);
    loading.addView(title);
    loading.addView(sub);
    splash=loading;

    root.addView(w,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
    root.addView(loading,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
    setContentView(root);

    w.setWebViewClient(new WebViewClient(){
      @Override public void onPageFinished(WebView v,String url){
        super.onPageFinished(v,url);
        w.animate().alpha(1f).setDuration(220).start();
        if(halconAlerts!=null)halconAlerts.onPageReady();
        if(splash!=null){
          splash.animate().alpha(0f).setDuration(180).withEndAction(new Runnable(){
            public void run(){ root.removeView(splash); splash=null; }
          }).start();
        }
      }
      @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err){
        if(req!=null && req.isForMainFrame()){
          showNativeError("No se pudo cargar la interfaz. Revisa la conexión e intenta nuevamente.");
        }
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
        Uri u=r.getUrl();String scheme=u.getScheme();
        if("halcon".equals(scheme)){
          String host=u.getHost();String path=u.getPath();String stage=u.getQueryParameter("stage");
          if("diagnostic".equals(host)){
            if("/download".equals(path)){ Bridge b=new Bridge(); b.appendDiagnostic("diagnostic_tap","Descargar diagnostico"); b.downloadDiagnosticFile(stage); }
            else if("/send".equals(path)){ Bridge b=new Bridge(); b.appendDiagnostic("diagnostic_tap","Enviar diagnostico"); b.sendDiagnosticNative(stage); }
          }
          return true;
        }
        if("http".equals(scheme)||"https".equals(scheme)){
          String siteHost="";
          try{String rawSite=getSharedPreferences(P,0).getString("requested_site_url","");if(!rawSite.matches("(?i)^https?://.*"))rawSite="https://"+rawSite;Uri su=Uri.parse(rawSite);siteHost=su.getHost()==null?"":su.getHost().toLowerCase(Locale.US);}catch(Exception e){}
          String targetHost=u.getHost()==null?"":u.getHost().toLowerCase(Locale.US);
          if(!siteHost.isEmpty()&&siteHost.equals(targetHost))return false;
          try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
          return true;
        }
        if("whatsapp".equals(scheme)){
          try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
          return true;
        }
        return false;
      }
    });

    w.loadUrl("file:///android_asset/index.html");
    handle(getIntent());
  }

  void showNativeError(String message){
    runOnUiThread(new Runnable(){
      public void run(){
        LinearLayout box=new LinearLayout(MainActivity.this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(40,40,40,40);
        box.setBackgroundColor(Color.WHITE);

        ImageView logo=new ImageView(MainActivity.this);
        logo.setImageResource(R.drawable.icon_halcon);
        logo.setAdjustViewBounds(true);
        logo.setLayoutParams(new LinearLayout.LayoutParams(180,180));

        TextView t=new TextView(MainActivity.this);
        t.setText("Sistema Halcón");
        t.setTextSize(22);
        t.setTextColor(Color.rgb(112,72,184));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0,18,0,10);

        TextView m=new TextView(MainActivity.this);
        m.setText(message);
        m.setTextSize(14);
        m.setTextColor(Color.DKGRAY);
        m.setGravity(Gravity.CENTER);

        Button b=new Button(MainActivity.this);
        b.setText("Reintentar");
        b.setOnClickListener(new View.OnClickListener(){
          public void onClick(View v){ recreate(); }
        });

        box.addView(logo);
        box.addView(t);
        box.addView(m);
        box.addView(b);

        root.removeAllViews();
        root.addView(box,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
      }
    });
  }

  protected void onNewIntent(Intent i){
    super.onNewIntent(i);
    setIntent(i);
    if(halconAlerts!=null)halconAlerts.handleIntent(i);
    handle(i);
  }

  void queuePendingShare(String text,String source,String sender){
    try{
      String clean=text==null?"":text.trim();
      if(clean.isEmpty())return;
      android.content.SharedPreferences pref=getSharedPreferences(P,0);
      JSONArray rows;try{rows=new JSONArray(pref.getString(PENDING_SHARES_KEY,"[]"));}catch(Exception e){rows=new JSONArray();}
      JSONObject row=new JSONObject();
      row.put("text",clean);
      row.put("source",source==null?"compartido":source);
      row.put("sender",sender==null?"":sender);
      row.put("count",1);
      row.put("receivedAt",System.currentTimeMillis());
      JSONArray out=new JSONArray();out.put(row);
      for(int x=0;x<rows.length()&&x<39;x++)out.put(rows.opt(x));
      pref.edit().putString(PENDING_SHARES_KEY,out.toString()).apply();
    }catch(Exception e){}
  }

  String consumePendingSharesNative(){
    try{
      android.content.SharedPreferences pref=getSharedPreferences(P,0);
      String raw=pref.getString(PENDING_SHARES_KEY,"[]");
      pref.edit().remove(PENDING_SHARES_KEY).apply();
      return raw==null?"[]":raw;
    }catch(Exception e){return "[]";}
  }

  void handle(Intent i){
    if(i==null)return;
    String a=i.getAction();
    if(Intent.ACTION_SEND.equals(a)||Intent.ACTION_SEND_MULTIPLE.equals(a)){
      String t=i.getStringExtra(Intent.EXTRA_TEXT);
      String sender=i.getStringExtra(Intent.EXTRA_SUBJECT);
      String meta=((t==null?"":t)+" "+(sender==null?"":sender)).toLowerCase();
      String source="compartido";
      try{Uri ref=getReferrer();if(ref!=null)meta+=" "+String.valueOf(ref.getHost()).toLowerCase();}catch(Exception e){}
      if(meta.contains("telegram")||meta.contains("t.me"))source="telegram";
      else if(meta.contains("messenger")||meta.contains("m.me")||meta.contains("facebook"))source="messenger";
      else if(meta.contains("instagram"))source="instagram";
      else if(meta.contains("tiktok"))source="tiktok";
      else if(meta.contains("whatsapp")||meta.contains("wa.me"))source="whatsapp";
      queuePendingShare(t==null?"":t,source,sender==null?"":sender);
      final String js="window.__halconConsumePending&&window.__halconConsumePending()";
      w.postDelayed(new Runnable(){ public void run(){ w.evaluateJavascript(js,null); }},550);
    }
  }

  class Resp{
    int code; String body;
    Resp(int c,String b){code=c;body=b;}
  }

  class Bridge{
    @JavascriptInterface public String getToken(){return getSharedPreferences(P,0).getString("token","");}
    @JavascriptInterface public String consumePendingShares(){return consumePendingSharesNative();}
    @JavascriptInterface public String getDeviceId(){
      String x=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
      return x==null?"android":x;
    }
    @JavascriptInterface public void inspectSite(String siteUrl){inspectSiteNative(siteUrl);}
    @JavascriptInterface public void activateCode(String code){activateCodeOnly(code);}
    @JavascriptInterface public void activateForSite(String siteUrl,String code){
      String normalized=normalizeSiteInput(siteUrl);
      if(normalized.isEmpty()){sendJs("activate",new Resp(400,"{\"ok\":false,\"message\":\"Ingresa una dirección válida del sistema.\"}"));return;}
      getSharedPreferences(P,0).edit().putString("requested_site_url",normalized).apply();
      activateCodeOnly(code);
    }
    @JavascriptInterface public void resolveAccount(String email){resolveAccountNative(email);}
    @JavascriptInterface public void loginExisting(String email,String password){loginWithStoredActivation(email,password);}
    @JavascriptInterface public void login(String raw){activateThenLogin(raw);}
    @JavascriptInterface public void changePassword(String raw){api("change-password",raw,true);}
    @JavascriptInterface public void bootstrap(){api("bootstrap","{}",true);}
    @JavascriptInterface public void openWebApp(String module){openWebAppNative(module);}
    @JavascriptInterface public void logoutApp(){
      getSharedPreferences(P,0).edit().remove("token").remove("activated_email").apply();
      w.post(new Runnable(){ public void run(){ w.loadUrl("file:///android_asset/index.html"); }});
    }
    @JavascriptInterface public void logout(){getSharedPreferences(P,0).edit().clear().apply();}
    @JavascriptInterface public void forgot(String email){ centralPost("forgot",email); }
    @JavascriptInterface public void help(String email){ centralPost("help",email); }
    @JavascriptInterface public void openSupport(int index){ openSupportContact(index); }
    @JavascriptInterface public void save(String raw){api("quotes",raw,true);}
    @JavascriptInterface public String beginFileDownload(String name,String mime){return beginNativeFile(name,mime);}
    @JavascriptInterface public String appendFileChunk(String id,String base64){return appendNativeFile(id,base64);}
    @JavascriptInterface public String finishFileDownload(String id){return finishNativeFile(id);}
    @JavascriptInterface public void cancelFileDownload(String id){cancelNativeFile(id);}

    @JavascriptInterface public void logEvent(String type,String message){appendDiagnostic(type,message);}
    @JavascriptInterface public void downloadDiagnostic(String stage){downloadDiagnosticFile(stage);}
    @JavascriptInterface public void sendDiagnostic(String stage){sendDiagnosticNative(stage);}
    @JavascriptInterface public void refreshControl(){refreshControlNative();}
    @JavascriptInterface public void markControlMessageRead(String id){markControlMessageReadNative(id);}
    @JavascriptInterface public void share(String kind){
      try{
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT,"Sistema Halcón - "+kind);
        startActivity(Intent.createChooser(i,"Compartir"));
      }catch(Exception e){}
    }

    void centralPost(final String name,final String email){
      new Thread(new Runnable(){ public void run(){
        try{
          JSONObject o=new JSONObject();o.put("email",email);o.put("deviceId",getDeviceId());o.put("deviceName","Android");
          Resp rr=http(MASTER+name,"POST",o.toString(),"");
          final String js="window.__api&&window.__api("+q(name)+","+rr.code+","+q(rr.body)+")";
          w.post(new Runnable(){ public void run(){w.evaluateJavascript(js,null);}});
        }catch(Exception e){
          final String body="{\"ok\":false,\"message\":"+JSONObject.quote("No fue posible contactar al servidor.")+"}";
          final String js="window.__api&&window.__api("+q(name)+",0,"+q(body)+")";
          w.post(new Runnable(){ public void run(){w.evaluateJavascript(js,null);}});
        }
      }}).start();
    }

    void openSupportContact(final int index){
      new Thread(new Runnable(){ public void run(){
        try{
          Resp rr=http(MASTER+"support","GET","", "");
          JSONObject j=new JSONObject(rr.body);JSONArray a=j.optJSONArray("whatsapp");
          if(a==null||index<0||index>=a.length())return;final String number=a.optString(index,"").replaceAll("[^0-9]","");
          if(number.isEmpty())return;
          runOnUiThread(new Runnable(){public void run(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+number)));}catch(Exception e){}}});
        }catch(Exception e){}
      }}).start();
    }

    String normalizeSiteInput(String raw){
      try{
        String x=raw==null?"":raw.trim();
        if(x.isEmpty())return "";
        if(!x.matches("(?i)^https?://.*"))x="https://"+x;
        Uri u=Uri.parse(x);
        String host=u.getHost();
        if(host==null||host.trim().isEmpty())return "";
        String path=u.getPath()==null?"":u.getPath().trim();
        while(path.endsWith("/")&&path.length()>1)path=path.substring(0,path.length()-1);
        return "https://"+host.toLowerCase(Locale.US)+path;
      }catch(Exception e){return "";}
    }

    String hostOf(String raw){
      try{
        String x=raw==null?"":raw.trim();
        if(!x.matches("(?i)^https?://.*"))x="https://"+x;
        String h=Uri.parse(x).getHost();
        return h==null?"":h.toLowerCase(Locale.US);
      }catch(Exception e){return "";}
    }

    void inspectSiteNative(final String siteUrl){
      final String normalized=normalizeSiteInput(siteUrl);
      if(normalized.isEmpty()){sendJs("site-status",new Resp(400,"{\"ok\":false,\"message\":\"Ingresa una dirección válida del sistema.\"}"));return;}
      getSharedPreferences(P,0).edit().putString("requested_site_url",normalized).apply();
      new Thread(new Runnable(){ public void run(){
        try{
          Uri target=Uri.parse(normalized);
          String origin="https://"+String.valueOf(target.getHost()).toLowerCase(Locale.US);
          String[] statusUrls=new String[]{
            normalized+"/wp-json/halcon-app/v1/status",
            origin+"/wp-json/halcon-app/v1/status",
            normalized+"/?rest_route=/halcon-app/v1/status",
            origin+"/?rest_route=/halcon-app/v1/status"
          };
          String[] apiBases=new String[]{
            normalized+"/wp-json/halcon-app/v1/",
            origin+"/wp-json/halcon-app/v1/",
            normalized+"/?rest_route=/halcon-app/v1/",
            origin+"/?rest_route=/halcon-app/v1/"
          };
          JSONObject st=null;String apiBase="";int lastCode=0;
          for(int n=0;n<statusUrls.length;n++){
            try{
              Resp rr=http(statusUrls[n],"GET","","");lastCode=rr.code;
              if(rr.code<200||rr.code>=300)continue;
              JSONObject candidate=new JSONObject(rr.body);
              if(!candidate.optBoolean("ok",false))continue;
              st=candidate;apiBase=apiBases[n];break;
            }catch(Exception ignored){}
          }
          if(st==null){sendJs("site-status",new Resp(lastCode==0?404:lastCode,"{\"ok\":false,\"message\":\"No fue posible comprobar Sistema Halcón en esta dirección. Revisa la dirección e intenta nuevamente.\"}"));return;}
          boolean activationComplete=st.optBoolean("activationComplete",false);
          android.content.SharedPreferences.Editor ed=getSharedPreferences(P,0).edit()
            .remove("token").remove("activated_email").remove("control_token").remove("activation_code")
            .remove("installation_id").remove("company_name").remove("company_domain").remove("license_key")
            .putString("requested_site_url",normalized)
            .putString("site_web_url",normalized);
          if(activationComplete)ed.putString("site_api",apiBase); else ed.remove("site_api");
          ed.apply();
          JSONObject out=new JSONObject();
          out.put("ok",true);out.put("siteUrl",normalized);out.put("apiBase",apiBase);
          out.put("activationComplete",activationComplete);out.put("licenseActive",st.optBoolean("licenseActive",false));
          out.put("ready",st.optBoolean("ready",false));out.put("company",st.optString("company",""));
          out.put("installationId",st.optString("installationId",""));
          sendJs("site-status",new Resp(200,out.toString()));
        }catch(Exception e){sendJs("site-status",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible conectar con esta dirección.\"}"));}
      }}).start();
    }

    void activateCodeOnly(final String code){
      // Limpia por completo cualquier empresa anterior antes de validar otra licencia.
      getSharedPreferences(P,0).edit()
        .remove("token").remove("activated_email")
        .remove("site_api").remove("control_token")
        .remove("activation_code").remove("installation_id")
        .remove("company_name").remove("company_domain").remove("license_key")
        .apply();
      new Thread(new Runnable(){ public void run(){
        try{
          JSONObject act=new JSONObject();
          act.put("accessCode",code);
          act.put("siteUrl",getSharedPreferences(P,0).getString("requested_site_url",""));
          act.put("email","");
          act.put("deviceId",getDeviceId());
          act.put("deviceName","Android");
          act.put("appVersion","3.3.7");
          appendDiagnostic("activation_start","Validando licencia/codigo");
          Resp ar=http(MASTER+"activate","POST",act.toString(),"");
          appendDiagnostic("activation_http","HTTP "+ar.code+" / activate");
          if(ar.code<200||ar.code>=300){sendJs("activate",ar);return;}
          JSONObject aj=new JSONObject(ar.body);
          if(!aj.optBoolean("ok",false)){sendJs("activate",ar);return;}
          String controlToken=aj.optString("token","");
          JSONObject inst=aj.optJSONObject("installation");
          String base=inst==null?"":inst.optString("url","");
          String installationId=inst==null?"":inst.optString("id","");
          String companyName=inst==null?"":inst.optString("company","");
          String companyDomain=inst==null?"":inst.optString("domain","");
          String licenseKey=inst==null?"":inst.optString("licenseKey","");
          if(base.isEmpty()||controlToken.isEmpty()){
            sendJs("activate",new Resp(502,"{\"ok\":false,\"message\":\"No se pudo identificar la empresa.\"}"));
            return;
          }
          String requestedSite=getSharedPreferences(P,0).getString("requested_site_url","");
          String wantedHost=hostOf(requestedSite),actualHost=hostOf(base);
          if(!requestedSite.isEmpty()&&(wantedHost.isEmpty()||actualHost.isEmpty()||!wantedHost.equals(actualHost))){
            appendDiagnostic("activation_mismatch","La dirección indicada no coincide con la empresa devuelta por la licencia");
            sendJs("activate",new Resp(409,"{\"ok\":false,\"message\":\"La licencia activa no corresponde a la dirección indicada.\"}"));
            return;
          }
          if(!base.endsWith("/"))base+="/";
          String api=base+"wp-json/halcon-app/v1/";
          getSharedPreferences(P,0).edit()
            .remove("token").remove("activated_email")
            .putString("site_api",api)
            .putString("control_token",controlToken)
            .putString("activation_code",code)
            .putString("installation_id",installationId)
            .putString("company_name",companyName)
            .putString("company_domain",companyDomain)
            .putString("license_key",licenseKey)
            .apply();
          sendJs("activate",ar);
        }catch(Exception e){
          sendJs("activate",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible validar la licencia.\"}"));
        }
      }}).start();
    }

    void resolveAccountNative(final String email){
      appendDiagnostic("email_lookup_start","Consultando cuenta dentro de la empresa activada");
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);
          String api=pref.getString("site_api","");
          String controlToken=pref.getString("control_token","");
          if(api.isEmpty()){
            appendDiagnostic("email_lookup_error","Falta empresa vinculada");
            sendJs("resolve",new Resp(400,"{\"ok\":false,\"message\":\"Primero selecciona la dirección de la empresa.\"}"));
            return;
          }
          JSONObject p=new JSONObject();
          p.put("email",email);
          p.put("controlToken",controlToken);
          p.put("installationId",pref.getString("installation_id",""));
          p.put("companyDomain",pref.getString("company_domain",""));
          p.put("licenseKey",pref.getString("license_key",""));
          p.put("accessCode",pref.getString("activation_code",""));
          p.put("deviceId",getDeviceId());
          p.put("deviceName","Android");
          Resp rr=http(api+"account-probe","POST",p.toString(),"");
          appendDiagnostic("email_lookup_http","HTTP "+rr.code+" / account-probe");
          if(rr.code>=200&&rr.code<300){
            try{
              JSONObject j=new JSONObject(rr.body);
              String tok=j.optString("token","");
              if(!tok.isEmpty())pref.edit().putString("token",tok).putString("activated_email",email).apply();
              JSONObject bind=new JSONObject();bind.put("email",email);try{http(MASTER+"bind-user","POST",bind.toString(),controlToken);}catch(Exception ignore){}
            }catch(Exception e){}
          }
          sendJs("resolve",rr);
        }catch(Exception e){
          appendDiagnostic("email_lookup_exception",e.getMessage()==null?"Error desconocido":e.getMessage());
          sendJs("resolve",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible consultar la cuenta.\"}"));
        }
      }}).start();
    }

    void loginWithStoredActivation(final String email,final String password){
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);
          String api=pref.getString("site_api","");
          String controlToken=pref.getString("control_token","");
          if(api.isEmpty()){
            sendJs("login",new Resp(400,"{\"ok\":false,\"message\":\"Primero selecciona la dirección de la empresa.\"}"));
            return;
          }
          JSONObject lp=new JSONObject();
          lp.put("email",email);
          lp.put("password",password);
          lp.put("controlToken",controlToken);
          lp.put("installationId",pref.getString("installation_id",""));
          lp.put("companyDomain",pref.getString("company_domain",""));
          lp.put("licenseKey",pref.getString("license_key",""));
          lp.put("accessCode",pref.getString("activation_code",""));
          lp.put("deviceId",getDeviceId());
          lp.put("deviceName","Android");
          Resp lr=http(api+"login","POST",lp.toString(),"");
          if(lr.code>=200&&lr.code<300){
            try{
              JSONObject lj=new JSONObject(lr.body);
              if(lj.has("token"))pref.edit().putString("token",lj.optString("token")).putString("activated_email",email).apply();
              JSONObject bind=new JSONObject();bind.put("email",email);try{http(MASTER+"bind-user","POST",bind.toString(),controlToken);}catch(Exception ignore){}
            }catch(Exception e){}
          }
          sendJs("login",lr);
        }catch(Exception e){
          sendJs("login",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible ingresar.\"}"));
        }
      }}).start();
    }

    void activateThenLogin(final String raw){
      try{
        JSONObject p=new JSONObject(raw);
        loginWithStoredActivation(p.optString("email",""),p.optString("password",""));
      }catch(Exception e){
        sendJs("login",new Resp(0,"{\"ok\":false,\"message\":\"Solicitud de acceso invalida.\"}"));
      }
    }

    void appendDiagnostic(String type,String message){
      try{
        android.content.SharedPreferences pref=getSharedPreferences(P,0);
        JSONArray rows;try{rows=new JSONArray(pref.getString(LOG_KEY,"[]"));}catch(Exception e){rows=new JSONArray();}
        JSONObject row=new JSONObject();
        row.put("at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date()));
        row.put("type",type==null?"event":type);
        String m=message==null?"":message;
        if(m.length()>1000)m=m.substring(0,1000);
        row.put("message",m);
        row.put("appVersion","3.3.7");
        row.put("deviceId",new Bridge().getDeviceId());
        JSONArray out=new JSONArray();int from=Math.max(0,rows.length()-118);for(int i=from;i<rows.length();i++)out.put(rows.opt(i));out.put(row);
        pref.edit().putString(LOG_KEY,out.toString()).apply();
      }catch(Exception e){}
    }

    JSONObject diagnosticPayload(String stage){
      JSONObject o=new JSONObject();
      try{
        android.content.SharedPreferences pref=getSharedPreferences(P,0);
        o.put("generatedAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date()));
        o.put("appVersion","3.3.7");
        o.put("stage",stage==null?"":stage);
        o.put("deviceId",new Bridge().getDeviceId());
        o.put("requestedSiteUrl",pref.getString("requested_site_url",""));
        o.put("siteApi",pref.getString("site_api",""));
        o.put("installationId",pref.getString("installation_id",""));
        o.put("companyName",pref.getString("company_name",""));
        o.put("companyDomain",pref.getString("company_domain",""));
        o.put("hasControlToken",!pref.getString("control_token","").isEmpty());
        o.put("hasSessionToken",!pref.getString("token","").isEmpty());
        o.put("events",new JSONArray(pref.getString(LOG_KEY,"[]")));
        JSONObject net=new JSONObject();net.put("master",MASTER);net.put("mode","independent-diagnostic");o.put("network",net);
      }catch(Exception e){}
      return o;
    }

    void downloadDiagnosticFile(final String stage){
      new Thread(new Runnable(){ public void run(){
        try{
          appendDiagnostic("diagnostic_download","Generando archivo local");
          byte[] data=diagnosticPayload(stage).toString(2).getBytes(StandardCharsets.UTF_8);
          String name="sistema-halcon-diagnostico-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json";
          if(Build.VERSION.SDK_INT>=29){
            ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.DISPLAY_NAME,name);cv.put(MediaStore.Downloads.MIME_TYPE,"application/json");cv.put(MediaStore.Downloads.RELATIVE_PATH,"Download/Sistema Halcon");
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(uri==null)throw new Exception("No se pudo crear el archivo");
            OutputStream os=getContentResolver().openOutputStream(uri);os.write(data);os.flush();os.close();
          }else{
            File dir=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File sub=new File(dir,"Sistema Halcon");if(!sub.exists())sub.mkdirs();
            File file=new File(sub,name);FileOutputStream os=new FileOutputStream(file);os.write(data);os.close();
          }
          final String js="alert("+q("Diagnóstico descargado. Revisa Descargas > Sistema Halcon.")+")";
          w.post(new Runnable(){public void run(){w.evaluateJavascript(js,null);}});
        }catch(Exception e){
          appendDiagnostic("diagnostic_download_error",e.getMessage());
          final String js="alert("+q("No fue posible descargar el diagnóstico.")+")";
          w.post(new Runnable(){public void run(){w.evaluateJavascript(js,null);}});
        }
      }}).start();
    }

    void sendDiagnosticNative(final String stage){
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);String control=pref.getString("control_token","");
          if(control.isEmpty()){sendJs("diagnostic-send",new Resp(401,"{\"ok\":false,\"message\":\"Primero valida la licencia. Mientras tanto puedes descargar el diagnóstico.\"}"));return;}
          JSONObject p=diagnosticPayload(stage);p.put("message","Diagnostico manual desde APK");
          p.put("installationId",pref.getString("installation_id",""));
          p.put("companyName",pref.getString("company_name",""));
          p.put("companyDomain",pref.getString("company_domain",""));
          Resp rr=http(MASTER+"diagnostic","POST",p.toString(),control);
          appendDiagnostic("diagnostic_send_http","HTTP "+rr.code+" / app/diagnostic");
          sendJs("diagnostic-send",rr);
        }catch(Exception e){
          appendDiagnostic("diagnostic_send_error",e.getMessage());
          sendJs("diagnostic-send",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible enviar el diagnóstico. Puedes descargarlo y compartirlo manualmente.\"}"));
        }
      }}).start();
    }

    void refreshControlNative(){
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);
          String control=pref.getString("control_token","");
          if(control.isEmpty())return;
          Resp mr=http(MASTER+"modules","GET","",control);
          Resp rr=http(MASTER+"messages","GET","",control);
          JSONObject out=new JSONObject();out.put("ok",true);
          if(mr.code>=200&&mr.code<300){try{JSONObject m=new JSONObject(mr.body);out.put("modules",m.optJSONArray("modules")==null?new JSONArray():m.optJSONArray("modules"));}catch(Exception e){out.put("modules",new JSONArray());}}
          if(rr.code>=200&&rr.code<300){try{JSONObject m=new JSONObject(rr.body);out.put("messages",m.optJSONArray("messages")==null?new JSONArray():m.optJSONArray("messages"));}catch(Exception e){out.put("messages",new JSONArray());}}
          appendDiagnostic("control_refresh","modules="+mr.code+" messages="+rr.code);
          sendJs("control-refresh",new Resp(200,out.toString()));
          JSONObject ping=new JSONObject();ping.put("appVersion","3.3.7");ping.put("deviceName","Android");
          try{http(MASTER+"ping","POST",ping.toString(),control);}catch(Exception e){}
        }catch(Exception e){appendDiagnostic("control_refresh_error",e.getMessage()==null?"error":e.getMessage());}
      }}).start();
    }

    void markControlMessageReadNative(final String id){
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);String control=pref.getString("control_token","");
          if(control.isEmpty())return;
          Resp rr=http(MASTER+"messages/"+URLEncoder.encode(id,"UTF-8")+"/read","POST","{}",control);
          sendJs("message-read",rr);
        }catch(Exception e){appendDiagnostic("message_read_error",e.getMessage()==null?"error":e.getMessage());}
      }}).start();
    }

    void openWebAppNative(final String module){
      new Thread(new Runnable(){ public void run(){
        try{
          android.content.SharedPreferences pref=getSharedPreferences(P,0);
          String api=pref.getString("site_api","");
          String token=pref.getString("token","");
          if(api.isEmpty()||token.isEmpty()){
            sendJs("web-session",new Resp(401,"{\"ok\":false,\"message\":\"Sesión de aplicación no disponible.\"}"));
            return;
          }
          String mod=(module==null||module.trim().isEmpty())?"quote":module.trim();
          Resp rr=http(api+"web-session?module="+URLEncoder.encode(mod,"UTF-8"),"GET","",token);
          appendDiagnostic("web_session_http","HTTP "+rr.code+" / web-session / "+mod);
          if(rr.code<200||rr.code>=300){sendJs("web-session",rr);return;}
          JSONObject out=new JSONObject(rr.body);
          final String url=out.optString("url","");
          if(url.isEmpty()){sendJs("web-session",new Resp(502,"{\"ok\":false,\"message\":\"El Núcleo no entregó la vista de aplicación.\"}"));return;}
          w.post(new Runnable(){ public void run(){ w.loadUrl(url); }});
        }catch(Exception e){
          appendDiagnostic("web_session_error",e.getMessage()==null?"error":e.getMessage());
          sendJs("web-session",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible abrir el sistema real. Verifica Núcleo 6.9.6 o superior.\"}"));
        }
      }}).start();
    }

    void sendJs(final String name,final Resp r){
      final String js="window.__api&&window.__api("+q(name)+","+r.code+","+q(r.body)+")";
      w.post(new Runnable(){ public void run(){w.evaluateJavascript(js,null);}});
    }

    Resp http(String url,String method,String body,String bearer)throws Exception{
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(20000);
      c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json; charset=utf-8");
      if(bearer!=null&&!bearer.isEmpty())c.setRequestProperty("Authorization","Bearer "+bearer);
      if("POST".equals(method)){c.setDoOutput(true);OutputStream os=c.getOutputStream();os.write((body==null?"{}":body).getBytes(StandardCharsets.UTF_8));os.flush();os.close();}
      int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();return new Resp(code,read(in));
    }

    void api(final String name,final String raw,final boolean auth){
      new Thread(new Runnable(){
        public void run(){
          try{
            Resp r=request(name,raw==null?"{}":raw,auth);
            final String js="window.__api&&window.__api("+q(name)+","+r.code+","+q(r.body)+")";
            w.post(new Runnable(){ public void run(){ w.evaluateJavascript(js,null); }});
          }catch(Exception e){
            final String body="{\"ok\":false,\"message\":"+JSONObject.quote(e.getMessage()==null?"Error de conexión":e.getMessage())+"}";
            final String js="window.__api&&window.__api("+q(name)+",0,"+q(body)+")";
            w.post(new Runnable(){ public void run(){ w.evaluateJavascript(js,null); }});
          }
        }
      }).start();
    }

    Resp request(String path,String body,boolean auth)throws Exception{
      boolean post=path.equals("login")||path.equals("change-password")||path.equals("forgot-password")||path.equals("quotes");
      String site=getSharedPreferences(P,0).getString("site_api","");if(site.isEmpty())throw new Exception("Empresa no vinculada");HttpURLConnection c=(HttpURLConnection)new URL(site+path).openConnection();
      c.setRequestMethod(post?"POST":"GET");
      c.setConnectTimeout(15000);
      c.setReadTimeout(20000);
      c.setRequestProperty("Accept","application/json");
      c.setRequestProperty("Content-Type","application/json; charset=utf-8");
      if(auth){
        String t=getToken();
        if(!t.isEmpty())c.setRequestProperty("Authorization","Bearer "+t);
      }
      if(post){
        c.setDoOutput(true);
        OutputStream os=c.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
      }
      int code=c.getResponseCode();
      InputStream in=code>=400?c.getErrorStream():c.getInputStream();
      String out=read(in);
      if(code>=200&&code<300){
        try{
          JSONObject j=new JSONObject(out);
          if(j.has("token"))getSharedPreferences(P,0).edit().putString("token",j.optString("token")).apply();
        }catch(Exception e){}
      }
      return new Resp(code,out);
    }

    String read(InputStream in)throws Exception{
      if(in==null)return "";
      BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
      StringBuilder s=new StringBuilder();
      String x;
      while((x=r.readLine())!=null)s.append(x);
      return s.toString();
    }
  }

  String q(String s){
    if(s==null)s="";
    return JSONObject.quote(s);
  }


  String fileExtension(String mime){
    if("application/pdf".equals(mime))return ".pdf";
    if("image/jpeg".equals(mime))return ".jpg";
    if("image/png".equals(mime))return ".png";
    if("image/webp".equals(mime))return ".webp";
    if("image/svg+xml".equals(mime))return ".svg";
    if("text/plain".equals(mime))return ".txt";
    if("application/json".equals(mime))return ".json";
    return "";
  }

  String cleanFileName(String raw,String mime){
    String name=(raw==null?"":raw).replaceAll("[^\\p{L}\\p{N}._ -]","_").trim();
    if(name.isEmpty())name="Documento-Halcon";
    if(name.length()>110)name=name.substring(0,110);
    String ext=fileExtension(mime);
    if(!name.toLowerCase(Locale.ROOT).endsWith(ext))name+=ext;
    return name;
  }

  boolean trustedDocumentForDownload(){
    final String[] current={""};
    final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
    w.post(()->{try{current[0]=w.getUrl();}catch(Exception ignored){}finally{latch.countDown();}});
    try{if(!latch.await(4,java.util.concurrent.TimeUnit.SECONDS))return false;}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
    String site=getSharedPreferences(P,0).getString("site_web_url","");
    if(site.isEmpty())site=getSharedPreferences(P,0).getString("requested_site_url","");
    try{
      Uri loaded=Uri.parse(current[0]),expected=Uri.parse(site);
      return "https".equalsIgnoreCase(loaded.getScheme()) &&
        loaded.getHost()!=null && loaded.getHost().equalsIgnoreCase(expected.getHost());
    }catch(Exception e){return false;}
  }

  String beginNativeFile(String rawName,String rawMime){
    String mime=rawMime==null?"":rawMime.trim().toLowerCase(Locale.ROOT);
    if("image/jpg".equals(mime))mime="image/jpeg";
    if(fileExtension(mime).isEmpty())return "error: Formato de descarga no autorizado.";
    if(!trustedDocumentForDownload())return "error: Esta pantalla no está autorizada para descargar archivos.";
    synchronized(fileLock){
      if(!pendingFiles.isEmpty())return "error: Espere a terminar la descarga anterior.";
      try{
        String id=java.util.UUID.randomUUID().toString();
        File file=File.createTempFile("halcon_download_",".part",getCacheDir());
        pendingFiles.put(id,new PendingFile(file,cleanFileName(rawName,mime),mime));
        return id;
      }catch(Exception e){return "error: No se pudo preparar la descarga.";}
    }
  }

  String appendNativeFile(String id,String chunk){
    synchronized(fileLock){
      PendingFile p=pendingFiles.get(id);
      if(p==null)return "error: Descarga desconocida.";
      try{
        if(chunk==null||chunk.isEmpty()||chunk.length()>700000)throw new IOException("Bloque inválido");
        byte[] bytes=android.util.Base64.decode(chunk,android.util.Base64.DEFAULT);
        if(p.file.length()+bytes.length>150L*1024L*1024L)throw new IOException("Archivo mayor que 150 MB");
        try(FileOutputStream out=new FileOutputStream(p.file,true)){out.write(bytes);}
        return "ok";
      }catch(Exception e){pendingFiles.remove(id);p.file.delete();return "error: "+e.getMessage();}
    }
  }

  String finishNativeFile(String id){
    final PendingFile p;
    synchronized(fileLock){p=pendingFiles.remove(id);}
    if(p==null)return "error: Descarga desconocida.";
    if(p.file.length()<12){p.file.delete();return "error: Archivo vacío o incompleto.";}
    new Thread(()->{
      try{
        if(!checkFileSignature(p.file,p.mime))throw new IOException("El archivo generado no coincide con su formato.");
        if(Build.VERSION.SDK_INT>=29){
          ContentValues values=new ContentValues();
          values.put(MediaStore.Downloads.DISPLAY_NAME,p.name);
          values.put(MediaStore.Downloads.MIME_TYPE,p.mime);
          values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/Sistema Halcon");
          values.put(MediaStore.Downloads.IS_PENDING,1);
          Uri target=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
          if(target==null)throw new IOException("Android no pudo crear el documento.");
          try{
            try(InputStream in=new FileInputStream(p.file);OutputStream out=getContentResolver().openOutputStream(target)){
              if(out==null)throw new IOException("Android no pudo abrir Descargas.");
              byte[] buffer=new byte[65536];int len;
              while((len=in.read(buffer))!=-1)out.write(buffer,0,len);
              out.flush();
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);
            getContentResolver().update(target,ready,null,null);
            notifyFileDownload(true,p.name,"Archivo guardado en Descargas / Sistema Halcón.");
          }catch(Exception e){getContentResolver().delete(target,null,null);throw e;}
          finally{p.file.delete();}
        }else{
          runOnUiThread(()->{
            if(legacyFile!=null){p.file.delete();notifyFileDownload(false,p.name,"Hay una descarga pendiente. Termine la anterior.");return;}
            legacyFile=p.file;legacyName=p.name;legacyMime=p.mime;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(p.mime);
            intent.putExtra(Intent.EXTRA_TITLE,p.name);
            try{startActivityForResult(intent,HALCON_CREATE_DOCUMENT);}
            catch(Exception e){legacyFile=null;p.file.delete();notifyFileDownload(false,p.name,"No fue posible abrir el selector de archivos.");}
          });
        }
      }catch(Exception e){p.file.delete();notifyFileDownload(false,p.name,"No fue posible descargar el archivo: "+e.getMessage());}
    }).start();
    return "queued";
  }

  void cancelNativeFile(String id){
    synchronized(fileLock){
      PendingFile p=pendingFiles.remove(id);
      if(p!=null)p.file.delete();
    }
  }

  boolean checkFileSignature(File file,String mime)throws IOException{
    byte[] bytes=new byte[12];int n;
    try(FileInputStream in=new FileInputStream(file)){n=in.read(bytes);}
    if(n<4)return false;
    if("application/pdf".equals(mime))return bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F';
    if("image/jpeg".equals(mime))return (bytes[0]&255)==255&&(bytes[1]&255)==216;
    if("image/png".equals(mime))return (bytes[0]&255)==137&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G';
    if("image/webp".equals(mime))return bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F';
    return true;
  }

  void notifyFileDownload(boolean ok,String name,String message){
    runOnUiThread(()->{
      Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();
      if(w!=null){
        String js="window.dispatchEvent(new CustomEvent('halcon-native-download',{detail:{ok:"+ok+
          ",name:"+JSONObject.quote(name)+",message:"+JSONObject.quote(message)+"}}));";
        w.evaluateJavascript(js,null);
      }
    });
  }

  void downloadHttpFile(String url,String userAgent,String disposition,String mime){
    try{
      Uri target=Uri.parse(url);
      String site=getSharedPreferences(P,0).getString("site_web_url","");
      if(site.isEmpty())site=getSharedPreferences(P,0).getString("requested_site_url","");
      Uri expected=Uri.parse(site);
      if(!"https".equalsIgnoreCase(target.getScheme())||target.getHost()==null||
        !target.getHost().equalsIgnoreCase(expected.getHost()))throw new IOException("Enlace de descarga no autorizado.");
      String fileName=WebUtilsFileName(url,disposition,mime);
      DownloadManager.Request req=new DownloadManager.Request(target);
      String cookie=CookieManager.getInstance().getCookie(url);
      if(cookie!=null&&!cookie.isEmpty())req.addRequestHeader("Cookie",cookie);
      if(userAgent!=null&&!userAgent.isEmpty())req.addRequestHeader("User-Agent",userAgent);
      req.setTitle(fileName);
      req.setDescription("Sistema Halcón");
      req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"Sistema Halcon/"+fileName);
      DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
      dm.enqueue(req);
      Toast.makeText(this,"Descargando archivo…",Toast.LENGTH_SHORT).show();
    }catch(Exception e){notifyFileDownload(false,"Archivo",e.getMessage()==null?"Descarga no disponible.":e.getMessage());}
  }

  String WebUtilsFileName(String url,String disposition,String mime){
    String suggested=URLUtil.guessFileName(url,disposition,mime);
    String extension=fileExtension(mime==null?"":mime.toLowerCase(Locale.ROOT));
    String name=(suggested==null?"Archivo":suggested).replaceAll("[^\\p{L}\\p{N}._ -]","_");
    if(name.isEmpty())name="Archivo";
    if(name.length()>110)name=name.substring(0,110);
    if(!extension.isEmpty()&&!name.toLowerCase(Locale.ROOT).endsWith(extension))name+=extension;
    return name;
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode!=HALCON_CREATE_DOCUMENT)return;
    File file=legacyFile;String name=legacyName;
    legacyFile=null;legacyName="";legacyMime="";
    if(file==null)return;
    try{
      if(resultCode!=RESULT_OK||data==null||data.getData()==null){
        notifyFileDownload(false,name,"Se canceló la descarga.");return;
      }
      try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(data.getData())){
        if(out==null)throw new IOException("No se pudo abrir el documento.");
        byte[] bytes=new byte[65536];int len;
        while((len=in.read(bytes))!=-1)out.write(bytes,0,len);
        out.flush();
      }
      notifyFileDownload(true,name,"Archivo guardado correctamente.");
    }catch(Exception e){notifyFileDownload(false,name,"No se pudo guardar el archivo.");}
    finally{file.delete();}
  }

  @Override public void onBackPressed(){
    if(w!=null && w.canGoBack())w.goBack();
    else super.onBackPressed();
  }
}