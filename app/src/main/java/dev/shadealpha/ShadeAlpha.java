package dev.shadealpha;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShadeAlpha implements IXposedHookLoadPackage {

    private static final String TAG = "ShadeAlpha";
    private static final String EXTRA = "shadealpha_save";
    private static final String[] SCRIM_CLASSES = {
            "com.android.systemui.scrim.ScrimView",
            "com.android.systemui.statusbar.ScrimView"
    };
    private static final int ROLE_OTHER = 0, ROLE_TOP = 1, ROLE_BOTTOM = 2, ROLE_FRONT = 3;
    private static final int TAG_ROLE = 0x7ffa0001, TAG_ALPHA_SET = 0x7ffa0002, TAG_ROLE_SIZE = 0x7ffa0003;
    private static final int MAX_FAILURES = 20;
    private static final boolean[] SVA_LOGGED = new boolean[4];
    private static volatile boolean disabled = false;
    private static int failures = 0;
    private static volatile Boolean lastKeyguardLog = null;
    private static volatile boolean kgLocked = false;
    private static volatile long kgReadAt = 0;
    private static final long KEYGUARD_TTL_MS = 50;
    private static volatile long unlockedAt = 0;
    private static volatile boolean unlockScrimSeen = false;
    private static final long UNLOCK_WINDOW_MS = 500;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if ("com.android.systemui".equals(pkg)) hookSystemUI(lpparam);
        else if (LauncherHooks.isLauncher(pkg)) LauncherHooks.hook(lpparam);
        else if ("dev.shadealpha".equals(pkg)) hookSelf(lpparam);
    }

    private void hookSelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> keys = XposedHelpers.findClassIfExists(Keys.class.getName(), lpparam.classLoader);
            if (keys == null) { XposedBridge.log(TAG + ": self hook: Keys class not found"); return; }
            XposedBridge.hookAllMethods(keys, "isModuleActive", new XC_MethodReplacement() {
                @Override protected Object replaceHookedMethod(MethodHookParam param) { return Boolean.TRUE; }
            });
            XposedBridge.log(TAG + ": self hooked, module active");
        } catch (Throwable t) { XposedBridge.log(TAG + ": self hook failed: " + t); }
    }

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": SystemUI loaded");
        HookStatus.owner(HookStatus.SYSTEM_UI_BITS, HookStatus.REPORTED_SYSTEM_UI);
        VolumeHook.hook(lpparam.classLoader);
        BouncerHook.hook(lpparam.classLoader);
        Class<?> scrim = null;
        for (String name : SCRIM_CLASSES) {
            scrim = XposedHelpers.findClassIfExists(name, lpparam.classLoader);
            if (scrim != null) break;
        }
        if (scrim == null) { XposedBridge.log(TAG + ": ScrimView not found"); return; }

        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(scrim, "onDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 1 || !(param.args[0] instanceof Canvas)) return;
                        View v = (View) param.thisObject;
                        if (HookStatus.pending()) HookStatus.flush(v.getContext());
                        if (disabled) { resetViewAlpha(v); return; }
                        int role = role(v);
                        if (role == ROLE_OTHER) return;
                        boolean forceZero = (role == ROLE_TOP || role == ROLE_FRONT)
                                && (inUnlockWindow() || BouncerHook.shouldForceUnlockScrimZero());
                        if (forceZero) {
                            applyViewAlpha(v, 0f);
                            return;
                        }
                        float f = roleFactor(v.getContext(), role);
                        int m = Prefs.mode(v.getContext());
                        if (m == Keys.MODE_VIEW_ALPHA) {
                            applyViewAlpha(v, f);
                        } else {
                            resetViewAlpha(v);
                            if (m == Keys.MODE_LAYER && f < 0.999f) {
                                Canvas c = (Canvas) param.args[0];
                                int count = c.saveLayerAlpha(0, 0, v.getWidth(), v.getHeight(), Math.round(255 * f));
                                param.setObjectExtra(EXTRA, Integer.valueOf(count));
                            }
                        }
                        failures = 0;
                    } catch (Throwable t) { fail("before onDraw", t); }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object o = param.getObjectExtra(EXTRA);
                        if (o instanceof Integer) ((Canvas) param.args[0]).restoreToCount((Integer) o);
                    } catch (Throwable t) { fail("after onDraw", t); }
                }
            });
            XposedBridge.log(TAG + ": hooked onDraw, methods=" + (hooks == null ? 0 : hooks.size()));
            if (hooks != null && !hooks.isEmpty()) HookStatus.mark(HookStatus.SHADE);
        } catch (Throwable t) { XposedBridge.log(TAG + ": onDraw hook failed: " + t); }

        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(scrim, "setViewAlpha", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (disabled) return;
                        if (param.args.length != 1 || !(param.args[0] instanceof Float)) return;
                        View v = (View) param.thisObject;
                        int role = role(v);
                        if (role == ROLE_OTHER) return;
                        float a = (Float) param.args[0];
                        if (!SVA_LOGGED[role]) {
                            SVA_LOGGED[role] = true;
                            XposedBridge.log(TAG + ": first setViewAlpha role=" + roleName(role) + " value=" + a);
                        }
                        if ((role == ROLE_TOP || role == ROLE_FRONT) && inUnlockWindow()) {
                            if (role == ROLE_TOP) noteUnlockScrimAlpha(a);
                            param.args[0] = 0f;
                            return;
                        }
                        float f = roleFactor(v.getContext(), role);
                        if (Prefs.mode(v.getContext()) == Keys.MODE_SCRIM_ALPHA) {
                            param.args[0] = a * f;
                        } else if ((role == ROLE_TOP || role == ROLE_FRONT) && useBouncerForTop(v.getContext())) {
                            param.args[0] = a * f;
                        }
                    } catch (Throwable t) { fail("setViewAlpha", t); }
                }
            });
            XposedBridge.log(TAG + ": hooked setViewAlpha, methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) { XposedBridge.log(TAG + ": setViewAlpha hook failed: " + t); }
    }

    private static int role(View v) {
        Object cached = v.getTag(TAG_ROLE);
        Object cachedSize = v.getTag(TAG_ROLE_SIZE);
        int size = (v.getWidth() << 16) ^ v.getHeight();
        if (cached instanceof Integer && cachedSize instanceof Integer && (Integer) cachedSize == size) {
            return (Integer) cached;
        }
        String id = idName(v);
        int r = ROLE_OTHER;
        if ("scrim_behind".equals(id) || "scrimBehind".equals(id) || id.endsWith("_behind")) r = ROLE_TOP;
        else if ("scrim_notifications".equals(id) || "scrimNotifications".equals(id)
                || (id.contains("scrim") && id.contains("notification"))
                || id.endsWith("_notifications")) r = ROLE_BOTTOM;
        else if ("scrim_in_front".equals(id) || "scrimInFront".equals(id)
                || id.endsWith("_front") || "scrim_front".equals(id)) r = ROLE_FRONT;
        else if (!"(no-id)".equals(id) && !"(unknown)".equals(id)) {
            try {
                int h = v.getHeight();
                int screenH = v.getResources().getDisplayMetrics().heightPixels;
                if (h > 0 && screenH > 0) {
                    float ratio = (float) h / screenH;
                    if (ratio > 0.85f) r = ROLE_TOP;
                    else if (ratio > 0.3f && v.getY() > screenH * 0.15f) r = ROLE_BOTTOM;
                }
            } catch (Throwable ignored) { /* best effort: falls back to defaults */ }
        }
        v.setTag(TAG_ROLE, Integer.valueOf(r));
        v.setTag(TAG_ROLE_SIZE, Integer.valueOf(size));
        XposedBridge.log(TAG + ": scrim view id=" + id + " role=" + roleName(r)
                + " size=" + v.getWidth() + "x" + v.getHeight());
        return r;
    }

    private static String roleName(int r) {
        switch (r) {
            case ROLE_TOP: return "top/behind";
            case ROLE_BOTTOM: return "bottom/notifications";
            case ROLE_FRONT: return "front";
            default: return "other";
        }
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "(no-id)";
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return "(unknown)"; }
    }

    /**
     * Keyguard state, asked of the system at most once per {@link #KEYGUARD_TTL_MS}.
     *
     * The draw path calls this for every top and front scrim frame, and a bare call is a
     * synchronous binder round-trip into system_server, so the answer is reused for 50 ms:
     * that keeps the transition lag inside the {@link #UNLOCK_WINDOW_MS} zero window while the
     * binder calls drop from ~60/s to ~20/s. A failed call keeps the previous answer instead of
     * flipping the slider to the TOP value on a locked screen.
     */
    private static boolean isKeyguardLocked(Context c) {
        long now = SystemClock.uptimeMillis();
        long readAt = kgReadAt;
        if (readAt != 0 && now - readAt < KEYGUARD_TTL_MS) return kgLocked;

        kgReadAt = now;
        boolean locked;
        try {
            KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) return kgLocked;
            locked = km.isKeyguardLocked();
        } catch (Throwable t) {
            return kgLocked;
        }

        Boolean prev = lastKeyguardLog;
        if (prev == null || prev.booleanValue() != locked) {
            lastKeyguardLog = Boolean.valueOf(locked);
            XposedBridge.log(TAG + ": keyguardLocked=" + locked);
            if (!locked && prev != null && prev.booleanValue()) {
                unlockedAt = now;
                unlockScrimSeen = false;
            } else if (locked) {
                unlockedAt = 0;
                unlockScrimSeen = false;
            }
        }
        kgLocked = locked;
        return locked;
    }

    /**
     * True for a short window after unlocking. SystemUI fades {@code scrim_behind} and
     * {@code scrim_in_front} out during that time; multiplying that fading alpha by the TOP or PIN
     * value makes the scrim pop back to a visible alpha right after unlock and then fade away.
     * The window keeps those scrims at zero so the transition stays invisible.
     */
    static boolean inUnlockWindow() {
        long t = unlockedAt;
        return t != 0 && SystemClock.uptimeMillis() - t < UNLOCK_WINDOW_MS;
    }

    /**
     * Ends the unlock window as soon as the system has finished fading the keyguard scrim instead
     * of always waiting for the timer. SystemUI reports the fade through {@code setViewAlpha}:
     * while it runs the alpha is above zero, and the last call drops it to zero. The timer stays
     * as the fallback for ROMs that animate the scrim some other way.
     */
    static void noteUnlockScrimAlpha(float alpha) {
        if (alpha > 0.01f) {
            unlockScrimSeen = true;
            return;
        }
        if (unlockScrimSeen) {
            unlockedAt = 0;
            unlockScrimSeen = false;
        }
    }

    /**
     * PIN slider drives TOP/FRONT only while the device keyguard is locked.
     * Unlocked home / QS always use the TOP slider — never the PIN value.
     */
    private static boolean useBouncerForTop(Context c) {
        return isKeyguardLocked(c);
    }

    private static float roleFactor(Context c, int role) {
        if (role == ROLE_OTHER) return 1f;
        boolean topOrFront = (role == ROLE_TOP || role == ROLE_FRONT);
        if (topOrFront && useBouncerForTop(c)) return Prefs.bouncer(c);
        return topOrFront ? Prefs.top(c) : Prefs.bottom(c);
    }

    private static void applyViewAlpha(View v, float f) {
        if (f >= 0.999f) { resetViewAlpha(v); return; }
        if (Math.abs(v.getAlpha() - f) > 0.001f) v.setAlpha(f);
        if (v.getTag(TAG_ALPHA_SET) == null) v.setTag(TAG_ALPHA_SET, Boolean.TRUE);
    }

    private static void resetViewAlpha(View v) {
        if (v.getTag(TAG_ALPHA_SET) == null) return;
        v.setTag(TAG_ALPHA_SET, null);
        if (v.getAlpha() != 1f) v.setAlpha(1f);
    }

    private static void fail(String where, Throwable t) {
        int n = ++failures;
        if (n <= 3) XposedBridge.log(TAG + ": " + where + " failed: " + t);
        if (n >= MAX_FAILURES && !disabled) {
            disabled = true;
            XposedBridge.log(TAG + ": too many failures, hook disabled");
        }
    }
}

