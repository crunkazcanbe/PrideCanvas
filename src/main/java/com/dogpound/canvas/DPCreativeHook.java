package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Opens Pride Creative instead of the vanilla tabbed creative inventory (Esc menu > Item FX to switch it off). */
@Mod.EventBusSubscriber(modid = DPMenuMod.MODID, value = Side.CLIENT)
public final class DPCreativeHook {
    private DPCreativeHook() {}

    private static boolean vanillaOnce;

    @SubscribeEvent
    public static void open(GuiOpenEvent e) {
        if (!(e.getGui() instanceof GuiContainerCreative) || !DPItemFx.get().prideCreative) return;
        if (vanillaOnce) { vanillaOnce = false; return; }
        e.setGui(new DPCreativeScreen());
    }

    /** build Pride Creative's item list a slice at a time while she plays, so E opens instantly */
    @SubscribeEvent
    public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || !DPItemFx.get().prideCreative) return;
        try { DPCreativeScreen.tickBuild(); } catch (Throwable ignored) { }
    }

    /** the normal creative inventory, just this once */
    public static void openVanilla() {
        Minecraft mc = Minecraft.getMinecraft();
        vanillaOnce = true;
        mc.displayGuiScreen(new GuiContainerCreative(mc.player));
    }
}
