package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

/**
 * The single switch for everything this module writes to the Vector log.
 *
 * The settings screen writes a {@code Settings.System} flag; {@link #log} and {@link #logAlways}
 * are the only places that call {@code XposedBridge.log}, so with the switch off the module
 * writes nothing at all. A cached boolean is refreshed at most once a second, which is much
 * cheaper than formatting a message that will be thrown away.
 *
 * While the flag has never been read (the very first frames after SystemUI starts) the module
 * stays silent as well: a missing answer must not turn into a burst of logs.
 */
final class Logs {

    private static final long TTL_MS = 1000;
    private static volatile boolean on = false;
    private static volatile long readAt = 0;

    private Logs() {}

    static boolean on() {
        long now = android.os.SystemClock.uptimeMillis();
        long last = readAt;
        if (last != 0 && now - last < TTL_MS) return on;
        readAt = now;
        Context c = Prefs.appContext();
        if (c == null) return on;
        try {
            on = Settings.System.getInt(c.getContentResolver(), Keys.DEBUG_LOGS, 0) == 1;
        } catch (Throwable ignored) {
            // keep the previous value
        }
        return on;
    }

    /** Writes only when the switch is on. */
    static void log(String message) {
        if (on()) de.robv.android.xposed.XposedBridge.log(message);
    }

    /**
     * Writes only when the switch is on; kept as a named alternative for call sites that used to
     * be unconditional, so the intent ("this one matters") stays readable in the source.
     */
    static void logAlways(String message) {
        log(message);
    }
}
