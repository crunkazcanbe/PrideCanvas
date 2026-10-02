package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The SAME flat Minecraft block tiles the Tools panel's Sites page uses, loaded straight from
 * ~/.config/waybar/mc-blocks/*.png so the menu slots read like her site list. Each PNG is
 * uploaded once as a DynamicTexture and blitted flat into a slot (no 3D item render).
 */
public final class DPBlocks {
    private DPBlocks() {}

    private static ResourceLocation[] tex;   // loaded block textures, indexable
    private static boolean tried = false;

    private static ResourceLocation[] load() {
        if (tried) return tex;
        tried = true;
        List<ResourceLocation> out = new ArrayList<ResourceLocation>();
        try {
            File dir = new File(System.getProperty("user.home"), ".config/waybar/mc-blocks");
            File[] pngs = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (pngs != null) {
                Arrays.sort(pngs);   // stable order -> stable per-slot assignment
                Minecraft mc = Minecraft.getMinecraft();
                for (File f : pngs) {
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img == null) continue;
                        ResourceLocation rl = mc.getTextureManager()
                                .getDynamicTextureLocation("dpblock_" + out.size(), new DynamicTexture(img));
                        out.add(rl);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        tex = out.toArray(new ResourceLocation[0]);
        return tex;
    }

    public static boolean available() {
        return load().length > 0;
    }

    /** Blit the block texture at slot `index` (wraps) flat to fill (x,y,w,h). */
    public static void draw(Minecraft mc, int index, int x, int y, int w, int h) {
        ResourceLocation[] t = load();
        if (t.length == 0) return;
        ResourceLocation rl = t[Math.floorMod(index, t.length)];
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                 GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // translucent so the wallpaper (the dog) shows through the button tiles
        GlStateManager.color(1F, 1F, 1F, DPStyle.ICON_ALPHA);
        mc.getTextureManager().bindTexture(rl);
        // the PNGs are 64x64; scale the whole thing into the slot with a 1px inset
        Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1, 0, 0, w - 2, h - 2, w - 2, h - 2);
        GlStateManager.color(1F, 1F, 1F, 1F);   // leave GL as we found it
    }

    /** Stable index for a label so the same button always shows the same block (md5-ish, cheap). */
    public static int indexForLabel(String label) {
        if (label == null) return 0;
        int h = 0;
        for (int i = 0; i < label.length(); i++) h = h * 31 + label.charAt(i);
        return Math.floorMod(h, Math.max(1, load().length));
    }
}
