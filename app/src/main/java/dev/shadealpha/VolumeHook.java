package dev.shadealpha;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": volume hook failed: " + t);
        }
    }

    private static void apply(Object self) {
        try {
            if (!(self instanceof Dialog)) return;
            Dialog d = (Dialog) self;
            Context ctx = d.getContext();
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
            setBackgroundAlpha(ringer, f);
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
