package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pride System Monitor (requested feature): CPU, every core, GPU usage, VRAM, GTT, RAM, swap, Minecraft's own memory,
 * FPS, disk and network speeds, temperatures, clocks and power, with 60-second graphs. Opens from the Esc menu tile
 * "System Monitor" and from Options -> "System Monitor". Optional mini readout in the top-left corner while playing.
 */
public class DPSysMonitor extends GuiScreen {
    private final GuiScreen parent;
    private PrideFrame f;
    private final List<Object[]> hits = new ArrayList<Object[]>();

    public DPSysMonitor(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() { f = PrideFrame.fit(width, height); DPSysInfo.intervalMs = 500; }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static int load(float pct) { return pct < 0 ? 0xFF8A8499 : pct < 60 ? 0xFF8CE06A : pct < 85 ? 0xFFFFC040 : 0xFFFF5A64; }
    private static String pct(float v) { return v < 0 ? "n/a" : Math.round(v) + "%"; }
    private static float frac(long a, long b) { return a < 0 || b <= 0 ? 0 : Math.min(1F, a / (float) b); }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        DPSysInfo.want();
        if ("GPU".equals(DPSysInfo.gpuName)) {                                   // no product_name file: ask the driver
            try {
                String r = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^()]*(Radeon|GeForce|Arc|Intel)[^()]*)").matcher(r == null ? "" : r);
                DPSysInfo.gpuName = m.find() ? m.group(1).trim() : r;
            } catch (Throwable ignored) { }
        }
        f.draw(this, "System Monitor", "§7updates twice a second");
        int gap = 6, cw = (f.cw - 2 * gap) / 3, rowH = (f.ch - 26 - 2 * gap) / 3;
        int x1 = f.cx, x2 = f.cx + cw + gap, x3 = f.cx + 2 * (cw + gap), y1 = f.cy, y2 = y1 + rowH + gap, y3 = y2 + rowH + gap;
        FontRenderer fr = fontRenderer;

        // ---- CPU (two columns wide)
        int cpuW = 2 * cw + gap;
        PrideFrame.card(x1, y1, cpuW, rowH, PrideFrame.PINK);
        title(x1, y1, "⚙ CPU", fr.trimStringToWidth(DPSysInfo.cpuName, cpuW - 70));
        big(x1 + 6, y1 + 18, pct(DPSysInfo.cpu), load(DPSysInfo.cpu));
        line(x1 + 70, y1 + 19, "Speed  §f" + (DPSysInfo.cpuGhz < 0 ? "n/a" : String.format(Locale.ROOT, "%.2f GHz", DPSysInfo.cpuGhz)) + (DPSysInfo.cpuMaxGhz > 0 ? String.format(Locale.ROOT, " §7/ %.1f max", DPSysInfo.cpuMaxGhz) : ""));
        line(x1 + 70, y1 + 30, "Temp  §f" + temp(DPSysInfo.cpuTemp) + "   §7Cores §f" + DPSysInfo.cores.length);
        graph(x1 + 6, y1 + 44, cw - 12, rowH - 50, DPSysInfo.hCpu, 100, PrideFrame.PINK, "60 s");
        // every core as a bar
        float[] cores = DPSysInfo.cores;
        int gx = x1 + cw + 4, gy = y1 + 18, gw = cpuW - cw - 10, gh = rowH - 24;
        if (cores.length > 0) {
            int perRow = Math.min(cores.length, Math.max(8, (int) Math.ceil(cores.length / 2.0)));
            int rows = (cores.length + perRow - 1) / perRow, bw = Math.max(2, gw / perRow - 1), bh = gh / rows - 3;
            for (int i = 0; i < cores.length; i++) {
                int bx = gx + (i % perRow) * (bw + 1), by = gy + (i / perRow) * (bh + 3);
                Gui.drawRect(bx, by, bx + bw, by + bh, 0xFF1C1530);
                int fill = (int) (bh * Math.min(1F, cores[i] / 100F));
                Gui.drawRect(bx, by + bh - fill, bx + bw, by + bh, load(cores[i]));
            }
            small(gx, y1 + 6, "§7every core (" + cores.length + ")");
        }

        // ---- GPU
        PrideFrame.card(x3, y1, cw, rowH, PrideFrame.BLUE);
        title(x3, y1, "▣ GPU", fr.trimStringToWidth(DPSysInfo.gpuName, cw - 60));
        big(x3 + 6, y1 + 18, pct(DPSysInfo.gpu), load(DPSysInfo.gpu));
        line(x3 + 70, y1 + 19, "Clock §f" + (DPSysInfo.gpuMhz < 0 ? "n/a" : Math.round(DPSysInfo.gpuMhz) + " MHz"));
        line(x3 + 70, y1 + 30, "Temp  §f" + temp(DPSysInfo.gpuTemp) + (DPSysInfo.gpuHotspot > 0 ? " §7hot §f" + temp(DPSysInfo.gpuHotspot) : ""));
        line(x3 + 6, y1 + 41, "Power §f" + (DPSysInfo.gpuWatts < 0 ? "n/a" : Math.round(DPSysInfo.gpuWatts) + " W") + "   §7Fan §f" + (DPSysInfo.gpuFan < 0 ? "n/a" : Math.round(DPSysInfo.gpuFan) + " rpm"));
        graph(x3 + 6, y1 + 53, cw - 12, rowH - 59, DPSysInfo.hGpu, 100, PrideFrame.BLUE, "60 s");

        // ---- RAM
        PrideFrame.card(x1, y2, cw, rowH, PrideFrame.RAINBOW[2]);
        title(x1, y2, "▤ Computer RAM", "");
        long used = DPSysInfo.ramTotal < 0 ? -1 : DPSysInfo.ramTotal - DPSysInfo.ramAvail;
        bar(x1 + 6, y2 + 18, cw - 12, frac(used, DPSysInfo.ramTotal), "Used " + DPSysInfo.bytes(used) + " / " + DPSysInfo.bytes(DPSysInfo.ramTotal));
        long sw = DPSysInfo.swapTotal < 0 ? -1 : DPSysInfo.swapTotal - DPSysInfo.swapFree;
        bar(x1 + 6, y2 + 34, cw - 12, frac(sw, DPSysInfo.swapTotal), "Swap " + DPSysInfo.bytes(sw) + " / " + DPSysInfo.bytes(DPSysInfo.swapTotal));
        line(x1 + 6, y2 + 50, "Free for programs §f" + DPSysInfo.bytes(DPSysInfo.ramAvail) + "  §7file cache §f" + DPSysInfo.bytes(DPSysInfo.cached));
        graph(x1 + 6, y2 + 62, cw - 12, rowH - 68, DPSysInfo.hRam, 100, PrideFrame.RAINBOW[2], "used %");

        // ---- graphics memory
        PrideFrame.card(x2, y2, cw, rowH, PrideFrame.RAINBOW[4]);
        title(x2, y2, "▦ Graphics memory", "");
        bar(x2 + 6, y2 + 18, cw - 12, frac(DPSysInfo.vramUsed, DPSysInfo.vramTotal), "VRAM " + DPSysInfo.bytes(DPSysInfo.vramUsed) + " / " + DPSysInfo.bytes(DPSysInfo.vramTotal));
        bar(x2 + 6, y2 + 34, cw - 12, frac(DPSysInfo.gttUsed, DPSysInfo.gttTotal), "GTT (RAM the GPU borrows) " + DPSysInfo.bytes(DPSysInfo.gttUsed));
        line(x2 + 6, y2 + 50, "§7GTT grows when VRAM is full: it eats computer RAM");
        graph(x2 + 6, y2 + 62, cw - 12, rowH - 68, DPSysInfo.hVram, 100, PrideFrame.RAINBOW[4], "VRAM %");

        // ---- Minecraft
        PrideFrame.card(x3, y2, cw, rowH, PrideFrame.RAINBOW[3]);
        title(x3, y2, "✦ Minecraft", "");
        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        bar(x3 + 6, y2 + 18, cw - 12, frac(heapUsed, rt.maxMemory()), "Java " + DPSysInfo.bytes(heapUsed) + " / " + DPSysInfo.bytes(rt.maxMemory()));
        line(x3 + 6, y2 + 34, "Game total RAM §f" + DPSysInfo.bytes(DPSysInfo.gameRss) + (DPSysInfo.gameSwap > 0 ? " §7+ swap §f" + DPSysInfo.bytes(DPSysInfo.gameSwap) : ""));
        line(x3 + 6, y2 + 45, "FPS §f" + Minecraft.getDebugFPS() + "   §7threads §f" + DPSysInfo.threads);
        graph(x3 + 6, y2 + 58, cw - 12, rowH - 64, DPSysInfo.hFps, 0, 0xFFF5A9B8, "FPS");

        // ---- disk, network, system
        PrideFrame.card(x1, y3, cw, rowH, 0xFFFF8C00);
        title(x1, y3, "◍ Disk", "");
        line(x1 + 6, y3 + 18, "Read  §f" + DPSysInfo.speed(DPSysInfo.diskRead));
        line(x1 + 6, y3 + 29, "Write §f" + DPSysInfo.speed(DPSysInfo.diskWrite));
        graph(x1 + 6, y3 + 42, cw - 12, rowH - 48, DPSysInfo.hDisk, 0, 0xFFFF8C00, "MB/s");
        PrideFrame.card(x2, y3, cw, rowH, 0xFF20E0C0);
        title(x2, y3, "⇅ Network", "");
        line(x2 + 6, y3 + 18, "Down §f" + DPSysInfo.speed(DPSysInfo.netDown));
        line(x2 + 6, y3 + 29, "Up   §f" + DPSysInfo.speed(DPSysInfo.netUp));
        graph(x2 + 6, y3 + 42, cw - 12, rowH - 48, DPSysInfo.hNet, 0, 0xFF20E0C0, "MB/s");
        PrideFrame.card(x3, y3, cw, rowH, 0xFFB07CFF);
        title(x3, y3, "ℹ System", "");
        line(x3 + 6, y3 + 18, "Up for §f" + uptime(DPSysInfo.uptimeSec));
        line(x3 + 6, y3 + 29, "OS §f" + System.getProperty("os.name") + " " + System.getProperty("os.version", "").replaceAll("-.*", ""));
        line(x3 + 6, y3 + 40, "Java §f" + System.getProperty("java.version") + " §7" + System.getProperty("java.vm.name", "").replace("OpenJDK 64-Bit ", ""));
        line(x3 + 6, y3 + 51, "Screen §f" + mc.displayWidth + " x " + mc.displayHeight);

        // ---- bottom: corner readout switch + Done
        int by = f.y + f.h - 26;
        boolean on = DPConfig.sysOverlay;
        Gui.drawRect(f.cx, by + 5, f.cx + 16, by + 13, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? f.cx + 9 : f.cx + 1, by + 6, on ? f.cx + 15 : f.cx + 7, by + 12, 0xFFFFFFFF);
        fr.drawStringWithShadow("Show a small readout in the corner while playing", f.cx + 22, by + 5, on ? 0xFFFFFF : 0xA79FBF);
        hits.add(new Object[]{ f.cx, by, 300, 18, (Runnable) () -> {
            DPConfig.sysOverlay = !DPConfig.sysOverlay;
            net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE);
        } });
        PrideFrame.button(f.cx + f.cw - 90, by, 90, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
        hits.add(new Object[]{ f.cx + f.cw - 90, by, 90, 18, (Runnable) () -> mc.displayGuiScreen(parent) });
        super.drawScreen(mx, my, pt);
    }

    // ------------------------------------------------------------------ pieces

    private void title(int x, int y, String t, String right) {
        fontRenderer.drawStringWithShadow("§l" + t, x + 6, y + 5, 0xFFFFFF);
        if (!right.isEmpty()) small(x + 6 + fontRenderer.getStringWidth("§l" + t) + 6, y + 7, "§7" + right);
    }

    private void big(int x, int y, String s, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(2.2F, 2.2F, 1F);
        fontRenderer.drawStringWithShadow(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void line(int x, int y, String s) { fontRenderer.drawStringWithShadow("§7" + s, x, y, 0xFFFFFF); }

    private void small(float x, float y, String s) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        fontRenderer.drawStringWithShadow(s, 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    private void bar(int x, int y, int w, float f, String label) {
        Gui.drawRect(x, y, x + w, y + 12, 0xFF1C1530);
        int col = load(f * 100);
        PrideFrame.gradient(x, y, x + (int) (w * f), y + 12, PrideFrame.brighten(col), col);
        Gui.drawRect(x, y, x + (int) (w * f), y + 1, 0x50FFFFFF);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, w - 6), x + 3, y + 2, 0xFFFFFF);
    }

    /** a filled history graph; max <= 0 = scale to the biggest value shown */
    private void graph(int x, int y, int w, int h, float[] data, float max, int color, String label) {
        if (h < 8) return;
        Gui.drawRect(x, y, x + w, y + h, 0x60000000);
        for (int g = 1; g < 4; g++) Gui.drawRect(x, y + h * g / 4, x + w, y + h * g / 4 + 1, 0x18FFFFFF);
        int n = DPSysInfo.HIST, head = DPSysInfo.head;
        float top = max;
        if (top <= 0) { for (float v : data) top = Math.max(top, v); top = Math.max(1, top * 1.15F); }
        float colW = w / (float) n;
        int fill = (color & 0xFFFFFF) | 0x70000000;
        for (int i = 0; i < n; i++) {
            float v = data[(head + 1 + i) % n];
            int bh = (int) (h * Math.min(1F, v / top));
            int bx0 = x + (int) (i * colW), bx1 = x + (int) ((i + 1) * colW);
            if (bh > 0) { Gui.drawRect(bx0, y + h - bh, Math.max(bx0 + 1, bx1), y + h, fill); Gui.drawRect(bx0, y + h - bh, Math.max(bx0 + 1, bx1), y + h - bh + 1, color); }
        }
        small(x + 2, y + 2, "§7" + label + (max <= 0 ? "  max " + Math.round(top / 1.15F) : ""));
    }

    private static String temp(float c) { return c < 0 ? "n/a" : Math.round(c) + "°C"; }

    private static String uptime(long s) {
        if (s < 0) return "n/a";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        return (d > 0 ? d + "d " : "") + h + "h " + m + "m";
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        for (Object[] h : hits) {
            if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ((Runnable) h[4]).run();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException { if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent); }

    // ------------------------------------------------------------------ the small corner readout

    public static final class Overlay {
        @SubscribeEvent
        public void draw(RenderGameOverlayEvent.Post e) {
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || !DPConfig.sysOverlay) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings.showDebugInfo || mc.gameSettings.hideGUI) return;
            DPSysInfo.want();
            FontRenderer fr = mc.fontRenderer;
            long used = DPSysInfo.ramTotal < 0 ? -1 : DPSysInfo.ramTotal - DPSysInfo.ramAvail;
            String[] l = {
                    "§dFPS §f" + Minecraft.getDebugFPS(),
                    "§dCPU §f" + pct(DPSysInfo.cpu) + (DPSysInfo.cpuTemp > 0 ? " §7" + temp(DPSysInfo.cpuTemp) : ""),
                    "§bGPU §f" + pct(DPSysInfo.gpu) + (DPSysInfo.gpuTemp > 0 ? " §7" + temp(DPSysInfo.gpuTemp) : ""),
                    "§bVRAM §f" + DPSysInfo.bytes(DPSysInfo.vramUsed) + (DPSysInfo.gttUsed > (1L << 30) ? " §7+GTT " + DPSysInfo.bytes(DPSysInfo.gttUsed) : ""),
                    "§eRAM §f" + DPSysInfo.bytes(used) + " §7/ " + DPSysInfo.bytes(DPSysInfo.ramTotal),
            };
            int w = 0;
            for (String s : l) w = Math.max(w, fr.getStringWidth(s));
            Gui.drawRect(2, 2, 8 + w, 6 + l.length * 10, 0x90140E22);
            Gui.drawRect(2, 2, 8 + w, 3, PrideFrame.PINK);
            for (int i = 0; i < l.length; i++) fr.drawStringWithShadow(l[i], 5, 5 + i * 10, 0xFFFFFF);
        }
    }
}
