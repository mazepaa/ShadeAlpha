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
    private static volatile int volume = 100;
    private static volatile int volTrack = 100;
    private static volatile int volFill = 100;
    private static volatile int bouncer = 100;
    private static volatile long lastRead = 0;
    private static Method currentApplication;

    private Prefs() {}

    static float recents(Context c) {
        refresh(c);
        return recents / 100f;
    }

    static float drawer(Context c) {
        refresh(c);
        return drawer / 100f;
    }

    static float volume(Context c) {
        refresh(c);
        return volume / 100f;
    }

    static float volumeTrack(Context c) {
        refresh(c);
        return volTrack / 100f;
    }

    static float volumeFill(Context c) {
        refresh(c);
        return volFill / 100f;
    }

    static float bouncer(Context c) {
        refresh(c);
        return bouncer / 100f;
    }

    private static void refresh(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead < TTL_MS) return;
        if (c == null) c = appContext();
        if (c == null) return;
        lastRead = now;
        recents = read(c, Keys.RECENTS);
        drawer = read(c, Keys.APPDRAWER);
        volume = read(c, Keys.VOLUME);
        volTrack = read(c, Keys.VOL_TRACK);
        volFill = read(c, Keys.VOL_FILL);
        bouncer = read(c, Keys.BOUNCER);
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
