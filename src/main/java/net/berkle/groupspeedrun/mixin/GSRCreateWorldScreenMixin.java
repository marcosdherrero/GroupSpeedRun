package net.berkle.groupspeedrun.mixin;

import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.client.GSRSeedFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(CreateWorldScreen.class)
public abstract class GSRCreateWorldScreenMixin extends Screen {

    private static final int WORLD_NAME_MAX_LENGTH = 32;
    private static final int MOD_CONTROL = InputConstants.MOD_CONTROL;

    @Shadow @Final private WorldCreationUiState uiState;

    protected GSRCreateWorldScreenMixin() {
        super(null);
    }

    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void gsr$filterRandomSeed(CallbackInfo ci) {
        if (GSRSeedFilter.isSearching()) {
            ci.cancel();
            return;
        }
        if (!GSRSeedFilter.shouldFilter(uiState)) return;
        ci.cancel();
        GSRSeedFilter.start((CreateWorldScreen) (Object) this, uiState);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        GSRSeedFilter.renderOverlay((CreateWorldScreen) (Object) this, context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean captured) {
        if (GSRSeedFilter.handleClick((CreateWorldScreen) (Object) this, click)) return true;
        return super.mouseClicked(click, captured);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (GSRSeedFilter.handleKey((CreateWorldScreen) (Object) this, keyInput)) return true;
        return super.keyPressed(keyInput);
    }

    /** Injects at end of init to prefill world name from nextGsrWorldName when set. */
    @Inject(method = "init", at = @At("TAIL"))
    private void gsr$prefillWorldName(CallbackInfo ci) {
        String name = GSRClient.nextGsrWorldName;
        if (name == null || name.isEmpty() || uiState == null || minecraft == null) return;

        uiState.setName(name);
        // Defer to next tick; retry until we find the world name field (Game tab may load later)
        Minecraft mc = minecraft;
        gsr$scheduleApplyAttempt(mc, name, 0);
    }

    private static void gsr$scheduleApplyAttempt(Minecraft mc, String name, int attempt) {
        mc.execute(() -> {
            if (attempt >= 60) { // ~3 seconds max
                GSRClient.nextGsrWorldName = null;
                return;
            }
            if (!gsr$tryApplyName(mc, name)) {
                gsr$scheduleApplyAttempt(mc, name, attempt + 1);
            }
        });
    }

    private static boolean gsr$tryApplyName(Minecraft mc, String name) {
        try {
            if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) return false;
            if (GSRClient.nextGsrWorldName == null) return true; // already done

            EditBox field = gsr$findWorldNameField(screen);
            if (field == null) return false;

            mc.keyboardHandler.setClipboard(name);
            screen.setFocused(field);
            // Simulate Ctrl+A (select all)
            KeyEvent ctrlA = new KeyEvent(InputConstants.KEY_A, 0, MOD_CONTROL);
            screen.keyPressed(ctrlA);
            // Simulate Ctrl+V (paste) - uses clipboard we just set
            KeyEvent ctrlV = new KeyEvent(InputConstants.KEY_V, 0, MOD_CONTROL);
            screen.keyPressed(ctrlV);
            // Direct setText as fallback (paste may be handled by Keyboard before Screen)
            field.setValue(name);
            GSRClient.nextGsrWorldName = null;
            return true;
        } catch (Throwable t) {
            GSRClient.nextGsrWorldName = null;
            return true; // stop retrying
        }
    }

    /** Find the world name EditBox (max length 32) in the screen hierarchy. */
    private static EditBox gsr$findWorldNameField(Screen screen) {
        return gsr$findWorldNameFieldRecurse(screen);
    }

    private static EditBox gsr$findWorldNameFieldRecurse(GuiEventListener e) {
        if (e instanceof EditBox tf && gsr$getMaxLength(tf) == WORLD_NAME_MAX_LENGTH) {
            return tf;
        }
        if (e instanceof ContainerEventHandler pe) {
            for (GuiEventListener child : pe.children()) {
                EditBox found = gsr$findWorldNameFieldRecurse(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int gsr$getMaxLength(EditBox tf) {
        try {
            Method m = EditBox.class.getDeclaredMethod("getMaxLength");
            m.setAccessible(true);
            return (Integer) m.invoke(tf);
        } catch (Exception ex) {
            return -1;
        }
    }
}
