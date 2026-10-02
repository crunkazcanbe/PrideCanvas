package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Shared moving-wallpaper renderer. Every screen draws from the same global
 * clock, so the animation stays on the exact same frame across screens -> the
 * wallpaper blends seamlessly when you move between menu screens.
 *
 * Frames come from the built-in by-the-fire set, or from an external folder set
 * via DPConfig.wallpaperPath (any PNGs, played in sorted name order).
 */
public final class DPBackground {
    private static final int BUILTIN_COUNT = 60;
    private static final long FRAME_MS = 83L; // ~12 fps

    private static ResourceLocation[] frames;
    private static int count;

    private DPBackground() {}

    /** Drop loaded frames so the next draw() rebuilds (e.g. after a config change). */
    public static void reload() {
        frames = null;
        count = 0;
    }

    private static void ensure() {
        if (frames != null) return;

        // External wallpaper folder, if configured + valid.
        String ext = DPConfig.wallpaperPath;
        if (ext != null && !ext.trim().isEmpty()) {
            try {
                File dir = new File(ext.trim());
                if (dir.isDirectory()) {
                    File[] pngs = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
                    if (pngs != null && pngs.length > 0) {
                        Arrays.sort(pngs);
                        ResourceLocation[] f = new ResourceLocation[pngs.length];
                        for (int i = 0; i < pngs.length; i++) {
                            BufferedImage img = ImageIO.read(pngs[i]);
                            ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "extbg/" + i);
                            Minecraft.getMinecraft().getTextureManager().loadTexture(rl, new DynamicTexture(img));
                            f[i] = rl;
                        }
                        frames = f;
                        count = f.length;
                        return;
                    }
                }
            } catch (Throwable t) {
                // fall through to built-in
            }
        }

        // Built-in by-the-fire frames.
        ResourceLocation[] f = new ResourceLocation[BUILTIN_COUNT];
        for (int i = 0; i < BUILTIN_COUNT; i++) {
            f[i] = new ResourceLocation(DPMenuMod.MODID, String.format("textures/bg/f%03d.png", i + 1));
        }
        frames = f;
        count = BUILTIN_COUNT;
    }

    public static void draw(Minecraft mc, int width, int height) {
        ensure();
        int idx = (int) ((Minecraft.getSystemTime() / FRAME_MS) % count);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(frames[idx]);
        Gui.drawScaledCustomSizeModalRect(0, 0, 0.0F, 0.0F, 480, 270,
                width, height, 480.0F, 270.0F);
    }
}
