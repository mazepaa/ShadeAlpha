package de.robv.android.xposed;
import java.util.Set;

public class XposedHelpers {
    public static Set<XC_MethodHook.Unhook> findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object getAdditionalInstanceField(Object obj, String key) { return null; }
    public static Object setAdditionalInstanceField(Object obj, String key, Object value) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }
}