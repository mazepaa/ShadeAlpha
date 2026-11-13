package dev.shadealpha;

import android.content.Context;
import android.provider.Settings;

/**
 * Answer to "log everything or stay quiet", shared by the hooks and toggled from the app.
 *
 * Verbose logs are not worth a separate debug build: the settings screen writes a
 * {@code Settings.System} flag, and the hooks check this cached boolean before formatting
 * anything. The cache is refreshed every second, which is cheap compared to building strings.
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
}
