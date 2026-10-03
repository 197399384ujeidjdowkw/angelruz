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
  static final String MASTER="https://halconimpresiones.cl/wp-json/halcon-control/v3/app/";
  static final String P="halcon";
  static final String LOG_KEY="diagnostic_events";

  public void onCreate(Bundle b){
    super.onCreate(b);

    root=new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);

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
        if("http".equals(scheme)||"https".equals(scheme)||"whatsapp".equals(scheme)){
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
    handle(i);
  }

  void handle(Intent i){
    if(i==null)return;
    String a=i.getAction();
    if(Intent.ACTION_SEND.equals(a)||Intent.ACTION_SEND_MULTIPLE.equals(a)){
      String t=i.getStringExtra(Intent.EXTRA_TEXT);
      String sender=i.getStringExtra(Intent.EXTRA_SUBJECT);String meta=((t==null?"":t)+" "+(sender==null?"":sender)).toLowerCase();String source="compartido";
      try{Uri ref=getReferrer();if(ref!=null)meta+=" "+String.valueOf(ref.getHost()).toLowerCase();}catch(Exception e){}
      if(meta.contains("telegram")||meta.contains("t.me"))source="telegram";else if(meta.contains("messenger")||meta.contains("m.me")||meta.contains("facebook"))source="messenger";else if(meta.contains("instagram"))source="instagram";else if(meta.contains("tiktok"))source="tiktok";else if(meta.contains("whatsapp")||meta.contains("wa.me"))source="whatsapp";
      final String js="window.__incoming&&window.__incoming("+q(t==null?"":t)+",1,"+q(source)+","+q(sender==null?"":sender)+")";
      w.postDelayed(new Runnable(){ public void run(){ w.evaluateJavascript(js,null); }},700);
    }
  }

  class Resp{
    int code; String body;
    Resp(int c,String b){code=c;body=b;}
  }

  class Bridge{
    @JavascriptInterface public String getToken(){return getSharedPreferences(P,0).getString("token","");}
    @JavascriptInterface public String getDeviceId(){
      String x=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
      return x==null?"android":x;
    }
    @JavascriptInterface public void activateCode(String code){activateCodeOnly(code);}
    @JavascriptInterface public void resolveAccount(String email){resolveAccountNative(email);}
    @JavascriptInterface public void loginExisting(String email,String password){loginWithStoredActivation(email,password);}
    @JavascriptInterface public void login(String raw){activateThenLogin(raw);}
    @JavascriptInterface public void changePassword(String raw){api("change-password",raw,true);}
    @JavascriptInterface public void bootstrap(){api("bootstrap","{}",true);}
    @JavascriptInterface public void logout(){getSharedPreferences(P,0).edit().clear().apply();}
    @JavascriptInterface public void forgot(String email){ centralPost("forgot",email); }
    @JavascriptInterface public void help(String email){ centralPost("help",email); }
    @JavascriptInterface public void openSupport(int index){ openSupportContact(index); }
    @JavascriptInterface public void save(String raw){api("quotes",raw,true);}
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

    void activateCodeOnly(final String code){
      new Thread(new Runnable(){ public void run(){
        try{
          JSONObject act=new JSONObject();
          act.put("accessCode",code);
          act.put("email","");
          act.put("deviceId",getDeviceId());
          act.put("deviceName","Android");
          act.put("appVersion","3.1.0");
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
          if(api.isEmpty()||controlToken.isEmpty()){
            appendDiagnostic("email_lookup_error","Falta empresa activada");
            sendJs("resolve",new Resp(400,"{\"ok\":false,\"message\":\"Primero valida la licencia.\"}"));
            return;
          }
          JSONObject p=new JSONObject();
          p.put("email",email);
          p.put("controlToken",controlToken);
          p.put("installationId",pref.getString("installation_id",""));
          p.put("companyDomain",pref.getString("company_domain",""));
          p.put("deviceId",getDeviceId());
          p.put("deviceName","Android");
          Resp rr=http(api+"account-probe","POST",p.toString(),"");
          appendDiagnostic("email_lookup_http","HTTP "+rr.code+" / account-probe");
          if(rr.code>=200&&rr.code<300){
            try{
              JSONObject j=new JSONObject(rr.body);
              String tok=j.optString("token","");
              if(!tok.isEmpty())pref.edit().putString("token",tok).putString("activated_email",email).apply();
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
          if(api.isEmpty()||controlToken.isEmpty()){
            sendJs("login",new Resp(400,"{\"ok\":false,\"message\":\"Primero valida la licencia.\"}"));
            return;
          }
          JSONObject lp=new JSONObject();
          lp.put("email",email);
          lp.put("password",password);
          lp.put("controlToken",controlToken);
          lp.put("installationId",pref.getString("installation_id",""));
          lp.put("companyDomain",pref.getString("company_domain",""));
          lp.put("deviceId",getDeviceId());
          lp.put("deviceName","Android");
          Resp lr=http(api+"login","POST",lp.toString(),"");
          if(lr.code>=200&&lr.code<300){
            try{
              JSONObject lj=new JSONObject(lr.body);
              if(lj.has("token"))pref.edit().putString("token",lj.optString("token")).putString("activated_email",email).apply();
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
        row.put("appVersion","3.1.0");
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
        o.put("appVersion","3.1.0");
        o.put("stage",stage==null?"":stage);
        o.put("deviceId",new Bridge().getDeviceId());
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
          JSONObject ping=new JSONObject();ping.put("appVersion","3.1.0");ping.put("deviceName","Android");
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

  @Override public void onBackPressed(){
    if(w!=null && w.canGoBack())w.goBack();
    else super.onBackPressed();
  }
}