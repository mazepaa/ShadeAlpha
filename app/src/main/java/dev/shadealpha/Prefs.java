package dev.shadealpha;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * Кэшированное чтение значений из Settings.System внутри процесса SystemUI и лаунчера.
 * Значения пишет приложение ShadeAlpha (через root), хуки только читают.
 *
 * Один кэш на все хуки: шторка, экран блокировки, панель громкости и лаунчер читают из него,
 * поэтому при отрисовке не бывает похода в ContentResolver на каждый кадр.
 */
final class Prefs {

    private static final String TAG = "ShadeAlpha";
    private static final long TTL_MS = 300;
    private static final long LOG_THROTTLE_MS = 1500;
    private static final boolean DEBUG = false;
    private static volatile long lastLogAt = 0;

    private static volatile int top = 100;
    private static volatile int bottom = 100;
    private static volatile int recents = 100;
    private static volatile int drawer = 100;
    private static volatile int volume = 100;
    private static volatile int volTrack = 100;
    private static volatile int volFill = 100;
    private static volatile int bouncer = 100;
    private static volatile int mode = Keys.MODE_VIEW_ALPHA;
    private static volatile long lastRead = 0;
    private static Method currentApplication;

    private Prefs() {}

    static float top(Context c) {
        refresh(c);
        return top / 100f;
    }

    static float bottom(Context c) {
        refresh(c);
        return bottom / 100f;
    }

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

    static int mode(Context c) {
        refresh(c);
        return mode;
    }

    private static void refresh(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRead < TTL_MS) return;
        if (c == null) c = appContext();
        if (c == null) return;
        lastRead = now;

        int nt = read(c, Keys.TOP);
        int nb = read(c, Keys.BOTTOM);
        int nr = read(c, Keys.RECENTS);
        int nd = read(c, Keys.APPDRAWER);
        int nv = read(c, Keys.VOLUME);
        int nvt = read(c, Keys.VOL_TRACK);
        int nvf = read(c, Keys.VOL_FILL);
        int nbo = read(c, Keys.BOUNCER);
        int nm = read(c, Keys.MODE, Keys.MODE_VIEW_ALPHA, 2);

        if (nt != top || nb != bottom || nr != recents || nd != drawer || nv != volume
                || nvt != volTrack || nvf != volFill || nbo != bouncer || nm != mode) {
            logValues(nt, nb, nr, nd, nv, nvt, nvf, nbo, nm);
        }

        top = nt;
        bottom = nb;
        recents = nr;
        drawer = nd;
        volume = nv;
        volTrack = nvt;
        volFill = nvf;
        bouncer = nbo;
        mode = nm;
    }

    private static void logValues(int t, int b, int r, int d, int v, int vt, int vf, int bo, int m) {
        if (!DEBUG) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLogAt < LOG_THROTTLE_MS) return;
        lastLogAt = now;
        XposedBridge.log(TAG + ": values top=" + t + " bottom=" + b + " recents=" + r
                + " drawer=" + d + " volume=" + v + " track=" + vt + " fill=" + vf
                + " bouncer=" + bo + " mode=" + m);
    }

    private static int read(Context c, String key) {
        return read(c, key, 100, 100);
    }

    private static int read(Context c, String key, int def, int max) {
        try {
            int v = Settings.System.getInt(c.getContentResolver(), key, def);
            return Math.max(0, Math.min(max, v));
        } catch (Throwable t) {
            return def;
        }
    }

    static Context appContext() {
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
