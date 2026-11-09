package dev.shadealpha;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Set;
import java.lang.reflect.Method;


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

    private static final int ROLE_OTHER = 0;
    private static final int ROLE_TOP = 1;
    private static final int ROLE_BOTTOM = 2;

    private static final int TAG_ROLE = 0x7ffa0001;
    private static final int TAG_ALPHA_SET = 0x7ffa0002;

    private static final int MAX_FAILURES = 20;
    private static final boolean[] SVA_LOGGED = new boolean[3];
    private static volatile String lastScrimDiagnostic = "";

    private static volatile float fTop = 1f;
    private static volatile float fBottom = 1f;
    private static volatile int mode = Keys.MODE_VIEW_ALPHA;
    private static volatile long lastRead = 0;
    private static volatile boolean disabled = false;
    private static int failures = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if ("com.android.systemui".equals(pkg)) {
            hookSystemUI(lpparam);
        } else if (LauncherHooks.isLauncher(pkg)) {
            LauncherHooks.hook(lpparam);
        } else if ("dev.shadealpha".equals(pkg)) {
            hookSelf(lpparam);
        }
    }

    // ========== SELF (статус модуля в приложении) ==========
    private void hookSelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // В Vector нет XposedHelpers.findAndHookMethod(String, ClassLoader, ...), поэтому через hookAllMethods
            Class<?> keys = XposedHelpers.findClassIfExists(Keys.class.getName(), lpparam.classLoader);
            if (keys == null) {
                XposedBridge.log(TAG + ": self hook: Keys class not found");
                return;
            }
            XposedBridge.hookAllMethods(keys, "isModuleActive", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            XposedBridge.log(TAG + ": self hooked, module active");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": self hook failed: " + t);
        }
    }

    // ========== SYSTEM UI ==========
    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": SystemUI loaded");
        BouncerHook.hook(lpparam.classLoader);
        VolumeHook.hook(lpparam.classLoader);
        Class<?> scrim = null;
        for (String name : SCRIM_CLASSES) {
            scrim = XposedHelpers.findClassIfExists(name, lpparam.classLoader);
            if (scrim != null) break;
        }
        if (scrim == null) { XposedBridge.log(TAG + ": ScrimView not found"); return; }

        hookScrimColorSetters(scrim);

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
                        float f = factor(v, role == ROLE_TOP);
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
                        float f = factor(v, role == ROLE_TOP);
                        int m = mode;
                        float applied = (m == Keys.MODE_SCRIM_ALPHA) ? a * f : a;
                        String state = BouncerHook.scrimStateForDiagnostics();
                        String diagnostic = "setViewAlpha id=" + idName(v) + " state=" + state
                                + " mode=" + m + " requested=" + a + " factor=" + f
                                + " applied=" + applied;
                        if (!diagnostic.equals(lastScrimDiagnostic)) {
                            lastScrimDiagnostic = diagnostic;
                            XposedBridge.log(TAG + ": scrim diagnostic " + diagnostic);
                        }
                        if (m == Keys.MODE_SCRIM_ALPHA) param.args[0] = applied;
                    } catch (Throwable t) { fail("setViewAlpha", t); }
                }
            });
            XposedBridge.log(TAG + ": hooked setViewAlpha, methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) { XposedBridge.log(TAG + ": setViewAlpha hook failed: " + t); }
    }

    private static void hookScrimColorSetters(Class<?> scrim) {
        try {
            int count = 0;
            for (Method method : scrim.getDeclaredMethods()) {
                String name = method.getName();
                if (!(name.startsWith("setColor") || name.startsWith("setColors")
                        || name.startsWith("setTint") || name.startsWith("setScrimColor"))) continue;
                Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(scrim, name,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam p) {
                                try {
                                    View v = (View) p.thisObject;
                                    if (role(v) == ROLE_OTHER) return;
                                    String state = BouncerHook.scrimStateForDiagnostics();
                                    String signature = "setter=" + name + " id=" + idName(v)
                                            + " state=" + state + " args=" + java.util.Arrays.deepToString(p.args);
                                    if (!signature.equals(lastScrimDiagnostic)) {
                                        lastScrimDiagnostic = signature;
                                        XposedBridge.log(TAG + ": scrim diagnostic " + signature);
                                    }
                                } catch (Throwable t) {
                                    fail("scrim color diagnostic", t);
                                }
                            }
                        });
                count += hooks == null ? 0 : hooks.size();
            }
            XposedBridge.log(TAG + ": hooked scrim color setters x" + count);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": scrim color diagnostics failed: " + t);
        }
    }

    // ========== HELPERS ==========
    private static int role(View v) {
        Object cached = v.getTag(TAG_ROLE);
        if (cached instanceof Integer) return (Integer) cached;
        String id = idName(v);
        int r = "scrim_behind".equals(id) ? ROLE_TOP : "scrim_notifications".equals(id) ? ROLE_BOTTOM : ROLE_OTHER;
        v.setTag(TAG_ROLE, Integer.valueOf(r));
        XposedBridge.log(TAG + ": scrim view id=" + id + " role=" + r);
        return r;
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "(no-id)";
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return "(unknown)"; }
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
        if (n >= MAX_FAILURES && !disabled) { disabled = true; XposedBridge.log(TAG + ": too many failures, hook disabled"); }
    }

    private static float factor(View v, boolean top) {
        // During PIN entry, the behind/front scrim is part of the lock screen background,
        // not the normal notification shade. Use the dedicated PIN opacity slider.
        float bouncer = BouncerHook.scrimFactor(v);
        if (bouncer >= 0f) return bouncer;
        return factor(v.getContext(), top);
    }

    private static float factor(Context c, boolean top) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead > 500) {
            lastRead = now;
            float nt = read(c, Keys.TOP);
            float nb = read(c, Keys.BOTTOM);
            int nm = readMode(c);
            if (nt != fTop || nb != fBottom || nm != mode) XposedBridge.log(TAG + ": values top=" + nt + " bottom=" + nb + " mode=" + nm);
            fTop = nt; fBottom = nb; mode = nm;
        }
        return top ? fTop : fBottom;
    }

    private static float read(Context c, String key) {
        try { int p = Settings.System.getInt(c.getContentResolver(), key, 100); return Math.max(0, Math.min(100, p)) / 100f; }
        catch (Throwable e) { return 1f; }
    }

    private static int readMode(Context c) {
        try { int m = Settings.System.getInt(c.getContentResolver(), Keys.MODE, Keys.MODE_VIEW_ALPHA); return Math.max(0, Math.min(2, m)); }
        catch (Throwable e) { return Keys.MODE_VIEW_ALPHA; }
    }
}
