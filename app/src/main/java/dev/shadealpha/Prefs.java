package dev.shadealpha;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * Кэшированное чтение значений из Settings.System внутри процесса лаунчера.
 * Значения пишет приложение ShadeAlpha (через root), хуки только читают.
 */
final class Prefs {

    private static final long TTL_MS = 300;

    private static volatile int recents = 100;
    private static volatile int drawer = 100;
    private static volatile long lastRead = 0;
    private static Method currentApplication;

    private Prefs() {}

    /** Доля «оригинальной» непрозрачности для недавних: 1.0 = как в системе, 0 = полностью прозрачно. */
    static float recents(Context c) {
        refresh(c);
        return recents / 100f;
    }

    /** То же самое для списка приложений. */
    static float drawer(Context c) {
        refresh(c);
        return drawer / 100f;
    }

    private static void refresh(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead < TTL_MS) return;
        if (c == null) c = appContext();
        if (c == null) return;
        lastRead = now;
        recents = read(c, Keys.RECENTS);
        drawer = read(c, Keys.APPDRAWER);
    }

    private static int read(Context c, String key) {
        try {
            int v = Settings.System.getInt(c.getContentResolver(), key, 100);
            return Math.max(0, Math.min(100, v));
        } catch (Throwable t) {
            return 100;
        }
    }

    private static Context appContext() {
        try {
            if (currentApplication == null) {
                Class<?> at = Class.forName("android.app.ActivityThread");
                currentApplication = at.getMethod("currentApplication");
            }
            return (Context) currentApplication.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
