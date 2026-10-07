package com.dogpound.canvas;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loading-screen settings (her roadmap's [Settings] tab). The boot splash runs before Forge reads any config, so these
 * live in their own little file: config/pride-boot.txt (key=value). Opened from the "Settings" button on the splash.
 */
final class DPBootSettings {
    private DPBootSettings() {}

    private static final File FILE = new File("config/pride-boot.txt");
    private static final Map<String, String> V = new LinkedHashMap<String, String>();
    private static boolean loaded;
    static boolean open;

    // ---- the choices
    static final String[] BG = {"Animated", "Still", "Procedural", "Slideshow", "Dark"};

    static String get(String k, String def) { load(); String v = V.get(k); return v == null ? def : v; }
    static int getInt(String k, int def) { try { return Integer.parseInt(get(k, Integer.toString(def))); } catch (Throwable t) { return def; } }
    static boolean on(String k, boolean def) { return Boolean.parseBoolean(get(k, Boolean.toString(def))); }
    /** a part of the screen that is on unless switched off in Options > Loading Screen */
    static boolean show(String k) { return on(k, true); }

    /** Options > Loading Screen > Reset: forget every choice */
    static void reset() { load(); V.clear(); try { Files.deleteIfExists(FILE.toPath()); } catch (Throwable ignored) { } }

    static void set(String k, Object v) {
        load();
        V.put(k, String.valueOf(v));
        StringBuilder b = new StringBuilder("# Pride loading screen settings (changed from the Settings button while loading)\n");
        for (Map.Entry<String, String> e : V.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        try { FILE.getParentFile().mkdirs(); Files.write(FILE.toPath(), b.toString().getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            for (String l : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8)) {
                int eq = l.indexOf('=');
                if (eq > 0 && !l.startsWith("#")) V.put(l.substring(0, eq).trim(), l.substring(eq + 1).trim());
            }
        } catch (Throwable ignored) { }
    }

    static int bg() { return Math.max(0, Math.min(BG.length - 1, getInt("background", 0))); }

    /** a "Label  < value >" row that cycles through options on click; returns the new index */
    private static int cycle(int x, int y, int w, String label, String[] opts, int cur) {
        DPBootUI.text(label, x, y + 2, DPBootUI.BLUE);
        int bw = Math.min(170, w - 120);
        if (DPBootUI.button(x + w - bw, y, bw, 12, "< " + opts[cur] + " >", false)) return (cur + 1) % opts.length;
        return cur;
    }

    private static boolean toggle(int x, int y, int w, String label, String key, boolean def) {
        boolean v = on(key, def);
        DPBootUI.text(label, x, y + 2, DPBootUI.BLUE);
        int bw = Math.min(170, w - 120);
        if (DPBootUI.button(x + w - bw, y, bw, 12, v ? "ON" : "off", v)) { set(key, !v); return !v; }
        return v;
    }

    /** the settings panel in the middle of the splash; false when closed */
    static boolean draw(int x, int y, int w, int h) {
        if (!open) return false;
        int cy = DPBootUI.panel(x, y, w, h, "Loading screen settings");
        if (DPBootUI.button(x + w - 16, y + 3, 13, 11, "x", false)) { open = false; return false; }
        int rx = x + 8, rw = Math.min(w - 16, 420), ry = cy + 2, step = 16;
        int b = cycle(rx, ry, rw, "Background", BG, bg());
        if (b != bg()) set("background", b);
        DPBootUI.text(BG_HELP[bg()], rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        toggle(rx, ry, rw, "Turbo (fastest loading)", "turbo", false);
        DPBootUI.text("10 fps, still picture, no side panels, loading thread first", rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        toggle(rx, ry, rw, "Terminal boot mode", "terminal", false);
        DPBootUI.text("black screen, [  OK  ] lines like a real computer booting", rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        int th = cycle(rx, ry, rw, "Theme", DPBootTheme.NAMES, Math.max(0, Math.min(DPBootTheme.NAMES.length - 1, getInt("theme", 0))));
        if (th != getInt("theme", 0)) set("theme", th);
        DPBootUI.text("Custom = config/pride-boot-theme.json (panel, accent, accent2, text, dim, bar[])", rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        toggle(rx, ry, rw, "Sky follows progress", "sunrise", true);
        DPBootUI.text("night -> dawn -> day as it loads, rain while it's stuck", rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        toggle(rx, ry, rw, "Music visualizer", "visualizer", true);
        DPBootUI.text("bars in the music player + the wallpaper glows with the bass", rx + 10, ry + 13, DPBootUI.DIM);
        ry += step + 10;
        ry = extra(rx, ry, rw, step);
        DPBootUI.text("Saved in config/pride-boot.txt - Esc or x to close", rx, y + h - 12, DPBootUI.DIM);
        if (!DPBootUI.searchFocus) for (int i = 0; i < DPBootUI.nKeys; i++) if (DPBootUI.KEYS[i] == org.lwjgl.input.Keyboard.KEY_ESCAPE) open = false;
        return true;
    }

    private static final String[] BG_HELP = {"the Pride wallpaper, moving", "one frame - the cheapest picture",
            "rainbow aurora drawn live", "your pictures from config/pride-backgrounds/ (png / jpg), every 12 s", "plain dark - fastest of all"};

    /** later items add rows here */
    private static int extra(int x, int y, int w, int step) { return y; }
}
