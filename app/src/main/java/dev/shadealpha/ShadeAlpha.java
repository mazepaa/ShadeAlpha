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
    /**
     * Scrim view ids, resolved lazily from the first scrim that draws (there is no Application,
     * and so no Resources, when the hooks are installed). 0 = not found. The fast path in the
     * draw hook is on only when at least one id was found.
     */
    private static volatile int scrimIdBehind = 0;
    private static volatile int scrimIdNotifications = 0;
    private static volatile int scrimIdFront = 0;
    private static volatile boolean idsResolved = false;
    private static volatile boolean fastPath = false;
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
        if ("com.android.systemui".equals(pkg)) {
            if (BootGuard.allow(lpparam, BootGuard.SYSTEM_UI)) hookSystemUI(lpparam);
        } else if (LauncherHooks.isLauncher(pkg)) {
            if (BootGuard.allow(lpparam, BootGuard.LAUNCHER)) LauncherHooks.hook(lpparam);
        } else if ("dev.shadealpha".equals(pkg)) {
            hookSelf(lpparam);
        }
    }

    private void hookSelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> keys = XposedHelpers.findClassIfExists(Keys.class.getName(), lpparam.classLoader);
            if (keys == null) { Logs.log(TAG + ": self hook: Keys class not found"); return; }
            XposedBridge.hookAllMethods(keys, "isModuleActive", new XC_MethodReplacement() {
                @Override protected Object replaceHookedMethod(MethodHookParam param) { return Boolean.TRUE; }
            });
            Logs.log(TAG + ": self hooked, module active");
        } catch (Throwable t) { Logs.log(TAG + ": self hook failed: " + t); }
    }

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        Logs.log(TAG + ": SystemUI loaded");
        HookStatus.owner(HookStatus.SYSTEM_UI_BITS, HookStatus.REPORTED_SYSTEM_UI);
        VolumeHook.hook(lpparam.classLoader);
        BouncerHook.hook(lpparam.classLoader);
        Class<?> scrim = null;
        for (String name : SCRIM_CLASSES) {
            scrim = XposedHelpers.findClassIfExists(name, lpparam.classLoader);
            if (scrim != null) break;
        }
        if (scrim == null) { Logs.log(TAG + ": ScrimView not found"); return; }

        // onDraw runs on every SystemUI view, so non-scrim views are rejected by id first;
        // the ids are resolved on the first scrim draw (see resolveScrimIds).
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(scrim, "onDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 1 || !(param.args[0] instanceof Canvas)) return;
                        View v = (View) param.thisObject;
                        if (!idsResolved) resolveScrimIds(v);
                        if (fastPath) {
                            int rawId = v.getId();
                            if (rawId != scrimIdBehind && rawId != scrimIdNotifications
                                    && rawId != scrimIdFront) return;
                        }
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
            Logs.log(TAG + ": hooked onDraw, methods=" + (hooks == null ? 0 : hooks.size()));
            if (hooks != null && !hooks.isEmpty()) HookStatus.mark(HookStatus.SHADE);
        } catch (Throwable t) { Logs.log(TAG + ": onDraw hook failed: " + t); }

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
                            Logs.log(TAG + ": first setViewAlpha role=" + roleName(role) + " value=" + a);
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
            Logs.log(TAG + ": hooked setViewAlpha, methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) { Logs.log(TAG + ": setViewAlpha hook failed: " + t); }
    }

    /**
     * Looks the scrim ids up through the view's own Resources. Runs once; if nothing resolves the
     * fast path stays off and role() falls back to resource entry names.
     */
    private static void resolveScrimIds(View v) {
        try {
            android.content.res.Resources res = v.getResources();
            String pkg = v.getContext().getPackageName();
            scrimIdBehind = res.getIdentifier("scrim_behind", "id", pkg);
            scrimIdNotifications = res.getIdentifier("scrim_notifications", "id", pkg);
            scrimIdFront = res.getIdentifier("scrim_in_front", "id", pkg);
            fastPath = scrimIdBehind != 0 || scrimIdNotifications != 0 || scrimIdFront != 0;
            if (!fastPath) Logs.log(TAG + ": scrim ids not resolved, fast path disabled");
        } catch (Throwable t) {
            fastPath = false;
            Logs.log(TAG + ": scrim id lookup failed: " + t);
        }
        idsResolved = true;
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
        // No geometry guessing: a mis-detected role would make the wrong side translucent on tablets.
        v.setTag(TAG_ROLE, Integer.valueOf(r));
        v.setTag(TAG_ROLE_SIZE, Integer.valueOf(size));
        Logs.log(TAG + ": scrim view id=" + id + " role=" + roleName(r)
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
            Logs.log(TAG + ": keyguardLocked=" + locked);
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
     * Unlocked home / QS always use the TOP slider, never the PIN value.
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
        if (n <= 3) Logs.log(TAG + ": " + where + " failed: " + t);
        if (n >= MAX_FAILURES && !disabled) {
            disabled = true;
            Logs.log(TAG + ": too many failures, hook disabled");
        }
    }
}
