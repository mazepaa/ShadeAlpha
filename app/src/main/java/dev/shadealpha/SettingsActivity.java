package dev.shadealpha;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** Язык RU/EN, два ползунка (верх / низ шторки), тестовый режим, сброс и перезапуск SystemUI. */
public class SettingsActivity extends Activity {

    private static final String PREFS = "ui";
    private static final String KEY_LANG = "lang";

    private SharedPreferences prefs;
    private String lang;
    private TextView permText;
    private TextView status;
    private Button permButton;
    private SeekBar topBar;
    private SeekBar bottomBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String def = "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
        lang = prefs.getString(KEY_LANG, def);
        setTitle(t("Прозрачность шторки", "Shade transparency"));
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermission();
        syncToSystem();
    }

    private String t(String ru, String en) {
        return "ru".equals(lang) ? ru : en;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private void buildUi() {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // targetSdk 35: окно рисуется под системными панелями, добавляем отступы
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20), bars.top + dp(24), dp(20), bars.bottom + dp(24));
            return WindowInsets.CONSUMED;
        });

        boolean active = Keys.isModuleActive(); TextView statusText = text("", 14, true); statusText.setText(active ? t("Статус: Активен ✅\n", "Status: Active ✅\n") + Keys.getHookStatus() : t("Статус: Не активен ❌", "Status: Inactive ❌")); statusText.setTextColor(active ? 0xFF4CAF50 : 0xFFF44336); root.addView(statusText, lp(0)); 
        root.addView(text(
                t("100% — стандартный вид системы. Чем ниже значение, тем прозрачнее шторка.",
                        "100% = default system appearance. Lower values make the shade more transparent."), 14, false), lp(4));

        // Язык
        

        // Ползунки
        topBar = addSlider(root, t("Верхняя часть (панели быстрых настроек)", "Top area (Quick Settings panels)"), Keys.TOP);
        bottomBar = addSlider(root, t("Нижняя часть (уведомления)", "Bottom area (notifications)"),
                Keys.BOTTOM);

        // Режим отрисовки
        root.addView(headerWithHelp(
        t("Режим отрисовки", "Rendering mode"),
        t("Режимы отрисовки", "Rendering modes"),
        modesHelpText()), lp(24));
        final String[] labels = {
                t("0: Слой (лучше совместимость, выше нагрузка)", "0: Layer (best compatibility, higher system load)"),
                t("1: View alpha (лёгкий и плавный режим)", "1: View alpha (lightweight and smooth)"),
                t("2: Scrim alpha (минимальная нагрузка)", "2: Scrim alpha (lowest system load)")
        };
        final int[] ids = new int[3];
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setId(View.generateViewId());
            ids[i] = rb.getId();
            modes.addView(rb);
        }
        int curMode = prefs.getInt(Keys.MODE, -1);
        if (curMode < 0) curMode = readSystem(Keys.MODE, Keys.MODE_VIEW_ALPHA);
        modes.check(ids[Math.max(0, Math.min(2, curMode))]);
        modes.setOnCheckedChangeListener((g, id) -> {
            for (int i = 0; i < 3; i++) {
                if (ids[i] == id) save(Keys.MODE, i);
            }
        });
        root.addView(modes, lp(4));
        root.addView(text(
                t("После смены режима нажми «Перезапустить SystemUI».",
                        "After changing the rendering mode, tap \"Restart SystemUI\"."), 13, false), lp(4));

        // Кнопки
        Button reset = new Button(this);
        reset.setText(t("Сбросить до 100%", "Reset to 100%"));
        reset.setOnClickListener(v -> {
            topBar.setProgress(100);
            bottomBar.setProgress(100);
            save(Keys.TOP, 100);
            save(Keys.BOTTOM, 100);
        });
        root.addView(reset, lp(24));

        Button restart = new Button(this);
        restart.setText(t("Перезапустить SystemUI", "Restart SystemUI"));
        restart.setOnClickListener(v -> new Thread(() -> {
            final int code = runRoot("killall com.android.systemui || pkill -f com.android.systemui");
            runOnUiThread(() -> status.setText(code == 0
                    ? t("SystemUI перезапущен.", "SystemUI restarted.")
                    : t("Не удалось перезапустить (нужен root).", "Restart failed (root required).")));
        }).start());
        root.addView(restart, lp(8));

        status = text("", 14, false);
        root.addView(status, lp(12));

        // Разрешение на запись системных настроек (альтернатива root)
        permText = text("", 13, false);
        permButton = new Button(this);
        permButton.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(permText, lp(24));
        root.addView(permButton, lp(4));

        setContentView(scroll);
    }

    private SeekBar addSlider(LinearLayout root, String label, final String key) {
        final TextView value = text("", 16, false);
        final SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        int cur = prefs.getInt(key, -1);
        if (cur < 0) cur = Math.max(0, Math.min(100, readSystem(key, 100)));
        bar.setProgress(cur);
        value.setText(cur + "%");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                value.setText(p + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save(key, s.getProgress());
            }
        });
        root.addView(text(label, 16, true), lp(24));
        root.addView(value, lp(2));
        root.addView(bar, lp(4));
        return bar;
    }

    // ---------- сохранение ----------

    /** Запоминает значение в самом приложении и передаёт его системе (для хука в SystemUI). */
    private void save(final String key, final int p) {
        prefs.edit().putInt(key, p).apply();
        new Thread(() -> {
            final boolean ok = writeSetting(key, p);
            final String shown = Keys.MODE.equals(key) ? t("режим ", "mode ") + p : p + "%";
            runOnUiThread(() -> status.setText(ok
                    ? t("Сохранено: ", "Saved: ") + shown
                    : t("Не удалось записать в систему. Выдай разрешение ниже или root этому приложению.",
                    "Could not write to the system. Grant the permission below or root access to this app.")));
        }).start();
    }

    /** При возврате в приложение (например, после выдачи разрешения) досылает сохранённые значения. */
    private void syncToSystem() {
        new Thread(() -> {
            for (String key : new String[]{Keys.TOP, Keys.BOTTOM, Keys.MODE}) {
                int want = prefs.getInt(key, -1);
                if (want < 0) continue;
                if (readSystem(key, -1) != want) writeSetting(key, want);
            }
        }).start();
    }

    private int readSystem(String key, int def) {
        try {
            return Settings.System.getInt(getContentResolver(), key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private boolean writeSetting(String key, int p) {
        // 1) напрямую, если выдано разрешение «Изменение системных настроек»
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), key, p);
                if (readSystem(key, -1) == p) return true;
            }
        } catch (Throwable ignored) {
        }
        // 2) через root
        runRoot("settings put system " + key + " " + p);
        return readSystem(key, -1) == p;
    }

    private int runRoot(String cmd) {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return pr.waitFor();
        } catch (Throwable e) {
            return -1;
        }
    }

    private void refreshPermission() {
        if (permText == null) return;
        boolean ok = Settings.System.canWrite(this);
        permText.setText(ok
                ? t("Разрешение на изменение системных настроек выдано.",
                "Permission to modify system settings is granted.")
                : t("Если нет root: нужно разрешение «Изменение системных настроек».",
                "Without root, the app needs the \"Modify system settings\" permission."));
        permButton.setText(t("Выдать разрешение", "Grant permission"));
        permButton.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private LinearLayout headerWithHelp(String title, final String helpTitle, final String helpText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(title, 16, true));

        TextView help = new TextView(this);
        help.setText("?");
        help.setTextSize(13);
        help.setTypeface(help.getTypeface(), Typeface.BOLD);
        help.setGravity(Gravity.CENTER);
        help.setContentDescription(t("Справка", "Help"));

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(1), help.getCurrentTextColor());
        help.setBackground(ring);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(22), dp(22));
        p.setMarginStart(dp(4));
        help.setLayoutParams(p);
        help.setOnClickListener(v -> showHelp(helpTitle, helpText));
        row.addView(help);
        return row;
    }

    private void showHelp(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(t("Понятно", "Got it"), null)
                .show();
    }

    private String modesHelpText() { return t("Режим 1 (Рекомендуется) — самый быстрый и оптимизированный. Не нагружает систему.\n\nРежим 2 — легкий для видеокарты.\n\nРежим 0 — максимальная совместимость, но выше нагрузка.", "Mode 1 (Recommended) — fastest and optimized. Does not load the system.\n\nMode 2 — light for GPU.\n\nMode 0 — max compatibility, but higher load."); } }
    }
}

