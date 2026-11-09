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
    private static final String SCRIM_CONTROLLER = "com.android.systemui.statusbar.phone.ScrimController";
    private static final String ORIGINAL_ALPHA = "shadealpha_bouncer_original_alpha";

    // Scrim behind the PIN panel is separate from KeyguardSecurityContainer's own background.
    // In Android 16 it can retain its own dim value unless we use the PIN opacity for it too.
    private static volatile View activeBouncerScrim;
    private static volatile String lastScrimState = "";

    private static volatile boolean logged;
    private static int errors;

    private BouncerHook() {}

    static void hook(ClassLoader cl) {
        hookContainer(cl);
        hookPrepareToShow(cl);
        hookScrimController(cl);
    }

    /**
     * Track which SystemUI scrim currently belongs to the PIN bouncer.
     * Normal BOUNCER uses the behind scrim; BOUNCER_SCRIMMED (over a
     * show-when-locked activity) uses the front scrim.
     */
    private static void hookScrimController(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(SCRIM_CONTROLLER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": scrim controller not found: " + SCRIM_CONTROLLER);
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "updateScrims",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            refreshBouncerScrim(p.thisObject);
                        }
                    });
            XposedBridge.log(TAG + ": hooked bouncer scrim state x" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer scrim hook failed: " + t);
        }
    }

    private static void refreshBouncerScrim(Object controller) {
        try {
            Object state = XposedHelpers.getObjectField(controller, "mState");
            String name = state == null ? "" : state.toString();
            View target = null;
            if ("BOUNCER".equals(name)) {
                Object v = XposedHelpers.getObjectField(controller, "mScrimBehind");
                if (v instanceof View) target = (View) v;
            } else if ("BOUNCER_SCRIMMED".equals(name)) {
                Object v = XposedHelpers.getObjectField(controller, "mScrimInFront");
                if (v instanceof View) target = (View) v;
            }
            activeBouncerScrim = target;
            if (!name.equals(lastScrimState)) {
                lastScrimState = name;
                XposedBridge.log(TAG + ": scrim state=" + name
                        + " bouncerScrim=" + (target == null ? "none" : target.getClass().getSimpleName()));
            }
        } catch (Throwable t) {
            activeBouncerScrim = null;
            if (++errors <= 3) XposedBridge.log(TAG + ": bouncer scrim state read failed: " + t);
        }
    }

    /** Returns -1 when this View is not the scrim used by the active PIN state. */
    static float scrimFactor(View view) {
        return activeBouncerScrim == view ? Prefs.bouncer(view.getContext()) : -1f;
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
