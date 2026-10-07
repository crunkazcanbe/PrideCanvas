package com.dogpound.canvas;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.Iterator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.AdvancementToast;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.gui.toasts.RecipeToast;
import net.minecraft.client.gui.toasts.TutorialToast;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Requested: no more top-right pop-ups when she joins (with 900 mods it's a flood of "Advancement Made!",
 * "New Recipes Unlocked!" and tutorial hints). Drops the kinds she switched off before they show (Config → Pop-ups).
 */
public class DPToasts {
    private static Field queue, visible;
    private static boolean broken;

    static boolean allowed(IToast t) {
        if (t instanceof AdvancementToast && DPHud.active() && DPHudSettings.get().achievements) { DPHudPanels.Achievements.add((AdvancementToast) t); return false; }   // shown in the HUD instead (requested feature)
        if (t instanceof AdvancementToast) return DPConfig.toastAdvancements;
        if (t instanceof RecipeToast) return DPConfig.toastRecipes;
        if (t instanceof TutorialToast) return DPConfig.toastTutorial;
        return DPConfig.toastOther;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START || broken) return;
        GuiToast gui = Minecraft.getMinecraft().getToastGui();
        if (gui == null) return;
        try {
            if (queue == null) {
                for (Field f : GuiToast.class.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (Deque.class.isAssignableFrom(f.getType())) queue = f;
                    else if (f.getType().isArray()) visible = f;
                }
            }
            @SuppressWarnings("unchecked") Deque<IToast> q = (Deque<IToast>) queue.get(gui);
            for (Iterator<IToast> it = q.iterator(); it.hasNext(); ) if (!allowed(it.next())) it.remove();
            Object[] vis = (Object[]) visible.get(gui);
            for (int i = 0; i < vis.length; i++) {
                if (vis[i] == null) continue;
                IToast t = toastOf(vis[i]);
                if (t != null && !allowed(t)) vis[i] = null;
            }
        } catch (Throwable t) {
            broken = true;
            System.out.println("[PrideCanvas] pop-up filter off: " + t);
        }
    }

    private static Field instToast;
    private static IToast toastOf(Object inst) throws Exception {
        if (instToast == null) for (Field f : inst.getClass().getDeclaredFields())
            if (IToast.class.isAssignableFrom(f.getType())) { f.setAccessible(true); instToast = f; }
        return instToast == null ? null : (IToast) instToast.get(inst);
    }
}
