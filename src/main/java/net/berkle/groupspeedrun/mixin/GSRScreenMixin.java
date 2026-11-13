package net.berkle.groupspeedrun.mixin;

import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.client.GSRGameMenuLayout;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-applies title/pause square GSR placement after Screen.refreshWidgetPositions.
 * Prefer the square GSR button on pause/title clicks.
 */
@Mixin(Screen.class)
public abstract class GSRScreenMixin {

    /** Injects at end of refreshWidgetPositions to re-apply GSR layout on TitleScreen and GameMenuScreen. */
    @Inject(method = "refreshWidgetPositions", at = @At("TAIL"))
    private void gsr$onRefreshWidgetPositions(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof TitleScreen && (GSRClient.nextGsrWorldName == null || GSRClient.nextGsrWorldName.isEmpty())) {
            GSRClient.applyRunHistoryLayout(self);
        }
        if (self instanceof GameMenuScreen gameMenu) {
            GSRGameMenuLayout.reapplyLayout(gameMenu);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gsr$preferSquareButton(Click click, boolean captured, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof GameMenuScreen) && !(self instanceof TitleScreen)) return;
        if (GSRGameMenuLayout.handleSquareClick(self, click, captured)) {
            cir.setReturnValue(true);
        }
    }
}
