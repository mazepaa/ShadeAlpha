package dev.shadealpha;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Boot-loop guard for the processes this module hooks (SystemUI and the launcher).
 *
 * <p>Every start of a guarded process is recorded in a small file in that process's own
 * device-protected data directory. The record is marked "stable" once the process has stayed
 * alive for {@link #STABLE_MS}. If a process starts again without the previous run ever becoming
 * stable, that run died early, and {@link #LIMIT} early deaths in a row trip the guard: from then
 * on the module installs no hooks in that package, so SystemUI or the launcher run exactly as
 * stock until the user re-enables the hooks from the app.
 *
 * <p>Design rules:
 * <ul>
 *   <li><b>Fail open.</b> Any problem inside the guard itself (no data directory, unreadable
 *       file, odd framework) means "hook as usual"; the guard must never be the reason a
 *       process cannot start.</li>
 *   <li><b>No Context needed.</b> The state lives in a plain file, because hooks are installed
 *       before the process has an Application object and long before Settings is usable.</li>
 *   <li><b>A new build re-arms.</b> The file stores the module's versionCode; installing another
 *       version of the module starts from a clean state, which is the natural way to ship a fix.</li>
 *   <li><b>Visible.</b> A trip is always written to the Vector log, and once a Context exists the
 *       state is published to {@link Keys#GUARD} so the app can show a banner.</li>
 * </ul>
 *
 * <p>Manual recovery without the app: delete {@code /data/user_de/0/<package>/shadealpha_guard}
 * as root (see {@link #rearmCommand()}), or clear the package's data.
 */
final class BootGuard {

    /** Bit in {@link Keys#GUARD}: the SystemUI guard has tripped. */
    static final int SYSTEM_UI = 1;
    /** Bit in {@link Keys#GUARD}: the launcher guard has tripped. */
    static final int LAUNCHER = 1 << 1;

    static final String FILE = "shadealpha_guard";

    private static final String TAG = "ShadeAlpha";
    /** Early deaths in a row that trip the guard. */
    private static final int LIMIT = 3;
    /** A process that lives this long is considered healthy. */
    private static final long STABLE_MS = 60_000;
    /** Early deaths further apart than this are unrelated and do not add up. */
    private static final long WINDOW_MS = 10 * 60_000;
    /** Wait before publishing to Settings: the Application and the settings provider must exist. */
    private static final long PUBLISH_DELAY_MS = 15_000;
    private static final int PUBLISH_RETRIES = 6;
    private static final long PUBLISH_RETRY_MS = 10_000;

    private static final String[] GUARDED_PACKAGES = {
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3"
    };

    private BootGuard() {}

    /**
     * Called once when a guarded package is loaded. Returns false when the hooks must not be
     * installed in this process.
     */
    static boolean allow(XC_LoadPackage.LoadPackageParam lpparam, int bit) {
        try {
            // Only the main process of the package: a helper process must not count as a start.
            if (lpparam.processName != null && !lpparam.processName.equals(lpparam.packageName)) {
                return true;
            }
            File file = stateFile(lpparam);
            if (file == null) return true;

            State s = State.read(file);
            long now = System.currentTimeMillis();
            if (s.version != BuildConfig.VERSION_CODE) {
                s = new State();
                s.version = BuildConfig.VERSION_CODE;
            }

            if (s.tripped) {
                XposedBridge.log(TAG + ": boot guard is tripped for " + lpparam.packageName
                        + ", hooks are not installed");
                publishLater(bit, true);
                return false;
            }

            boolean diedEarly = !s.stable && s.startedAt != 0 && now - s.startedAt < WINDOW_MS;
            s.crashes = diedEarly ? s.crashes + 1 : 0;
            if (s.crashes >= LIMIT) {
                s.tripped = true;
                s.write(file);
                XposedBridge.log(TAG + ": " + lpparam.packageName + " died early " + s.crashes
                        + " times in a row, boot guard tripped, hooks are not installed");
                publishLater(bit, true);
                return false;
            }

            s.stable = false;
            s.startedAt = now;
            s.write(file);
            markStableLater(file, bit);
            return true;
        } catch (Throwable t) {
            Logs.log(TAG + ": boot guard failed, hooking anyway: " + t);
            return true;
        }
    }

    /** Shell command (run as root) that clears every guard state file. */
    static String rearmCommand() {
        StringBuilder sb = new StringBuilder("rm -f");
        for (String pkg : GUARDED_PACKAGES) {
            String path = " /data/user_de/*/" + pkg + "/" + FILE;
            sb.append(path).append(path).append(".tmp");
        }
        return sb.toString();
    }

    private static File stateFile(XC_LoadPackage.LoadPackageParam lpparam) {
        String dir = null;
        try {
            Object info = XposedHelpers.getObjectField(lpparam, "appInfo");
            if (info instanceof ApplicationInfo) dir = ((ApplicationInfo) info).deviceProtectedDataDir;
        } catch (Throwable ignored) {
            // older frameworks: use the conventional path below
        }
        if (dir == null) dir = "/data/user_de/0/" + lpparam.packageName;
        File d = new File(dir);
        if (!d.isDirectory() || !d.canWrite()) return null;
        return new File(d, FILE);
    }

    /** After {@link #STABLE_MS} alive: clears the early-death counter and publishes "not tripped". */
    private static void markStableLater(final File file, final int bit) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(PUBLISH_DELAY_MS);
                publish(bit, false);
                Thread.sleep(STABLE_MS - PUBLISH_DELAY_MS);
                State s = State.read(file);
                s.stable = true;
                s.crashes = 0;
                s.write(file);
            } catch (Throwable ignored) {
                // the process is going away or the file is gone: nothing to do
            }
        }, "ShadeAlpha-guard");
        t.setDaemon(true);
        t.start();
    }

    private static void publishLater(final int bit, final boolean tripped) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(PUBLISH_DELAY_MS);
                publish(bit, tripped);
            } catch (Throwable ignored) {
                // best effort
            }
        }, "ShadeAlpha-guard-publish");
        t.setDaemon(true);
        t.start();
    }

    /** Merges this process's bit into {@link Keys#GUARD}; retries while no Context exists yet. */
    private static void publish(int bit, boolean tripped) throws InterruptedException {
        for (int i = 0; i < PUBLISH_RETRIES; i++) {
            Context c = Prefs.appContext();
            if (c != null) {
                try {
                    ContentResolver cr = c.getContentResolver();
                    int cur = Settings.System.getInt(cr, Keys.GUARD, 0);
                    int next = tripped ? (cur | bit) : (cur & ~bit);
                    if (next != cur) Settings.System.putInt(cr, Keys.GUARD, next);
                } catch (Throwable t) {
                    Logs.log(TAG + ": boot guard status write failed: " + t);
                }
                return;
            }
            Thread.sleep(PUBLISH_RETRY_MS);
        }
    }

    /** The persisted record. Plain java.util.Properties: no JSON or Android dependency. */
    private static final class State {
        int version;
        int crashes;
        /** True when the last recorded run reached {@link #STABLE_MS} (or nothing was recorded). */
        boolean stable = true;
        boolean tripped;
        long startedAt;

        static State read(File f) {
            State s = new State();
            if (!f.isFile()) return s;
            try (FileInputStream in = new FileInputStream(f)) {
                Properties p = new Properties();
                p.load(in);
                s.version = Integer.parseInt(p.getProperty("v", "0"));
                s.crashes = Integer.parseInt(p.getProperty("c", "0"));
                s.stable = !"0".equals(p.getProperty("s", "1"));
                s.tripped = "1".equals(p.getProperty("t", "0"));
                s.startedAt = Long.parseLong(p.getProperty("a", "0"));
            } catch (Throwable t) {
                // a damaged file must not trip or block anything: start from a clean record
                return new State();
            }
            return s;
        }

        void write(File f) {
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                Properties p = new Properties();
                p.setProperty("v", Integer.toString(version));
                p.setProperty("c", Integer.toString(crashes));
                p.setProperty("s", stable ? "1" : "0");
                p.setProperty("t", tripped ? "1" : "0");
                p.setProperty("a", Long.toString(startedAt));
                p.store(out, null);
                // The record has to survive the crash or reboot it is meant to detect.
                out.getFD().sync();
            } catch (Throwable t) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(f)) tmp.delete();
        }
    }
}
