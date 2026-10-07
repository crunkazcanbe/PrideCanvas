package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/**
 * The DogPound world load/create screen, matching the boot loading screen:
 *  - the SAME moving wallpaper (drawn by the caller -> seamless),
 *  - a centered animated spinner (rotating ring of green squares),
 *  - "Loading World" / "Creating World" centered under the spinner,
 *  - a green see-through box across the bottom with a loading bar on top + the live log inside.
 */
public final class DPLoadingScreen {
    private static final int GREEN      = 0xFFF5A9B8; // text / border / bar
    private static final int GREEN_LT   = 0xFF8CE06A; // log text
    private static final int LINE_H     = 10;

    private DPLoadingScreen() {}

    // the boot splash's box, bars and log, copied exactly (DPSplashHook) so boot -> world load is one look
    private static final int BAR_H = 11, WHITE = 0xFFFFFFFF, SPLASH_DIM = 0x66102808, SPLASH_FILL = 0x0A0A1A06;

    /** Caller already drew DPBackground + enabled blend, in REAL screen pixels. progress 0..100. */
    public static void draw(Minecraft mc, int width, int height, int progress) {
        if (progress < 1) progress = 1;
        if (progress > 100) progress = 100;
        FontRenderer fr = mc.fontRenderer;
        long t = Minecraft.getSystemTime();

        // logo, back button, spinner and title are drawn at the GUI scale — same size as the splash's logo
        int s = guiScale(width, height);
        int gw = width / s, gh = height / s;
        GlStateManager.pushMatrix();
        GlStateManager.scale((float) s, (float) s, 1.0F);
        int backSz = 24, backPad = 8;
        int backX = gw - backPad - backSz, backY = backPad;
        DPStyle.slot(backX, backY, backSz, backSz, false, true);
        String back = "\u2190";
        fr.drawStringWithShadow(back, backX + (backSz - fr.getStringWidth(back)) / 2.0f, backY + (backSz - 8) / 2.0f, GREEN_LT);
        DPChrome.drawBrand(mc, gw);
        int cx = gw / 2, cy = (int) (gh * 0.34);
        if (DPBootSettings.show("w.spinner")) drawSpinner(cx, cy, 30, t);
        String header = "Loading";
        for (String r : DPLogBuffer.last(4)) {
            String low = r.toLowerCase();
            if (low.contains("world") || low.contains("terrain") || low.contains("spawn")) header = "Loading World";
            if (low.contains("creat") || low.contains("generat") || low.contains("convert")) { header = "Creating World"; break; }
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        fr.drawStringWithShadow(header, cx / 2.0f - fr.getStringWidth(header) / 2f, (cy + 44) / 2.0f, GREEN);
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();

        // ----- the splash's green box, in real pixels: MEMORY bar, LOAD bar under it, white log -----
        // her 8K screen 10-02: 820 REAL px was a sliver — draw the box in scaled units (×1 per 720 px of height)
        int bs = Math.max(1, height / 720);
        GlStateManager.pushMatrix();
        GlStateManager.scale((float) bs, (float) bs, 1.0F);
        width /= bs; height /= bs;
        int boxW = Math.min(width - 60, 820);
        int bx = (width - boxW) / 2;
        int top = (int) (height * 0.56);
        int bottom = height - 30;
        int memTop = top, loadTop = top + BAR_H;

        Gui.drawRect(bx, top, bx + boxW, bottom, SPLASH_FILL);
        Gui.drawRect(bx - 2, top - 2, bx + boxW + 2, top,        GREEN);
        Gui.drawRect(bx - 2, bottom,  bx + boxW + 2, bottom + 2, GREEN);
        Gui.drawRect(bx - 2, top - 2, bx,            bottom + 2, GREEN);
        Gui.drawRect(bx + boxW, top - 2, bx + boxW + 2, bottom + 2, GREEN);

        long max = Runtime.getRuntime().maxMemory(), used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int memPct = max > 0 ? (int) Math.max(0, Math.min(100, 100L * used / max)) : 0;
        Gui.drawRect(bx, memTop, bx + boxW, memTop + BAR_H, SPLASH_DIM);
        int memFill = DPBootSettings.show("membar") ? (int) (boxW * (memPct / 100.0)) : 0;
        if (memFill > 0) Gui.drawRect(bx, memTop, bx + memFill, memTop + BAR_H, GREEN);
        Gui.drawRect(bx, loadTop, bx + boxW, loadTop + BAR_H, SPLASH_DIM);
        int fill = (int) (boxW * (progress / 100.0));
        if (fill > 0) Gui.drawRect(bx, loadTop, bx + fill, loadTop + BAR_H, GREEN);

        if (DPBootSettings.show("membar")) fr.drawStringWithShadow("MEMORY " + memPct + "%", bx + 4, memTop + 1, WHITE);
        fr.drawStringWithShadow("LOAD " + progress + "%", bx + 4, loadTop + 1, WHITE);
        String eta = DPEta.text();                                           // Zoomies' "about 3m 20s left"
        if (eta != null && DPBootSettings.show("eta")) fr.drawStringWithShadow(eta, bx + boxW - 4 - fr.getStringWidth(eta), loadTop + 1, WHITE);

        int logTop = loadTop + BAR_H + 3;
        int rows = Math.max(1, (bottom - logTop) / LINE_H);
        List<String> lines = DPBootSettings.show("w.log") ? DPLogBuffer.last(rows) : java.util.Collections.<String>emptyList();
        int ly = logTop;
        if (lines.isEmpty()) {
            fr.drawStringWithShadow("Loading\u2026", bx + 5, ly, WHITE);
        } else {
            for (String ln : lines) {
                fr.drawStringWithShadow(fr.trimStringToWidth(ln, boxW - 10), bx + 5, ly, WHITE);
                ly += LINE_H;
            }
        }
        // #21-#23: connection / download / world stats card, top-left
        DPWorldStats.refresh(mc);
        int cardW = Math.min(270, width / 4 + 40);
        int cardBottom = DPBootSettings.show("w.stats") ? DPWorldStats.draw(fr, 10, 40, cardW) : 34;
        if (DPBootSettings.show("w.map")) DPWorldStats.drawMap(fr, 10, cardBottom + 6, Math.min(cardW - 10, top - cardBottom - 34));
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** same rule as Minecraft's ScaledResolution / the splash's logo */
    private static int guiScale(int w, int h) {
        int gs = 2;
        try { gs = Minecraft.getMinecraft().gameSettings.guiScale; } catch (Throwable ignored) { }
        int max = gs == 0 ? 1000 : gs, sf = 1;
        while (sf < max && w / (sf + 1) >= 320 && h / (sf + 1) >= 240) sf++;
        return sf;
    }

    /** A rotating comet-tail ring of green squares — the "loading" symbol. */
    private static void drawSpinner(int cx, int cy, int radius, long t) {
        final int n = 12;
        final int sq = 7;
        int head = (int) ((t / 70L) % n);
        for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2.0 * i) / n - Math.PI / 2.0;
            int x = cx + (int) Math.round(Math.cos(ang) * radius);
            int y = cy + (int) Math.round(Math.sin(ang) * radius);
            int d = (head - i + n) % n;                 // 0 = brightest (head), trails fade
            int alpha = Math.max(0x26, 0xFF - d * 26);
            int col = (alpha << 24) | 0xF5A9B8;
            Gui.drawRect(x - sq / 2, y - sq / 2, x + sq / 2, y + sq / 2, col);
        }
    }
}
