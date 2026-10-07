package com.dogpound.canvas;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * Her Rival 700's vibration (2026-10-03): sends small UDP messages to ~/bin/pridemouse (127.0.0.1:47700), which plays
 * the patterns. Getting hit (harder hit = stronger), dying, levelling up, eating, picking things up, advancements,
 * every click in a menu, and health for the low-health heartbeat. Nothing happens if pridemouse isn't running.
 */
public class DPMouse {
    private static DatagramSocket socket;
    private static InetAddress local;
    private float lastHealth = -1;
    private int lastLevel = -1, tick;
    private boolean dead;

    static void send(String msg) {
        if (!DPConfig.mouseBuzz) return;
        try {
            if (socket == null) { socket = new DatagramSocket(); local = InetAddress.getLoopbackAddress(); }
            byte[] b = msg.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, local, 47700));
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) { lastHealth = -1; lastLevel = -1; dead = false; return; }
        float hp = p.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.01F && hp > 0) send(lastHealth - hp >= 4 ? "buzz bighit" : "buzz hit");
        if (hp <= 0 && !dead) { dead = true; send("buzz death"); }
        if (hp > 0) dead = false;
        if (lastLevel >= 0 && p.experienceLevel > lastLevel) send("buzz levelup");
        lastHealth = hp;
        lastLevel = p.experienceLevel;
        if (++tick % 10 == 0) send("hp " + hp + " " + p.getMaxHealth());
    }

    /** any click inside a menu or inventory */
    @SubscribeEvent
    public void menuClick(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) send("buzz menu");
    }

    @SubscribeEvent
    public void ate(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntityLiving() == Minecraft.getMinecraft().player && e.getItem().getItem() instanceof ItemFood) send("buzz eat");
    }

    @SubscribeEvent
    public void pickup(EntityItemPickupEvent e) {
        EntityPlayer p = e.getEntityPlayer();
        if (p != null && Minecraft.getMinecraft().player != null && p.getUniqueID().equals(Minecraft.getMinecraft().player.getUniqueID())) send("buzz pickup");
    }

    @SubscribeEvent
    public void advancement(AdvancementEvent e) {
        if (e.getAdvancement().getDisplay() != null && Minecraft.getMinecraft().player != null
                && e.getEntityPlayer().getUniqueID().equals(Minecraft.getMinecraft().player.getUniqueID())) send("buzz achievement");
    }
}
