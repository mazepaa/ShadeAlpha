package dev.shadealpha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
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

import java.util.Locale;

public class SettingsActivity extends Activity {

    private static final String PREFS = "ui";
    private static final String KEY_LANG = "lang";

    private SharedPreferences prefs;
    private String lang;
    private TextView status;
    private SeekBar topBar;
    private SeekBar bottomBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String def = "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
        lang = prefs.getString(KEY_LANG, def);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private boolean checkRoot() {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            return pr.waitFor() == 0;
        } catch (Throwable e) {
            return false;
        }
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

        // Шапка с заголовком и кнопкой языка
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(t("Прозрачность шторки", "Shade transparency"), 24, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(title, titleLp);

        TextView langBtn = text(lang.equals("ru") ? "RU" : "EN", 16, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(0x22888888);
        langBtn.setBackground(bg);
        langBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        langBtn.setOnClickListener(v -> {
            String nl = lang.equals("ru") ? "en" : "ru";
            prefs.edit().putString(KEY_LANG, nl).apply();
            recreate();
        });
        headerRow.addView(langBtn);

        root.addView(headerRow, lp(0));

        // Статус Root
        boolean hasRoot = checkRoot();
        TextView statusText = text("", 14, true);
        if (hasRoot) {
            statusText.setText(t("Root: Выдан ✅", "Root: Granted ✅"));
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
            int accentColor = typedValue.data;
            if (accentColor == 0) accentColor = 0xFF4CAF50;
            statusText.setTextColor(accentColor);
        } else {
            statusText.setText(t("Root: Нет доступа ❌", "Root: Denied ❌"));
            statusText.setTextColor(0xFFF44336);
        }
        root.addView(statusText, lp(8));

        // Ползунки
        topBar = addSlider(root, t("Верхняя часть (панели быстрых настроек)", "Top area (Quick Settings panels)"), Keys.TOP);
        bottomBar = addSlider(root, t("Нижняя часть (уведомления)", "Bottom area (notifications)"), Keys.BOTTOM);

        // Режим отрисовки
        root.addView(headerWithHelp(
                t("Режим отрисовки", "Rendering mode"),
                t("Режимы отрисовки", "Rendering modes"),
                modesHelpText()), lp(24));

        final String[] labels = {
                t("1: Совместимость", "1: Compatibility"),
                t("2: Быстрый", "2: Fast"),
                t("3: Самый легкий", "3: Lightest")
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

        Button restart = new Button(this);
        restart.setText(t("Перезапустить SystemUI", "Restart SystemUI"));
        restart.setOnClickListener(v -> new Thread(() -> {
            runRoot("killall com.android.systemui || pkill -f com.android.systemui");
            runOnUiThread(() -> status.setText(t("Команда перезапуска отправлена.", "Restart command sent.")));
        }).start());
        root.addView(restart, lp(8));

        status = text("", 14, false);
        root.addView(status, lp(12));

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
                    value.setText(p + "% " + t("(текст нечитаем)", "(unreadable text)"));
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
            final String shown = Keys.MODE.equals(key) ? t("режим ", "mode ") + (p + 1) : p + "%";
            runOnUiThread(() -> status.setText(ok
                    ? t("Сохранено: ", "Saved: ") + shown
                    : t("Ошибка записи. Проверьте root.", "Write error. Check root.")));
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
        runRoot("settings put system " + key + " " + p);
        return readSystem(key, -1) == p;
    }

    private void runRoot(String cmd) {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            pr.waitFor();
        } catch (Throwable e) {
            try {
                Process pr = Runtime.getRuntime().exec("su -c " + cmd);
                pr.waitFor();
            } catch (Throwable ignored) {}
        }
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
                "🟢 Режим 3 (ScrimView.setViewAlpha) — Самый легкий\n" +
                "Прямо меняет прозрачность цвета фона. Не создает лишних слоев, минимальная нагрузка.\n\n" +
                "🟡 Режим 2 (View.setAlpha) — Быстрый\n" +
                "Применяет прозрачность ко всему элементу. Может быть чуть тяжелее, так как иногда требует создания промежуточного слоя, но отлично работает как альтернатива.\n\n" +
                "🔴 Режим 1 (saveLayerAlpha) — Максимальная совместимость\n" +
                "Принудительно создает слой для отрисовки. Самый надежный, но и самый тяжелый (двойная отрисовка). Используйте только если остальные не работают.\n\n" +
                "После смены режима обязательно нажми «Перезапустить SystemUI».",
                
                "The rendering mode determines how the module makes the background transparent. Visually there is no difference, but there is a difference in battery and GPU load.\n\n" +
                "🟢 Mode 3 (ScrimView.setViewAlpha) - Lightest\n" +
                "Directly changes the background color's alpha. Creates no extra layers, minimal load.\n\n" +
                "🟡 Mode 2 (View.setAlpha) - Fast\n" +
                "Applies transparency to the whole View. Might be slightly heavier as it sometimes requires an intermediate layer, but works great as an alternative.\n\n" +
                "🔴 Mode 1 (saveLayerAlpha) - Max Compatibility\n" +
                "Forces the creation of a rendering layer. Most reliable, but the heaviest (double rendering). Use only if the others fail.\n\n" +
                "After changing the mode, be sure to tap \"Restart SystemUI\".");
    }
}