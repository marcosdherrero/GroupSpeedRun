package net.berkle.groupspeedrun.gui.preferences;

// Minecraft: screen, GUI, input
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

// Fabric: minecraft networking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// GSR: config, network, parameters, components
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.client.GSRKeyBindings;
import net.berkle.groupspeedrun.config.GSRBastionIconOption;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRSeedFilterSettings;
import net.berkle.groupspeedrun.config.GSRFortressIconOption;
import net.berkle.groupspeedrun.config.GSREndShowTicksOption;
import net.berkle.groupspeedrun.config.GSRHudLookMode;
import net.berkle.groupspeedrun.parameter.GSRHudParameters;
import net.berkle.groupspeedrun.config.GSRHudPaddingOption;
import net.berkle.groupspeedrun.config.GSRHudVisibilityMode;
import net.berkle.groupspeedrun.config.GSRIconScaleOption;
import net.berkle.groupspeedrun.config.GSRLocatorColorOption;
import net.berkle.groupspeedrun.config.GSRLocatorNonAdminMode;
import net.berkle.groupspeedrun.config.GSRMaxScaleDistOption;
import net.berkle.groupspeedrun.config.GSRRowHeightOption;
import net.berkle.groupspeedrun.config.GSRScaleOption;
import net.berkle.groupspeedrun.config.GSRSeparatorAlphaOption;
import net.berkle.groupspeedrun.config.GSRShipIconOption;
import net.berkle.groupspeedrun.config.GSRSplitGapOption;
import net.berkle.groupspeedrun.config.GSRSplitShowTicksOption;
import net.berkle.groupspeedrun.config.GSRStrongholdIconOption;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.berkle.groupspeedrun.network.GSRWorldConfigPayload;
import net.berkle.groupspeedrun.gui.GSRTickerState;
import net.berkle.groupspeedrun.gui.components.GSRMenuComponents;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdown;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdownBehavior;
import net.berkle.groupspeedrun.mixin.accessors.GSRClickableWidgetAccessor;
import net.berkle.groupspeedrun.mixin.accessors.GSRPressableWidgetAccessor;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRLocatorParameters;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;
import net.berkle.groupspeedrun.timer.hud.GSRTimerHudRenderer;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.berkle.groupspeedrun.util.GSRLocatorIconHelper;
import net.berkle.groupspeedrun.util.GSRItemStacks;
import net.berkle.groupspeedrun.util.GSRScrollbarHelper;

// Minecraft: GUI rendering, items
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Java collections
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Custom GSR Preferences screen using {@link GSRMultiSelectDropdown} for enum options.
 * Matches Run History and Locators dropdown style. Replaces Cloth Config for consistency.
 */
@SuppressWarnings("null")
public final class GSRPreferencesScreen extends Screen {

    private static final int BAR_HEIGHT = GSRRunHistoryParameters.SELECTOR_BAR_HEIGHT;
    private static final int CONTENT_TOP = GSRUiParameters.PREFERENCES_CONTENT_TOP;
    private static final int CONTENT_MARGIN = GSRUiParameters.PREFERENCES_CONTENT_MARGIN;
    private static final int ROW_HEIGHT = GSRUiParameters.PREFERENCES_ROW_HEIGHT;
    private static final int LABEL_AREA_HEIGHT = GSRUiParameters.PREFERENCES_LABEL_AREA_HEIGHT;
    private static final int COL_GAP = GSRUiParameters.PREFERENCES_COL_GAP;
    private static final int CATEGORY_GAP = GSRUiParameters.PREFERENCES_CATEGORY_GAP;
    private static final int CATEGORY_MARGIN = GSRUiParameters.PREFERENCES_CATEGORY_MARGIN;
    private static final int CATEGORY_HEADER = GSRUiParameters.PREFERENCES_CATEGORY_HEADER_HEIGHT;
    private static final int LIST_WIDTH = GSRUiParameters.PREFERENCES_DROPDOWN_LIST_WIDTH;
    private static final float LABEL_SCALE = GSRRunHistoryParameters.LEFT_COLUMN_LABEL_SCALE;

    /** Cached toggle icon for ON state. Built lazily — 26.2 ItemStacks need bound components. */
    private static ItemStack toggleIconOn() {
        return GSRItemStacks.of(Items.LANTERN);
    }

    /** Cached toggle icon for OFF state. */
    private static ItemStack toggleIconOff() {
        return GSRItemStacks.of(Items.IRON_CHAIN);
    }

    /** Seed-filter square button face. */
    private static ItemStack seedFilterIcon() {
        return GSRItemStacks.of(Items.MAP);
    }

    private static final int ID_HUD_SCALE = 2;
    private static final int ID_HUD_LOOK = 1;
    private static final int ID_VISIBILITY = 4;
    private static final int ID_TIMER_COLOR_RUNNING = 26;
    private static final int ID_TIMER_COLOR_DERANKED = 27;
    private static final int ID_TIMER_COLOR_FREEZE = 28;
    private static final int ID_TIMER_COLOR_PAUSED = 29;
    private static final int ID_TIMER_COLOR_FAIL = 30;
    private static final int ID_TIMER_COLOR_VICTORY = 31;
    private static final int ID_MAX_SCALE_DIST = 8;
    private static final int ID_MIN_ICON_SCALE = 9;
    private static final int ID_FORTRESS_ICON = 11;
    private static final int ID_FORTRESS_COLOR = 12;
    private static final int ID_BASTION_ICON = 13;
    private static final int ID_BASTION_COLOR = 14;
    private static final int ID_STRONGHOLD_ICON = 15;
    private static final int ID_STRONGHOLD_COLOR = 16;
    private static final int ID_SHIP_ICON = 17;
    private static final int ID_SHIP_COLOR = 18;
    private static final int ID_HUD_PADDING = 19;
    private static final int ID_ROW_HEIGHT = 20;
    private static final int ID_SPLIT_GAP = 21;
    private static final int ID_SEPARATOR_ALPHA = 22;
    private static final int ID_SPLIT_SHOW_TICKS = 23;
    private static final int ID_END_SHOW_TICKS = 24;
    private static final int ID_LOCATOR_NON_ADMIN = 25;

    private final Screen parent;
    private final GSRPreferencesScreenModel model = new GSRPreferencesScreenModel();
    private final GSRTickerState tickerState = new GSRTickerState();

    private final List<GSRPreferencesDropdownEntry> dropdowns = new ArrayList<>();

    /** True when user is dragging the content list scrollbar. */
    private boolean contentScrollbarDragging = false;

    /** Footer buttons; disabled when dropdown overlay is open. */
    private Button backButton;
    private Button keybindsButton;

    public GSRPreferencesScreen(Screen parent) {
        this(parent, 0);
    }

    /**
     * @param parent              Parent screen (Back returns here).
     * @param initialContentScroll Scroll offset to restore when re-opening after config sync.
     */
    public GSRPreferencesScreen(Screen parent, int initialContentScroll) {
        super(GSRButtonParameters.literal(GSRButtonParameters.SCREEN_PREFERENCES));
        this.parent = parent;
        this.model.contentScroll = initialContentScroll;
        gsr$initDropdowns();
    }

    /** Returns the parent screen for re-opening after config sync. */
    public Screen getParent() {
        return parent;
    }

    /** Returns current content scroll offset. Used to preserve scroll when screen is recreated after config sync. */
    public int getContentScroll() {
        return model.contentScroll;
    }

    /** Plays the vanilla button click sound for custom-handled clicks (dropdowns, toggles). Uses same sound as Minecraft menu buttons. */
    private void gsr$playClickSound() {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && backButton != null) {
            ((GSRClickableWidgetAccessor) backButton).gsr$playDownSound(minecraft.getSoundManager());
        }
    }

    private void gsr$initDropdowns() {
        GSRConfigPlayer pc = GSRClient.PLAYER_CONFIG;
        GSRConfigWorld wc = GSRClient.clientWorldConfig;
        int fortressDef = GSRLocatorParameters.DEFAULT_FORTRESS_COLOR;
        int bastionDef = GSRLocatorParameters.DEFAULT_BASTION_COLOR;
        int strongholdDef = GSRLocatorParameters.DEFAULT_STRONGHOLD_COLOR;
        int shipDef = GSRLocatorParameters.DEFAULT_SHIP_COLOR;

        dropdowns.add(new GSRPreferencesDropdownEntry(ID_HUD_SCALE, "HUD Scale:", "HUD Scale",
                () -> java.util.Arrays.stream(GSRScaleOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRScaleOption.values()).indexOf(GSRScaleOption.from(pc.timerScale)),
                idx -> {
                    float v = GSRConfigPlayer.clampOverallScale(GSRScaleOption.values()[idx].getValue());
                    pc.timerScale = v;
                    pc.locateScale = v;
                    gsr$syncPlayerConfig();
                },
                2, () -> pc.timerScale == GSRHudParameters.DEFAULT_SCALE, null, null, null, null,
                "Scales both Timer and Locator HUDs. Larger values make both HUDs bigger on screen."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_HUD_LOOK, "HUD Look:", "HUD Look – Full or Condensed",
                () -> List.of(GSRHudLookMode.FULL.getDisplayName().getString(), GSRHudLookMode.CONDENSED.getDisplayName().getString()),
                () -> GSRHudLookMode.from(pc.hudMode).ordinal(),
                idx -> { pc.hudMode = GSRHudLookMode.values()[idx].getValue(); gsr$syncPlayerConfig(); },
                0, () -> pc.hudMode == GSRHudParameters.MODE_FULL, null, null, null, null,
                "Full: always show timer and splits. Condensed: timer always visible; splits only during event window (start, stop, split)."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_VISIBILITY, "Visibility:", "Visibility – Toggle or Hold",
                () -> List.of(gsr$visibilityLabel(GSRHudVisibilityMode.TOGGLE), gsr$visibilityLabel(GSRHudVisibilityMode.PRESSED)),
                () -> GSRHudVisibilityMode.from(pc.hudVisibility).ordinal(),
                idx -> { pc.hudVisibility = GSRHudVisibilityMode.values()[idx].getValue(); gsr$syncPlayerConfig(); gsr$applyVisibilityChange(); },
                1, () -> pc.hudVisibility == GSRConfigPlayer.VISIBILITY_PRESSED, null, null, null, null,
                "Toggle (V): press to show/hide HUD; stays visible until toggled again. Hold (Tab): hold key to show, release to hide with fade."));
        int runDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_RUNNING_ARGB;
        int derankDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_DERANKED_ARGB;
        int freezeDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_FREEZE_ARGB;
        int pauseDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_PAUSED_ARGB;
        int failDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_FAIL_ARGB;
        int victoryDef = net.berkle.groupspeedrun.parameter.GSRTimerConfig.COLOR_VICTORY_ARGB;
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_RUNNING, "Running Color:", "Running Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(runDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorRunning, runDef)),
                idx -> { pc.timerColorRunning = GSRLocatorColorOption.values()[idx].getValue(runDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorRunning == runDef, () -> "#" + java.lang.String.format("%06X", runDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(runDef) : null,
                "Color for running timer (ranked)."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_DERANKED, "Deranked Color:", "Deranked Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(derankDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorDeranked, derankDef)),
                idx -> { pc.timerColorDeranked = GSRLocatorColorOption.values()[idx].getValue(derankDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorDeranked == derankDef, () -> "#" + java.lang.String.format("%06X", derankDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(derankDef) : null,
                "Color for deranked run (locators or admin commands used)."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_FREEZE, "Freeze Color:", "Freeze Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(freezeDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorFreeze, freezeDef)),
                idx -> { pc.timerColorFreeze = GSRLocatorColorOption.values()[idx].getValue(freezeDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorFreeze == freezeDef, () -> "#" + java.lang.String.format("%06X", freezeDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(freezeDef) : null,
                "Color when timer frozen by pause menu or server stop."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_PAUSED, "Paused Color:", "Paused Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(pauseDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorPaused, pauseDef)),
                idx -> { pc.timerColorPaused = GSRLocatorColorOption.values()[idx].getValue(pauseDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorPaused == pauseDef, () -> "#" + java.lang.String.format("%06X", pauseDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(pauseDef) : null,
                "Color when timer manually paused by admin."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_FAIL, "Fail Color:", "Fail Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(failDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorFail, failDef)),
                idx -> { pc.timerColorFail = GSRLocatorColorOption.values()[idx].getValue(failDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorFail == failDef, () -> "#" + java.lang.String.format("%06X", failDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(failDef) : null,
                "Color when run fails (death)."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_TIMER_COLOR_VICTORY, "Victory Color:", "Victory Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(victoryDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.timerColorVictory, victoryDef)),
                idx -> { pc.timerColorVictory = GSRLocatorColorOption.values()[idx].getValue(victoryDef); gsr$syncPlayerConfig(); },
                0, () -> pc.timerColorVictory == victoryDef, () -> "#" + java.lang.String.format("%06X", victoryDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(victoryDef) : null,
                "Color when dragon is killed (victory)."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_MAX_SCALE_DIST, "Minimum Scale Distance:", "Minimum Scale Distance",
                () -> java.util.Arrays.stream(GSRMaxScaleDistOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRMaxScaleDistOption.values()).indexOf(GSRMaxScaleDistOption.from(pc.maxScaleDistance)),
                idx -> { pc.maxScaleDistance = Math.max(GSRConfigPlayer.MIN_MAX_SCALE_DIST, Math.min(GSRConfigPlayer.MAX_MAX_SCALE_DIST, GSRMaxScaleDistOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                1, () -> pc.maxScaleDistance == GSRLocatorParameters.DEFAULT_MAX_SCALE_DIST, null, null, null, null,
                "Distance at which structure icons reach full size. Closer structures scale up from min to max; farther stay at min scale."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_MIN_ICON_SCALE, "Min Icon Scale:", "Min Icon Scale",
                () -> java.util.Arrays.stream(GSRIconScaleOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRIconScaleOption.values()).indexOf(GSRIconScaleOption.from(pc.minIconScale)),
                idx -> { pc.minIconScale = Math.max(GSRConfigPlayer.MIN_ICON_SCALE, Math.min(GSRConfigPlayer.MAX_ICON_SCALE, GSRIconScaleOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                1, () -> pc.minIconScale == GSRLocatorParameters.DEFAULT_MIN_ICON_SCALE, null, null, null, null,
                "Minimum size of structure icons when far away. Icons grow toward full size as you get closer."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_FORTRESS_ICON, "Fortress Icon:", "Fortress Icon",
                () -> java.util.Arrays.stream(GSRFortressIconOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRFortressIconOption.values()).indexOf(GSRFortressIconOption.from(pc.fortressItem)),
                idx -> { pc.fortressItem = GSRFortressIconOption.values()[idx].getRegistryId(); gsr$syncPlayerConfig(); },
                0, () -> GSRLocatorParameters.DEFAULT_FORTRESS_ITEM.equals(pc.fortressItem),
                () -> gsr$itemDisplayName(GSRLocatorParameters.DEFAULT_FORTRESS_ITEM, Items.BLAZE_ROD),
                (m, idx) -> gsr$iconForIndex(GSRFortressIconOption.values(), idx, Items.BLAZE_ROD, o -> o.getRegistryId()), null, null,
                "Item icon shown on the locator bar for fortress. Appears in Nether when fortress is active."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_FORTRESS_COLOR, "Fortress Color:", "Fortress Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(fortressDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.fortressColor, fortressDef)),
                idx -> { pc.fortressColor = GSRLocatorColorOption.values()[idx].getValue(fortressDef); gsr$syncPlayerConfig(); },
                0, () -> pc.fortressColor == fortressDef,
                () -> "#" + java.lang.String.format("%06X", fortressDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(fortressDef) : null,
                "Theme color for fortress icon when looking toward it. Fades to gray when looking away."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_BASTION_ICON, "Bastion Icon:", "Bastion Icon",
                () -> java.util.Arrays.stream(GSRBastionIconOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRBastionIconOption.values()).indexOf(GSRBastionIconOption.from(pc.bastionItem)),
                idx -> { pc.bastionItem = GSRBastionIconOption.values()[idx].getRegistryId(); gsr$syncPlayerConfig(); },
                0, () -> GSRLocatorParameters.DEFAULT_BASTION_ITEM.equals(pc.bastionItem),
                () -> gsr$itemDisplayName(GSRLocatorParameters.DEFAULT_BASTION_ITEM, Items.PIGLIN_HEAD),
                (m, idx) -> gsr$iconForIndex(GSRBastionIconOption.values(), idx, Items.PIGLIN_HEAD, o -> o.getRegistryId()), null, null,
                "Item icon shown on the locator bar for bastion. Appears in Nether when bastion is active."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_BASTION_COLOR, "Bastion Color:", "Bastion Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(bastionDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.bastionColor, bastionDef)),
                idx -> { pc.bastionColor = GSRLocatorColorOption.values()[idx].getValue(bastionDef); gsr$syncPlayerConfig(); },
                0, () -> pc.bastionColor == bastionDef,
                () -> "#" + java.lang.String.format("%06X", bastionDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(bastionDef) : null,
                "Theme color for bastion icon when looking toward it. Fades to gray when looking away."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_STRONGHOLD_ICON, "Stronghold Icon:", "Stronghold Icon",
                () -> java.util.Arrays.stream(GSRStrongholdIconOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRStrongholdIconOption.values()).indexOf(GSRStrongholdIconOption.from(pc.strongholdItem)),
                idx -> { pc.strongholdItem = GSRStrongholdIconOption.values()[idx].getRegistryId(); gsr$syncPlayerConfig(); },
                0, () -> GSRLocatorParameters.DEFAULT_STRONGHOLD_ITEM.equals(pc.strongholdItem),
                () -> gsr$itemDisplayName(GSRLocatorParameters.DEFAULT_STRONGHOLD_ITEM, Items.ENDER_EYE),
                (m, idx) -> gsr$iconForIndex(GSRStrongholdIconOption.values(), idx, Items.ENDER_EYE, o -> o.getRegistryId()), null, null,
                "Item icon shown on the locator bar for stronghold. Appears in Overworld when stronghold is active."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_STRONGHOLD_COLOR, "Stronghold Color:", "Stronghold Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(strongholdDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.strongholdColor, strongholdDef)),
                idx -> { pc.strongholdColor = GSRLocatorColorOption.values()[idx].getValue(strongholdDef); gsr$syncPlayerConfig(); },
                0, () -> pc.strongholdColor == strongholdDef,
                () -> "#" + java.lang.String.format("%06X", strongholdDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(strongholdDef) : null,
                "Theme color for stronghold icon when looking toward it. Fades to gray when looking away."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_SHIP_ICON, "Wings (Ship) Icon:", "Wings (Ship) Icon",
                () -> java.util.Arrays.stream(GSRShipIconOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRShipIconOption.values()).indexOf(GSRShipIconOption.from(pc.shipItem)),
                idx -> { pc.shipItem = GSRShipIconOption.values()[idx].getRegistryId(); gsr$syncPlayerConfig(); },
                0, () -> GSRLocatorParameters.DEFAULT_SHIP_ITEM.equals(pc.shipItem),
                () -> gsr$itemDisplayName(GSRLocatorParameters.DEFAULT_SHIP_ITEM, Items.ELYTRA),
                (m, idx) -> gsr$iconForIndex(GSRShipIconOption.values(), idx, Items.ELYTRA, o -> o.getRegistryId()), null, null,
                "Item icon shown on the locator bar for end ship. Appears in End when ship is active."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_SHIP_COLOR, "Wings (Ship) Color:", "Wings (Ship) Color",
                () -> java.util.Arrays.stream(GSRLocatorColorOption.values()).map(e -> e.getDisplayName(shipDef).getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorColorOption.values()).indexOf(GSRLocatorColorOption.from(pc.shipColor, shipDef)),
                idx -> { pc.shipColor = GSRLocatorColorOption.values()[idx].getValue(shipDef); gsr$syncPlayerConfig(); },
                0, () -> pc.shipColor == shipDef,
                () -> "#" + java.lang.String.format("%06X", shipDef & 0x00FFFFFF),
                (m, idx) -> gsr$colorIconForIndex(idx), (m, idx) -> null,
                (m, idx) -> idx >= 0 && idx < GSRLocatorColorOption.values().length ? GSRLocatorColorOption.values()[idx].getValue(shipDef) : null,
                "Theme color for end ship icon when looking toward it. Fades to gray when looking away."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_HUD_PADDING, "HUD Padding:", "HUD Padding",
                () -> java.util.Arrays.stream(GSRHudPaddingOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRHudPaddingOption.values()).indexOf(GSRHudPaddingOption.from(pc.hudPadding)),
                idx -> { pc.hudPadding = Math.max(GSRConfigPlayer.MIN_HUD_PADDING, Math.min(GSRConfigPlayer.MAX_HUD_PADDING, GSRHudPaddingOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                2, () -> pc.hudPadding == GSRHudParameters.DEFAULT_HUD_PADDING, null, null, null, null,
                "Padding between the HUD and screen edges. Larger values move the HUD inward."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_ROW_HEIGHT, "Row Height:", "Row Height",
                () -> java.util.Arrays.stream(GSRRowHeightOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRRowHeightOption.values()).indexOf(GSRRowHeightOption.from(pc.hudRowHeight)),
                idx -> { pc.hudRowHeight = Math.max(GSRConfigPlayer.MIN_HUD_ROW_HEIGHT, Math.min(GSRConfigPlayer.MAX_HUD_ROW_HEIGHT, GSRRowHeightOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                2, () -> pc.hudRowHeight == GSRHudParameters.DEFAULT_HUD_ROW_HEIGHT, null, null, null, null,
                "Vertical spacing between split rows in the timer HUD. Larger values spread splits further apart."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_SPLIT_GAP, "Split Gap:", "Split Gap",
                () -> java.util.Arrays.stream(GSRSplitGapOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRSplitGapOption.values()).indexOf(GSRSplitGapOption.from(pc.hudSplitGap)),
                idx -> { pc.hudSplitGap = Math.max(GSRConfigPlayer.MIN_HUD_SPLIT_GAP, Math.min(GSRConfigPlayer.MAX_HUD_SPLIT_GAP, GSRSplitGapOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                1, () -> pc.hudSplitGap == GSRHudParameters.DEFAULT_HUD_SPLIT_GAP, null, null, null, null,
                "Gap between timer and split list. Larger values add more space between them."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_SEPARATOR_ALPHA, "Separator Alpha:", "Separator Alpha",
                () -> java.util.Arrays.stream(GSRSeparatorAlphaOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRSeparatorAlphaOption.values()).indexOf(GSRSeparatorAlphaOption.from(pc.hudSeparatorAlpha)),
                idx -> { pc.hudSeparatorAlpha = Math.max(GSRConfigPlayer.MIN_SEPARATOR_ALPHA, Math.min(GSRConfigPlayer.MAX_SEPARATOR_ALPHA, GSRSeparatorAlphaOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                2, () -> pc.hudSeparatorAlpha == GSRHudParameters.DEFAULT_SEPARATOR_ALPHA, null, null, null, null,
                "Opacity of the separator line between splits. Lower values make the line more transparent."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_SPLIT_SHOW_TICKS, "Split Show (s):", "Split Show (s)",
                () -> java.util.Arrays.stream(GSRSplitShowTicksOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRSplitShowTicksOption.values()).indexOf(GSRSplitShowTicksOption.from(pc.splitShowTicks)),
                idx -> { pc.splitShowTicks = Math.max(GSRConfigPlayer.MIN_SPLIT_SHOW_TICKS, Math.min(GSRConfigPlayer.MAX_SPLIT_SHOW_TICKS, GSRSplitShowTicksOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                2, () -> pc.splitShowTicks == GSRHudParameters.DEFAULT_SPLIT_SHOW_TICKS, null, null, null, null,
                "How long the split list stays visible after a split (seconds). Longer values keep splits on screen longer."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_END_SHOW_TICKS, "End Show (s):", "End Show (s)",
                () -> java.util.Arrays.stream(GSREndShowTicksOption.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSREndShowTicksOption.values()).indexOf(GSREndShowTicksOption.from(pc.endShowTicks)),
                idx -> { pc.endShowTicks = Math.max(GSRConfigPlayer.MIN_END_SHOW_TICKS, Math.min(GSRConfigPlayer.MAX_END_SHOW_TICKS, GSREndShowTicksOption.values()[idx].getValue())); gsr$syncPlayerConfig(); },
                3, () -> pc.endShowTicks == GSRHudParameters.DEFAULT_END_SHOW_TICKS, null, null, null, null,
                "How long the HUD stays visible after victory or fail (seconds). Longer values keep the final time on screen longer."));
        dropdowns.add(new GSRPreferencesDropdownEntry(ID_LOCATOR_NON_ADMIN, "Locator Non-Admin Mode:", "Locator Non-Admin Mode",
                () -> java.util.Arrays.stream(GSRLocatorNonAdminMode.values()).map(e -> e.getDisplayName().getString()).toList(),
                () -> java.util.Arrays.asList(GSRLocatorNonAdminMode.values()).indexOf(GSRLocatorNonAdminMode.from(wc != null ? wc.locatorNonAdminMode : 0)),
                idx -> { if (wc != null) { wc.locatorNonAdminMode = GSRLocatorNonAdminMode.values()[idx].getValue(); gsr$syncWorldConfig(); } },
                2, () -> wc != null && wc.locatorNonAdminMode == GSRLocatorNonAdminMode.POST_SPLIT_30MIN.getValue(), null,
                (m, idx) -> gsr$locatorNonAdminIcon(idx), null, null,
                "When non-admins can use locator bar: Never = disabled. Always = no time gate. 30 min post split = fortress/bastion 30 min after Nether; stronghold 30 min after overworld return; wings 30 min after dragon. Host only."));
    }

    /** Default-option label: hover name when components are bound, otherwise a readable registry id. */
    private String gsr$itemDisplayName(String registryId, net.minecraft.world.item.Item fallbackItem) {
        ItemStack stack = registryId != null
                ? GSRLocatorIconHelper.getItemStack(registryId, fallbackItem)
                : GSRItemStacks.of(fallbackItem);
        return GSRItemStacks.displayName(stack, registryId, "Default");
    }

    /** Returns ItemStack for icon dropdown at index. Uses registry ID for enum value; never Air. */
    private static <T> ItemStack gsr$iconForIndex(T[] options, int idx, net.minecraft.world.item.Item defaultItem,
            java.util.function.Function<T, String> registryIdGetter) {
        if (idx >= 0 && idx < options.length) {
            return GSRLocatorIconHelper.getItemStack(registryIdGetter.apply(options[idx]), defaultItem);
        }
        ItemStack fallback = GSRItemStacks.of(defaultItem);
        return GSRItemStacks.isUsable(fallback) ? fallback : ItemStack.EMPTY;
    }

    /** Returns ItemStack for Locator Non-Admin dropdown: Never=barrier, Always=compass, Post 30 Mins=clock. */
    private static ItemStack gsr$locatorNonAdminIcon(int idx) {
        return switch (idx) {
            case 0 -> GSRItemStacks.of(Items.BARRIER);
            case 1 -> GSRItemStacks.of(Items.COMPASS);
            default -> GSRItemStacks.of(Items.CLOCK);
        };
    }

    /** Returns ItemStack for color dropdown at index. DEFAULT uses glow ink; others use corresponding dye. */
    private static ItemStack gsr$colorIconForIndex(int idx) {
        if (idx >= 0 && idx < GSRLocatorColorOption.values().length) {
            return GSRItemStacks.of(GSRLocatorColorOption.values()[idx].getDisplayItem());
        }
        return GSRItemStacks.of(Items.GLOW_INK_SAC);
    }

    private static String gsr$visibilityLabel(GSRHudVisibilityMode m) {
        if (m == GSRHudVisibilityMode.PRESSED) {
            String key = GSRKeyBindings.pressToShowGsrHudKey != null ? GSRKeyBindings.pressToShowGsrHudKey.getTranslatedKeyMessage().getString() : "Tab";
            return "Hold (" + key + ")";
        }
        String key = GSRKeyBindings.toggleGsrHudKey != null ? GSRKeyBindings.toggleGsrHudKey.getTranslatedKeyMessage().getString() : "V";
        return "Toggle (" + key + ")";
    }

    void gsr$syncPlayerConfig() {
        GSRConfigPlayer config = GSRClient.PLAYER_CONFIG;
        config.clampAll();
        CompoundTag nbt = new CompoundTag();
        config.writeNbt(nbt);
        GSRClient.PLAYER_CONFIG.readNbt(nbt);
        if (minecraft != null && minecraft.player != null) {
            ClientPlayNetworking.send(new GSRConfigPayload(nbt));
        }
    }

    private void gsr$syncWorldConfig() {
        if (minecraft != null && minecraft.player != null) {
            ClientPlayNetworking.send(new GSRWorldConfigPayload(GSRWorldConfigPayload.fromConfig()));
        }
    }

    void gsr$applyVisibilityChange() {
        if (GSRClient.PLAYER_CONFIG.hudVisibility == GSRConfigPlayer.VISIBILITY_TOGGLE) {
            GSRClient.setHudToggledVisible(true);
        }
        GSRClient.setPreviousHudVisibility(GSRClient.PLAYER_CONFIG.hudVisibility);
    }

    @Override
    protected void init() {
        super.init();
        var footer = GSRMenuComponents.footerLayout(width, height);
        backButton = Button.builder(GSRButtonParameters.literal(GSRButtonParameters.FOOTER_BACK), b -> goBack())
                .bounds(footer.leftX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()).build();
        keybindsButton = Button.builder(GSRButtonParameters.literal(GSRButtonParameters.PREFERENCES_KEYBINDS), b -> {
            if (minecraft != null && minecraft.options != null) minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options));
        }).bounds(footer.rightX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()).build();
        addRenderableWidget(backButton);
        addRenderableWidget(keybindsButton);
    }

    private void goBack() {
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        if (parent != null) minecraft.gui.setScreen(parent);
        else minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean dropdownOpen = model.openDropdownId != GSRPreferencesScreenModel.DROPDOWN_NONE;
        if (backButton != null) ((GSRClickableWidgetAccessor) backButton).gsr$setActive(!dropdownOpen);
        if (keybindsButton != null) ((GSRClickableWidgetAccessor) keybindsButton).gsr$setActive(!dropdownOpen);
        context.fill(0, 0, width, height, GSRUiParameters.SCREEN_BG_DARK);
        // Timer drawn behind all content (blurred/faded) as in GSR Controls screen. Hides splits when HUD Look is Condensed.
        GSRConfigWorld wc = GSRClient.clientWorldConfig;
        GSRConfigPlayer pc = GSRClient.PLAYER_CONFIG;
        if (wc != null && pc != null && minecraft != null) {
            boolean showSplits;
            if (model.openDropdownId == ID_HUD_LOOK && model.pendingIndex >= 0 && model.pendingIndex < GSRHudLookMode.values().length) {
                showSplits = GSRHudLookMode.values()[model.pendingIndex] == GSRHudLookMode.FULL;
            } else {
                showSplits = GSRHudLookMode.from(pc.hudMode) == GSRHudLookMode.FULL;
            }
            int[] size = GSRTimerHudRenderer.getTimerBoxScaledSize(font, wc, pc, showSplits);
            int scaledH = size[1];
            int anchorX = pc.timerHudOnRight ? (width - GSRTimerHudRenderer.EDGE_MARGIN) : GSRTimerHudRenderer.EDGE_MARGIN;
            int anchorY = (int) ((height / 2f) - (scaledH / 2f) - (height * GSRTimerHudRenderer.VERTICAL_OFFSET_FACTOR));
            GSRTimerHudRenderer.drawTimerBox(context, font, pc.timerHudOnRight, anchorX, anchorY, wc, pc, true, 1f, showSplits, GSRUiParameters.CONTROLS_TIMER_ALPHA);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, getTitle(), width / 2, GSRUiParameters.TITLE_Y, GSRUiParameters.TITLE_COLOR);

        int contentLeft = CONTENT_MARGIN;
        int contentWidth = width - 2 * CONTENT_MARGIN;
        int contentTop = CONTENT_TOP;
        int contentBottom = GSRMenuComponents.singleButtonFooterLayout(width, height).footerY() - GSRUiParameters.FOOTER_CONTENT_GAP;

        if (model.openDropdownId != GSRPreferencesScreenModel.DROPDOWN_NONE) {
            context.fill(0, 0, width, height, GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_DIM);
            gsr$renderDropdownOverlay(context, contentLeft, contentTop, contentWidth, contentBottom, mouseX, mouseY);
        } else {
            gsr$renderContentList(context, contentLeft, contentTop, contentWidth, contentBottom, mouseX, mouseY);
        }

        tickerState.clearIfNotDrawn();
        gsr$updateHoverState(contentLeft, contentTop, contentWidth, contentBottom, mouseX, mouseY);

        if (model.openDropdownId == GSRPreferencesScreenModel.DROPDOWN_NONE) {
            Component tooltip = gsr$getHoveredTooltip(contentLeft, contentTop, contentWidth, contentBottom, mouseX, mouseY);
            if (tooltip != null) {
                context.setComponentTooltipForNextFrame(font, List.of(tooltip), mouseX, mouseY);
            }
        }
    }

    /** Returns tooltip for the hovered button, or null if none. Only shows when hovering over the actual selection button, not the label. */
    private Component gsr$getHoveredTooltip(int contentLeft, int contentTop, int contentWidth, int contentBottom, int mouseX, int mouseY) {
        int listHeight = contentBottom - contentTop;
        int totalHeight = gsr$contentHeight();
        int maxScroll = Math.max(0, totalHeight - listHeight);
        int contentWidthForContent = maxScroll > 0 ? contentWidth - GSRScrollbarHelper.getScrollbarWidth() : contentWidth;
        int colWidth = (contentWidthForContent - COL_GAP) / 2;
        int leftCol = contentLeft;
        int rightCol = contentLeft + colWidth + COL_GAP;
        int centeredCol = contentLeft + colWidth / 2 + COL_GAP / 2;
        int scroll = Math.min(model.contentScroll, maxScroll);
        int[] hit = gsr$getButtonAt(contentLeft, contentWidthForContent, colWidth, leftCol, rightCol, centeredCol, contentTop, contentBottom, scroll, mouseX, mouseY);
        if (hit == null) return null;
        int rowType = hit[0];
        int rowId = hit[1];
        if (rowType == 1) {
            GSRPreferencesDropdownEntry entry = gsr$getEntry(rowId);
            return entry != null && entry.tooltip != null ? Component.literal(entry.tooltip) : null;
        }
        if (rowType == 2) {
            return switch (rowId) {
                case 0 -> Component.literal("Timer on right (→) or left (←) side of screen.");
                case 1 -> Component.literal("When on, the structure compass bar is at the top of the screen; when off, at the bottom.");
                case 2 -> Component.literal("When ON (host only): using locators invalidates the run for ranking. When OFF: locator use does not invalidate.");
                case 3 -> Component.literal("When ON (host only): allows the New World key before run ends. When OFF: New World key only works after victory or fail.");
                case 4 -> Component.literal("When ON (green, default): random-seed new worlds retry until Overworld+Nether pass. When OFF (red): vanilla random. Typed seeds are never filtered.");
                default -> null;
            };
        }
        if (rowType == 4 && rowId == 0) {
            return Component.literal(GSRButtonParameters.PREFERENCES_RESET_CONFIRM_MESSAGE);
        }
        return null;
    }

    /** Updates lastHoveredElement/lastHoveredTimeMs for click stability. Uses button bar hit test so it matches the visible control. */
    private void gsr$updateHoverState(int contentLeft, int contentTop, int contentWidth, int contentBottom, int mouseX, int mouseY) {
        int listHeight = contentBottom - contentTop;
        int totalHeight = gsr$contentHeight();
        int maxScroll = Math.max(0, totalHeight - listHeight);
        int contentWidthForContent = maxScroll > 0 ? contentWidth - GSRScrollbarHelper.getScrollbarWidth() : contentWidth;
        int colWidth = (contentWidthForContent - COL_GAP) / 2;
        int leftCol = contentLeft;
        int rightCol = contentLeft + colWidth + COL_GAP;
        int centeredCol = contentLeft + colWidth / 2 + COL_GAP / 2;
        int scroll = Math.min(model.contentScroll, maxScroll);
        int[] hit = gsr$getButtonAt(contentLeft, contentWidthForContent, colWidth, leftCol, rightCol, centeredCol, contentTop, contentBottom, scroll, mouseX, mouseY);
        long now = System.currentTimeMillis();
        if (hit != null) {
            int[] key = new int[] { hit[0], hit[1] };
            if (model.lastHoveredElement == null || model.lastHoveredElement[0] != key[0] || model.lastHoveredElement[1] != key[1]) {
                model.lastHoveredElement = key;
                model.lastHoveredTimeMs = now;
            }
        } else {
            model.lastHoveredElement = null;
        }
    }

    private void gsr$renderContentList(GuiGraphicsExtractor context, int contentLeft, int contentTop, int contentWidth, int contentBottom, int mouseX, int mouseY) {
        int listHeight = contentBottom - contentTop;
        int totalHeight = gsr$contentHeight();
        int maxScroll = Math.max(0, totalHeight - listHeight);
        int scroll = Math.min(model.contentScroll, maxScroll);

        int contentWidthForContent = maxScroll > 0 ? contentWidth - GSRScrollbarHelper.getScrollbarWidth() : contentWidth;
        int colWidth = (contentWidthForContent - COL_GAP) / 2;
        int leftCol = contentLeft;
        int rightCol = contentLeft + colWidth + COL_GAP;
        int centeredCol = contentLeft + colWidth / 2 + COL_GAP / 2;

        context.enableScissor(contentLeft, contentTop, contentLeft + contentWidthForContent, contentBottom);
        int y = contentTop - scroll;

        y = gsr$drawCategory(context, "Mod Settings", y, contentLeft, contentWidthForContent, false);
        gsr$drawDropdownRow(context, ID_HUD_SCALE, "HUD Scale", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_VISIBILITY, "Visibility", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawToggleRow(context, "Anti-Cheat (Invalidate Run)", GSRClient.clientWorldConfig != null && GSRClient.clientWorldConfig.antiCheatEnabled, y, leftCol, colWidth, mouseX, mouseY, () -> {
            if (GSRClient.clientWorldConfig != null) {
                GSRClient.clientWorldConfig.antiCheatEnabled = !GSRClient.clientWorldConfig.antiCheatEnabled;
                gsr$syncWorldConfig();
            }
        }, toggleIconOn(), toggleIconOff(), GSRUiParameters.PREFERENCES_TOGGLE_ANTICHEAT_ON, GSRUiParameters.PREFERENCES_TOGGLE_ANTICHEAT_OFF, "ON (default)", "OFF");
        gsr$drawToggleRow(context, "Auto Start", GSRClient.clientWorldConfig != null && GSRClient.clientWorldConfig.autoStartEnabled, y, rightCol, colWidth, mouseX, mouseY, () -> {
            if (GSRClient.clientWorldConfig != null) {
                GSRClient.clientWorldConfig.autoStartEnabled = !GSRClient.clientWorldConfig.autoStartEnabled;
                gsr$syncWorldConfig();
            }
        }, toggleIconOn(), toggleIconOff(), GSRUiParameters.PREFERENCES_TOGGLE_NEUTRAL_ON, GSRUiParameters.PREFERENCES_TOGGLE_NEUTRAL_OFF, "ON (default)", "OFF");
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_LOCATOR_NON_ADMIN, "Locator Non-Admin Mode", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawToggleRow(context, "New World Before Run Ends", GSRClient.PLAYER_CONFIG.allowNewWorldBeforeRunEnd, y, rightCol, colWidth, mouseX, mouseY, () -> {
            GSRClient.PLAYER_CONFIG.allowNewWorldBeforeRunEnd = !GSRClient.PLAYER_CONFIG.allowNewWorldBeforeRunEnd;
            gsr$syncPlayerConfig();
        }, toggleIconOn(), toggleIconOff(), GSRUiParameters.PREFERENCES_TOGGLE_NEWWORLD_ON, GSRUiParameters.PREFERENCES_TOGGLE_NEWWORLD_OFF, "ON", "OFF (default)");
        y += ROW_HEIGHT;
        gsr$drawToggleRow(context, "Seed Filter", gsr$isSeedFilterEnabled(), y, leftCol, colWidth, mouseX, mouseY,
                this::gsr$toggleSeedFilter, seedFilterIcon(), seedFilterIcon(),
                GSRUiParameters.PREFERENCES_SEED_FILTER_ON, GSRUiParameters.PREFERENCES_SEED_FILTER_OFF, "ON (default)", "OFF", true);
        y += ROW_HEIGHT;
        gsr$drawButtonRow(context, GSRButtonParameters.PREFERENCES_RESET_MOD_SETTINGS, y, centeredCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        y += CATEGORY_MARGIN;
        y = gsr$drawCategoryDivider(context, y, contentLeft, contentWidthForContent);
        y = gsr$drawCategory(context, "Timer HUD", y, contentLeft, contentWidthForContent, false);
        gsr$drawToggleRow(context, "Timer Display Side:", GSRClient.PLAYER_CONFIG.timerHudOnRight, y, leftCol, colWidth, mouseX, mouseY, () -> {
            GSRClient.PLAYER_CONFIG.timerHudOnRight = !GSRClient.PLAYER_CONFIG.timerHudOnRight;
            gsr$syncPlayerConfig();
        }, GSRItemStacks.of(Items.CLOCK), GSRItemStacks.of(Items.CLOCK), -1, -1, "\u2192", "\u2190");
        gsr$drawDropdownRow(context, ID_HUD_LOOK, "HUD Look", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_RUNNING, "Running Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_DERANKED, "Deranked Color", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_FREEZE, "Freeze Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_PAUSED, "Paused Color", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_FAIL, "Fail Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_TIMER_COLOR_VICTORY, "Victory Color", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        y += CATEGORY_MARGIN;
        y = gsr$drawCategoryDivider(context, y, contentLeft, contentWidthForContent);
        y = gsr$drawCategory(context, "Locator HUD", y, contentLeft, contentWidthForContent, true);
        gsr$drawToggleRow(context, "Compass Display Height:", GSRClient.PLAYER_CONFIG.locateHudOnTop, y, leftCol, colWidth, mouseX, mouseY, () -> {
            GSRClient.PLAYER_CONFIG.locateHudOnTop = !GSRClient.PLAYER_CONFIG.locateHudOnTop;
            gsr$syncPlayerConfig();
        }, GSRItemStacks.of(Items.COMPASS), GSRItemStacks.of(Items.COMPASS), -1, -1, "\u2191", "\u2193");
        gsr$drawDropdownRow(context, ID_MAX_SCALE_DIST, "Minimum Scale Distance", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_MIN_ICON_SCALE, "Min Icon Scale", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_FORTRESS_ICON, "Fortress Icon", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_FORTRESS_COLOR, "Fortress Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_BASTION_ICON, "Bastion Icon", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_BASTION_COLOR, "Bastion Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_STRONGHOLD_ICON, "Stronghold Icon", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_STRONGHOLD_COLOR, "Stronghold Color", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_SHIP_ICON, "Wings (Ship) Icon", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_SHIP_COLOR, "Wings (Ship) Color", y, centeredCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        y += CATEGORY_MARGIN;
        y = gsr$drawCategoryDivider(context, y, contentLeft, contentWidthForContent);
        y = gsr$drawCategory(context, "Styling", y, contentLeft, contentWidthForContent, false);
        gsr$drawDropdownRow(context, ID_HUD_PADDING, "HUD Padding", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_ROW_HEIGHT, "Row Height", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_SPLIT_GAP, "Split Gap", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_SEPARATOR_ALPHA, "Separator Alpha", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;
        gsr$drawDropdownRow(context, ID_SPLIT_SHOW_TICKS, "Split Show (s)", y, leftCol, colWidth, mouseX, mouseY);
        gsr$drawDropdownRow(context, ID_END_SHOW_TICKS, "End Show (s)", y, rightCol, colWidth, mouseX, mouseY);
        y += ROW_HEIGHT;

        context.disableScissor();

        if (maxScroll > 0) {
            int trackX = contentLeft + contentWidthForContent;
            GSRScrollbarHelper.drawScrollbar(context, trackX, contentTop, listHeight, scroll, maxScroll,
                    GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT);
        }
    }

    private int gsr$contentHeight() {
        int rows = 5 + 4 + 6 + 3;
        int categories = 4;
        int categoryMargins = (categories - 1) * CATEGORY_MARGIN;
        int dividers = (categories - 1) * (GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_HEIGHT + GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_GAP);
        return categories * (CATEGORY_HEADER + CATEGORY_GAP) + rows * ROW_HEIGHT + categoryMargins + dividers + CATEGORY_GAP;
    }

    /** Draws a horizontal divider between categories. Returns y after the divider and gap. */
    private int gsr$drawCategoryDivider(GuiGraphicsExtractor context, int y, int left, int width) {
        int h = GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_HEIGHT;
        context.fill(left, y, left + width, y + h, GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_COLOR);
        return y + h + GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_GAP;
    }

    /** Draws category title. When showLocatorPreview, draws mini locator bar with icons next to "Locator HUD". */
    private int gsr$drawCategory(GuiGraphicsExtractor context, String title, int y, int left, int width, boolean showLocatorPreview) {
        int labelX = left + GSRRunHistoryParameters.LIST_TEXT_INSET;
        context.text(font, Component.literal(title), labelX, y, GSRUiParameters.TITLE_COLOR, true);
        if (showLocatorPreview) {
            int labelW = font.width(title);
            int previewX = labelX + labelW + GSRUiParameters.PREFERENCES_LOCATOR_PREVIEW_LABEL_GAP;
            GSRPreferencesPreviewRenderer.drawLocatorPreview(context, model, previewX, y, CATEGORY_HEADER);
        }
        return y + CATEGORY_HEADER + CATEGORY_GAP;
    }

    private int gsr$drawDropdownRow(GuiGraphicsExtractor context, int id, String label, int y, int colLeft, int colWidth, int mouseX, int mouseY) {
        GSRPreferencesDropdownEntry entry = gsr$getEntry(id);
        if (entry == null) return y + ROW_HEIGHT;
        int sectionTop = y;
        int barTop = y + LABEL_AREA_HEIGHT;
        boolean hovered = mouseX >= colLeft && mouseX < colLeft + colWidth && mouseY >= sectionTop && mouseY < sectionTop + ROW_HEIGHT;
        GSRMultiSelectDropdown<GSRPreferencesScreenModel> dd = entry.getDropdown();
        dd.renderTrigger(model, context, font, tickerState, colLeft, sectionTop, barTop, colWidth, BAR_HEIGHT, LABEL_SCALE, model.openDropdownId == id, hovered);
        return y + ROW_HEIGHT;
    }

    private int gsr$drawToggleRow(GuiGraphicsExtractor context, String label, boolean value, int y, int colLeft, int colWidth,
                                   int mouseX, int mouseY, Runnable onToggle,
                                   ItemStack iconOn, ItemStack iconOff, int colorOn, int colorOff,
                                   String displayOn, String displayOff) {
        return gsr$drawToggleRow(context, label, value, y, colLeft, colWidth, mouseX, mouseY, onToggle,
                iconOn, iconOff, colorOn, colorOff, displayOn, displayOff, false);
    }

    private int gsr$drawToggleRow(GuiGraphicsExtractor context, String label, boolean value, int y, int colLeft, int colWidth,
                                   int mouseX, int mouseY, Runnable onToggle,
                                   ItemStack iconOn, ItemStack iconOff, int colorOn, int colorOff,
                                   String displayOn, String displayOff, boolean overlayStatusOnIcon) {
        int sectionTop = y;
        int barTop = y + LABEL_AREA_HEIGHT;
        int labelX = colLeft + GSRRunHistoryParameters.LIST_TEXT_INSET;
        int labelY = sectionTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(labelX, labelY);
        matrices.scale(LABEL_SCALE, LABEL_SCALE);
        context.text(font, Component.literal(label), 0, 0, GSRRunHistoryParameters.LABEL_COLOR, true);
        matrices.popMatrix();
        var textures = GSRPressableWidgetAccessor.gsr$getTextures();
        boolean hovered = mouseX >= colLeft && mouseX < colLeft + colWidth && mouseY >= sectionTop && mouseY < sectionTop + ROW_HEIGHT;
        var tex = textures.get(true, hovered);
        int barDrawWidth = colWidth - GSRRunHistoryParameters.CONTAINER_INSET * 2;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, tex, colLeft + GSRRunHistoryParameters.CONTAINER_INSET, barTop, barDrawWidth, BAR_HEIGHT);
        ItemStack icon = value ? iconOn : iconOff;
        int textColor = (value ? colorOn : colorOff) != -1 ? (value ? colorOn : colorOff) : GSRRunHistoryParameters.TEXT_COLOR;
        int textLeft = colLeft + GSRRunHistoryParameters.LIST_TEXT_INSET;
        if (icon != null && !icon.isEmpty()) {
            int iconSize = GSRUiParameters.PREFERENCES_TOGGLE_ICON_SIZE;
            int iconMargin = GSRUiParameters.PREFERENCES_TOGGLE_ICON_MARGIN;
            int iconX = colLeft + GSRRunHistoryParameters.CONTAINER_INSET + iconMargin;
            int iconY = barTop + (BAR_HEIGHT - iconSize) / 2;
            gsr$drawToggleIcon(context, icon, iconX, iconY, iconSize, iconMargin);
            if (overlayStatusOnIcon) {
                gsr$drawStatusMark(context, iconX, iconY, iconSize, value);
            }
            textLeft = iconX + iconSize + iconMargin + GSRRunHistoryParameters.DROPDOWN_ITEM_ICON_TEXT_GAP;
        }
        String display = value ? displayOn : displayOff;
        int textY = barTop + (BAR_HEIGHT - font.lineHeight) / 2;
        context.text(font, Component.literal(display), textLeft, textY, textColor, true);
        return y + ROW_HEIGHT;
    }

    private boolean gsr$isSeedFilterEnabled() {
        if (minecraft != null && minecraft.level != null && GSRClient.clientWorldConfig != null) {
            return GSRClient.clientWorldConfig.seedFilterEnabled;
        }
        return GSRSeedFilterSettings.isEnabled();
    }

    private void gsr$toggleSeedFilter() {
        boolean next = !gsr$isSeedFilterEnabled();
        GSRSeedFilterSettings.setEnabled(next);
        if (GSRClient.clientWorldConfig != null) {
            GSRClient.clientWorldConfig.seedFilterEnabled = next;
            gsr$syncWorldConfig();
        }
    }

    /** Draws a button in one column (e.g. Reset All to Default in right column). Uses same bar style as toggle/dropdown rows. */
    private int gsr$drawButtonRow(GuiGraphicsExtractor context, String label, int y, int colLeft, int colWidth, int mouseX, int mouseY) {
        int sectionTop = y;
        int barTop = y + LABEL_AREA_HEIGHT;
        int barDrawWidth = colWidth - GSRRunHistoryParameters.CONTAINER_INSET * 2;
        boolean hovered = mouseX >= colLeft && mouseX < colLeft + colWidth && mouseY >= sectionTop && mouseY < sectionTop + ROW_HEIGHT;
        var textures = GSRPressableWidgetAccessor.gsr$getTextures();
        var tex = textures.get(true, hovered);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, tex, colLeft + GSRRunHistoryParameters.CONTAINER_INSET, barTop, barDrawWidth, BAR_HEIGHT);
        int textX = colLeft + GSRRunHistoryParameters.CONTAINER_INSET + GSRRunHistoryParameters.LIST_TEXT_INSET;
        int textY = barTop + (BAR_HEIGHT - font.lineHeight) / 2;
        context.text(font, Component.literal(label), textX, textY, GSRRunHistoryParameters.TEXT_COLOR, true);
        return y + ROW_HEIGHT;
    }

    /** Green check or red X drawn over a toggle item icon. */
    private void gsr$drawStatusMark(GuiGraphicsExtractor context, int x, int y, int size, boolean on) {
        int pad = 2;
        int x0 = x + pad;
        int y0 = y + pad;
        int x1 = x + size - pad - 1;
        int y1 = y + size - pad - 1;
        int color = on ? GSRUiParameters.PREFERENCES_SEED_FILTER_ON : GSRUiParameters.PREFERENCES_SEED_FILTER_OFF;
        int outline = 0xFF000000;
        if (on) {
            int midX = x0 + (x1 - x0) / 3;
            int midY = y1;
            int leftY = (y0 + y1) / 2;
            gsr$drawThickLine(context, x0, leftY, midX, midY, outline, 3);
            gsr$drawThickLine(context, midX, midY, x1, y0, outline, 3);
            gsr$drawThickLine(context, x0, leftY, midX, midY, color, 2);
            gsr$drawThickLine(context, midX, midY, x1, y0, color, 2);
        } else {
            gsr$drawThickLine(context, x0, y0, x1, y1, outline, 3);
            gsr$drawThickLine(context, x1, y0, x0, y1, outline, 3);
            gsr$drawThickLine(context, x0, y0, x1, y1, color, 2);
            gsr$drawThickLine(context, x1, y0, x0, y1, color, 2);
        }
    }

    private void gsr$drawThickLine(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        int t = Math.max(1, thickness);
        while (true) {
            context.fill(x, y, x + t, y + t, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** Draws a scaled item icon for toggle buttons. */
    private void gsr$drawToggleIcon(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int size, int margin) {
        int inner = size - 2 * margin;
        if (inner <= 0 || !GSRItemStacks.isUsable(stack)) return;
        float scale = inner / 16f;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x + margin, y + margin);
        matrices.scale(scale, scale);
        context.item(stack, 0, 0);
        matrices.popMatrix();
    }

    private void gsr$renderDropdownOverlay(GuiGraphicsExtractor context, int listLeft, int listTop, int listWidth, int listBottom, int mouseX, int mouseY) {
        GSRPreferencesDropdownEntry entry = gsr$getEntry(model.openDropdownId);
        if (entry == null) return;
        GSRMultiSelectDropdown<GSRPreferencesScreenModel> dd = entry.getDropdown();
        int listW = Math.min(LIST_WIDTH, listWidth - 8);
        int listCenter = listLeft + listWidth / 2;
        int overlayTop = listTop + (listBottom - listTop) / 2 - GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HALF_HEIGHT;
        overlayTop = Math.max(GSRUiParameters.PREFERENCES_DROPDOWN_MIN_TOP, overlayTop);
        int overlayLeft = listCenter - listW / 2;
        int overlayBottom = Math.min(listBottom, overlayTop + GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HEIGHT);
        int selectionBottom = overlayBottom - GSRRunHistoryParameters.MAKE_SELECTION_CONTAINER_HEIGHT;
        int delimiterTop = selectionBottom + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT;
        int confirmTop = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BUTTON_GAP;

        int m = GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
        context.fill(overlayLeft - m, overlayTop - m, overlayLeft + listW + m, overlayBottom + m, GSRUiParameters.CONTENT_BOX_BG);
        int listBottomExtended = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
        dd.renderOverlay(model, context, font, tickerState, overlayLeft, overlayTop, listBottomExtended, listW, model.dropdownScroll, mouseX, mouseY);
        context.fill(overlayLeft, delimiterTop, overlayLeft + listW,
                delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT,
                GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_COLOR);

        boolean confirmHover = mouseX >= overlayLeft && mouseX < overlayLeft + listW && mouseY >= confirmTop && mouseY < confirmTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT;
        var textures = GSRPressableWidgetAccessor.gsr$getTextures();
        var confirmTexture = textures.get(true, confirmHover);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, confirmTexture, overlayLeft, confirmTop, listW, GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT);
        int confirmTextY = confirmTop + (GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT - font.lineHeight) / 2;
        context.centeredText(font, GSRMultiSelectDropdown.CONFIRM_BUTTON_TEXT, overlayLeft + listW / 2, confirmTextY, GSRRunHistoryParameters.TEXT_COLOR);
    }

    private GSRPreferencesDropdownEntry gsr$getEntry(int id) {
        for (GSRPreferencesDropdownEntry e : dropdowns) {
            if (e.id == id) return e;
        }
        return null;
    }

    /**
     * Returns [rowType, rowId] for button bar at (mx,my). Only matches when mouse is over the actual selection
     * button (bar area), not the label. Used for tooltips to avoid overlap with buttons below.
     * centeredCol is the left edge of centered buttons (odd final item in a two-column group).
     */
    private int[] gsr$getButtonAt(int contentLeft, int contentWidth, int colWidth, int leftCol, int rightCol, int centeredCol, int top, int bottom, int scroll, int mx, int my) {
        if (my < top || my >= bottom) return null;
        if (mx < contentLeft || mx >= contentLeft + contentWidth) return null;
        boolean inLeftCol = mx >= leftCol && mx < leftCol + colWidth;
        boolean inRightCol = mx >= rightCol && mx < rightCol + colWidth;
        boolean inCenteredCol = mx >= centeredCol && mx < centeredCol + colWidth;
        if (!inLeftCol && !inRightCol && !inCenteredCol) return null;

        int barTopOffset = LABEL_AREA_HEIGHT;
        int barBottomOffset = barTopOffset + BAR_HEIGHT;

        int y = top - scroll;

        y = gsr$skipToFirstCategory(y);
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 1, ID_HUD_SCALE };
            if (inRightCol) return new int[] { 1, ID_VISIBILITY };
        }
        y += ROW_HEIGHT;
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 2, 2 };
            if (inRightCol) return new int[] { 2, 5 };
        }
        y += ROW_HEIGHT;
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 1, ID_LOCATOR_NON_ADMIN };
            if (inRightCol) return new int[] { 2, 3 };
        }
        y += ROW_HEIGHT;
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 2, 4 };
        }
        y += ROW_HEIGHT;
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inCenteredCol) return new int[] { 4, 0 };
        }
        y += ROW_HEIGHT;

        y = gsr$skipCategory(y);
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 2, 0 };
            if (inRightCol) return new int[] { 1, ID_HUD_LOOK };
        }
        y += ROW_HEIGHT;
        int[][] timerColorPairs = { { ID_TIMER_COLOR_RUNNING, ID_TIMER_COLOR_DERANKED }, { ID_TIMER_COLOR_FREEZE, ID_TIMER_COLOR_PAUSED }, { ID_TIMER_COLOR_FAIL, ID_TIMER_COLOR_VICTORY } };
        for (int[] pair : timerColorPairs) {
            if (my >= y + barTopOffset && my < y + barBottomOffset) {
                if (inLeftCol) return new int[] { 1, pair[0] };
                if (inRightCol) return new int[] { 1, pair[1] };
            }
            y += ROW_HEIGHT;
        }

        y = gsr$skipCategory(y);
        if (my >= y + barTopOffset && my < y + barBottomOffset) {
            if (inLeftCol) return new int[] { 2, 1 };
            if (inRightCol) return new int[] { 1, ID_MAX_SCALE_DIST };
        }
        y += ROW_HEIGHT;
        int[][] locatorPairs = { { ID_MIN_ICON_SCALE, ID_FORTRESS_ICON }, { ID_FORTRESS_COLOR, ID_BASTION_ICON },
                { ID_BASTION_COLOR, ID_STRONGHOLD_ICON }, { ID_STRONGHOLD_COLOR, ID_SHIP_ICON }, { ID_SHIP_COLOR, -1 } };
        for (int[] pair : locatorPairs) {
            if (my >= y + barTopOffset && my < y + barBottomOffset) {
                if (pair[1] >= 0) {
                    if (inLeftCol && pair[0] >= 0) return new int[] { 1, pair[0] };
                    if (inRightCol) return new int[] { 1, pair[1] };
                } else if (inCenteredCol && pair[0] >= 0) {
                    return new int[] { 1, pair[0] };
                }
            }
            y += ROW_HEIGHT;
        }

        y = gsr$skipCategory(y);
        int[][] stylePairs = { { ID_HUD_PADDING, ID_ROW_HEIGHT }, { ID_SPLIT_GAP, ID_SEPARATOR_ALPHA }, { ID_SPLIT_SHOW_TICKS, ID_END_SHOW_TICKS } };
        for (int[] pair : stylePairs) {
            if (my >= y + barTopOffset && my < y + barBottomOffset) {
                if (inLeftCol) return new int[] { 1, pair[0] };
                if (inRightCol) return new int[] { 1, pair[1] };
            }
            y += ROW_HEIGHT;
        }

        return null;
    }

    /** Skips to first category header. Matches drawCategory (no margin/divider before first category). */
    private int gsr$skipToFirstCategory(int y) {
        return y + CATEGORY_HEADER + CATEGORY_GAP;
    }

    /** Skips margin, divider, and next category header. Matches draw flow between categories. */
    private int gsr$skipCategory(int y) {
        return y + CATEGORY_MARGIN + GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_HEIGHT + GSRUiParameters.PREFERENCES_CATEGORY_DIVIDER_GAP + CATEGORY_HEADER + CATEGORY_GAP;
    }

    private void gsr$handleToggleClick(int toggleId) {
        switch (toggleId) {
            case 0 -> { GSRClient.PLAYER_CONFIG.timerHudOnRight = !GSRClient.PLAYER_CONFIG.timerHudOnRight; gsr$syncPlayerConfig(); }
            case 1 -> { GSRClient.PLAYER_CONFIG.locateHudOnTop = !GSRClient.PLAYER_CONFIG.locateHudOnTop; gsr$syncPlayerConfig(); }
            case 2 -> { if (GSRClient.clientWorldConfig != null) { GSRClient.clientWorldConfig.antiCheatEnabled = !GSRClient.clientWorldConfig.antiCheatEnabled; gsr$syncWorldConfig(); } }
            case 3 -> { GSRClient.PLAYER_CONFIG.allowNewWorldBeforeRunEnd = !GSRClient.PLAYER_CONFIG.allowNewWorldBeforeRunEnd; gsr$syncPlayerConfig(); }
            case 4 -> gsr$toggleSeedFilter();
            case 5 -> { if (GSRClient.clientWorldConfig != null) { GSRClient.clientWorldConfig.autoStartEnabled = !GSRClient.clientWorldConfig.autoStartEnabled; gsr$syncWorldConfig(); } }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean captured) {
        int mx = (int) click.x();
        int my = (int) click.y();
        long now = System.currentTimeMillis();

        int contentLeft = CONTENT_MARGIN;
        int contentTop = CONTENT_TOP;
        int contentBottom = GSRMenuComponents.singleButtonFooterLayout(width, height).footerY() - GSRUiParameters.FOOTER_CONTENT_GAP;
        int contentWidth = width - 2 * CONTENT_MARGIN;
        int listW = Math.min(LIST_WIDTH, contentWidth - 8);
        int listCenter = contentLeft + contentWidth / 2;
        int overlayLeft = listCenter - listW / 2;

        // When dropdown open, consume all clicks first so overlay selections take precedence over background buttons
        if (model.openDropdownId != GSRPreferencesScreenModel.DROPDOWN_NONE) {
            GSRPreferencesDropdownEntry entry = gsr$getEntry(model.openDropdownId);
            int overlayTop = Math.max(GSRUiParameters.PREFERENCES_DROPDOWN_MIN_TOP, contentTop + (contentBottom - contentTop) / 2 - GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HALF_HEIGHT);
            int overlayBottom = Math.min(contentBottom, overlayTop + GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HEIGHT);
            int selectionBottom = overlayBottom - GSRRunHistoryParameters.MAKE_SELECTION_CONTAINER_HEIGHT;
            int delimiterTop = selectionBottom + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT;
            int confirmTop = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT
                    + GSRRunHistoryParameters.SELECTION_DELIMITER_BUTTON_GAP;
            int listBottomForGeom = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
            int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
            if (entry != null && mx >= overlayLeft && mx < overlayLeft + listW + sbWidth && my >= overlayTop && my < overlayBottom + 4) {
                if (my >= confirmTop && my < confirmTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT) {
                    gsr$playClickSound();
                    entry.apply(model.pendingIndex);
                    model.openDropdownId = GSRPreferencesScreenModel.DROPDOWN_NONE;
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                var geom = GSRMultiSelectDropdown.computeGeometry(font, entry.getDropdown().getHeader(), overlayLeft, overlayTop, listBottomForGeom, listW, entry.getDropdown().getItemCount(model));
                int itemWidth = listW;
                int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, entry.getDropdown().getItemCount(model), model.dropdownScroll, overlayLeft, itemWidth, mx, my);
                if (itemIdx >= 0) {
                    gsr$playClickSound();
                    model.pendingIndex = itemIdx;
                    return true;
                }
            }
            model.openDropdownId = GSRPreferencesScreenModel.DROPDOWN_NONE;
            model.lastClickHandledTimeMs = now;
            return true;
        }

        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(click, captured);

        // Footer widgets first (26.2 captured = double-click, not "already handled")
        int footerY = GSRMenuComponents.footerLayout(width, height).footerY();
        if (my >= footerY - 4 && super.mouseClicked(click, captured)) {
            return true;
        }

        int totalHeight = gsr$contentHeight();
        int listHeight = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalHeight - listHeight);
        int contentWidthForContent = maxScroll > 0 ? contentWidth - GSRScrollbarHelper.getScrollbarWidth() : contentWidth;
        int colWidth = (contentWidthForContent - COL_GAP) / 2;
        int leftCol = contentLeft;
        int rightCol = contentLeft + colWidth + COL_GAP;
        int centeredCol = contentLeft + colWidth / 2 + COL_GAP / 2;
        int scroll = Math.min(model.contentScroll, maxScroll);

        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        if (maxScroll > 0 && GSRScrollbarHelper.isInScrollbarHitArea(mx, contentLeft + contentWidthForContent, sbWidth)
                && my >= contentTop && my < contentBottom) {
            int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT,
                    (int) (listHeight * (listHeight / (double) (listHeight + maxScroll))));
            model.contentScroll = GSRMultiSelectDropdown.scrollFromMouseY(my, contentTop, contentBottom, thumbHeight, maxScroll);
            contentScrollbarDragging = true;
            return true;
        }

        int[] hit = gsr$getButtonAt(contentLeft, contentWidthForContent, colWidth, leftCol, rightCol, centeredCol, contentTop, contentBottom, scroll, mx, my);
        if (hit != null) {
            int rowType = hit[0];
            int rowId = hit[1];
            if (rowType == 1) {
                GSRPreferencesDropdownEntry e = gsr$getEntry(rowId);
                if (e != null) {
                    gsr$playClickSound();
                    if (e.getItemCount() == 2) {
                        int current = e.getCurrentIndex();
                        int next = (current == 0) ? 1 : 0;
                        e.apply(next);
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    model.openDropdownId = e.id;
                    model.pendingIndex = e.getCurrentIndex();
                    model.dropdownScroll = 0;
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
            } else if (rowType == 2) {
                gsr$playClickSound();
                gsr$handleToggleClick(rowId);
                model.lastClickHandledTimeMs = now;
                return true;
            } else if (rowType == 4 && rowId == 0) {
                gsr$playClickSound();
                if (minecraft != null) {
                    minecraft.gui.setScreen(new GSRResetModSettingsConfirmScreen(this));
                }
                model.lastClickHandledTimeMs = now;
                return true;
            }
        }

        return super.mouseClicked(click, captured);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (model.openDropdownId != GSRPreferencesScreenModel.DROPDOWN_NONE) {
            if (verticalAmount != 0) {
                GSRPreferencesDropdownEntry entry = gsr$getEntry(model.openDropdownId);
                if (entry != null) {
                    int contentBottom = GSRMenuComponents.singleButtonFooterLayout(width, height).footerY() - GSRUiParameters.FOOTER_CONTENT_GAP;
                    int contentWidth = width - 2 * CONTENT_MARGIN;
                    int listW = Math.min(LIST_WIDTH, contentWidth - 8);
                    int overlayLeft = CONTENT_MARGIN + contentWidth / 2 - listW / 2;
                    int overlayTop = Math.max(GSRUiParameters.PREFERENCES_DROPDOWN_MIN_TOP, CONTENT_TOP + (contentBottom - CONTENT_TOP) / 2 - GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HALF_HEIGHT);
                    int overlayBottom = Math.min(contentBottom, overlayTop + GSRUiParameters.PREFERENCES_DROPDOWN_OVERLAY_HEIGHT);
                    int selectionBottom = overlayBottom - GSRRunHistoryParameters.MAKE_SELECTION_CONTAINER_HEIGHT;
                    int delimiterTop = selectionBottom + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT;
                    int listBottomForGeom = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
                    int sbW = GSRScrollbarHelper.getScrollbarWidth();
                    if (mouseX >= overlayLeft && mouseX < overlayLeft + listW + sbW && mouseY >= overlayTop && mouseY < listBottomForGeom) {
                        var geom = GSRMultiSelectDropdown.computeGeometry(font, entry.getDropdown().getHeader(), overlayLeft, overlayTop, listBottomForGeom, listW, entry.getDropdown().getItemCount(model));
                        int delta = (int) (-verticalAmount * GSRRunHistoryParameters.FILTER_DROPDOWN_SCROLL_AMOUNT);
                        model.dropdownScroll = Math.max(0, Math.min(geom.maxScroll(), model.dropdownScroll + delta));
                    }
                }
            }
            return true;
        }
        if (model.openDropdownId == GSRPreferencesScreenModel.DROPDOWN_NONE && verticalAmount != 0) {
            int listHeight = GSRMenuComponents.singleButtonFooterLayout(width, height).footerY() - GSRUiParameters.FOOTER_CONTENT_GAP - CONTENT_TOP;
            int totalHeight = gsr$contentHeight();
            int maxScroll = Math.max(0, totalHeight - listHeight);
            model.contentScroll = (int) Math.max(0, Math.min(maxScroll, model.contentScroll - verticalAmount * GSRUiParameters.CONTENT_SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (contentScrollbarDragging) {
            int contentTop = CONTENT_TOP;
            int contentBottom = GSRMenuComponents.singleButtonFooterLayout(width, height).footerY() - GSRUiParameters.FOOTER_CONTENT_GAP;
            int listHeight = contentBottom - contentTop;
            int totalHeight = gsr$contentHeight();
            int maxScroll = Math.max(0, totalHeight - listHeight);
            if (maxScroll > 0) {
                int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT,
                        (int) (listHeight * (listHeight / (double) (listHeight + maxScroll))));
                model.contentScroll = GSRMultiSelectDropdown.scrollFromMouseY(click.y(), contentTop, contentBottom, thumbHeight, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (contentScrollbarDragging) {
            contentScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.key() == InputConstants.KEY_ESCAPE) {
            if (model.openDropdownId != GSRPreferencesScreenModel.DROPDOWN_NONE) {
                model.openDropdownId = GSRPreferencesScreenModel.DROPDOWN_NONE;
                return true;
            }
            goBack();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    /** Suffix appended to the default option label (e.g. "Blaze Rod (default)"). */
    private static final String DEFAULT_LABEL_SUFFIX = " (Default)";

    /**
     * Single dropdown entry definition: id, label, items, current index, apply callback.
     * When defaultOptionIndex >= 0, that option's label is suffixed with " (default)".
     * When iconSupplier is non-null, item icons are shown in the dropdown list.
     * When labelColorSupplier is non-null, the trigger button label is drawn in that color.
     * When tooltip is non-null, shown on hover to describe what the option changes.
     */
    private static final class GSRPreferencesDropdownEntry {
        final int id;
        final java.util.function.Supplier<List<String>> itemsSupplier;
        final java.util.function.Supplier<Integer> currentIndexSupplier;
        final java.util.function.Consumer<Integer> applyConsumer;
        final int defaultOptionIndex;
        final java.util.function.Supplier<Boolean> defaultIndexSupplier;
        final String tooltip;
        final GSRMultiSelectDropdown<GSRPreferencesScreenModel> dropdown;

        GSRPreferencesDropdownEntry(int id, String label, String header,
                java.util.function.Supplier<List<String>> baseItemsSupplier,
                java.util.function.Supplier<Integer> currentIndexSupplier,
                java.util.function.Consumer<Integer> applyConsumer,
                int defaultOptionIndex,
                java.util.function.Supplier<Boolean> defaultIndexSupplier,
                java.util.function.Supplier<String> defaultLabelSupplier,
                java.util.function.BiFunction<GSRPreferencesScreenModel, Integer, ItemStack> iconSupplier,
                java.util.function.BiFunction<GSRPreferencesScreenModel, Integer, Integer> tintSupplier,
                java.util.function.BiFunction<GSRPreferencesScreenModel, Integer, Integer> labelColorSupplier,
                String tooltip) {
            this.id = id;
            this.itemsSupplier = (defaultOptionIndex >= 0 && defaultIndexSupplier != null)
                    ? () -> {
                        var list = new ArrayList<>(baseItemsSupplier.get());
                        if (defaultOptionIndex >= 0 && defaultOptionIndex < list.size()) {
                            String labelForDefault = defaultLabelSupplier != null ? defaultLabelSupplier.get() : list.get(defaultOptionIndex);
                            String suffix = "Default".equals(labelForDefault) ? "" : DEFAULT_LABEL_SUFFIX;
                            list.set(defaultOptionIndex, labelForDefault + suffix);
                        }
                        return list;
                    }
                    : baseItemsSupplier;
            this.currentIndexSupplier = currentIndexSupplier;
            this.applyConsumer = applyConsumer;
            this.defaultOptionIndex = defaultOptionIndex;
            this.defaultIndexSupplier = defaultIndexSupplier;
            this.tooltip = tooltip;
            this.dropdown = new GSRMultiSelectDropdown<>(label, header, "dd-pref-" + id, false, new GSRMultiSelectDropdownBehavior<>() {
                @Override
                public List<String> getItems(GSRPreferencesScreenModel m) {
                    return GSRPreferencesDropdownEntry.this.itemsSupplier.get();
                }

                @Override
                public Set<Integer> getSelectedIndices(GSRPreferencesScreenModel m) {
                    int idx = m.openDropdownId == id ? m.pendingIndex : getCurrentIndex();
                    return idx >= 0 ? Set.of(idx) : Set.of();
                }

                @Override
                public String getDisplayLabel(GSRPreferencesScreenModel m) {
                    List<String> items = itemsSupplier.get();
                    int idx = m.openDropdownId == id ? m.pendingIndex : getCurrentIndex();
                    if (idx >= 0 && idx < items.size()) return items.get(idx);
                    return "Select";
                }

                @Override
                public long getSelectionTimeMs(GSRPreferencesScreenModel m) {
                    return 0;
                }

                @Override
                public ItemStack getItemIcon(GSRPreferencesScreenModel m, int index) {
                    if (iconSupplier == null) return ItemStack.EMPTY;
                    ItemStack icon = iconSupplier.apply(m, index);
                    return GSRItemStacks.isUsable(icon) ? icon : ItemStack.EMPTY;
                }

                @Override
                public Integer getItemIconTint(GSRPreferencesScreenModel m, int index) {
                    return tintSupplier != null ? tintSupplier.apply(m, index) : null;
                }

                @Override
                public Integer getDisplayLabelColor(GSRPreferencesScreenModel m) {
                    int idx = m.openDropdownId == id ? m.pendingIndex : getCurrentIndex();
                    return labelColorSupplier != null && idx >= 0 ? labelColorSupplier.apply(m, idx) : null;
                }

                private int getCurrentIndex() {
                    return GSRPreferencesDropdownEntry.this.getCurrentIndex();
                }
            });
        }

        int getCurrentIndex() {
            if (defaultOptionIndex >= 0 && defaultIndexSupplier != null && defaultIndexSupplier.get()) {
                return defaultOptionIndex;
            }
            return currentIndexSupplier.get();
        }

        /** Number of options. Used to treat 2-option dropdowns as toggles. */
        int getItemCount() {
            return itemsSupplier.get().size();
        }

        GSRMultiSelectDropdown<GSRPreferencesScreenModel> getDropdown() {
            return dropdown;
        }

        void apply(int idx) {
            if (idx >= 0) applyConsumer.accept(idx);
        }
    }
}
