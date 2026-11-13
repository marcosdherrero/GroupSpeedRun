package net.berkle.groupspeedrun.client;

import net.berkle.groupspeedrun.parameter.GSRKeyBindingParameters;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

/**
 * GSR key bindings (client). Translation keys and default keys in {@link GSRKeyBindingParameters}.
 * Uses category "key.category.gsr" so they appear under "GSR" in Options → Controls → Key Binds.
 */
public final class GSRKeyBindings {

    public static final String NEW_WORLD_KEY = GSRKeyBindingParameters.NEW_WORLD_KEY;
    public static final String OPEN_OPTIONS_KEY = GSRKeyBindingParameters.OPEN_OPTIONS_KEY;
    public static final String OPEN_CONFIG_KEY = GSRKeyBindingParameters.OPEN_CONFIG_KEY;
    public static final String TOGGLE_HUD_KEY = GSRKeyBindingParameters.TOGGLE_HUD_KEY;
    public static final String PRESS_TO_SHOW_HUD_KEY = GSRKeyBindingParameters.PRESS_TO_SHOW_HUD_KEY;

    /** Category for Options → Controls so GSR keybinds appear in a "GSR" section. */
    private static final KeyMapping.Category GSR_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gsr", "category"));

    public static KeyMapping newGsrWorldKey;
    public static KeyMapping openGsrOptionsKey;
    /** Open GSR Config (default G+C: hold G, press C). */
    public static KeyMapping openGsrConfigKey;
    /** Toggle HUD on/off (default V). */
    public static KeyMapping toggleGsrHudKey;
    /** Hold to show HUD (default Tab). */
    public static KeyMapping pressToShowGsrHudKey;

    public static void register() {
        newGsrWorldKey = new KeyMapping(
                NEW_WORLD_KEY,
                InputConstants.Type.KEYBOARD,
                GSRKeyBindingParameters.DEFAULT_NEW_WORLD,
                GSR_CATEGORY
        );
        openGsrOptionsKey = new KeyMapping(
                OPEN_OPTIONS_KEY,
                InputConstants.Type.KEYBOARD,
                GSRKeyBindingParameters.DEFAULT_OPEN_OPTIONS,
                GSR_CATEGORY
        );
        openGsrConfigKey = new KeyMapping(
                OPEN_CONFIG_KEY,
                InputConstants.Type.KEYBOARD,
                GSRKeyBindingParameters.DEFAULT_OPEN_CONFIG,
                GSR_CATEGORY
        );
        toggleGsrHudKey = new KeyMapping(
                TOGGLE_HUD_KEY,
                InputConstants.Type.KEYBOARD,
                GSRKeyBindingParameters.DEFAULT_TOGGLE_HUD,
                GSR_CATEGORY
        );
        pressToShowGsrHudKey = new KeyMapping(
                PRESS_TO_SHOW_HUD_KEY,
                InputConstants.Type.KEYBOARD,
                GSRKeyBindingParameters.DEFAULT_PRESS_TO_SHOW_HUD,
                GSR_CATEGORY
        );
    }
}
