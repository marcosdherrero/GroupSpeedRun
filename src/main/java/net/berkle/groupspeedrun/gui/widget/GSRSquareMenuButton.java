package net.berkle.groupspeedrun.gui.widget;

import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Square menu button with the letters GSR scaled to fit, matching 26.2 pause/title icon-row size (20×20).
 */
public class GSRSquareMenuButton extends ButtonWidget.Text {

    public static final int SIZE = 20;

    public GSRSquareMenuButton(int x, int y, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, SIZE, SIZE, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        drawButton(context);
        float fade = getAlpha();
        if (fade <= 0.01f) return;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String label = getMessage().getString();
        int padding = GSRButtonParameters.SQUARE_BUTTON_LABEL_PADDING;
        int maxW = Math.max(1, getWidth() - padding * 2);
        int maxH = Math.max(1, getHeight() - padding * 2);
        float scale = Math.min(maxW / (float) Math.max(1, font.getWidth(label)), maxH / (float) font.fontHeight);
        scale = Math.min(scale, 1.0f);

        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(scale, scale);
        int color = active ? GSRButtonParameters.TITLE_BUTTON_TEXT_ACTIVE : GSRButtonParameters.TITLE_BUTTON_TEXT_INACTIVE;
        context.drawCenteredTextWithShadow(font, getMessage(), 0, -font.fontHeight / 2, GSRColorHelper.applyAlpha(color, fade));
        matrices.popMatrix();
    }
}
