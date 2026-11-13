package net.berkle.groupspeedrun.client;

import net.berkle.groupspeedrun.config.GSRSeedFilterSettings;
import net.berkle.groupspeedrun.mixin.accessors.GSRCreateWorldScreenAccessor;
import net.berkle.groupspeedrun.parameter.GSRUiParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the random-seed filter on Create World without generating worlds.
 * Typed seeds are never filtered. Cancel with Escape or the overlay button.
 */
public final class GSRSeedFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-SeedFilter");
    private static final int CANCEL_WIDTH = 120;
    private static final int CANCEL_HEIGHT = 20;

    private static final AtomicBoolean searching = new AtomicBoolean(false);
    private static final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private static final AtomicInteger attempts = new AtomicInteger(0);
    private static volatile String status = "";
    private static volatile CreateWorldScreen activeScreen;

    private GSRSeedFilter() {}

    public static boolean isSearching() {
        return searching.get();
    }

    public static boolean shouldFilter(WorldCreationUiState uiState) {
        if (!GSRSeedFilterSettings.isEnabled()) return false;
        if (uiState == null) return false;
        if (!uiState.isGenerateStructures()) return false;
        if (uiState.isDebug()) return false;
        String seed = uiState.getSeed();
        return seed == null || seed.isBlank();
    }

    public static void start(CreateWorldScreen screen, WorldCreationUiState uiState) {
        if (screen == null || uiState == null || !searching.compareAndSet(false, true)) return;
        cancelRequested.set(false);
        attempts.set(0);
        activeScreen = screen;
        status = "Checking seed 1…";
        WorldCreationContext ctx = uiState.getSettings();
        Thread worker = new Thread(() -> search(screen, uiState, ctx), "gsr-seed-filter");
        worker.setDaemon(true);
        worker.start();
    }

    public static void cancel() {
        cancelRequested.set(true);
        status = "Cancelling…";
    }

    public static boolean handleKey(Screen screen, KeyEvent key) {
        if (!isSearching() || !(screen instanceof CreateWorldScreen)) return false;
        if (key.key() == InputConstants.KEY_ESCAPE) {
            cancel();
            return true;
        }
        return true;
    }

    public static boolean handleClick(Screen screen, MouseButtonEvent click) {
        if (!isSearching() || !(screen instanceof CreateWorldScreen create)) return false;
        int[] box = cancelBounds(create);
        int mx = (int) click.x();
        int my = (int) click.y();
        if (mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
            cancel();
        }
        return true;
    }

    public static void renderOverlay(Screen screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!isSearching() || !(screen instanceof CreateWorldScreen create)) return;
        int w = create.width;
        int h = create.height;
        context.fill(0, 0, w, h, 0xB0000000);
        Font font = Minecraft.getInstance().font;
        String line = status == null || status.isEmpty() ? "Checking seed…" : status;
        context.centeredText(font, Component.literal(line), w / 2, h / 2 - 20, 0xFFFFFFFF);
        context.centeredText(font, Component.literal("Random seeds only. Escape to cancel."), w / 2, h / 2 - 6, 0xFFCCCCCC);

        int[] box = cancelBounds(create);
        boolean hovered = mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
        context.fill(box[0], box[1], box[0] + box[2], box[1] + box[3],
                hovered ? GSRUiParameters.PREFERENCES_SEED_FILTER_OFF : 0xFF8B1E1E);
        context.centeredText(font, Component.literal("Cancel"), box[0] + box[2] / 2,
                box[1] + (box[3] - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    private static int[] cancelBounds(CreateWorldScreen screen) {
        int x = screen.width / 2 - CANCEL_WIDTH / 2;
        int y = screen.height / 2 + 16;
        return new int[] { x, y, CANCEL_WIDTH, CANCEL_HEIGHT };
    }

    private static void search(CreateWorldScreen screen, WorldCreationUiState uiState, WorldCreationContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        boolean found = false;
        try {
            int n = 0;
            while (!cancelRequested.get()) {
                n++;
                attempts.set(n);
                status = "Checking seed " + n + "…";
                long seed = WorldOptions.randomSeed();
                if (GSRSeedFilterChecker.passes(ctx, seed)) {
                    found = true;
                    final long accepted = seed;
                    final int tried = n;
                    mc.execute(() -> applyAndCreate(screen, uiState, accepted, tried));
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("GSR: Seed filter search failed", e);
        } finally {
            if (!found) {
                mc.execute(() -> {
                    searching.set(false);
                    activeScreen = null;
                    status = "";
                });
            }
        }
    }

    private static void applyAndCreate(CreateWorldScreen screen, WorldCreationUiState uiState, long seed, int tried) {
        try {
            if (Minecraft.getInstance().gui.screen() != screen) {
                searching.set(false);
                activeScreen = null;
                return;
            }
            uiState.setSeed(Long.toString(seed));
            LOGGER.info("GSR: Seed filter accepted {} after {} attempt(s)", seed, tried);
            searching.set(false);
            ((GSRCreateWorldScreenAccessor) screen).gsr$onCreate();
        } finally {
            searching.set(false);
            activeScreen = null;
            status = "";
        }
    }
}
