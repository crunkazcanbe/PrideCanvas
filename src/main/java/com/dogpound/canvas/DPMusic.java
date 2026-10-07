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
 * Pride loading + menu music (requested feature). Starts from the coremod, seconds after launch, long before Minecraft's own sound system
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
            if (new File("config/dpcanvas-music-paused.txt").isFile()) return;   // she paused it (DPMusicPause)
            File cfg = new File("config/dpcanvas.cfg");
            if (cfg.isFile()) {
                for (String l : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8))
                    if (l.replace(" ", "").contains("\"LoadingMusic\"=false")) return;
            }
            setVolume(optionsVolume() * DPBootSettings.getInt("musicvol", 100) / 100f);   // Options > Loading Screen > Music
            ctl().put("shuffle", DPBootSettings.on("shuffle", false));
            ctl().put("repeat1", DPBootSettings.on("repeat1", false));
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

    /** undo a fadeOut() that hasn't finished yet (music toggled back on quickly) */
    public static void cancelFade() { ctl().remove("fadeStart"); }

    /** play the tracks in a loop until fadeOut(); no-op if already playing.
     *  Player controls (loading screen #10) go through ctl() too: "cmd" = next / prev / jump:N, "shuffle" = Boolean,
     *  "repeat1" = Boolean (repeat one); the thread publishes "playlist", "index", "track", "pos" + "len" (seconds). */
    public static synchronized void start(String... playlist) {
        Map<String, Object> c = ctl();
        if (c.get("thread") != null) return;
        c.remove("fadeStart");
        c.remove("cmd");
        c.put("playlist", playlist.clone());
        Thread t = new Thread(() -> {
            try {
                java.util.Random rnd = new java.util.Random();
                int n = playlist.length;
                int i = Boolean.TRUE.equals(c.get("shuffle")) && n > 1 ? rnd.nextInt(n) : 0;
                while (c.get("fadeStart") == null || !faded(c)) {
                    c.put("index", i);
                    if (!play(playlist[i], c)) Thread.sleep(500);
                    Object cmd = c.remove("cmd");
                    if ("prev".equals(cmd)) i = (i - 1 + n) % n;
                    else if (cmd instanceof String && ((String) cmd).startsWith("jump:")) i = Math.floorMod(Integer.parseInt(((String) cmd).substring(5)), n);
                    else if (cmd == null && Boolean.TRUE.equals(c.get("repeat1"))) { /* same song again */ }
                    else if (Boolean.TRUE.equals(c.get("shuffle")) && n > 1) { int j; do j = rnd.nextInt(n); while (j == i); i = j; }
                    else i = (i + 1) % n;
                }
            } catch (Throwable ignored) {
            } finally { c.remove("thread"); c.remove("fadeStart"); c.remove("track"); }
        }, "Pride Music");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY - 1);   // the loading screen hammers every core; music must not stutter
        c.put("thread", t);
        t.start();
    }

    static final int VIZ_RING = 256, VIZ_BANDS = 16;

    @SuppressWarnings("unchecked")
    private static <T> T viz(Map<String, Object> c, String k, T fresh) {
        Object o = c.get(k);
        if (o == null || o.getClass() != fresh.getClass()) { c.put(k, fresh); return fresh; }
        return (T) o;
    }

    /** send a player command (next / prev / jump:N); the song stops at once and the music thread picks the next one */
    public static void command(String cmd) { ctl().put("cmd", cmd); }

    /** "pride_theme" -> "Pride Theme" */
    public static String title(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder();
        for (String w : name.split("_")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    /** song length in samples from the last Ogg page's granule position (-1 if unknown) */
    private static long oggSamples(byte[] d, int len) {
        for (int p = len - 27; p >= 0; p--) {
            if (d[p] == 'O' && d[p + 1] == 'g' && d[p + 2] == 'g' && d[p + 3] == 'S') {
                long g = 0;
                for (int k = 7; k >= 0; k--) g = (g << 8) | (d[p + 6 + k] & 0xFF);
                return g;
            }
        }
        return -1;
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
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(1 << 20);
        try (InputStream raw = in) { byte[] b = new byte[16384]; int r; while ((r = raw.read(b)) > 0) bo.write(b, 0, r); }
        byte[] data = bo.toByteArray();
        long totalSamples = oggSamples(data, data.length);
        c.put("track", name);
        c.put("pos", 0f);
        try (InputStream is = new java.io.ByteArrayInputStream(data)) {
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
            c.put("len", totalSamples > 0 ? totalSamples / (float) vi.rate : -1f);
            int ch = vi.channels;
            AudioFormat fmt = new AudioFormat(vi.rate, 16, ch, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, vi.rate * ch * 2 * 3);   // 3 s buffer: rides out GC pauses while the pack loads (she heard cut-outs at 0.5 s)
            line.start();
            byte[] out = new byte[8192 * ch * 2];
            // visualizer (loading screen #27): 16 bands per 1024-frame block, tagged with the frame they belong to,
            // so the screen can show what you HEAR (the line buffers ~3 s ahead)
            long[] vizPos = viz(c, "vizPos", new long[VIZ_RING]);
            float[][] vizBands = viz(c, "vizBands", new float[VIZ_RING][VIZ_BANDS]);
            int[] vizHead = viz(c, "vizHead", new int[1]);
            java.util.Arrays.fill(vizPos, Long.MAX_VALUE);           // new song: forget the last one's blocks
            float[] coef = new float[VIZ_BANDS];
            for (int b = 0; b < VIZ_BANDS; b++) coef[b] = (float) (2 * Math.cos(2 * Math.PI * (60 * Math.pow(200, b / (double) (VIZ_BANDS - 1))) / vi.rate));
            float[] mono = new float[1024];
            int monoN = 0;
            long written = 0;
            c.put("rate", vi.rate);
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
                            if (c.containsKey("cmd")) { line.flush(); return true; }   // next / prev / jump: cut now
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
                            for (int j = 0; j < len; j++) {
                                float m = 0;
                                for (int k = 0; k < ch; k++) m += pcm[k][index[k] + j];
                                mono[monoN++] = m / ch;
                                if (monoN == mono.length) {
                                    int hd = (vizHead[0] + 1) % VIZ_RING;
                                    float[] bands = vizBands[hd];
                                    for (int b = 0; b < VIZ_BANDS; b++) {           // Goertzel: one frequency's strength
                                        float s1 = 0, s2 = 0, cf = coef[b];
                                        for (int q = 0; q < monoN; q++) { float s0 = mono[q] + cf * s1 - s2; s2 = s1; s1 = s0; }
                                        float pw = s1 * s1 + s2 * s2 - cf * s1 * s2;
                                        bands[b] = (float) Math.min(1.0, Math.sqrt(Math.max(0, pw)) / 180.0);
                                    }
                                    vizPos[hd] = written + j;
                                    vizHead[0] = hd;
                                    monoN = 0;
                                }
                            }
                            written += len;
                            line.write(out, 0, len * ch * 2);
                            vd.synthesis_read(len);
                            c.put("pos", line.getLongFramePosition() / (float) vi.rate);   // what you hear, not what's buffered
                            c.put("posFrames", line.getLongFramePosition());
                            c.put("posAt", System.currentTimeMillis());
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
