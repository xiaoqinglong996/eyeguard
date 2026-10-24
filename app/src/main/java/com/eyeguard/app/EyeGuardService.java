package com.eyeguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * 护眼核心服务（前台服务）：
 * - 监听亮屏/息屏，仅亮屏时累计“本次已亮屏时间”。
 * - 息屏把本次计入“累计亮屏时间”，下次亮屏重新计时（单次独立记录）。
 * - 达到自定义间隔后弹出全屏悬浮窗提醒休息；确认后进入眼睛休息倒计时，
 *   倒计时结束自动关闭；未确认则 2 分钟后自动关闭。
 * - 眼睛休息时间单独累计并在主界面展示。
 */
public class EyeGuardService extends Service {

    public static final String ACTION_START = "com.eyeguard.START";
    public static final String ACTION_PAUSE = "com.eyeguard.PAUSE";
    public static final String ACTION_EXIT = "com.eyeguard.EXIT";
    public static final String ACTION_RELOAD = "com.eyeguard.RELOAD";
    public static final String ACTION_REQUEST_STATE = "com.eyeguard.REQ_STATE";
    public static final String ACTION_TIME_UPDATE = "com.eyeguard.TIME_UPDATE";
    public static final String EXTRA_CURRENT = "current";
    public static final String EXTRA_CUMULATIVE = "cumulative";
    public static final String EXTRA_REST = "rest";

    private static final int NOTIF_ID = 1;
    private static final String CHANNEL = "eyeguard_channel";
    private static final long AUTO_CANCEL_MS = 120000; // 未确认时 2 分钟自动关闭

    private Prefs prefs;
    private WindowManager wm;
    private PowerManager pm;
    private Handler handler;
    private View overlayView;
    private boolean overlayAdded = false;

    private boolean timing = false;
    private boolean paused = false;
    private boolean screenOn = false;
    private boolean popupShown = false;
    private boolean resting = false;
    private boolean reminded = false;
    private boolean tickerRunning = false;

    private long currentPeriodMs = 0;
    private long cumulativeMs = 0;
    private long restMs = 0;
    private long breakThresholdMs = 20 * 60000L;
    private long restDurationMs = 60000L;
    private long restRemainingMs = 0;
    private long delayMs = 5 * 60000L;   // 延迟提醒时长（设置可自定义，默认5分钟）

    private BroadcastReceiver screenReceiver;

    private final Runnable ticker = new Runnable() {
        @Override public void run() { tick(); }
    };
    private final Runnable autoCancel = new Runnable() {
        @Override public void run() {
            if (popupShown && !resting) removeOverlay();
        }
    };
    private final Runnable delayReminder = new Runnable() {
        @Override public void run() {
            // 延迟时间到：解除“已提醒”标记，下一拍重新弹出提醒
            reminded = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        cumulativeMs = prefs.getLong(Prefs.KEY_CUMULATIVE, 0);
        restMs = prefs.getLong(Prefs.KEY_REST, 0);

        createChannel();

        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) onScreenOn();
                else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) onScreenOff();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }

        screenOn = pm != null && pm.isInteractive();
        reloadSettings();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "护眼助手",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("护眼计时后台运行");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent it = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else b = new Notification.Builder(this);
        b.setContentTitle("护眼助手运行中")
         .setContentText("正在守护您的用眼健康")
         .setSmallIcon(R.mipmap.ic_notify)
         .setContentIntent(pi);
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // 系统回收后重新拉起：若之前正在计时则恢复，否则退出
            if (prefs.getBool(Prefs.KEY_TIMING, false)) startTiming();
            else stopSelf();
            return START_STICKY;
        }
        String a = intent.getAction();
        if (ACTION_START.equals(a)) startTiming();
        else if (ACTION_PAUSE.equals(a)) pauseTiming();
        else if (ACTION_EXIT.equals(a)) { exitAll(); return START_NOT_STICKY; }
        else if (ACTION_RELOAD.equals(a)) reloadSettings();
        else if (ACTION_REQUEST_STATE.equals(a)) broadcastTime();
        if (!timing) stopSelf(); // 非计时状态下收到查询/重载等，处理完即退出
        return START_STICKY;
    }

    private void reloadSettings() {
        int bm = prefs.getInt(Prefs.KEY_BREAK_MIN, 20);
        breakThresholdMs = (bm < 1 ? 1 : bm) * 60000L;
        int rm = prefs.getInt(Prefs.KEY_REST_MIN, 1);
        restDurationMs = (rm < 1 ? 1 : rm) * 60000L;
        int dm = prefs.getInt(Prefs.KEY_DELAY_MIN, 5);
        delayMs = (dm < 1 ? 1 : dm) * 60000L;
    }

    private void startTiming() {
        timing = true;
        paused = false;
        currentPeriodMs = 0;
        reminded = false;
        prefs.putBool(Prefs.KEY_TIMING, true);
        prefs.putBool(Prefs.KEY_PAUSED, false);
        startForeground(NOTIF_ID, buildNotification());
        ensureTicker();
        broadcastTime();
    }

    private void pauseTiming() {
        if (!timing) return;
        paused = true;
        prefs.putBool(Prefs.KEY_PAUSED, true);
        stopTicker();
        broadcastTime();
    }

    private void exitAll() {
        if (timing && currentPeriodMs > 0) {
            cumulativeMs += currentPeriodMs;
            currentPeriodMs = 0;
            persistCumulative();
        }
        timing = false;
        paused = false;
        prefs.putBool(Prefs.KEY_TIMING, false);
        prefs.putBool(Prefs.KEY_PAUSED, false);
        removeOverlay();
        stopTicker();
        stopForeground(true);
        broadcastTime();
        stopSelf();
    }

    private void ensureTicker() {
        if (tickerRunning) return;
        if (timing && !paused) {
            tickerRunning = true;
            handler.postDelayed(ticker, 1000);
        }
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
        tickerRunning = false;
    }

    private void tick() {
        if (timing && !paused) {
            if (resting) {
                restMs += 1000;
                restRemainingMs -= 1000;
                if (restRemainingMs <= 0) finishRest();
                else updateOverlayCountdown();
            } else if (screenOn && !popupShown) {
                currentPeriodMs += 1000;
                if (currentPeriodMs >= breakThresholdMs && !reminded) {
                    reminded = true;
                    showOverlay();
                }
            }
        }
        broadcastTime();
        // 仅在亮屏或休息倒计时进行中才继续跳动；息屏时停下以省电
        if (timing && !paused && (screenOn || resting)) handler.postDelayed(ticker, 1000);
        else tickerRunning = false;
    }

    private void onScreenOn() {
        screenOn = true;
        if (timing && !paused) {
            currentPeriodMs = 0;   // 下次亮屏重新记录单次已亮屏时间
            reminded = false;
            ensureTicker();
        }
    }

    private void onScreenOff() {
        screenOn = false;
        // 息屏：本次亮屏时间计入累计
        if (timing && !paused && currentPeriodMs > 0) {
            cumulativeMs += currentPeriodMs;
            currentPeriodMs = 0;
            persistCumulative();
        }
        if (!resting) stopTicker(); // 休息倒计时进行中不停止
    }

    private void showOverlay() {
        if (popupShown) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return; // 未授权则不弹

        popupShown = true;
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_rest, null);

        int bg = prefs.getInt(Prefs.KEY_POPUP_BG, 0xFF1565C0);
        int tc = prefs.getInt(Prefs.KEY_POPUP_TEXT_COLOR, 0xFFFFFFFF);
        int tf = prefs.getInt(Prefs.KEY_POPUP_FONT_SIZE, 28);
        String text = prefs.getString(Prefs.KEY_POPUP_TEXT, "该休息一下眼睛啦！\n闭眼或远眺，放松双眼");

        overlayView.setBackgroundColor(bg);
        TextView tv = overlayView.findViewById(R.id.overlay_text);
        tv.setText(text);
        tv.setTextColor(tc);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tf);
        Button btn = overlayView.findViewById(R.id.overlay_confirm);
        btn.setTextColor(tc);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(16, tf - 6));
        btn.setOnClickListener(v -> startRest());

        Button btnDelay = overlayView.findViewById(R.id.overlay_delay);
        btnDelay.setTextColor(tc);
        btnDelay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(16, tf - 6));
        int dm = (int) (delayMs / 60000L);
        btnDelay.setText("延迟" + dm + "分钟");
        btnDelay.setOnClickListener(v -> delayReminder5());

        Button btnEmergency = overlayView.findViewById(R.id.overlay_emergency);
        btnEmergency.setTextColor(tc);
        btnEmergency.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(14, tf - 10));
        btnEmergency.setOnClickListener(v -> emergencyCancel());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        lp.gravity = Gravity.TOP;
        lp.x = 0; lp.y = 0;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.format = PixelFormat.TRANSLUCENT;
        try {
            wm.addView(overlayView, lp);
            overlayAdded = true;
        } catch (Exception e) {
            overlayAdded = false;
            popupShown = false;
            return;
        }
        handler.postDelayed(autoCancel, AUTO_CANCEL_MS);
    }

    private void startRest() {
        if (!popupShown) return;
        // 先把休息前的亮屏时间存入累计
        if (currentPeriodMs > 0) {
            cumulativeMs += currentPeriodMs;
            currentPeriodMs = 0;
            persistCumulative();
        }
        resting = true;
        restRemainingMs = restDurationMs;
        if (overlayView != null) {
            // 进入休息倒计时：隐藏“确认/延迟”按钮行，仅保留紧急取消
            View btns = overlayView.findViewById(R.id.overlay_buttons_remind);
            if (btns != null) btns.setVisibility(View.GONE);
        }
        updateOverlayCountdown();
    }

    private void updateOverlayCountdown() {
        if (overlayView != null) {
            TextView tv = overlayView.findViewById(R.id.overlay_text);
            if (tv != null) {
                tv.setText("眼睛休息中…\n请闭眼或远眺\n剩余 " + (restRemainingMs / 1000) + " 秒");
            }
        }
    }

    private void finishRest() {
        removeOverlay();
        resting = false;
        currentPeriodMs = 0;
        reminded = false;
        broadcastTime();
    }

    private void removeOverlay() {
        handler.removeCallbacks(autoCancel);
        handler.removeCallbacks(delayReminder);
        removeOverlayView();
        currentPeriodMs = 0;   // 重新计时，下一次提醒在全新的间隔之后
        reminded = false;
    }

    // 仅移除悬浮窗视图，不改变计时/提醒状态（供紧急取消、延迟复用）
    private void removeOverlayView() {
        handler.removeCallbacks(autoCancel);
        if (overlayView != null && overlayAdded) {
            try { wm.removeView(overlayView); } catch (Exception e) { /* ignore */ }
        }
        overlayView = null;
        overlayAdded = false;
        popupShown = false;
    }

    // 紧急取消：立即移除全屏悬浮窗（如来电时），不记录休息时间。
    // 已亮屏时间并入累计并重置本轮，避免取消后立刻又弹出。
    private void emergencyCancel() {
        handler.removeCallbacks(autoCancel);
        handler.removeCallbacks(delayReminder);
        removeOverlayView();
        if (currentPeriodMs > 0) {
            cumulativeMs += currentPeriodMs;
            persistCumulative();
        }
        currentPeriodMs = 0;
        resting = false;
        reminded = false;
        broadcastTime();
    }

    // 延迟提醒：临时延后 delayMs 再提醒，期间可正常用手机（如继续填表）。
    private void delayReminder5() {
        handler.removeCallbacks(autoCancel);
        removeOverlayView();      // 仅移除视图，保留 currentPeriodMs 继续累计
        reminded = true;          // 延迟期间抑制立即重弹
        handler.postDelayed(delayReminder, delayMs);
        broadcastTime();
    }

    private void broadcastTime() {
        Intent i = new Intent(ACTION_TIME_UPDATE);
        i.putExtra(EXTRA_CURRENT, currentPeriodMs);
        i.putExtra(EXTRA_CUMULATIVE, cumulativeMs);
        i.putExtra(EXTRA_REST, restMs);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void persistCumulative() {
        prefs.putLong(Prefs.KEY_CUMULATIVE, cumulativeMs);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception e) { /* ignore */ }
        }
        removeOverlay();
        stopTicker();
        if (timing && currentPeriodMs > 0) {
            cumulativeMs += currentPeriodMs;
            persistCumulative();
        }
        prefs.putLong(Prefs.KEY_REST, restMs);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
