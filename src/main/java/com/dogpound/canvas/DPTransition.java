package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Screen transitions for EVERY screen, ours or any mod's (her ask 2026-09-30: "smooth transitions and animations
 * for everything"). Purely visual: while a screen is new (~0.25 s) it is drawn slightly smaller and lower, easing
 * into place, under a fading veil. Mouse positions aren't touched — by the time anyone clicks it's in place.
 * When a menu closes, the game fades back in instead of snapping. Chat is never animated; inventories only if on.
 */
public final class DPTransition {
    private static final long IN_MS = 240, RESUME_MS = 260;
    private static GuiScreen last;
    private static long openedAt, resumedAt;
    private static boolean pushed;

    public static void resumed() { resumedAt = DPAnim.now(); }

    private static boolean eligible(GuiScreen g) {
        if (!DPConfig.transitions || g == null) return false;
        if (g instanceof GuiChat || g instanceof DPPauseMenu || g instanceof DPMainMenu) return false;   // chat instant; ours animate themselves
        if (g instanceof GuiContainer && !DPConfig.transitionContainers) return false;
        String n = g.getClass().getName();
        return !n.contains("GuiDownloadTerrain") && !n.contains("GuiConnecting") && !n.contains("Loading");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void pre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        GuiScreen g = e.getGui();
        if (g != last) { last = g; openedAt = DPAnim.now(); }
        pushed = false;
        if (!eligible(g)) return;
        float t = DPAnim.progress(openedAt, 0, IN_MS);
        if (t >= 1) return;
        float k = DPAnim.easeOutCubic(t), sc = 0.955f + 0.045f * k;
        GlStateManager.pushMatrix();
        float cx = DPBoxLayout.fullW(g) / 2f, cy = DPBoxLayout.fullH(g) / 2f;   // the real screen's middle, boxed or not
        GlStateManager.translate(cx, cy + (1 - k) * 10, 0);
        GlStateManager.scale(sc, sc, 1);
        GlStateManager.translate(-cx, -cy, 0);
        pushed = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void post(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!pushed) return;
        pushed = false;
        GlStateManager.popMatrix();
        float t = DPAnim.progress(openedAt, 0, IN_MS);
        int a = (int) (28 * (1 - DPAnim.easeOutCubic(t)));   // a whisper of tint, not a dark flash (her report: black/day flashing)
        if (a > 2) Gui.drawRect(0, 0, DPBoxLayout.fullW(e.getGui()), DPBoxLayout.fullH(e.getGui()), (a << 24) | 0x16092F);
    }

    /** a menu closed back to the game → soft fade-in of the world */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent e) {
        if (e.getGui() == null && Minecraft.getMinecraft().currentScreen != null && !(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) resumed();
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post e) {
        // the "game fades back in" veil is OFF: closing menus made the screen flash dark (her report 2026-09-30)
        if (true) return;
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || !DPConfig.transitions || resumedAt == 0) return;
        float t = DPAnim.progress(resumedAt, 0, RESUME_MS);
        if (t >= 1) return;
        ScaledResolution r = e.getResolution();
        int a = (int) (110 * (1 - DPAnim.easeOutCubic(t)));
        Gui.drawRect(0, 0, r.getScaledWidth(), r.getScaledHeight(), (a << 24) | 0x16092F);
    }
}
