package dev.shadealpha;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
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
    private static final int TAG_ROLE = 0x7ffa0001, TAG_ALPHA_SET = 0x7ffa0002;
    private static final int MAX_FAILURES = 20;
    private static final boolean[] SVA_LOGGED = new boolean[4];
    private static volatile float fTop = 1f, fBottom = 1f;
    private static volatile int mode = Keys.MODE_VIEW_ALPHA;
    private static volatile long lastRead = 0;
    private static volatile boolean disabled = false;
    private static int failures = 0;

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
                        if (disabled) { resetViewAlpha(v); return; }
                        int role = role(v);
                        if (role == ROLE_OTHER) return;
                        if ((role == ROLE_TOP || role == ROLE_FRONT) && BouncerHook.shouldForceUnlockScrimZero()) {
                            applyViewAlpha(v, 0f); return;
                        }
                        float f = roleFactor(v.getContext(), role);
                        int m = mode;
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
                        float f = roleFactor(v.getContext(), role);
                        boolean lock = false;
                        try {
                            lock = BouncerHook.isLockscreenDimActive()
                                    && (role == ROLE_TOP || role == ROLE_FRONT)
                                    && !BouncerHook.isShadeExpanded();
                        } catch (Throwable ignored) {}
                        if (mode == Keys.MODE_SCRIM_ALPHA || lock) {
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
        if (cached instanceof Integer) return (Integer) cached;
        String id = idName(v);
        int r = ROLE_OTHER;
        if ("scrim_behind".equals(id) || "scrimBehind".equals(id) || id.endsWith("_behind")) r = ROLE_TOP;
        else if ("scrim_notifications".equals(id) || "scrimNotifications".equals(id)
                || id.contains("notification") || id.endsWith("_notifications")) r = ROLE_BOTTOM;
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
            } catch (Throwable ignored) {}
        }
        v.setTag(TAG_ROLE, Integer.valueOf(r));
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

    private static float roleFactor(Context c, int role) {
        if (role == ROLE_OTHER) return 1f;
        try {
            if ((role == ROLE_TOP || role == ROLE_FRONT)
                    && BouncerHook.isLockscreenDimActive()
                    && !BouncerHook.isShadeExpanded()) {
                return Prefs.bouncer(c);
            }
        } catch (Throwable ignored) {}
        return factor(c, role == ROLE_TOP || role == ROLE_FRONT);
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

    private static float factor(Context c, boolean top) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead > 500) {
            lastRead = now;
            float nt = read(c, Keys.TOP), nb = read(c, Keys.BOTTOM);
            int nm = readMode(c);
            if (nt != fTop || nb != fBottom || nm != mode)
                XposedBridge.log(TAG + ": values top=" + nt + " bottom=" + nb + " mode=" + nm);
            fTop = nt; fBottom = nb; mode = nm;
        }
        return top ? fTop : fBottom;
    }

    private static float read(Context c, String key) {
        try {
            int p = Settings.System.getInt(c.getContentResolver(), key, 100);
            return Math.max(0, Math.min(100, p)) / 100f;
        } catch (Throwable e) { return 1f; }
    }

    private static int readMode(Context c) {
        try {
            int m = Settings.System.getInt(c.getContentResolver(), Keys.MODE, Keys.MODE_VIEW_ALPHA);
            return Math.max(0, Math.min(2, m));
        } catch (Throwable e) { return Keys.MODE_VIEW_ALPHA; }
    }
}
