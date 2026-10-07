package com.dogpound.canvas;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Between "Loading world" and the first frame of the world, vanilla shows GuiScreenWorking (a small box with a
 * percentage) and then "Downloading terrain". Requested: never show that box, only our loading screen with
 * the spinner. Draw the Pride loading screen in their place.
 */
public class DPWorkingScreen {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void pre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        GuiScreen g = e.getGui();
        String n = g == null ? "" : g.getClass().getSimpleName();
        boolean connecting = n.equals("GuiConnecting");   // loading screen #21: joining a server looks like the rest (+ connection card)
        if (!n.equals("GuiScreenWorking") && !n.equals("GuiDownloadTerrain") && !connecting) return;
        e.setCanceled(true);
        DPLoadingHook.onProgress(connecting ? 5 : 100);
        if (connecting) {                                  // keep its Cancel button
            try {
                java.util.List<net.minecraft.client.gui.GuiButton> bl = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, g, "field_146292_n", "buttonList");
                for (net.minecraft.client.gui.GuiButton b : bl) b.drawButton(net.minecraft.client.Minecraft.getMinecraft(), e.getMouseX(), e.getMouseY(), e.getRenderPartialTicks());
            } catch (Throwable ignored) { }
        }
    }
}
