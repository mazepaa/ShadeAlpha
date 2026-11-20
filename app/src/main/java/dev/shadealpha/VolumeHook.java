package dev.shadealpha;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Volume panel transparency (Android 16 dialog implementation).
 *
 * The panel background, the ringer backdrop and the floating sliders are plain
 * views with a GradientDrawable; only their drawable alpha is touched, never
 * View.alpha, which the system animates. The slider tracks are drawn by Compose
 * and are recoloured separately.
 *
 * Applied on every VolumeDialog.onStart, so a new value is picked up on the next show.
 */
final class VolumeHook {

    private static final String TAG = "ShadeAlpha";
    private static final String PKG = "com.android.systemui";
    private static final String DIALOG = "com.android.systemui.volume.dialog.VolumeDialog";
    private static final String TRACK_KT = "com.android.systemui.volume.dialog.sliders.ui.compose.VolumeDialogSliderTrackKt";
    private static final String SLIDER_COLORS = "androidx.compose.material3.SliderColors";
    // active, disabledActive, inactive, disabledInactive
    private static final String[] COLOR_FIELDS = {
            "activeTrackColor", "disabledActiveTrackColor", "inactiveTrackColor", "disabledInactiveTrackColor"
    };
    private static Field[] colorFields;
    private static boolean colorFieldsFailed;
    private static volatile boolean trackLogged;
    /** Extra-instance field holding {base colour, last written colour} for a GradientDrawable. */
    private static final String BASE_KEY = "shadealpha_fillAlpha";

    private static volatile int idBackground;
    private static volatile int idRinger;
    private static volatile int idFloating;
    private static volatile boolean logged;
    private static int errors;

    private VolumeHook() {}

    static void hook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(DIALOG, cl);
            if (cls == null) {
                Logs.log(TAG + ": volume: " + DIALOG + " not found");
                return;
            }
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(cls, "onStart",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            apply(p.thisObject);
                        }
                    });
            Logs.log(TAG + ": hooked volume dialog x" + (hooks == null ? 0 : hooks.size()));
            if (hooks != null && !hooks.isEmpty()) HookStatus.mark(HookStatus.VOLUME);
        } catch (Throwable t) {
            Logs.log(TAG + ": volume hook failed: " + t);
        }
        hookSliderTrack(cl);
    }

    /**
     * Slider track colours come as packed longs inside a SliderColors object.
     * The Compose function name carries a signature hash, so every method starting
     * with "SliderTrack" is hooked.
     */
    private static void hookSliderTrack(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(TRACK_KT, cl);
            if (cls == null) {
                Logs.log(TAG + ": volume slider: " + TRACK_KT + " not found");
                return;
            }
            Set<String> names = new HashSet<String>();
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().startsWith("SliderTrack")) names.add(m.getName());
            }
            int n = 0;
            for (String name : names) {
                Set<XC_MethodHook.Unhook> h = XposedBridge.hookAllMethods(cls, name, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        recolor(p.args);
                    }
                });
                n += h == null ? 0 : h.size();
            }
            Logs.log(TAG + ": hooked volume slider track x" + n);
            if (n > 0) HookStatus.mark(HookStatus.VOLUME_TRACK);
        } catch (Throwable t) {
            Logs.log(TAG + ": volume slider hook failed: " + t);
        }
    }

    private static void recolor(Object[] args) {
        if (args == null) return;
        for (Object a : args) {
            if (a != null && SLIDER_COLORS.equals(a.getClass().getName())) {
                applyColors(a);
                return;
            }
        }
    }

    /** Scales the track/fill alpha. Originals are kept per object, so calls never compound. */
    static void applyColors(Object sc) {
        try {
            Field[] fs = colorFields(sc.getClass());
            if (fs == null) return;
            float fill = Prefs.volumeFill(null);
            float track = Prefs.volumeTrack(null);
            float[] f = {fill, fill, track, track};

            long[] cur = new long[4];
            for (int i = 0; i < 4; i++) cur[i] = fs[i].getLong(sc);
            long[] st = (long[]) XposedHelpers.getAdditionalInstanceField(sc, "shadealpha_slider");
            if (st == null) {
                st = new long[8];
                for (int i = 0; i < 4; i++) {
                    st[i] = cur[i];
                    st[4 + i] = cur[i];
                }
                XposedHelpers.setAdditionalInstanceField(sc, "shadealpha_slider", st);
            } else {
                for (int i = 0; i < 4; i++) {
                    if (cur[i] != st[4 + i]) st[i] = cur[i]; // system changed it: that is the new original
                }
            }
            if (!trackLogged) {
                trackLogged = true;
                Logs.log(TAG + ": volume slider first hit fill=" + fill + " track=" + track);
            }
            for (int i = 0; i < 4; i++) {
                long nv = (f[i] >= 0.999f) ? st[i] : scaleComposeColor(st[i], f[i]);
                if (nv != cur[i]) fs[i].setLong(sc, nv);
                st[4 + i] = nv;
            }
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume slider colors failed: " + t);
        }
    }

    private static Field[] colorFields(Class<?> cls) {
        if (colorFields != null) return colorFields;
        if (colorFieldsFailed) return null;
        try {
            Field[] fs = new Field[4];
            for (int i = 0; i < 4; i++) {
                fs[i] = cls.getDeclaredField(COLOR_FIELDS[i]);
                fs[i].setAccessible(true);
            }
            colorFields = fs;
            return fs;
        } catch (Throwable t) {
            colorFieldsFailed = true;
            Logs.log(TAG + ": volume slider: SliderColors fields not found: " + t);
            return null;
        }
    }

    /** Packed Compose Color: sRGB keeps alpha in the top 8 bits, otherwise 10 bits at 6..15. */
    static long scaleComposeColor(long v, float f) {
        if ((v & 0x3FL) == 0L) {
            int a = (int) ((v >>> 56) & 0xFFL);
            int na = Math.max(0, Math.min(255, Math.round(a * f)));
            return (v & 0x00FFFFFFFFFFFFFFL) | ((long) na << 56);
        }
        int a = (int) ((v >>> 6) & 0x3FFL);
        int na = Math.max(0, Math.min(1023, Math.round(a * f)));
        return (v & ~(0x3FFL << 6)) | ((long) na << 6);
    }

    private static void apply(Object self) {
        try {
            if (!(self instanceof Dialog)) return;
            Dialog d = (Dialog) self;
            Context ctx = d.getContext();
            if (HookStatus.pending()) HookStatus.flush(ctx);
            float f = Prefs.volume(ctx);
            resolveIds(ctx);

            View bg = idBackground != 0 ? d.findViewById(idBackground) : null;
            View ringer = idRinger != 0 ? d.findViewById(idRinger) : null;
            View floating = idFloating != 0 ? d.findViewById(idFloating) : null;
            if (!logged) {
                logged = true;
                Logs.log(TAG + ": volume first hit factor=" + f
                        + " background=" + (bg != null) + " ringer=" + (ringer != null)
                        + " floating=" + (floating != null));
            }

            setBackgroundAlpha(bg, f);
            setupRingerCap(ringer, bg, f);
            if (floating instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) floating;
                // onStart runs again for every show; a fresh listener replaces the old one.
                g.setOnHierarchyChangeListener(new SliderChildListener());
                for (int i = 0; i < g.getChildCount(); i++) setBackgroundAlpha(g.getChildAt(i), f);
            }
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume apply failed: " + t);
        }
    }

    /** Alpha for sliders that appear after the panel is already up. */
    private static final class SliderChildListener implements ViewGroup.OnHierarchyChangeListener {
        @Override
        public void onChildViewAdded(View parent, View child) {
            setBackgroundAlpha(child, Prefs.volume(child.getContext()));
        }

        @Override
        public void onChildViewRemoved(View parent, View child) { }
    }

    /**
     * The ringer backdrop has the same colour as the panel background and partly lies on top of it.
     * Opaque they merge; translucent they add up and a dark band shows in the overlap.
     *
     * Important: ringer_buttons_background must stay a real GradientDrawable. The system's
     * VolumeDialogRingerViewBinder casts that background to GradientDrawable while animating a
     * ringer-mode change, so any wrapper crashes SystemUI with a ClassCastException.
     */
    private static void setupRingerCap(View ringer, View plate, float f) {
        if (ringer == null) return;
        try {
            Drawable target = ringer.getBackground();
            if (target == null) return;
            setBackgroundAlpha(ringer, f);
            if (plate != null && target instanceof GradientDrawable) {
                ensureDrawHook();
                Cap c = cap;
                if (c == null || c.drawable.get() != target) {
                    c = new Cap(target, ringer, plate);
                    cap = c;
                }
                c.clip = f < 0.999f;
            } else {
                cap = null;
            }
            ringer.invalidate();
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume ringer cap failed: " + t);
        }
    }

    private static volatile Cap cap;
    private static boolean drawHooked;

    /** Weak refs so a SystemUI restart cannot be held back by this static holder. */
    private static final class Cap {
        final WeakReference<Drawable> drawable;
        private final WeakReference<View> ringer;
        private final WeakReference<View> plate;
        volatile boolean clip;
        // Reused between frames: draw() runs on every panel frame and must not allocate.
        private final int[] ringerPos = new int[2];
        private final int[] platePos = new int[2];

        Cap(Drawable d, View ringer, View plate) {
            this.drawable = new WeakReference<Drawable>(d);
            this.ringer = new WeakReference<View>(ringer);
            this.plate = new WeakReference<View>(plate);
        }

        /**
         * Computes the panel-background rectangle relative to the ringer view origin.
         * Callers clip out only that area, so the part of the backdrop sticking out beside the
         * panel (an expanded ringer row) is still drawn whole, rounded corners included.
         */
        boolean overlap(int[] out) {
            View r = ringer.get();
            View p = plate.get();
            if (r == null || p == null) return false;
            r.getLocationInWindow(ringerPos);
            p.getLocationInWindow(platePos);
            out[0] = platePos[0] - ringerPos[0];
            out[1] = platePos[1] - ringerPos[1];
            out[2] = out[0] + p.getWidth();
            out[3] = out[1] + p.getHeight();
            return true;
        }
    }

    private static final String EXTRA_SAVE = "shadealpha.capSave";

    /** One hook on GradientDrawable.draw; acts only on the ringer backdrop (identity check). */
    private static synchronized void ensureDrawHook() {
        if (drawHooked) return;
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(GradientDrawable.class, "draw",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                Cap c = cap;
                                if (c == null || !c.clip || p.thisObject != c.drawable.get()) return;
                                Canvas canvas = (Canvas) p.args[0];
                                if (canvas == null) return;
                                Rect r = ((Drawable) p.thisObject).getBounds();
                                // Record the save id before anything else can throw: the restore in
                                // afterHookedMethod only runs when this extra is present.
                                int save = canvas.save();
                                p.setObjectExtra(EXTRA_SAVE, Integer.valueOf(save));
                                // Move the canvas to the drawable origin so the overlap rect applies.
                                canvas.translate(r.left, r.top);
                                if (c.overlap(overlapRect)) {
                                    canvas.clipOutRect(overlapRect[0], overlapRect[1],
                                            overlapRect[2], overlapRect[3]);
                                }
                            } catch (Throwable t) {
                                if (++errors <= 3) Logs.log(TAG + ": volume cap draw failed: " + t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            Object save = p.getObjectExtra(EXTRA_SAVE);
                            if (save instanceof Integer) {
                                ((Canvas) p.args[0]).restoreToCount((Integer) save);
                            }
                        }
                    });
            int n = hooks == null ? 0 : hooks.size();
            Logs.log(TAG + ": hooked GradientDrawable.draw x" + n);
            // Only latch on success: a throwing hook must be retried on the next show.
            if (n > 0) drawHooked = true;
        } catch (Throwable t) {
            Logs.log(TAG + ": volume cap draw hook failed: " + t);
        }
    }

    private static final int[] overlapRect = new int[4];

    private static void resolveIds(Context ctx) {
        if (idBackground != 0) return;
        try {
            android.content.res.Resources r = ctx.getResources();
            idBackground = r.getIdentifier("volume_dialog_background", "id", PKG);
            idRinger = r.getIdentifier("ringer_buttons_background", "id", PKG);
            idFloating = r.getIdentifier("volume_dialog_floating_sliders_container", "id", PKG);
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume ids failed: " + t);
        }
    }

    private static void setBackgroundAlpha(View v, float f) {
        if (v == null) return;
        try {
            Drawable d = v.getBackground();
            if (d == null) return;
            int a = Math.max(0, Math.min(255, Math.round(255f * f)));
            // GradientDrawable keeps its opacity in the fill colour, where setAlpha is a no-op,
            // so the colour has to be scaled instead.
            if (d instanceof GradientDrawable) applyAlphaToColor((GradientDrawable) d, a);
            d.mutate().setAlpha(a);
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume alpha failed: " + t);
        }
    }

    /**
     * Scales the fill colour of a GradientDrawable, which is where its opacity actually lives.
     *
     * The original colour ("base") and the value we last wrote ("applied") are kept on the
     * drawable: when nobody else touched the colour, the base still applies to the new alpha,
     * so moving the slider from 100% to 50% to 30% gives 30% instead of compounding. If the
     * system changed the colour in between, that colour becomes the new base. Gradients have no
     * fill colour and keep the native alpha path.
     */
    private static void applyAlphaToColor(GradientDrawable g, int alpha) {
        try {
            Drawable.ConstantState cs = g.getConstantState();
            if (cs == null) return;
            // A gradient drawable has no fill colour to scale; leave it on the native alpha path.
            try {
                Field grad = cs.getClass().getDeclaredField("mGradient");
                grad.setAccessible(true);
                if (grad.get(cs) != null) return;
            } catch (NoSuchFieldException ignored) {
                // Older builds have no such field; the fill colour path below is still valid.
            }
            Field f = cs.getClass().getDeclaredField("mFillColor");
            f.setAccessible(true);
            Object v = f.get(cs);
            if (!(v instanceof android.content.res.ColorStateList)) return;
            android.content.res.ColorStateList list = (android.content.res.ColorStateList) v;
            int cur = list.getDefaultColor();
            if ((cur >>> 24) == 0 && (cur & 0x00FFFFFF) == 0) return;
            int[] st = (int[]) XposedHelpers.getAdditionalInstanceField(g, BASE_KEY);
            if (st == null || cur != st[1]) {
                // First call, or the system replaced the colour: that colour is the new base.
                st = new int[] {cur, cur};
            }
            int base = st[0];
            int na = (base >>> 24) * alpha / 255;
            int next = (cur & 0x00FFFFFF) | (na << 24);
            st[1] = next;
            XposedHelpers.setAdditionalInstanceField(g, BASE_KEY, st);
            if (next != cur) g.setColor(android.content.res.ColorStateList.valueOf(next));
        } catch (Throwable t) {
            if (++errors <= 3) Logs.log(TAG + ": volume fill alpha failed: " + t);
        }
    }
}
