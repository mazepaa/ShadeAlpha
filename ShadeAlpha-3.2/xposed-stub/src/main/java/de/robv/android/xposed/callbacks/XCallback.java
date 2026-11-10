package de.robv.android.xposed.callbacks;

public abstract class XCallback {
    public static class Param {
        public synchronized Object getObjectExtra(String key) {
            return null;
        }

        public synchronized void setObjectExtra(String key, Object o) {
        }
    }
}
