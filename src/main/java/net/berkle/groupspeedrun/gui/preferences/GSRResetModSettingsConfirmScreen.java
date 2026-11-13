package net.berkle.groupspeedrun.gui.preferences;

// Minecraft: screen, GUI, input, NBT, text
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

// Fabric: minecraft networking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// GSR: config, network, parameters
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRLocatorNonAdminMode;
import net.berkle.groupspeedrun.config.GSRSeedFilterSettings;
import net.berkle.groupspeedrun.network.GSRWorldConfigPayload;
import net.berkle.groupspeedrun.gui.GSRConfirmLayout;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRHudParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;

/**
 * Confirmation popup before resetting Mod Settings to defaults.
 * Prevents accidental reset of HUD Scale, Visibility, Anti-Cheat, Locator Non-Admin, New World Before Run Ends.
 */
public class GSRResetModSettingsConfirmScreen extends Screen {

    private final Screen parent;

    public GSRResetModSettingsConfirmScreen(Screen parent) {
        super(Component.literal("Reset Mod Settings?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = GSRUiParameters.NEW_WORLD_BUTTON_WIDTH;
        int buttonHeight = GSRUiParameters.CONTROLS_BUTTON_HEIGHT;
        int gap = Math.max(GSRUiParameters.NEW_WORLD_BUTTON_GAP, GSRUiParameters.NEW_WORLD_MIN_BUTTON_GAP);
        int centerX = width / 2;
        int y = GSRConfirmLayout.buttonY(font, GSRButtonParameters.PREFERENCES_RESET_CONFIRM_MESSAGE, width, height);

        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.PREFERENCES_RESET_CONFIRM), btn -> confirm())
                .bounds(centerX - buttonWidth - gap / 2, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.PREFERENCES_RESET_CANCEL), btn -> cancel())
                .bounds(centerX + gap / 2, y, buttonWidth, buttonHeight).build());
    }

    private void confirm() {
        GSRConfigPlayer pc = GSRClient.PLAYER_CONFIG;
        GSRConfigWorld wc = GSRClient.clientWorldConfig;

        pc.timerScale = GSRHudParameters.DEFAULT_SCALE;
        pc.locateScale = GSRHudParameters.DEFAULT_SCALE;
        pc.hudVisibility = GSRConfigPlayer.VISIBILITY_PRESSED;
        pc.allowNewWorldBeforeRunEnd = false;
        pc.clampAll();

        GSRPreferencesScreen gsrPrefs = parent instanceof GSRPreferencesScreen ? (GSRPreferencesScreen) parent : null;
        if (gsrPrefs != null) {
            gsrPrefs.gsr$syncPlayerConfig();
            gsrPrefs.gsr$applyVisibilityChange();
        } else {
            CompoundTag nbt = new CompoundTag();
            pc.writeNbt(nbt);
            if (minecraft != null && minecraft.player != null) {
                ClientPlayNetworking.send(new GSRConfigPayload(nbt));
            }
            GSRClient.setPreviousHudVisibility(pc.hudVisibility);
        }

        if (wc != null) {
            wc.antiCheatEnabled = true;
            wc.autoStartEnabled = true;
            wc.seedFilterEnabled = true;
            GSRSeedFilterSettings.setEnabled(true);
            wc.locatorNonAdminMode = GSRLocatorNonAdminMode.POST_SPLIT_30MIN.getValue();
            if (minecraft != null && minecraft.player != null) {
                ClientPlayNetworking.send(new GSRWorldConfigPayload(GSRWorldConfigPayload.fromConfig()));
            }
        }

        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private void cancel() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, GSRUiParameters.STATUS_BG_COLOR);
        super.extractRenderState(context, mouseX, mouseY, delta);

        GSRConfirmLayout.drawTitleAndMessage(context, font, getTitle(),
                GSRButtonParameters.PREFERENCES_RESET_CONFIRM_MESSAGE, width, height);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.key() == InputConstants.KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
