package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Called by the coremod transformer at the end of LoadingScreenRenderer's draw.
 * Paints the DogPound loading overlay on top of the vanilla screen. Loaded lazily
 * (first call happens at world load, when all mod classes are available), and
 * everything is wrapped so it can never break loading.
 */
public final class DPLoadingHook {
    private DPLoadingHook() {}

    private static boolean loggedOk = false;
    private static boolean loggedErr = false;

    public static void onProgress(int progress) {
        try {
            Minecraft q = Minecraft.getMinecraft();
            // Forge asks its startup questions (missing registries...) THROUGH this renderer —
            // painting over them made the game look hung (2026-09-29). Stand aside + report it.
            if (q != null && DPPrompts.isQuestion(q.currentScreen)) { DPPrompts.fromLoading(q); return; }
            if (!DPConfig.enableLoading) return;
            if (!loggedOk) { loggedOk = true; System.out.println("[DogPound] loading overlay onProgress reached, progress=" + progress); }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return;
            // REAL screen pixels, exactly like the boot splash: crisp text, no jump to Minecraft's GUI Scale mid-load
            // (her 2026-09-28 report: sharp splash with memory bars, then halfway "low resolution, bars gone").
            int w = mc.displayWidth, h = mc.displayHeight;
            // Set up a 2D GUI projection using ONLY GlStateManager — the same ortho
            // Minecraft's setupOverlayRendering() uses, but WITHOUT referencing the
            // EntityRenderer class (that reference made DPLoadingHook crash on lazy
            // load during world start). This is the missing piece that makes our
            // draws actually land on screen.
            GlStateManager.matrixMode(5889);            // GL_PROJECTION
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, (double) w, (double) h, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(5888);            // GL_MODELVIEW
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.color(1F, 1F, 1F, 1F);
            DPBackground.draw(mc, w, h);
            DPLoadingScreen.draw(mc, w, h, progress);
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.matrixMode(5889);            // restore projection
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);            // back to modelview
            GlStateManager.popMatrix();
        } catch (Throwable t) {
            // never let the overlay break world loading — but log the first failure
            if (!loggedErr) { loggedErr = true; System.out.println("[DogPound] loading overlay ERROR: " + t); t.printStackTrace(); }
        }
    }
}
