package dev.shadealpha;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Делает фон шторки прозрачнее. Вместо перехвата setViewAlpha (его может встроить оптимизатор R8,
 * и тогда хук не срабатывает) перехватывается ScrimView.onDraw: содержимое скрима рисуется
 * в слое с альфой, равной проценту из Settings.System.
 *   shade_alpha_top_percent    -> scrim_behind        (верх / фон шторки)
 *   shade_alpha_bottom_percent -> scrim_notifications (низ / уведомления)
 * 100 = как в системе, 0 = полностью прозрачно.
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
    private static volatile float fTop = 1f;
    private static volatile float fBottom = 1f;
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
                                float f;
                                if ("scrim_behind".equals(id)) {
                                    f = factor(v.getContext(), true);
                                } else if ("scrim_notifications".equals(id)) {
                                    f = factor(v.getContext(), false);
                                } else {
                                    logOnce(id, -1f);
                                    return;
                                }
                                logOnce(id, f);
                                if (f >= 0.999f) return;
                                Canvas c = (Canvas) param.args[0];
                                int count = c.saveLayerAlpha(0, 0, v.getWidth(), v.getHeight(),
                                        Math.round(255 * f));
                                param.setObjectExtra(EXTRA, Integer.valueOf(count));
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
            int n = hooks == null ? 0 : hooks.size();
            XposedBridge.log(TAG + ": hooked onDraw, methods=" + n);
            if (n == 0) XposedBridge.log(TAG + ": WARNING: ScrimView has no onDraw to hook");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed: " + t);
        }
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

    private static void logOnce(String id, float factor) {
        synchronized (SEEN) {
            if (SEEN.add(id)) {
                XposedBridge.log(TAG + ": first draw of scrim id=" + id + " factor=" + factor);
            }
        }
    }

    private static float factor(Context c, boolean top) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead > 500) {
            lastRead = now;
            float nt = read(c, Keys.TOP);
            float nb = read(c, Keys.BOTTOM);
            if (nt != fTop || nb != fBottom) {
                XposedBridge.log(TAG + ": values top=" + nt + " bottom=" + nb);
            }
            fTop = nt;
            fBottom = nb;
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
}
