# Точку входа LSPosed находит по имени из assets/xposed_init, поэтому её нельзя переименовывать/удалять.
-keep class dev.shadealpha.ShadeAlpha { *; }
-keep class dev.shadealpha.ShadeAlpha$* { *; }

# Классы Xposed API подставляет LSPosed во время работы.
-dontwarn de.robv.android.xposed.**

# Имена не обфусцируем: читаемые логи и прозрачность кода для пользователей.
-dontobfuscate
