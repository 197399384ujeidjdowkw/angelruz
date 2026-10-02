package cl.halconimpresiones.mobile;
import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.webkit.*;import android.provider.Settings;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class MainActivity extends Activity{
 WebView w; static final String API="https://halconimpresiones.cl/wp-json/halcon-app/v1/"; static final String P="halcon"; 
 public void onCreate(Bundle b){super.onCreate(b);w=new WebView(this);w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);w.addJavascriptInterface(new Bridge(),"HalconNative");w.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String s=r.getUrl().getScheme();if("http".equals(s)||"https".equals(s)||"whatsapp".equals(s)){try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception e){}return true;}return false;}});w.loadUrl("file:///android_asset/index.html");handle(getIntent());}
 protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);}
 void handle(Intent i){if(i==null)return;String a=i.getAction();if(Intent.ACTION_SEND.equals(a)||Intent.ACTION_SEND_MULTIPLE.equals(a)){String t=i.getStringExtra(Intent.EXTRA_TEXT);String js="window.__incoming&&window.__incoming("+q(t==null?"":t)+",1)";w.postDelayed(()->w.evaluateJavascript(js,null),500);}}
 class Bridge{
  @JavascriptInterface public String getToken(){return getSharedPreferences(P,0).getString("token","");}
  @JavascriptInterface public String getDeviceId(){String x=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);return x==null?"android":x;}
  @JavascriptInterface public void login(String raw){api("login",raw,false);}
  @JavascriptInterface public void changePassword(String raw){api("change-password",raw,true);}
  @JavascriptInterface public void bootstrap(){api("bootstrap","",true);}
  @JavascriptInterface public void logout(){getSharedPreferences(P,0).edit().clear().apply();}
  @JavascriptInterface public void forgot(String email){new Thread(()->{try{JSONObject o=new JSONObject();o.put("email",email);post("forgot-password",o.toString(),false);}catch(Exception e){}}).start();}
  @JavascriptInterface public void save(String raw){ }
  @JavascriptInterface public void share(String kind){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Sistema Halcón - "+kind);startActivity(Intent.createChooser(i,"Compartir"));}catch(Exception e){}}
  void api(String name,String raw,boolean auth){new Thread(()->{try{String body=raw;if(body.isEmpty())body="{}";String r=post(name,body,auth);w.post(()->w.evaluateJavascript("window.__api&&window.__api("+q(name)+","+q(r)+")",null));}catch(Exception e){w.post(()->w.evaluateJavascript("window.__api&&window.__api("+q(name)+",0,"+q("{\"ok\":false,\"message\":\""+e.getMessage()+"\"}")+")",null));}}).start();}
  String post(String path,String body,boolean auth)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod("POST".equals(path)||path.equals("login")||path.equals("change-password")||path.equals("forgot-password")?"POST":"GET");c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Content-Type","application/json");if(auth){String t=getToken();if(!t.isEmpty())c.setRequestProperty("Authorization","Bearer "+t);}c.setDoOutput(true);c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String out=read(in);if(code>=200&&code<300){try{JSONObject j=new JSONObject(out);if(j.has("token"))getSharedPreferences(P,0).edit().putString("token",j.optString("token")).apply();}catch(Exception e){}}return code+"|"+out;}
  String read(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x);return s.toString();}
 }
 String getToken(){return getSharedPreferences(P,0).getString("token","");}
 String q(String s){if(s==null)s="";return JSONObject.quote(s);}
 public void onBackPressed(){if(w.canGoBack())w.goBack();else super.onBackPressed();}
}