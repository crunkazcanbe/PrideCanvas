package com.dogpound.canvas.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pride Item FX: wrap every slot's drawing (GuiContainer.drawSlot) so hovered items can pop and wiggle and rare ones glow. */
@Mixin(value = GuiContainer.class, remap = false)
public abstract class MixinGuiContainerSlotFx {
    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("HEAD"), require = 0)
    private void pride$preSlot(Slot slot, CallbackInfo ci) {
        com.dogpound.canvas.DPItemFx.preSlot((GuiContainer) (Object) this, slot);
    }

    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("RETURN"), require = 0)
    private void pride$postSlot(Slot slot, CallbackInfo ci) {
        com.dogpound.canvas.DPItemFx.postSlot((GuiContainer) (Object) this, slot);
    }
}
