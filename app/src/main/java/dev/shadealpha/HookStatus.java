package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

import de.robv.android.xposed.XposedBridge;

/**
 * Records which hooks actually installed, in a {@code Settings.System} row the app can read.
 *
 * Two processes report: SystemUI (shade, lock screen, volume) and the launcher (recents, drawer).
 * Each process owns a fixed set of bits plus its own "reported" flag and merges them into the row
 * instead of overwriting it, otherwise whichever process starts last would erase the other answer.
 *
 * Hooks are installed long before the process has an Application object, so bits are collected in
 * memory by {@link #mark(int)} and written by the first {@link #flush(Context)} that has a real
 * Context.
 */
final class HookStatus {

    static final int SHADE = 1;
    static final int BOUNCER = 1 << 1;
    static final int VOLUME = 1 << 2;
    static final int VOLUME_TRACK = 1 << 3;
    static final int LAUNCHER = 1 << 4;
    static final int DRAWER = 1 << 5;

    /** Bits owned by the SystemUI process. */
    static final int SYSTEM_UI_BITS = SHADE | BOUNCER | VOLUME | VOLUME_TRACK;
    /** Bits owned by the launcher process. */
    static final int LAUNCHER_BITS = LAUNCHER | DRAWER;

    /** Set by a reporting process so "nothing hooked" differs from "never reported". */
    static final int REPORTED_SYSTEM_UI = 1 << 30;
    static final int REPORTED_LAUNCHER = 1 << 29;

    private static final String TAG = "ShadeAlpha";
    private static final Object LOCK = new Object();

    private static int bits = 0;
    private static int ownerMask = 0;
    private static int reportedFlag = 0;
    /** Last value this process wrote; -1 means it has not written yet. */
    private static int written = -1;

    private HookStatus() {}

    /** Declares the bits this process owns and its "reported" flag. */
    static void owner(int mask, int flag) {
        synchronized (LOCK) {
            ownerMask = mask;
            reportedFlag = flag;
        }
    }

    static void mark(int bit) {
        synchronized (LOCK) {
            bits |= bit;
        }
    }

    /** Writes the collected bits once per change; cheap enough to call from a draw path. */
    static void flush(Context c) {
        if (c == null) return;
        int mine;
        int owner;
        int flag;
        synchronized (LOCK) {
            mine = bits;
            owner = ownerMask;
            flag = reportedFlag;
            if (mine == written) return;
            written = mine;
        }
        try {
            int current = Settings.System.getInt(c.getContentResolver(), Keys.HOOKS, 0);
            int merged = (current & ~owner) | mine | flag;
            Settings.System.putInt(c.getContentResolver(), Keys.HOOKS, merged);
        } catch (Throwable t) {
            synchronized (LOCK) {
                written = -1;
            }
            XposedBridge.log(TAG + ": hook status write failed: " + t);
        }
    }

    /** True when a flush would write something new, so callers can skip fetching a Context. */
    static boolean pending() {
        synchronized (LOCK) {
            return bits != written;
        }
    }
}
