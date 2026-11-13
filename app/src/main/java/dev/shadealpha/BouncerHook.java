package dev.shadealpha;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class BouncerHook {
    private static final String TAG = "ShadeAlpha";
    static final int STATE_OTHER = 0, STATE_BOUNCER = 1, STATE_BOUNCER_SCRIMMED = 2, STATE_KEYGUARD = 3, STATE_UNLOCKED = 4;
    private static final int SB_KEYGUARD = 1, SB_SHADE_LOCKED = 2;
    private static volatile WeakReference<View> containerRef;
    private static volatile int scrimState = STATE_OTHER, statusBarState;
    private static volatile boolean keyguardShowing, containerVisible, bouncerToGoneTransitionRunning;
    private static volatile float shadeExpansionFraction;
    private static volatile long unlockTransitionStartedAt;
    private static int errors;
    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<String>());

    private BouncerHook() {}

    /** Reports a failure once per site: a silent catch here would hide a hook that never installed. */
    private static void logOnce(String where, Throwable t) {
        if (LOGGED.size() > 64 || !LOGGED.add(where)) return;
        XposedBridge.log(TAG + ": " + where + " failed: " + t);
    }

    public static void hook(ClassLoader cl) {
        hookScrimState(cl);
        hookStatusBarState(cl);
        hookKeyguardState(cl);
        hookShadeExpansion(cl);
        hookContainer(cl);
    }

    public static int scrimState() { return scrimState; }
    public static boolean isBouncerToGoneTransitionRunning() { return bouncerToGoneTransitionRunning; }
    public static boolean isShadeExpanded() { return shadeExpansionFraction > 0.05f; }

    public static boolean isLockscreenDimActive() {
        if (shouldForceUnlockScrimZero()) return false;
        if (keyguardShowing || containerVisible) return true;
        if (statusBarState == SB_KEYGUARD || statusBarState == SB_SHADE_LOCKED) return true;
        int st = scrimState;
        return st == STATE_BOUNCER || st == STATE_BOUNCER_SCRIMMED || st == STATE_KEYGUARD || bouncerToGoneTransitionRunning;
    }

    public static boolean shouldForceUnlockScrimZero() {
        if (scrimState == STATE_UNLOCKED && !keyguardShowing) return true;
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

    private static void hookContainer(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.android.keyguard.KeyguardSecurityContainer", cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": BOUNCER_HOOK: KeyguardSecurityContainer not found");
                return;
            }
            XposedBridge.hookAllMethods(cls, "reloadBackgroundColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { apply(p); }
            });
            try {
                XposedBridge.hookAllMethods(cls, "onDraw", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) { apply(p); }
                });
            } catch (Throwable t) { logOnce("bouncer onDraw hook", t); }
            try {
                XposedBridge.hookAllMethods(cls, "onVisibilityChanged", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            View v = (View) p.thisObject;
                            containerRef = new WeakReference<>(v);
                            containerVisible = v.getVisibility() == View.VISIBLE && v.isShown();
                        } catch (Throwable t) { logOnce("bouncer visibility", t); }
                    }
                });
            } catch (Throwable t) { logOnce("bouncer visibility hook", t); }
            XposedBridge.log(TAG + ": BOUNCER_HOOK_READY");
        } catch (Throwable t) { XposedBridge.log(TAG + ": BOUNCER_HOOK_ERROR " + t); }
    }

    static void apply(XC_MethodHook.MethodHookParam param) {
        try {
            if (!(param.thisObject instanceof View)) return;
            View view = (View) param.thisObject;
            containerRef = new WeakReference<>(view);
            if (view.getVisibility() == View.VISIBLE) containerVisible = true;
            float factor = Prefs.bouncer(view.getContext());
            Drawable bg = view.getBackground();
            if (ShadeAlpha.traceActive()) {
                String kind = bg instanceof ColorDrawable
                        ? "color=" + Integer.toHexString(((ColorDrawable) bg).getColor())
                        : (bg == null ? "none" : bg.getClass().getSimpleName() + " a=" + bg.getAlpha());
                ShadeAlpha.trace("bouncerApply view=" + view.getClass().getSimpleName() + " " + kind
                        + " factor=" + factor + " viewAlpha=" + view.getAlpha());
            }
            if (bg instanceof ColorDrawable) {
                ColorDrawable cd = (ColorDrawable) bg;
                int c = cd.getColor();
                int baseA = Color.alpha(c); if (baseA == 0) baseA = 255;
                int nc = Color.argb(Math.round(baseA * factor), Color.red(c), Color.green(c), Color.blue(c));
                if (c != nc) cd.setColor(nc);
            } else if (bg != null) {
                int da = Math.round(255 * factor);
                if (bg.getAlpha() != da) bg.setAlpha(da);
            } else if (Math.abs(view.getAlpha() - factor) > 0.01f) {
                view.setAlpha(factor);
            }
        } catch (Throwable t) {
            if (++errors <= 5) XposedBridge.log(TAG + ": BOUNCER_APPLY_ERROR " + t);
        }
    }

    private static void hookStatusBarState(ClassLoader cl) {
        boolean found = false;
        for (String name : new String[]{
                "com.android.systemui.statusbar.StatusBarStateControllerImpl",
                "com.android.systemui.statusbar.SysuiStatusBarStateController"}) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls == null) continue;
            found = true;
            for (String m : new String[]{"setState", "setAndInstrumentState"}) {
                try {
                    XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                int st = -1;
                                if (p.args != null && p.args.length > 0 && p.args[0] instanceof Integer)
                                    st = (Integer) p.args[0];
                                if (st < 0) {
                                    try {
                                        Object v = XposedHelpers.getObjectField(p.thisObject, "mState");
                                        if (v instanceof Integer) st = (Integer) v;
                                    } catch (Throwable ignored) {}
                                }
                                if (st >= 0) {
                                    statusBarState = st;
                                    if (st == SB_KEYGUARD || st == SB_SHADE_LOCKED) keyguardShowing = true;
                                    XposedBridge.log(TAG + ": STATUS_BAR_STATE " + st);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable t) { logOnce("status bar state hook", t); }
            }
        }
        if (!found) XposedBridge.log(TAG + ": BOUNCER_HOOK: StatusBarStateController not found");
    }

    private static void hookKeyguardState(ClassLoader cl) {
        boolean found = false;
        for (String name : new String[]{
                "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl",
                "com.android.systemui.keyguard.KeyguardViewMediator"}) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls == null) continue;
            found = true;
            for (String m : new String[]{"notifyKeyguardState", "setKeyguardShowing", "setShowing", "isShowing"}) {
                try {
                    XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if ("isShowing".equals(m) && p.getResult() instanceof Boolean) {
                                    keyguardShowing = (Boolean) p.getResult();
                                    return;
                                }
                                if (p.args != null)
                                    for (Object a : p.args)
                                        if (a instanceof Boolean) { keyguardShowing = (Boolean) a; return; }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable t) { logOnce("keyguard state hook", t); }
            }
        }
        if (!found) XposedBridge.log(TAG + ": BOUNCER_HOOK: KeyguardStateController not found");
    }

    private static void hookScrimState(ClassLoader cl) {
        Class<?> cls = null;
        for (String n : new String[]{"com.android.systemui.statusbar.phone.ScrimController", "com.android.systemui.scrim.ScrimController"}) {
            cls = XposedHelpers.findClassIfExists(n, cl);
            if (cls != null) break;
        }
        if (cls == null) {
            XposedBridge.log(TAG + ": BOUNCER_HOOK: ScrimController not found");
            return;
        }
        for (String method : new String[]{"transitionTo", "setState", "applyState"}) {
            try {
                XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) { onTransition(p.thisObject, p.args); }
                });
            } catch (Throwable t) { logOnce("scrim state hook", t); }
        }
    }

    static void onTransition(Object controller, Object[] args) {
        try {
            int code = STATE_OTHER;
            if (args != null) {
                for (Object a : args) {
                    if (a == null) continue;
                    String s = a.toString();
                    if (s.contains("BOUNCER") && s.contains("SCRIMMED")) code = STATE_BOUNCER_SCRIMMED;
                    else if (s.contains("BOUNCER")) code = STATE_BOUNCER;
                    else if (s.contains("KEYGUARD")) code = STATE_KEYGUARD;
                    else if (s.contains("UNLOCKED") || s.contains("OFF") || s.contains("AOD")) code = STATE_UNLOCKED;
                }
            }
            try {
                Object st = XposedHelpers.getObjectField(controller, "mState");
                if (st != null) {
                    String ss = st.toString();
                    if (ss.contains("BOUNCER") && ss.contains("SCRIMMED")) code = STATE_BOUNCER_SCRIMMED;
                    else if (ss.contains("BOUNCER")) code = STATE_BOUNCER;
                    else if (ss.contains("KEYGUARD")) code = STATE_KEYGUARD;
                    else if (ss.contains("UNLOCKED")) code = STATE_UNLOCKED;
                }
            } catch (Throwable ignored) {}
            if (code != scrimState) {
                scrimState = code;
                if (code == STATE_UNLOCKED) {
                    unlockTransitionStartedAt = SystemClock.uptimeMillis();
                    keyguardShowing = false;
                } else if (code == STATE_KEYGUARD || code == STATE_BOUNCER || code == STATE_BOUNCER_SCRIMMED) {
                    unlockTransitionStartedAt = 0;
                    keyguardShowing = true;
                }
                XposedBridge.log(TAG + ": STATE_TRANSITION code=" + code + " keyguard=" + keyguardShowing);            }
            try {
                Object f = XposedHelpers.getObjectField(controller, "mIsBouncerToGoneTransitionRunning");
                if (f instanceof Boolean) bouncerToGoneTransitionRunning = (Boolean) f;
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            if (++errors <= 5) XposedBridge.log(TAG + ": STATE_TRANSITION_ERROR " + t);
        }
    }

    private static void hookShadeExpansion(ClassLoader cl) {
        boolean found = false;
        for (String name : new String[]{
                "com.android.systemui.shade.ShadeExpansionStateManager",
                "com.android.systemui.statusbar.phone.NotificationPanelViewController",
                "com.android.systemui.shade.NotificationPanelViewController"}) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls == null) continue;
            found = true;
            for (String m : new String[]{"setExpandedFraction", "onExpansionChanged", "setExpansionFraction"}) {
                try {
                    XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Float f = null;
                                try {
                                    Object v = XposedHelpers.getObjectField(p.thisObject, "mExpandedFraction");
                                    if (v instanceof Float) f = (Float) v;
                                } catch (Throwable ignored) {}
                                if (f == null && p.args != null && p.args.length > 0 && p.args[0] instanceof Float)
                                    f = (Float) p.args[0];
                                if (f != null) {
                                    float v = Math.max(0f, Math.min(1f, f));
                                    if (Math.abs(v - shadeExpansionFraction) >= 0.01f) shadeExpansionFraction = v;
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable t) { logOnce("shade expansion hook", t); }
            }
        }
        if (!found) XposedBridge.log(TAG + ": BOUNCER_HOOK: ShadeExpansionStateManager not found");
    }
}
