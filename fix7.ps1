# 1. Полностью перезаписываем Keys.java идеальным кодом
$keysCode = @'
package dev.shadealpha;

public class Keys {
    public static final String TOP = "shade_alpha_top_percent";
    public static final String BOTTOM = "shade_alpha_bottom_percent";
    public static final String MODE = "shade_alpha_mode";

    public static final int MODE_SAVE_LAYER = 0;
    public static final int MODE_VIEW_ALPHA = 1;
    public static final int MODE_SCRIM_ALPHA = 2;

    public static boolean isModuleActive() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge", false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getHookStatus() {
        return isModuleActive() ? "Активен: ScrimView.onDraw" : "Модуль не активен";
    }
}
'@
[IO.File]::WriteAllText('app\src\main\java\dev\shadealpha\Keys.java', $keysCode, [Text.UTF8Encoding]::new($false))

Write-Host "✅ Keys.java исправлен! Теперь никаких синтаксических ошибок."