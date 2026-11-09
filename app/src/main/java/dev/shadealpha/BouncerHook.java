package dev.shadealpha;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Прозрачность экрана ввода PIN / пароля / графического ключа (bouncer) в SystemUI.
 *
 * KeyguardSecurityContainer.reloadBackgroundColor() задаёт фон контейнера цветом из темы.
 * Система зовёт его при создании, смене темы и при КАЖДОМ показе экрана (prepareToShow),
 * поэтому достаточно подправить альфу после него: ничего не рисуем и не опрашиваем лишний раз.
 *
 * Работает для классического (View) bouncer. Compose-версия (scene container) этим хуком
 * не затрагивается — в логе тогда не будет строки "first hit".
 */
final class BouncerHook {

    private static final String TAG = "ShadeAlpha";
    private static final String CONTAINER = "com.android.keyguard.KeyguardSecurityContainer";

    private static volatile boolean logged;
    private static int errors;

    private BouncerHook() {}

    static void hook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTAINER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": bouncer: " + CONTAINER + " not found");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "reloadBackgroundColor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p);
                        }
                    });
            XposedBridge.log(TAG + ": hooked bouncer x" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer hook failed: " + t);
        }
    }

    private static void apply(XC_MethodHook.MethodHookParam p) {
        try {
            if (!(p.thisObject instanceof View)) return;
            View v = (View) p.thisObject;
            float f = Prefs.bouncer(v.getContext());
            Drawable bg = v.getBackground();
            if (!logged) {
                logged = true;
                XposedBridge.log(TAG + ": bouncer first hit factor=" + f
                        + " background=" + (bg == null ? "null" : bg.getClass().getSimpleName()));
            }
            if (f >= 0.999f || !(bg instanceof ColorDrawable)) return;
            int c = ((ColorDrawable) bg).getColor();
            int a = Math.max(0, Math.min(255, Math.round(Color.alpha(c) * f)));
            int n = (c & 0x00FFFFFF) | (a << 24);
            if (n != c) v.setBackgroundColor(n);
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": bouncer apply failed: " + t);
        }
    }
}
