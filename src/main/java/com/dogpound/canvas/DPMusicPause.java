package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * One "pause music" switch shared by the loading screen and the main menu (requested feature).
 *
 * Paused = a flag file that remembers her old Music slider. While it exists: the loading song never
 * starts, the main-menu song stops, and in game the Music slider is held at 0 (that silences vanilla
 * music AND the Pride songs, which both follow the slider). Un-pausing puts the old slider back.
 * A file, not dpcanvas.cfg, because the loading screen runs before Forge's config is loaded.
 */
public final class DPMusicPause {
    private static final File FLAG = new File("config/dpcanvas-music-paused.txt");

    private static volatile float pendingRestore = -1f;   // un-paused on the loading screen -> restore the slider once the game is up

    private DPMusicPause() {}

    public static boolean paused() { return FLAG.isFile(); }

    private static volatile long lastToggle = 0;

    /** Pause/unpause from anywhere: the splash thread (no game settings yet) or the menu.
     *  Ignores repeats within 1.5 s — on the loading screen one click/keypress can register twice,
     *  which paused and instantly un-paused the music (bug report report 2026-10-03). */
    public static void toggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggle < 1500) return;
        lastToggle = now;
        if (paused()) unpause(); else pause();
    }

    public static void pause() {
        // On the loading screen the game's settings aren't loaded yet, so we can't read the player's real Music slider:
        // write "?" and let the first game tick record the real value (otherwise un-pausing later jumped to 100%).
        String remember = "?";
        Minecraft mc = Minecraft.getMinecraft();
        if (ready(mc)) {
            float old = mc.gameSettings.getSoundLevel(SoundCategory.MUSIC);
            remember = Float.toString(old > 0f ? old : 1f);
        }
        write(remember);
        DPMusic.setVolume(0f);
        DPMusic.fadeOut(400);
        holdSilent();   // no-op on the loading screen; the client tick applies it once the game is up
    }

    private static void write(String v) {
        try {
            FLAG.getParentFile().mkdirs();
            Files.write(FLAG.toPath(), v.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    private static String stored() {
        try { return new String(Files.readAllBytes(FLAG.toPath()), StandardCharsets.UTF_8).trim(); } catch (Throwable t) { return "?"; }
    }

    public static void unpause() {
        float old = 1f;
        try { old = Float.parseFloat(stored()); } catch (Throwable ignored) {}
        FLAG.delete();
        DPMusic.cancelFade();
        Minecraft mc = Minecraft.getMinecraft();
        if (ready(mc)) {
            mc.gameSettings.setSoundLevel(SoundCategory.MUSIC, old);
            mc.gameSettings.saveOptions();
            boolean menu = mc.world == null;
            DPMusic.setVolume(mc.gameSettings.getSoundLevel(SoundCategory.MASTER) * old);
            if (menu && DPConfig.menuMusic && !DPMusic.playing()) DPMusic.start(DPMusic.LOADING_PLAYLIST);
        } else {                                      // loading screen: play again now, fix the slider later
            pendingRestore = old;
            DPMusic.setVolume(old);
            DPMusic.start(DPMusic.LOADING_PLAYLIST);
        }
    }

    /** Called every client tick: while paused, keep the Music slider at 0. */
    static void holdSilent() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ready(mc)) return;
        if (pendingRestore >= 0f && !paused()) {
            mc.gameSettings.setSoundLevel(SoundCategory.MUSIC, pendingRestore);
            mc.gameSettings.saveOptions();
            pendingRestore = -1f;
        }
        if (!paused()) return;
        if (DPMusic.playing()) { DPMusic.setVolume(0f); DPMusic.fadeOut(400); }   // our own song too, not just Minecraft's
        float level = mc.gameSettings.getSoundLevel(SoundCategory.MUSIC);
        if (level <= 0f) return;
        // The player raised the Music slider in Options while music was OFF: that means "I want music" -> turn it back
        // ON and keep their new level (before, the slider snapped back to 0 every tick and looked broken).
        if (mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("sound")) {
            FLAG.delete();
            DPMusic.cancelFade();
            return;
        }
        if ("?".equals(stored())) write(Float.toString(level));   // paused on the loading screen: remember the real slider now
        mc.gameSettings.setSoundLevel(SoundCategory.MUSIC, 0f);
        mc.gameSettings.saveOptions();
    }

    /** Game settings + sound system exist and we're on the game thread (not the loading-screen thread). */
    private static boolean ready(Minecraft mc) {
        try { return mc != null && mc.gameSettings != null && mc.getSoundHandler() != null && mc.isCallingFromMinecraftThread(); }
        catch (Throwable t) { return false; }
    }

    public static String label() { return paused() ? "♫ Music: OFF" : "♫ Music: ON"; }
}
