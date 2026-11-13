package net.berkle.groupspeedrun.gui.widget;

import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Button for title screen with internal padding and scaled text so labels fit without clipping.
 * Used for GSR Run History and aligned with the shrunk Realms button.
 */
public class GSRTitleScreenButton extends ButtonWidget.Text {

    public GSRTitleScreenButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        drawButton(context);
        if (getAlpha() <= 0.01f) return;
        drawLabelWithPaddingAndScale(context);
    }

    /** Draw label with padding and reduced scale so text fits. */
    private void drawLabelWithPaddingAndScale(DrawContext context) {
        float scale = GSRButtonParameters.TITLE_BUTTON_TEXT_SCALE;
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(scale, scale);
        matrices.translate(-centerX, -centerY);

        int color = active ? GSRButtonParameters.TITLE_BUTTON_TEXT_ACTIVE : GSRButtonParameters.TITLE_BUTTON_TEXT_INACTIVE;
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            getMessage(),
            centerX,
            centerY - 4,
            GSRColorHelper.applyAlpha(color, getAlpha())
        );
        matrices.popMatrix();
    }
}
