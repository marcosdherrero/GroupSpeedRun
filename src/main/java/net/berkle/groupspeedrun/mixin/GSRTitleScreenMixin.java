package net.berkle.groupspeedrun.mixin;

import net.berkle.groupspeedrun.GSRClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When TitleScreen opens after "New GSR World" save & quit, and nextGsrWorldName is set,
 * automatically open SelectWorldScreen (which triggers CreateWorldScreen with GSR name prefilled).
 * Adds a 20x20 GSR square beside the existing title-screen button row. GSR Options, Config, and New World are in the Controls screen.
 */
@Mixin(TitleScreen.class)
public abstract class GSRTitleScreenMixin extends Screen {

    protected GSRTitleScreenMixin() {
        super(null);
    }

    /** Injects at end of init to open SelectWorld if nextGsrWorldName set, else add GSR buttons. */
    @Inject(method = "init", at = @At("TAIL"))
    private void gsr$onInit(CallbackInfo ci) {
        if (GSRClient.nextGsrWorldName != null && !GSRClient.nextGsrWorldName.isEmpty() && client != null) {
            TitleScreen self = (TitleScreen) (Object) this;
            client.setScreen(new SelectWorldScreen(self));
        } else {
            TitleScreen self = (TitleScreen) (Object) this;
            ButtonWidget gsrControlsBtn = GSRClient.createControlsButton(client, self, width, height);
            addDrawableChild(gsrControlsBtn);
            GSRClient.applyRunHistoryLayout(this);
            if (client != null) {
                client.execute(() -> {
                    GSRClient.applyRunHistoryLayout(this);
                    client.execute(() -> GSRClient.applyRunHistoryLayout(this));
                });
            }
        }
    }
}
