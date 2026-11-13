package net.berkle.groupspeedrun.gui;

// Fabric: minecraft networking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// Minecraft: GUI, NBT
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

// LWJGL: key codes
import com.mojang.blaze3d.platform.InputConstants;

// GSR: minecraft state, gui components, network, parameters, util
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.gui.components.GSRMenuComponents;
import net.berkle.groupspeedrun.gui.components.GSRMultiSelectDropdown;
import net.berkle.groupspeedrun.gui.runmanager.GSRRunManagerContent;
import net.berkle.groupspeedrun.gui.runmanager.GSRRunManagerLayout;
import net.berkle.groupspeedrun.gui.runmanager.GSRRunManagerModel;
import net.berkle.groupspeedrun.network.GSRRunManagerRequestPayload;
import net.berkle.groupspeedrun.network.GSRRunManagerUpdatePayload;
import net.berkle.groupspeedrun.parameter.GSRButtonParameters;
import net.berkle.groupspeedrun.parameter.GSRRunHistoryParameters;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.berkle.groupspeedrun.parameter.GSRWorldConfigParameters;
import net.berkle.groupspeedrun.util.GSRScrollbarHelper;

import java.util.Set;
import java.util.UUID;

/**
 * Run Manager: Group Death and Group Health participant selection via multi-select dropdowns.
 * Container fits content exactly: no extra gap, selections not cut off.
 */
public class GSRRunManagerScreen extends Screen {

    private final Screen parent;
    private CompoundTag runManagerData;
    private boolean dataReceived;

    private final GSRRunManagerModel model = new GSRRunManagerModel();
    private final GSRTickerState tickerState = new GSRTickerState();
    private final GSRRunManagerContent content = new GSRRunManagerContent();

    public GSRRunManagerScreen(Screen parent) {
        super(GSRButtonParameters.literal(GSRButtonParameters.SCREEN_RUN_MANAGER));
        this.parent = parent;
        this.runManagerData = null;
        this.dataReceived = false;
    }

    /** Called when server sends player list and participant config. */
    public void setRunManagerData(CompoundTag nbt) {
        this.runManagerData = nbt;
        this.dataReceived = true;
    }

    /** Returns the parent screen for re-opening after save. */
    public Screen getParent() {
        return parent;
    }

    @Override
    protected void init() {
        super.init();
        dataReceived = false;
        ClientPlayNetworking.send(new GSRRunManagerRequestPayload());

        if (runManagerData != null) {
            model.loadFrom(runManagerData);
        }
        if (model.allPlayers.isEmpty() && minecraft != null) {
            var p = minecraft.player;
            if (p != null) model.allPlayers.add(new GSRRunManagerModel.PlayerEntry(p.getUUID(), p.getName().getString()));
        }

        var layout = GSRMenuComponents.singleButtonFooterLayout(width, height);
        addRenderableWidget(GSRMenuComponents.button(GSRButtonParameters.FOOTER_BACK, this::goBack,
                layout.buttonX(), layout.footerY(), layout.buttonWidth(), layout.buttonHeight()));
    }

    private void goBack() {
        if (minecraft == null) return;
        if (parent != null) minecraft.gui.setScreen(parent);
        else minecraft.gui.setScreen(null);
    }

    /** True when changing participants would derank the run (active run + anti-cheat enabled, not already deranked). */
    private boolean wouldDerankRun() {
        var wc = GSRClient.clientWorldConfig;
        if (wc == null) return false;
        return wc.antiCheatEnabled && wc.startTime > 0 && !wc.isVictorious && !wc.isFailed && !wc.locatorDeranked;
    }

    private void save() {
        CompoundTag nbt = new CompoundTag();
        writeUuidSet(nbt, GSRWorldConfigParameters.K_GROUP_DEATH_PARTICIPANTS, model.groupDeathParticipants);
        writeUuidSet(nbt, GSRWorldConfigParameters.K_SHARED_HEALTH_PARTICIPANTS, model.sharedHealthParticipants);
        writeUuidSet(nbt, GSRWorldConfigParameters.K_EXCLUDED_FROM_RUN, model.excluded);
        ClientPlayNetworking.send(new GSRRunManagerUpdatePayload(nbt));
        ClientPlayNetworking.send(new GSRRunManagerRequestPayload());
    }

    private static void writeUuidSet(CompoundTag nbt, String key, Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID u : set) {
            if (u != null) list.add(StringTag.valueOf(u.toString()));
        }
        nbt.put(key, list);
    }

    /** Max container bottom (4px margin above footer). */
    private int contentBottomMax() {
        var layout = GSRMenuComponents.singleButtonFooterLayout(width, height);
        return layout.footerY() - GSRUiParameters.FOOTER_CONTENT_GAP - GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
    }

    private int listLeft() {
        return GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN
                + (width - 2 * GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN - GSRUiParameters.RUN_MANAGER_LIST_WIDTH) / 2;
    }

    private int listWidth() {
        return GSRUiParameters.RUN_MANAGER_LIST_WIDTH;
    }

    private int containerTop() {
        return GSRUiParameters.RUN_MANAGER_CONTAINER_TOP + GSRRunHistoryParameters.SELECTION_CONTAINER_MARGIN;
    }

    private int containerBottom() {
        int maxBottom = contentBottomMax();
        if (model.deathDropdownOpen || model.healthDropdownOpen) {
            int itemCount = model.getSelectablePlayers().size();
            String header = model.deathDropdownOpen ? content.getDeathDropdown().getHeader() : content.getHealthDropdown().getHeader();
            int[] bounds = GSRRunManagerLayout.openBounds(font, header, itemCount, listWidth());
            return Math.min(bounds[1], maxBottom);
        }
        return Math.min(containerTop() + GSRRunManagerLayout.closedContentHeight(), maxBottom);
    }

    private int[] openBoundsArray() {
        int itemCount = model.getSelectablePlayers().size();
        String header = model.deathDropdownOpen ? content.getDeathDropdown().getHeader() : content.getHealthDropdown().getHeader();
        return GSRRunManagerLayout.openBounds(font, header, itemCount, listWidth());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (runManagerData != null && !dataReceived) {
            dataReceived = true;
        }
        if (runManagerData != null) {
            model.loadFrom(runManagerData);
        }
        if (model.allPlayers.isEmpty() && minecraft != null && minecraft.player != null) {
            model.allPlayers.add(new GSRRunManagerModel.PlayerEntry(minecraft.player.getUUID(), minecraft.player.getName().getString()));
        }

        context.fill(0, 0, width, height, GSRUiParameters.SCREEN_BG_DARK);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, getTitle(), width / 2, GSRUiParameters.TITLE_Y, GSRUiParameters.TITLE_COLOR);

        boolean dropdownOpen = model.deathDropdownOpen || model.healthDropdownOpen;
        if (dropdownOpen) {
            context.fill(0, 0, width, height, GSRUiParameters.DROPDOWN_OVERLAY_DIM);
        }

        if (!dataReceived && runManagerData == null) {
            context.centeredText(font, "Loading...", width / 2, height / 2 - GSRUiParameters.RUN_MANAGER_LOADING_Y_OFFSET, GSRUiParameters.RUN_MANAGER_LOADING_COLOR);
            return;
        }

        int left = listLeft();
        int w = listWidth();
        int top = containerTop();
        int bottom = containerBottom();
        content.render(model, context, font, tickerState, left, w, top, bottom, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean captured) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(click, captured);

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        long now = System.currentTimeMillis();
        int left = listLeft();
        int w = listWidth();
        int top = containerTop();
        int bottom = containerBottom();

        boolean dropdownOpen = model.deathDropdownOpen || model.healthDropdownOpen;
        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        boolean inContainer = mouseX >= left && mouseX < left + w + (dropdownOpen ? sbWidth : 0) && mouseY >= top && mouseY < bottom;

        if (dropdownOpen || inContainer) {
            if (now - model.lastClickHandledTimeMs < GSRUiParameters.CLICK_COOLDOWN_MS) {
                return true;
            }
        }

        if (!dropdownOpen) {
            int barHeight = GSRRunHistoryParameters.SELECTOR_BAR_HEIGHT;
            int deathBarTop = top + GSRUiParameters.RUN_MANAGER_LABEL_TOP_OFFSET;
            int healthSectionTop = top + GSRUiParameters.RUN_MANAGER_SECTION_HEIGHT + GSRUiParameters.RUN_MANAGER_SECTION_GAP;
            int healthBarTop = healthSectionTop + GSRUiParameters.RUN_MANAGER_LABEL_TOP_OFFSET;

            if (content.getDeathDropdown().isBarHovered(left, deathBarTop, w, barHeight, mouseX, mouseY)) {
                model.deathDropdownOpen = true;
                model.initPendingDeath();
                model.lastClickHandledTimeMs = now;
                return true;
            }
            if (content.getHealthDropdown().isBarHovered(left, healthBarTop, w, barHeight, mouseX, mouseY)) {
                model.healthDropdownOpen = true;
                model.initPendingHealth();
                model.lastClickHandledTimeMs = now;
                return true;
            }
            return super.mouseClicked(click, false);
        }

        int[] bounds = openBoundsArray();
        int overlayTop = bounds[0];
        int overlayBottom = bounds[1];
        int confirmButtonTop = bounds[4];

        if (mouseX >= left && mouseX < left + w && mouseY >= confirmButtonTop && mouseY < confirmButtonTop + GSRRunHistoryParameters.FILTER_MAKE_SELECTION_BUTTON_HEIGHT) {
            boolean hasPending = model.hasPendingDeathChanges() || model.hasPendingHealthChanges();
            if (!hasPending) {
                model.deathDropdownOpen = false;
                model.healthDropdownOpen = false;
                model.lastClickHandledTimeMs = now;
                return true;
            }
            boolean wouldDerank = wouldDerankRun();
            if (wouldDerank) {
                boolean wasDeath = model.deathDropdownOpen;
                model.deathDropdownOpen = false;
                model.healthDropdownOpen = false;
                Runnable onConfirm = () -> {
                    if (wasDeath) model.applyPendingDeath();
                    else model.applyPendingHealth();
                    save();
                };
                if (minecraft != null) {
                    minecraft.gui.setScreen(new GSRRunManagerDerankConfirmScreen(this, onConfirm));
                }
            } else {
                if (model.deathDropdownOpen) {
                    model.applyPendingDeath();
                    model.deathDropdownOpen = false;
                } else {
                    model.applyPendingHealth();
                    model.healthDropdownOpen = false;
                }
                save();
            }
            model.lastClickHandledTimeMs = now;
            return true;
        }

        if (model.deathDropdownOpen && !model.getSelectablePlayers().isEmpty()) {
            var deathGeom = GSRMultiSelectDropdown.computeGeometry(font, content.getDeathDropdown().getHeader(), left, overlayTop, overlayBottom, w, content.getDeathDropdown().getItemCount(model));
            int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(deathGeom, content.getDeathDropdown().getItemCount(model), model.deathDropdownScroll, left, w, mouseX, mouseY);
            if (itemIdx >= 0) {
                if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                    model.selectAllDeath();
                } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                    model.deselectAllDeath();
                } else {
                    int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                    model.toggleDeathIndex(dataIndex);
                }
                model.lastClickHandledTimeMs = now;
                return true;
            }
        }
        if (model.healthDropdownOpen && !model.getSelectablePlayers().isEmpty()) {
            var healthGeom = GSRMultiSelectDropdown.computeGeometry(font, content.getHealthDropdown().getHeader(), left, overlayTop, overlayBottom, w, content.getHealthDropdown().getItemCount(model));
            int itemIdx = GSRMultiSelectDropdown.getItemIndexAt(healthGeom, content.getHealthDropdown().getItemCount(model), model.healthDropdownScroll, left, w, mouseX, mouseY);
            if (itemIdx >= 0) {
                if (itemIdx == GSRRunHistoryParameters.MULTISELECT_SELECT_ALL_INDEX) {
                    model.selectAllHealth();
                } else if (itemIdx == GSRRunHistoryParameters.MULTISELECT_DESELECT_ALL_INDEX) {
                    model.deselectAllHealth();
                } else {
                    int dataIndex = itemIdx - GSRRunHistoryParameters.MULTISELECT_DATA_START_INDEX;
                    model.toggleHealthIndex(dataIndex);
                }
                model.lastClickHandledTimeMs = now;
                return true;
            }
        }

        model.deathDropdownOpen = false;
        model.healthDropdownOpen = false;
        model.lastClickHandledTimeMs = now;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int left = listLeft();
        int w = listWidth();
        int top = containerTop();
        int bottom = containerBottom();

        if (!model.deathDropdownOpen && !model.healthDropdownOpen) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int[] bounds = openBoundsArray();
        int overlayTop = bounds[0];
        int overlayBottom = bounds[1];

        int sbWidth = GSRScrollbarHelper.getScrollbarWidth();
        if (model.deathDropdownOpen && mx >= left && mx < left + w + sbWidth && my >= top && my < bottom) {
            int delta = (int) (verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT);
            var geom = GSRMultiSelectDropdown.computeGeometry(font, content.getDeathDropdown().getHeader(), left, overlayTop, overlayBottom, w, content.getDeathDropdown().getItemCount(model));
            model.deathDropdownScroll = Math.max(0, Math.min(geom.maxScroll(), model.deathDropdownScroll + delta));
            return true;
        }
        if (model.healthDropdownOpen && mx >= left && mx < left + w + sbWidth && my >= top && my < bottom) {
            int delta = (int) (verticalAmount * GSRRunHistoryParameters.ROW_HEIGHT);
            var geom = GSRMultiSelectDropdown.computeGeometry(font, content.getHealthDropdown().getHeader(), left, overlayTop, overlayBottom, w, content.getHealthDropdown().getItemCount(model));
            model.healthDropdownScroll = Math.max(0, Math.min(geom.maxScroll(), model.healthDropdownScroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        if (keyInput.key() == InputConstants.KEY_ESCAPE) {
            if (model.deathDropdownOpen || model.healthDropdownOpen) {
                model.deathDropdownOpen = false;
                model.healthDropdownOpen = false;
                return true;
            }
            goBack();
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
