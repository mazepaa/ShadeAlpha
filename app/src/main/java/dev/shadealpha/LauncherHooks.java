package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherHooks {
    private static final String TAG = "ShadeAlpha";

    public static boolean isLauncher(String pkg) {
        return "com.google.android.apps.nexuslauncher".equals(pkg)
                || "com.android.launcher3".equals(pkg);
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": Launcher loaded (" + lpparam.packageName + ")");

        hookLauncherScrim(lpparam.classLoader, "com.android.launcher3.uioverrides.states.OverviewState", "getWorkspaceScrimColor", Keys.RECENTS);
        hookLauncherScrim(lpparam.classLoader, "com.android.launcher3.uioverrides.states.QuickSwitchState", "getWorkspaceScrimColor", Keys.RECENTS);
        hookLauncherScrim(lpparam.classLoader, "com.android.quickstep.fallback.RecentsState", "getScrimColor", Keys.RECENTS);
        hookLauncherScrim(lpparam.classLoader, "com.android.launcher3.uioverrides.states.HintState", "getWorkspaceScrimColor", Keys.RECENTS);
        hookLauncherScrim(lpparam.classLoader, "com.android.launcher3.states.HintState", "getWorkspaceScrimColor", Keys.RECENTS);

        hookLauncherScrim(lpparam.classLoader, "com.android.launcher3.uioverrides.states.AllAppsState", "getWorkspaceScrimColor", Keys.APPDRAWER);

        hookAllAppsView(lpparam.classLoader, "com.android.launcher3.allapps.ActivityAllAppsContainerView");
        hookAllAppsView(lpparam.classLoader, "com.android.launcher3.allapps.BaseAllAppsContainerView");
        hookAllAppsView(lpparam.classLoader, "com.android.launcher3.allapps.AllAppsContainerView");
    }

    private static Context getAppContext() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method method = atClass.getMethod("currentApplication");
            return (Context) method.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hookLauncherScrim(ClassLoader cl, String className, String methodName, final String key) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, cl);
            if (cls == null) return;

            XposedBridge.hookAllMethods(cls, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context ctx = getAppContext();
                        if (ctx == null) return;
                        int opacity = Settings.System.getInt(ctx.getContentResolver(), key, 100);
                        if (opacity >= 100 || opacity < 0) return;
                        int alpha = opacity * 255 / 100;
                        Object result = param.getResult();
                        if (result == null) return;
                        if (result instanceof Integer) {
                            int color = (Integer) result;
                            param.setResult((color & 0x00FFFFFF) | (alpha << 24));
                        } else {
                            Object rebuilt = rebuildScrimColors(result, alpha);
                            if (rebuilt != null) param.setResult(rebuilt);
                        }
                    } catch (Throwable t) { XposedBridge.log(TAG + ": scrim error: " + t); }
                }
            });
            XposedBridge.log(TAG + ": hooked " + className + "." + methodName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook " + className + " - " + t);
        }
    }

    private static void hookAllAppsView(ClassLoader cl, String className) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, cl);
            if (cls == null) return;

            String[] methods = {"getBackgroundColor", "getScrimColor", "getHeaderColor", "getBottomSheetBackgroundColor"};
            for (String m : methods) {
                XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = getAppContext();
                            if (ctx == null) return;
                            int opacity = Settings.System.getInt(ctx.getContentResolver(), Keys.APPDRAWER, 100);
                            if (opacity >= 100 || opacity < 0) return;
                            int alpha = opacity * 255 / 100;
                            Object result = param.getResult();
                            if (result instanceof Integer) {
                                int color = (Integer) result;
                                param.setResult((color & 0x00FFFFFF) | (alpha << 24));
                            }
                        } catch (Throwable t) {}
                    }
                });
            }
            XposedBridge.log(TAG + ": hooked " + className + " colors");
        } catch (Throwable t) {}
    }

    private static Object rebuildScrimColors(Object result, int alpha) {
        try {
            Class<?> cls = result.getClass();
            int bg = 0, fg = 0;
            boolean found = false;
            try {
                Method getBg = cls.getMethod("getBackgroundColor");
                Method getFg = cls.getMethod("getForegroundColor");
                bg = (int) getBg.invoke(result);
                fg = (int) getFg.invoke(result);
                found = true;
            } catch (Throwable ignored) {}
            if (!found) {
                try {
                    java.lang.reflect.Field fBg = cls.getDeclaredField("backgroundColor");
                    java.lang.reflect.Field fFg = cls.getDeclaredField("foregroundColor");
                    fBg.setAccessible(true);
                    fFg.setAccessible(true);
                    bg = fBg.getInt(result);
                    fg = fFg.getInt(result);
                    found = true;
                } catch (Throwable ignored) {}
            }
            if (!found) return null;
            int newBg = (bg & 0x00FFFFFF) | (alpha << 24);
            Constructor<?> ctor = cls.getDeclaredConstructor(int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(newBg, fg);
        } catch (Throwable t) {
            return null;
        }
    }
}