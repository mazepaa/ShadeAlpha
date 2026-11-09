package dev.shadealpha;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Прозрачность фона PIN / пароля / графического ключа в SystemUI.
 *
 * Android 16 может обновлять фон KeyguardSecurityContainer через reloadColors(),
 * enableTransparentMode()/disableTransparentMode(), а на отдельных сборках —
 * через reloadBackgroundColor(). Поэтому обрабатываем все доступные пути.
 * Дополнительно применяем значение после prepareToShow(), то есть непосредственно
 * перед показом экрана ввода. Альфа самого View не меняется — анимации SystemUI
 * и содержимое PIN-панели остаются нетронутыми.
 */
final class BouncerHook {

    private static final String TAG = "ShadeAlpha";
    private static final String CONTAINER = "com.android.keyguard.KeyguardSecurityContainer";
    private static final String CONTROLLER = "com.android.keyguard.KeyguardSecurityContainerController";
    private static final String ORIGINAL_ALPHA = "shadealpha_bouncer_original_alpha";

    private static volatile boolean logged;
    private static int errors;

    private BouncerHook() {}

    static void hook(ClassLoader cl) {
        hookContainer(cl);
        hookPrepareToShow(cl);
    }

    private static void hookContainer(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTAINER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": bouncer: " + CONTAINER + " not found");
                return;
            }

            int total = 0;
            total += hookReapplyMethod(cls, "reloadBackgroundColor");
            total += hookReapplyMethod(cls, "reloadColors");
            total += hookReapplyMethod(cls, "enableTransparentMode");
            total += hookReapplyMethod(cls, "disableTransparentMode");

            Set<XC_MethodHook.Unhook> constructors = XposedBridge.hookAllConstructors(cls,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p.thisObject);
                        }
                    });
            total += constructors == null ? 0 : constructors.size();

            XposedBridge.log(TAG + ": hooked bouncer refresh paths x" + total);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer hook failed: " + t);
        }
    }

    private static int hookReapplyMethod(Class<?> cls, String methodName) {
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p.thisObject);
                        }
                    });
            return hooks == null ? 0 : hooks.size();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer " + methodName + " hook failed: " + t);
            return 0;
        }
    }

    /** Reapply the selected opacity immediately before the bouncer becomes visible. */
    private static void hookPrepareToShow(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTROLLER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": bouncer controller not found");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "prepareToShow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object view = XposedHelpers.getObjectField(p.thisObject, "mView");
                                if (view instanceof View) apply(view);
                            } catch (Throwable t) {
                                if (++errors <= 3) XposedBridge.log(TAG + ": bouncer prepareToShow failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked bouncer prepareToShow x" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer controller hook failed: " + t);
        }
    }

    private static void apply(Object target) {
        try {
            if (!(target instanceof View)) return;
            View view = (View) target;
            float factor = Prefs.bouncer(view.getContext());
            Drawable current = view.getBackground();

            if (!logged) {
                logged = true;
                XposedBridge.log(TAG + ": bouncer first hit factor=" + factor
                        + " background=" + (current == null ? "null" : current.getClass().getSimpleName()));
            }
            if (current == null) return;

            Drawable background = current.mutate();
            if (background != current) view.setBackground(background);

            // Keep the original drawable alpha independently from the slider value.
            // This avoids alpha accumulating after repeated bouncer show/hide cycles.
            Object saved = XposedHelpers.getAdditionalInstanceField(background, ORIGINAL_ALPHA);
            int originalAlpha;
            if (saved instanceof Integer) {
                originalAlpha = ((Integer) saved).intValue();
            } else {
                originalAlpha = background.getAlpha();
                XposedHelpers.setAdditionalInstanceField(background, ORIGINAL_ALPHA,
                        Integer.valueOf(originalAlpha));
            }

            float clamped = Math.max(0f, Math.min(1f, factor));
            int alpha = Math.max(0, Math.min(255, Math.round(originalAlpha * clamped)));
            if (background.getAlpha() != alpha) background.setAlpha(alpha);
        } catch (Throwable t) {
            if (++errors <= 3) XposedBridge.log(TAG + ": bouncer apply failed: " + t);
        }
    }
}
