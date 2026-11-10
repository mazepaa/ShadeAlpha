package dev.shadealpha;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Прозрачность экрана ввода PIN / пароля / графического ключа (bouncer) в SystemUI.
 *
 * KeyguardSecurityContainer.reloadBackgroundColor() задаёт фон контейнера цветом из темы.
 * Система зовёт его при создании, смене темы и при КАЖДОМ показе экрана (prepareToShow),
 * поэтому достаточно подправить альфу после него: ничего не рисуем и не опрашиваем лишний раз.
 *
 * Работает для классического (View) bouncer. Compose-версия (scene container) этим хуком
 * не затрагивается — в логе тогда не будет строки "first hit".
 */
final class BouncerHook {

    private static final String TAG = "ShadeAlpha";
    private static final String CONTAINER = "com.android.keyguard.KeyguardSecurityContainer";

    static final int STATE_OTHER = 0;
    static final int STATE_BOUNCER = 1;
    static final int STATE_BOUNCER_SCRIMMED = 2;
    static final int STATE_KEYGUARD = 3;
    static final int STATE_UNLOCKED = 4;

    private static volatile int scrimState = STATE_OTHER;
    private static volatile boolean bouncerToGoneTransitionRunning;
    private static volatile float shadeExpansionFraction;
    private static volatile long unlockTransitionStartedAt;
    private static volatile WeakReference<View> container;
    private static volatile WeakReference<Object> scrimController;
    private static int lastScrimLogState = -1;
    private static float lastScrimLogAlpha = -1f;
    private static boolean loggedScrimBehindField;
    private static boolean loggedScrimBehindMissing;
    private static volatile boolean logged;
    private static int errors;
    private static int transitionLogs;
    private static float lastVisibility = -1f;
    private static long lastHierarchyScan;

    private BouncerHook() {}

    /** Текущее состояние подложек SystemUI: BOUNCER / BOUNCER_SCRIMMED (экран ввода), KEYGUARD или прочее. */
    static int scrimState() {
        return scrimState;
    }

    /** Истинно на протяжении системной анимации разблокировки Bouncer -> Gone. */
    static boolean isBouncerToGoneTransitionRunning() {
        return bouncerToGoneTransitionRunning;
    }

    private static void updateBouncerToGoneFlag(Object controller) {
        if (controller == null) return;
        Class<?> type = controller.getClass();
        while (type != null && type != Object.class) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("mIsBouncerToGoneTransitionRunning");
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value instanceof Boolean) bouncerToGoneTransitionRunning = (Boolean) value;
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                lockLog("BOUNCER_GONE_FLAG_READ_ERROR " + t.getClass().getSimpleName());
                return;
            }
        }
    }

    /**
     * Насколько экран ввода PIN сейчас виден: 0 — скрыт, 1 — показан полностью. Берём реальную
     * видимость контейнера с учётом альфы всех родителей: при свайпе вверх/вниз значение плавно
     * меняется вместе с экраном, поэтому подложки не «щёлкают» между режимами.
     */
    static float visibility() {
        WeakReference<View> ref = container;
        View v = ref == null ? null : ref.get();
        if (v == null || !v.isShown()) return 0f;
        float a = 1f;
        View x = v;
        for (int i = 0; i < 12 && x != null; i++) {
            a *= x.getAlpha();
            ViewParent p = x.getParent();
            x = (p instanceof View) ? (View) p : null;
        }
        return Math.max(0f, Math.min(1f, a));
    }

    static void hook(ClassLoader cl) {
        hookScrimState(cl);
        hookShadeExpansion(cl);
        // The hierarchy scanner hooked ViewGroup.dispatchDraw globally and was diagnostic only.
        // Keep the targeted bouncer/unlock hooks without per-frame SystemUI overhead.
        hookScrimBehindTransparent(cl);
        hookKeyguardFadeOut(cl);
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(CONTAINER, cl);
            if (cls == null) {
                lockLog("BOUNCER_HOOK_MISSING class=" + CONTAINER);
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "reloadBackgroundColor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p);
                        }
                    });
            lockLog("BOUNCER_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            lockLog("BOUNCER_HOOK_ERROR " + t);
        }
    }

    /**
     * Диагностика полноэкранных слоёв вокруг PIN-интерфейса.
     * Ничего не меняет: ищет крупные View с фоном/передним планом или собственной альфой,
     * чтобы обнаружить дополнительную подложку, не относящуюся к ScrimView.
     */
    private static void hookHierarchyDiagnostics() {
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(ViewGroup.class, "dispatchDraw",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                View root = (View) p.thisObject;
                                if (root != root.getRootView() || visibility() < 0.05f) return;
                                long now = android.os.SystemClock.uptimeMillis();
                                if (now - lastHierarchyScan < 500) return;
                                WeakReference<View> ref = container;
                                View target = ref == null ? null : ref.get();
                                if (target == null || !containsView(root, target)) return;
                                lastHierarchyScan = now;
                                logHierarchy(root, target);
                            } catch (Throwable t) {
                                lockLog("HIERARCHY_SCAN_ERROR " + t.getClass().getSimpleName());
                            }
                        }
                    });
            lockLog("HIERARCHY_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            lockLog("HIERARCHY_HOOK_ERROR " + t);
        }
    }

    /**
     * Точечный тест: меняет альфу только у ScrimView, который ScrimController
     * хранит в mScrimBehind. Передний и notification scrim не затрагиваются.
     */
    private static void hookScrimBehindTransparent(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimView", cl);
            if (cls == null) {
                lockLog("SCRIM_BEHIND_CLASS_MISSING");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "setViewAlpha",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            int state = scrimState;
                            float bouncerVisibility = visibility();
                            boolean shadeExpanded = isShadeExpanded();
                            boolean unlockTransition = shouldForceUnlockScrimZero();
                            // Do not force scrim_behind transparent on the ordinary lockscreen.
                            // ShadeAlpha must be allowed to apply the configured top-shade opacity there.
                            // Force zero only while the credential bouncer is active or during unlock.
                            // The swipe-to-bouncer animation starts while ScrimController may
                            // still report KEYGUARD. Remove mScrimBehind as soon as the PIN
                            // container becomes visible, not only after BOUNCER state arrives.
                            if (state != STATE_BOUNCER
                                    && state != STATE_BOUNCER_SCRIMMED
                                    && bouncerVisibility <= 0.01f
                                    && !unlockTransition) return;
                            if (p.args == null || p.args.length != 1
                                    || !(p.args[0] instanceof Float)) return;
                            Object controller = scrimController == null ? null : scrimController.get();
                            Object behind = readScrimBehind(controller);
                            if (behind == null || p.thisObject != behind) return;

                            float requested = ((Float) p.args[0]).floatValue();
                            if (lastScrimLogState != scrimState
                                    || Math.abs(requested - lastScrimLogAlpha) >= 0.05f) {
                                lastScrimLogState = scrimState;
                                lastScrimLogAlpha = requested;
                                lockLog("SCRIM_BEHIND_ALPHA_ZERO state=" + state
                                        + " requested=" + requested
                                        + " transitionHold=" + bouncerToGoneTransitionRunning
                                        + " bouncerVisibility=" + bouncerVisibility
                                        + " shadeExpansion=" + shadeExpansionFraction
                                        + " unlockHold=" + unlockTransition
                                        + " view=" + p.thisObject.getClass().getName());
                            }
                            p.args[0] = Float.valueOf(0f);
                        }
                    });
            lockLog("SCRIM_BEHIND_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            lockLog("SCRIM_BEHIND_HOOK_ERROR " + t);
        }
    }

    /**
     * Убирает именно системный fade-out подложки после успешной разблокировки.
     * Не меняет остальные ScrimView и не вмешивается в анимации PIN.
     */
    private static void hookKeyguardFadeOut(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.ScrimController", cl);
            if (cls == null) {
                lockLog("UNLOCK_FADE_CLASS_MISSING");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    cls, "animateKeyguardFadingOut", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                int forcedLongs = 0;
                                if (p.args != null) {
                                    for (int i = 0; i < p.args.length; i++) {
                                        if (p.args[i] instanceof Long) {
                                            p.args[i] = Long.valueOf(0L);
                                            forcedLongs++;
                                        }
                                    }
                                }
                                Object behind = readScrimBehind(p.thisObject);
                                if (behind != null) {
                                    XposedHelpers.callMethod(behind, "setViewAlpha", 0f);
                                }
                                lockLog("UNLOCK_FADE forced to 0, args="
                                        + (p.args == null ? 0 : p.args.length)
                                        + " longArgs=" + forcedLongs);
                            } catch (Throwable t) {
                                lockLog("UNLOCK_FADE_APPLY_ERROR " + t.getClass().getSimpleName());
                            }
                        }
                    });
            lockLog("UNLOCK_FADE_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            lockLog("UNLOCK_FADE_HOOK_ERROR " + t);
        }
    }

    private static Object readScrimBehind(Object controller) {
        if (controller == null) return null;
        Class<?> type = controller.getClass();
        while (type != null && type != Object.class) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("mScrimBehind");
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value != null && !loggedScrimBehindField) {
                    loggedScrimBehindField = true;
                    lockLog("SCRIM_BEHIND_FIELD_READY type=" + value.getClass().getName());
                }
                return value;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                lockLog("SCRIM_BEHIND_FIELD_ERROR " + t.getClass().getSimpleName());
                return null;
            }
        }
        if (!loggedScrimBehindMissing) {
            loggedScrimBehindMissing = true;
            lockLog("SCRIM_BEHIND_FIELD_MISSING controller=" + controller.getClass().getName());
        }
        return null;
    }

    private static boolean containsView(View root, View target) {
        if (root == target) return true;
        if (!(root instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsView(group.getChildAt(i), target)) return true;
        }
        return false;
    }

    private static void logHierarchy(View root, View target) {
        int rw = root.getWidth();
        int rh = root.getHeight();
        lockLog("HIERARCHY_BEGIN root=" + root.getClass().getName()
                + " size=" + rw + "x" + rh + " bouncer=" + viewId(target)
                + " bouncerBackground=" + drawableInfo(target.getBackground()));
        Object lp = root.getLayoutParams();
        if (lp instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
            lockLog("WINDOW_PARAMS type=" + wlp.type + " flags=0x"
                    + Integer.toHexString(wlp.flags) + " dimAmount=" + wlp.dimAmount
                    + " format=" + wlp.format);
        } else {
            lockLog("WINDOW_PARAMS unavailable class=" + (lp == null ? "null" : lp.getClass().getName()));
        }
        dumpLargeViews(root, root, rw, rh, 0);
        lockLog("HIERARCHY_END");
    }

    private static void dumpLargeViews(View view, View root, int rw, int rh, int depth) {
        if (depth > 30) return;
        int w = view.getWidth();
        int h = view.getHeight();
        boolean large = rw > 0 && rh > 0 && w >= rw * 0.70f && h >= rh * 0.55f;
        Drawable bg = view.getBackground();
        Drawable fg = view.getForeground();
        if (large && (bg != null || fg != null || view.getAlpha() < 0.995f)) {
            lockLog("HIERARCHY_LAYER depth=" + depth + " class=" + view.getClass().getName()
                    + " id=" + viewId(view) + " size=" + w + "x" + h
                    + " visibility=" + view.getVisibility() + " shown=" + view.isShown()
                    + " alpha=" + view.getAlpha() + " elevation=" + view.getElevation()
                    + " translationZ=" + view.getTranslationZ()
                    + " background=" + drawableInfo(bg) + " foreground=" + drawableInfo(fg));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpLargeViews(group.getChildAt(i), root, rw, rh, depth + 1);
            }
        }
    }

    /** ScrimController.internalTransitionTo(callback, state) вызывается при каждой смене состояния. */
    private static void hookScrimState(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.ScrimController", cl);
            if (cls == null) {
                lockLog("SCRIM_CONTROLLER_MISSING");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "internalTransitionTo",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            onTransition(p.thisObject, p.args);
                        }
                    });
            lockLog("SCRIM_STATE_HOOK_READY methods=" + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            lockLog("SCRIM_STATE_HOOK_ERROR " + t);
        }
    }

    static void onTransition(Object controller, Object[] args) {
        scrimController = new WeakReference<Object>(controller);
        updateBouncerToGoneFlag(controller);
        if (args == null) return;
        for (Object a : args) {
            if (a instanceof Enum) {
                String n = ((Enum<?>) a).name();
                scrimState = "BOUNCER".equals(n) ? STATE_BOUNCER
                        : "BOUNCER_SCRIMMED".equals(n) ? STATE_BOUNCER_SCRIMMED
                        : "KEYGUARD".equals(n) ? STATE_KEYGUARD
                        : "UNLOCKED".equals(n) ? STATE_UNLOCKED : STATE_OTHER;
                if (scrimState == STATE_UNLOCKED) {
                    unlockTransitionStartedAt = android.os.SystemClock.uptimeMillis();
                } else if (scrimState == STATE_KEYGUARD || scrimState == STATE_BOUNCER
                        || scrimState == STATE_BOUNCER_SCRIMMED) {
                    unlockTransitionStartedAt = 0L;
                }
                if (++transitionLogs <= 300) lockLog("STATE_TRANSITION state=" + n + " code=" + scrimState
                        + " bouncerVisibility=" + visibility()
                        + " bouncerToGone=" + bouncerToGoneTransitionRunning
                        + " controllerFlags=" + controllerTransitionFlags(controller));
                return;
            }
        }
    }



    /** Tracks the real notification panel expansion; KEYGUARD remains active while shade is dragged. */
    private static void hookShadeExpansion(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.shade.NotificationPanelViewController", cl);
            if (cls == null) {
                lockLog("SHADE_EXPANSION_CLASS_MISSING");
                return;
            }
            int total = 0;
            String[] methods = {"setExpandedHeightInternal", "setExpandedHeight", "setExpandedFraction"};
            for (String method : methods) {
                final String methodName = method;
                Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, methodName, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        updateShadeExpansion(p.thisObject, p.args, methodName);
                    }
                });
                if (hooks != null) total += hooks.size();
            }
            lockLog("SHADE_EXPANSION_HOOK_READY methods=" + total);
        } catch (Throwable t) {
            lockLog("SHADE_EXPANSION_HOOK_ERROR " + t.getClass().getSimpleName());
        }
    }

    private static void updateShadeExpansion(Object controller, Object[] args, String method) {
        try {
            Float value = readFloatField(controller, "mExpandedFraction");
            if (value == null && "setExpandedFraction".equals(method)
                    && args != null && args.length > 0 && args[0] instanceof Float) {
                value = (Float) args[0];
            }
            if (value == null) return;
            float next = Math.max(0f, Math.min(1f, value));
            if (Math.abs(next - shadeExpansionFraction) >= 0.01f) {
                shadeExpansionFraction = next;
                lockLog("SHADE_EXPANSION fraction=" + next + " source=" + method);
            }
        } catch (Throwable t) {
            lockLog("SHADE_EXPANSION_READ_ERROR " + t.getClass().getSimpleName());
        }
    }

    private static Float readFloatField(Object object, String name) {
        if (object == null) return null;
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(object);
                return value instanceof Number ? Float.valueOf(((Number) value).floatValue()) : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    static boolean isShadeExpanded() {
        return shadeExpansionFraction > 0.01f;
    }

    /** Short unlock guard catches late ValueAnimator/Compose alpha writes after the duration hook. */
    static boolean shouldForceUnlockScrimZero() {
        if (scrimState != STATE_UNLOCKED || isShadeExpanded()) return false;
        long started = unlockTransitionStartedAt;
        return started > 0L && android.os.SystemClock.uptimeMillis() - started < 1200L;
    }

    /**
     * Диагностика полей ScrimController, связанных с переходом Bouncer -> Gone.
     * Имена полей зависят от версии SystemUI, поэтому читаем только существующие boolean-поля
     * с подходящими именами и ничего не меняем.
     */
    private static String controllerTransitionFlags(Object controller) {
        if (controller == null) return "controller=null";
        StringBuilder out = new StringBuilder();
        Class<?> type = controller.getClass();
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!(name.contains("bouncer") || name.contains("gone") || name.contains("transition"))) continue;
                if (field.getType() != boolean.class && field.getType() != Boolean.class) continue;
                try {
                    field.setAccessible(true);
                    if (out.length() > 0) out.append(",");
                    out.append(field.getName()).append("=").append(field.get(controller));
                } catch (Throwable ignored) {
                    if (out.length() > 0) out.append(",");
                    out.append(field.getName()).append("=inaccessible");
                }
            }
            type = type.getSuperclass();
        }
        return out.length() == 0 ? "no_matching_boolean_fields" : out.toString();
    }

    private static void logVisibilityIfChanged(float value) {
        if (Math.abs(value - lastVisibility) > 0.005f) {
            float old = lastVisibility;
            lastVisibility = value;
            lockLog("BOUNCER_VISIBILITY old=" + old + " new=" + value + " state=" + scrimState);
        }
    }

    private static String viewId(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "NO_ID";
        try { return v.getResources().getResourceName(id); }
        catch (Throwable ignored) { return "0x" + Integer.toHexString(id); }
    }

    private static String colorHex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    private static String drawableInfo(Drawable d) {
        if (d == null) return "null";
        StringBuilder s = new StringBuilder(d.getClass().getName());
        s.append("{alpha=").append(d.getAlpha()).append(",opacity=").append(d.getOpacity())
                .append(",bounds=").append(d.getBounds());
        if (d instanceof ColorDrawable) {
            int color = ((ColorDrawable) d).getColor();
            s.append(",color=").append(colorHex(color))
                    .append(",colorAlpha=").append(Color.alpha(color));
        }
        return s.append('}').toString();
    }

    private static void lockLog(String message) {
        XposedBridge.log(TAG + ": LOCKSCREEN " + message);
    }

    private static void apply(XC_MethodHook.MethodHookParam p) {
        try {
            if (!(p.thisObject instanceof View)) return;
            View v = (View) p.thisObject;
            if (container == null || container.get() != v) container = new WeakReference<View>(v);
            float f = Prefs.bouncer(v.getContext());
            Drawable bg = v.getBackground();
            float visible = visibility();
            if (!logged) {
                logged = true;
                lockLog("BOUNCER_FIRST_HIT class=" + v.getClass().getName()
                        + " id=" + viewId(v) + " factor=" + f
                        + " visibility=" + visible + " background=" + drawableInfo(bg));
            }
            logVisibilityIfChanged(visible);
            int original = bg instanceof ColorDrawable ? ((ColorDrawable) bg).getColor() : 0;
            lockLog("BOUNCER_RELOAD factor=" + f + " state=" + scrimState
                    + " viewAlpha=" + v.getAlpha() + " backgroundBefore=" + drawableInfo(bg));
            if (f >= 0.999f || !(bg instanceof ColorDrawable)) {
                lockLog("BOUNCER_NO_CHANGE reason=" + (f >= 0.999f ? "factor_100" : "not_ColorDrawable"));
                return;
            }
            int a = Math.max(0, Math.min(255, Math.round(Color.alpha(original) * f)));
            int result = (original & 0x00FFFFFF) | (a << 24);
            if (result != original) v.setBackgroundColor(result);
            lockLog("BOUNCER_BACKGROUND_APPLIED original=" + colorHex(original)
                    + " result=" + colorHex(result) + " originalAlpha=" + Color.alpha(original)
                    + " resultAlpha=" + Color.alpha(result) + " factor=" + f
                    + " backgroundAfter=" + drawableInfo(v.getBackground()));
        } catch (Throwable t) {
            if (++errors <= 100) lockLog("BOUNCER_APPLY_ERROR count=" + errors + " exception=" + t);
        }
    }
}
