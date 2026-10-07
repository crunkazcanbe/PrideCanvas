package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Options > Loading Screen: every loading-screen setting, page by page (requested feature).
 * Everything lands in config/pride-boot.txt (DPBootSettings) because the splash runs before Forge reads any config;
 * the few switches that already lived in dpcanvas.cfg (Pride loading screen, loading/menu music) stay there.
 */
public class DPBootOptionsScreen extends GuiScreen {
    private final GuiScreen parent;
    public DPBootOptionsScreen(GuiScreen parent) { this.parent = parent; }

    static final String[][] TABS = {
            {"Look", "background, theme, sky, logo, layout"}, {"Speed", "Turbo, FPS cap, adaptive quality"},
            {"Panels", "every box on the screen"}, {"Games", "which games + Explore views show"},
            {"Music", "loading music, volume, shuffle"}, {"World loading", "the screen when a world opens"},
            {"Startup", "safe mode, reports, history"}};
    private static int tab = 0;
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int px, pw, oy, top, bottom, scroll, contentH, lastMx;
    private String hint = "";
    private String dragKey; private int dragX, dragW, dragMin, dragMax; private Consumer<Integer> dragSet;

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear(); hint = "";
        PrideFrame f = PrideFrame.fit(width, height);
        f.draw(this, "Loading Screen", "§7theme: §d" + DPBootTheme.NAMES[clampTheme()] + (DPBootSettings.on("turbo", false) ? "  §e⚡ Turbo" : ""));
        int tw = Math.min(130, Math.max(96, f.cw / 6)), ty = f.cy, th = Math.max(16, Math.min(28, (f.ch - 30) / TABS.length - 3));
        for (int i = 0; i < TABS.length; i++) {
            boolean over = in(mx, my, f.cx, ty, tw, th), on = tab == i;
            PrideFrame.tile(f.cx, ty, tw, th, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, on);
            fontRenderer.drawStringWithShadow(TABS[i][0], f.cx + 6, ty + (th > 22 ? 4 : (th - 8) / 2f), on ? 0xFFFFFF : 0xD8D0E8);
            if (th > 22) fontRenderer.drawString(fontRenderer.trimStringToWidth(TABS[i][1], tw - 10), f.cx + 6, ty + 15, 0x8A8499);
            if (over) hint = TABS[i][1];
            final int k = i;
            hits.add(new Object[]{ f.cx, ty, tw, th, (Runnable) () -> { tab = k; scroll = 0; } });
            ty += th + 3;
        }
        px = f.cx + tw + 12; pw = f.cx + f.cw - px - 6; top = f.cy; bottom = f.cy + f.ch - 30;
        PrideFrame.card(px - 6, top - 2, pw + 12, bottom - top + 4, PrideFrame.RAINBOW[tab % PrideFrame.RAINBOW.length]);
        PrideFrame.clip(px - 6, top, pw + 12, bottom - top);
        oy = top + 6 - scroll;
        int start = hits.size();
        page(mx, my);
        contentH = oy + scroll - top;
        PrideFrame.unclip();
        for (int i = hits.size() - 1; i >= start; i--) { Object[] h = hits.get(i); int y = (Integer) h[1]; if (y + (Integer) h[3] < top || y > bottom) hits.remove(i); }
        PrideFrame.scrollbar(px + pw + 3, top, bottom - top, scroll, bottom - top, contentH);
        int by = f.y + f.h - 24;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint.isEmpty() ? "§8Changes save right away and show at the next start. Hover anything to see what it does." : "§7" + hint, f.cw - 190), f.cx, by + 5, 0xFFFFFF);
        button(mx, my, f.cx + f.cw - 180, by, 96, 18, "Reset all", 0xFF8A2238, DPBootSettings::reset, "Put every loading-screen option back to how it started");
        button(mx, my, f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, () -> mc.displayGuiScreen(parent), null);
        super.drawScreen(mx, my, pt);
    }

    private static int clampTheme() { return Math.max(0, Math.min(DPBootTheme.NAMES.length - 1, DPBootSettings.getInt("theme", 0))); }

    private void page(int mx, int my) {
        switch (tab) {
            case 0:
                head("Look", "How the loading screen looks while Minecraft starts.");
                pick(mx, my, "Background", DPBootSettings.BG, DPBootSettings.bg(), v -> DPBootSettings.set("background", v), "What's behind everything");
                info("§8" + new String[]{"the Pride wallpaper, moving", "one frame - the cheapest picture", "rainbow aurora drawn live",
                        "your pictures from config/pride-backgrounds/ (png / jpg)", "plain dark - fastest of all"}[DPBootSettings.bg()]);
                slider(mx, my, "Seconds per picture", "slidesecs", 2, 60, () -> DPBootSettings.getInt("slidesecs", 12), v -> DPBootSettings.set("slidesecs", v), "How long each of your pictures stays up");
                button(mx, my, px, oy, 210, 16, "Open my backgrounds folder", PrideFrame.BUTTON, () -> openDir(new File(mc.mcDataDir, "config/pride-backgrounds")), "Drop png / jpg pictures here for the Slideshow background"); oy += 22;
                pick(mx, my, "Theme", DPBootTheme.NAMES, clampTheme(), v -> DPBootSettings.set("theme", v), "Colours of the panels, bars and text");
                button(mx, my, px, oy, 210, 16, "Open the Custom theme file", PrideFrame.BUTTON, this::openThemeFile, "config/pride-boot-theme.json: panel, accent, accent2, text, dim, bar[] (hex colours)"); oy += 22;
                tog(mx, my, "Theme effects (CRT scanlines, Matrix rain)", "themefx", true, "Extra effects some themes draw on top");
                tog(mx, my, "Terminal boot mode", "terminal", false, "Black screen with [ OK ] lines like a real computer booting");
                tog(mx, my, "Sky follows progress", "sunrise", true, "Night -> dawn -> day as it loads, rain while it's stuck");
                tog(mx, my, "Music visualizer", "visualizer", true, "Bars in the music player + the wallpaper glows with the bass");
                tog(mx, my, "Pride logo at the top", "logo", true, "The big Pride logo");
                pick(mx, my, "Layout", new String[]{"Auto", "Compact", "Full"}, Math.max(0, Math.min(2, DPBootSettings.getInt("layout", 0))), v -> DPBootSettings.set("layout", v),
                        "Auto = compact in small windows (under 1600 px wide), full on big screens");
                break;
            case 1:
                head("Speed", "The loading screen must never make loading slower. These trade looks for speed.");
                tog(mx, my, "⚡ Turbo (fastest loading)", "turbo", false, "10 fps, still picture, no side panels, the loading thread goes first");
                tog(mx, my, "Adaptive quality", "adaptive", true, "Draws less on its own when the screen gets expensive (never slows loading)");
                slider(mx, my, "Max frames per second", "fps", 5, 60, () -> DPBootSettings.getInt("fps", 30), v -> DPBootSettings.set("fps", v), "Lower = more CPU for loading. 30 is smooth, 10 is Turbo speed");
                info("§8Turbo and adaptive quality can only lower this number, never raise it.");
                break;
            case 2:
                head("Panels", "Turn single boxes on the loading screen on or off.");
                tog(mx, my, "Dashboard (phase, mod, ETA, speed...)", "dash", true, "The 'Pride Boot Sequence' box top-left (the big card in compact layout)");
                tog(mx, my, "Slowest mods", "slowest", true, "Live list of the mods taking longest");
                tog(mx, my, "Play while you wait (games + Explore)", "games", true, "Mini games and the mod web / facts / dev views");
                tog(mx, my, "What's New", "news", true, "Mods added, removed and updated since last start");
                tog(mx, my, "Music player", "player", true, "Song, progress, prev / next, shuffle (the Music ON/OFF switch always stays)");
                tog(mx, my, "System / Java / Threads column", "system", true, "CPU, RAM, GPU, disk, net, heap graph, busiest threads");
                tog(mx, my, "Problem cards", "problems", true, "'⚠ Potential problem' cards for errors and missing mods");
                tog(mx, my, "'What's taking so long?' card", "stall", true, "Shows after 10 s without progress");
                tog(mx, my, "Achievement toasts", "toasts", true, "'1,000 blocks registered!' pop-ups");
                tog(mx, my, "Fun tips line", "tips", true, "The tip under the big box");
                tog(mx, my, "Clock", "clock", true, "Time of day, bottom-left");
                tog(mx, my, "Playtime", "playtime", true, "Hours played in this pack + this start, bottom-right");
                tog(mx, my, "Log", "log", true, "The scrolling log in the big box");
                tog(mx, my, "Memory bar", "membar", true, "MEMORY % bar above the LOAD bar");
                tog(mx, my, "Time left (ETA)", "eta", true, "'about 3m left' at the end of the LOAD bar");
                break;
            case 3:
                head("Games", "Which mini games and Explore views show in 'Play while you wait'.");
                for (String g : DPBootGames.NAMES) tog(mx, my, g, "g." + g, true, "Show " + g);
                head("Explore", "");
                for (String v : DPBootViews.NAMES) tog(mx, my, v, "v." + v, true, "Show the " + v + " view");
                button(mx, my, px, oy, 160, 16, "Clear high scores", 0xFF8A2238, () -> new File(mc.mcDataDir, "config/pride-boot-games.txt").delete(), "Forget every best score"); oy += 22;
                break;
            case 4:
                head("Music", "The chiptune songs that play while Minecraft starts.");
                cfgTog(mx, my, "Loading music", () -> DPConfig.loadingMusic, () -> DPConfig.loadingMusic = !DPConfig.loadingMusic, "Play music from the very first loading bar");
                cfgTog(mx, my, "Keep playing on the main menu", () -> DPConfig.menuMusic, () -> DPConfig.menuMusic = !DPConfig.menuMusic, "Off = it fades out when the menu shows");
                slider(mx, my, "Loading music volume %", "musicvol", 0, 100, () -> DPBootSettings.getInt("musicvol", 100), v -> DPBootSettings.set("musicvol", v), "On top of your Master and Music sliders");
                tog(mx, my, "Shuffle", "shuffle", false, "Start on a random song and keep mixing them");
                tog(mx, my, "Repeat one song", "repeat1", false, "Play the same song over and over");
                tog(mx, my, "Music visualizer", "visualizer", true, "Bars in the music player + the wallpaper glows with the bass");
                tog(mx, my, "Music player panel", "player", true, "The player box on the loading screen");
                break;
            case 5:
                head("World loading", "The screen while a world is created or opened.");
                tog(mx, my, "Spinner", "w.spinner", true, "The spinning ring under the logo");
                tog(mx, my, "World stats card", "w.stats", true, "Ping, download, chunks, entities, generation speed");
                tog(mx, my, "Live mini map", "w.map", true, "Chunks appearing as the world generates");
                tog(mx, my, "Log", "w.log", true, "The log lines in the box");
                tog(mx, my, "Memory bar", "membar", true, "Shared with the startup screen");
                tog(mx, my, "Time left (ETA)", "eta", true, "Shared with the startup screen");
                break;
            default:
                head("Startup", "What happens around loading.");
                cfgTog(mx, my, "Pride loading screen", () -> DPConfig.enableLoading, () -> DPConfig.enableLoading = !DPConfig.enableLoading, "Off = the plain Forge loading screen");
                tog(mx, my, "Ask about Safe mode after failed starts", "safemode", true, "After 2 starts that never reached the menu, offer to turn changed mods off");
                tog(mx, my, "'Loaded in ...' chip on the main menu", "loadedin", true, "Bottom-left of the menu, click it for the full report");
                button(mx, my, px, oy, 180, 16, "⏱ Open the last loading report", PrideFrame.BUTTON, () -> mc.displayGuiScreen(new DPBootReport(this)), "Why did Minecraft take so long?"); oy += 22;
                head("Loading history", "");
                for (String l : DPBoot.historyLines()) info(l);
                button(mx, my, px, oy, 160, 16, "Clear loading history", 0xFF8A2238, () -> new File(mc.mcDataDir, "config/pride-boot-history.txt").delete(), "The ETA starts learning again from the next start"); oy += 22;
                break;
        }
    }

    private void openDir(File d) { d.mkdirs(); DPSafeMode.openFolder(d); }

    private void openThemeFile() {
        File f = new File(mc.mcDataDir, "config/pride-boot-theme.json");
        try {
            if (!f.isFile()) java.nio.file.Files.write(f.toPath(), ("{\n  \"panel\": \"#140E22\",\n  \"accent\": \"#F5A9B8\",\n  \"accent2\": \"#5BCEFA\",\n"
                    + "  \"text\": \"#FFFFFF\",\n  \"dim\": \"#8A8499\",\n  \"bar\": [\"#E40303\", \"#FF8C00\", \"#FFED00\", \"#008026\", \"#24408E\", \"#732982\"]\n}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            net.minecraft.client.renderer.OpenGlHelper.openFile(f);
        } catch (Throwable t) { System.out.println("[PrideCanvas] couldn't open " + f + ": " + t); }
        DPBootSettings.set("theme", DPBootTheme.NAMES.length - 1);   // Custom
    }

    private static void syncCfg() { net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE); }

    // ------------------------------------------------------------------ widgets (same look as HUD Settings)
    private void head(String t, String sub) {
        fontRenderer.drawStringWithShadow("§l" + t, px, oy, 0xF5A9B8); oy += 12;
        if (!sub.isEmpty()) for (String l : fontRenderer.listFormattedStringToWidth(sub, pw - 8)) { fontRenderer.drawString(l, px, oy, 0xA79FBF); oy += 10; }
        oy += 6;
    }

    private void info(String t) { for (String l : fontRenderer.listFormattedStringToWidth(t, pw - 8)) { fontRenderer.drawStringWithShadow(l, px, oy, 0xC8C0D8); oy += 10; } oy += 4; }

    private void tog(int mx, int my, String label, String key, boolean def, String tip) {
        toggle(mx, my, label, () -> DPBootSettings.on(key, def), () -> DPBootSettings.set(key, !DPBootSettings.on(key, def)), tip);
    }

    private void cfgTog(int mx, int my, String label, BooleanSupplier v, Runnable r, String tip) {
        toggle(mx, my, label, v, () -> { r.run(); syncCfg(); }, tip);
    }

    private void toggle(int mx, int my, String label, BooleanSupplier v, Runnable r, String tip) {
        boolean on = v.getAsBoolean(), over = in(mx, my, px, oy, pw, 16);
        if (over) { Gui.drawRect(px - 2, oy - 1, px + pw, oy + 15, 0x30FFFFFF); hint = tip; }
        Gui.drawRect(px, oy + 4, px + 16, oy + 12, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? px + 9 : px + 1, oy + 5, on ? px + 15 : px + 7, oy + 11, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, pw - 30), px + 22, oy + 4, on ? 0xFFFFFF : 0xA79FBF);
        hits.add(new Object[]{ px, oy, pw, 16, r });
        oy += 18;
    }

    private void slider(int mx, int my, String label, String key, int min, int max, IntSupplier get, Consumer<Integer> set, String tip) {
        int v = Math.max(min, Math.min(max, get.getAsInt()));
        int lw = Math.min(220, pw / 2), x = px + lw, w = pw - lw - 40;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, lw - 6), px, oy + 3, 0xFFFFFF);
        boolean over = in(mx, my, x - 3, oy, w + 6, 14);
        Gui.drawRect(x, oy + 6, x + w, oy + 8, 0xFF3D2168);
        int kx = x + (int) ((long) (v - min) * w / Math.max(1, max - min));
        Gui.drawRect(x, oy + 6, kx, oy + 8, PrideFrame.PINK);
        Gui.drawRect(kx - 3, oy + 1, kx + 3, oy + 13, over || key.equals(dragKey) ? 0xFFFFFFFF : 0xFFE0D8F0);
        fontRenderer.drawStringWithShadow(String.valueOf(v), x + w + 8, oy + 3, 0xF5A9B8);
        if (over) hint = tip + "  §8(drag, or scroll over it)";
        final int fx = x, fw = w;
        hits.add(new Object[]{ x - 3, oy, w + 6, 14, (Runnable) () -> { dragKey = key; dragX = fx; dragW = fw; dragMin = min; dragMax = max; dragSet = set; drag(lastMx); }, key, min, max, set, get });
        oy += 18;
    }

    /** a row of chips, one per choice; the picked one is lit */
    private void pick(int mx, int my, String label, String[] opts, int cur, Consumer<Integer> set, String tip) {
        fontRenderer.drawString("§7" + label, px, oy + 4, 0xFFFFFF);
        int lx = px + 80, x = lx;
        for (int i = 0; i < opts.length; i++) {
            int w = fontRenderer.getStringWidth(opts[i]) + 10;
            if (x + w > px + pw) { x = lx; oy += 17; }
            boolean on = i == cur, over = in(mx, my, x, oy, w, 15);
            Gui.drawRect(x, oy, x + w, oy + 15, on ? 0xFF6A3FA0 : over ? 0xFF3A2E52 : 0xFF241C33);
            fontRenderer.drawStringWithShadow(opts[i], x + 5, oy + 4, on ? 0xFFFFFF : 0xC8C0D8);
            if (over) hint = tip;
            final int k = i;
            hits.add(new Object[]{ x, oy, w, 15, (Runnable) () -> set.accept(k) });
            x += w + 3;
        }
        oy += 21;
    }

    private void button(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String tip) {
        if (PrideFrame.button(x, y, w, h, label, color, mx, my) && tip != null) hint = tip;
        hits.add(new Object[]{ x, y, w, h, r });
    }

    private void drag(int mx) { if (dragKey != null) dragSet.accept(Math.max(dragMin, Math.min(dragMax, dragMin + Math.round((float) (mx - dragX) * (dragMax - dragMin) / Math.max(1, dragW))))); }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        lastMx = mx;
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                ((Runnable) h[4]).run();
                DPSounds.play(DPSounds.CLICK, 1.1F, 0.6F);
                return;
            }
        }
    }

    @Override protected void mouseClickMove(int mx, int my, int b, long t) { lastMx = mx; drag(mx); }
    @Override protected void mouseReleased(int mx, int my, int s) { dragKey = null; }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1, d = wheel > 0 ? 1 : -1;
        for (Object[] h : hits)
            if (h.length > 9 && in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                int v = ((IntSupplier) h[9]).getAsInt() + d * (isShiftKeyDown() ? 5 : 1);
                ((Consumer<Integer>) h[8]).accept(Math.max((Integer) h[6], Math.min((Integer) h[7], v)));
                return;
            }
        scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top) + 8), scroll - d * 24));
    }
}
