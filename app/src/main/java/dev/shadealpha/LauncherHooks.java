package dev.shadealpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Прозрачность «Недавних» и «Списка приложений» в Pixel Launcher / Launcher3.
 *
 * Недавние: подменяем альфу цвета скрима у состояний лаунчера (OverviewState и др.).
 *
 * Список приложений:
 *  - скрим-цвет за шторкой (AllAppsState.getWorkspaceScrimColor);
 *  - сама шторка (ActivityAllAppsContainerView). Здесь две ветки:
 *      A) старые версии: у view есть поле mScrimColor — меняем цвета напрямую;
 *      B) Android 15/16: поля нет, фон рисуется в drawOnScrim* — оборачиваем
 *         отрисовку в saveLayerAlpha.
 *    Ветки взаимоисключающие, чтобы прозрачность не применялась дважды.
 */
final class LauncherHooks {

    private static final String TAG = "ShadeAlpha";

    static final String PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher";
    static final String LAUNCHER3 = "com.android.launcher3";

    private static final String[] ALL_APPS_VIEWS = {
            "com.android.launcher3.allapps.ActivityAllAppsContainerView",
            "com.android.launcher3.allapps.BaseAllAppsContainerView",
            "com.android.launcher3.allapps.AllAppsContainerView"
    };
    private static final String[] COLOR_GETTERS = {
            "getHeaderColor", "getScrimColor", "getBackgroundColor", "getBottomSheetBackgroundColor"
    };
    private static final String[] DRAW_METHODS = {
            "drawOnScrimWithScaleAndBottomOffset", "drawOnScrimWithScale", "drawOnScrim"
    };

    private static final Object NONE = new Object();
    private static final ConcurrentHashMap<String, Object> FIELDS = new ConcurrentHashMap<String, Object>();
    private static int errors = 0;

    private LauncherHooks() {}

    static boolean isLauncher(String pkg) {
        return PIXEL_LAUNCHER.equals(pkg) || LAUNCHER3.equals(pkg);
    }

    static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        log("Launcher loaded (" + lpparam.packageName + ")");
        HookStatus.owner(HookStatus.LAUNCHER_BITS, HookStatus.REPORTED_LAUNCHER);

        // ---- Недавние ----
        int recents = 0;
        recents += hookScrimColor(cl, "com.android.launcher3.uioverrides.states.OverviewState", "getWorkspaceScrimColor", false);
        recents += hookScrimColor(cl, "com.android.launcher3.uioverrides.states.QuickSwitchState", "getWorkspaceScrimColor", false);
        recents += hookScrimColor(cl, "com.android.quickstep.fallback.RecentsState", "getScrimColor", false);
        recents += hookScrimColor(cl, "com.android.launcher3.uioverrides.states.HintState", "getWorkspaceScrimColor", false);
        recents += hookScrimColor(cl, "com.android.launcher3.states.HintState", "getWorkspaceScrimColor", false);
        if (recents > 0) HookStatus.mark(HookStatus.LAUNCHER);

        // ---- Список приложений ----
        int drawer = hookScrimColor(cl, "com.android.launcher3.uioverrides.states.AllAppsState", "getWorkspaceScrimColor", true);
        drawer += hookAllApps(cl);
        if (drawer > 0) HookStatus.mark(HookStatus.DRAWER);
    }

    // ------------------------------------------------------------------ state scrims

    private static int hookScrimColor(ClassLoader cl, String className, String method, boolean drawer) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, cl);
            if (cls == null) {
                log("skip, class not found: " + className);
                return 0;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, method,
                    new ScrimColorHook(className + "." + method, drawer));
            log("hooked " + className + "." + method + " x" + (hooks == null ? 0 : hooks.size()));
            return hooks == null ? 0 : hooks.size();
        } catch (Throwable t) {
            log("failed to hook " + className + ": " + t);
            return 0;
        }
    }

    private static final class ScrimColorHook extends XC_MethodHook {
        private final String name;
        private final boolean drawer;
        private volatile boolean logged;

        ScrimColorHook(String name, boolean drawer) {
            this.name = name;
            this.drawer = drawer;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            try {
                Context ctx = contextOf(p);
                if (HookStatus.pending()) HookStatus.flush(ctx);
                float f = drawer ? Prefs.drawer(ctx) : Prefs.recents(ctx);
                Object r = p.getResult();
                if (!logged) {
                    logged = true;
                    log("first hit " + name + " factor=" + f + " result=" + describe(r));
                }
                if (f >= 0.999f || r == null) return;
                if (r instanceof Integer) {
                    p.setResult(Integer.valueOf(scaleAlpha(((Integer) r).intValue(), f)));
                } else {
                    Object rebuilt = rebuildScrimColors(r, f);
                    if (rebuilt != null) p.setResult(rebuilt);
                }
            } catch (Throwable t) {
                err(name, t);
            }
        }
    }

    // ------------------------------------------------------------------ all apps sheet

    private static int hookAllApps(ClassLoader cl) {
        Class<?> start = null;
        for (String n : ALL_APPS_VIEWS) {
            start = XposedHelpers.findClassIfExists(n, cl);
            if (start != null) break;
        }
        if (start == null) {
            log("all apps container class not found");
            return 0;
        }

        ColorGetterHook getterHook = new ColorGetterHook();
        HeaderHook headerHook = new HeaderHook();
        DrawHook drawHook = new DrawHook();

        int total = 0;
        // methods hookAllMethods() ловит только объявленные в самом классе → идём по иерархии
        for (Class<?> c = start;
             c != null && c.getName().startsWith("com.android.launcher3.");
             c = c.getSuperclass()) {
            int n = 0;
            n += hookNames(c, COLOR_GETTERS, getterHook);
            n += hookNames(c, new String[]{"updateHeaderScroll"}, headerHook);
            n += hookNames(c, DRAW_METHODS, drawHook);
            log("all apps: " + c.getName() + " -> " + n + " hooks");
            total += n;
        }
        if (total == 0) log("all apps: nothing to hook, this launcher version is not supported");
        return total;
    }

    private static int hookNames(Class<?> c, String[] names, XC_MethodHook cb) {
        int n = 0;
        for (String name : names) {
            try {
                Set<XC_MethodHook.Unhook> s = XposedBridge.hookAllMethods(c, name, cb);
                if (s != null) n += s.size();
            } catch (Throwable t) {
                err("hook " + name, t);
            }
        }
        return n;
    }

    /** Ветка A: геттеры цветов (старые версии, где есть поле mScrimColor). */
    private static final class ColorGetterHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            try {
                if (!(p.thisObject instanceof View) || !usesColorField(p.thisObject)) return;
                applyField(p.thisObject, "mScrimColor", Prefs.drawer(((View) p.thisObject).getContext()));
            } catch (Throwable t) {
                err("getter.before", t);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            try {
                if (!(p.thisObject instanceof View) || !usesColorField(p.thisObject)) return;
                Object r = p.getResult();
                if (!(r instanceof Integer)) return;
                float f = Prefs.drawer(((View) p.thisObject).getContext());
                if (f >= 0.999f) return;
                p.setResult(Integer.valueOf(capAlpha(((Integer) r).intValue(), f)));
            } catch (Throwable t) {
                err("getter.after", t);
            }
        }
    }

    /** Ветка A: цвет полосы заголовка после прокрутки. */
    private static final class HeaderHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            try {
                if (!(p.thisObject instanceof View) || !usesColorField(p.thisObject)) return;
                float f = Prefs.drawer(((View) p.thisObject).getContext());
                applyField(p.thisObject, "mHeaderColor", f);
                try {
                    XposedHelpers.callMethod(p.thisObject, "invalidateHeader");
                } catch (Throwable ignored) { /* метода может не быть */ }
            } catch (Throwable t) {
                err("header", t);
            }
        }
    }

    /** Ветка B: оборачиваем рисование фона шторки в слой с альфой. */
    private static final class DrawHook extends XC_MethodHook {
        // [0] = глубина вложенности, [1] = номер сохранённого слоя (или -1)
        private static final ThreadLocal<int[]> STATE = new ThreadLocal<int[]>() {
            @Override
            protected int[] initialValue() {
                return new int[]{0, -1};
            }
        };
        private static volatile boolean logged;

        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            int[] s = STATE.get();
            s[0]++;
            if (s[0] != 1) return; // вложенный вызов: слой уже открыт снаружи
            s[1] = -1;
            try {
                if (!(p.thisObject instanceof View)) return;
                float f = Prefs.drawer(((View) p.thisObject).getContext());
                if (usesColorField(p.thisObject)) {
                    applyField(p.thisObject, "mScrimColor", f); // ветка A
                    return;
                }
                if (!logged) {
                    logged = true;
                    log("all apps draw hit, layer mode, factor=" + f);
                }
                if (f >= 0.999f) return;
                if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof Canvas)) return;
                if (f <= 0.004f) {
                    p.setResult(null); // полностью прозрачно: фон вообще не рисуем
                    return;
                }
                s[1] = ((Canvas) p.args[0]).saveLayerAlpha(null, Math.round(255f * f));
            } catch (Throwable t) {
                err("draw.before", t);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            int[] s = STATE.get();
            if (s[0] <= 0) return;
            s[0]--;
            if (s[0] == 0 && s[1] >= 0) {
                int save = s[1];
                s[1] = -1;
                try {
                    ((Canvas) p.args[0]).restoreToCount(save);
                } catch (Throwable t) {
                    err("draw.after", t);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean usesColorField(Object view) {
        return intField(view.getClass(), "mScrimColor") != null;
    }

    private static Field intField(Class<?> c, String name) {
        String key = c.getName() + "#" + name;
        Object v = FIELDS.get(key);
        if (v == null) {
            Field f = null;
            try {
                f = XposedHelpers.findFieldIfExists(c, name);
            } catch (Throwable ignored) { /* нет поля */ }
            if (f != null && f.getType() != int.class) f = null;
            v = (f != null) ? (Object) f : NONE;
            FIELDS.put(key, v);
        }
        return v == NONE ? null : (Field) v;
    }

    /**
     * Меняет int-поле цвета. Оригинальное значение запоминаем на экземпляре, поэтому повторные
     * вызовы не «накапливают» прозрачность, а смена ползунка применяется сразу.
     */
    private static void applyField(Object o, String name, float f) {
        try {
            Field fld = intField(o.getClass(), name);
            if (fld == null) return;
            int cur = fld.getInt(o);
            String key = "shadealpha_" + name;
            int[] st = (int[]) XposedHelpers.getAdditionalInstanceField(o, key);
            if (st == null) {
                st = new int[]{cur, cur};
                XposedHelpers.setAdditionalInstanceField(o, key, st);
            } else if (cur != st[1]) {
                st[0] = cur; // значение изменила сама система → это новый оригинал
            }
            int nv = (f >= 0.999f) ? st[0] : capAlpha(st[0], f);
            if (cur != nv) fld.setInt(o, nv);
            st[1] = nv;
        } catch (Throwable t) {
            err("field " + name, t);
        }
    }

    /** Умножает альфу цвета на f. 100% = как в системе, 0% = прозрачно. */
    private static int scaleAlpha(int color, float f) {
        int a = Math.round(Color.alpha(color) * f);
        a = Math.max(0, Math.min(255, a));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Ограничивает альфу сверху (идемпотентно, никогда не делает цвет плотнее оригинала). */
    private static int capAlpha(int color, float f) {
        int cap = Math.max(0, Math.min(255, Math.round(255f * f)));
        int a = Math.min(Color.alpha(color), cap);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Новые версии Launcher3 возвращают ScrimColors(background, foreground) вместо Int. */
    private static Object rebuildScrimColors(Object scrim, float f) {
        try {
            Class<?> cls = scrim.getClass();
            int bg;
            int fg;
            try {
                Method getBg = cls.getMethod("getBackgroundColor");
                Method getFg = cls.getMethod("getForegroundColor");
                bg = ((Integer) getBg.invoke(scrim)).intValue();
                fg = ((Integer) getFg.invoke(scrim)).intValue();
            } catch (Throwable e) {
                Field fBg = cls.getDeclaredField("backgroundColor");
                Field fFg = cls.getDeclaredField("foregroundColor");
                fBg.setAccessible(true);
                fFg.setAccessible(true);
                bg = fBg.getInt(scrim);
                fg = fFg.getInt(scrim);
            }
            Constructor<?> ctor = cls.getDeclaredConstructor(int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(Integer.valueOf(scaleAlpha(bg, f)), Integer.valueOf(fg));
        } catch (Throwable t) {
            err("ScrimColors", t);
            return null;
        }
    }

    private static Context contextOf(XC_MethodHook.MethodHookParam p) {
        if (p.args != null) {
            for (Object a : p.args) {
                if (a instanceof Context) return (Context) a;
            }
        }
        if (p.thisObject instanceof View) return ((View) p.thisObject).getContext();
        return null;
    }

    private static String describe(Object r) {
        if (r == null) return "null";
        if (r instanceof Integer) return "#" + Integer.toHexString(((Integer) r).intValue());
        return r.getClass().getSimpleName();
    }

    private static void log(String s) {
        XposedBridge.log(TAG + ": " + s);
    }

    private static void err(String where, Throwable t) {
        if (++errors <= 5) log(where + " failed: " + t);
    }
}

