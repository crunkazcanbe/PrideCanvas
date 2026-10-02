package com.dogpound.canvas;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pride loading + menu music (her ask 2026-10-01: "play music while Minecraft is loading and at the main menu and have
 * it smoothly transition"). Starts from the coremod, seconds after launch, long before Minecraft's own sound system
 * exists, so it runs on its own thread: jorbis (ships with the game) decodes the OGG, javax.sound plays it. The same
 * track keeps going into the main menu and fades out when a world loads. Volume follows options.txt master × music
 * until the game is up, then the live sliders (DPMusicGame pushes them in).
 *
 * The coremod and the mod can load this class from different class loaders, so all shared state lives in one
 * java.util map stored in the system properties (only java.* types cross the boundary).
 */
public final class DPMusic {
    static final String[] LOADING_PLAYLIST = {"pride_theme", "night_stars", "day_meadow", "end_void"};
    private static final String KEY = "pride.music.ctl";

    @SuppressWarnings("unchecked")
    static Map<String, Object> ctl() {
        synchronized (System.getProperties()) {
            Object o = System.getProperties().get(KEY);
            if (o == null) System.getProperties().put(KEY, o = new ConcurrentHashMap<String, Object>());
            return (Map<String, Object>) o;
        }
    }

    /** called by the coremod: start the loading playlist (if enabled in config/dpcanvas.cfg) */
    public static void startFromCoremod() {
        try {
            File cfg = new File("config/dpcanvas.cfg");
            if (cfg.isFile()) {
                for (String l : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8))
                    if (l.replace(" ", "").contains("\"LoadingMusic\"=false")) return;
            }
            setVolume(optionsVolume());
            start(LOADING_PLAYLIST);
        } catch (Throwable ignored) {}
    }

    /** master × music from options.txt (the in-game sliders aren't loaded yet) */
    static float optionsVolume() {
        float master = 1f, music = 1f;
        try {
            for (String l : Files.readAllLines(new File("options.txt").toPath(), StandardCharsets.UTF_8)) {
                if (l.startsWith("soundCategory_master:")) master = Float.parseFloat(l.substring(21).trim());
                if (l.startsWith("soundCategory_music:")) music = Float.parseFloat(l.substring(20).trim());
            }
        } catch (Throwable ignored) {}
        return master * music;
    }

    public static boolean playing() { return ctl().get("thread") != null; }

    public static void setVolume(float v) { ctl().put("volume", Math.max(0f, Math.min(1f, v))); }

    /** fade out over the given time, then stop */
    public static void fadeOut(int ms) { ctl().putIfAbsent("fadeStart", System.currentTimeMillis()); ctl().put("fadeMs", ms); }

    /** play the tracks in a loop until fadeOut(); no-op if already playing */
    public static synchronized void start(String... playlist) {
        Map<String, Object> c = ctl();
        if (c.get("thread") != null) return;
        c.remove("fadeStart");
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; c.get("fadeStart") == null || !faded(c); i = (i + 1) % playlist.length)
                    if (!play(playlist[i], c)) Thread.sleep(500);
            } catch (Throwable ignored) {
            } finally { c.remove("thread"); c.remove("fadeStart"); }
        }, "Pride Music");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY - 1);   // the loading screen hammers every core; music must not stutter
        c.put("thread", t);
        t.start();
    }

    private static boolean faded(Map<String, Object> c) {
        Object s = c.get("fadeStart");
        return s != null && System.currentTimeMillis() - (Long) s >= ((Number) c.getOrDefault("fadeMs", 3000)).intValue();
    }

    private static float gain(Map<String, Object> c) {
        float v = ((Number) c.getOrDefault("volume", 1f)).floatValue();
        Object s = c.get("fadeStart");
        if (s != null) {
            int ms = ((Number) c.getOrDefault("fadeMs", 3000)).intValue();
            v *= Math.max(0f, 1f - (System.currentTimeMillis() - (Long) s) / (float) ms);
        }
        return v * v;   // sliders feel linear when the gain is squared
    }

    /** decode + play one track; returns false if it couldn't be opened */
    private static boolean play(String name, Map<String, Object> c) throws Exception {
        InputStream in = DPMusic.class.getResourceAsStream("/assets/dpcanvas/sounds/music/" + name + ".ogg");
        if (in == null) return false;
        try (InputStream is = in) {
            SyncState oy = new SyncState();
            StreamState os = new StreamState();
            Page og = new Page();
            Packet op = new Packet();
            Info vi = new Info();
            Comment vc = new Comment();
            DspState vd = new DspState();
            Block vb = new Block(vd);
            oy.init();
            // --- 3 header packets ---
            int got = 0;
            boolean streamInit = false;
            while (got < 3) {
                int idx = oy.buffer(4096);
                int n = is.read(oy.data, idx, 4096);
                if (n <= 0) return false;
                oy.wrote(n);
                while (got < 3 && oy.pageout(og) == 1) {
                    if (!streamInit) { os.init(og.serialno()); vi.init(); vc.init(); streamInit = true; }
                    os.pagein(og);
                    while (got < 3 && os.packetout(op) == 1) {
                        if (vi.synthesis_headerin(vc, op) < 0) return false;
                        got++;
                    }
                }
            }
            vd.synthesis_init(vi);
            vb.init(vd);
            int ch = vi.channels;
            AudioFormat fmt = new AudioFormat(vi.rate, 16, ch, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, vi.rate * ch * 2 * 3);   // 3 s buffer: rides out GC pauses while the pack loads (she heard cut-outs at 0.5 s)
            line.start();
            byte[] out = new byte[8192 * ch * 2];
            float[][][] pcmHolder = new float[1][][];
            int[] index = new int[ch];
            boolean eos = false;
            try {
                // packets already queued from the header pages
                while (!eos) {
                    while (os.packetout(op) == 1) {
                        if (vb.synthesis(op) == 0) vd.synthesis_blockin(vb);
                        int samples;
                        while ((samples = vd.synthesis_pcmout(pcmHolder, index)) > 0) {
                            if (faded(c)) return true;
                            float g = gain(c);
                            int len = Math.min(samples, out.length / (ch * 2));
                            float[][] pcm = pcmHolder[0];
                            for (int k = 0; k < ch; k++) {
                                int p = k * 2;
                                for (int j = 0; j < len; j++) {
                                    int v = (int) (pcm[k][index[k] + j] * 32767f * g);
                                    if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
                                    out[p] = (byte) v;
                                    out[p + 1] = (byte) (v >>> 8);
                                    p += ch * 2;
                                }
                            }
                            line.write(out, 0, len * ch * 2);
                            vd.synthesis_read(len);
                        }
                    }
                    int pr;
                    while ((pr = oy.pageout(og)) == 0) {
                        int idx = oy.buffer(4096);
                        int n = is.read(oy.data, idx, 4096);
                        if (n <= 0) { eos = true; break; }
                        oy.wrote(n);
                    }
                    if (pr == 1) os.pagein(og);
                }
                line.drain();
            } finally {
                line.stop();
                line.close();
                os.clear(); vb.clear(); vd.clear(); vi.clear(); oy.clear();
            }
        }
        return true;
    }

    private DPMusic() {}
}
