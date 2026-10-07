package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Why did Minecraft take so long?" (loading screen roadmap #3). Opens from the main menu's "Loaded in ..." chip.
 * Left: where the time went (stages) and how this launch compares. Right: the slowest mods (Zoomies' profiler when
 * installed, otherwise PrideCanvas' own timing) — and a button into the full Pride Profiler.
 */
public class DPBootReport extends GuiScreen {
    private final GuiScreen parent;
    private PrideFrame f;
    private int scroll;

    public DPBootReport(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        f = PrideFrame.fit(width, height);
        buttonList.clear();
        int bw = 120, by = f.y + f.h - 26;
        addButton(new DPButton(0, f.x + f.w - bw - 10, by, bw, 18, "Done").plain().sound(DPSounds.BACK));
        if (zoomiesProfiler(this) != null) addButton(new DPButton(1, f.x + 10, by, 150, 18, "Full Pride Profiler").plain());
        int off = DPSafeMode.offList().size();
        if (off > 0) addButton(new DPButton(2, f.x + 166, by, 190, 18, "Safe mode: turn " + off + " mod(s) back on").plain());
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) mc.displayGuiScreen(parent);
        if (b.id == 1) { GuiScreen g = zoomiesProfiler(this); if (g != null) mc.displayGuiScreen(g); }
        if (b.id == 2) { int n = DPSafeMode.turnBackOn(); b.displayString = n + " back on - restart to load them"; b.enabled = false; }
    }

    static GuiScreen zoomiesProfiler(GuiScreen parent) {
        try { return (GuiScreen) Class.forName("com.dogpound.zoomies.GuiProfiler").getConstructor(GuiScreen.class, int.class).newInstance(parent, 0); }
        catch (Throwable t) { return null; }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) scroll = Math.max(0, scroll + (d > 0 ? -1 : 1));
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        long total = DPBoot.totalMs;
        f.draw(this, "Why did Minecraft take so long?", total >= 0 ? "§fthis launch: §d" + DPBoot.fmt(total) : "");
        int gap = 8, cw = (f.cw - gap) / 2, x1 = f.cx, x2 = f.cx + cw + gap, y = f.cy, bottom = f.y + f.h - 32;

        // ---- left: compared with earlier launches + where the time went
        PrideFrame.card(x1, y, cw, bottom - y, PrideFrame.PINK);
        int ly = y + 5;
        fontRenderer.drawStringWithShadow("§dCompared with before", x1 + 6, ly, 0xFFFFFF); ly += 12;
        for (String s : DPBoot.historyLines()) { fontRenderer.drawStringWithShadow(s, x1 + 8, ly, 0xFFFFFF); ly += 10; }
        // last launches as bars, this one in pink (loading screen #15)
        List<long[]> hist = DPBoot.history();
        java.util.List<Long> bars = new java.util.ArrayList<Long>();
        for (int i = Math.max(0, hist.size() - (total >= 0 ? 19 : 20)); i < hist.size(); i++) bars.add(hist.get(i)[1]);
        if (total >= 0) bars.add(total);
        if (bars.size() >= 2) {
            ly += 4;
            int ch = 34, bw = Math.max(3, (cw - 16) / 20 - 2);
            long hmax = 1;
            for (long v : bars) hmax = Math.max(hmax, v);
            for (int i = 0; i < bars.size(); i++) {
                int bh = Math.max(1, (int) (ch * bars.get(i) / (double) hmax)), bxp = x1 + 8 + i * (bw + 2);
                Gui.drawRect(bxp, ly + ch - bh, bxp + bw, ly + ch, total >= 0 && i == bars.size() - 1 ? PrideFrame.PINK : 0xA05BCEFA);
            }
            ly += ch + 2;
            fontRenderer.drawStringWithShadow("§7last " + bars.size() + " launches" + (total >= 0 ? ", this one in pink" : ""), x1 + 8, ly, 0xFFFFFF);
            ly += 10;
        }
        ly += 6;
        fontRenderer.drawStringWithShadow("§dWhere the time went", x1 + 6, ly, 0xFFFFFF); ly += 12;
        Map<String, Long> st = DPBoot.finalStages;
        if (st == null || st.isEmpty()) fontRenderer.drawStringWithShadow("§7no stage timings (loading screen was off?)", x1 + 8, ly, 0xFFFFFF);
        else {
            long max = 1;
            for (long v : st.values()) max = Math.max(max, v);
            for (Map.Entry<String, Long> e : DPBoot.ranked(st)) {
                if (ly > bottom - 12) break;
                String v = DPBoot.fmt(e.getValue());
                Gui.drawRect(x1 + 8, ly + 9, x1 + 8 + (int) ((cw - 16) * e.getValue() / (double) max), ly + 10, PrideFrame.BLUE);
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(e.getKey(), cw - 70), x1 + 8, ly, 0xFFFFFF);
                fontRenderer.drawStringWithShadow(v, x1 + cw - 8 - fontRenderer.getStringWidth(v), ly, 0xB0A8C8);
                ly += 12;
            }
        }

        // ---- right: slowest mods
        PrideFrame.card(x2, y, cw, bottom - y, PrideFrame.BLUE);
        List<Map.Entry<String, Long>> mods = DPBoot.finalMods != null ? DPBoot.finalMods : DPBoot.ranked(DPBoot.modTimesAfterLoad());
        fontRenderer.drawStringWithShadow("§bSlowest mods" + (DPBoot.topFromZoomies ? " §7(Zoomies profiler)" : " §7(PrideCanvas timing)"), x2 + 6, y + 5, 0xFFFFFF);
        int rows = Math.max(1, (bottom - y - 22) / 11);
        scroll = Math.min(scroll, Math.max(0, mods.size() - rows));
        long max = mods.isEmpty() ? 1 : Math.max(1, mods.get(0).getValue());
        int ry = y + 18;
        for (int i = scroll; i < mods.size() && i < scroll + rows; i++) {
            Map.Entry<String, Long> e = mods.get(i);
            String v = String.format(Locale.ROOT, "%.1f s", e.getValue() / 1000f);
            Gui.drawRect(x2 + 8, ry + 9, x2 + 8 + (int) ((cw - 16) * e.getValue() / (double) max), ry + 10, i == 0 ? PrideFrame.PINK : 0x805BCEFA);
            fontRenderer.drawStringWithShadow("§7" + (i + 1) + ". §f" + fontRenderer.trimStringToWidth(e.getKey(), cw - 80), x2 + 8, ry, 0xFFFFFF);
            fontRenderer.drawStringWithShadow(v, x2 + cw - 8 - fontRenderer.getStringWidth(v), ry, 0xB0A8C8);
            ry += 11;
        }
        if (mods.isEmpty()) fontRenderer.drawStringWithShadow("§7no per-mod timings yet", x2 + 8, ry, 0xFFFFFF);
        PrideFrame.scrollbar(x2 + cw - 4, y + 18, bottom - y - 22, scroll, rows, mods.size());
        super.drawScreen(mx, my, pt);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
