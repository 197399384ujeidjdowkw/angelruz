package cl.halconimpresiones.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class MainActivity extends Activity {
  WebView w;
  FrameLayout root;
  View splash;
  static final String MASTER="https://halconimpresiones.cl/wp-json/halcon-control/v3/app/";
  static final String P="halcon";

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
        String scheme=r.getUrl().getScheme();
        if("http".equals(scheme)||"https".equals(scheme)||"whatsapp".equals(scheme)){
          try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception e){}
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
      final String js="window.__incoming&&window.__incoming("+q(t==null?"":t)+",1)";
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
    @JavascriptInterface public void login(String raw){activateThenLogin(raw);}
    @JavascriptInterface public void changePassword(String raw){api("change-password",raw,true);}
    @JavascriptInterface public void bootstrap(){api("bootstrap","{}",true);}
    @JavascriptInterface public void logout(){getSharedPreferences(P,0).edit().clear().apply();}
    @JavascriptInterface public void forgot(String email){ centralPost("forgot",email); }
    @JavascriptInterface public void help(String email){ centralPost("help",email); }
    @JavascriptInterface public void openSupport(int index){ openSupportContact(index); }
    @JavascriptInterface public void save(String raw){api("quotes",raw,true);}
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

    void activateThenLogin(final String raw){
      new Thread(new Runnable(){ public void run(){
        try{
          JSONObject p=new JSONObject(raw);String email=p.optString("email","");String password=p.optString("password","");String code=p.optString("controlToken","");
          JSONObject act=new JSONObject();act.put("accessCode",code);act.put("email",email);act.put("deviceId",getDeviceId());act.put("deviceName","Android");act.put("appVersion","3.0.2");
          Resp ar=http(MASTER+"activate","POST",act.toString(),"");
          if(ar.code<200||ar.code>=300){sendJs("login",ar);return;}
          JSONObject aj=new JSONObject(ar.body);if(!aj.optBoolean("ok",false)){sendJs("login",ar);return;}
          String controlToken=aj.optString("token","");JSONObject inst=aj.optJSONObject("installation");String base=inst==null?"":inst.optString("url","");
          if(base.isEmpty()||controlToken.isEmpty()){sendJs("login",new Resp(502,"{\"ok\":false,\"message\":\"No se pudo identificar la empresa.\"}"));return;}
          if(!base.endsWith("/"))base+="/";String api=base+"wp-json/halcon-app/v1/";
          getSharedPreferences(P,0).edit().putString("site_api",api).putString("control_token",controlToken).apply();
          JSONObject lp=new JSONObject();lp.put("email",email);lp.put("password",password);lp.put("controlToken",controlToken);lp.put("deviceId",getDeviceId());lp.put("deviceName","Android");
          Resp lr=http(api+"login","POST",lp.toString(),"");
          if(lr.code>=200&&lr.code<300){try{JSONObject lj=new JSONObject(lr.body);if(lj.has("token"))getSharedPreferences(P,0).edit().putString("token",lj.optString("token")).apply();}catch(Exception e){}}
          sendJs("login",lr);
        }catch(Exception e){sendJs("login",new Resp(0,"{\"ok\":false,\"message\":\"No fue posible activar el dispositivo.\"}"));}
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