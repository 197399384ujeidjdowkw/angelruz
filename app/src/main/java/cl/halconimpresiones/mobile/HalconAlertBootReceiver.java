package cl.halconimpresiones.mobile;
import android.content.*;
public final class HalconAlertBootReceiver extends BroadcastReceiver{
  @Override public void onReceive(Context c,Intent i){
    HalconAlertRuntime.ensureChannels(c);HalconAlertRuntime.rescheduleStored(c);HalconAlertRuntime.schedulePeriodicSync(c);HalconAlertRuntime.syncNow(c);
  }
}