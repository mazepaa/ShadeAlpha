package dev.shadealpha;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ТЕСТОВАЯ сборка: три способа сделать скрим прозрачнее, переключаются на лету через
 * Settings.System["shade_alpha_mode"]:
 *   0 = слой: ScrimView.onDraw рисуется в saveLayerAlpha (два прохода, как в 1.0)
 *   1 = альфа view: View.setAlpha(f) у самого скрима (один проход, если hasOverlappingRendering=false)
 *   2 = альфа скрима: умножение аргумента ScrimView.setViewAlpha (как «снижение альфы цвета» в Iconify)
 * Верх -> scrim_behind, низ -> scrim_notifications. 100 = как в системе.
 */
public class ShadeAlpha implements IXposedHookLoadPackage {

    private static final String TAG = "ShadeAlpha";
    private static final String EXTRA = "shadealpha_save";

    private static final String[] SCRIM_CLASSES = {
            "com.android.systemui.scrim.ScrimView",
            "com.android.systemui.statusbar.ScrimView"
    };

    private static final ConcurrentHashMap<Integer, String> NAMES = new ConcurrentHashMap<>();
    private static final Set<String> SEEN = new HashSet<>();
    private static final Set<View> ALPHA_SET =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private static volatile float fTop = 1f;
    private static volatile float fBottom = 1f;
    private static volatile int mode = Keys.MODE_VIEW_ALPHA;
    private static volatile long lastRead = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": SystemUI loaded, process=" + lpparam.processName);

        Class<?> scrim = null;
        for (String name : SCRIM_CLASSES) {
            scrim = XposedHelpers.findClassIfExists(name, lpparam.classLoader);
            if (scrim != null) break;
        }
        if (scrim == null) {
            XposedBridge.log(TAG + ": ScrimView class not found");
            return;
        }
        XposedBridge.log(TAG + ": found " + scrim.getName());

        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    scrim, "onDraw", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length != 1 || !(param.args[0] instanceof Canvas)) return;
                                View v = (View) param.thisObject;
                                String id = idName(v);
                                boolean top = "scrim_behind".equals(id);
                                boolean bottom = "scrim_notifications".equals(id);
                                if (!top && !bottom) {
                                    logOnce("draw:" + id, -1f);
                                    return;
                                }
                                float f = factor(v.getContext(), top);
                                logOnce("draw:" + id, f);
                                if (mode == Keys.MODE_VIEW_ALPHA) {
                                    applyViewAlpha(v, f);
                                } else {
                                    resetViewAlpha(v);
                                    if (mode == Keys.MODE_LAYER && f < 0.999f) {
                                        Canvas c = (Canvas) param.args[0];
                                        int count = c.saveLayerAlpha(0, 0, v.getWidth(), v.getHeight(),
                                                Math.round(255 * f));
                                        param.setObjectExtra(EXTRA, Integer.valueOf(count));
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": before onDraw failed: " + t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object o = param.getObjectExtra(EXTRA);
                                if (o instanceof Integer) {
                                    ((Canvas) param.args[0]).restoreToCount((Integer) o);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": after onDraw failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked onDraw, methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": onDraw hook failed: " + t);
        }

        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    scrim, "setViewAlpha", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length != 1 || !(param.args[0] instanceof Float)) return;
                                View v = (View) param.thisObject;
                                String id = idName(v);
                                boolean top = "scrim_behind".equals(id);
                                boolean bottom = "scrim_notifications".equals(id);
                                if (!top && !bottom) return;
                                float a = (Float) param.args[0];
                                // если эта строка появилась в журнале, метод НЕ встроен и режим 2 может работать
                                logOnce("setViewAlpha:" + id, a);
                                float f = factor(v.getContext(), top);
                                if (mode == Keys.MODE_SCRIM_ALPHA) {
                                    param.args[0] = a * f;
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": setViewAlpha hook failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked setViewAlpha, methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setViewAlpha hook failed: " + t);
        }
    }

    private static void applyViewAlpha(View v, float f) {
        if (f >= 0.999f) {
            resetViewAlpha(v);
            return;
        }
        if (Math.abs(v.getAlpha() - f) > 0.001f) v.setAlpha(f);
        synchronized (ALPHA_SET) {
            ALPHA_SET.add(v);
        }
    }

    private static void resetViewAlpha(View v) {
        boolean had;
        synchronized (ALPHA_SET) {
            had = ALPHA_SET.remove(v);
        }
        if (had && v.getAlpha() != 1f) v.setAlpha(1f);
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "(no-id)";
        String n = NAMES.get(id);
        if (n == null) {
            try {
                n = v.getResources().getResourceEntryName(id);
            } catch (Throwable t) {
                n = "(unknown)";
            }
            NAMES.put(id, n);
        }
        return n;
    }

    private static void logOnce(String key, float value) {
        synchronized (SEEN) {
            if (SEEN.add(key)) {
                XposedBridge.log(TAG + ": first " + key + " value=" + value);
            }
        }
    }

    private static float factor(Context c, boolean top) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead > 500) {
            lastRead = now;
            float nt = read(c, Keys.TOP);
            float nb = read(c, Keys.BOTTOM);
            int nm = readMode(c);
            if (nt != fTop || nb != fBottom || nm != mode) {
                XposedBridge.log(TAG + ": values top=" + nt + " bottom=" + nb + " mode=" + nm);
            }
            fTop = nt;
            fBottom = nb;
            mode = nm;
        }
        return top ? fTop : fBottom;
    }

    private static float read(Context c, String key) {
        try {
            int p = Settings.System.getInt(c.getContentResolver(), key, 100);
            return Math.max(0, Math.min(100, p)) / 100f;
        } catch (Throwable e) {
            return 1f;
        }
    }

    private static int readMode(Context c) {
        try {
            int m = Settings.System.getInt(c.getContentResolver(), Keys.MODE, Keys.MODE_VIEW_ALPHA);
            return Math.max(0, Math.min(2, m));
        } catch (Throwable e) {
            return Keys.MODE_VIEW_ALPHA;
        }
    }
}
