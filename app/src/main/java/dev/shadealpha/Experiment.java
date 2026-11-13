package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

/**
 * A/B switch for the render-budget comparison the developer asked for.
 *
 * "A/B" here means: run the same screen animation twice, once with the module's hooks in charge
 * ("B", the normal behaviour) and once with them skipped ("A", the system drawing as if the module
 * were not there), and compare the render numbers Android itself records. The switch is a
 * {@code Settings.System} flag so it can be flipped from the settings screen without a reboot;
 * the hooks re-read it at most once a second.
 */
final class Experiment {

    private static final long TTL_MS = 1000;
    private static volatile boolean bypass = false;
    private static volatile long readAt = 0;

    private Experiment() {}

    /** True while the A/B run wants the stock look, so the hooks leave the frame alone. */
    static boolean bypass() {
        long now = android.os.SystemClock.uptimeMillis();
        long last = readAt;
        if (last != 0 && now - last < TTL_MS) return bypass;
        readAt = now;
        Context c = Prefs.appContext();
        if (c == null) return bypass;
        try {
            bypass = Settings.System.getInt(c.getContentResolver(), Keys.EXPERIMENT, 0) == 1;
        } catch (Throwable ignored) {
            // keep the previous value
        }
        return bypass;
    }
}
