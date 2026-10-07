package com.dogpound.canvas.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Chunk Pregenerator's world Preview (the "Preview" button on Create New World) in the Pride look: our animated backdrop
 * instead of the dirt/grey, the big light-grey panel becomes dark glass with the rainbow bar, the inset boxes become
 * dark cards with a pink edge (requested feature).
 */
@Pseudo
@Mixin(targets = "pregenerator.impl.client.preview.PreviewScreen", remap = false)
public abstract class MixinPregenPreviewTheme {
    @Redirect(method = "renderBackground", require = 0,
              at = @At(value = "INVOKE", target = "Lpregenerator/impl/client/preview/PreviewScreen;func_146276_q_()V"))
    private void pride$backdrop(@org.spongepowered.asm.mixin.injection.Coerce net.minecraft.client.gui.GuiScreen self) {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution r = new net.minecraft.client.gui.ScaledResolution(mc);
        com.dogpound.canvas.DPBackdrop.draw(mc, r.getScaledWidth(), r.getScaledHeight());
    }
}
