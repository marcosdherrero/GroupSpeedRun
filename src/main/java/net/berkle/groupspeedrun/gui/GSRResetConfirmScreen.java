package net.berkle.groupspeedrun.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.berkle.groupspeedrun.network.GSRRunActionPayload;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Confirmation popup before Reset Run: wipes run data, teleports players to spawn, restores from snapshot.
 * Prevents accidental reset.
 */
public class GSRResetConfirmScreen extends Screen {

    private final Screen parent;

    public GSRResetConfirmScreen(Screen parent) {
        super(Component.literal("Reset Run?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = GSRUiParameters.NEW_WORLD_BUTTON_WIDTH;
        int buttonHeight = GSRUiParameters.CONTROLS_BUTTON_HEIGHT;
        int gap = Math.max(GSRUiParameters.NEW_WORLD_BUTTON_GAP, GSRUiParameters.NEW_WORLD_MIN_BUTTON_GAP);
        int centerX = width / 2;
        int y = GSRConfirmLayout.buttonY(font, GSRButtonParameters.RESET_CONFIRM_MESSAGE, width, height);

        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.RESET_CONFIRM), btn -> confirm())
                .bounds(centerX - buttonWidth - gap / 2, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.RESET_CANCEL), btn -> cancel())
                .bounds(centerX + gap / 2, y, buttonWidth, buttonHeight).build());
    }

    private void confirm() {
        ClientPlayNetworking.send(new GSRRunActionPayload(GSRRunActionPayload.ACTION_RESET));
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

        GSRConfirmLayout.drawTitleAndMessage(context, font, getTitle(), GSRButtonParameters.RESET_CONFIRM_MESSAGE, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
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
