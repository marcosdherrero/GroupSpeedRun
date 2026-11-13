package net.berkle.groupspeedrun.gui;

// Minecraft screen and drawing
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

// GSR run loading and data
import net.berkle.groupspeedrun.client.GSRSharedRunLoader;
import net.berkle.groupspeedrun.data.GSRRunRecord;
import net.berkle.groupspeedrun.data.GSRRunSaveState;
import net.berkle.groupspeedrun.gui.components.GSRMenuComponents;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdown;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdownBehavior;
import net.berkle.groupspeedrun.gui.runhistory.GSRExportCsvScreenModel;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryDetailPanel;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryLayout;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryLeftColumn;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryScreenModel;
import net.berkle.groupspeedrun.mixin.accessors.GSRPressableWidgetAccessor;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRRunHistoryCsvExport;
import net.berkle.groupspeedrun.util.GSRScrollbarHelper;
import net.berkle.groupspeedrun.util.GSRStatusText;

// GLFW key codes
import com.mojang.blaze3d.platform.InputConstants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Full-screen for configuring CSV export: two-column layout (filters left, summary right).
 * Matches Run History menu style. Exports from merged personal and shared run files.
 */
public class GSRExportCsvScreen extends Screen {

    private final Screen parent;
    /** Which scrollbar is being dragged; null when none. */
    private ExportScrollbarId scrollbarDragging = null;

    private enum ExportScrollbarId { FILTER, PLAYER_COUNT, RUNS, RIGHT_PANEL }
    private final GSRExportCsvScreenModel model = new GSRExportCsvScreenModel();
    private final GSRTickerState tickerState = new GSRTickerState();
    /** When non-null, export uses this run history state (filters and runs) instead of re-loading. */
    private final GSRRunHistoryScreenModel initialFilters;

    private final GSRMultiSelectDropdown<GSRRunHistoryScreenModel> runsDropdown;
    private final GSRMultiSelectDropdown<GSRRunHistoryScreenModel> playerCountDropdown;
    private final GSRMultiSelectDropdown<GSRRunHistoryScreenModel> filterDropdown;

    /** Convenience constructor when no initial filters (e.g. opened from elsewhere). */
    public GSRExportCsvScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * @param parent Parent screen (typically Run History).
     * @param initialFilters Optional; when provided, copies player and player-count filters from run history.
     */
    public GSRExportCsvScreen(Screen parent, GSRRunHistoryScreenModel initialFilters) {
        super(Component.literal("Export CSV"));
        this.parent = parent;
        this.initialFilters = initialFilters;
        if (initialFilters != null) {
            model.selectedPlayerFilter = new HashSet<>(initialFilters.selectedPlayerFilter);
            model.selectedPlayerCountFilter = new HashSet<>(initialFilters.selectedPlayerCountFilter);
        }
        this.runsDropdown = new GSRMultiSelectDropdown<>("Runs:", "Runs – select runs to export", "dd-export-runs", true,
                new GSRMultiSelectDropdownBehavior<>() {
                    @Override
                    public List<String> getItems(GSRRunHistoryScreenModel m) {
                        List<String> items = new ArrayList<>();
                        items.add(GSRRunHistoryParameters.RUNS_PRESET_LAST_5_LABEL);
                        items.add(GSRRunHistoryParameters.RUNS_PRESET_LAST_10_LABEL);
                        items.add(GSRRunHistoryParameters.RUNS_PRESET_LAST_20_LABEL);
                        items.addAll(m.runs.stream().map(GSRExportCsvScreen::formatRunLabel).toList());
                        return items;
                    }

                    @Override
                    public Set<Integer> getSelectedIndices(GSRRunHistoryScreenModel m) {
                        GSRExportCsvScreenModel em = (GSRExportCsvScreenModel) m;
                        Set<Integer> indices = new HashSet<>();
                        for (int i : em.exportSelectedRunIndices) {
                            indices.add(i + GSRExportCsvScreen.RUNS_PRESET_COUNT);
                        }
                        return indices;
                    }

                    @Override
                    public String getDisplayLabel(GSRRunHistoryScreenModel m) {
                        GSRExportCsvScreenModel em = (GSRExportCsvScreenModel) m;
                        if (em.exportSelectedRunIndices.isEmpty()) return "All (" + em.runs.size() + ")";
                        return em.exportSelectedRunIndices.size() + " run(s) selected";
                    }

                    @Override
                    public long getSelectionTimeMs(GSRRunHistoryScreenModel m) {
                        return 0;
                    }
                });
        this.playerCountDropdown = new GSRMultiSelectDropdown<>("Player Count:", "Player Count – filter by party size", "dd-export-playercount", true,
                new GSRMultiSelectDropdownBehavior<>() {
                    @Override
                    public List<String> getItems(GSRRunHistoryScreenModel m) {
                        return m.allPlayerCounts.stream()
                                .map(c -> c == 1 ? "1 player" : c + " players")
                                .toList();
                    }

                    @Override
                    public Set<Integer> getSelectedIndices(GSRRunHistoryScreenModel m) {
                        Set<Integer> indices = new HashSet<>();
                        for (int i = 0; i < m.allPlayerCounts.size(); i++) {
                            if (m.pendingPlayerCountFilter.contains(m.allPlayerCounts.get(i))) indices.add(i);
                        }
                        return indices;
                    }

                    @Override
                    public String getDisplayLabel(GSRRunHistoryScreenModel m) {
                        return m.pendingPlayerCountFilter.isEmpty() ? "All (" + m.allPlayerCounts.size() + ")" : m.pendingPlayerCountFilter.size() + " selected";
                    }

                    @Override
                    public long getSelectionTimeMs(GSRRunHistoryScreenModel m) {
                        return 0;
                    }
                });
        this.filterDropdown = new GSRMultiSelectDropdown<>("Player Filter:", "Player Filter – select players", "dd-export-filter", true,
                new GSRMultiSelectDropdownBehavior<>() {
                    @Override
                    public List<String> getItems(GSRRunHistoryScreenModel m) {
                        return m.allPlayerNames;
                    }

                    @Override
                    public Set<Integer> getSelectedIndices(GSRRunHistoryScreenModel m) {
                        Set<Integer> indices = new HashSet<>();
                        for (int i = 0; i < m.allPlayerNames.size(); i++) {
                            if (m.pendingPlayerFilter.contains(m.allPlayerNames.get(i))) indices.add(i);
                        }
                        return indices;
                    }

                    @Override
                    public String getDisplayLabel(GSRRunHistoryScreenModel m) {
                        return m.pendingPlayerFilter.isEmpty() ? "All (" + m.allPlayerNames.size() + ")" : m.pendingPlayerFilter.size() + " selected";
                    }

                    @Override
                    public long getSelectionTimeMs(GSRRunHistoryScreenModel m) {
                        return 0;
                    }
                });
    }

    @Override
    protected void init() {
        super.init();
        var footer = GSRMenuComponents.footerLayout(width, height, GSRRunHistoryParameters.RUN_HISTORY_FOOTER_Y_OFFSET);
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.FOOTER_BACK, this::cancel,
                footer.leftX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()));
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.EXPORT_CSV_EXPORT, this::doExport,
                footer.rightX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()));
        if (initialFilters != null) {
            model.allRuns = new ArrayList<>(initialFilters.allRuns);
            model.allPlayerNames = new ArrayList<>(initialFilters.allPlayerNames);
            model.allPlayerCounts = new ArrayList<>(initialFilters.allPlayerCounts);
            model.runs = new ArrayList<>(initialFilters.runs);
            model.pendingPlayerFilter = new HashSet<>(initialFilters.selectedPlayerFilter);
            model.pendingPlayerCountFilter = new HashSet<>(initialFilters.selectedPlayerCountFilter);
            model.selectedPlayerFilter = new HashSet<>(initialFilters.selectedPlayerFilter);
            model.selectedPlayerCountFilter = new HashSet<>(initialFilters.selectedPlayerCountFilter);
            applyRunSelectionFromHistory(initialFilters);
        } else {
            model.allRuns = GSRSharedRunLoader.loadAll();
            model.allPlayerNames = collectUniquePlayerNames(model.allRuns);
            model.allPlayerCounts = collectDistinctPlayerCounts(model.allRuns);
            model.pendingPlayerFilter = new HashSet<>(model.selectedPlayerFilter);
            model.pendingPlayerCountFilter = new HashSet<>(model.selectedPlayerCountFilter);
            applyPlayerFilter();
        }
    }

    /** Pre-selects runs in export based on run history selection. Uses unified selectedRuns for all tabs. */
    private void applyRunSelectionFromHistory(GSRRunHistoryScreenModel history) {
        model.exportSelectedRunIndices.clear();
        Set<GSRRunSaveState> selected = history.selectedRuns.isEmpty() ? new HashSet<>(history.runs) : new HashSet<>(history.selectedRuns);
        for (int i = 0; i < model.runs.size(); i++) {
            if (selected.contains(model.runs.get(i))) model.exportSelectedRunIndices.add(i);
        }
    }

    /** Collects distinct participant counts from runs (1, 2, 4, etc.), sorted ascending. */
    private List<Integer> collectDistinctPlayerCounts(List<GSRRunSaveState> runList) {
        return runList.stream()
                .mapToInt(r -> Math.max(1, r.record().participantCount()))
                .distinct()
                .sorted()
                .boxed()
                .toList();
    }

    /** Collects unique player names from participants and snapshots across runs; sorted alphabetically. */
    private List<String> collectUniquePlayerNames(List<GSRRunSaveState> runList) {
        Set<String> names = new LinkedHashSet<>();
        for (GSRRunSaveState state : runList) {
            for (var p : state.participants()) {
                if (p.playerName() != null && !p.playerName().isBlank()) names.add(p.playerName());
            }
            for (var s : state.snapshots()) {
                if (s.playerName() != null && !s.playerName().isBlank()) names.add(s.playerName());
            }
        }
        List<String> list = new ArrayList<>(names);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /** Filters runs by player and player-count filters; updates selected and clears export indices. */
    private void applyPlayerFilter() {
        List<GSRRunSaveState> filtered = new ArrayList<>(model.allRuns);
        if (!model.pendingPlayerFilter.isEmpty()) {
            filtered = filtered.stream()
                    .filter(r -> runHasAnyPlayer(r, model.pendingPlayerFilter))
                    .toList();
        }
        if (!model.pendingPlayerCountFilter.isEmpty()) {
            filtered = filtered.stream()
                    .filter(r -> model.pendingPlayerCountFilter.contains(Math.max(1, r.record().participantCount())))
                    .toList();
        }
        model.runs = new ArrayList<>(filtered);
        model.runs.sort(Comparator.comparingLong((GSRRunSaveState r) -> r.record().endMs()).reversed());
        model.selectedPlayerFilter = new HashSet<>(model.pendingPlayerFilter);
        model.selectedPlayerCountFilter = new HashSet<>(model.pendingPlayerCountFilter);
        model.exportSelectedRunIndices.clear();
    }

    /** Returns true if the run has any of the given players in participants or snapshots. */
    private static boolean runHasAnyPlayer(GSRRunSaveState run, Set<String> players) {
        for (var p : run.participants()) {
            if (players.contains(p.playerName())) return true;
        }
        for (var s : run.snapshots()) {
            if (players.contains(s.playerName())) return true;
        }
        return false;
    }

    /** Formats run for dropdown label: status icon, world name, date. */
    private static String formatRunLabel(GSRRunSaveState state) {
        GSRRunRecord r = state.record();
        String date = r.endDateIso();
        if (date != null && date.length() >= 10) date = date.substring(0, 10);
        else date = "?";
        String status;
        if (GSRRunRecord.STATUS_ACTIVE.equals(r.status())) {
            status = GSRRunHistoryParameters.STATUS_ICON_ACTIVE;
        } else if (GSRRunRecord.STATUS_VICTORY.equals(r.status())) {
            status = GSRRunHistoryParameters.STATUS_ICON_VICTORY;
        } else {
            status = GSRRunHistoryParameters.STATUS_ICON_FAIL;
        }
        return status + " " + (r.worldName() != null ? r.worldName() : "?") + " " + date;
    }

    /** Number of preset items at top of Runs dropdown (Last 5, Last 10, Last 20). */
    public static final int RUNS_PRESET_COUNT = 3;

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, GSRUiParameters.SCREEN_BG_DARK);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, getTitle(), width / 2, GSRRunHistoryParameters.RUN_HISTORY_TITLE_Y, GSRUiParameters.TITLE_COLOR);

        boolean dropdownOpen = model.runsDropdownOpen || model.playerCountDropdownOpen || model.filterDropdownOpen;
        if (dropdownOpen) {
            context.fill(0, 0, width, height, GSRUiParameters.DROPDOWN_OVERLAY_DIM);
        }

        var bounds = GSRRunHistoryLayout.ExportCsvBounds.compute(width, height);
        int leftPanelLeft = bounds.leftPanelLeft();
        int leftPanelWidth = bounds.leftPanelWidth();
        int leftPanelTop = bounds.leftPanelTop();
        int leftPanelBottom = bounds.leftPanelBottom();
        int rightPanelLeft = bounds.rightPanelLeft();
        int rightPanelWidth = bounds.rightPanelWidth();
        int rightPanelTop = bounds.rightPanelTop();
        int rightPanelBottom = bounds.rightPanelBottom();
        int overlayLeft = bounds.overlayLeft();
        int overlayWidth = bounds.overlayWidth();
        int barHeight = bounds.barHeight();
        int[] st = bounds.sectionTops();
        int[] bt = new int[] { bounds.barTop(0), bounds.barTop(1), bounds.barTop(2) };
        int barLeft = leftPanelLeft + GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;
        int barDrawWidth = leftPanelWidth - 2 * GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;

        if (dropdownOpen) {
            context.fill(leftPanelLeft, leftPanelTop, leftPanelLeft + leftPanelWidth, leftPanelBottom, GSRUiParameters.CONTENT_BOX_BG);
            int listTop = leftPanelTop + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
            int selectionListBottom = bounds.selectionListBottom();
            int delimiterTop = selectionListBottom + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT;
            int listBottom = delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
            if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                filterDropdown.renderOverlay(model, context, font, tickerState, overlayLeft, listTop, listBottom, overlayWidth,
                        model.filterDropdownScroll, mouseX, mouseY);
                context.fill(overlayLeft, delimiterTop, overlayLeft + overlayWidth,
                        delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT,
                        GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_COLOR);
                renderMakeSelectionButton(context, overlayLeft, overlayWidth, leftPanelBottom, mouseX, mouseY, hasFilterPendingChanges());
            } else if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                playerCountDropdown.renderOverlay(model, context, font, tickerState, overlayLeft, listTop, listBottom, overlayWidth,
                        model.playerCountDropdownScroll, mouseX, mouseY);
                context.fill(overlayLeft, delimiterTop, overlayLeft + overlayWidth,
                        delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT,
                        GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_COLOR);
                renderMakeSelectionButton(context, overlayLeft, overlayWidth, leftPanelBottom, mouseX, mouseY, hasPlayerCountPendingChanges());
            } else if (model.runsDropdownOpen && (model.runs.size() + RUNS_PRESET_COUNT > 0)) {
                runsDropdown.renderOverlay(model, context, font, tickerState, overlayLeft, listTop, listBottom, overlayWidth,
                        model.runsDropdownScroll, mouseX, mouseY);
                context.fill(overlayLeft, delimiterTop, overlayLeft + overlayWidth,
                        delimiterTop + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT,
                        GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_COLOR);
                renderMakeSelectionButton(context, overlayLeft, overlayWidth, leftPanelBottom, mouseX, mouseY, false);
            }
        } else {
            context.fill(leftPanelLeft, leftPanelTop, leftPanelLeft + leftPanelWidth, leftPanelBottom, GSRUiParameters.CONTENT_BOX_BG);
            int iconTop = bounds.iconTop();
            int iconHeight = bounds.iconHeight();
            int iconLeft = bounds.iconLeft();
            int iconWidth = bounds.iconWidth();
            if (iconHeight > 0 && iconWidth > 0) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, GSRRunHistoryParameters.EXPORT_CSV_ICON_SPRITE, iconLeft, iconTop, iconWidth, iconHeight);
            }
            boolean filterBarHovered = filterDropdown.isBarHovered(barLeft, bt[0], barDrawWidth, barHeight, mouseX, mouseY);
            boolean playerCountBarHovered = !model.allPlayerCounts.isEmpty() && playerCountDropdown.isBarHovered(barLeft, bt[1], barDrawWidth, barHeight, mouseX, mouseY);
            boolean runsBarHovered = runsDropdown.isBarHovered(barLeft, bt[2], barDrawWidth, barHeight, mouseX, mouseY);
            filterDropdown.renderTrigger(model, context, font, tickerState, leftPanelLeft, st[0], bt[0],
                    leftPanelWidth, barHeight, GSRRunHistoryParameters.LEFT_COLUMN_LABEL_SCALE, false, filterBarHovered);
            if (!model.allPlayerCounts.isEmpty()) {
                playerCountDropdown.renderTrigger(model, context, font, tickerState, leftPanelLeft, st[1], bt[1],
                        leftPanelWidth, barHeight, GSRRunHistoryParameters.LEFT_COLUMN_LABEL_SCALE, false, playerCountBarHovered);
            }
            runsDropdown.renderTrigger(model, context, font, tickerState, leftPanelLeft, st[2], bt[2],
                    leftPanelWidth, barHeight, GSRRunHistoryParameters.LEFT_COLUMN_LABEL_SCALE, false, runsBarHovered);
        }

        context.fill(rightPanelLeft, rightPanelTop, rightPanelLeft + rightPanelWidth, rightPanelBottom, GSRUiParameters.CONTENT_BOX_BG);
        int contentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentTop = rightPanelTop + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentRight = rightPanelLeft + rightPanelWidth - GSRRunHistoryParameters.CONTENT_PADDING;
        int contentBottom = rightPanelBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
        int contentHeight = contentBottom - contentTop;
        List<GSRRunSaveState> runsToShow = getRunsForExportPreview();
        if (runsToShow.isEmpty()) {
            context.centeredText(font, "No runs to export",
                    (contentLeft + contentRight) / 2,
                    contentTop + contentHeight / 2 - font.lineHeight / 2,
                    GSRRunHistoryParameters.EMPTY_MESSAGE_COLOR);
        } else {
            String avgText = GSRStatusText.buildAverageRunInfo(runsToShow);
            int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeightFromText(avgText);
            int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
            model.exportDetailScroll = Math.min(model.exportDetailScroll, maxDetailScroll);
            int textLeft = contentLeft;
            if (maxDetailScroll > 0) {
                int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
                GSRScrollbarHelper.drawScrollbar(context, contentLeft, contentTop, contentHeight,
                        model.exportDetailScroll, maxDetailScroll, GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT);
                textLeft = contentLeft + sbWidth + GSRRunHistoryParameters.LIST_TEXT_INSET;
            }
            GSRRunHistoryDetailPanel.renderRunInfoFromText(context, font, avgText,
                    textLeft, contentTop, contentRight, contentBottom, model.exportDetailScroll);
        }
    }

    /** Returns runs used for export preview (selected or all). */
    private List<GSRRunSaveState> getRunsForExportPreview() {
        if (model.exportSelectedRunIndices.isEmpty()) return model.runs;
        List<GSRRunSaveState> out = new ArrayList<>();
        for (int i : model.exportSelectedRunIndices) {
            if (i >= 0 && i < model.runs.size()) out.add(model.runs.get(i));
        }
        return out;
    }

    private void renderMakeSelectionButton(GuiGraphicsExtractor context, int listLeft, int listWidth, int overlayBottom, int mouseX, int mouseY, boolean hasPendingChanges) {
        int selectionListBottom = overlayBottom - GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN
                - GSRRunHistoryParameters.MAKE_SELECTION_CONTAINER_HEIGHT;
        int confirmButtonTop = selectionListBottom + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT + GSRRunHistoryParameters.SELECTION_DELIMITER_BUTTON_GAP;
        int confirmLeft = listLeft + GSRRunHistoryParameters.CONTAINER_INSET;
        int confirmWidth = listWidth - 2 * GSRRunHistoryParameters.CONTAINER_INSET;
        boolean confirmHover = mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= confirmButtonTop && mouseY < confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT;
        var textures = GSRPressableWidgetAccessor.gsr$getTextures();
        context.blitSprite(RenderPipelines.GUI_TEXTURED, textures.get(true, confirmHover), confirmLeft, confirmButtonTop, confirmWidth, GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT);
        if (hasPendingChanges) {
            float breath = (float) ((1 + Math.sin(System.currentTimeMillis() * Math.PI * 2 / GSRRunHistoryParameters.MAKE_SELECTION_BREATHE_PERIOD_MS)) / 2);
            float alpha = GSRRunHistoryParameters.MAKE_SELECTION_BREATHE_ALPHA_MIN
                    + (GSRRunHistoryParameters.MAKE_SELECTION_BREATHE_ALPHA_MAX - GSRRunHistoryParameters.MAKE_SELECTION_BREATHE_ALPHA_MIN) * breath;
            int glowColor = GSRColorHelper.applyAlpha(GSRRunHistoryParameters.MAKE_SELECTION_BREATHE_GLOW_COLOR, alpha);
            int border = GSRRunHistoryParameters.MAKE_SELECTION_GLOW_BORDER;
            context.fill(confirmLeft - border, confirmButtonTop - border, confirmLeft + confirmWidth + border, confirmButtonTop, glowColor);
            context.fill(confirmLeft - border, confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT, confirmLeft + confirmWidth + border, confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT + border, glowColor);
            context.fill(confirmLeft - border, confirmButtonTop, confirmLeft, confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT, glowColor);
            context.fill(confirmLeft + confirmWidth, confirmButtonTop, confirmLeft + confirmWidth + border, confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT, glowColor);
        }
        int confirmCenterX = listLeft + listWidth / 2;
        int confirmTextY = confirmButtonTop + (GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT - font.lineHeight) / 2;
        context.centeredText(font, GSRMultiSelectDropdown.CONFIRM_BUTTON_TEXT, confirmCenterX, confirmTextY, GSRRunHistoryParameters.TEXT_COLOR);
    }

    /** True when pending player filter differs from confirmed. */
    private boolean hasFilterPendingChanges() {
        return !model.pendingPlayerFilter.equals(model.selectedPlayerFilter);
    }

    /** True when pending player-count filter differs from confirmed. */
    private boolean hasPlayerCountPendingChanges() {
        return !model.pendingPlayerCountFilter.equals(model.selectedPlayerCountFilter);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean captured) {
        double mouseX = click.x();
        double mouseY = click.y();
        long now = System.currentTimeMillis();

        var bounds = GSRRunHistoryLayout.ExportCsvBounds.compute(width, height);
        int leftPanelLeft = bounds.leftPanelLeft();
        int leftPanelRight = leftPanelLeft + bounds.leftPanelWidth();
        int leftPanelTop = bounds.leftPanelTop();
        int leftPanelBottom = bounds.leftPanelBottom();
        int overlayLeft = bounds.overlayLeft();
        int overlayWidth = bounds.overlayWidth();
        int barHeight = bounds.barHeight();
        int[] bt = new int[] { bounds.barTop(0), bounds.barTop(1), bounds.barTop(2) };
        int barLeft = leftPanelLeft + GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;
        int barDrawWidth = bounds.leftPanelWidth() - 2 * GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;
        int listTop = leftPanelTop + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
        int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
        boolean dropdownOpen = model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen;
        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        boolean inOverlayArea = mouseX >= leftPanelLeft && mouseX < leftPanelRight + (dropdownOpen ? sbWidth : 0) && mouseY >= listTop && mouseY < leftPanelBottom;

        if (inOverlayArea) {
            if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) {
                return true;
            }
            int trackX = overlayLeft + overlayWidth - sbWidth;
            if (GSRScrollbarHelper.isInScrollbarHitArea((int) mouseX, trackX, sbWidth) && click.button() == InputConstants.MOUSE_BUTTON_LEFT && dropdownOpen) {
                if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, filterDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, filterDropdown.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int overlayListTop = geom.listTop();
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) ((listAreaBottom - overlayListTop) * ((listAreaBottom - overlayListTop) / (double) ((listAreaBottom - overlayListTop) + geom.maxScroll()))));
                        scrollbarDragging = ExportScrollbarId.FILTER;
                        model.filterDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, playerCountDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, playerCountDropdown.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int overlayListTop = geom.listTop();
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) ((listAreaBottom - overlayListTop) * ((listAreaBottom - overlayListTop) / (double) ((listAreaBottom - overlayListTop) + geom.maxScroll()))));
                        scrollbarDragging = ExportScrollbarId.PLAYER_COUNT;
                        model.playerCountDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.runsDropdownOpen && (model.runs.size() + RUNS_PRESET_COUNT > 0)) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, runsDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, runsDropdown.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int overlayListTop = geom.listTop();
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) ((listAreaBottom - overlayListTop) * ((listAreaBottom - overlayListTop) / (double) ((listAreaBottom - overlayListTop) + geom.maxScroll()))));
                        scrollbarDragging = ExportScrollbarId.RUNS;
                        model.runsDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
            }
            if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, filterDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, filterDropdown.getItemCount(model));
                if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                    applyPlayerFilter();
                    model.filterDropdownOpen = false;
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, filterDropdown.getItemCount(model), model.filterDropdownScroll, overlayLeft, overlayWidth, (int) mouseX, (int) mouseY);
                if (itemIdx >= 0) {
                    if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                        model.pendingPlayerFilter.addAll(model.allPlayerNames);
                    } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                        model.pendingPlayerFilter.clear();
                    } else {
                        int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                        String name = model.allPlayerNames.get(dataIndex);
                        if (model.pendingPlayerFilter.contains(name)) model.pendingPlayerFilter.remove(name);
                        else model.pendingPlayerFilter.add(name);
                    }
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                return true;
            }
            if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, playerCountDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, playerCountDropdown.getItemCount(model));
                if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                    applyPlayerFilter();
                    model.playerCountDropdownOpen = false;
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, playerCountDropdown.getItemCount(model), model.playerCountDropdownScroll, overlayLeft, overlayWidth, (int) mouseX, (int) mouseY);
                if (itemIdx >= 0) {
                    if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                        model.pendingPlayerCountFilter.addAll(model.allPlayerCounts);
                    } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                        model.pendingPlayerCountFilter.clear();
                    } else {
                        int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                        int count = model.allPlayerCounts.get(dataIndex);
                        if (model.pendingPlayerCountFilter.contains(count)) model.pendingPlayerCountFilter.remove(count);
                        else model.pendingPlayerCountFilter.add(count);
                    }
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                return true;
            }
            if (model.runsDropdownOpen && (model.runs.size() + RUNS_PRESET_COUNT > 0)) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, runsDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, runsDropdown.getItemCount(model));
                if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                    model.runsDropdownOpen = false;
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, runsDropdown.getItemCount(model), model.runsDropdownScroll, overlayLeft, overlayWidth, (int) mouseX, (int) mouseY);
                if (itemIdx >= 0) {
                    int dataCount = model.runs.size();
                    int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                    if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                        model.exportSelectedRunIndices.clear();
                        for (int i = 0; i < dataCount; i++) model.exportSelectedRunIndices.add(i);
                    } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                        model.exportSelectedRunIndices.clear();
                    } else if (dataIndex < RUNS_PRESET_COUNT) {
                        int n = GSRRunHistoryLeftColumn.getRunsPresetCount(dataIndex);
                        model.exportSelectedRunIndices.clear();
                        for (int i = 0; i < Math.min(n, dataCount); i++) {
                            model.exportSelectedRunIndices.add(i);
                        }
                        model.runsDropdownOpen = false;
                    } else {
                        int runIdx = dataIndex - RUNS_PRESET_COUNT;
                        if (model.exportSelectedRunIndices.contains(runIdx)) {
                            model.exportSelectedRunIndices.remove(runIdx);
                        } else {
                            model.exportSelectedRunIndices.add(runIdx);
                        }
                    }
                    model.lastClickHandledTimeMs = now;
                    return true;
                }
                return true;
            }
        }

        if (mouseY >= bt[0] && mouseY < bt[0] + barHeight && mouseX >= barLeft && mouseX < barLeft + barDrawWidth) {
            if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
            model.playerCountDropdownOpen = false;
            model.runsDropdownOpen = false;
            model.filterDropdownOpen = !model.filterDropdownOpen;
            if (model.filterDropdownOpen) {
                model.filterDropdownScroll = 0;
                model.pendingPlayerFilter = new HashSet<>(model.selectedPlayerFilter);
            }
            model.lastClickHandledTimeMs = now;
            return true;
        }
        if (!model.allPlayerCounts.isEmpty() && mouseY >= bt[1] && mouseY < bt[1] + barHeight && mouseX >= barLeft && mouseX < barLeft + barDrawWidth) {
            if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
            model.filterDropdownOpen = false;
            model.runsDropdownOpen = false;
            model.playerCountDropdownOpen = !model.playerCountDropdownOpen;
            if (model.playerCountDropdownOpen) {
                model.playerCountDropdownScroll = 0;
                model.pendingPlayerCountFilter = new HashSet<>(model.selectedPlayerCountFilter);
            }
            model.lastClickHandledTimeMs = now;
            return true;
        }
        if (mouseY >= bt[2] && mouseY < bt[2] + barHeight && mouseX >= barLeft && mouseX < barLeft + barDrawWidth) {
            if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
            model.filterDropdownOpen = false;
            model.playerCountDropdownOpen = false;
            model.runsDropdownOpen = !model.runsDropdownOpen;
            if (model.runsDropdownOpen) model.runsDropdownScroll = 0;
            model.lastClickHandledTimeMs = now;
            return true;
        }

        if (model.runsDropdownOpen || model.playerCountDropdownOpen || model.filterDropdownOpen) {
            if (mouseX < leftPanelLeft || mouseX >= leftPanelRight + sbWidth || mouseY < leftPanelTop || mouseY >= leftPanelBottom) {
                model.runsDropdownOpen = false;
                model.playerCountDropdownOpen = false;
                model.filterDropdownOpen = false;
                model.lastClickHandledTimeMs = now;
                return true;
            }
            return true;
        }

        int rightPanelLeft = bounds.rightPanelLeft();
        int rightPanelTop = bounds.rightPanelTop();
        int rightPanelBottom = bounds.rightPanelBottom();
        int detailContentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
        int detailContentTop = rightPanelTop + GSRRunHistoryParameters.CONTENT_PADDING;
        int detailContentBottom = rightPanelBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
        int contentHeight = detailContentBottom - detailContentTop;
        List<GSRRunSaveState> runsToShow = getRunsForExportPreview();
        if (!runsToShow.isEmpty() && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            String avgText = GSRStatusText.buildAverageRunInfo(runsToShow);
            int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeightFromText(avgText);
            int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
            if (maxDetailScroll > 0 && GSRScrollbarHelper.isInScrollbarHitArea((int) mouseX, detailContentLeft, sbWidth)
                    && mouseY >= detailContentTop && mouseY < detailContentBottom) {
                int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (contentHeight * (contentHeight / (double) (contentHeight + maxDetailScroll))));
                scrollbarDragging = ExportScrollbarId.RIGHT_PANEL;
                model.exportDetailScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, detailContentTop, detailContentBottom, thumbHeight, maxDetailScroll);
                return true;
            }
        }

        return super.mouseClicked(click, false);
    }

    private void doExport() {
        // Apply any pending filter changes so export reflects current UI selections
        if (hasFilterPendingChanges() || hasPlayerCountPendingChanges()) {
            applyPlayerFilter();
        }
        if (minecraft == null || model.runs.isEmpty()) {
            if (minecraft != null) {
                SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Export CSV"), Component.literal("No run data to export."));
            }
            return;
        }
        List<GSRRunSaveState> toExport;
        if (model.exportSelectedRunIndices.isEmpty()) {
            toExport = new ArrayList<>(model.runs);
        } else {
            toExport = new ArrayList<>();
            for (int i : model.exportSelectedRunIndices) {
                if (i >= 0 && i < model.runs.size()) toExport.add(model.runs.get(i));
            }
        }
        if (toExport.isEmpty()) {
            if (minecraft != null) {
                SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Export CSV"), Component.literal("No runs selected."));
            }
            return;
        }
        try {
            Path path = GSRRunHistoryCsvExport.exportToCsv(toExport);
            String msg = "Saved to " + path.getFileName();
            SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Export CSV"), Component.literal(msg));
            if (minecraft != null) minecraft.gui.setScreen(parent);
        } catch (IOException e) {
            SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                    Component.literal("Export CSV"), Component.literal("Failed: " + e.getMessage()));
        }
    }

    private void cancel() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (scrollbarDragging != null) {
            double mouseY = click.y();
            var bounds = GSRRunHistoryLayout.ExportCsvBounds.compute(width, height);
            int overlayLeft = bounds.overlayLeft();
            int overlayWidth = bounds.overlayWidth();
            int listTop = bounds.leftPanelTop() + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
            int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
            switch (scrollbarDragging) {
                case FILTER -> {
                    if (!model.allPlayerNames.isEmpty()) {
                        var geom = GSRMultiSelectDropdown.computeGeometry(font, filterDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, filterDropdown.getItemCount(model));
                        int listAreaBottom = geom.listBottom();
                        int overlayListTop = geom.listTop();
                        if (geom.maxScroll() > 0) {
                            int listAreaHeight = listAreaBottom - overlayListTop;
                            int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                            model.filterDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        }
                    }
                }
                case PLAYER_COUNT -> {
                    if (!model.allPlayerCounts.isEmpty()) {
                        var geom = GSRMultiSelectDropdown.computeGeometry(font, playerCountDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, playerCountDropdown.getItemCount(model));
                        int listAreaBottom = geom.listBottom();
                        int overlayListTop = geom.listTop();
                        if (geom.maxScroll() > 0) {
                            int listAreaHeight = listAreaBottom - overlayListTop;
                            int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                            model.playerCountDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        }
                    }
                }
                case RUNS -> {
                    if (model.runs.size() + RUNS_PRESET_COUNT > 0) {
                        var geom = GSRMultiSelectDropdown.computeGeometry(font, runsDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, runsDropdown.getItemCount(model));
                        int listAreaBottom = geom.listBottom();
                        int overlayListTop = geom.listTop();
                        if (geom.maxScroll() > 0) {
                            int listAreaHeight = listAreaBottom - overlayListTop;
                            int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                            model.runsDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        }
                    }
                }
                case RIGHT_PANEL -> {
                    var exportBounds = GSRRunHistoryLayout.ExportCsvBounds.compute(width, height);
                    int detailContentTop = exportBounds.rightPanelTop() + GSRRunHistoryParameters.CONTENT_PADDING;
                    int detailContentBottom = exportBounds.rightPanelBottom() - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
                    int contentHeight = detailContentBottom - detailContentTop;
                    List<GSRRunSaveState> runsToShow = getRunsForExportPreview();
                    if (!runsToShow.isEmpty()) {
                        String avgText = GSRStatusText.buildAverageRunInfo(runsToShow);
                        int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeightFromText(avgText);
                        int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
                        if (maxDetailScroll > 0) {
                            int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (contentHeight * (contentHeight / (double) (contentHeight + maxDetailScroll))));
                            model.exportDetailScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, detailContentTop, detailContentBottom, thumbHeight, maxDetailScroll);
                        }
                    }
                }
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (scrollbarDragging != null) {
            scrollbarDragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        var bounds = GSRRunHistoryLayout.ExportCsvBounds.compute(width, height);
        int leftPanelLeft = bounds.leftPanelLeft();
        int leftPanelRight = leftPanelLeft + bounds.leftPanelWidth();
        int leftPanelTop = bounds.leftPanelTop();
        int leftPanelBottom = bounds.leftPanelBottom();
        int overlayLeft = bounds.overlayLeft();
        int overlayWidth = bounds.overlayWidth();
        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        boolean dropdownOpen = model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen;
        boolean inOverlayArea = dropdownOpen && mouseX >= leftPanelLeft && mouseX < leftPanelRight + sbWidth && mouseY >= leftPanelTop && mouseY < leftPanelBottom;

        if (inOverlayArea) {
            int listTop = leftPanelTop + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
            int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
            if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, filterDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, filterDropdown.getItemCount(model));
                int listAreaBottom = geom.listBottom();
                if (mouseY >= geom.listTop() && mouseY < listAreaBottom) {
                    model.filterDropdownScroll = (int) Math.max(0, Math.min(model.filterDropdownScroll - verticalAmount * GSRRunHistoryParameters.FILTER_DROPDOWN_SCROLL_AMOUNT, geom.maxScroll()));
                }
                return true;
            }
            if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, playerCountDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, playerCountDropdown.getItemCount(model));
                int listAreaBottom = geom.listBottom();
                if (mouseY >= geom.listTop() && mouseY < listAreaBottom) {
                    model.playerCountDropdownScroll = (int) Math.max(0, Math.min(model.playerCountDropdownScroll - verticalAmount * GSRRunHistoryParameters.FILTER_DROPDOWN_SCROLL_AMOUNT, geom.maxScroll()));
                }
                return true;
            }
            if (model.runsDropdownOpen && (model.runs.size() + RUNS_PRESET_COUNT > 0)) {
                var geom = GSRMultiSelectDropdown.computeGeometry(font, runsDropdown.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, runsDropdown.getItemCount(model));
                if (mouseY >= geom.listTop() && mouseY < geom.listBottom()) {
                    model.runsDropdownScroll = (int) Math.max(0, Math.min(model.runsDropdownScroll - verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT, geom.maxScroll()));
                }
                return true;
            }
        }
        int rightPanelLeft = bounds.rightPanelLeft();
        int rightPanelRight = rightPanelLeft + bounds.rightPanelWidth();
        int rightPanelTop = bounds.rightPanelTop();
        int rightPanelBottom = bounds.rightPanelBottom();
        int contentTop = rightPanelTop + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentBottom = rightPanelBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
        if (mouseX >= rightPanelLeft && mouseX < rightPanelRight && mouseY >= contentTop && mouseY < contentBottom) {
            List<GSRRunSaveState> runsToShow = getRunsForExportPreview();
            if (!runsToShow.isEmpty()) {
                String avgText = GSRStatusText.buildAverageRunInfo(runsToShow);
                int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeightFromText(avgText);
                int contentHeight = contentBottom - contentTop;
                int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
                model.exportDetailScroll = (int) Math.max(0, Math.min(model.exportDetailScroll - verticalAmount * GSRRunHistoryParameters.DETAIL_SCROLL_AMOUNT, maxDetailScroll));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
