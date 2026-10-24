package com.eyeguard.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private Prefs prefs;
    private TextView tvCurrent, tvCumulative, tvRest;
    private boolean notifRequested = false;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (EyeGuardService.ACTION_TIME_UPDATE.equals(i.getAction())) {
                long cur = i.getLongExtra(EyeGuardService.EXTRA_CURRENT, 0);
                long cum = i.getLongExtra(EyeGuardService.EXTRA_CUMULATIVE, 0);
                long rest = i.getLongExtra(EyeGuardService.EXTRA_REST, 0);
                tvCurrent.setText(fmt(cur));
                tvCumulative.setText(fmt(cum));
                tvRest.setText(fmt(rest));
            }
        }
    };

    public static String fmt(long ms) {
        long s = ms / 1000;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        return String.format(Locale.CHINA, "%02d:%02d:%02d", h, m, s);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 若上次启动发生过崩溃，跳转到全屏错误页（避免反复闪退，并方便用户反馈）
        SharedPreferences sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        String lastCrash = sp.getString(CrashApplication.KEY_LAST_CRASH, null);
        if (lastCrash != null && !lastCrash.isEmpty()) {
            sp.edit().remove(CrashApplication.KEY_LAST_CRASH).apply();
            Intent ci = new Intent(this, CrashActivity.class);
            ci.putExtra(CrashApplication.EXTRA_TRACE, lastCrash);
            startActivity(ci);
            finish();
            return;
        }
        try {
            initUI();
        } catch (Throwable t) {
            Intent ci = new Intent(this, CrashActivity.class);
            ci.putExtra(CrashApplication.EXTRA_TRACE, CrashApplication.buildTrace(t));
            startActivity(ci);
            finish();
        }
    }

    private void initUI() {
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        tvCurrent = findViewById(R.id.tv_current);
        tvCumulative = findViewById(R.id.tv_cumulative);
        tvRest = findViewById(R.id.tv_rest);

        applySettings();

        Button btnStart = findViewById(R.id.btn_start);
        Button btnPause = findViewById(R.id.btn_pause);
        Button btnExit = findViewById(R.id.btn_exit);
        Button btnSettings = findViewById(R.id.btn_settings);

        btnStart.setOnClickListener(v -> startTiming());
        btnPause.setOnClickListener(v -> pauseTiming());
        btnExit.setOnClickListener(v -> exitApp());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        tvCurrent.setText(fmt(0));
        tvCumulative.setText(fmt(prefs.getLong(Prefs.KEY_CUMULATIVE, 0)));
        tvRest.setText(fmt(prefs.getLong(Prefs.KEY_REST, 0)));

        requestNotificationsIfNeeded();
    }

    private void applySettings() {
        int fs = prefs.getInt(Prefs.KEY_FONT_SIZE, 18);
        int fc = prefs.getInt(Prefs.KEY_FONT_COLOR, 0xFF000000);
        for (TextView t : new TextView[]{tvCurrent, tvCumulative, tvRest}) {
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fs);
            t.setTextColor(fc);
        }
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !notifRequested) {
            notifRequested = true;
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void startTiming() {
        // 悬浮窗权限在使用全屏提醒前必须授予；首次开始时引导用户开启（不再于启动时打扰）
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "请允许“显示在其他应用上层”权限，否则休息提醒无法全屏弹出",
                    Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, 100);
            } catch (Exception ignore) { /* 个别系统无此入口则忽略 */ }
        }
        try {
            Intent i = new Intent(this, EyeGuardService.class);
            i.setAction(EyeGuardService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) { /* 忽略 */ }
        prefs.putBool(Prefs.KEY_TIMING, true);
        prefs.putBool(Prefs.KEY_PAUSED, false);
        ensureBatteryOptimizationOff();
        Toast.makeText(this, "已开始护眼计时", Toast.LENGTH_SHORT).show();
    }

    private void pauseTiming() {
        try {
            Intent i = new Intent(this, EyeGuardService.class);
            i.setAction(EyeGuardService.ACTION_PAUSE);
            startService(i);
        } catch (Exception e) { /* 忽略 */ }
        prefs.putBool(Prefs.KEY_PAUSED, true);
        Toast.makeText(this, "已暂停", Toast.LENGTH_SHORT).show();
    }

    private void exitApp() {
        try {
            Intent i = new Intent(this, EyeGuardService.class);
            i.setAction(EyeGuardService.ACTION_EXIT);
            startService(i);
        } catch (Exception e) { /* 忽略 */ }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 400);
    }

    private void ensureBatteryOptimizationOff() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception e) { /* 部分机型无此设置，忽略 */ }
    }

    @Override
    protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (rq == 100 && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "未授予悬浮窗权限，休息提醒将无法全屏弹出", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySettings();
        // 向服务索取当前状态，刷新界面
        try {
            Intent i = new Intent(this, EyeGuardService.class);
            i.setAction(EyeGuardService.ACTION_REQUEST_STATE);
            startService(i);
        } catch (Exception e) { /* 服务未运行等情况忽略 */ }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(EyeGuardService.ACTION_TIME_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(timeReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timeReceiver, f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(timeReceiver); } catch (Exception e) { /* ignore */ }
    }
}
