package net.berkle.groupspeedrun.gui;

// Fabric: minecraft networking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
// Minecraft: screen, GUI, input, items
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
// LWJGL: key codes
import com.mojang.blaze3d.platform.InputConstants;

// GSR: minecraft state, config, network, parameters, UI helpers
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.gui.components.GSRMenuComponents;
import net.berkle.groupspeedrun.mixin.accessors.GSRPressableWidgetAccessor;
import net.berkle.groupspeedrun.network.GSRLocatorActionPayload;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;
import net.berkle.groupspeedrun.parameter.GSRLocatorParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRItemStacks;
import net.berkle.groupspeedrun.util.GSRLocatorIconHelper;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * GSR Locators menu: Clear Locators plus ON/OFF toggles for Fortress, Bastion, Stronghold, Wings.
 * Each toggle turns the locator for that structure on or off. Icon styling is configured in Preferences.
 * Shows a preview of the locator HUD (bar + icons). Time gates enforced server-side; any use deranks the run.
 */
public class GSRLocatorsScreen extends Screen {

    private static final int PREVIEW_BAR_WIDTH = GSRUiParameters.LOCATORS_PREVIEW_BAR_WIDTH;
    private static final int PREVIEW_BAR_HEIGHT = GSRUiParameters.LOCATORS_PREVIEW_BAR_HEIGHT;
    private static final int PREVIEW_ICON_RADIUS = GSRUiParameters.LOCATORS_PREVIEW_ICON_RADIUS;
    private static final int PREVIEW_Y = GSRUiParameters.LOCATORS_PREVIEW_Y;
    private static final int BUTTON_WIDTH = GSRUiParameters.LOCATORS_BUTTON_WIDTH;
    private static final int BUTTON_HEIGHT = GSRUiParameters.CONTROLS_BUTTON_HEIGHT;
    private static final int ROW_PAD = GSRUiParameters.LOCATORS_ROW_PAD;
    private static final int COL_GAP = GSRUiParameters.LOCATORS_COL_GAP;
    private static final int TWO_COLUMN_THRESHOLD = GSRUiParameters.LOCATORS_TWO_COLUMN_THRESHOLD;
    private static final int TOGGLE_ROW_HEIGHT = GSRUiParameters.LOCATORS_TOGGLE_ROW_HEIGHT;
    private static final float LABEL_SCALE = 0.85f;
    private static final int TOGGLE_ON_COLOR = GSRUiParameters.PREFERENCES_TOGGLE_NEUTRAL_ON;
    private static final int TOGGLE_OFF_COLOR = GSRUiParameters.PREFERENCES_TOGGLE_NEUTRAL_OFF;

    private final Screen parent;
    private final GSRLocatorsScreenModel model = new GSRLocatorsScreenModel();

    public GSRLocatorsScreen(Screen parent) {
        super(GSRButtonParameters.literal(GSRButtonParameters.SCREEN_LOCATORS));
        this.parent = parent;
    }

    /** Returns the parent screen for re-opening after config sync. */
    public Screen getParent() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();
        boolean useTwoColumns = height < TWO_COLUMN_THRESHOLD;
        int contentY = PREVIEW_Y + PREVIEW_BAR_HEIGHT + PREVIEW_ICON_RADIUS * 2 + GSRUiParameters.LOCATORS_CONTENT_OFFSET;

        if (useTwoColumns) {
            int colWidth = (width - COL_GAP - GSRUiParameters.PARTICIPANT_TOTAL_HORIZONTAL_INSET) / 2;
            int fullWidth = colWidth * 2 + COL_GAP;
            int y = contentY;

            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.LOCATORS_CLEAR), b ->
                    ClientPlayNetworking.send(new GSRLocatorActionPayload(GSRLocatorActionPayload.ACTION_CLEAR)))
                    .bounds(width / 2 - fullWidth / 2, y, fullWidth, BUTTON_HEIGHT).build());
        } else {
            int centerX = width / 2 - BUTTON_WIDTH / 2;
            addRenderableWidget(Button.builder(GSRButtonParameters.literal(GSRButtonParameters.LOCATORS_CLEAR), b ->
                    ClientPlayNetworking.send(new GSRLocatorActionPayload(GSRLocatorActionPayload.ACTION_CLEAR)))
                    .bounds(centerX, contentY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        var footer = GSRMenuComponents.singleButtonFooterLayout(width, height);
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.FOOTER_BACK, this::goBack,
                footer.buttonX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()));
    }

    private void goBack() {
        if (minecraft != null) {
            if (parent != null) minecraft.gui.setScreen(parent);
            else minecraft.gui.setScreen(null);
        }
    }

    /** Computes bar bounds for toggle rows. Returns {left, top, width, height} for each of the 4 locators. */
    private int[][] gsr$toggleBarBounds() {
        boolean useTwoColumns = height < TWO_COLUMN_THRESHOLD;
        int contentY = PREVIEW_Y + PREVIEW_BAR_HEIGHT + PREVIEW_ICON_RADIUS * 2 + GSRUiParameters.LOCATORS_CONTENT_OFFSET;
        int startY = contentY + BUTTON_HEIGHT + ROW_PAD;

        if (useTwoColumns) {
            int colWidth = (width - COL_GAP - GSRUiParameters.PARTICIPANT_TOTAL_HORIZONTAL_INSET) / 2;
            int leftX = GSRUiParameters.LOCATORS_PANEL_MARGIN;
            int rightX = GSRUiParameters.LOCATORS_PANEL_MARGIN + colWidth + COL_GAP;
            return new int[][]{
                    {leftX, startY, colWidth, TOGGLE_ROW_HEIGHT},
                    {rightX, startY, colWidth, TOGGLE_ROW_HEIGHT},
                    {leftX, startY + TOGGLE_ROW_HEIGHT + ROW_PAD, colWidth, TOGGLE_ROW_HEIGHT},
                    {rightX, startY + TOGGLE_ROW_HEIGHT + ROW_PAD, colWidth, TOGGLE_ROW_HEIGHT}
            };
        } else {
            int centerX = width / 2 - BUTTON_WIDTH / 2;
            int y = startY;
            return new int[][]{
                    {centerX, y, BUTTON_WIDTH, TOGGLE_ROW_HEIGHT},
                    {centerX, y + TOGGLE_ROW_HEIGHT + ROW_PAD, BUTTON_WIDTH, TOGGLE_ROW_HEIGHT},
                    {centerX, y + (TOGGLE_ROW_HEIGHT + ROW_PAD) * 2, BUTTON_WIDTH, TOGGLE_ROW_HEIGHT},
                    {centerX, y + (TOGGLE_ROW_HEIGHT + ROW_PAD) * 3, BUTTON_WIDTH, TOGGLE_ROW_HEIGHT}
            };
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, GSRUiParameters.SCREEN_BG_DARK);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, getTitle(), width / 2, GSRUiParameters.TITLE_Y, GSRUiParameters.TITLE_COLOR);

        GSRConfigWorld wc = GSRClient.clientWorldConfig;
        GSRConfigPlayer pc = GSRClient.PLAYER_CONFIG;
        if (wc != null && pc != null) {
            drawPreview(context, wc, pc);
        }

        int[][] bars = gsr$toggleBarBounds();
        String[] labels = { GSRButtonParameters.LOCATORS_FORTRESS, GSRButtonParameters.LOCATORS_BASTION, GSRButtonParameters.LOCATORS_STRONGHOLD, GSRButtonParameters.LOCATORS_WINGS };
        boolean[] active = (wc != null && pc != null) ? new boolean[] { wc.fortressLocated && pc.fortressLocatorOn, wc.bastionLocated && pc.bastionLocatorOn, wc.strongholdLocated && pc.strongholdLocatorOn, wc.shipLocated && pc.shipLocatorOn } : new boolean[4];
        ItemStack[] icons = { GSRItemStacks.of(Items.BLAZE_ROD), GSRItemStacks.of(Items.PIGLIN_HEAD), GSRItemStacks.of(Items.ENDER_EYE), GSRItemStacks.of(Items.ELYTRA) };

        for (int i = 0; i < 4; i++) {
            int[] b = bars[i];
            boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            gsr$drawToggleRow(context, labels[i], active[i], b[0], b[1], b[2], b[3], icons[i], hovered);
        }
    }

    /** Draws one toggle row: label + ON/OFF with icon. */
    private void gsr$drawToggleRow(GuiGraphicsExtractor context, String label, boolean value, int left, int top, int width, int height, ItemStack icon, boolean hovered) {
        int labelY = top + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
        int labelX = left + GSRRunHistoryParameters.LIST_TEXT_INSET;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(labelX, labelY);
        matrices.scale(LABEL_SCALE, LABEL_SCALE);
        context.text(font, net.minecraft.network.chat.Component.literal(label), 0, 0, GSRRunHistoryParameters.LABEL_COLOR, true);
        matrices.popMatrix();

        int barTop = top + (int) (GSRUiParameters.PREFERENCES_LABEL_AREA_HEIGHT * LABEL_SCALE) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
        int barHeight = height - (barTop - top);
        var textures = GSRPressableWidgetAccessor.gsr$getTextures();
        var tex = textures.get(true, hovered);
        int barDrawWidth = width - GSRRunHistoryParameters.CONTAINER_INSET * 2;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, tex, left + GSRRunHistoryParameters.CONTAINER_INSET, barTop, barDrawWidth, barHeight);

        int iconSize = GSRUiParameters.PREFERENCES_TOGGLE_ICON_SIZE;
        int iconMargin = GSRUiParameters.PREFERENCES_TOGGLE_ICON_MARGIN;
        int iconX = left + GSRRunHistoryParameters.CONTAINER_INSET + iconMargin;
        int iconY = barTop + (barHeight - iconSize) / 2;
        float scale = (iconSize - 2 * iconMargin) / 16f;
        matrices.pushMatrix();
        matrices.translate(iconX + iconMargin, iconY + iconMargin);
        matrices.scale(scale, scale);
        if (GSRItemStacks.isUsable(icon)) {
            context.item(icon, 0, 0);
        }
        matrices.popMatrix();

        int textColor = value ? TOGGLE_ON_COLOR : TOGGLE_OFF_COLOR;
        String display = value ? "ON" : "OFF";
        int textLeft = iconX + iconSize + iconMargin + GSRRunHistoryParameters.LIST_TEXT_INSET;
        int textY = barTop + (barHeight - font.lineHeight) / 2;
        context.text(font, net.minecraft.network.chat.Component.literal(display), textLeft, textY, textColor, true);
    }

    /** True if turning on a locator would invalidate the run (active run + anti-cheat enabled, not already deranked). */
    private boolean gsr$wouldInvalidateRun() {
        GSRConfigWorld wc = GSRClient.clientWorldConfig;
        return wc != null && wc.startTime > 0 && wc.antiCheatEnabled && !wc.locatorDeranked;
    }

    /** Toggles the given locator (0=fortress, 1=bastion, 2=stronghold, 3=ship). Shows invalidate confirm if turning on would derank. */
    private void gsr$toggleLocator(int index) {
        GSRConfigWorld wc = GSRClient.clientWorldConfig;
        GSRConfigPlayer pc = GSRClient.PLAYER_CONFIG;
        if (wc == null || pc == null) return;
        boolean currentlyActive = switch (index) {
            case 0 -> wc.fortressLocated && pc.fortressLocatorOn;
            case 1 -> wc.bastionLocated && pc.bastionLocatorOn;
            case 2 -> wc.strongholdLocated && pc.strongholdLocatorOn;
            case 3 -> wc.shipLocated && pc.shipLocatorOn;
            default -> false;
        };
        if (currentlyActive) {
            gsr$sendToggle(index);
        } else {
            if (gsr$wouldInvalidateRun()) {
                String label = switch (index) {
                    case 0 -> GSRButtonParameters.LOCATORS_FORTRESS;
                    case 1 -> GSRButtonParameters.LOCATORS_BASTION;
                    case 2 -> GSRButtonParameters.LOCATORS_STRONGHOLD;
                    case 3 -> GSRButtonParameters.LOCATORS_WINGS;
                    default -> "";
                };
                if (minecraft != null) minecraft.gui.setScreen(new GSRLocatorInvalidateConfirmScreen(this, label, () -> gsr$sendToggle(index)));
            } else {
                gsr$sendToggle(index);
            }
        }
    }

    private void gsr$sendToggle(int index) {
        byte action = switch (index) {
            case 0 -> GSRLocatorActionPayload.ACTION_TOGGLE_FORTRESS;
            case 1 -> GSRLocatorActionPayload.ACTION_TOGGLE_BASTION;
            case 2 -> GSRLocatorActionPayload.ACTION_TOGGLE_STRONGHOLD;
            case 3 -> GSRLocatorActionPayload.ACTION_TOGGLE_SHIP;
            default -> GSRLocatorActionPayload.ACTION_CLEAR;
        };
        if (action != GSRLocatorActionPayload.ACTION_CLEAR) {
            ClientPlayNetworking.send(new GSRLocatorActionPayload(action));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean captured) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(click, captured);
        int mx = (int) click.x();
        int my = (int) click.y();
        long now = System.currentTimeMillis();

        int[][] bars = gsr$toggleBarBounds();
        for (int i = 0; i < 4; i++) {
            int[] b = bars[i];
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                gsr$toggleLocator(i);
                model.lastClickHandledTimeMs = now;
                return true;
            }
        }

        return super.mouseClicked(click, false);
    }

    /** Draws the exact locator bar as in-game: same bar style with icons on the bar, equally spaced (as if in front of you). */
    private void drawPreview(GuiGraphicsExtractor context, GSRConfigWorld wc, GSRConfigPlayer pc) {
        int centerX = width / 2;
        int halfW = PREVIEW_BAR_WIDTH / 2;
        int barLeft = centerX - halfW;
        int barTop = PREVIEW_Y + GSRLocatorParameters.BAR_Y_OFFSET;
        float alpha = 1f;

        // Bar: same as GSRLocateHudMixin.renderTrackingBar
        context.fill(barLeft, barTop, barLeft + PREVIEW_BAR_WIDTH, barTop + PREVIEW_BAR_HEIGHT, GSRColorHelper.applyAlpha(GSRLocatorParameters.BAR_BG, GSRLocatorParameters.BAR_BG_ALPHA * alpha));
        for (int i = barLeft; i < barLeft + PREVIEW_BAR_WIDTH; i++) {
            float ratio = (float) (i - barLeft) / PREVIEW_BAR_WIDTH;
            context.fill(i, barTop, i + 1, barTop + PREVIEW_BAR_HEIGHT, previewGradientColor(GSRLocatorParameters.BAR_GRADIENT_START, GSRLocatorParameters.BAR_GRADIENT_END, ratio));
        }
        for (int i = 0; i < 5; i++) {
            float rel = (i / 4.0f) * 2 - 1;
            int tx = centerX + (int) (rel * halfW);
            context.fill(tx, barTop, tx + 1, barTop + PREVIEW_BAR_HEIGHT, GSRColorHelper.applyAlpha(GSRLocatorParameters.BAR_TICK_COLOR, alpha * GSRLocatorParameters.BAR_TICK_ALPHA));
        }
        context.fill(barLeft, barTop - 1, barLeft + PREVIEW_BAR_WIDTH, barTop, GSRColorHelper.applyAlpha(GSRLocatorParameters.BAR_TOP_BORDER, alpha));
        context.fill(barLeft, barTop + PREVIEW_BAR_HEIGHT, barLeft + PREVIEW_BAR_WIDTH, barTop + PREVIEW_BAR_HEIGHT + 1, GSRColorHelper.applyAlpha(GSRLocatorParameters.BAR_BOTTOM_BORDER, alpha));

        // Icons on the bar: use player config (icon styling set in Preferences)
        int iconCenterY = barTop + PREVIEW_BAR_HEIGHT / 2;
        float[] positions = { -0.75f, -0.25f, 0.25f, 0.75f };
        String fortressItem = gsr$previewFortressItem(pc);
        String bastionItem = gsr$previewBastionItem(pc);
        String strongholdItem = gsr$previewStrongholdItem(pc);
        String shipItem = gsr$previewShipItem(pc);
        boolean fortressActive = gsr$previewFortressActive(wc, pc);
        boolean bastionActive = gsr$previewBastionActive(wc, pc);
        boolean strongholdActive = gsr$previewStrongholdActive(wc, pc);
        boolean shipActive = gsr$previewShipActive(wc, pc);
        drawPreviewIconOnBar(context, centerX + (int) (positions[0] * halfW), iconCenterY, GSRLocatorIconHelper.getItemStack(fortressItem, Items.BLAZE_ROD), pc.fortressColor, fortressActive, "Fortress");
        drawPreviewIconOnBar(context, centerX + (int) (positions[1] * halfW), iconCenterY, GSRLocatorIconHelper.getItemStack(bastionItem, Items.PIGLIN_HEAD), pc.bastionColor, bastionActive, "Bastion");
        drawPreviewIconOnBar(context, centerX + (int) (positions[2] * halfW), iconCenterY, GSRLocatorIconHelper.getItemStack(strongholdItem, Items.ENDER_EYE), pc.strongholdColor, strongholdActive, "Stronghold");
        drawPreviewIconOnBar(context, centerX + (int) (positions[3] * halfW), iconCenterY, GSRLocatorIconHelper.getItemStack(shipItem, Items.ELYTRA), pc.shipColor, shipActive, "Wings");

        if (wc.antiCheatEnabled && wc.locatorDeranked) {
            context.centeredText(font, GSRUiParameters.LOCATORS_DERANKED_MESSAGE, centerX, PREVIEW_Y - GSRUiParameters.LOCATORS_DERANKED_LABEL_OFFSET, GSRUiParameters.LOCATORS_DERANKED_COLOR);
        }
    }

    private static String gsr$previewFortressItem(GSRConfigPlayer pc) { return pc.fortressItem; }
    private static String gsr$previewBastionItem(GSRConfigPlayer pc) { return pc.bastionItem; }
    private static String gsr$previewStrongholdItem(GSRConfigPlayer pc) { return pc.strongholdItem; }
    private static String gsr$previewShipItem(GSRConfigPlayer pc) { return pc.shipItem; }
    private static boolean gsr$previewFortressActive(GSRConfigWorld wc, GSRConfigPlayer pc) { return wc != null && pc != null && wc.fortressLocated && pc.fortressLocatorOn; }
    private static boolean gsr$previewBastionActive(GSRConfigWorld wc, GSRConfigPlayer pc) { return wc != null && pc != null && wc.bastionLocated && pc.bastionLocatorOn; }
    private static boolean gsr$previewStrongholdActive(GSRConfigWorld wc, GSRConfigPlayer pc) { return wc != null && pc != null && wc.strongholdLocated && pc.strongholdLocatorOn; }
    private static boolean gsr$previewShipActive(GSRConfigWorld wc, GSRConfigPlayer pc) { return wc != null && pc != null && wc.shipLocated && pc.shipLocatorOn; }

    private static int previewGradientColor(int c1, int c2, float ratio) {
        int a = (int) Mth.lerp(ratio, (c1 >> 24) & 0xFF, (c2 >> 24) & 0xFF);
        int r = (int) Mth.lerp(ratio, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
        int g = (int) Mth.lerp(ratio, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
        int b = (int) Mth.lerp(ratio, c1 & 0xFF, c2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Draws one icon on the bar: theme color box (as if looking directly at), dark inner, centered item. Component uses structure color. */
    private void drawPreviewIconOnBar(GuiGraphicsExtractor context, int iconCenterX, int iconCenterY, ItemStack stack, int themeColor, boolean active, String label) {
        int r = PREVIEW_ICON_RADIUS; // 9
        int inner = GSRLocatorParameters.ICON_INNER_RADIUS;
        // Preview: show theme color as if looking directly at location
        context.fill(iconCenterX - r, iconCenterY - r, iconCenterX + r, iconCenterY + r, GSRColorHelper.applyAlpha(themeColor & 0x00FFFFFF, 1.0f));
        context.fill(iconCenterX - inner, iconCenterY - inner, iconCenterX + inner, iconCenterY + inner, GSRColorHelper.applyAlpha(GSRLocatorParameters.BAR_BG, 1.0f));
        // Item centered: drawItem uses top-left, so (centerX - 8, centerY - 8) centers 16x16 item
        if (GSRItemStacks.isUsable(stack)) {
            context.item(stack, iconCenterX - inner, iconCenterY - inner);
        }
        int textColor = active ? (0xFF000000 | (themeColor & 0x00FFFFFF)) : GSRUiParameters.LOCATORS_INACTIVE_LABEL;
        context.centeredText(font, label, iconCenterX, iconCenterY + r + 2, textColor);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (keyInput.key() == InputConstants.KEY_ESCAPE) {
            goBack();
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
