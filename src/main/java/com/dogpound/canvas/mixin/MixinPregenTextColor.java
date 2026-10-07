package com.dogpound.canvas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chunk Pregenerator screens in the Pride look: light labels and Pride panels instead of their grey ones. */
@Pseudo
@Mixin(targets = "pregenerator.base.impl.gui.BasePregenScreen", remap = false)
public abstract class MixinPregenTextColor {
    @ModifyVariable(method = "drawUnalignedText", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
    private int pride$light(int color) {
        return com.dogpound.canvas.DPPregenTheme.text(this, color);
    }

    /** their grey boxes: the big window becomes Pride glass with the rainbow bar, the insets dark cards with a pink edge */
    @Inject(method = "drawSimpleRect", at = @At("HEAD"), cancellable = true, require = 0)
    private void pride$panel(int x1, int y1, int x2, int y2, int color, boolean inset, CallbackInfo ci) {
        if (com.dogpound.canvas.DPPregenTheme.onPreview()) { com.dogpound.canvas.DPPregenTheme.panel(x1, y1, x2, y2, color, inset); ci.cancel(); }
    }
}
