package com.eyeguard.app;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获：把任何未捕获异常（含 Activity / Service 启动期）的完整堆栈
 * 写入文件与 SharedPreferences，便于在无法连接电脑的用户设备上定位问题。
 * 下次启动时由 MainActivity 读取并跳转到一个全屏错误页展示。
 */
public class CrashApplication extends Application {

    public static final String KEY_LAST_CRASH = "last_crash";
    public static final String EXTRA_TRACE = "trace";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            String trace = buildTrace(ex);
            // 1) 写入应用私有外部目录，便于用户导出/分享
            try {
                File dir = getExternalFilesDir(null);
                if (dir != null) {
                    File f = new File(dir, "crash.log");
                    FileWriter w = new FileWriter(f);
                    w.write(trace);
                    w.close();
                }
            } catch (Exception ignore) { /* 无权限或失败则忽略 */ }
            // 2) 写入 SharedPreferences，供下次启动弹窗展示
            try {
                SharedPreferences sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
                sp.edit().putString(KEY_LAST_CRASH, trace).apply();
            } catch (Exception ignore) { /* ignore */ }
            // 3) 尽力直接弹出全屏错误页（即便崩溃发生在 MainActivity 加载阶段）
            try {
                Intent ci = new Intent(this, CrashActivity.class);
                ci.putExtra(EXTRA_TRACE, trace);
                ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(ci);
            } catch (Exception ignore) { /* 失败则交由下次启动处理 */ }
            // 4) 交给系统默认处理（显示“应用已停止”）
            if (def != null) def.uncaughtException(thread, ex);
        });
    }

    /** 把异常堆栈 + 设备信息转成字符串（供 Activity 复用）。 */
    public static String buildTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("时间: ").append(time).append("\n");
        sb.append("机型: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("系统: Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("==========================================\n");
        sb.append(sw.toString());
        return sb.toString();
    }
}
