package net.berkle.groupspeedrun.gui;

// Minecraft: screen, GUI, input, text
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// GSR: minecraft, config, data, gui components, parameters, util
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.client.GSRSharedRunLoader;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.data.GSRRunParticipant;
import net.berkle.groupspeedrun.data.GSRRunRecord;
import net.berkle.groupspeedrun.data.GSRRunSaveState;
import net.berkle.groupspeedrun.data.GSRRunPlayerSnapshot;
import net.berkle.groupspeedrun.gui.components.GSRMenuComponents;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryChartRenderer;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryDetailPanel;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdown;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryLayout;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryLeftColumn;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryScreenModel;
import net.berkle.groupspeedrun.gui.runhistory.GSRRunHistoryTabBar;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.berkle.groupspeedrun.util.GSRScrollbarHelper;

// LWJGL: key codes
import com.mojang.blaze3d.platform.InputConstants;

// Java: time, collections
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Run History screen: left column has Player Filter, Runs, Compare, View (equal vertical space;
 * View at bottom). Right panel has tabs: Run Info, Graphs (run-level), Player Graphs
 * (player-level comparison).
 */
public class GSRRunHistoryScreen extends Screen {

    private final Screen parent;
    /** Shared state for screen and components. */
    private final GSRRunHistoryScreenModel model = new GSRRunHistoryScreenModel();
    /** Ticker animation state. */
    private final GSRTickerState tickerState = new GSRTickerState();
    /** Which scrollbar is currently being dragged; null when none. */
    private ScrollbarId scrollbarDragging = null;

    /** UI components built from constructors. */
    private final GSRRunHistoryLeftColumn leftColumn = new GSRRunHistoryLeftColumn();
    private final GSRRunHistoryTabBar tabBar = new GSRRunHistoryTabBar();
    private final GSRRunHistoryDetailPanel detailPanel = new GSRRunHistoryDetailPanel();

    /** Identifies each scrollbar for drag state. */
    private enum ScrollbarId {
        FILTER_DROPDOWN,
        PLAYER_COUNT_DROPDOWN,
        RUNS_DROPDOWN,
        COMPARE_DROPDOWN,
        VIEW_DROPDOWN,
        TYPE_DROPDOWN,
        DETAIL_PANEL,
        CHART_HORIZONTAL
    }

    /** When true (opened from GSR Options), include current run in filters if active. */
    private final boolean includeCurrentRun;

    public GSRRunHistoryScreen(Screen parent) {
        this(parent, false);
    }

    /**
     * @param parent            Parent screen (Back returns here).
     * @param includeCurrentRun When true, prepends current run to the list and selects it if active.
     */
    public GSRRunHistoryScreen(Screen parent, boolean includeCurrentRun) {
        super(GSRButtonParameters.literal(GSRButtonParameters.SCREEN_RUN_HISTORY));
        this.parent = parent;
        this.includeCurrentRun = includeCurrentRun;
    }

    @Override
    protected void init() {
        super.init();
        model.allRuns = GSRSharedRunLoader.loadAll();
        // Active run (from world config) only visible when in that world; buildCurrentRunState returns null otherwise
        if (includeCurrentRun && minecraft != null) {
            GSRRunSaveState current = buildCurrentRunState();
            if (current != null) {
                model.allRuns.add(0, current);
                model.selectedRuns.add(current);
            }
        }
        model.allPlayerNames = collectUniquePlayerNames(model.allRuns);
        model.allPlayerCounts = collectDistinctPlayerCounts(model.allRuns);
        applyPlayerFilter();
        model.deriveSelectedRunForTab0();

        var footer = GSRMenuComponents.footerLayout(width, height, GSRRunHistoryParameters.RUN_HISTORY_FOOTER_Y_OFFSET);
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.FOOTER_BACK, this::goBack,
                footer.leftX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()));
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.RUN_HISTORY_EXPORT_CSV, this::openExportCsvPopup,
                footer.rightX(), footer.footerY(), footer.buttonWidth(), footer.buttonHeight()));
    }

    private void openExportCsvPopup() {
        if (minecraft != null) {
            minecraft.gui.setScreen(new GSRExportCsvScreen(this, model));
        }
    }

    /** Opens Delete Run confirmation screen; on confirm, deletes run(s) and reloads. */
    private void openDeleteRunConfirmScreen() {
        if (minecraft == null || model.selectedRun == null || "current".equals(model.selectedRun.record().runId())) return;
        String runId = model.selectedRun.record().runId();
        Runnable onDeleteOne = () -> {
            if (GSRSharedRunLoader.deleteRun(runId)) reloadAfterDelete();
        };
        Runnable onDeleteAll = null;
        if (model.selectedRuns.size() >= 2) {
            List<String> idsToDelete = model.selectedRuns.stream()
                    .map(r -> r.record().runId())
                    .filter(id -> id != null && !id.isEmpty() && !"current".equals(id))
                    .toList();
            if (idsToDelete.size() >= 2) {
                onDeleteAll = () -> {
                    for (String id : idsToDelete) GSRSharedRunLoader.deleteRun(id);
                    reloadAfterDelete();
                };
            }
        }
        minecraft.gui.setScreen(new GSRDeleteRunConfirmScreen(this, onDeleteOne, onDeleteAll));
    }

    /** Reloads run history after delete; clears selection and re-derives. */
    private void reloadAfterDelete() {
        model.allRuns = GSRSharedRunLoader.loadAll();
        if (includeCurrentRun && minecraft != null) {
            GSRRunSaveState current = buildCurrentRunState();
            if (current != null) model.allRuns.add(0, current);
        }
        model.allPlayerNames = collectUniquePlayerNames(model.allRuns);
        model.allPlayerCounts = collectDistinctPlayerCounts(model.allRuns);
        applyPlayerFilter();
        model.selectedRun = null;
        model.selectedRuns.clear();
        model.deriveSelectedRunForTab0();
    }

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

    private List<Integer> collectDistinctPlayerCounts(List<GSRRunSaveState> runList) {
        return runList.stream()
                .mapToInt(r -> Math.max(1, r.record().participantCount()))
                .distinct()
                .sorted()
                .boxed()
                .toList();
    }

    /**
     * Builds a GSRRunSaveState from the current run config when in world with an active run.
     * Returns null if not in world, no active run, or minecraft/config unavailable.
     * Active runs are only shown in stat tracking while in the world with that run.
     */
    private GSRRunSaveState buildCurrentRunState() {
        GSRConfigWorld config = GSRClient.clientWorldConfig;
        if (config == null || config.startTime <= 0 || minecraft == null || minecraft.level == null) return null;
        long startMs = config.startTime;
        long endMs = config.isTimerFrozen ? startMs + config.frozenTime : System.currentTimeMillis();
        String startDateIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(startMs));
        String endDateIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(endMs));
        String status = config.isVictorious ? GSRRunRecord.STATUS_VICTORY
                : (config.isFailed ? GSRRunRecord.STATUS_FAIL : GSRRunRecord.STATUS_ACTIVE);
        var server = minecraft.getSingleplayerServer();
        String worldName = server != null
                ? server.getWorldData().getLevelName()
                : (minecraft.level != null ? "World" : "Current");
        boolean deranked = config.antiCheatEnabled && config.locatorDeranked;
        int participantCount = Math.max(1, config.runParticipantCount);
        String runDifficulty = GSRRunRecord.computeRunDifficulty(participantCount, config.lowestDifficultyOrdinal);
        GSRRunRecord record = new GSRRunRecord(
                "current",
                worldName,
                startMs,
                endMs,
                startDateIso,
                endDateIso,
                status,
                config.failedByPlayerName != null ? config.failedByPlayerName : "",
                config.failedByDeathMessage != null ? config.failedByDeathMessage : "",
                participantCount,
                config.timeNether,
                config.timeBastion,
                config.timeFortress,
                config.timeEnd,
                config.timeDragon,
                deranked,
                runDifficulty
        );
        List<GSRRunParticipant> participants = new ArrayList<>();
        List<GSRRunPlayerSnapshot> snapshots = new ArrayList<>();
        var player = minecraft.player;
        if (player != null) {
            String name = player.getName().getString();
            String uuid = player.getStringUUID();
            participants.add(new GSRRunParticipant("current", uuid, name));
            snapshots.add(new GSRRunPlayerSnapshot("current", uuid, name, 0f, 0f, null, 0f, null, 0f, 0f, 0, 0, 0, 0, null, 0, null, 0, 0f, null, 0f, 0, null, 0, 0, null, 0, 0L, null, 0L, 0f));
        }
        return new GSRRunSaveState(record, participants, snapshots);
    }

    /** Returns live leaderboard lines for the active run when in-world with server; null otherwise. */
    private List<String> getActiveRunLeaderboardLines() {
        if (model.selectedRun == null || !"current".equals(model.selectedRun.record().runId())
                || minecraft == null || minecraft.getSingleplayerServer() == null) {
            return null;
        }
        return GSRStats.getLeaderboardLines(minecraft.getSingleplayerServer());
    }

    /** Returns freshly built active run state when selected run is current; null otherwise. Used for live Run Info updates. */
    private GSRRunSaveState getActiveRunFreshState() {
        if (model.selectedRun == null || !"current".equals(model.selectedRun.record().runId())) return null;
        return buildCurrentRunState();
    }

    private void applyPendingFilter() {
        model.selectedPlayerFilter = new HashSet<>(model.pendingPlayerFilter);
        applyPlayerFilter();
        if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO) model.deriveSelectedRunForTab0();
        else if (model.selectedRun != null && !model.runs.contains(model.selectedRun)) {
            model.selectedRun = model.runs.isEmpty() ? null : model.runs.get(0);
        }
    }

    private void applyPendingPlayerCountFilter() {
        model.selectedPlayerCountFilter = new HashSet<>(model.pendingPlayerCountFilter);
        applyPlayerFilter();
        if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO) model.deriveSelectedRunForTab0();
        else if (model.selectedRun != null && !model.runs.contains(model.selectedRun)) {
            model.selectedRun = model.runs.isEmpty() ? null : model.runs.get(0);
        }
    }

    private void applyPendingRun() {
        model.selectedRuns.clear();
        for (int i : model.pendingRunIndices) {
            if (i >= 0 && i < model.runs.size()) model.selectedRuns.add(model.runs.get(i));
        }
        model.deriveSelectedRunForTab0();
    }

    private void applyPlayerFilter() {
        List<GSRRunSaveState> filtered = new ArrayList<>(model.allRuns);
        if (!model.selectedPlayerFilter.isEmpty()) {
            filtered = filtered.stream()
                    .filter(r -> runHasAnyPlayer(r, model.selectedPlayerFilter))
                    .toList();
        }
        if (!model.selectedPlayerCountFilter.isEmpty()) {
            filtered = filtered.stream()
                    .filter(r -> model.selectedPlayerCountFilter.contains(Math.max(1, r.record().participantCount())))
                    .toList();
        }
        model.runs = new ArrayList<>(filtered);
        model.runs.sort(Comparator.comparingLong((GSRRunSaveState r) -> r.record().endMs()).reversed());
        model.selectedRuns.retainAll(new HashSet<>(model.runs));
    }

    private static boolean runHasAnyPlayer(GSRRunSaveState run, Set<String> players) {
        for (var p : run.participants()) {
            if (players.contains(p.playerName())) return true;
        }
        for (var s : run.snapshots()) {
            if (players.contains(s.playerName())) return true;
        }
        return false;
    }

    private void goBack() {
        if (minecraft != null) {
            if (parent != null) minecraft.gui.setScreen(parent);
            else minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, GSRUiParameters.SCREEN_BG_DARK);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, getTitle(), width / 2, GSRRunHistoryParameters.RUN_HISTORY_TITLE_Y, GSRUiParameters.TITLE_COLOR);

        boolean dropdownOpen = model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen || model.compareDropdownOpen || model.chartViewDropdownOpen || model.typeDropdownOpen;
        if (dropdownOpen) {
            context.fill(0, 0, width, height, GSRUiParameters.DROPDOWN_OVERLAY_DIM);
        }

        var bounds = GSRRunHistoryLayout.TwoColumnBounds.compute(width, height);

        leftColumn.render(model, context, font, tickerState, bounds, mouseX, mouseY);

        int detailLeft = bounds.rightPanelLeft();
        int detailRight = bounds.rightPanelLeft() + bounds.rightPanelWidth();
        int detailTop = bounds.rightPanelTop();
        int detailBottom = bounds.rightPanelBottom();

        /* Right panel (detail, tabs, content) always drawn so screen stays visible when dropdown open; overlay provides blur. */
        context.fill(detailLeft, detailTop, detailRight, detailBottom, GSRUiParameters.CONTENT_BOX_BG);
        tabBar.render(context, font, detailLeft, detailRight, detailTop, model.selectedTab, mouseX, mouseY);

        int contentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
        int contentLeft = detailLeft + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentRight = detailRight - GSRRunHistoryParameters.CONTENT_PADDING;

        if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO && model.selectedRun != null) {
            updateRunInfoAutoScroll(contentLeft, contentTop, contentRight, contentBottom, mouseX, mouseY, delta);
        }

        float effectiveScroll = (model.runInfoAutoScrollState == RUN_INFO_AUTO_SCROLL_DOWN || model.runInfoAutoScrollState == RUN_INFO_AUTO_SCROLL_UP)
                ? model.runInfoAutoScrollOffset : model.detailScroll;
        boolean isGraphTab = model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS || model.selectedTab == GSRRunHistoryParameters.TAB_PLAYER_GRAPHS;
        float scrollForDetail = isGraphTab ? 0f : effectiveScroll;
        boolean isAllTimeView = model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS
                && GSRRunHistoryLeftColumn.getViewModeForDisplayIndex(model.selectedChartViewIndex) == 2;
        int chartScrollForDetail = isAllTimeView ? model.chartScrollX : 0;
        List<GSRRunSaveState> runsForDetail = switch (model.selectedTab) {
            case GSRRunHistoryParameters.TAB_RUN_GRAPHS -> isAllTimeView ? model.getRunsForRunGraphsAllTime() : model.getRunsForRunGraphs();
            case GSRRunHistoryParameters.TAB_PLAYER_GRAPHS -> model.getRunsForPlayerGraphs();
            default -> model.getFilteredRunsForTab(model.selectedTab);
        };
        List<GSRRunSaveState> filteredRunsForCharts = (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS && isAllTimeView)
                ? model.getRunsForRunGraphs() : null;
        List<String> activeRunLeaderboardLines = (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO)
                ? getActiveRunLeaderboardLines() : null;
        GSRRunSaveState activeRunFreshState = (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO
                && model.selectedRun != null && "current".equals(model.selectedRun.record().runId()))
                ? buildCurrentRunState() : null;
        List<GSRRunSaveState> selectedRunsForCharts;
        if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS) {
            if (filteredRunsForCharts != null) {
                selectedRunsForCharts = model.selectedRuns.isEmpty() ? new ArrayList<>() : new ArrayList<>(model.selectedRuns);
            } else {
                selectedRunsForCharts = model.selectedRuns.isEmpty() ? new ArrayList<>(runsForDetail) : new ArrayList<>(model.selectedRuns);
            }
            selectedRunsForCharts.retainAll(new HashSet<>(runsForDetail));
        } else {
            selectedRunsForCharts = List.of();
        }
        detailPanel.render(context, font, tickerState,
                contentLeft, contentTop, contentRight, contentBottom,
                model.selectedTab, model.selectedRun, selectedRunsForCharts, runsForDetail, filteredRunsForCharts,
                model.selectedPlayerFilter, model.selectedCategoryIndex, model.selectedChartViewIndex, model.selectedPlayerTypeIndex,
                activeRunLeaderboardLines, activeRunFreshState,
                scrollForDetail, chartScrollForDetail, mouseX, mouseY, width, height);

        tickerState.clearIfNotDrawn();
    }

    /** Returns height in pixels for wrapped dropdown header text. */
    private int getDropdownHeaderHeight(String text, int maxWidth) {
        return font.split(Component.literal(text), Math.max(1, maxWidth)).size() * font.lineHeight;
    }

    private static final int RUN_INFO_AUTO_IDLE = 0;
    private static final int RUN_INFO_AUTO_HOVER_WAIT = 1;
    private static final int RUN_INFO_AUTO_SCROLL_DOWN = 2;
    private static final int RUN_INFO_AUTO_PAUSE_AT_BOTTOM = 3;
    private static final int RUN_INFO_AUTO_SCROLL_UP = 4;
    private static final int RUN_INFO_AUTO_PAUSE_AT_TOP = 5;

    /**
     * Updates Run Info auto-scroll: after 2.5s hover, scrolls down slowly; at bottom pauses 2.5s then
     * scrolls up at same speed; at top pauses 2.5s and repeats if still hovering. Completes current
     * phase if hover stops; restarts only when back at top and hovered for 2.5s.
     */
    private void updateRunInfoAutoScroll(int contentLeft, int contentTop, int contentRight, int contentBottom,
                                         int mouseX, int mouseY, float delta) {
        int contentHeight = contentBottom - contentTop;
        List<String> leaderboard = getActiveRunLeaderboardLines();
        GSRRunSaveState freshState = getActiveRunFreshState();
        int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeight(model.selectedRun, leaderboard, freshState);
        int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
        if (maxDetailScroll <= 0) {
            model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
            return;
        }

        boolean hovering = mouseX >= contentLeft && mouseX < contentRight && mouseY >= contentTop && mouseY < contentBottom;
        long now = System.currentTimeMillis();

        switch (model.runInfoAutoScrollState) {
            case RUN_INFO_AUTO_IDLE -> {
                if (hovering) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_HOVER_WAIT;
                    model.runInfoAutoScrollHoverStartMs = now;
                }
            }
            case RUN_INFO_AUTO_HOVER_WAIT -> {
                if (!hovering) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                    return;
                }
                if (now - model.runInfoAutoScrollHoverStartMs >= GSRRunHistoryParameters.RUN_INFO_AUTO_SCROLL_HOVER_DELAY_MS) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_SCROLL_DOWN;
                    model.runInfoAutoScrollOffset = model.detailScroll;
                }
            }
            case RUN_INFO_AUTO_SCROLL_DOWN -> {
                if (!hovering) {
                    model.detailScroll = (int) model.runInfoAutoScrollOffset;
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                    return;
                }
                float scrollDelta = delta * GSRRunHistoryParameters.RUN_INFO_AUTO_SCROLL_SPEED;
                model.runInfoAutoScrollOffset = Math.min(maxDetailScroll, model.runInfoAutoScrollOffset + scrollDelta);
                model.detailScroll = (int) model.runInfoAutoScrollOffset;
                if (model.runInfoAutoScrollOffset >= maxDetailScroll) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_PAUSE_AT_BOTTOM;
                    model.runInfoAutoScrollPauseStartMs = now;
                }
            }
            case RUN_INFO_AUTO_PAUSE_AT_BOTTOM -> {
                if (now - model.runInfoAutoScrollPauseStartMs >= GSRRunHistoryParameters.RUN_INFO_AUTO_SCROLL_PAUSE_MS) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_SCROLL_UP;
                }
            }
            case RUN_INFO_AUTO_SCROLL_UP -> {
                if (!hovering) {
                    model.detailScroll = (int) model.runInfoAutoScrollOffset;
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                    return;
                }
                float scrollDelta = delta * GSRRunHistoryParameters.RUN_INFO_AUTO_SCROLL_SPEED;
                model.runInfoAutoScrollOffset = Math.max(0f, model.runInfoAutoScrollOffset - scrollDelta);
                model.detailScroll = (int) model.runInfoAutoScrollOffset;
                if (model.runInfoAutoScrollOffset <= 0) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_PAUSE_AT_TOP;
                    model.runInfoAutoScrollPauseStartMs = now;
                }
            }
            case RUN_INFO_AUTO_PAUSE_AT_TOP -> {
                if (!hovering) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                    return;
                }
                if (now - model.runInfoAutoScrollPauseStartMs >= GSRRunHistoryParameters.RUN_INFO_AUTO_SCROLL_PAUSE_MS) {
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_SCROLL_DOWN;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean captured) {
        double mouseX = click.x();
        double mouseY = click.y();
        long now = System.currentTimeMillis();
        var bounds = GSRRunHistoryLayout.TwoColumnBounds.compute(width, height);
        int leftPanelLeft = bounds.leftPanelLeft();
        int leftPanelRight = leftPanelLeft + bounds.leftPanelWidth();
        int rightPanelLeft = bounds.rightPanelLeft();
        int rightPanelRight = rightPanelLeft + bounds.rightPanelWidth();
        int overlayLeft = bounds.overlayLeft();
        int overlayWidth = bounds.overlayWidth();
        int detailTop = bounds.rightPanelTop();
        int detailBottom = bounds.rightPanelBottom();
        int barHeight = bounds.barHeight();
        int barLeft = leftPanelLeft + GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;
        int barDrawWidth = bounds.leftPanelWidth() - 2 * GSRRunHistoryParameters.LEFT_COLUMN_INTERIOR_MARGIN;
        int ddHeaderMaxWidth = bounds.ddHeaderMaxWidth();
        int leftPanelTop = bounds.leftPanelTop();
        int leftPanelBottom = bounds.leftPanelBottom();
        int listTop = leftPanelTop + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
        int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
        boolean dropdownOpen = model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen || model.compareDropdownOpen || model.chartViewDropdownOpen || model.typeDropdownOpen;

        /* Filter overlay fills left panel when dropdown open. */
        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        boolean inFilterOrOverlay = dropdownOpen
                ? (mouseX >= leftPanelLeft && mouseX < leftPanelRight + sbWidth && mouseY >= leftPanelTop && mouseY < leftPanelBottom)
                : (mouseX >= leftPanelLeft && mouseX < leftPanelRight && mouseY >= leftPanelTop && mouseY < leftPanelBottom);
        if (inFilterOrOverlay) {
            int overlayListTop;
            if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getFilterDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getPlayerCountDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.runsDropdownOpen && (model.runs.size() + GSRRunHistoryLeftColumn.RUNS_PRESET_COUNT > 0)) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getRunsDropdown(model).getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.compareDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getCompareDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.chartViewDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getViewDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.typeDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getTypeDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            }

            int trackX = overlayLeft + overlayWidth;
            if (GSRScrollbarHelper.isInScrollbarHitArea((int) mouseX, trackX, sbWidth) && click.button() == InputConstants.MOUSE_BUTTON_LEFT && dropdownOpen) {
                if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getFilterDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getFilterDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int filterListHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (filterListHeight * (filterListHeight / (double) (filterListHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.FILTER_DROPDOWN;
                        model.filterDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getPlayerCountDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getPlayerCountDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int filterListHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (filterListHeight * (filterListHeight / (double) (filterListHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.PLAYER_COUNT_DROPDOWN;
                        model.playerCountDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.runsDropdownOpen && !model.runs.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getRunsDropdown(model).getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getRunsDropdown(model).getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.RUNS_DROPDOWN;
                        model.runsDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.compareDropdownOpen) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getCompareDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getCompareDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.COMPARE_DROPDOWN;
                        model.compareDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.chartViewDropdownOpen) {
                    var dd = leftColumn.getViewDropdown();
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.VIEW_DROPDOWN;
                        model.viewDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
                if (model.typeDropdownOpen) {
                    var dd = leftColumn.getTypeDropdown();
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0 && mouseY >= overlayListTop && mouseY < listAreaBottom) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        scrollbarDragging = ScrollbarId.TYPE_DROPDOWN;
                        model.viewDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                        return true;
                    }
                }
            }

            // Overlay: process confirm button and dropdown item clicks (only when dropdown open)
            int overlayListBottom = leftPanelBottom;
            if (dropdownOpen && mouseY >= overlayListTop && mouseY < overlayListBottom) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) {
                    return true;
                }
                if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getFilterDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getFilterDropdown().getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        applyPendingFilter();
                        model.filterSelectionTimeMs = System.currentTimeMillis();
                        model.filterDropdownOpen = false;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int itemCount = leftColumn.getFilterDropdown().getItemCount(model);
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, itemCount, model.filterDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
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
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getPlayerCountDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getPlayerCountDropdown().getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        applyPendingPlayerCountFilter();
                        model.playerCountSelectionTimeMs = System.currentTimeMillis();
                        model.playerCountDropdownOpen = false;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int itemCount = leftColumn.getPlayerCountDropdown().getItemCount(model);
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, itemCount, model.playerCountDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
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
                if (model.runsDropdownOpen && !model.runs.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getRunsDropdown(model).getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getRunsDropdown(model).getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        applyPendingRun();
                        model.runsSelectionTimeMs = System.currentTimeMillis();
                        model.runsDropdownOpen = false;
                        model.detailScroll = 0;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int runsItemCount = leftColumn.getRunsDropdown(model).getItemCount(model);
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, runsItemCount, model.runsDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
                    if (itemIdx >= 0) {
                        int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                        if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                            model.pendingRunIndices.clear();
                            for (int i = 0; i < model.runs.size(); i++) model.pendingRunIndices.add(i);
                        } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                            model.pendingRunIndices.clear();
                        } else if (dataIndex < GSRRunHistoryLeftColumn.RUNS_PRESET_COUNT) {
                            int n = GSRRunHistoryLeftColumn.getRunsPresetCount(dataIndex);
                            model.pendingRunIndices.clear();
                            for (int i = 0; i < Math.min(n, model.runs.size()); i++) model.pendingRunIndices.add(i);
                            applyPendingRun();
                            model.runsDropdownOpen = false;
                        } else {
                            int runIdx = dataIndex - GSRRunHistoryLeftColumn.RUNS_PRESET_COUNT;
                            if (model.pendingRunIndices.contains(runIdx)) model.pendingRunIndices.remove(runIdx);
                            else model.pendingRunIndices.add(runIdx);
                        }
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    return true;
                }
                if (model.compareDropdownOpen) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getCompareDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getCompareDropdown().getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        model.selectedCategoryIndex = model.pendingCategoryIndex;
                        model.compareSelectionTimeMs = System.currentTimeMillis();
                        model.compareDropdownOpen = false;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, leftColumn.getCompareDropdown().getItemCount(model), model.compareDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
                    if (itemIdx >= 0) {
                        model.pendingCategoryIndex = itemIdx;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    return true;
                }
                if (model.chartViewDropdownOpen) {
                    var dd = leftColumn.getViewDropdown();
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        model.selectedChartViewIndex = model.pendingViewIndex;
                        model.viewSelectionTimeMs = System.currentTimeMillis();
                        model.chartViewDropdownOpen = false;
                        model.detailScroll = 0;
                        model.chartScrollX = 0;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, dd.getItemCount(model), model.viewDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
                    if (itemIdx >= 0) {
                        model.pendingViewIndex = itemIdx;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    return true;
                }
                if (model.typeDropdownOpen) {
                    var dd = leftColumn.getTypeDropdown();
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    if (geom.isConfirmButtonAt(overlayLeft, overlayWidth, (int) mouseX, (int) mouseY)) {
                        model.selectedPlayerTypeIndex = model.pendingViewIndex;
                        model.viewSelectionTimeMs = System.currentTimeMillis();
                        model.typeDropdownOpen = false;
                        model.detailScroll = 0;
                        model.chartScrollX = 0;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    int itemWidth = overlayWidth;
                    int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(geom, dd.getItemCount(model), model.viewDropdownScroll, overlayLeft, itemWidth, (int) mouseX, (int) mouseY);
                    if (itemIdx >= 0) {
                        model.pendingViewIndex = itemIdx;
                        model.lastClickHandledTimeMs = now;
                        return true;
                    }
                    return true;
                }
            }

            // Bar clicks (only when dropdown closed): 6 vertical sections in left panel
            int row0 = bounds.barTop(0);
            int row1 = bounds.barTop(1);
            int row2 = bounds.barTop(2);
            int row3 = bounds.barTop(3);
            int row4 = bounds.barTop(4);
            int row5 = bounds.barTop(5);
            if (!dropdownOpen && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row0 && mouseY < row0 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.playerCountDropdownOpen = false; model.runsDropdownOpen = false; model.compareDropdownOpen = false; model.chartViewDropdownOpen = false; model.typeDropdownOpen = false;
                model.filterDropdownOpen = !model.filterDropdownOpen;
                if (model.filterDropdownOpen) { model.filterDropdownScroll = 0; model.pendingPlayerFilter = new HashSet<>(model.selectedPlayerFilter); }
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (!dropdownOpen && !model.allPlayerCounts.isEmpty() && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row1 && mouseY < row1 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.filterDropdownOpen = false; model.runsDropdownOpen = false; model.compareDropdownOpen = false; model.chartViewDropdownOpen = false; model.typeDropdownOpen = false;
                model.playerCountDropdownOpen = !model.playerCountDropdownOpen;
                if (model.playerCountDropdownOpen) { model.playerCountDropdownScroll = 0; model.pendingPlayerCountFilter = new HashSet<>(model.selectedPlayerCountFilter); }
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (!dropdownOpen && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row2 && mouseY < row2 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.filterDropdownOpen = false; model.playerCountDropdownOpen = false; model.compareDropdownOpen = false; model.chartViewDropdownOpen = false; model.typeDropdownOpen = false;
                model.runsDropdownOpen = !model.runsDropdownOpen;
                if (model.runsDropdownOpen) {
                    model.runsDropdownScroll = 0;
                    model.pendingRunIndices.clear();
                    if (model.selectedRuns.isEmpty()) { for (int i = 0; i < model.runs.size(); i++) model.pendingRunIndices.add(i); }
                    else { for (int i = 0; i < model.runs.size(); i++) { if (model.selectedRuns.contains(model.runs.get(i))) model.pendingRunIndices.add(i); } }
                }
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (!dropdownOpen && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row3 && mouseY < row3 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.filterDropdownOpen = false; model.playerCountDropdownOpen = false; model.runsDropdownOpen = false; model.chartViewDropdownOpen = false; model.typeDropdownOpen = false;
                model.compareDropdownOpen = !model.compareDropdownOpen;
                if (model.compareDropdownOpen) { model.compareDropdownScroll = 0; model.pendingCategoryIndex = model.selectedCategoryIndex; }
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (!dropdownOpen && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row4 && mouseY < row4 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.filterDropdownOpen = false; model.playerCountDropdownOpen = false; model.runsDropdownOpen = false; model.compareDropdownOpen = false; model.typeDropdownOpen = false;
                model.chartViewDropdownOpen = !model.chartViewDropdownOpen;
                if (model.chartViewDropdownOpen) { model.viewDropdownScroll = 0; model.pendingViewIndex = model.selectedChartViewIndex; }
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (!dropdownOpen && mouseX >= barLeft && mouseX < barLeft + barDrawWidth && mouseY >= row5 && mouseY < row5 + barHeight) {
                if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) return true;
                model.filterDropdownOpen = false; model.playerCountDropdownOpen = false; model.runsDropdownOpen = false; model.compareDropdownOpen = false; model.chartViewDropdownOpen = false;
                model.typeDropdownOpen = !model.typeDropdownOpen;
                if (model.typeDropdownOpen) { model.viewDropdownScroll = 0; model.pendingViewIndex = model.selectedPlayerTypeIndex; }
                model.lastClickHandledTimeMs = now;
                return true;
            }
        }

        // MouseButtonEvent outside left panel (with scrollbar) and right panel closes dropdown without applying
        if (model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen || model.compareDropdownOpen || model.chartViewDropdownOpen || model.typeDropdownOpen) {
            boolean inLeft = mouseX >= leftPanelLeft && mouseX < leftPanelRight + sbWidth && mouseY >= bounds.leftPanelTop() && mouseY < bounds.leftPanelBottom();
            boolean inRight = mouseX >= rightPanelLeft && mouseX < rightPanelRight + sbWidth && mouseY >= detailTop && mouseY < detailBottom;
            if (!inLeft && !inRight) {
                model.filterDropdownOpen = false;
                model.playerCountDropdownOpen = false;
                model.runsDropdownOpen = false;
                model.compareDropdownOpen = false;
                model.chartViewDropdownOpen = false;
                model.typeDropdownOpen = false;
            }
        }

        int tabY = detailTop + GSRRunHistoryParameters.TAB_TOP_OFFSET;
        int tabRowWidth = rightPanelRight - rightPanelLeft - 2 * GSRRunHistoryParameters.CONTENT_PADDING;
        int tabWidth = (tabRowWidth - 2 * GSRRunHistoryParameters.TAB_GAP) / 3;
        int tabHeight = GSRRunHistoryParameters.SELECTOR_BAR_HEIGHT;
        int tab1X = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
        int tab2X = tab1X + tabWidth + GSRRunHistoryParameters.TAB_GAP;
        int tab3X = tab2X + tabWidth + GSRRunHistoryParameters.TAB_GAP;

        if (model.selectedRun != null) {
            // Delete Run button (Run Info tab only; not for "current" run)
            if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                int detailContentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
                int detailContentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
                int detailContentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
                int detailContentRight = rightPanelRight - GSRRunHistoryParameters.CONTENT_PADDING;
                float effectiveScroll = (model.runInfoAutoScrollState == RUN_INFO_AUTO_SCROLL_DOWN || model.runInfoAutoScrollState == RUN_INFO_AUTO_SCROLL_UP)
                        ? model.runInfoAutoScrollOffset : model.detailScroll;
                List<String> lb = getActiveRunLeaderboardLines();
                GSRRunSaveState fresh = getActiveRunFreshState();
                if (GSRRunHistoryDetailPanel.isDeleteRunButtonAt(model.selectedRun, lb, fresh, detailContentLeft, detailContentTop, detailContentRight, detailContentBottom, effectiveScroll, (int) mouseX, (int) mouseY)) {
                    openDeleteRunConfirmScreen();
                    return true;
                }
            }
            // Detail panel scrollbar (Run Info tab only)
            if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                int detailContentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
                int detailContentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
                int detailContentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
                int contentHeight = detailContentBottom - detailContentTop;
                List<String> lb = getActiveRunLeaderboardLines();
                GSRRunSaveState fresh = getActiveRunFreshState();
                int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeight(model.selectedRun, lb, fresh);
                int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
                int detailSbWidth = GSRScrollbarHelper.getScrollbarWidth();
                if (maxDetailScroll > 0 && GSRScrollbarHelper.isInScrollbarHitArea((int) mouseX, detailContentLeft, detailSbWidth) && mouseY >= detailContentTop && mouseY < detailContentBottom) {
                    int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (contentHeight * (contentHeight / (double) (contentHeight + maxDetailScroll))));
                    scrollbarDragging = ScrollbarId.DETAIL_PANEL;
                    model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                    model.detailScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, detailContentTop, detailContentBottom, thumbHeight, maxDetailScroll);
                    return true;
                }
            }
            // Chart horizontal scrollbar (Run Graphs tab, All time view only)
            if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS
                    && GSRRunHistoryLeftColumn.getViewModeForDisplayIndex(model.selectedChartViewIndex) == 2
                    && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                List<GSRRunSaveState> runsForChart = model.getRunsForRunGraphs();
                int detailContentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
                int detailContentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
                int detailContentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
                int detailContentRight = rightPanelRight - GSRRunHistoryParameters.CONTENT_PADDING;
                int chartWidth = detailContentRight - detailContentLeft - GSRRunHistoryParameters.CONTENT_PADDING;
                int maxScrollX = GSRRunHistoryChartRenderer.computeOverTimeMaxScrollX(chartWidth, runsForChart.size());
                if (maxScrollX > 0 && !runsForChart.isEmpty()) {
                    int sbHeight = GSRScrollbarHelper.getScrollbarWidth();
                    int chartBottom = detailContentBottom - sbHeight;
                    int trackLeft = detailContentLeft;
                    int trackTop = chartBottom;
                    int thumbWidth = Math.max(GSRRunHistoryParameters.CHART_HORIZONTAL_SCROLLBAR_MIN_THUMB,
                            (int) (chartWidth * (chartWidth / (double) (chartWidth + maxScrollX))));
                    if (GSRScrollbarHelper.isInHorizontalScrollbarHitArea((int) mouseY, trackTop, sbHeight)
                            && mouseX >= trackLeft && mouseX < trackLeft + chartWidth) {
                        scrollbarDragging = ScrollbarId.CHART_HORIZONTAL;
                        model.chartScrollX = GSRScrollbarHelper.scrollFromMouseX(mouseX, trackLeft, trackLeft + chartWidth, thumbWidth, maxScrollX);
                        return true;
                    }
                }
            }
            // Run Info, Graphs, and Player Graphs tabs
            if (mouseX >= tab1X && mouseX < tab1X + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight) {
                model.selectedTab = GSRRunHistoryParameters.TAB_RUN_INFO;
                model.deriveSelectedRunForTab0();
                model.chartViewDropdownOpen = false;
                model.typeDropdownOpen = false;
                model.detailScroll = 0;
                model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                return true;
            }
            if (mouseX >= tab2X && mouseX < tab2X + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight) {
                model.selectedTab = GSRRunHistoryParameters.TAB_RUN_GRAPHS;
                model.chartViewDropdownOpen = false;
                model.typeDropdownOpen = false;
                model.detailScroll = 0;
                model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                return true;
            }
            if (mouseX >= tab3X && mouseX < tab3X + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight) {
                model.selectedTab = GSRRunHistoryParameters.TAB_PLAYER_GRAPHS;
                model.chartViewDropdownOpen = false;
                model.typeDropdownOpen = false;
                model.detailScroll = 0;
                model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                return true;
            }
        }
        return super.mouseClicked(click, false);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (scrollbarDragging != null) {
            double mouseX = click.x();
            double mouseY = click.y();
            var bounds = GSRRunHistoryLayout.TwoColumnBounds.compute(width, height);
            int overlayLeft = bounds.overlayLeft();
            int overlayWidth = bounds.overlayWidth();
            int listTop = bounds.leftPanelTop() + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
            int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
            int ddHeaderMaxWidth = bounds.ddHeaderMaxWidth();
            switch (scrollbarDragging) {
                case FILTER_DROPDOWN -> {
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getFilterDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getFilterDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getFilterDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int filterListHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (filterListHeight * (filterListHeight / (double) (filterListHeight + geom.maxScroll()))));
                        model.filterDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case PLAYER_COUNT_DROPDOWN -> {
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getPlayerCountDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getPlayerCountDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getPlayerCountDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        model.playerCountDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case RUNS_DROPDOWN -> {
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getRunsDropdown(model).getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getRunsDropdown(model).getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getRunsDropdown(model).getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        model.runsDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case COMPARE_DROPDOWN -> {
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getCompareDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getCompareDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getCompareDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        model.compareDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case VIEW_DROPDOWN -> {
                    var dd = leftColumn.getViewDropdown();
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(dd.getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        model.viewDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case TYPE_DROPDOWN -> {
                    var dd = leftColumn.getTypeDropdown();
                    int overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(dd.getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, dd.getHeader(), overlayLeft, listTop, listBottom, overlayWidth, dd.getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    int listAreaHeight = listAreaBottom - overlayListTop;
                    if (geom.maxScroll() > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (listAreaHeight * (listAreaHeight / (double) (listAreaHeight + geom.maxScroll()))));
                        model.viewDropdownScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, overlayListTop, listAreaBottom, thumbHeight, geom.maxScroll());
                    }
                }
                case DETAIL_PANEL -> {
                    int detailTop = bounds.rightPanelTop();
                    int detailBottom = bounds.rightPanelBottom();
                    int contentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
                    int contentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
                    int contentHeight = contentBottom - contentTop;
                    List<String> lb = getActiveRunLeaderboardLines();
                    GSRRunSaveState fresh = getActiveRunFreshState();
                    int runInfoContentHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeight(model.selectedRun, lb, fresh);
                    int maxDetailScroll = Math.max(0, runInfoContentHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
                    if (maxDetailScroll > 0) {
                        int thumbHeight = Math.max(GSRRunHistoryParameters.SCROLLBAR_MIN_THUMB_HEIGHT, (int) (contentHeight * (contentHeight / (double) (contentHeight + maxDetailScroll))));
                        model.detailScroll = GSRMultiSelectDropdown.scrollFromMouseY(mouseY, contentTop, contentBottom, thumbHeight, maxDetailScroll);
                    }
                }
                case CHART_HORIZONTAL -> {
                    int detailTop = bounds.rightPanelTop();
                    int detailBottom = bounds.rightPanelBottom();
                    int contentLeft = bounds.rightPanelLeft() + GSRRunHistoryParameters.CONTENT_PADDING;
                    int contentRight = bounds.rightPanelLeft() + bounds.rightPanelWidth() - GSRRunHistoryParameters.CONTENT_PADDING;
                    int chartWidth = contentRight - contentLeft - GSRRunHistoryParameters.CONTENT_PADDING;
                    List<GSRRunSaveState> runsForChart = model.getRunsForRunGraphs();
                    int maxScrollX = GSRRunHistoryChartRenderer.computeOverTimeMaxScrollX(chartWidth, runsForChart.size());
                    if (maxScrollX > 0) {
                        int trackLeft = contentLeft;
                        int trackRight = trackLeft + chartWidth;
                        int thumbWidth = Math.max(GSRRunHistoryParameters.CHART_HORIZONTAL_SCROLLBAR_MIN_THUMB,
                                (int) (chartWidth * (chartWidth / (double) (chartWidth + maxScrollX))));
                        model.chartScrollX = GSRScrollbarHelper.scrollFromMouseX(mouseX, trackLeft, trackRight, thumbWidth, maxScrollX);
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
        var bounds = GSRRunHistoryLayout.TwoColumnBounds.compute(width, height);
        int leftPanelLeft = bounds.leftPanelLeft();
        int leftPanelRight = leftPanelLeft + bounds.leftPanelWidth();
        int rightPanelLeft = bounds.rightPanelLeft();
        int rightPanelRight = rightPanelLeft + bounds.rightPanelWidth();
        int overlayLeft = bounds.overlayLeft();
        int overlayWidth = bounds.overlayWidth();
        int leftPanelTop = bounds.leftPanelTop();
        int leftPanelBottom = bounds.leftPanelBottom();
        int detailTop = bounds.rightPanelTop();
        int detailBottom = bounds.rightPanelBottom();
        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        int ddHeaderMaxWidth = bounds.ddHeaderMaxWidth();
        boolean dropdownOpen = model.filterDropdownOpen || model.playerCountDropdownOpen || model.runsDropdownOpen || model.compareDropdownOpen || model.chartViewDropdownOpen || model.typeDropdownOpen;
        boolean inFilterOverlay = dropdownOpen && mouseX >= leftPanelLeft && mouseX < leftPanelRight + sbWidth && mouseY >= leftPanelTop && mouseY < leftPanelBottom;

        int listTop = leftPanelTop + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
        int listBottom = bounds.selectionListBottom() + GSRRunHistoryParameters.SELECTION_SCROLL_BEHIND_HEIGHT
                + GSRRunHistoryParameters.SELECTION_DELIMITER_BAR_HEIGHT;
        if (inFilterOverlay) {
            int overlayListTop;
            if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getFilterDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getPlayerCountDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.runsDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getRunsDropdown(model).getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.compareDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getCompareDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.chartViewDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getViewDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else if (model.typeDropdownOpen) {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP + getDropdownHeaderHeight(leftColumn.getTypeDropdown().getHeader(), ddHeaderMaxWidth) + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            } else {
                overlayListTop = listTop + GSRRunHistoryParameters.LIST_VERTICAL_GAP;
            }

            if (mouseY >= overlayListTop && mouseY < leftPanelBottom) {
                if (model.filterDropdownOpen && !model.allPlayerNames.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getFilterDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getFilterDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    if (mouseY < listAreaBottom) {
                        model.filterDropdownScroll = (int) Math.max(0, Math.min(model.filterDropdownScroll - verticalAmount * GSRRunHistoryParameters.FILTER_DROPDOWN_SCROLL_AMOUNT, geom.maxScroll()));
                    }
                    return true;
                }
                if (model.playerCountDropdownOpen && !model.allPlayerCounts.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getPlayerCountDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getPlayerCountDropdown().getItemCount(model));
                    int listAreaBottom = geom.listBottom();
                    if (mouseY < listAreaBottom) {
                        model.playerCountDropdownScroll = (int) Math.max(0, Math.min(model.playerCountDropdownScroll - verticalAmount * GSRRunHistoryParameters.FILTER_DROPDOWN_SCROLL_AMOUNT, geom.maxScroll()));
                    }
                    return true;
                }
                if (model.runsDropdownOpen && !model.runs.isEmpty()) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getRunsDropdown(model).getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getRunsDropdown(model).getItemCount(model));
                    model.runsDropdownScroll = (int) Math.max(0, Math.min(model.runsDropdownScroll - verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT, geom.maxScroll()));
                    return true;
                }
                if (model.compareDropdownOpen) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getCompareDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getCompareDropdown().getItemCount(model));
                    model.compareDropdownScroll = (int) Math.max(0, Math.min(model.compareDropdownScroll - verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT, geom.maxScroll()));
                    return true;
                }
                if (model.chartViewDropdownOpen) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getViewDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getViewDropdown().getItemCount(model));
                    model.viewDropdownScroll = (int) Math.max(0, Math.min(model.viewDropdownScroll - verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT, geom.maxScroll()));
                    return true;
                }
                if (model.typeDropdownOpen) {
                    var geom = GSRMultiSelectDropdown.computeGeometry(font, leftColumn.getTypeDropdown().getHeader(), overlayLeft, listTop, listBottom, overlayWidth, leftColumn.getTypeDropdown().getItemCount(model));
                    model.viewDropdownScroll = (int) Math.max(0, Math.min(model.viewDropdownScroll - verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT, geom.maxScroll()));
                    return true;
                }
            }
        }
        // Detail panel scroll (Run Info, Stats vs Avg, All time chart horizontal)
        int detailContentLeft = rightPanelLeft + GSRRunHistoryParameters.CONTENT_PADDING;
        int detailContentRight = rightPanelRight - GSRRunHistoryParameters.CONTENT_PADDING;
        int contentTop = detailTop + GSRRunHistoryParameters.TAB_HEIGHT + GSRRunHistoryParameters.CONTENT_PADDING;
        int contentBottom = detailBottom - GSRRunHistoryParameters.DETAIL_PANEL_BOTTOM_PADDING;
        int contentHeight = contentBottom - contentTop;
        int contentWidth = detailContentRight - detailContentLeft;
        if (mouseX >= rightPanelLeft && mouseX < rightPanelRight && mouseY >= contentTop && mouseY < contentBottom) {
            if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_INFO && model.selectedRun != null) {
                List<String> lb = getActiveRunLeaderboardLines();
                GSRRunSaveState fresh = getActiveRunFreshState();
                int contentTotalHeight = GSRRunHistoryDetailPanel.computeRunInfoContentHeight(model.selectedRun, lb, fresh);
                int maxDetailScroll = Math.max(0, contentTotalHeight - contentHeight + GSRRunHistoryParameters.BOTTOM_SCROLL_PADDING);
                model.detailScroll = (int) Math.max(0, Math.min(model.detailScroll - verticalAmount * GSRRunHistoryParameters.DETAIL_SCROLL_AMOUNT, maxDetailScroll));
                model.runInfoAutoScrollState = RUN_INFO_AUTO_IDLE;
                return true;
            }
            if (model.selectedTab == GSRRunHistoryParameters.TAB_RUN_GRAPHS
                    && GSRRunHistoryLeftColumn.getViewModeForDisplayIndex(model.selectedChartViewIndex) == 2
                    && !model.runs.isEmpty()) {
                int chartWidth = contentWidth - GSRRunHistoryParameters.CONTENT_PADDING;
                int maxScrollX = GSRRunHistoryChartRenderer.computeOverTimeMaxScrollX(chartWidth, model.runs.size());
                if (maxScrollX > 0) {
                    double scrollDelta = horizontalAmount != 0 ? horizontalAmount : -verticalAmount;
                    model.chartScrollX = (int) Math.max(0, Math.min(model.chartScrollX + scrollDelta * GSRRunHistoryParameters.OVER_TIME_HORIZONTAL_SCROLL_AMOUNT, maxScrollX));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
