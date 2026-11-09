package dev.shadealpha;

public class Keys {
    public static final String TOP = "shade_alpha_top_percent";
    public static final String BOTTOM = "shade_alpha_bottom_percent";
    public static final String RECENTS = "shade_alpha_recents_percent";
    public static final String APPDRAWER = "shade_alpha_appdrawer_percent";
    public static final String BOUNCER = "shade_alpha_bouncer_percent";
    public static final String VOLUME = "shade_alpha_volume_percent";
    public static final String VOLUME_EXPANDED = "shade_alpha_volume_expanded_percent";
    public static final String VOL_TRACK = "shade_alpha_vol_track_percent";
    public static final String VOL_FILL = "shade_alpha_vol_fill_percent";
    public static final String MODE = "shade_alpha_mode";

    public static final int MODE_LAYER = 0;
    public static final int MODE_VIEW_ALPHA = 1;
    public static final int MODE_SCRIM_ALPHA = 2;

    /** Подменяется хуком в процессе самого приложения (нужно включить ShadeAlpha в области модуля). */
    public static boolean isModuleActive() { return Boolean.parseBoolean(System.getProperty("shadealpha.never")); }
}