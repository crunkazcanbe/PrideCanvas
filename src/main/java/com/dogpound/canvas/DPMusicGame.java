package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Random;

/**
 * The game side of Pride music (her ask 2026-10-01: "several different songs for while you're playing").
 *  - keeps the loading/menu player (DPMusic) in step with the Master × Music sliders, fades it when a world loads,
 *    and starts it again when she's back on the main menu after quitting a world
 *  - mutes vanilla menu music while ours plays (no two songs at once)
 *  - when vanilla wants a game/creative/Nether/End song, plays a Pride song for where the player is instead (chance in
 *    config): caves underground, the night song at night, the town song near villagers, the day song otherwise
 * Songs were made with her local MiniMax Music 3 (~/mc-mods/pride-music).
 */
@Mod.EventBusSubscriber(modid = DPMenuMod.MODID, value = Side.CLIENT)
public final class DPMusicGame {
    public static final SoundEvent DAY = ev("music.pride.day"), NIGHT = ev("music.pride.night"), CAVES = ev("music.pride.caves"),
            NETHER = ev("music.pride.nether"), END = ev("music.pride.end"), TOWN = ev("music.pride.town");
    private static final Random RNG = new Random();
    private static ISound current;
    private static int currentDim;
    private static boolean wasInWorld;

    private static SoundEvent ev(String name) {
        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, name);
        return new SoundEvent(rl).setRegistryName(rl);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(DAY, NIGHT, CAVES, NETHER, END, TOWN);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null) return;
        boolean player = DPMusic.playing();
        if (player) DPMusic.setVolume(mc.gameSettings.getSoundLevel(SoundCategory.MASTER) * mc.gameSettings.getSoundLevel(SoundCategory.MUSIC));
        boolean mainMenu = mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().contains("MainMenu");
        if (mc.world != null) {
            wasInWorld = true;
            if (player) DPMusic.fadeOut(4000);
            if (current != null && mc.player != null && mc.player.dimension != currentDim) {   // left the Nether mid-song
                mc.getSoundHandler().stopSound(current);
                current = null;
            }
        } else {
            current = null;
            if (mainMenu && !DPConfig.menuMusic && player) DPMusic.fadeOut(2500);
            else if (mainMenu && wasInWorld && DPConfig.menuMusic && !player) { wasInWorld = false; DPMusic.start(DPMusic.LOADING_PLAYLIST); }
        }
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent e) {
        ISound s = e.getSound();
        if (s == null || !"minecraft".equals(s.getSoundLocation().getResourceDomain())) return;
        String path = s.getSoundLocation().getResourcePath();
        if (!path.startsWith("music.")) return;
        if (DPMusic.playing()) { e.setResultSound(null); return; }   // our loading/menu song is still going
        if (!DPConfig.prideGameMusic || RNG.nextDouble() >= DPConfig.prideMusicChance) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        World w = mc.world;
        if (p == null || w == null) return;
        SoundEvent pick;
        switch (path) {
            case "music.nether": pick = NETHER; break;
            case "music.end": pick = END; break;
            case "music.game":
            case "music.creative": pick = overworld(p, w); break;
            default: return;   // menu, credits, dragon fight: leave vanilla's
        }
        current = PositionedSoundRecord.getMusicRecord(pick);
        currentDim = p.dimension;
        e.setResultSound(current);
    }

    private static SoundEvent overworld(EntityPlayer p, World w) {
        BlockPos pos = new BlockPos(p);
        if (p.posY < 56 && !w.canSeeSky(pos)) return CAVES;
        if (w.getEntitiesWithinAABB(EntityVillager.class, p.getEntityBoundingBox().grow(48)).size() >= 3) return TOWN;
        long t = w.getWorldTime() % 24000;
        return t >= 13000 && t < 23000 ? NIGHT : DAY;
    }

    private DPMusicGame() {}
}
