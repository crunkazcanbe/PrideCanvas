package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * Pride menu sounds (her ask 2026-09-30: "pretty sounds when you click a button or hover"). The sounds are
 * synthesized by tools/make_ui_sounds.py (glass chimes + soft whooshes), not taken from anywhere.
 * Volume and on/off live in the config; hover sounds are rate-limited so sweeping the mouse isn't noisy.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = DPMenuMod.MODID)
public final class DPSounds {
    public static final SoundEvent HOVER = ev("ui.hover"), CLICK = ev("ui.click"), BACK = ev("ui.back"), OPEN = ev("ui.open"),
            CLOSE = ev("ui.close"), POPUP = ev("ui.popup"), CONFIRM = ev("ui.confirm");
    private static final Random RNG = new Random();
    private static long lastHover;

    private static SoundEvent ev(String name) {
        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, name);
        return new SoundEvent(rl).setRegistryName(rl);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(HOVER, CLICK, BACK, OPEN, CLOSE, POPUP, CONFIRM);
    }

    public static void play(SoundEvent s) { play(s, 1f, 1f); }

    /** volume is scaled by the config's UI volume; pitch gets a hair of randomness so repeats don't sound robotic */
    public static void play(SoundEvent s, float pitch, float volume) {
        if (!DPConfig.uiSounds || DPConfig.uiVolume <= 0) return;
        try {
            float p = pitch * (0.97f + RNG.nextFloat() * 0.06f);
            Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(s.getSoundName(), net.minecraft.util.SoundCategory.MASTER,
                    (float) DPConfig.uiVolume * volume, p, false, 0, net.minecraft.client.audio.ISound.AttenuationType.NONE, 0f, 0f, 0f));
        } catch (Throwable ignored) {}
    }

    /** hover chime: rate-limited, pitch climbs a little along a row (index) so moving across tiles plays a scale */
    public static void hover(int index) {
        if (!DPConfig.hoverSounds) return;
        long t = System.currentTimeMillis();
        if (t - lastHover < 45) return;
        lastHover = t;
        float[] scale = {1f, 1.122f, 1.26f, 1.335f, 1.498f, 1.682f, 1.888f, 2f};   // major scale steps
        play(HOVER, scale[Math.floorMod(index, scale.length)] * 0.9f, 1f);
    }
}
