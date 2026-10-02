package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;

/**
 * Your real 3D character that you can grab and spin (her ask 2026-09-30: "grab it with your mouse and spin it
 * around and see your whole body"). Drag = turn (left/right) and tilt (up/down); let go and it keeps spinning a
 * little, slowing down; scroll = zoom; idle = a gentle sway. Same rendering as the inventory's player, but the
 * angles are ours instead of following the mouse.
 */
public final class DPPlayerModel {
    private float yaw = 200, pitch = 8, spin, zoom = 1f;
    private boolean dragging;
    private int lastX, lastY;
    private long lastMove;

    /** is (mx,my) on the model's area? */
    public boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public void press(int mx, int my) { dragging = true; lastX = mx; lastY = my; spin = 0; lastMove = System.currentTimeMillis(); }

    public void drag(int mx, int my) {
        if (!dragging) return;
        float dx = mx - lastX, dy = my - lastY;
        yaw += dx * 1.4f;
        pitch = Math.max(-40, Math.min(50, pitch + dy * 0.9f));
        spin = dx * 1.4f;                                 // remember the flick speed for the coast
        lastX = mx; lastY = my; lastMove = System.currentTimeMillis();
    }

    public void release() { dragging = false; if (System.currentTimeMillis() - lastMove > 90) spin = 0; }

    public void scroll(int d) { zoom = Math.max(0.6f, Math.min(1.8f, zoom * (d > 0 ? 1.1f : 1 / 1.1f))); }

    public boolean dragging() { return dragging; }

    /** draw the entity with its feet at (cx, feetY), `size` = roughly its height in GUI px */
    public void draw(EntityLivingBase e, int cx, int feetY, int size) {
        if (e == null) return;
        if (!dragging) {
            spin *= 0.93f;                                   // coast, slowing down
            if (Math.abs(spin) < 0.05f) spin = 0;
            yaw += spin;
            if (spin == 0 && DPConfig.animations) yaw += (float) Math.sin(System.currentTimeMillis() / 1400.0) * 0.12f;   // idle sway
        }
        int scale = Math.round(size * zoom / 1.9f);
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, feetY, 50);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180, 0, 0, 1);
        float body = e.renderYawOffset, rot = e.rotationYaw, pit = e.rotationPitch, headPrev = e.prevRotationYawHead, head = e.rotationYawHead;
        GlStateManager.rotate(135, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135, 0, 1, 0);
        GlStateManager.rotate(pitch, 1, 0, 0);
        GlStateManager.rotate(yaw, 0, 1, 0);
        e.renderYawOffset = 0; e.rotationYaw = 0; e.rotationPitch = 0; e.rotationYawHead = 0; e.prevRotationYawHead = 0;
        GlStateManager.color(1f, 1f, 1f, 1f);
        // full brightness: otherwise the model takes the world's light where you stand (night/rain = pitch black)
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        RenderManager rm = mc.getRenderManager();
        rm.setPlayerViewY(180);
        rm.setRenderShadow(false);
        try { rm.renderEntity(e, 0, 0, 0, 0, 1, false); } catch (Throwable ignored) {}
        rm.setRenderShadow(true);
        e.renderYawOffset = body; e.rotationYaw = rot; e.rotationPitch = pit; e.prevRotationYawHead = headPrev; e.rotationYawHead = head;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
