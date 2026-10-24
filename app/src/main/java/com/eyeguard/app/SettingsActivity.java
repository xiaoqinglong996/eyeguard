package com.eyeguard.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    static final int[] COLORS = {0xFF000000, 0xFFFFFFFF, 0xFFE53935, 0xFF1565C0,
            0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A, 0xFF00838F};
    static final String[] COLOR_NAMES = {"黑色", "白色", "红色", "蓝色", "绿色", "橙色", "紫色", "青色"};

    private Prefs prefs;
    private SeekBar sbFont, sbPopupFont;
    private TextView tvFontVal, tvPopupFontVal;
    private Spinner spFontColor, spPopupBg, spPopupTextColor;
    private EditText etPopupText, etBreak, etRest, etDelay;
    private Switch swAuto;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        sbFont = findViewById(R.id.sb_font_size);
        tvFontVal = findViewById(R.id.tv_font_val);
        sbPopupFont = findViewById(R.id.sb_popup_font_size);
        tvPopupFontVal = findViewById(R.id.tv_popup_font_val);
        spFontColor = findViewById(R.id.sp_font_color);
        spPopupBg = findViewById(R.id.sp_popup_bg);
        spPopupTextColor = findViewById(R.id.sp_popup_text_color);
        etPopupText = findViewById(R.id.et_popup_text);
        etBreak = findViewById(R.id.et_break_min);
        etRest = findViewById(R.id.et_rest_min);
        etDelay = findViewById(R.id.et_delay_min);
        swAuto = findViewById(R.id.sw_auto_start);
        Button btnDone = findViewById(R.id.btn_done);
        Button btnSystem = findViewById(R.id.btn_system_settings);

        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, COLOR_NAMES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFontColor.setAdapter(ad);
        spPopupBg.setAdapter(ad);
        spPopupTextColor.setAdapter(ad);

        int fs = prefs.getInt(Prefs.KEY_FONT_SIZE, 18);
        sbFont.setProgress(fs - 12);
        tvFontVal.setText(fs + " sp");
        int pfs = prefs.getInt(Prefs.KEY_POPUP_FONT_SIZE, 28);
        sbPopupFont.setProgress(pfs - 12);
        tvPopupFontVal.setText(pfs + " sp");
        spFontColor.setSelection(indexOfColor(prefs.getInt(Prefs.KEY_FONT_COLOR, 0xFF000000)));
        spPopupBg.setSelection(indexOfColor(prefs.getInt(Prefs.KEY_POPUP_BG, 0xFF1565C0)));
        spPopupTextColor.setSelection(indexOfColor(prefs.getInt(Prefs.KEY_POPUP_TEXT_COLOR, 0xFFFFFFFF)));
        etPopupText.setText(prefs.getString(Prefs.KEY_POPUP_TEXT, "该休息一下眼睛啦！\n闭眼或远眺，放松双眼"));
        etBreak.setText(String.valueOf(prefs.getInt(Prefs.KEY_BREAK_MIN, 20)));
        etRest.setText(String.valueOf(prefs.getInt(Prefs.KEY_REST_MIN, 1)));
        etDelay.setText(String.valueOf(prefs.getInt(Prefs.KEY_DELAY_MIN, 5)));
        swAuto.setChecked(prefs.getBool(Prefs.KEY_AUTO_START, false));

        sbFont.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                int v = p + 12; tvFontVal.setText(v + " sp"); prefs.putInt(Prefs.KEY_FONT_SIZE, v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sbPopupFont.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                int v = p + 12; tvPopupFontVal.setText(v + " sp"); prefs.putInt(Prefs.KEY_POPUP_FONT_SIZE, v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        spFontColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                prefs.putInt(Prefs.KEY_FONT_COLOR, COLORS[pos]);
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        spPopupBg.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                prefs.putInt(Prefs.KEY_POPUP_BG, COLORS[pos]);
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        spPopupTextColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                prefs.putInt(Prefs.KEY_POPUP_TEXT_COLOR, COLORS[pos]);
            }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        etPopupText.setOnFocusChangeListener((v, has) -> {
            if (!has) prefs.putString(Prefs.KEY_POPUP_TEXT, etPopupText.getText().toString());
        });
        swAuto.setOnCheckedChangeListener((b2, c) -> prefs.putBool(Prefs.KEY_AUTO_START, c));

        btnDone.setOnClickListener(v -> {
            saveMinutes();
            Intent i = new Intent(this, EyeGuardService.class);
            i.setAction(EyeGuardService.ACTION_RELOAD);
            startService(i);
            finish();
        });
        btnSystem.setOnClickListener(v -> openSystemSettings());
    }

    private void saveMinutes() {
        try {
            int bm = Integer.parseInt(etBreak.getText().toString().trim());
            if (bm < 1) bm = 1;
            prefs.putInt(Prefs.KEY_BREAK_MIN, bm);
        } catch (Exception e) { prefs.putInt(Prefs.KEY_BREAK_MIN, 20); }
        try {
            int rm = Integer.parseInt(etRest.getText().toString().trim());
            if (rm < 1) rm = 1;
            prefs.putInt(Prefs.KEY_REST_MIN, rm);
        } catch (Exception e) { prefs.putInt(Prefs.KEY_REST_MIN, 1); }
        try {
            String dms = etDelay.getText().toString().trim();
            int dm = dms.isEmpty() ? 5 : Integer.parseInt(dms);
            if (dm < 1) dm = 1;
            prefs.putInt(Prefs.KEY_DELAY_MIN, dm);
        } catch (Exception e) { prefs.putInt(Prefs.KEY_DELAY_MIN, 5); }
    }

    private void openSystemSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(i);
            Toast.makeText(this, "请在设置中将本应用设为“允许后台运行 / 自启动 / 不受电池优化限制”",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ex) { /* ignore */ }
        }
    }

    private int indexOfColor(int c) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c) return i;
        return 0;
    }
}
