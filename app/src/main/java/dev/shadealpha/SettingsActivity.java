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
import android.transition.AutoTransition;
import android.transition.TransitionManager;
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
import android.widget.Toast;

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
import java.util.concurrent.TimeUnit;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "ui";
    private static final String KEY_LANG = "lang";
    private static final String TG_LINK = "https://t.me/mazepaa";
    private static final String GITHUB_LINK = "https://github.com/mazepaa/ShadeAlpha";
    /** Upper bound for one su invocation; a stuck root prompt must not block the worker. */
    private static final int ROOT_TIMEOUT_SEC = 10;
    private static final String[] ALL_KEYS = {
            Keys.TOP, Keys.BOTTOM, Keys.BOUNCER, Keys.RECENTS, Keys.APPDRAWER,
            Keys.VOLUME, Keys.VOL_TRACK, Keys.VOL_FILL, Keys.MODE
    };

    private TextView hooksNote;

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
        updateHookStatus();
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
        Process pr = null;
        try {
            pr = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            if (!pr.waitFor(ROOT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                pr.destroy();
                return false;
            }
            return pr.exitValue() == 0;
        } catch (Throwable e) {
            if (pr != null) pr.destroy();
            return false;
        }
    }

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
        
        // Уменьшили верхний отступ с 56 до 50dp (чуть ниже)
        root.setPadding(dp(24), dp(35), dp(24), dp(32));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView icon = new ImageView(this);
        icon.setImageResource(getApplicationInfo().icon);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        
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

        String ver = "";
        try {
            ver = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) { }
        TextView version = text(ver, 14, false);
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

        addPressPhysics(devCard);
        devCard.setOnClickListener(v -> {
            Toast.makeText(this, "<3", Toast.LENGTH_SHORT).show();
        });

        root.addView(devCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = text(t(
            "Модуль для LSPosed: полупрозрачная шторка уведомлений, недавние приложения и список приложений в Pixel Launcher.",
            "LSPosed module: translucent notification shade, recents and app drawer in Pixel Launcher."), 14, false);
        desc.setPadding(0, dp(24), 0, 0);
        desc.setGravity(Gravity.CENTER);
        root.addView(desc);

        dialog.setContentView(root);
        
        ViewGroup bottomSheet = (ViewGroup) dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setClipChildren(false);
            bottomSheet.setClipToPadding(false);
        }
        
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

        TextView title = text(t("Прозрачность", "Transparency"), 24, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(title, titleLp);

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
            statusText.performHapticFeedback(HapticFeedbackConstants.REJECT);
        }
        root.addView(statusText, lp(8));

        TextView moduleText = text("", 14, true);
        if (Keys.isModuleActive()) {
            moduleText.setText(t("Модуль: Активен ✅", "Module: Active ✅"));
            moduleText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF4CAF50));
        } else {
            moduleText.setText(t("Модуль: Не активен ❌ (включи в LSPosed и добавь ShadeAlpha в область)",
                    "Module: Inactive ❌ (enable it in LSPosed and add ShadeAlpha to the scope)"));
            moduleText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0xFFF44336));
        }
        root.addView(moduleText, lp(2));

        // ---- Шторка уведомлений ----
        final LinearLayout shadeBox = addGroup(root, t("Шторка уведомлений", "Notification shade"), "shade", true);
        topBar = addSlider(shadeBox, t("Верхняя часть (панели быстрых настроек)", "Top area (Quick Settings panels)"), Keys.TOP);
        bottomBar = addSlider(shadeBox, t("Нижняя часть (уведомления)", "Bottom area (notifications)"), Keys.BOTTOM);
        addSlider(shadeBox, t("Экран блокировки (PIN / пароль)", "Lock screen (PIN / password)"), Keys.BOUNCER);

        shadeBox.addView(headerWithHelp(
                t("Режим отрисовки шторки", "Shade rendering mode"),
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
        shadeBox.addView(modes, lp(4));
        shadeBox.addView(text(
                t("После смены режима нажми «Перезапустить SystemUI».", 
                        "After changing the rendering mode, tap \"Restart SystemUI\"."), 13, false), lp(4));

        // ---- Панель громкости ----
        LinearLayout volBox = addGroup(root, t("Панель громкости", "Volume panel"), "volume", false);
        addSlider(volBox, t("Фон панели", "Panel background"), Keys.VOLUME);
        addSlider(volBox, t("Слайдер: дорожка", "Slider: track"), Keys.VOL_TRACK);
        addSlider(volBox, t("Слайдер: заполнение", "Slider: fill"), Keys.VOL_FILL);
        volBox.addView(note(t(
                "Дорожка — неактивная часть слайдера, заполнение — активная. Применяется при следующем показе панели. После первого включения нажми «Перезапустить SystemUI».",
                "Track is the inactive part of the slider, fill is the active part. Applied the next time the panel is shown. After enabling it for the first time, tap \"Restart SystemUI\".")), lp(8));

        // ---- Лаунчер ----
        LinearLayout launcherBox = addGroup(root, t("Лаунчер (Pixel Launcher)", "Launcher (Pixel Launcher)"), "launcher", false);
        addSlider(launcherBox, t("Недавние приложения", "Recents apps"), Keys.RECENTS);
        addSlider(launcherBox, t("Список приложений", "App drawer"), Keys.APPDRAWER);
        launcherBox.addView(note(t(
                "Работает при включённом модуле для Pixel Launcher в LSPosed. После первого включения нажми «Перезапустить лаунчер».",
                "Needs the module enabled for Pixel Launcher in LSPosed. After enabling it for the first time, tap \"Restart launcher\".")), lp(8));

        // ---- Действия ----
        LinearLayout actions = addGroup(root, t("Действия", "Actions"), "actions", true);

        MaterialButton showShade = actionButton(t("Показать шторку", "Show shade"), v -> new Thread(() -> {
            runRoot("cmd statusbar expand-notifications || cmd statusbar expand-settings");
        }).start());
        MaterialButton showRecents = actionButton(t("Показать недавние", "Show recents"),
                v -> new Thread(() -> runRoot("input keyevent 187")).start());
        MaterialButton restart = actionButton(t("Перезапустить SystemUI", "Restart SystemUI"), v -> new Thread(() -> {
            runRoot("killall com.android.systemui || pkill -f com.android.systemui");
            runOnUiThread(() -> status.setText(t("Команда перезапуска отправлена.", "Restart command sent.")));
        }).start());
        MaterialButton restartLauncher = actionButton(t("Перезапустить лаунчер", "Restart launcher"), v -> new Thread(() -> {
            runRoot("killall com.google.android.apps.nexuslauncher; killall com.android.launcher3");
            runOnUiThread(() -> status.setText(t("Лаунчер перезапущен.", "Launcher restarted.")));
        }).start());
        addButtonRow(actions, showShade, showRecents, 16);
        addButtonRow(actions, restart, restartLauncher, 8);

        MaterialButton reset = actionButton(t("Сбросить значения", "Reset values"), v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(t("Сбросить значения?", "Reset values?"))
                        .setMessage(t("Все ползунки вернутся к 100%. Значения хранятся в Settings.System и переживают удаление приложения, поэтому их нужно удалять отдельно.",
                                "All sliders go back to 100%. The values live in Settings.System and survive uninstalling the app, so they are deleted separately."))
                        .setNegativeButton(t("Отмена", "Cancel"), null)
                        .setPositiveButton(t("Сбросить", "Reset"), (d, w) -> new Thread(() -> {
                            for (String key : ALL_KEYS) runRoot("settings delete system " + key);
                            prefs.edit().clear().apply();
                            runOnUiThread(() -> {
                                status.setText(t("Значения сброшены. Перезапусти SystemUI.",
                                        "Values reset. Restart SystemUI."));
                                recreate();
                            });
                        }).start())
                        .show());
        addWideButton(actions, reset, 8);

        status = text("", 14, false);
        root.addView(status, lp(12));

        hooksNote = note("");
        root.addView(hooksNote, lp(6));

        setContentView(scroll);
    }

    private TextView note(String s) {
        TextView tv = text(s, 13, false);
        tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        return tv;
    }

    private MaterialButton actionButton(String label, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setTextSize(13);
        addPressPhysics(b);
        b.setOnClickListener(l);
        return b;
    }

    private void addButtonRow(LinearLayout box, View left, View right, int topDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMarginEnd(dp(4));
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r.setMarginStart(dp(4));
        row.addView(left, l);
        row.addView(right, r);
        box.addView(row, lp(topDp));
    }

    private void addWideButton(LinearLayout box, View button, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        box.addView(button, p);
    }

    /**
     * Сворачиваемая карточка-группа (Material 3, filled card). Возвращает контейнер для содержимого.
     * Состояние «открыта/закрыта» запоминается между запусками.
     */
    private LinearLayout addGroup(final LinearLayout root, String title, String id, boolean defaultOpen) {
        final MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer, 0x22888888));
        root.addView(card, lp(12));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(16));
        header.setClickable(true);
        header.setFocusable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        header.setBackgroundResource(ripple.resourceId);

        TextView titleTv = text(title, 17, true);
        titleTv.setGravity(Gravity.CENTER);
        header.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(header);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), 0, dp(20), dp(16));
        col.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final String prefKey = "grp_" + id;
        final boolean[] open = {prefs.getBoolean(prefKey, defaultOpen)};
        body.setVisibility(open[0] ? View.VISIBLE : View.GONE);

        header.setOnClickListener(v -> {
            open[0] = !open[0];
            prefs.edit().putBoolean(prefKey, open[0]).apply();
            TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(180));
            body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        });
        return body;
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
            
            if (fromUser && Math.abs(v - lastVal[0]) >= 4.9f) {
                s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                lastVal[0] = v;
            }

            boolean showWarning = key.equals(Keys.TOP) || key.equals(Keys.BOTTOM);
                if (p < 20 && fromUser && showWarning) {
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

    private void updateHookStatus() {
        if (hooksNote == null) return;
        int mask = readSystem(Keys.HOOKS, 0);
        boolean sysUi = (mask & HookStatus.REPORTED_SYSTEM_UI) != 0;
        boolean launcher = (mask & HookStatus.REPORTED_LAUNCHER) != 0;
        if (!sysUi && !launcher) {
            hooksNote.setText(t("Хуки: нет данных — перезапусти SystemUI и лаунчер",
                    "Hooks: no data yet — restart SystemUI and the launcher"));
            return;
        }

        StringBuilder sb = new StringBuilder(t("Хуки:", "Hooks:"));
        sb.append('\n').append(hookLine(t("Шторка", "Shade"), HookStatus.SHADE, mask, sysUi));
        sb.append('\n').append(hookLine(t("Экран блокировки", "Lock screen"), HookStatus.BOUNCER, mask, sysUi));
        sb.append('\n').append(hookLine(t("Панель громкости", "Volume panel"), HookStatus.VOLUME, mask, sysUi));
        sb.append('\n').append(hookLine(t("Ползунок громкости", "Volume slider"), HookStatus.VOLUME_TRACK, mask, sysUi));
        sb.append('\n').append(hookLine(t("Недавние", "Recents"), HookStatus.LAUNCHER, mask, launcher));
        sb.append('\n').append(hookLine(t("Список приложений", "App drawer"), HookStatus.DRAWER, mask, launcher));
        hooksNote.setText(sb.toString());
    }

    private String hookLine(String name, int bit, int mask, boolean reported) {
        String state;
        if (!reported) {
            state = t("нет данных", "no data");
        } else if ((mask & bit) != 0) {
            state = t("работает", "ok");
        } else {
            state = t("не найден", "not found");
        }
        return "• " + name + ": " + state;
    }

    private void syncToSystem() {
        new Thread(() -> {
            for (String key : ALL_KEYS) {
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

    /**
     * Runs one shell command through su. Bounded by a timeout: a root manager that never answers
     * the prompt must not hang the worker thread forever.
     */
    private void runRoot(String cmd) {
        Process pr = null;
        try {
            pr = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            pr.getOutputStream().close();
            if (!pr.waitFor(ROOT_TIMEOUT_SEC, TimeUnit.SECONDS)) pr.destroy();
        } catch (Throwable e) {
            if (pr != null) pr.destroy();
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