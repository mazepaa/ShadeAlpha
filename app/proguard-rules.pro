# Xposed loads this entry point by the exact class name in assets/xposed_init.
-keep class dev.shadealpha.ShadeAlpha { *; }

# SettingsActivity checks this method after the module hooks its own process.
-keepclassmembers class dev.shadealpha.Keys {
    public static boolean isModuleActive();
}

# Xposed API classes are supplied by the framework, not packaged in this APK.
-dontwarn de.robv.android.xposed.**
