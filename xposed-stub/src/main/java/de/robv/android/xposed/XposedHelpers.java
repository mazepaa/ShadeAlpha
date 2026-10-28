package de.robv.android.xposed;

public class XposedHelpers {
    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {}
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object getAdditionalInstanceField(Object obj, String key) { return null; }
    public static Object setAdditionalInstanceField(Object obj, String key, Object value) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
}