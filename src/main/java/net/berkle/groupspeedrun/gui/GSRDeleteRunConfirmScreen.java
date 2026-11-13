package net.berkle.groupspeedrun.gui;

// Minecraft: screen, GUI, input
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

// GSR: parameters
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;

/**
 * Confirmation popup before deleting a run from personal and shared lists.
 * Shows when the user clicks "Delete Run" in Run History.
 * When multiple runs are selected, offers "Delete" (single) and "Delete All Selected".
 */
public class GSRDeleteRunConfirmScreen extends Screen {

    private final Screen parent;
    /** Deletes only the run currently being displayed. */
    private final Runnable onConfirmOne;
    /** Deletes all selected runs; null when only one run is selected. */
    private final Runnable onConfirmAll;

    /**
     * @param parent       Screen to return to on cancel (typically Run History).
     * @param onConfirmOne Runnable to delete the single run being displayed.
     * @param onConfirmAll Optional runnable to delete all selected runs; null to hide "Delete All Selected" button.
     */
    public GSRDeleteRunConfirmScreen(Screen parent, Runnable onConfirmOne, Runnable onConfirmAll) {
        super(Component.literal("Delete Run?"));
        this.parent = parent;
        this.onConfirmOne = onConfirmOne;
        this.onConfirmAll = onConfirmAll;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = GSRUiParameters.NEW_WORLD_BUTTON_WIDTH;
        int buttonHeight = GSRUiParameters.CONTROLS_BUTTON_HEIGHT;
        int gap = Math.max(GSRUiParameters.NEW_WORLD_BUTTON_GAP, GSRUiParameters.NEW_WORLD_MIN_BUTTON_GAP);
        int centerX = width / 2;
        int y = GSRConfirmLayout.buttonY(font, confirmMessage(), width, height);

        if (onConfirmAll != null) {
            int totalWidth = 3 * buttonWidth + 2 * gap;
            int leftX = centerX - totalWidth / 2;
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.RUN_HISTORY_DELETE_ONE), btn -> confirmOne())
                    .bounds(leftX, y, buttonWidth, buttonHeight).build());
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.RUN_HISTORY_DELETE_ALL_SELECTED), btn -> confirmAll())
                    .bounds(leftX + buttonWidth + gap, y, buttonWidth, buttonHeight).build());
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.NEW_WORLD_CANCEL), btn -> cancel())
                    .bounds(leftX + 2 * (buttonWidth + gap), y, buttonWidth, buttonHeight).build());
        } else {
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.RUN_HISTORY_DELETE_ONE), btn -> confirmOne())
                    .bounds(centerX - buttonWidth - gap / 2, y, buttonWidth, buttonHeight).build());
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.NEW_WORLD_CANCEL), btn -> cancel())
                    .bounds(centerX + gap / 2, y, buttonWidth, buttonHeight).build());
        }
    }

    private void confirmOne() {
        if (onConfirmOne != null) onConfirmOne.run();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void confirmAll() {
        if (onConfirmAll != null) onConfirmAll.run();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void cancel() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, GSRUiParameters.STATUS_BG_COLOR);
        super.extractRenderState(context, mouseX, mouseY, delta);

        GSRConfirmLayout.drawTitleAndMessage(context, font, getTitle(), confirmMessage(), width, height);
    }

    private String confirmMessage() {
        return onConfirmAll != null
                ? "Delete the displayed run, or delete all selected runs from your personal and shared lists."
                : "This will remove the run from your personal and shared lists.";
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
