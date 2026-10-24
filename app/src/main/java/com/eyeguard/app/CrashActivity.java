package com.eyeguard.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全屏错误页：展示上次崩溃的完整堆栈，并提供“复制 / 分享 / 关闭”按钮，
 * 方便无开发经验的用户把错误原文发回给开发者定位问题。
 */
public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String trace = getIntent().getStringExtra(CrashApplication.EXTRA_TRACE);
        if (trace == null || trace.isEmpty()) {
            try {
                trace = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
                        .getString(CrashApplication.KEY_LAST_CRASH, "(无错误信息)");
            } catch (Exception e) { trace = "(无错误信息)"; }
        }

        final String finalTrace = trace;

        ScrollView sv = new ScrollView(this);
        sv.setPadding(24, 24, 24, 24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("应用上次启动出错\n请把下面的内容截图或“复制”后发给开发者");
        title.setTextSize(16);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView tv = new TextView(this);
        tv.setText(trace);
        tv.setTextSize(11);
        root.addView(tv);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, 24, 0, 0);

        Button copy = new Button(this);
        copy.setText("复制");
        copy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("crash", finalTrace));
                Toast.makeText(this, "已复制，可粘贴发给开发者", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "复制失败，请直接截图", Toast.LENGTH_SHORT).show();
            }
        });

        Button share = new Button(this);
        share.setText("分享");
        share.setOnClickListener(v -> {
            try {
                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("text/plain");
                it.putExtra(Intent.EXTRA_TEXT, finalTrace);
                startActivity(Intent.createChooser(it, "发送错误日志"));
            } catch (Exception e) {
                Toast.makeText(this, "分享失败，请直接截图", Toast.LENGTH_SHORT).show();
            }
        });

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> {
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
        });

        bar.addView(copy);
        bar.addView(share);
        bar.addView(close);
        root.addView(bar);

        setContentView(sv);
    }
}
