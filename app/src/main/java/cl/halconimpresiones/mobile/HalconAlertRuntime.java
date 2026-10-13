package cl.halconimpresiones.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class HalconAlertRuntime {
  static final String PREFS="halcon_native_alerts_v2";
  static final String KEY_REST="rest_url", KEY_TOKEN="rest_token", KEY_SCHEDULES="appointment_schedules", KEY_IDS="scheduled_ids", KEY_SEEN="event_seen", KEY_NAV="pending_navigation";
  static final int REQUEST_NOTIFICATIONS=7319;
  static final long SYNC_INTERVAL_MS=15L*60L*1000L;
  private final Activity activity; private final WebView webView; private final Bridge bridge;

  private HalconAlertRuntime(Activity a,WebView w){activity=a;webView=w;bridge=new Bridge();}
  public static HalconAlertRuntime attach(Activity a,WebView w){
    HalconAlertRuntime r=new HalconAlertRuntime(a,w);w.addJavascriptInterface(r.bridge,"HalconAlerts");
    ensureChannels(a);schedulePeriodicSync(a);r.handleIntent(a.getIntent());return r;
  }
  public void onPageReady(){deliverPendingNavigation();}
  public void handleIntent(Intent i){
    if(i==null)return;String route=i.getStringExtra("halcon_route"), ap=i.getStringExtra("halcon_appointment");
    if((route==null||route.isEmpty())&&(ap==null||ap.isEmpty()))return;
    try{JSONObject n=new JSONObject();n.put("tab",route==null||route.isEmpty()?"home":route);n.put("appointmentId",ap==null?"":ap);prefs(activity).edit().putString(KEY_NAV,n.toString()).apply();}catch(Exception ignored){}
    deliverPendingNavigation();
  }
  void deliverPendingNavigation(){
    String raw=prefs(activity).getString(KEY_NAV,"");if(raw==null||raw.isEmpty())return;
    try{JSONObject n=new JSONObject(raw);String tab=n.optString("tab","home"),ap=n.optString("appointmentId","");
      String js="(function(){try{return !!(window.__halconNativeOpen&&window.__halconNativeOpen("+JSONObject.quote(tab)+","+JSONObject.quote(ap)+"));}catch(e){return false;}})()";
      webView.post(()->webView.evaluateJavascript(js,v->{if("true".equalsIgnoreCase(String.valueOf(v)))prefs(activity).edit().remove(KEY_NAV).apply();}));
    }catch(Exception ignored){}
  }

  public final class Bridge {
    @JavascriptInterface public void setAlertSession(String rest,String token){
      if(rest==null||token==null||rest.trim().isEmpty()||token.trim().isEmpty())return;
      prefs(activity).edit().putString(KEY_REST,rest.trim()).putString(KEY_TOKEN,token.trim()).apply();
      requestPermission(activity);ensureChannels(activity);schedulePeriodicSync(activity);syncNow(activity);
    }
    @JavascriptInterface public void refreshAlerts(){requestPermission(activity);schedulePeriodicSync(activity);syncNow(activity);}
    @JavascriptInterface public void notify(String title,String body){showEventNotification(activity,"general:"+System.currentTimeMillis(),title,body,"home","general");}
    @JavascriptInterface public String consumePendingNavigation(){String x=prefs(activity).getString(KEY_NAV,"");prefs(activity).edit().remove(KEY_NAV).apply();return x==null?"":x;}
  }

  static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
  static void requestPermission(Activity a){if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)try{a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);}catch(Exception ignored){}}
  static void ensureChannels(Context c){
    if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
    channel(nm,"halcon_appointments","Agenda y citas");channel(nm,"halcon_stock","Stock bajo");channel(nm,"halcon_quotes","Cotizador Online");channel(nm,"halcon_system","Mensajes de Sistema Halcón");channel(nm,"halcon_general","Sistema Halcón");
  }
  static void channel(NotificationManager nm,String id,String name){
    if(nm.getNotificationChannel(id)!=null)return;NotificationChannel c=new NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH);
    c.setDescription("Avisos de Sistema Halcón");c.enableVibration(true);c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(c);
  }
  static int icon(Context c){int i=c.getApplicationInfo().icon;return i==0?android.R.drawable.ic_dialog_info:i;}
  static int flags(int f){return Build.VERSION.SDK_INT>=23?f|PendingIntent.FLAG_IMMUTABLE:f;}
  static int stable(String s){int h=s==null?1:s.hashCode();if(h==Integer.MIN_VALUE)h=1;return Math.abs(h==0?1:h);}

  static void schedulePeriodicSync(Context c){
    SharedPreferences p=prefs(c);if(p.getString(KEY_REST,"").isEmpty()||p.getString(KEY_TOKEN,"").isEmpty())return;
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
    Intent i=new Intent(c,HalconAlertSyncReceiver.class).setAction("cl.halconimpresiones.mobile.HALCON_ALERT_SYNC");
    PendingIntent pi=PendingIntent.getBroadcast(c,7201,i,flags(PendingIntent.FLAG_UPDATE_CURRENT));
    am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+60000L,SYNC_INTERVAL_MS,pi);
  }
  static void syncNow(Context c){Context app=c.getApplicationContext();new Thread(()->syncBlocking(app),"halcon-alert-sync").start();}
  static void syncBlocking(Context c){
    SharedPreferences p=prefs(c);String rest=p.getString(KEY_REST,""),token=p.getString(KEY_TOKEN,"");if(rest.isEmpty()||token.isEmpty())return;
    HttpURLConnection h=null;try{
      h=(HttpURLConnection)new URL(rest).openConnection();h.setRequestMethod("GET");h.setConnectTimeout(12000);h.setReadTimeout(15000);h.setRequestProperty("Accept","application/json");h.setRequestProperty("X-Halcon-Mobile-Alert-Token",token);
      int code=h.getResponseCode();if(code==401)return;InputStream in=code>=200&&code<300?h.getInputStream():h.getErrorStream();String raw=read(in);if(code<200||code>=300||raw.isEmpty())return;
      JSONObject root=new JSONObject(raw);JSONArray ap=root.optJSONArray("appointmentSchedules"),ev=root.optJSONArray("eventAlerts");if(ap==null)ap=new JSONArray();if(ev==null)ev=new JSONArray();
      p.edit().putString(KEY_SCHEDULES,ap.toString()).apply();reconcileAppointments(c,ap);reconcileEvents(c,ev);
    }catch(Exception ignored){}finally{if(h!=null)h.disconnect();}
  }
  static String read(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String x;while((x=r.readLine())!=null)b.append(x);r.close();return b.toString();}

  static void reconcileEvents(Context c,JSONArray events){
    ensureChannels(c);SharedPreferences p=prefs(c);JSONObject seen;try{seen=new JSONObject(p.getString(KEY_SEEN,"{}"));}catch(Exception e){seen=new JSONObject();}
    JSONObject next=new JSONObject();long now=System.currentTimeMillis();int shown=0;
    for(int i=0;i<events.length();i++){JSONObject row=events.optJSONObject(i);if(row==null)continue;String id=row.optString("id","").trim();if(id.isEmpty())continue;long old=seen.optLong(id,0L);try{next.put(id,old>0?old:now);}catch(Exception ignored){}
      if(old>0||shown>=12)continue;showEventNotification(c,id,row.optString("title","Sistema Halcón"),row.optString("body","Nuevo aviso"),row.optString("route","home"),row.optString("channel","system"));shown++;}
    Iterator<String> it=seen.keys();while(it.hasNext()){String id=it.next();long ts=seen.optLong(id,0L);if(ts>0&&now-ts<14L*24L*60L*60L*1000L&&!next.has(id))try{next.put(id,ts);}catch(Exception ignored){}}
    p.edit().putString(KEY_SEEN,next.toString()).apply();
  }
  static void showEventNotification(Context c,String id,String title,String body,String route,String channel){
    ensureChannels(c);String cid="halcon_system";if("stock".equals(channel))cid="halcon_stock";else if("quotes".equals(channel))cid="halcon_quotes";else if("general".equals(channel))cid="halcon_general";
    Intent launch=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());PendingIntent pi=null;
    if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);launch.putExtra("halcon_route",route==null||route.isEmpty()?"home":route);launch.putExtra("halcon_from_notification",true);pi=PendingIntent.getActivity(c,stable("open:"+id),launch,flags(PendingIntent.FLAG_UPDATE_CURRENT));}
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,cid):new Notification.Builder(c);b.setSmallIcon(icon(c)).setContentTitle(title==null||title.isEmpty()?"Sistema Halcón":title).setContentText(body==null?"":body).setStyle(new Notification.BigTextStyle().bigText(body==null?"":body)).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_HIGH);
    if(pi!=null)b.setContentIntent(pi);if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(stable(id),b.build());
  }

  static void reconcileAppointments(Context c,JSONArray rows){
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;SharedPreferences p=prefs(c);Set<String> previous=new HashSet<>();
    try{JSONObject old=new JSONObject(p.getString(KEY_IDS,"{}"));Iterator<String> k=old.keys();while(k.hasNext())previous.add(k.next());}catch(Exception ignored){}
    JSONObject next=new JSONObject();Set<String> incoming=new HashSet<>();long now=System.currentTimeMillis();
    for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;String id=r.optString("id","");long at=r.optLong("triggerEpochMs",0L);if(id.isEmpty()||at<=0)continue;incoming.add(id);int req=stable(id);try{next.put(id,req);}catch(Exception ignored){}
      if(at<now-300000L){cancelAppointment(c,am,req);continue;}Intent in=new Intent(c,HalconAppointmentReceiver.class);in.putExtra("halcon_alert_id",id);in.putExtra("halcon_title",r.optString("title","Cita próxima"));in.putExtra("halcon_body",r.optString("body","Recordatorio de agenda"));in.putExtra("halcon_route",r.optString("route","appointments"));in.putExtra("halcon_appointment",r.optString("appointmentId",""));
      PendingIntent pi=PendingIntent.getBroadcast(c,req,in,flags(PendingIntent.FLAG_UPDATE_CURRENT));long when=Math.max(now+500L,at);try{if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}catch(Exception e){try{am.set(AlarmManager.RTC_WAKEUP,when,pi);}catch(Exception ignored){}}}
    for(String id:previous)if(!incoming.contains(id))cancelAppointment(c,am,stable(id));p.edit().putString(KEY_IDS,next.toString()).apply();
  }
  static void cancelAppointment(Context c,AlarmManager am,int req){Intent i=new Intent(c,HalconAppointmentReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,req,i,flags(PendingIntent.FLAG_NO_CREATE));if(pi!=null){am.cancel(pi);pi.cancel();}}
  static void rescheduleStored(Context c){try{String x=prefs(c).getString(KEY_SCHEDULES,"[]");reconcileAppointments(c,new JSONArray(x==null||x.isEmpty()?"[]":x));}catch(Exception ignored){}}
}