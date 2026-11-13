package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

import de.robv.android.xposed.XposedBridge;

/**
 * Records which hooks actually installed, in a {@code Settings.System} row the app can read.
 *
 * SystemUI writes it once per hook group on start (and again after a restart), the settings screen
 * shows the result, so a user does not have to dig through the LSPosed log to learn that, say, the
 * lock screen hook did not find its class on this ROM.
 */
final class HookStatus {

    static final int SHADE = 1;
    static final int BOUNCER = 1 << 1;
    static final int VOLUME = 1 << 2;
    static final int VOLUME_TRACK = 1 << 3;
    static final int LAUNCHER = 1 << 4;
    static final int DRAWER = 1 << 5;

    private static final String TAG = "ShadeAlpha";
    private static final Object LOCK = new Object();

    /** Mask already written by this process; SystemUI restarts reset it. */
    private static int written = 0;

    private HookStatus() {}

    /** Clears the row when SystemUI starts, so a hook that stopped installing cannot stay "ok". */
    static void reset() {
        Context c = Prefs.appContext();
        if (c == null) return;
        synchronized (LOCK) {
            written = 0;
            try {
                Settings.System.putInt(c.getContentResolver(), Keys.HOOKS, 0);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hook status reset failed: " + t);
            }
        }
    }

    static void mark(int bit, String name) {
        Context c = Prefs.appContext();
        if (c == null) return;
        synchronized (LOCK) {
            if ((written & bit) != 0) return;
            int mask = written | bit;
            written = mask;
            try {
                Settings.System.putInt(c.getContentResolver(), Keys.HOOKS, mask);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hook status write failed for " + name + ": " + t);
            }
        }
    }
}
