package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Every sub-menu as a box in the middle, like the Pride pause menu (requested feature). The box is measured from what the screen really has — its visible buttons and its
 * lists — so it fits any vanilla or mod screen without knowing its layout. Lists draw their glass and their
 * edge strips inside the box only (DPHooks), so the world/wallpaper shows everywhere outside it.
 */
public final class DPBox {
    private DPBox() {}

    /** the box for the screen being drawn right now {x, y, w, h}, or null */
    public static int[] current;
    private static GuiScreen currentFor;

    @SuppressWarnings("unchecked")
    public static int[] measure(GuiScreen g) {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        try {
            Field bl = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            for (GuiButton b : (List<GuiButton>) bl.get(g)) {
                if (!b.visible) continue;
                x1 = Math.min(x1, b.x); y1 = Math.min(y1, b.y); x2 = Math.max(x2, b.x + b.width); y2 = Math.max(y2, b.y + b.height);
            }
        } catch (Throwable ignored) {}
        for (GuiSlot s : slots(g)) {
            int[] r = listRect(s, g.width);
            x1 = Math.min(x1, r[0]); y1 = Math.min(y1, r[1]); x2 = Math.max(x2, r[2]); y2 = Math.max(y2, r[3]);
        }
        if (x1 == Integer.MAX_VALUE) return null;
        int pad = 14;
        x1 -= pad; x2 += pad; y1 -= 30; y2 += 10;                      // room for the screen's own title up top
        int minW = 260;
        if (x2 - x1 < minW) { int c = (x1 + x2) / 2; x1 = c - minW / 2; x2 = c + minW / 2; }
        x1 = Math.max(4, x1); y1 = Math.max(2, y1); x2 = Math.min(g.width - 4, x2); y2 = Math.min(g.height - 3, y2);
        return new int[]{x1, y1, x2 - x1, y2 - y1};
    }

    /** a list's real area: vanilla lists are often full-screen-wide with the rows in the middle */
    public static int[] listRect(GuiSlot s, int screenW) {
        int l = s.left, r = s.right;
        if (r - l >= screenW - 8) {
            int half = s.getListWidth() / 2 + 12;                      // + the scroll bar on the right
            l = screenW / 2 - half; r = screenW / 2 + half;
        }
        return new int[]{l, s.top, r, s.bottom};
    }

    /** every list the screen holds in a field (world lists, pack lists, key binds, stats…) */
    public static List<GuiSlot> slots(GuiScreen g) {
        List<GuiSlot> out = new ArrayList<>();
        for (Class<?> c = g.getClass(); c != null && c != GuiScreen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!GuiSlot.class.isAssignableFrom(f.getType())) continue;
                try { f.setAccessible(true); Object v = f.get(g); if (v != null && !out.contains(v)) out.add((GuiSlot) v); } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    /** measure once per frame (called from the background event), and draw the panel */
    public static void drawFor(GuiScreen g) {
        current = DPBoxLayout.of(g) != null ? new int[]{0, 0, g.width, g.height} : measure(g);   // boxed: the box is the screen
        currentFor = g;
        if (current == null) return;
        PrideFrame.at(current[0], current[1], current[2], current[3]).drawOver(null, null);
    }

    /** the box, only if it belongs to the screen showing now */
    public static int[] box(GuiScreen g) { return g == currentFor ? current : null; }
}
