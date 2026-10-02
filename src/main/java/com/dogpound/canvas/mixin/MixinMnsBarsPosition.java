package com.dogpound.canvas.mixin;

import com.dogpound.canvas.DPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Her ask 2026-10-02: move the Mine and Slash bars (level badge + health/mana/energy/xp, all as one block) — at
 * top-left the inventory's side buttons covered them and the quest boxes crowded them. Mine and Slash draws every
 * style from BarsGUI.onRenderPlayerOverlay at the top-left corner, so the whole block is shifted there in one go.
 */
@Mixin(targets = "com.robertx22.uncommon.gui.player_overlays.BarsGUI", remap = false)
public abstract class MixinMnsBarsPosition {
    @Unique private static final int PRIDE_W = 116, PRIDE_H = 34;   // the Azure block's size in GUI pixels
    @Unique private boolean pride$pushed;

    @Inject(method = "onRenderPlayerOverlay", at = @At("HEAD"), require = 0)
    private void pride$move(RenderGameOverlayEvent e, CallbackInfo ci) {
        ScaledResolution r = new ScaledResolution(Minecraft.getMinecraft());
        int w = r.getScaledWidth(), h = r.getScaledHeight(), x = 0, y = 0;
        switch (String.valueOf(DPConfig.mnsBarsPosition).toLowerCase()) {
            case "top_left": break;
            case "top_right": x = w - PRIDE_W - 110; break;            // left of the minimap
            case "middle_left": y = h / 2 - PRIDE_H / 2; break;
            case "middle_right": x = w - PRIDE_W; y = h / 2 - PRIDE_H / 2; break;
            case "bottom_left": y = h - PRIDE_H - 44; break;
            default: x = w / 2 - PRIDE_W / 2; y = 2; break;            // top_center
        }
        x += DPConfig.mnsBarsNudgeX; y += DPConfig.mnsBarsNudgeY;
        if (x == 0 && y == 0) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        pride$pushed = true;
    }

    @Inject(method = "onRenderPlayerOverlay", at = @At("RETURN"), require = 0)
    private void pride$back(RenderGameOverlayEvent e, CallbackInfo ci) {
        if (pride$pushed) { GlStateManager.popMatrix(); pride$pushed = false; }
    }
}
