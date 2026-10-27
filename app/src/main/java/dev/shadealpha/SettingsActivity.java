package dev.shadealpha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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
import android.widget.Toast;

import java.util.Locale;

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

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20), bars.top + dp(24), dp(20), bars.bottom + dp(24));
            return WindowInsets.CONSUMED;
        });

        // Статус модуля
        boolean active = Keys.isModuleActive();
        TextView statusText = text("", 14, true);
        if (active) {
            statusText.setText(t("Статус: Активен ✅\n", "Status: Active ✅\n") + Keys.getHookStatus());
            statusText.setTextColor(0xFF4CAF50);
        } else {
            statusText.setText(t("Статус: Не активен ❌\nВключите модуль в LSPosed (System UI + ShadeAlpha)", "Status: Inactive ❌\nEnable module in LSPosed (System UI + ShadeAlpha)"));
            statusText.setTextColor(0xFFF44336);
        }
        root.addView(statusText, lp(0));

        // Заголовок
        root.addView(text(t("Прозрачность шторки", "Shade transparency"), 24, true), lp(16));
        root.addView(text(
                t("100% — стандартный вид системы. Чем ниже значение, тем прозрачнее шторка.",
                        "100% = default system appearance. Lower values make the shade more transparent."), 14, false), lp(4));

        // Язык
        root.addView(text(t("Язык", "Language"), 16, true), lp(16));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton ru = new RadioButton(this);
        ru.setText("Русский");
        ru.setId(View.generateViewId());
        final RadioButton en = new RadioButton(this);
        en.setText("English");
        en.setId(View.generateViewId());
        group.addView(ru);
        group.addView(en);
        group.check("ru".equals(lang) ? ru.getId() : en.getId());
        group.setOnCheckedChangeListener((g, id) -> {
            String nl = id == ru.getId() ? "ru" : "en";
            if (!nl.equals(lang)) {
                prefs.edit().putString(KEY_LANG, nl).apply();
                recreate();
            }
        });
        root.addView(group, lp(4));

        // Ползунки
        topBar = addSlider(root, t("Верхняя часть (панели быстрых настроек)", "Top area (Quick Settings panels)"), Keys.TOP);
        bottomBar = addSlider(root, t("Нижняя часть (уведомления)", "Bottom area (notifications)"), Keys.BOTTOM);

        // Режим отрисовки
        root.addView(headerWithHelp(
                t("Режим отрисовки", "Rendering mode"),
                t("Режимы отрисовки", "Rendering modes"),
                modesHelpText()), lp(24));

        final String[] labels = {
                t("0: Совместимость (чуть выше нагрузка)", "0: Compatibility (slightly higher load)"),
                t("1: Быстрый (рекомендуется)", "1: Fast (recommended)"),
                t("2: Самый легкий (минимальная нагрузка)", "2: Lightest (minimal load)")
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
        Button showShade = new Button(this);
        showShade.setText(t("Показать шторку", "Show shade"));
        showShade.setOnClickListener(v -> new Thread(() -> {
            runRoot("cmd statusbar expand-notifications || cmd statusbar expand-settings");
        }).start());
        root.addView(showShade, lp(24));

        Button reset = new Button(this);
        reset.setText(t("Сбросить до 100%", "Reset to 100%"));
        reset.setOnClickListener(v -> {
            topBar.setProgress(100);
            bottomBar.setProgress(100);
            save(Keys.TOP, 100);
            save(Keys.BOTTOM, 100);
        });
        root.addView(reset, lp(8));

        Button restart = new Button(this);
        restart.setText(t("Перезапустить SystemUI", "Restart SystemUI"));
        restart.setOnClickListener(v -> new Thread(() -> {
            final int code = runRoot("killall com.android.systemui || pkill -f com.android.systemui");
            runOnUiThread(() -> status.setText(code == 0
                    ? t("SystemUI перезапущен.", "SystemUI restarted.")
                    : t("ОШИБКА: Root не выдан или заблокирован!", "ERROR: Root denied or blocked!")));
        }).start());
        root.addView(restart, lp(8));

        status = text("", 14, false);
        root.addView(status, lp(12));

        // Разрешение
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
                if (p < 20 && fromUser) {
                    value.setTextColor(0xFFF44336);
                    value.setText(p + "% " + t("(текст может быть нечитаем)", "(text may be unreadable)"));
                } else {
                    value.setTextColor(text("", 16, false).getCurrentTextColor());
                    value.setText(p + "%");
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar s) {}
            @Override
            public void onStopTrackingTouch(SeekBar s) { save(key, s.getProgress()); }
        });
        root.addView(text(label, 16, true), lp(24));
        root.addView(value, lp(2));
        root.addView(bar, lp(4));
        return bar;
    }

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
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), key, p);
                if (readSystem(key, -1) == p) return true;
            }
        } catch (Throwable ignored) {}
        runRoot("settings put system " + key + " " + p);
        return readSystem(key, -1) == p;
    }

    private int runRoot(String cmd) {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return pr.waitFor();
        } catch (Throwable e) {
            // Пробуем альтернативный вызов для некоторых рут-менеджеров
            try {
                Process pr = Runtime.getRuntime().exec("su -c " + cmd);
                return pr.waitFor();
            } catch (Throwable e2) {
                return -1;
            }
        }
    }

    private void refreshPermission() {
        if (permText == null) return;
        boolean ok = Settings.System.canWrite(this);
        permText.setText(ok
                ? t("Разрешение на изменение системных настроек выдано.", "Permission to modify system settings is granted.")
                : t("Если нет root: нужно разрешение «Изменение системных настроек».", "Without root, the app needs the \"Modify system settings\" permission."));
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
        help.setTypeface(Typeface.DEFAULT_BOLD);
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

    private String modesHelpText() {
        return t(
                "Режим отрисовки определяет, как именно модуль делает фон прозрачным. На глаз разницы нет, но есть разница в нагрузке на батарею и GPU.\n\n" +
                "🟢 Режим 2 (ScrimView.setViewAlpha) — Самый легкий\n" +
                "Прямо меняет прозрачность цвета фона. Не создает лишних слоев, минимальная нагрузка. Работает, если ваша прошивка поддерживает этот метод.\n\n" +
                "🟡 Режим 1 (View.setAlpha) — Быстрый\n" +
                "Применяет прозрачность ко всему элементу. Может быть чуть тяжелее, так как иногда требует создания промежуточного слоя (Layer), но отлично работает как альтернатива.\n\n" +
                "🔴 Режим 0 (saveLayerAlpha) — Максимальная совместимость\n" +
                "Принудительно создает слой для отрисовки. Самый надежный, но и самый тяжелый (двойная отрисовка). Используйте только если остальные не работают.\n\n" +
                "После смены режима обязательно нажми «Перезапустить SystemUI».",
                
                "The rendering mode determines how the module makes the background transparent. Visually there is no difference, but there is a difference in battery and GPU load.\n\n" +
                "🟢 Mode 2 (ScrimView.setViewAlpha) - Lightest\n" +
                "Directly changes the background color's alpha. Creates no extra layers, minimal load. Works if your ROM supports this method.\n\n" +
                "🟡 Mode 1 (View.setAlpha) - Fast\n" +
                "Applies transparency to the whole View. Might be slightly heavier as it sometimes requires an intermediate layer, but works great as an alternative.\n\n" +
                "🔴 Mode 0 (saveLayerAlpha) - Max Compatibility\n" +
                "Forces the creation of a rendering layer. Most reliable, but the heaviest (double rendering). Use only if the others fail.\n\n" +
                "After changing the mode, be sure to tap \"Restart SystemUI\".");
    }
}