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

    private static volatile boolean logged;
    private static int errors;

    private BouncerHook() {}

    static void hook(ClassLoader cl) {
        hookContainer(cl);
        hookPrepareToShow(cl);
        hookExpansion(cl);
        hookScrimController(cl);
    }

    /**
     * ScrimController blends Monet tints while KEYGUARD transitions to the primary
     * bouncer. Hook the final alpha calculation itself so the PIN opacity is applied
     * from the first swipe frame, for every rendering mode.
     */
    private static void hookScrimController(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(SCRIM_CONTROLLER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": scrim controller not found: " + SCRIM_CONTROLLER);
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "updateScrimColor",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            applyScrimOpacity(p);
                        }
                    });
            XposedBridge.log(TAG + ": hooked bouncer scrim alpha x" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer scrim hook failed: " + t);
        }
    }

    private static void applyScrimOpacity(XC_MethodHook.MethodHookParam p) {
        try {
            if (p.args == null || p.args.length < 1 || !(p.args[0] instanceof View)) return;

            Object controller = p.thisObject;
            Object state = XposedHelpers.getObjectField(controller, "mState");
            String stateName = state == null ? "" : state.toString();

            boolean inTransit = false;
            try {
                Object manager = XposedHelpers.getObjectField(
                        controller, "mStatusBarKeyguardViewManager");
                inTransit = Boolean.TRUE.equals(
                        XposedHelpers.callMethod(manager, "isPrimaryBouncerInTransit"));
            } catch (Throwable ignored) {
                // Some vendor SystemUI versions do not expose the transition query.
            }

            boolean bouncerState = "BOUNCER".equals(stateName)
                    || "BOUNCER_SCRIMMED".equals(stateName);
            if (!bouncerState && !inTransit) {
                activeBouncerScrim = null;
                return;
            }

            boolean clipsQsScrim = false;
            try {
                clipsQsScrim = Boolean.TRUE.equals(
                        XposedHelpers.getObjectField(controller, "mClipsQsScrim"));
            } catch (Throwable ignored) { }

            View target;
            if ("BOUNCER_SCRIMMED".equals(stateName)) {
                Object front = XposedHelpers.getObjectField(controller, "mScrimInFront");
                target = front instanceof View ? (View) front : null;
            } else {
                // When QS clips the scrim, the notification scrim is the visible
                // background layer; otherwise it is the regular behind scrim.
                String field = clipsQsScrim ? "mNotificationsScrim" : "mScrimBehind";
                Object behind = XposedHelpers.getObjectField(controller, field);
                target = behind instanceof View ? (View) behind : null;
            }

            activeBouncerScrim = target;
            if (!logged && target != null) {
                logged = true;
                XposedBridge.log(TAG + ": bouncer scrim tracked state=" + stateName
                        + " transit=" + inTransit + " target=" + target.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            activeBouncerScrim = null;
            if (++errors <= 3) XposedBridge.log(TAG + ": bouncer scrim tracking failed: " + t);
        }
    }

    /** ShadeAlpha's three rendering modes apply this value in their normal draw path. */
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

    /**
     * The PIN container is animated into view while the lockscreen is being swiped up.
     * Reapply the drawable alpha on every expansion update so the Monet-colored bouncer
     * background is already transparent during the transition, not only after PIN appears.
     */
    private static void hookExpansion(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTROLLER, cl);
            if (cls == null) return;
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "setExpansion",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object view = XposedHelpers.getObjectField(p.thisObject, "mView");
                                if (view instanceof View) apply(view);
                            } catch (Throwable t) {
                                if (++errors <= 3) XposedBridge.log(TAG + ": bouncer expansion apply failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked bouncer expansion x" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bouncer expansion hook failed: " + t);
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
