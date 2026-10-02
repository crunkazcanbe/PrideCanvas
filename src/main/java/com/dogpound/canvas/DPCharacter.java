package com.dogpound.canvas;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

/**
 * Draws the player's own skin as a small flat front-facing figure (head, body,
 * arms, legs + the outer hat/jacket layer). Shows the default skin instantly,
 * then upgrades to the player's real skin once it downloads. Lives in GUI-scaled
 * space so it grows/shrinks with the player's GUI scale.
 */
public final class DPCharacter {
    private DPCharacter() {}

    private static volatile ResourceLocation realSkin = null;
    private static boolean started = false;

    private static ResourceLocation skin(Minecraft mc) {
        if (realSkin != null) return realSkin;

        if (!started) {
            started = true;
            try {
                final GameProfile profile = mc.getSession().getProfile();
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            GameProfile filled = mc.getSessionService().fillProfileProperties(profile, false);
                            mc.getSkinManager().loadProfileTextures(filled, new SkinManager.SkinAvailableCallback() {
                                public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation loc, MinecraftProfileTexture tex) {
                                    if (type == MinecraftProfileTexture.Type.SKIN && loc != null) realSkin = loc;
                                }
                            }, false);
                        } catch (Throwable e) {
                            // leave default
                        }
                    }
                }, "DP Skin Load");
                t.setDaemon(true);
                t.start();
            } catch (Throwable e) { }
        }

        try {
            return DefaultPlayerSkin.getDefaultSkin(mc.getSession().getProfile().getId());
        } catch (Throwable e) {
            return DefaultPlayerSkin.getDefaultSkinLegacy();
        }
    }

    /** px,py = top-left anchor; s = pixel scale (each skin texel becomes s screen px). */
    public static void draw(Minecraft mc, int px, int py, int s) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(skin(mc));

        part(px, py, s, 8, 8, 8, 8, 2, 0);     // head
        part(px, py, s, 20, 20, 8, 12, 2, 8);  // body
        part(px, py, s, 44, 20, 4, 12, -2, 8); // right arm
        part(px, py, s, 36, 52, 4, 12, 10, 8); // left arm
        part(px, py, s, 4, 20, 4, 12, 2, 20);  // right leg
        part(px, py, s, 20, 52, 4, 12, 6, 20); // left leg

        part(px, py, s, 40, 8, 8, 8, 2, 0);    // hat
        part(px, py, s, 20, 36, 8, 12, 2, 8);  // jacket
        part(px, py, s, 44, 36, 4, 12, -2, 8); // right sleeve
        part(px, py, s, 52, 52, 4, 12, 10, 8); // left sleeve
        part(px, py, s, 4, 36, 4, 12, 2, 20);  // right pant
        part(px, py, s, 4, 52, 4, 12, 6, 20);  // left pant
    }

    private static void part(int px, int py, int s, int u, int v, int w, int h, int ox, int oy) {
        Gui.drawScaledCustomSizeModalRect(px + ox * s, py + oy * s, (float) u, (float) v,
                w, h, w * s, h * s, 64.0F, 64.0F);
    }
}
