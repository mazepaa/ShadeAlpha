package dev.shadealpha;

public class Keys {
    public static final String TOP = "shade_alpha_top_percent";
    public static final String BOTTOM = "shade_alpha_bottom_percent";
    public static final String MODE = "shade_alpha_mode";

    // Возвращаем константы, которые нужны для интерфейса
    public static final int MODE_LAYER = 0;
    public static final int MODE_VIEW_ALPHA = 1;
    public static final int MODE_SCRIM_ALPHA = 2;

    public static boolean isModuleActive() {
        return false;
    }

    public static String getHookStatus() {
        return "Не активен";
    }
}