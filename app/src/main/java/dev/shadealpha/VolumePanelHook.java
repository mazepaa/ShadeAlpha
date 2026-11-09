package dev.shadealpha;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Opacity for the expanded, full-screen Volume Panel (Compose Activity).
 *
 * This is separate from VolumeDialog: Android 16 renders the expanded panel
 * in VolumePanelActivity, with its rounded background drawn by Material3 Surface
 * inside VolumePanelRoot. We scale that Surface color, and the window dim behind it.
 */
final class VolumePanelHook {
    private static final String TAG = "ShadeAlpha";
    private static final String ACTIVITY =
            "com.android.systemui.volume.panel.ui.activity.VolumePanelActivity";
    private static final String SURFACE_KT = "androidx.compose.material3.SurfaceKt";
    private static final String ORIGINAL_DIM = "shadealpha_volume_panel_original_dim";

    private static volatile Activity activeActivity;
    private static volatile boolean logged;

    private VolumePanelHook() {}

    static void hook(ClassLoader cl) {
        hookActivity(cl);
        hookComposeSurface(cl);
    }

    private static void hookActivity(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("android.app.Activity", cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": Activity base class not found");
                return;
            }

            // Hook the base methods so the logic also works when VolumePanelActivity
            // inherits onStart/onStop without overriding them.
            Set<XC_MethodHook.Unhook> starts = XposedBridge.hookAllMethods(cls, "onStart",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.thisObject instanceof Activity
                                    && ACTIVITY.equals(p.thisObject.getClass().getName())) {
                                Activity activity = (Activity) p.thisObject;
                                activeActivity = activity;
                                applyWindowDim(activity);
                            }
                        }
                    });

            Set<XC_MethodHook.Unhook> stops = XposedBridge.hookAllMethods(cls, "onStop",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.thisObject == activeActivity) activeActivity = null;
                        }
                    });

            XposedBridge.log(TAG + ": hooked Activity lifecycle start="
                    + (starts == null ? 0 : starts.size())
                    + " stop=" + (stops == null ? 0 : stops.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": expanded volume activity hook failed: " + t);
        }
    }

    private static void applyWindowDim(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window == null) return;

            WindowManager.LayoutParams attrs = window.getAttributes();
            Object saved = XposedHelpers.getAdditionalInstanceField(activity, ORIGINAL_DIM);
            float original;
            if (saved instanceof Float) {
                original = ((Float) saved).floatValue();
            } else {
                original = attrs.dimAmount;
                XposedHelpers.setAdditionalInstanceField(activity, ORIGINAL_DIM,
                        Float.valueOf(original));
            }

            float factor = Prefs.volume(activity);
            float target = Math.max(0f, Math.min(1f, original * factor));
            if (Math.abs(attrs.dimAmount - target) > 0.001f) {
                attrs.dimAmount = target;
                window.setAttributes(attrs);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": expanded volume window dim failed: " + t);
        }
    }

    private static void hookComposeSurface(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(SURFACE_KT, cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": Compose SurfaceKt not found");
                return;
            }

            Set<String> names = new HashSet<String>();
            for (Method method : cls.getDeclaredMethods()) {
                String name = method.getName();
                // Kotlin creates a $default bridge which delegates to the real composable.
                // Hooking both would multiply the alpha twice.
                if (name.startsWith("Surface") && !name.contains("$default")) names.add(name);
            }

            int count = 0;
            for (String name : names) {
                Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, name,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam p) {
                                applySurfaceOpacity(p);
                            }
                        });
                count += hooks == null ? 0 : hooks.size();
            }
            XposedBridge.log(TAG + ": hooked Compose Surface methods x" + count);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Compose Surface hook failed: " + t);
        }
    }

    private static void applySurfaceOpacity(XC_MethodHook.MethodHookParam p) {
        Activity activity = activeActivity;
        if (activity == null || p.args == null || !isVolumePanelRootCall()) return;

        try {
            // The expanded panel has its own slider; do not reuse the compact dialog opacity.
            float factor = Prefs.volumeExpanded(activity);
            if (factor >= 0.999f) return;

            // Compose Color is represented as a packed long. In Material3 Surface
            // the first Color argument is the background color; contentColor follows it.
            for (int i = 0; i < p.args.length; i++) {
                if (p.args[i] instanceof Long) {
                    long original = ((Long) p.args[i]).longValue();
                    long adjusted = VolumeHook.scaleComposeColor(original, factor);
                    if (adjusted != original) p.args[i] = Long.valueOf(adjusted);
                    if (!logged) {
                        logged = true;
                        XposedBridge.log(TAG + ": expanded volume Surface first hit factor="
                                + factor + " colorArg=" + i);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": expanded volume Surface opacity failed: " + t);
        }
    }

    /** The root panel's content is composed from VolumePanelRootKt and its generated lambdas. */
    private static boolean isVolumePanelRootCall() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.contains("VolumePanelRootKt")) return true;
        }
        return false;
    }
}
