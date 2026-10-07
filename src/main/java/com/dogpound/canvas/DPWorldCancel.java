package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * "Cancel" on the world-loading screen (requested feature): stop a world that's taking forever to start
 * and go back to the main menu.
 *
 * How: the click sets a flag; the server thread hits our hook in ChunkProviderServer.provideChunk (every
 * chunk it loads or generates while starting up) and throws Forge's StartupQuery.AbortedException.
 * Forge treats that as a clean "start-up cancelled" — the server stops without a crash report, and the
 * client's own wait loop sees the stopped server and goes back to the menu. Chunks already saved stay saved.
 */
public final class DPWorldCancel {
    private static volatile boolean requested;
    private static boolean wasDown;

    private DPWorldCancel() {}

    /** True while a singleplayer world is starting up (the only time Cancel makes sense). */
    public static boolean starting() {
        try {
            IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
            return s != null && !s.serverIsInRunLoop() && !requested;
        } catch (Throwable t) { return false; }
    }

    public static void request() {
        if (requested) return;
        requested = true;
        System.out.println("[DogPound] world load CANCELLED by the player");
        try {
            IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
            if (s != null) s.initiateShutdown();
        } catch (Throwable ignored) {}
    }

    /** Server thread, from the transformer hook at the top of ChunkProviderServer.provideChunk. */
    public static void check() {
        if (!requested) return;
        try {
            IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
            if (s != null && s.serverIsInRunLoop()) { requested = false; return; }   // already playing: ignore
        } catch (Throwable ignored) {}
        net.minecraftforge.fml.common.StartupQuery.abort();   // throws Forge's clean "start-up cancelled" exception
    }

    /** Back on the main menu: clear for next time. */
    public static void reset() { requested = false; }

    /** Draw + click-test the button (real screen pixels, called from the loading overlay). */
    public static void drawButton(net.minecraft.client.gui.FontRenderer font, int w, int h, int scale) {
        if (!starting() && !requested) return;
        String label = requested ? "Cancelling... going back to the menu" : "Cancel - back to main menu";
        int bw = (font.getStringWidth(label) + 20) * scale, bh = 18 * scale;
        int x1 = w - bw - 14 * scale, y1 = 14 * scale, x2 = x1 + bw, y2 = y1 + bh;
        boolean over = false, down = false;
        try {
            int mx = org.lwjgl.input.Mouse.getX(), my = h - org.lwjgl.input.Mouse.getY() - 1;
            over = mx >= x1 && mx < x2 && my >= y1 && my < y2;
            down = over && org.lwjgl.input.Mouse.isButtonDown(0);
        } catch (Throwable ignored) {}
        if (down && !wasDown && !requested) request();
        wasDown = down;
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, requested ? 0xCC5C1F2F : over ? 0xDD3A1F5C : 0xB0200F38);
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y1 + scale, 0xFFF5A9B8);
        net.minecraft.client.gui.Gui.drawRect(x1, y2 - scale, x2, y2, 0xFFF5A9B8);
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x1 + scale, y2, 0xFFF5A9B8);
        net.minecraft.client.gui.Gui.drawRect(x2 - scale, y1, x2, y2, 0xFFF5A9B8);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(x1 + 10 * scale, y1 + 5 * scale, 0);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1);
        font.drawStringWithShadow(label, 0, 0, 0xFFFFFFFF);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }
}
