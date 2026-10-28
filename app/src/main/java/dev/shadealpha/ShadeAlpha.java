package dev.shadealpha;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShadeAlpha implements IXposedHookLoadPackage {

    private static final String TAG = "ShadeAlpha";
    private static final WeakHashMap<View, Boolean> activeScrims = new WeakHashMap<>();
    private static final Object lock = new Object();

    private static final int ROLE_NONE = 0, ROLE_TOP = 1, ROLE_BOTTOM = 2;
    private static int failures = 0;
    private static boolean disabled = false;
    private static boolean observerRegistered = false;

    private static int valTop = 100, valBottom = 100, valMode = 0; // По умолчанию 0 (Слой)

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("dev.shadealpha")) {
            XposedHelpers.findAndHookMethod("dev.shadealpha.Keys", lpparam.classLoader, "isModuleActive", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) { return true; }
            });
            XposedHelpers.findAndHookMethod("dev.shadealpha.Keys", lpparam.classLoader, "getHookStatus", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) { return "onDraw hooked"; }
            });
            return;
        }

        if (!lpparam.packageName.equals("com.android.systemui")) return;

        try {
            XposedHelpers.findAndHookMethod("com.android.systemui.scrim.ScrimView", lpparam.classLoader, "onDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (disabled) return;
                    try {
                        View view = (View) param.thisObject;
                        if (!observerRegistered) {
                            Context ctx = view.getContext();
                            if (ctx != null) {
                                registerObserver(ctx);
                                observerRegistered = true;
                            }
                        }

                        int role = getRole(view);
                        if (role == ROLE_NONE) return;

                        synchronized (lock) { activeScrims.put(view, true); }

                        float alpha = (role == ROLE_TOP ? valTop : valBottom) / 100f;

                        // Режим 0: saveLayerAlpha (Рабочий)
                        if (valMode == 0) {
                            if (alpha < 0.999f && param.args != null && param.args.length > 0 && param.args[0] instanceof Canvas) {
                                Canvas canvas = (Canvas) param.args[0];
                                int sc = canvas.saveLayerAlpha(0, 0, view.getWidth(), view.getHeight(), (int) (alpha * 255));
                                param.setObjectExtra("sa_sc", sc);
                                param.setObjectExtra("sa_canvas", canvas);
                            }
                        } 
                        // Режим 1: View Alpha
                        else if (valMode == 1) {
                            if (view.getAlpha() != alpha) view.setAlpha(alpha);
                        } 
                        // Режим 2: Scrim Alpha
                        else if (valMode == 2) {
                            try { XposedHelpers.callMethod(view, "setViewAlpha", alpha); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        handleError(t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (disabled || valMode != 0) return;
                    try {
                        Integer sc = (Integer) param.getObjectExtra("sa_sc");
                        Canvas canvas = (Canvas) param.getObjectExtra("sa_canvas");
                        if (sc != null && canvas != null) {
                            canvas.restoreToCount(sc);
                        }
                    } catch (Throwable t) {
                        handleError(t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook failed: " + t.getMessage());
        }
    }

    private static void registerObserver(Context ctx) {
        try {
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    updateValues(ctx);
                }
            };
            ctx.getContentResolver().registerContentObserver(Settings.System.getUriFor(Keys.TOP), false, observer);
            ctx.getContentResolver().registerContentObserver(Settings.System.getUriFor(Keys.BOTTOM), false, observer);
            ctx.getContentResolver().registerContentObserver(Settings.System.getUriFor(Keys.MODE), false, observer);
            updateValues(ctx);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Observer failed: " + t.getMessage());
        }
    }

    private static void updateValues(Context ctx) {
        try {
            int newTop = Settings.System.getInt(ctx.getContentResolver(), Keys.TOP, 100);
            int newBottom = Settings.System.getInt(ctx.getContentResolver(), Keys.BOTTOM, 100);
            int newMode = Settings.System.getInt(ctx.getContentResolver(), Keys.MODE, 0); // 0 = Слой

            boolean changed = (newTop != valTop || newBottom != valBottom || newMode != valMode);
            valTop = newTop; valBottom = newBottom; valMode = newMode;

            if (changed) {
                synchronized (lock) {
                    for (View v : activeScrims.keySet()) {
                        if (v != null) v.post(v::invalidate);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Read failed: " + t.getMessage());
        }
    }

    private static int getRole(View v) {
        Object r = XposedHelpers.getAdditionalInstanceField(v, "sa_role");
        if (r != null) return (Integer) r;
        String idName = "unknown";
        try {
            int id = v.getId();
            if (id != View.NO_ID) idName = v.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {}
        int role = ROLE_NONE;
        if ("scrim_behind".equals(idName)) role = ROLE_TOP;
        else if ("scrim_notifications".equals(idName)) role = ROLE_BOTTOM;
        XposedHelpers.setAdditionalInstanceField(v, "sa_role", role);
        return role;
    }

    private static void handleError(Throwable t) {
        if (++failures > 20) {
            disabled = true;
            XposedBridge.log(TAG + ": Too many errors, hook disabled! Last error: " + t.getMessage());
        }
    }
}