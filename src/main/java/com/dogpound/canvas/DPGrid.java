package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Button layout for the settings pages (Options, Video Settings).
 *
 * REBUILT 2026-09-28 (house style): every slot button now sits in ONE centred PrideFrame
 * panel as a grid of square tiles -- no longer a hotbar stretched along the bottom and a
 * column up the right edge. The mod-added extras (PymTech, Inv Tweaks...) follow the
 * regular buttons in the same grid. If the grid is taller than the panel it scrolls with
 * the mouse wheel; rows that are scrolled out of the panel are hidden (visible=false) so
 * they can't be clicked either -- hit-testing always matches what's drawn.
 */
public final class DPGrid {
    private DPGrid() {}

    public static final int TILE = 50;        // slot px
    private static final int GAP = 4;         // space between slots

    private static final List<GuiButton> laid = new ArrayList<GuiButton>();
    private static final List<Integer> baseY = new ArrayList<Integer>();
    private static PrideFrame frame;
    private static int scroll = 0, contentH = 0;
    private static boolean active = false;

    public static void clear() { active = false; laid.clear(); baseY.clear(); }

    /** Lay the bottom buttons then the extras out as one tile grid inside the centred panel. */
    public static void layout(List<GuiButton> bottom, List<GuiButton> right, int width, int height) {
        List<GuiButton> all = new ArrayList<GuiButton>(bottom);
        if (right != null) all.addAll(right);
        laid.clear(); baseY.clear();
        frame = PrideFrame.sized(width, height, 640, 340);          // the same size family as the pause menu's box

        int n = all.size();
        int usable = frame.cw - 8;                                   // room for the scroll bar
        int cols = Math.max(1, Math.min(n, (usable + GAP) / (TILE + GAP)));
        int rows = (n + cols - 1) / cols;
        contentH = rows * (TILE + GAP) - GAP;
        // centre vertically when it fits, otherwise start at the top and scroll
        int top = contentH < frame.ch ? frame.cy + (frame.ch - contentH) / 2 : frame.cy;

        for (int i = 0; i < n; i++) {
            GuiButton b = all.get(i);
            int r = i / cols, c = i % cols;
            int inRow = Math.min(cols, n - r * cols);                 // last row centres its leftovers
            int rowX = frame.cx + (usable - (inRow * TILE + (inRow - 1) * GAP)) / 2;
            b.x = rowX + c * (TILE + GAP);
            b.width = TILE; b.height = TILE;
            if (b instanceof DPButton) ((DPButton) b).noSlot = false;   // button paints its own pride slot
            laid.add(b);
            baseY.add(top + r * (TILE + GAP));
        }
        active = true;
        scroll = 0;
        scrollBy(0);
    }

    /** Mouse wheel on the settings pages. */
    public static void wheel(int dWheel) {
        if (dWheel != 0) scrollBy(-dWheel / 4);
    }

    private static void scrollBy(int d) {
        if (!active) return;
        int max = Math.max(0, contentH - frame.ch);
        scroll = Math.max(0, Math.min(max, scroll + d));
        for (int i = 0; i < laid.size(); i++) {
            GuiButton b = laid.get(i);
            b.y = baseY.get(i) - scroll;
            b.visible = b.y >= frame.cy && b.y + b.height <= frame.cy + frame.ch;   // fully inside the panel
        }
    }

    /** The house-style panel (no dimming, so the wallpaper stays) + a scroll bar when needed. */
    public static void draw(GuiScreen g, String title) {
        if (!active) return;
        frame.drawOver(title, laid.size() + " settings");
        PrideFrame.scrollbar(frame.cx + frame.cw - 3, frame.cy, frame.ch, scroll, frame.ch, contentH);
    }
}
