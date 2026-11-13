package net.berkle.groupspeedrun.gui;

import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.mixin.accessors.GSRButtonWidgetAccessor;
import net.berkle.groupspeedrun.mixin.accessors.GSRGameMenuScreenAccessor;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Confirmation popup before "New GSR World": save and quit to world list, or cancel.
 * Prevents accidental disconnect and shows the suggested world name.
 */
public class GSRNewWorldConfirmScreen extends Screen {

    private final Screen parent;
    private final String suggestedWorldName;

    public GSRNewWorldConfirmScreen(Screen parent, String suggestedWorldName) {
        super(Component.literal("New GSR World?"));
        this.parent = parent;
        this.suggestedWorldName = suggestedWorldName != null ? suggestedWorldName : GSRUiParameters.NEW_WORLD_DEFAULT_NAME;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = GSRUiParameters.NEW_WORLD_BUTTON_WIDTH;
        int buttonHeight = GSRUiParameters.CONTROLS_BUTTON_HEIGHT;
        int gap = Math.max(GSRUiParameters.NEW_WORLD_BUTTON_GAP, GSRUiParameters.NEW_WORLD_MIN_BUTTON_GAP);
        int centerX = width / 2;
        int y = height / 2 + GSRUiParameters.CONTROLS_PADDING;

        // Two buttons: Continue (saves world and opens Create World) and Cancel
        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.NEW_WORLD_SAVE_CREATE), btn -> confirm())
                .bounds(centerX - buttonWidth - gap / 2, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.NEW_WORLD_CANCEL), btn -> cancel())
                .bounds(centerX + gap / 2, y, buttonWidth, buttonHeight).build());
    }

    private void confirm() {
        GSRClient.nextGsrWorldName = suggestedWorldName;
        if (minecraft != null && minecraft.getSingleplayerServer() != null) {
            // Open pause menu, then simulate clicking "Save and Quit" on next tick (same code path as user click).
            minecraft.gui.setScreen(new PauseScreen(true));
            minecraft.execute(() -> {
                if (minecraft.gui.screen() instanceof PauseScreen menu) {
                    Button exitBtn = ((GSRGameMenuScreenAccessor) menu).gsr$getExitButton();
                    if (exitBtn != null) {
                        ((GSRButtonWidgetAccessor) exitBtn).gsr$getOnPress().onPress(exitBtn);
                    }
                }
            });
        } else {
            cancel();
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

        context.centeredText(font, getTitle(), width / 2, height / 2 - GSRUiParameters.NEW_WORLD_TITLE_OFFSET, GSRUiParameters.NEW_WORLD_TITLE_COLOR);
        String line1 = "Saves the world and opens Create World with:";
        String line2 = suggestedWorldName;
        context.centeredText(font, line1, width / 2, height / 2 - GSRUiParameters.NEW_WORLD_LINE1_OFFSET, GSRUiParameters.NEW_WORLD_LINE1_COLOR);
        context.centeredText(font, line2, width / 2, height / 2 - GSRUiParameters.NEW_WORLD_LINE2_OFFSET, GSRUiParameters.NEW_WORLD_LINE2_COLOR);
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
