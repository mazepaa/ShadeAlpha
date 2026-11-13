package dev.shadealpha;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Прозрачность панели громкости (новая реализация Android 16, com.android.systemui.volume.dialog).
 *
 * Фон панели, подложка ряда кнопок звонка и «плавающих» слайдеров — это обычные View с
 * GradientDrawable. Достаточно выставить альфу самого drawable (View.alpha не трогаем,
 * её анимирует система). Сами дорожки слайдеров рисует Compose, их этот хук не затрагивает.
 *
 * Применяется при каждом показе панели (VolumeDialog.onStart), поэтому значение слайдера
 * подхватывается сразу. Плавающие слайдеры создаются позже, их ловим по добавлению в контейнер.
 */
final class VolumeHook {

    private static final String TAG = "ShadeAlpha";
    private static final String PKG = "com.android.systemui";
    private static final String DIALOG = "com.android.systemui.volume.dialog.VolumeDialog";
    private static final String TRACK_KT = "com.android.systemui.volume.dialog.sliders.ui.compose.VolumeDialogSliderTrackKt";
    private static final String SLIDER_COLORS = "androidx.compose.material3.SliderColors";
    // active, disabledActive, inactive, disabledInactive
    private static final String[] COLOR_FIELDS = {
            "activeTrackColor", "disabledActiveTrackColor", "inactiveTrackColor", "disabledInactiveTrackColor"
    };
    private static Field[] colorFields;
    private static boolean colorFieldsFailed;
    private static volatile boolean trackLogged;

    private static volatile int idBackground;
    private static volatile int idRinger;
    private static volatile int idFloating;
    private static volatile boolean logged;
    private static int errors;

    private VolumeHook() {}

    static void hook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(DIALOG, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": volume: " + DIALOG + " not found");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "onStart",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p.thisObject);
                        }
                    });
            XposedBridge.log(TAG + ": hooked volume dialog x" + (hooks == null ? 0 : hooks.size()));
            if (hooks != null && !hooks.isEmpty()) HookStatus.mark(HookStatus.VOLUME);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": volume hook failed: " + t);
        }
        hookSliderTrack(cl);
    }

    /**
     * Дорожку и заполнение слайдера рисует Compose (Material3 Slider). Цвета приходят в SliderTrack(...)
     * внутри объекта SliderColors (поля типа long — упакованный Compose Color). Имя функции содержит
     * хэш сигнатуры, поэтому ищем все методы, начинающиеся с "SliderTrack".
     */
    private static void hookSliderTrack(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(TRACK_KT, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": volume slider: " + TRACK_KT + " not found");
                return;
            }
            Set<String> names = new HashSet<String>();
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().startsWith("SliderTrack")) names.add(m.getName());
            }
            int n = 0;
            for (String name : names) {
                Set<XC_MethodHook.Unhook> h = XposedBridge.hookAllMethods(cls, name, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        recolor(p.args);
                    }
                });
                n += h == null ? 0 : h.size();
            }
            XposedBridge.log(TAG + ": hooked volume slider track x" + n);
            if (n > 0) HookStatus.mark(HookStatus.VOLUME_TRACK);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": volume slider hook failed: " + t);
        }
    }

    private static void recolor(Object[] args) {
        if (args == null) return;
        for (Object a : args) {
            if (a != null && SLIDER_COLORS.equals(a.getClass().getName())) {
                applyColors(a);
                return;
            }
        }
    }

    /** Меняет альфу цветов дорожки/заполнения. Оригиналы помним на объекте — повторные вызовы не накапливаются. */
    static void applyColors(Object sc) {
        if (Experiment.bypass()) return;
        try {
            Field[] fs = colorFields(sc.getClass());
            if (fs == null) return;
            float fill = Prefs.volumeFill(null);
            float track = Prefs.volumeTrack(null);
            float[] f = {fill, fill, track, track};

            long[] cur = new long[4];
            for (int i = 0; i < 4; i++) cur[i] = fs[i].getLong(sc);
            long[] st = (long[]) XposedHelpers.getAdditionalInstanceField(sc, "shadealpha_slider");
            if (st == null) {
                st = new long[8];
                for (int i = 0; i < 4; i++) {
                    st[i] = cur[i];
                    st[4 + i] = cur[i];
                }
                XposedHelpers.setAdditionalInstanceField(sc, "shadealpha_slider", st);
            } else {
                for (int i = 0; i < 4; i++) {
                    if (cur[i] != st[4 + i]) st[i] = cur[i]; // цвет поменяла система: это новый оригинал
                }
            }
            if (!trackLogged) {
                trackLogged = true;
                XposedBridge.log(TAG + ": volume slider first hit fill=" + fill + " track=" + track);
            }
            for (int i = 0; i < 4; i++) {
                long nv = (f[i] >= 0.999f) ? st[i] : scaleComposeColor(st[i], f[i]);
                if (nv != cur[i]) fs[i].setLong(sc, nv);
                st[4 + i] = nv;
            }
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": volume slider colors failed: " + t);
        }
    }

    private static Field[] colorFields(Class<?> cls) {
        if (colorFields != null) return colorFields;
        if (colorFieldsFailed) return null;
        try {
            Field[] fs = new Field[4];
            for (int i = 0; i < 4; i++) {
                fs[i] = cls.getDeclaredField(COLOR_FIELDS[i]);
                fs[i].setAccessible(true);
            }
            colorFields = fs;
            return fs;
        } catch (Throwable t) {
            colorFieldsFailed = true;
            XposedBridge.log(TAG + ": volume slider: SliderColors fields not found: " + t);
            return null;
        }
    }

    /** Compose Color упакован в Long: sRGB = a8 r8 g8 b8 в старших 32 битах, иначе альфа — 10 бит в позициях 6..15. */
    static long scaleComposeColor(long v, float f) {
        if ((v & 0x3FL) == 0L) {
            int a = (int) ((v >>> 56) & 0xFFL);
            int na = Math.max(0, Math.min(255, Math.round(a * f)));
            return (v & 0x00FFFFFFFFFFFFFFL) | ((long) na << 56);
        }
        int a = (int) ((v >>> 6) & 0x3FFL);
        int na = Math.max(0, Math.min(1023, Math.round(a * f)));
        return (v & ~(0x3FFL << 6)) | ((long) na << 6);
    }

    private static void apply(Object self) {
        if (Experiment.bypass()) return;
        try {
            if (!(self instanceof Dialog)) return;
            Dialog d = (Dialog) self;
            Context ctx = d.getContext();
            if (HookStatus.pending()) HookStatus.flush(ctx);
            float f = Prefs.volume(ctx);
            resolveIds(ctx);

            View bg = idBackground != 0 ? d.findViewById(idBackground) : null;
            View ringer = idRinger != 0 ? d.findViewById(idRinger) : null;
            View floating = idFloating != 0 ? d.findViewById(idFloating) : null;
            if (!logged) {
                logged = true;
                XposedBridge.log(TAG + ": volume first hit factor=" + f
                        + " background=" + (bg != null) + " ringer=" + (ringer != null)
                        + " floating=" + (floating != null));
            }

            setBackgroundAlpha(bg, f);
            setupRingerCap(ringer, bg, f);
            if (floating instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) floating;
                g.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        setBackgroundAlpha(child, Prefs.volume(child.getContext()));
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) { }
                });
                for (int i = 0; i < g.getChildCount(); i++) setBackgroundAlpha(g.getChildAt(i), f);
            }
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": volume apply failed: " + t);
        }
    }

    /**
     * Подложка ряда кнопок звонка того же цвета, что и фон панели. Она рисует скруглённый «колпачок»
     * панели вокруг кнопки и частично лежит прямо на фоне. В стоке два непрозрачных слоя сливаются,
     * а при полупрозрачности складываются и в зоне перекрытия появляется тёмная полоса.
     * Поэтому при f < 1 рисуем подложку звонка только выше верхнего края фона (колпачок), а фон
     * рисует всё остальное: панель остаётся одним ровным слоем и сохраняет скруглённый верх.
     */
    private static void setupRingerCap(View ringer, View plate, float f) {
        if (ringer == null) return;
        try {
            Drawable d = ringer.getBackground();
            if (d == null) return;
            CapDrawable cap;
            if (d instanceof CapDrawable) {
                cap = (CapDrawable) d;
            } else if (plate != null) {
                cap = new CapDrawable(d, ringer, plate);
                ringer.setBackground(cap);
            } else {
                setBackgroundAlpha(ringer, f);
                return;
            }
            cap.clip = f < 0.999f;
            setBackgroundAlpha(ringer, f);
            ringer.invalidate();
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": volume ringer cap failed: " + t);
        }
    }

    /** Высота «колпачка»: от верха подложки звонка до верха фона панели (в координатах окна). */
    static int capHeight(int ringerTopInWindow, int plateTopInWindow) {
        return plateTopInWindow - ringerTopInWindow;
    }

    private static final class CapDrawable extends DrawableWrapper {
        private final View ringer;
        private final View plate;
        volatile boolean clip;
        // Reused between frames: draw() runs on every panel frame and must not allocate.
        private final int[] ringerPos = new int[2];
        private final int[] platePos = new int[2];
        private int cachedHeight = -1;
        private int cachedRingerTop = Integer.MIN_VALUE;
        private int cachedPlateTop = Integer.MIN_VALUE;

        CapDrawable(Drawable inner, View ringer, View plate) {
            super(inner);
            this.ringer = ringer;
            this.plate = plate;
        }

        /** Positions only change on layout, so the query is skipped while they stay the same. */
        private int capHeightCached() {
            ringer.getLocationInWindow(ringerPos);
            plate.getLocationInWindow(platePos);
            if (ringerPos[1] != cachedRingerTop || platePos[1] != cachedPlateTop) {
                cachedRingerTop = ringerPos[1];
                cachedPlateTop = platePos[1];
                cachedHeight = capHeight(cachedRingerTop, cachedPlateTop);
            }
            return cachedHeight;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!clip) {
                super.draw(canvas);
                return;
            }
            int h = capHeightCached();
            if (h <= 0) return; // фон панели закрывает всё — подложке звонка рисовать нечего
            Rect r = getBounds();
            int save = canvas.save();
            canvas.clipRect(r.left, r.top, r.right, Math.min(r.bottom, r.top + h));
            super.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static void resolveIds(Context ctx) {
        if (idBackground != 0) return;
        try {
            android.content.res.Resources r = ctx.getResources();
            idBackground = r.getIdentifier("volume_dialog_background", "id", PKG);
            idRinger = r.getIdentifier("ringer_buttons_background", "id", PKG);
            idFloating = r.getIdentifier("volume_dialog_floating_sliders_container", "id", PKG);
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": volume ids failed: " + t);
        }
    }

    private static void setBackgroundAlpha(View v, float f) {
        if (v == null) return;
        try {
            Drawable d = v.getBackground();
            if (d == null) return;
            d.mutate().setAlpha(Math.max(0, Math.min(255, Math.round(255f * f))));
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": volume alpha failed: " + t);
        }
    }
}

