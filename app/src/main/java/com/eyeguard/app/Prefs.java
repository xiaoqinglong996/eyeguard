package com.eyeguard.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 轻量 SharedPreferences 封装，集中管理所有设置与累计数据。 */
public class Prefs {
    public static final String FILE = "eyeguard_prefs";

    // 主界面显示
    public static final String KEY_FONT_SIZE = "font_size";            // sp
    public static final String KEY_FONT_COLOR = "font_color";          // argb int

    // 全屏弹窗
    public static final String KEY_POPUP_BG = "popup_bg";              // argb int
    public static final String KEY_POPUP_TEXT = "popup_text";          // String
    public static final String KEY_POPUP_TEXT_COLOR = "popup_text_color";
    public static final String KEY_POPUP_FONT_SIZE = "popup_font_size";

    // 计时规则
    public static final String KEY_BREAK_MIN = "break_min";            // 分钟
    public static final String KEY_REST_MIN = "rest_min";              // 分钟
    public static final String KEY_DELAY_MIN = "delay_min";            // 延迟提醒分钟（默认5）

    // 开机自启动
    public static final String KEY_AUTO_START = "auto_start";

    // 运行状态与累计
    public static final String KEY_TIMING = "timing";
    public static final String KEY_PAUSED = "paused";
    public static final String KEY_CUMULATIVE = "cumulative_ms";
    public static final String KEY_REST = "rest_ms";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getInt(String k, int d) { return sp.getInt(k, d); }
    public long getLong(String k, long d) { return sp.getLong(k, d); }
    public String getString(String k, String d) {
        String v = sp.getString(k, d);
        return v == null ? d : v;
    }
    public boolean getBool(String k, boolean d) { return sp.getBoolean(k, d); }

    public void putInt(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public void putLong(String k, long v) { sp.edit().putLong(k, v).apply(); }
    public void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
    public void putBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
}
