package com.dogpound.canvas.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk Pregenerator's buttons drawn as Pride tiles (their sliders/check boxes that draw themselves are left alone). */
@Pseudo
@Mixin(targets = "pregenerator.base.impl.gui.comp.PregenButton", remap = false)
public abstract class MixinPregenButtonTheme {
    @Inject(method = "render(Lnet/minecraft/client/Minecraft;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pride$tile(Minecraft mc, int mx, int my, float pt, CallbackInfo ci) {
        if (com.dogpound.canvas.DPPregenTheme.button((GuiButton) (Object) this, mc, mx, my)) ci.cancel();
    }
}
