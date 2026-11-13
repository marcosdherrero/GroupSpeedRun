package net.berkle.groupspeedrun.parameter;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Parameters for GSR key bindings: translation keys and default key codes.
 * Used by {@link net.berkle.groupspeedrun.client.GSRKeyBindings}.
 */
public final class GSRKeyBindingParameters {

    private GSRKeyBindingParameters() {}

    /** Translation key for "New GSR World" keybind. */
    public static final String NEW_WORLD_KEY = "key.gsr.new_gsr_world";
    /** Translation key for "Open GSR Options" keybind. */
    public static final String OPEN_OPTIONS_KEY = "key.gsr.open_gsr_options";
    /** Translation key for "Open GSR Config" keybind (G+C). */
    public static final String OPEN_CONFIG_KEY = "key.gsr.open_gsr_config";
    /** Translation key for "Show GSR HUD (Toggle)" keybind. */
    public static final String TOGGLE_HUD_KEY = "key.gsr.toggle_gsr_hud";
    /** Translation key for "Show GSR HUD (Hold)" keybind. */
    public static final String PRESS_TO_SHOW_HUD_KEY = "key.gsr.press_to_show_gsr_hud";

    /** Default key: New GSR World. */
    public static final int DEFAULT_NEW_WORLD = InputConstants.KEY_N;
    /** Default key: Open GSR Options. */
    public static final int DEFAULT_OPEN_OPTIONS = InputConstants.KEY_G;
    /** Default key: Open GSR Config (used with G held for G+C). */
    public static final int DEFAULT_OPEN_CONFIG = InputConstants.KEY_C;
    /** Default key: Toggle HUD (V). */
    public static final int DEFAULT_TOGGLE_HUD = InputConstants.KEY_V;
    /** Default key: Hold to show HUD (Tab). */
    public static final int DEFAULT_PRESS_TO_SHOW_HUD = InputConstants.KEY_TAB;
}
