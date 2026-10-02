package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;

/**
 * Live JVM memory bar - a thin line that fills across the screen to the fraction
 * of heap in use (green -> yellow -> red as it climbs). Drawn on every screen so
 * memory usage is always visible.
 */
public final class DPMemoryBar {
    private DPMemoryBar() {}

    public static void draw(int x, int y, int width, int thickness) {
        Runtime r = Runtime.getRuntime();
        long max = r.maxMemory();
        long used = r.totalMemory() - r.freeMemory();
        double frac = max > 0 ? (double) used / (double) max : 0.0;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        int fillW = (int) (width * frac);
        int col = frac < 0.70 ? 0xFFF5A9B8 : (frac < 0.90 ? 0xFFE0C040 : 0xFFE05040);
        Gui.drawRect(x, y, x + width, y + thickness, 0x66000000); // empty track
        Gui.drawRect(x, y, x + fillW, y + thickness, col);        // used fill
    }

    public static String label() {
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) / 1048576L;
        long max = r.maxMemory() / 1048576L;
        int pct = max > 0 ? (int) (used * 100L / max) : 0;
        return "Mem: " + used + " / " + max + " MB (" + pct + "%)";
    }
}
