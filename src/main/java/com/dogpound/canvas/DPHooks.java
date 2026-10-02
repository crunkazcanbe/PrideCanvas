package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;

/**
 * Called from vanilla code patched by core/DPTransformer (only a static call is inserted, nothing else changes).
 * Lists (Controls, Language, Resource Packs, Stats, mod lists, world/server lists…) normally paint a dirt block
 * background and dirt strips above/below the list. With Pride theming on, the list sits on frosted purple glass
 * and the strips show the backdrop (wallpaper on the title screen, the tinted world in-game) instead.
 * Returning true = "Pride drew it, skip vanilla".
 */
public final class DPHooks {
    private DPHooks() {}

    public static boolean themed() {
        return DPConfig.blendAllScreens && Minecraft.getMinecraft().currentScreen != null;
    }

    /** GuiSlot.drawContainerBackground — the area behind the list rows: frosted glass over the list's own area only */
    public static boolean slotBackground(GuiSlot s) {
        if (!themed()) return false;
        try {
            int w = new net.minecraft.client.gui.ScaledResolution(Minecraft.getMinecraft()).getScaledWidth();
            int[] r = DPBox.listRect(s, w);
            PrideFrame.gradient(r[0], r[1], r[2], r[3], 0x90140E22, 0xA0080510);
            Gui.drawRect(r[0], r[1], r[2], r[1] + 1, 0x80F5A9B8);
            Gui.drawRect(r[0], r[3] - 1, r[2], r[3], 0x805BCEFA);
            // rows scrolled past the list's top/bottom must not spill out of the box: clip the rows to the list's
            // own band (lifted again at the first overlayBackground call, which vanilla makes right after the rows)
            PrideFrame.clip(0, r[1], w, r[3] - r[1]);
            clipping = true;
            return true;
        } catch (Throwable t) { return false; }
    }

    /** GuiSlot.overlayBackground — the strips above/below the list that hide rows scrolled out of view.
     *  Painted in the box's colour, only as wide as the box, so the world/wallpaper stays visible outside. */
    private static boolean clipping;

    public static boolean slotOverlay(int startY, int endY) {
        if (clipping) { PrideFrame.unclip(); clipping = false; }
        if (!themed()) return false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int[] b = DPBox.box(mc.currentScreen);
            if (b == null) return true;                                   // no box: just don't paint dirt
            int top = Math.max(startY, b[1]), bot = Math.min(endY, b[1] + b[3]);
            if (bot > top) {
                PrideFrame.clip(b[0], top, b[2], bot - top);
                PrideFrame.at(b[0], b[1], b[2], b[3]).drawOver(null, null);    // the same panel, just this strip of it
                PrideFrame.unclip();
            }
            return true;
        } catch (Throwable t) { PrideFrame.unclip(); return false; }
    }

    /**
     * GuiScreen.drawWorldBackground (in-game "darken the world behind this screen"). For our sub-menus the game
     * stays completely clear, like the Pride pause menu (her ask 2026-09-30: "I don't want to see the purple
     * background… clear, same as if I'm playing, in a box"): skip the darkening, but still post the background
     * event so the box (and any mod listening) draws. Title screen / other mods' screens: vanilla as usual.
     */
    public static boolean worldBackground(net.minecraft.client.gui.GuiScreen g) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !DPConfig.blendAllScreens || g == null) return false;
        if (!DPChrome.themable(g) && !g.getClass().getName().startsWith("com.dogpound.canvas.")) return false;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.BackgroundDrawnEvent(g));
        return true;
    }
}
