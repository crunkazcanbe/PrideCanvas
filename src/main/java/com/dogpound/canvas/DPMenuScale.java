package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Menus fill the screen (her ask 2026-09-30: "some of the menus… are real skinny on the monitor in the center…
 * make those… fit the whole screen"). On her huge monitor with GUI Scale 2 every menu was drawn tiny. While a
 * menu is open this switches to a bigger GUI scale (config: 0 = the biggest that fits), and puts her own scale
 * back the moment she's playing again — so the hotbar/HUD stay the size she chose. If she changes GUI Scale
 * herself while a menu is open, her new choice is kept.
 */
public final class DPMenuScale {
    private static int saved = -1;      // her scale while we're overriding it (-1 = not overriding)
    private static int applied = -1;    // the scale we put in

    /** a "menu": everything on the title screen; in-game ours + the themed sub-screens (never chat/inventories) */
    static boolean isMenu(GuiScreen g) {
        if (g == null || g instanceof GuiChat || g instanceof GuiContainer) return false;
        if (Minecraft.getMinecraft().world == null) return true;
        String n = g.getClass().getName();
        return n.startsWith("com.dogpound.canvas.") || DPChrome.themable(g) || n.startsWith("com.dogpound.pridequests.client.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (DPConfig.menuGuiScale < 0) { restore(mc); return; }
        if (isMenu(e.getGui())) {
            int want = DPConfig.menuGuiScale;                  // 0 = auto: the biggest that fits
            if (saved < 0) saved = mc.gameSettings.guiScale;
            else if (mc.gameSettings.guiScale != applied) saved = mc.gameSettings.guiScale;   // she changed it herself
            mc.gameSettings.guiScale = want;
            applied = want;
        } else {
            restore(mc);
        }
    }

    private static void restore(Minecraft mc) {
        if (saved < 0) return;
        if (mc.gameSettings.guiScale == applied) mc.gameSettings.guiScale = saved;   // untouched by her → put hers back
        saved = -1; applied = -1;
        mc.gameSettings.saveOptions();                          // options.txt must never keep the menu scale
    }
}
