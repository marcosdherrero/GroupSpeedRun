package net.berkle.groupspeedrun.gui.widget;

// Minecraft: GUI, text
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
// GSR: parameters
import net.berkle.groupspeedrun.gui.GSRTickerState;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;

/**
 * Button that draws sideways-scrolling text when the label overflows and the button is hovered.
 * Used for the pause menu Options / Open to LAN / GSR Options row.
 */
public class GSRTickerButtonWidget extends ButtonWidget.Text {

    private static final GSRTickerState TICKER = new GSRTickerState();
    private final String tickerKey;

    public GSRTickerButtonWidget(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress, String tickerKey) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.tickerKey = tickerKey != null ? tickerKey : "ticker-btn";
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        drawButton(context);
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        String label = getMessage().getString();
        int margin = GSRRunHistoryParameters.BUTTON_TICKER_MARGIN;
        int innerLeft = getX() + margin;
        int innerRight = getX() + getWidth() - margin;
        int maxWidth = getWidth() - 2 * margin;
        int textWidth = textRenderer.getWidth(label);
        int textY = getY() + (getHeight() - textRenderer.fontHeight) / 2;

        context.enableScissor(innerLeft, getY(), innerRight, getY() + getHeight());
        int color = active ? GSRRunHistoryParameters.TICKER_ACTIVE_COLOR : GSRRunHistoryParameters.TICKER_INACTIVE_COLOR;
        if (textWidth > maxWidth) {
            if (hovered) {
                String looping = label + "    " + label;
                int loopWidth = textRenderer.getWidth(looping);
                int maxScroll = loopWidth / 2;
                long elapsed = TICKER.getElapsedMs(tickerKey, System.currentTimeMillis());
                int scrollOffset = (int) ((elapsed / (double) GSRRunHistoryParameters.TICKER_CYCLE_MS) * maxScroll);
                context.drawTextWithShadow(textRenderer, net.minecraft.text.Text.literal(looping), getX() + margin - scrollOffset, textY, color);
            } else {
                String truncated = truncateWithPeriod(textRenderer, label, maxWidth);
                context.drawTextWithShadow(textRenderer, net.minecraft.text.Text.literal(truncated), getX() + margin, textY, color);
            }
        } else {
            context.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, textY, color);
        }
        context.disableScissor();
    }

    private static String truncateWithPeriod(net.minecraft.client.font.TextRenderer tr, String text, int maxWidth) {
        if (tr.getWidth(text) <= maxWidth) return text;
        int periodW = tr.getWidth(".");
        int available = maxWidth - periodW;
        for (int len = text.length(); len > 0; len--) {
            String s = text.substring(0, len);
            if (tr.getWidth(s) <= available) return s + ".";
        }
        return ".";
    }

    /** Call from GameMenuScreen render (end of frame) so ticker resets when no scrolling button was drawn. */
    public static void clearTickerIfNotDrawn() {
        TICKER.clearIfNotDrawn();
    }
}
