package dev.shadealpha;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class BouncerHook {

    private static final String TAG = "ShadeAlpha";
    private static final String CONTAINER = "com.android.keyguard.KeyguardSecurityContainer";

    static final int STATE_OTHER = 0;
    static final int STATE_BOUNCER = 1;
    static final int STATE_BOUNCER_SCRIMMED = 2;
    static final int STATE_KEYGUARD = 3;
    static final int STATE_UNLOCKED = 4;

    private static volatile WeakReference<View> containerRef = null;
    private static volatile int scrimState = STATE_OTHER;
    private static volatile float shadeExpansionFraction = 0f;
    private static volatile boolean bouncerToGoneTransitionRunning = false;
    private static volatile long unlockTransitionStartedAt = 0;
    private static volatile boolean logged = false;
    private static int errors = 0;

    private BouncerHook() {}

    public static void hook(ClassLoader cl) {
        hookScrimState(cl);
        hookShadeExpansion(cl);
        hookScrimBehindTransparent(cl);
        hookKeyguardFadeOut(cl);
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTAINER, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": BOUNCER_HOOK_MISSING class=" + CONTAINER);
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "reloadBackgroundColor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            apply(param);
                        }
                    });
            XposedBridge.log(TAG + ": BOUNCER_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": BOUNCER_HOOK_ERROR " + t);
        }
    }

    public static int scrimState() { return scrimState; }
    public static boolean isBouncerToGoneTransitionRunning() { return bouncerToGoneTransitionRunning; }
    public static boolean isShadeExpanded() { return shadeExpansionFraction > 0.05f; }

    public static boolean shouldForceUnlockScrimZero() {
        if (scrimState == STATE_UNLOCKED) return true;
        long now = SystemClock.uptimeMillis();
        return unlockTransitionStartedAt > 0 && (now - unlockTransitionStartedAt) < 1200;
    }

    public static float visibility() {
        WeakReference<View> ref = containerRef;
        View v = ref == null ? null : ref.get();
        if (v == null || !v.isShown()) return 0f;
        float a = 1f;
        for (int i = 0; i < 12 && v != null; i++) {
            a *= v.getAlpha();
            Object p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return Math.max(0f, Math.min(1f, a));
    }

    static void apply(XC_MethodHook.MethodHookParam param) {
        try {
            Object thiz = param.thisObject;
            if (!(thiz instanceof View)) return;
            View view = (View) thiz;
            containerRef = new WeakReference<>(view);
            float factor = Prefs.bouncer(view.getContext());
            if (factor >= 0.999f) return;
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                ColorDrawable cd = (ColorDrawable) bg;
                int c = cd.getColor();
                int a = Math.round(Color.alpha(c) * factor);
                int nc = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
                if (c != nc) {
                    cd.setColor(nc);
                    if (!logged) {
                        logged = true;
                        XposedBridge.log(TAG + ": BOUNCER_BACKGROUND_APPLIED original=#"
                                + Integer.toHexString(c) + " factor=" + factor);
                    }
                }
            } else if (bg != null) {
                int da = Math.round(255 * factor);
                if (bg.getAlpha() != da) bg.setAlpha(da);
            } else {
                if (Math.abs(view.getAlpha() - factor) > 0.01f) view.setAlpha(factor);
            }
        } catch (Throwable t) {
            if (++errors <= 5) XposedBridge.log(TAG + ": BOUNCER_APPLY_ERROR " + t);
        }
    }

    private static void hookScrimState(ClassLoader cl) {
        try {
            String[] candidates = {
                    "com.android.systemui.statusbar.phone.ScrimController",
                    "com.android.systemui.scrim.ScrimController"
            };
            Class<?> cls = null;
            for (String n : candidates) {
                cls = XposedHelpers.findClassIfExists(n, cl);
                if (cls != null) break;
            }
            if (cls == null) {
                XposedBridge.log(TAG + ": SCRIM_CONTROLLER_MISSING");
                return;
            }
            for (String method : new String[]{"transitionTo", "setState", "applyState"}) {
                try {
                    Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, method,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    onTransition(param.thisObject, param.args);
                                }
                            });
                    if (hooks != null && !hooks.isEmpty()) {
                        XposedBridge.log(TAG + ": SCRIM_STATE_HOOK " + method + " methods=" + hooks.size());
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SCRIM_STATE_HOOK_ERROR " + t);
        }
    }

    static void onTransition(Object controller, Object[] args) {
        try {
            int code = STATE_OTHER;
            String name = "other";
            if (args != null) {
                for (Object a : args) {
                    if (a == null) continue;
                    String s = a.toString();
                    if (s.contains("BOUNCER") && s.contains("SCRIMMED")) {
                        code = STATE_BOUNCER_SCRIMMED; name = "BOUNCER_SCRIMMED";
                    } else if (s.contains("BOUNCER")) {
                        code = STATE_BOUNCER; name = "BOUNCER";
                    } else if (s.contains("KEYGUARD")) {
                        code = STATE_KEYGUARD; name = "KEYGUARD";
                    } else if (s.contains("UNLOCKED") || s.contains("OFF") || s.contains("AOD")) {
                        code = STATE_UNLOCKED; name = s;
                    }
                }
            }
            try {
                Object st = XposedHelpers.getObjectField(controller, "mState");
                if (st != null) {
                    String ss = st.toString();
                    if (ss.contains("BOUNCER") && ss.contains("SCRIMMED")) {
                        code = STATE_BOUNCER_SCRIMMED; name = "BOUNCER_SCRIMMED";
                    } else if (ss.contains("BOUNCER")) {
                        code = STATE_BOUNCER; name = "BOUNCER";
                    } else if (ss.contains("KEYGUARD")) {
                        code = STATE_KEYGUARD; name = "KEYGUARD";
                    } else if (ss.contains("UNLOCKED")) {
                        code = STATE_UNLOCKED; name = "UNLOCKED";
                    }
                }
            } catch (Throwable ignored) {}

            if (code != scrimState) {
                scrimState = code;
                if (code == STATE_UNLOCKED) {
                    unlockTransitionStartedAt = SystemClock.uptimeMillis();
                } else if (code == STATE_KEYGUARD || code == STATE_BOUNCER || code == STATE_BOUNCER_SCRIMMED) {
                    unlockTransitionStartedAt = 0;
                }
                XposedBridge.log(TAG + ": STATE_TRANSITION state=" + name + " code=" + code
                        + " bouncerVisibility=" + visibility());
            }
            try {
                Object f = XposedHelpers.getObjectField(controller, "mIsBouncerToGoneTransitionRunning");
                if (f instanceof Boolean) bouncerToGoneTransitionRunning = (Boolean) f;
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            if (++errors <= 5) XposedBridge.log(TAG + ": STATE_TRANSITION_ERROR " + t);
        }
    }

    private static void hookShadeExpansion(ClassLoader cl) {
        try {
            String[] candidates = {
                    "com.android.systemui.shade.ShadeExpansionStateManager",
                    "com.android.systemui.statusbar.phone.NotificationPanelViewController",
                    "com.android.systemui.shade.NotificationPanelViewController"
            };
            for (String name : candidates) {
                Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
                if (cls == null) continue;
                for (String m : new String[]{"setExpandedFraction", "onExpansionChanged", "setExpansionFraction"}) {
                    try {
                        XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    Float f = null;
                                    try {
                                        Object v = XposedHelpers.getObjectField(param.thisObject, "mExpandedFraction");
                                        if (v instanceof Float) f = (Float) v;
                                    } catch (Throwable ignored) {}
                                    if (f == null && param.args != null && param.args.length > 0 && param.args[0] instanceof Float) {
                                        f = (Float) param.args[0];
                                    }
                                    if (f != null) {
                                        float v = Math.max(0f, Math.min(1f, f));
                                        if (Math.abs(v - shadeExpansionFraction) >= 0.01f) shadeExpansionFraction = v;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        });
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SHADE_EXPANSION_HOOK_ERROR " + t);
        }
    }

    private static void hookScrimBehindTransparent(ClassLoader cl) {
        for (String name : new String[]{
                "com.android.systemui.scrim.ScrimView",
                "com.android.systemui.statusbar.ScrimView"
        }) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
                if (cls == null) continue;
                XposedBridge.hookAllMethods(cls, "setViewAlpha", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args == null || param.args.length != 1 || !(param.args[0] instanceof Float)) return;
                            int st = scrimState;
                            boolean force = shouldForceUnlockScrimZero()
                                    || st == STATE_BOUNCER || st == STATE_BOUNCER_SCRIMMED;
                            if (!force) return;
                            View v = (View) param.thisObject;
                            String id = idName(v);
                            if ("scrim_behind".equals(id) || "scrim_in_front".equals(id) || id.contains("behind")) {
                                param.args[0] = 0f;
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private static void hookKeyguardFadeOut(ClassLoader cl) {
        try {
            String[] candidates = {
                    "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager",
                    "com.android.systemui.keyguard.KeyguardViewMediator"
            };
            for (String name : candidates) {
                Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
                if (cls == null) continue;
                try {
                    XposedBridge.hookAllMethods(cls, "animateKeyguardFadingOut", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args != null) {
                                    for (int i = 0; i < param.args.length; i++) {
                                        if (param.args[i] instanceof Long) param.args[i] = 0L;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": UNLOCK_FADE_HOOK_ERROR " + t);
        }
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "(no-id)";
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return "(unknown)"; }
    }
}
