package dev.shadealpha;

/** Ключи Settings.System, общие для приложения и хука. */
final class Keys {
    static final String TOP = "shade_alpha_top_percent";
    static final String BOTTOM = "shade_alpha_bottom_percent";
    /** Тестовый режим: 0 = слой, 1 = альфа view (лёгкий), 2 = альфа скрима (как в Iconify). */
    static final String MODE = "shade_alpha_mode";

    static final int MODE_LAYER = 0;
    static final int MODE_VIEW_ALPHA = 1;
    static final int MODE_SCRIM_ALPHA = 2;

    private Keys() {
    }

    public static boolean isModuleActive() { return false; }
    public static String getHookStatus() { return "Нет данных"; }
}