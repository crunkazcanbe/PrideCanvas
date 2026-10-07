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
        // menu theme wallpaper (Esc / Options > Themes): the theme's own colours, or plain dark
        if ("Theme".equals(DPConfig.menuWallpaper)) {
            PrideFrame.gradient(0, 0, width, height, PrideFrame.PANEL_TOP | 0xFF000000, PrideFrame.PANEL_BOTTOM | 0xFF000000);
            int sw = Math.max(1, width / PrideFrame.RAINBOW.length);
            for (int i = 0; i < PrideFrame.RAINBOW.length; i++) Gui.drawRect(i * sw, height - 3, i == PrideFrame.RAINBOW.length - 1 ? width : (i + 1) * sw, height, PrideFrame.RAINBOW[i]);
            return;
        }
        if ("Dark".equals(DPConfig.menuWallpaper)) { Gui.drawRect(0, 0, width, height, 0xFF0A0A0E); return; }
        ensure();
        int idx = (int) ((Minecraft.getSystemTime() / FRAME_MS) % count);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(frames[idx]);
        // her 8K screen 10-03: something (mod or zink) made this sample a tiny mip -> giant blocks.
        // Pin it to the full-size image, crisp pixel-art filtering.
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10241, 9728);   // MIN_FILTER = NEAREST
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10240, 9728);   // MAG_FILTER = NEAREST
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 33084, 0);      // BASE_LEVEL
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 33085, 0);      // MAX_LEVEL
        org.lwjgl.opengl.GL11.glTexParameterf(3553, 33082, 0f);     // MIN_LOD
        org.lwjgl.opengl.GL11.glTexParameterf(3553, 33083, 0f);     // MAX_LOD
        org.lwjgl.opengl.GL11.glTexParameterf(3553, 34049, 0f);     // LOD_BIAS
        Gui.drawScaledCustomSizeModalRect(0, 0, 0.0F, 0.0F, 480, 270,
                width, height, 480.0F, 270.0F);
    }
}
