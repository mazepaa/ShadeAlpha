package dev.shadealpha;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
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
    private static final String VOLUME_DIALOG_PACKAGE = "com.android.systemui.volume.dialog.";
    private static final String SURFACE_KT = "androidx.compose.material3.SurfaceKt";
    private static final String ORIGINAL_DIM = "shadealpha_volume_panel_original_dim";

    private static volatile Activity activeActivity;
    private static volatile Dialog activeVolumeDialog;
    private static volatile boolean logged;
    private static volatile boolean dialogLogged;

    private VolumePanelHook() {}

    static void hook(ClassLoader cl) {
        hookActivity(cl);
        hookVolumeDialog(cl);
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

    /**
     * The list of Alarm / Ring / Notification / Call sliders is the expanded state
     * of VolumeDialog, not VolumePanelActivity. Track the actual Dialog instance so
     * Compose Surface calls belonging to this window can be filtered reliably.
     */
    private static void hookVolumeDialog(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("android.app.Dialog", cl);
            if (cls == null) {
                XposedBridge.log(TAG + ": Dialog base class not found");
                return;
            }

            Set<XC_MethodHook.Unhook> shows = XposedBridge.hookAllMethods(cls, "show",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (isVolumeDialog(p.thisObject)) {
                                activeVolumeDialog = (Dialog) p.thisObject;
                                XposedBridge.log(TAG + ": expanded stream dialog became active: "
                                        + p.thisObject.getClass().getName());
                            }
                        }
                    });
            Set<XC_MethodHook.Unhook> dismisses = XposedBridge.hookAllMethods(cls, "dismiss",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.thisObject == activeVolumeDialog) activeVolumeDialog = null;
                        }
                    });

            XposedBridge.log(TAG + ": hooked Dialog lifecycle show="
                    + (shows == null ? 0 : shows.size())
                    + " dismiss=" + (dismisses == null ? 0 : dismisses.size()));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": expanded stream dialog lifecycle hook failed: " + t);
        }
    }

    private static boolean isVolumeDialog(Object object) {
        if (!(object instanceof Dialog)) return false;
        String name = object.getClass().getName();
        return name.startsWith(VOLUME_DIALOG_PACKAGE)
                && name.endsWith("VolumeDialog");
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
        Dialog volumeDialog = activeVolumeDialog;
        Activity panelActivity = activeActivity;
        boolean dialogSurface = volumeDialog != null && isVolumeDialogComposeCall();
        boolean panelSurface = panelActivity != null && isVolumePanelRootCall();
        if ((!dialogSurface && !panelSurface) || p.args == null) return;

        try {
            Context context = dialogSurface ? volumeDialog.getContext() : panelActivity;
            float factor = Prefs.volume(context);
            if (factor >= 0.999f) return;

            // Compose Color is represented as a packed long. Material3 Surface receives
            // background Color before contentColor, so scale only the first Color argument.
            for (int i = 0; i < p.args.length; i++) {
                if (p.args[i] instanceof Long) {
                    long original = ((Long) p.args[i]).longValue();
                    long adjusted = VolumeHook.scaleComposeColor(original, factor);
                    if (adjusted != original) p.args[i] = Long.valueOf(adjusted);

                    if (dialogSurface && !dialogLogged) {
                        dialogLogged = true;
                        XposedBridge.log(TAG + ": volume dialog Compose Surface first hit factor="
                                + factor + " colorArg=" + i);
                    } else if (panelSurface && !logged) {
                        logged = true;
                        XposedBridge.log(TAG + ": VolumePanel Compose Surface first hit factor="
                                + factor + " colorArg=" + i);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": expanded volume Surface opacity failed: " + t);
        }
    }

    private static boolean isVolumeDialogComposeCall() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            // Includes the dialog root, expanded stream rows and their Compose slider content.
            if (name.startsWith(VOLUME_DIALOG_PACKAGE)) return true;
        }
        return false;
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
