package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Pride look for Chunk Pregenerator's screens (drawing helpers for the pregen mixins; no Pregenerator classes needed). */
public final class DPPregenTheme {
    private DPPregenTheme() {}

    private static final int LIGHT_GREY = 0xFFC6C6C6, MID_GREY = 0xFF8B8B8B;

    public static boolean onPreview() {
        net.minecraft.client.gui.GuiScreen s = Minecraft.getMinecraft().currentScreen;
        return s != null && s.getClass().getName().startsWith("pregenerator.");
    }

    /** their drawSimpleRect: light-grey = the big window, mid-grey = inset boxes (map, info bars) */
    public static void panel(int x1, int y1, int x2, int y2, int color, boolean inset) {
        int x = Math.min(x1, x2), y = Math.min(y1, y2), w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
        if (color == LIGHT_GREY || (!inset && w > 200 && h > 120)) {
            PrideFrame.gradient(x, y, x + w, y + h, 0xF2140E22, 0xF2080510);
            int n = PrideFrame.RAINBOW.length, sw = w / n;
            for (int i = 0; i < n; i++) Gui.drawRect(x + i * sw, y, i == n - 1 ? x + w : x + (i + 1) * sw, y + 3, PrideFrame.RAINBOW[i]);
            DPStyle.glow(x, y, w, h, DPStyle.PINK, 40, 3);
        } else {
            Gui.drawRect(x, y, x + w, y + h, 0xC0100A1C);
            int edge = 0xC0F5A9B8;
            Gui.drawRect(x, y, x + w, y + 1, edge);
            Gui.drawRect(x, y + h - 1, x + w, y + h, 0x60F5A9B8);
            Gui.drawRect(x, y, x + 1, y + h, 0x60F5A9B8);
            Gui.drawRect(x + w - 1, y, x + w, y + h, 0x60F5A9B8);
        }
    }

    /** returns true when it drew the button (plain buttons and cycle buttons; sliders/check boxes keep their own art) */
    public static boolean button(GuiButton b, Minecraft mc, int mx, int my) {
        if (!b.visible) return true;
        String cls = b.getClass().getSimpleName();
        if (!cls.equals("PregenButton") && !cls.equals("CycleButton")) return false;
        boolean over = mx >= b.x && my >= b.y && mx < b.x + b.width && my < b.y + b.height;
        DPStyle.slotPlain(b.x, b.y, b.width, b.height, over && b.enabled, b.enabled);
        if (over && b.enabled) DPStyle.glow(b.x, b.y, b.width, b.height, DPStyle.PINK, 50, 2);
        // their buttons are only 12-16 px tall: plain centred text (the big-tile label clips the tops of letters)
        net.minecraft.client.gui.FontRenderer fr = mc.fontRenderer;
        String t = fr.trimStringToWidth(b.displayString, b.width - 4);
        fr.drawStringWithShadow(t, b.x + (b.width - fr.getStringWidth(t)) / 2f, b.y + (b.height - 8) / 2f,
                !b.enabled ? 0x8A8499 : over ? 0xFFE6F0 : 0xFFFFFF);
        return true;
    }

    /** dark-grey text (made for their grey panels) drawn light on the Pride glass */
    public static int text(Object screen, int color) {
        if ((color & 0xFFFFFF) == 0x404040 && onPreview()) return 0xF3E8FF;
        return color;
    }
}
