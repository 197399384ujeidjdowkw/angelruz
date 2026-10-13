package cl.halconimpresiones.mobile;
import android.content.*;
public final class HalconAlertSyncReceiver extends BroadcastReceiver{
  @Override public void onReceive(Context c,Intent i){
    final PendingResult p=goAsync();final Context app=c.getApplicationContext();
    new Thread(()->{try{HalconAlertRuntime.syncBlocking(app);}finally{p.finish();}},"halcon-alert-receiver").start();
  }
}