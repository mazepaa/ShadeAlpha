$settingsCode = @'
package dev.shadealpha;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "ui";
    private static final String KEY_LANG = "lang";
    private static final String TG_LINK = "https://t.me/mazepaa";
    private static final String GITHUB_LINK = "https://github.com/mazepaa/ShadeAlpha";

    private SharedPreferences prefs;
    private String lang;
    private TextView status;
    private Slider topBar;
    private Slider bottomBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
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
        tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
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

    // Физика нажатия для кнопок (без вибрации)
    private void addPressPhysics(View v) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    break;
            }
            return false;
        });
    }

    private void showAboutSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Увеличили верхний отступ (было 32), чтобы было куда прыгать логотипу
        root.setPadding(dp(24), dp(56), dp(24), dp(32));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(getApplicationInfo().icon);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        
        // Пружинка с вибрацией для логотипа
        icon.setOnClickListener(v -> {
            v.animate().cancel();
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().translationY(-dp(15)).setDuration(100)
             .withEndAction(() -> v.animate().translationY(0).setInterpolator(new BounceInterpolator()).setDuration(400).start())
             .start();
        });
        root.addView(icon, iconLp);

        TextView title = text("ShadeAlpha", 24, true);
        title.setPadding(0, dp(16), 0, dp(4));
        root.addView(title);

        TextView version = text("v1.0.1", 14, false);
        version.setTextColor(Color.GRAY);
        root.addView(version);

        LinearLayout linksRow = new LinearLayout(this);
        linksRow.setOrientation(LinearLayout.HORIZONTAL);
        linksRow.setGravity(Gravity.CENTER);
        linksRow.setPadding(0, dp(24), 0, dp(24));

        MaterialButton btnTg = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnTg.setText("Telegram");
        addPressPhysics(btnTg);
        btnTg.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TG_LINK))));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(dp(8));
        linksRow.addView(btnTg, lp1);

        MaterialButton btnGh = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGh.setText("GitHub");
        addPressPhysics(btnGh);
        btnGh.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_LINK))));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMarginStart(dp(8));
        linksRow.addView(btnGh, lp2);

        root.addView(linksRow);

        MaterialCardView devCard = new MaterialCardView(this);
        devCard.setRadius(dp(16));
        devCard.setCardElevation(0);
        devCard.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF222222));
        LinearLayout devLayout = new LinearLayout(this);
        devLayout.setOrientation(LinearLayout.HORIZONTAL);
        devLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        devLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView devName = text(t("Разработчик:\nmazepaa", "Developer:\nmazepaa"), 16, true);
        devLayout.addView(devName);
        devCard.addView(devLayout);

        root.addView(devCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = text(t(
            "Модуль для LSPosed, делающий шторку уведомлений полупрозрачной.",
            "LSPosed module making the notification shade translucent."), 14, false);
        desc.setPadding(0, dp(24), 0, 0);
        desc.setGravity(Gravity.CENTER);
        root.addView(desc);

        dialog.setContentView(root);
        dialog.show();
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

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(t("Прозрачность шторки", "Shade transparency"), 24, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(title, titleLp);

        // Кнопка RU/EN
        TextView langBtn = text(lang.equals("ru") ? "RU" : "EN", 16, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0x22888888));
        langBtn.setBackground(bg);
        langBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        langBtn.setGravity(Gravity.CENTER);
        langBtn.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF000000));
        addPressPhysics(langBtn);
        langBtn.setOnClickListener(v -> {
            String nl = lang.equals("ru") ? "en" : "ru";
            prefs.edit().putString(KEY_LANG, nl).apply();
            recreate();
        });
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        langLp.setMarginEnd(dp(12));
        headerRow.addView(langBtn, langLp);

        // Кнопка меню
        TextView menuBtn = text("⋮", 24, true);
        menuBtn.setIncludeFontPadding(false);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        menuBtn.setBackgroundResource(outValue.resourceId);
        addPressPhysics(menuBtn);
        menuBtn.setOnClickListener(v -> showAboutSheet());
        headerRow.addView(menuBtn);

        root.addView(headerRow, lp(0));

        boolean hasRoot = checkRoot();
        TextView statusText = text("", 14, true);
        if (hasRoot) {
            statusText.setText(t("Root: Выдан ✅", "Root: Granted ✅"));
            statusText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF4CAF50));
        } else {
            statusText.setText(t("Root: Нет доступа ❌", "Root: Denied ❌"));
            statusText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0xFFF44336));
            // Оставляем вибрацию для ошибки Root
            statusText.performHapticFeedback(HapticFeedbackConstants.REJECT);
        }
        root.addView(statusText, lp(8));

        topBar = addSlider(root, t("Верхняя часть (панели быстрых настроек)", "Top area (Quick Settings panels)"), Keys.TOP);
        bottomBar = addSlider(root, t("Нижняя часть (уведомления)", "Bottom area (notifications)"), Keys.BOTTOM);

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
            MaterialRadioButton rb = new MaterialRadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
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

        MaterialButton showShade = new MaterialButton(this);
        showShade.setText(t("Показать шторку", "Show shade"));
        addPressPhysics(showShade);
        showShade.setOnClickListener(v -> new Thread(() -> {
            runRoot("cmd statusbar expand-notifications || cmd statusbar expand-settings");
        }).start());
        root.addView(showShade, lp(24));

        MaterialButton restart = new MaterialButton(this);
        restart.setText(t("Перезапустить SystemUI", "Restart SystemUI"));
        addPressPhysics(restart);
        restart.setOnClickListener(v -> new Thread(() -> {
            runRoot("killall com.android.systemui || pkill -f com.android.systemui");
            runOnUiThread(() -> status.setText(t("Команда перезапуска отправлена.", "Restart command sent.")));
        }).start());
        root.addView(restart, lp(8));

        status = text("", 14, false);
        root.addView(status, lp(12));

        setContentView(scroll);
    }

    private Slider addSlider(LinearLayout root, String label, final String key) {
        final TextView value = text("", 16, false);
        final Slider slider = new Slider(this);
        
        slider.setValueFrom(0f);
        slider.setValueTo(100f);
        slider.setStepSize(5f);
        slider.setTickVisible(false);
        slider.setTrackStopIndicatorSize(0);
        slider.setLabelBehavior(2);

        int cur = prefs.getInt(key, -1);
        if (cur < 0) cur = Math.max(0, Math.min(100, readSystem(key, 100)));
        slider.setValue((float) cur);
        value.setText(cur + "%");

        final float[] lastVal = {cur};
        slider.addOnChangeListener((s, v, fromUser) -> {
            int p = (int) v;
            value.setText(p + "%");
            
            // Оставляем вибрацию для слайдера (тактильный отклик при прокрутке)
            if (fromUser && Math.abs(v - lastVal[0]) >= 4.9f) {
                s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                lastVal[0] = v;
            }

            if (p < 20 && fromUser) {
                value.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0xFFF44336));
                value.setText(p + "% " + t("(текст нечитаем)", "(unreadable)"));
            } else {
                value.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
                value.setText(p + "%");
            }
        });
        
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                slider.animate().scaleY(1.05f).setDuration(100).start();
            }
            @Override
            public void onStopTrackingTouch(Slider slider) {
                slider.animate().scaleY(1f).setDuration(100).start();
                save(key, (int) slider.getValue());
            }
        });

        root.addView(text(label, 16, true), lp(24));
        root.addView(value, lp(2));
        root.addView(slider, lp(4));
        return slider;
    }

    private void save(final String key, final int p) {
        prefs.edit().putInt(key, p).apply();
        new Thread(() -> {
            final boolean ok = writeSetting(key, p);
            final String shown = Keys.MODE.equals(key) ? t("режим ", "mode ") + (p + 1) : p + "%";
            runOnUiThread(() -> {
                status.setText(ok
                        ? t("Сохранено: ", "Saved: ") + shown
                        : t("Ошибка записи. Проверьте root.", "Write error. Check root."));
                if (!ok) status.performHapticFeedback(HapticFeedbackConstants.REJECT);
            });
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
        ring.setStroke(dp(1), MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        help.setBackground(ring);
        help.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(24), dp(24));
        p.setMarginStart(dp(4));
        help.setLayoutParams(p);
        addPressPhysics(help);
        help.setOnClickListener(v -> showHelp(helpTitle, helpText));
        row.addView(help);
        return row;
    }

    private void showHelp(String title, String message) {
        new MaterialAlertDialogBuilder(this)
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
'@
[IO.File]::WriteAllText('app\src\main\java\dev\shadealpha\SettingsActivity.java', $settingsCode, [Text.UTF8Encoding]::new($false))

Write-Host "✅ Вибрация убрана с кнопок, BottomSheet сделан выше, логотип пружинит без обрезки!"