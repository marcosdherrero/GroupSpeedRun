package net.berkle.groupspeedrun.client;

// Minecraft: GUI
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

// GSR: GUI, mixin accessors, parameters
import net.berkle.groupspeedrun.gui.widget.GSRSquareMenuButton;
import net.berkle.groupspeedrun.mixin.accessors.GSRGameMenuScreenAccessor;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;

// Java collections
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pause/title layout. Uses a 20x20 GSR square matching 26.x.
 * On 26.x-style compact icon rows the button is inserted and the row recentered.
 * On 1.21.11, language/accessibility sit on opposite sides of Options/Quit, so
 * recentering would overlap those buttons -- the square is parked beside the row instead.
 */
public final class GSRGameMenuLayout {

    private static final int SQUARE_SIZE = GSRSquareMenuButton.SIZE;
    private static final int SQUARE_GAP = 4;

    private GSRGameMenuLayout() {}

    /** True when vanilla pause/title has a 16-24px square icon row. */
    public static boolean hasVanillaSquareRow(net.minecraft.client.gui.screen.Screen screen) {
        return !collectVanillaSquareRow(screen).isEmpty();
    }

    /**
     * Creates the square GSR button (caller adds it via addDrawableChild, then calls reapplySquareLayout).
     */
    public static ButtonWidget createSquareButton(GameMenuScreen screen) {
        return createSquareButton((net.minecraft.client.gui.screen.Screen) screen);
    }

    /** Square GSR button for any screen (title or pause). Caller adds it, then applySquarePlacement. */
    public static ButtonWidget createSquareButton(net.minecraft.client.gui.screen.Screen screen) {
        ButtonWidget button = new GSRSquareMenuButton(0, 0,
                GSRButtonParameters.literal(GSRButtonParameters.TITLE_GSR_SQUARE),
                b -> GSRScreens.openControls(screen));
        button.setTooltip(Tooltip.of(Text.literal(GSRButtonParameters.TITLE_GSR_CONTROLS)));
        return button;
    }

    /**
     * Prefer the GSR square button when the cursor is on it (first overlapping child can steal the hit).
     */
    public static boolean handleSquareClick(net.minecraft.client.gui.screen.Screen screen, Click click, boolean captured) {
        ClickableWidget gsr = findGsrSquareButton(screen);
        if (gsr == null || !gsr.isMouseOver(click.x(), click.y())) return false;
        return gsr.mouseClicked(click, captured);
    }

    /** Place the 20x20 GSR square after vanilla layout. */
    public static void reapplySquareLayout(GameMenuScreen screen) {
        applySquarePlacement(screen);
    }

    /**
     * Compact 26.x icon row: insert GSR and re-center.
     * Otherwise park the 20x20 button beside the existing row without moving vanilla widgets.
     */
    public static void applySquarePlacement(net.minecraft.client.gui.screen.Screen screen) {
        ClickableWidget gsr = findGsrSquareButton(screen);
        if (gsr == null) return;
        List<ClickableWidget> vanilla = collectVanillaSquareRow(screen);
        if (isCompactSquareRow(vanilla)) {
            recenterSquareRow(screen);
            return;
        }
        parkBesideExistingRow(screen, gsr);
    }

    static void recenterSquareRow(net.minecraft.client.gui.screen.Screen screen) {
        List<ClickableWidget> vanilla = collectVanillaSquareRow(screen);
        if (vanilla.isEmpty()) return;
        vanilla.sort(Comparator.comparingInt(ClickableWidget::getX));

        ClickableWidget gsr = findGsrSquareButton(screen);
        int size = SQUARE_SIZE;
        int gap = measureSquareGap(vanilla);
        int minX = vanilla.get(0).getX();
        int maxX = vanilla.get(vanilla.size() - 1).getX() + vanilla.get(vanilla.size() - 1).getWidth();
        int centerX = (minX + maxX) / 2;
        int y = vanilla.get(0).getY();

        List<ClickableWidget> row = new ArrayList<>(vanilla);
        if (gsr != null && !row.contains(gsr)) {
            row.add(gsr);
        }
        int n = row.size();
        int totalW = n * size + Math.max(0, n - 1) * gap;
        int startX = centerX - totalW / 2;

        for (int i = 0; i < n; i++) {
            ClickableWidget w = row.get(i);
            w.setPosition(startX + i * (size + gap), y);
            w.setDimensions(size, size);
        }
    }

    /**
     * Applies two-column layout to the exit row. Used only if the square button could not be added.
     */
    public static ButtonWidget applyLayout(GameMenuScreen screen) {
        ButtonWidget exitBtn = ((GSRGameMenuScreenAccessor) screen).gsr$getExitButton();
        if (exitBtn == null) return null;

        int leftX = exitBtn.getX();
        int totalW = exitBtn.getWidth();
        int rowY = exitBtn.getY();
        int btnH = exitBtn.getHeight();
        int gap = measureGridColumnGap(screen);
        int halfW = (totalW - gap) / 2;
        int rightX = leftX + halfW + gap;

        exitBtn.setMessage(GSRButtonParameters.literal(GSRButtonParameters.GAME_MENU_SAVE_QUIT));
        exitBtn.setPosition(leftX, rowY);
        exitBtn.setDimensions(halfW, btnH);

        return ButtonWidget.builder(GSRButtonParameters.literal(GSRButtonParameters.TITLE_GSR_CONTROLS),
                        b -> GSRScreens.openControls(screen))
                .dimensions(rightX, rowY, halfW, btnH)
                .build();
    }

    /**
     * Re-applies pause layout after refreshWidgetPositions.
     */
    public static void reapplyLayout(GameMenuScreen screen) {
        if (findGsrSquareButton(screen) != null) {
            applySquarePlacement(screen);
            return;
        }
        reapplySplitLayout(screen);
    }

    private static boolean isCompactSquareRow(List<ClickableWidget> squares) {
        if (squares.size() < 2) return false;
        List<ClickableWidget> ordered = squares.stream()
                .sorted(Comparator.comparingInt(ClickableWidget::getX))
                .toList();
        for (int i = 1; i < ordered.size(); i++) {
            int gap = ordered.get(i).getX() - (ordered.get(i - 1).getX() + ordered.get(i - 1).getWidth());
            if (gap < 0 || gap > 8) return false;
        }
        return true;
    }

    /** Park GSR to the right of the target row; fall back to the left, then below, if it would clip. */
    private static void parkBesideExistingRow(net.minecraft.client.gui.screen.Screen screen, ClickableWidget gsr) {
        int targetY = resolveTargetRowY(screen, gsr);
        List<ClickableWidget> row = widgetsOnRow(screen, gsr, targetY);
        if (row.isEmpty()) {
            row = densestButtonRow(screen, gsr);
        }
        if (row.isEmpty()) {
            gsr.setPosition(Math.max(2, screen.width / 2 - SQUARE_SIZE / 2), Math.max(2, screen.height / 4));
            gsr.setDimensions(SQUARE_SIZE, SQUARE_SIZE);
            return;
        }
        row.sort(Comparator.comparingInt(ClickableWidget::getX));
        ClickableWidget leftmost = row.get(0);
        ClickableWidget rightmost = row.get(row.size() - 1);
        int y = rightmost.getY() + (rightmost.getHeight() - SQUARE_SIZE) / 2;
        int x = rightmost.getX() + rightmost.getWidth() + SQUARE_GAP;
        if (x + SQUARE_SIZE > screen.width - 2) {
            x = leftmost.getX() - SQUARE_GAP - SQUARE_SIZE;
        }
        if (x < 2) {
            int minX = leftmost.getX();
            int maxX = rightmost.getX() + rightmost.getWidth();
            x = Math.max(2, (minX + maxX) / 2 - SQUARE_SIZE / 2);
            y = rightmost.getY() + rightmost.getHeight() + SQUARE_GAP;
        }
        gsr.setPosition(x, y);
        gsr.setDimensions(SQUARE_SIZE, SQUARE_SIZE);
    }

    private static int resolveTargetRowY(net.minecraft.client.gui.screen.Screen screen, ClickableWidget gsr) {
        List<ClickableWidget> vanilla = collectVanillaSquareRow(screen);
        if (!vanilla.isEmpty()) return vanilla.get(0).getY();
        if (screen instanceof GameMenuScreen gameMenu) {
            ButtonWidget exit = ((GSRGameMenuScreenAccessor) gameMenu).gsr$getExitButton();
            if (exit != null) return exit.getY();
        }
        List<ClickableWidget> densest = densestButtonRow(screen, gsr);
        return densest.isEmpty() ? 0 : densest.get(0).getY();
    }

    private static List<ClickableWidget> widgetsOnRow(net.minecraft.client.gui.screen.Screen screen, ClickableWidget gsr, int y) {
        List<ClickableWidget> row = new ArrayList<>();
        for (ClickableWidget w : collectClickableWidgets(screen)) {
            if (w == gsr || isIgnoredLayoutWidget(w)) continue;
            if (w.getY() == y) row.add(w);
        }
        return row;
    }

    private static List<ClickableWidget> densestButtonRow(net.minecraft.client.gui.screen.Screen screen, ClickableWidget gsr) {
        List<ClickableWidget> candidates = new ArrayList<>();
        for (ClickableWidget w : collectClickableWidgets(screen)) {
            if (w == gsr || isIgnoredLayoutWidget(w)) continue;
            candidates.add(w);
        }
        if (candidates.isEmpty()) return candidates;
        candidates.sort(Comparator.comparingInt(ClickableWidget::getY).thenComparingInt(ClickableWidget::getX));
        int bestY = candidates.get(0).getY();
        int bestCount = 0;
        int currentY = candidates.get(0).getY();
        int currentCount = 0;
        for (ClickableWidget w : candidates) {
            if (w.getY() == currentY) {
                currentCount++;
            } else {
                if (currentCount > bestCount || (currentCount == bestCount && currentY > bestY)) {
                    bestCount = currentCount;
                    bestY = currentY;
                }
                currentY = w.getY();
                currentCount = 1;
            }
        }
        if (currentCount > bestCount || (currentCount == bestCount && currentY > bestY)) {
            bestY = currentY;
        }
        List<ClickableWidget> row = new ArrayList<>();
        for (ClickableWidget w : candidates) {
            if (w.getY() == bestY) row.add(w);
        }
        return row;
    }

    private static boolean isIgnoredLayoutWidget(ClickableWidget w) {
        return w.getHeight() <= 12;
    }

    private static void reapplySplitLayout(GameMenuScreen screen) {
        ButtonWidget exitBtn = ((GSRGameMenuScreenAccessor) screen).gsr$getExitButton();
        if (exitBtn == null) return;

        ButtonWidget gsrBtn = findGsrControlsButton(screen);
        if (gsrBtn == null) return;

        int leftX = exitBtn.getX();
        int totalW = exitBtn.getWidth();
        int rowY = exitBtn.getY();
        int btnH = exitBtn.getHeight();
        int gap = measureGridColumnGap(screen);
        int halfW = (totalW - gap) / 2;
        int rightX = leftX + halfW + gap;

        exitBtn.setPosition(leftX, rowY);
        exitBtn.setDimensions(halfW, btnH);
        gsrBtn.setPosition(rightX, rowY);
        gsrBtn.setDimensions(halfW, btnH);
    }

    private static ButtonWidget findGsrControlsButton(net.minecraft.client.gui.screen.Screen screen) {
        for (ClickableWidget cw : collectClickableWidgets(screen)) {
            if (cw instanceof ButtonWidget bw
                    && bw.getMessage().getString().equals(GSRButtonParameters.TITLE_GSR_CONTROLS)) {
                return bw;
            }
        }
        return null;
    }

    private static int measureGridColumnGap(GameMenuScreen screen) {
        List<ClickableWidget> buttons = collectClickableWidgets(screen);
        List<ClickableWidget> buttonList = buttons.stream()
                .filter(ButtonWidget.class::isInstance)
                .sorted(Comparator.comparingInt(ClickableWidget::getY).thenComparingInt(ClickableWidget::getX))
                .toList();

        int lastY = Integer.MIN_VALUE;
        ClickableWidget prev = null;
        for (ClickableWidget btn : buttonList) {
            int y = btn.getY();
            if (y == lastY && prev != null) {
                int gap = btn.getX() - (prev.getX() + prev.getWidth());
                if (gap >= 0) return gap;
            }
            lastY = y;
            prev = btn;
        }
        return ((GSRGameMenuScreenAccessor) screen).gsr$getGridMargin();
    }

    private static int measureSquareGap(List<ClickableWidget> squares) {
        if (squares.size() < 2) return SQUARE_GAP;
        List<ClickableWidget> ordered = squares.stream()
                .sorted(Comparator.comparingInt(ClickableWidget::getX))
                .toList();
        for (int i = 1; i < ordered.size(); i++) {
            int gap = ordered.get(i).getX() - (ordered.get(i - 1).getX() + ordered.get(i - 1).getWidth());
            if (gap > 0 && gap <= 8) return gap;
        }
        return SQUARE_GAP;
    }

    private static List<ClickableWidget> collectVanillaSquareRow(net.minecraft.client.gui.screen.Screen screen) {
        List<ClickableWidget> all = collectClickableWidgets(screen);
        List<ClickableWidget> candidates = new ArrayList<>();
        for (ClickableWidget w : all) {
            if (isVanillaSquareIconButton(w)) candidates.add(w);
        }
        if (candidates.isEmpty()) return candidates;

        candidates.sort(Comparator.comparingInt(ClickableWidget::getY).thenComparingInt(ClickableWidget::getX));
        int bestY = candidates.get(0).getY();
        int bestCount = 0;
        int currentY = candidates.get(0).getY();
        int currentCount = 0;
        for (ClickableWidget w : candidates) {
            if (w.getY() == currentY) {
                currentCount++;
            } else {
                if (currentCount > bestCount) {
                    bestCount = currentCount;
                    bestY = currentY;
                }
                currentY = w.getY();
                currentCount = 1;
            }
        }
        if (currentCount > bestCount) {
            bestY = currentY;
        }

        List<ClickableWidget> row = new ArrayList<>();
        for (ClickableWidget w : candidates) {
            if (w.getY() == bestY) row.add(w);
        }
        return row;
    }

    private static boolean isVanillaSquareIconButton(ClickableWidget w) {
        if (w instanceof GSRSquareMenuButton) return false;
        if (w.getWidth() != w.getHeight()) return false;
        if (w.getWidth() < 16 || w.getWidth() > 24) return false;
        return w instanceof TextIconButtonWidget;
    }

    private static ClickableWidget findGsrSquareButton(net.minecraft.client.gui.screen.Screen screen) {
        for (ClickableWidget cw : collectClickableWidgets(screen)) {
            if (cw instanceof GSRSquareMenuButton
                    || (cw instanceof ButtonWidget bw
                    && bw.getMessage().getString().equals(GSRButtonParameters.TITLE_GSR_SQUARE))) {
                return cw;
            }
        }
        return null;
    }

    private static List<ClickableWidget> collectClickableWidgets(Element parent) {
        List<ClickableWidget> out = new ArrayList<>();
        collectClickableWidgetsRecursive(parent, out);
        return out;
    }

    private static void collectClickableWidgetsRecursive(Element e, List<ClickableWidget> out) {
        if (e instanceof ClickableWidget cw) out.add(cw);
        if (e instanceof ParentElement pe) {
            for (Element child : pe.children()) {
                collectClickableWidgetsRecursive(child, out);
            }
        }
    }
}
