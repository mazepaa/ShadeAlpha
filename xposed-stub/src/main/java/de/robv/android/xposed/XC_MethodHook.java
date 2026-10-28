package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args; // <--- ДОБАВЛЕНО
        private Object result;
        private Object extra;

        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public void setObjectExtra(String key, Object value) { this.extra = value; }
        public Object getObjectExtra(String key) { return extra; }
    }

    public static class Unhook {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}