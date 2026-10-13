package cl.halconimpresiones.mobile;
import android.app.*;
import android.content.*;
import android.os.Build;
public final class HalconAppointmentReceiver extends BroadcastReceiver{
  @Override public void onReceive(Context c,Intent i){
    if(i==null)return;HalconAlertRuntime.ensureChannels(c);
    String id=i.getStringExtra("halcon_alert_id"),title=i.getStringExtra("halcon_title"),body=i.getStringExtra("halcon_body"),route=i.getStringExtra("halcon_route"),appointment=i.getStringExtra("halcon_appointment");
    Intent launch=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());if(launch==null)return;
    launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);launch.putExtra("halcon_route",route==null||route.isEmpty()?"appointments":route);launch.putExtra("halcon_appointment",appointment==null?"":appointment);launch.putExtra("halcon_from_notification",true);
    PendingIntent pi=PendingIntent.getActivity(c,HalconAlertRuntime.stable("open:"+(id==null?appointment:id)),launch,HalconAlertRuntime.flags(PendingIntent.FLAG_UPDATE_CURRENT));
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"halcon_appointments"):new Notification.Builder(c);
    b.setSmallIcon(HalconAlertRuntime.icon(c)).setContentTitle(title==null||title.isEmpty()?"Cita próxima":title).setContentText(body==null?"":body).setStyle(new Notification.BigTextStyle().bigText(body==null?"":body)).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_HIGH);
    if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);
    NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(HalconAlertRuntime.stable(id==null?"appointment":id),b.build());
  }
}