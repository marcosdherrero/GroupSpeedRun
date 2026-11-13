package net.berkle.groupspeedrun.mixin;

// Minecraft: screen
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GSR: layout
import net.berkle.groupspeedrun.client.GSRGameMenuLayout;

/**
 * Adds a 20x20 GSR button beside the pause-menu row (same size as 26.x).
 */
@Mixin(GameMenuScreen.class)
public abstract class GSRGameMenuScreenMixin extends Screen {

    protected GSRGameMenuScreenMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gsr$onInit(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen) (Object) this;
        var gsrBtn = GSRGameMenuLayout.createSquareButton(self);
        if (gsrBtn != null) {
            addDrawableChild(gsrBtn);
            GSRGameMenuLayout.reapplySquareLayout(self);
        }
    }
}
