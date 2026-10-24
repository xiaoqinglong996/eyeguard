package com.eyeguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机自启动：若用户开启自启动且退出前正在计时，则在开机后自动恢复服务。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs p = new Prefs(c);
        if (p.getBool(Prefs.KEY_AUTO_START, false) && p.getBool(Prefs.KEY_TIMING, false)) {
            Intent s = new Intent(c, EyeGuardService.class);
            s.setAction(EyeGuardService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        }
    }
}
