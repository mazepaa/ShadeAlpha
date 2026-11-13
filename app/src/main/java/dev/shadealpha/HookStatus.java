package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

import de.robv.android.xposed.XposedBridge;

/**
 * Records which hooks actually installed, in a {@code Settings.System} row the app can read.
 *
 * Hooks are installed long before SystemUI has an Application object, so the bits are collected
 * in memory by {@link #mark(int)} and written by the first {@link #flush(Context)} call that has a
 * real Context (the first scrim draw, volume panel or bouncer draw). The {@link #REPORTED} flag
 * tells the settings screen that SystemUI did report, even when every hook failed.
 */
final class HookStatus {

    static final int SHADE = 1;
    static final int BOUNCER = 1 << 1;
    static final int VOLUME = 1 << 2;
    static final int VOLUME_TRACK = 1 << 3;
    static final int LAUNCHER = 1 << 4;
    static final int DRAWER = 1 << 5;
    /** Set by SystemUI so "nothing hooked" differs from "never reported". */
    static final int REPORTED = 1 << 30;

    private static final String TAG = "ShadeAlpha";
    private static final Object LOCK = new Object();

    private static int bits = 0;
    /** Last value written in this process; -1 means nothing was written yet. */
    private static int written = -1;

    private HookStatus() {}

    static void mark(int bit) {
        synchronized (LOCK) {
            bits |= bit;
        }
    }

    /** Writes the collected bits once per change; cheap enough to call from a draw path. */
    static void flush(Context c) {
        if (c == null) return;
        int value;
        synchronized (LOCK) {
            value = bits | REPORTED;
            if (value == written) return;
            written = value;
        }
        try {
            Settings.System.putInt(c.getContentResolver(), Keys.HOOKS, value);
        } catch (Throwable t) {
            synchronized (LOCK) {
                written = -1;
            }
            XposedBridge.log(TAG + ": hook status write failed: " + t);
        }
    }

    /** True when a flush would write something new, so callers can skip building a Context. */
    static boolean pending() {
        synchronized (LOCK) {
            return (bits | REPORTED) != written;
        }
    }
}
