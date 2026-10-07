package com.dogpound.canvas;

import org.lwjgl.opengl.GL11;

/**
 * Pride Boot Sequence — drawing on the boot splash (raw GL11: the splash has its own GL context, so no GlStateManager
 * and no Minecraft GUI helpers). Pride look: dark purple panels, rainbow top bar, pink / blue accents.
 * Coordinates are the splash's scaled units (DPSplashHook scales by height/720).
 */
final class DPBootUI {
    private DPBootUI() {}

    // the palette - DPBootTheme swaps these (#32 themes)
    static int PINK = 0xFFF5A9B8, BLUE = 0xFF5BCEFA, WHITE = 0xFFFFFFFF, DIM = 0xFFB0A8C8, PANEL = 0xD0140E22;
    static final int GOOD = 0xFF8CE06A, WARN = 0xFFFFC040, BAD = 0xFFFF5A64;
    static int[] RAINBOW = {0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982, 0xFF5BCEFA, 0xFFF5A9B8};
    static final int LINE = 10, HEAD = 16;


    /** dark panel + rainbow bar + title; returns the y where content starts */
    static int panel(int x, int y, int w, int h, String title) {
        rect(x, y, x + w, y + h, PANEL);
        int sw = w / RAINBOW.length;
        for (int i = 0; i < RAINBOW.length; i++) rect(x + i * sw, y, i == RAINBOW.length - 1 ? x + w : x + (i + 1) * sw, y + 2, RAINBOW[i]);
        if (title != null) {
            text(title, x + 5, y + 5, PINK);
            rect(x + 4, y + HEAD - 1, x + w - 4, y + HEAD, 0x40FFFFFF);
            return y + HEAD + 3;
        }
        return y + 5;
    }

    /** "Label   value" rows; values trimmed to fit (trim cached per row so steady frames don't allocate) */
    static void rows(int x, int y, int w, String[] labels, String[] values, int labelW, int slot0) {
        for (int i = 0; i < labels.length; i++) {
            text(labels[i], x, y + i * LINE, BLUE);
            text(fit(slot0 + i, values[i], w - labelW), x + labelW, y + i * LINE, WHITE);
        }
    }

    // ---- trimmed-string cache: slot -> (source, width) -> trimmed ----
    private static final String[] SRC = new String[512], OUT = new String[512];
    private static final int[] WID = new int[512];

    static String fit(int slot, String s, int width) {
        if (s == null) return "";
        if (SRC[slot] == s && WID[slot] == width) return OUT[slot];
        String o = DPBootFont.width(s) <= width ? s : DPBootFont.trim(s, width - DPBootFont.width("..")) + "..";
        SRC[slot] = s; WID[slot] = width; OUT[slot] = o;
        return o;
    }

    static void text(String s, int x, int y, int color) {
        if (s == null) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        DPBootFont.draw(s, x, y, color);   // own renderer: never Minecraft's FontRenderer / shared Tessellator
    }

    static int width(String s) { return DPBootFont.width(s); }

    /** a meter: track + fill (fraction 0..1) */
    static void bar(int x, int y, int w, int h, float frac, int color) {
        rect(x, y, x + w, y + h, 0x60000000);
        int f = (int) (w * Math.max(0f, Math.min(1f, frac)));
        if (f > 0) rect(x, y, x + f, y + h, color);
    }

    static void rect(int x1, int y1, int x2, int y2, int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >> 24) & 0xFF) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
    }

    static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    // ================================================================== panels

    static int colW(int w) { return Math.max(220, Math.min(320, w / 5)); }

    /** #1 live dashboard, top-left; returns its bottom y */
    static int dashboard(int x, int y, int w) {
        int h = HEAD + 3 + DPBoot.DASH_L.length * LINE + 4;
        int cy = panel(x, y, w, h, "Pride Boot Sequence");
        rows(x + 5, cy, w - 10, DPBoot.DASH_L, DPBoot.DASH_V, 56, 0);
        return y + h;
    }

    /** #2 system meters, right column; returns its bottom y */
    static int meters(int x, int y, int w) {
        DPBoot.meters();
        int n = DPBoot.METER_L.length;
        int h = HEAD + 3 + n * LINE + 3 * LINE + 4;
        int cy = panel(x, y, w, h, "System");
        int barX = x + 40, barW = (w - 50) / 2;
        for (int i = 0; i < n; i++) {
            int ry = cy + i * LINE;
            text(DPBoot.METER_L[i], x + 5, ry, BLUE);
            bar(barX, ry, barW, 8, DPBoot.METER_F[i], DPBoot.METER_C[i]);
            text(fit(20 + i, DPBoot.METER_V[i], w - barW - 50), barX + barW + 5, ry, WHITE);
        }
        int ry = cy + n * LINE;
        text("Disk", x + 5, ry, BLUE);
        text(fit(30, DPBoot.diskLine, w - 45), barX, ry, WHITE);
        text("Net", x + 5, ry + LINE, BLUE);
        text(fit(31, DPBoot.netLine, w - 45), barX, ry + LINE, WHITE);
        text("Memory", x + 5, ry + 2 * LINE, BLUE);
        text(DPBoot.pressure, barX, ry + 2 * LINE, DPBoot.pressureColor);
        text(fit(32, DPBoot.pressureTip, w - 50 - width(DPBoot.pressure) - 8), barX + width(DPBoot.pressure) + 8, ry + 2 * LINE, DIM);
        return y + h;
    }

    /** #3 slowest mods so far, left column; returns its bottom y */
    static int slowest(int x, int y, int w) {
        DPBoot.top();
        int h = HEAD + 3 + DPBoot.TOP_N * LINE + 4;
        int cy = panel(x, y, w, h, DPBoot.topFromZoomies ? "Slowest mods (Zoomies)" : "Slowest mods");
        if (DPBoot.topCount == 0) text("measuring...", x + 5, cy, DIM);
        for (int i = 0; i < DPBoot.topCount; i++) {
            int ry = cy + i * LINE;
            rect(x + 5, ry + 8, x + 5 + (int) ((w - 10) * DPBoot.TOP_F[i]), ry + 9, i == 0 ? PINK : BLUE);
            text(fit(40 + i, DPBoot.TOP_NAME[i], w - 60), x + 5, ry, WHITE);
            text(DPBoot.TOP_V[i], x + w - 5 - width(DPBoot.TOP_V[i]), ry, i == 0 ? PINK : DIM);
        }
        return y + h;
    }

    // ================================================================== input
    // The splash thread owns the window while loading, so we drain LWJGL's event queues here. Draining also stops
    // clicks / keys from leaking into the game once it starts (the old "M key" bug).
    static int mx, my, clickX = -1, clickY = -1, rclickX = -1, rclickY = -1, wheel, nKeys;
    static final int[] KEYS = new int[32];
    static final char[] CHARS = new char[32];

    private static boolean firstFrame = true;

    static void input(int bs, int realH) {
        if (firstFrame) {                                   // Safe Mode's "Diagnostic" choice: open on the Errors tab
            firstFrame = false;
            if (System.getProperty("pride.boot.diag") != null) { filter = 1; DPBootViews.view = 3; DPBootViews.devTab = 2; }   // + dev view on Exceptions
        }
        clickX = clickY = rclickX = rclickY = -1; wheel = 0; nKeys = 0;
        // NEVER read input once loading is over or any Minecraft screen exists: those events belong to the game.
        // (Only DPSplashHook.render calls this, and Forge stops calling that when the splash ends - this is the backstop.)
        if (DPBoot.totalMs >= 0) return;
        try { if (net.minecraft.client.Minecraft.getMinecraft().currentScreen != null) return; } catch (Throwable t) { return; }
        try {
            mx = org.lwjgl.input.Mouse.getX() / bs;
            my = (realH - org.lwjgl.input.Mouse.getY() - 1) / bs;
            while (org.lwjgl.input.Mouse.next()) {
                if (org.lwjgl.input.Mouse.getEventButton() == 0 && org.lwjgl.input.Mouse.getEventButtonState()) {
                    clickX = org.lwjgl.input.Mouse.getEventX() / bs;
                    clickY = (realH - org.lwjgl.input.Mouse.getEventY() - 1) / bs;
                }
                if (org.lwjgl.input.Mouse.getEventButton() == 1 && org.lwjgl.input.Mouse.getEventButtonState()) {
                    rclickX = org.lwjgl.input.Mouse.getEventX() / bs;
                    rclickY = (realH - org.lwjgl.input.Mouse.getEventY() - 1) / bs;
                }
                wheel += org.lwjgl.input.Mouse.getEventDWheel();
            }
        } catch (Throwable ignored) { }
        try {
            while (org.lwjgl.input.Keyboard.next()) {
                if (!org.lwjgl.input.Keyboard.getEventKeyState() || nKeys >= KEYS.length) continue;
                KEYS[nKeys] = org.lwjgl.input.Keyboard.getEventKey();
                CHARS[nKeys++] = org.lwjgl.input.Keyboard.getEventCharacter();
            }
        } catch (Throwable ignored) { }
    }

    /** true once if this frame's click landed in the box (the click is used up) */
    static boolean clicked(int x, int y, int w, int h) {
        if (clickX < 0 || !in(clickX, clickY, x, y, w, h)) return false;
        clickX = clickY = -1;
        return true;
    }

    static boolean hover(int x, int y, int w, int h) { return in(mx, my, x, y, w, h); }

    /** a small flat button; returns true when clicked */
    static boolean button(int x, int y, int w, int h, String label, boolean on) {
        boolean over = hover(x, y, w, h);
        rect(x, y, x + w, y + h, on ? 0xE06A3FA0 : over ? 0xCC3A1F5C : 0x99200F38);
        rect(x, y, x + w, y + 1, on ? PINK : 0x60F5A9B8);
        text(label, x + (w - width(label)) / 2, y + (h - 8) / 2, on ? WHITE : over ? WHITE : DIM);
        return clicked(x, y, w, h);
    }

    // ================================================================== #4 log: filters + search
    static final String[] FILTERS = {"All", "Errors", "Warnings", "Info", "Debug", "Forge", "Status"};
    static int filter;
    static final StringBuilder search = new StringBuilder();
    static boolean searchFocus;
    static int logScroll;                                  // lines scrolled up from the newest
    private static final java.util.ArrayList<String> VIEW = new java.util.ArrayList<String>(400);
    private static final java.util.ArrayList<Integer> VIEW_C = new java.util.ArrayList<Integer>(400);
    private static int viewVersion = -1, viewFilter = -1, viewStatus = -1, errCount, warnCount;
    private static String viewSearch = "";
    private static long viewAt;
    private static final String[] CHIP = new String[FILTERS.length], CHIP_S = new String[FILTERS.length];
    static final String[] FILTERS_S = {"All", "Err", "Warn", "Info", "Dbg", "Forge", "Steps"};
    /** small window (< 1600 px wide): the compact layout, bigger text (GUI scale), short labels */
    static boolean compact;
    static int clipScale = 1, clipRealH;

    private static void rebuildView(java.util.Collection<String> status, int statusVer) {
        String q = search.toString().toLowerCase(java.util.Locale.ROOT);
        boolean changed = viewFilter != filter || !q.equals(viewSearch);
        long now = System.currentTimeMillis();
        int ver = filter == 6 ? statusVer : DPLogBuffer.version;
        if (!changed && (ver == (filter == 6 ? viewStatus : viewVersion) || now - viewAt < 250)) return;
        viewFilter = filter; viewSearch = q; viewAt = now;
        if (filter == 6) viewStatus = ver; else viewVersion = ver;
        VIEW.clear(); VIEW_C.clear();
        if (filter == 6) {
            for (String l : status) if (q.isEmpty() || l.toLowerCase(java.util.Locale.ROOT).contains(q)) { VIEW.add(l); VIEW_C.add(WHITE); }
        } else {
            java.util.List<DPLogBuffer.Line> lines = DPLogBuffer.snapshot(filter == 1 || filter == 2);
            for (DPLogBuffer.Line l : lines) {
                boolean ok = filter == 0 || (filter == 1 && l.lvl == 3) || (filter == 2 && l.lvl == 2) || (filter == 3 && l.lvl == 1)
                        || (filter == 4 && l.lvl == 0) || (filter == 5 && l.forge);
                if (!ok) continue;
                if (!q.isEmpty() && !l.msg.toLowerCase(java.util.Locale.ROOT).contains(q) && !(l.logger != null && l.logger.toLowerCase(java.util.Locale.ROOT).contains(q))) continue;
                VIEW.add(l.msg);
                VIEW_C.add(l.lvl == 3 ? BAD : l.lvl == 2 ? WARN : l.lvl == 1 ? WHITE : DIM);
            }
        }
        errCount = 0; warnCount = 0;
        for (DPLogBuffer.Line l : DPLogBuffer.snapshot(true)) if (l.lvl == 3) errCount++; else warnCount++;
        for (int i = 0; i < FILTERS.length; i++) {
            CHIP[i] = i == 1 && errCount > 0 ? FILTERS[i] + " " + errCount : i == 2 && warnCount > 0 ? FILTERS[i] + " " + warnCount : FILTERS[i];
            CHIP_S[i] = i == 1 && errCount > 0 ? FILTERS_S[i] + " " + errCount : i == 2 && warnCount > 0 ? FILTERS_S[i] + " " + warnCount : FILTERS_S[i];
        }
    }

    /** the log area of the big box: filter chips + search on top, lines below */
    static void logPanel(int x, int y, int w, int bottom, java.util.Collection<String> status, int statusVer) {
        rebuildView(status, statusVer);
        // keys for the search box
        for (int i = 0; i < nKeys; i++) {
            int k = KEYS[i]; char c = CHARS[i];
            if (!searchFocus) { if (c == '/') searchFocus = true; continue; }
            if (k == org.lwjgl.input.Keyboard.KEY_ESCAPE) { search.setLength(0); searchFocus = false; }
            else if (k == org.lwjgl.input.Keyboard.KEY_RETURN) searchFocus = false;
            else if (k == org.lwjgl.input.Keyboard.KEY_BACK) { if (search.length() > 0) search.setLength(search.length() - 1); }
            else if (c >= 32 && c < 127 && search.length() < 40) search.append(c);
        }
        // chips
        int cx = x;
        for (int i = 0; i < FILTERS.length; i++) {
            String lab = compact ? (CHIP_S[i] == null ? FILTERS_S[i] : CHIP_S[i]) : (CHIP[i] == null ? FILTERS[i] : CHIP[i]);
            int cw = width(lab) + 8;
            if (button(cx, y, cw, 11, lab, filter == i)) { filter = i; logScroll = 0; }
            if (i == 1 && errCount > 0 && filter != 1) rect(cx, y + 10, cx + cw, y + 11, BAD);
            if (i == 2 && warnCount > 0 && filter != 2) rect(cx, y + 10, cx + cw, y + 11, WARN);
            cx += cw + 2;
        }
        int sw = Math.max(compact ? 50 : 80, Math.min(160, x + w - cx - 4)), sx = x + w - sw;
        boolean over = hover(sx, y, sw, 11);
        rect(sx, y, sx + sw, y + 11, searchFocus ? 0xE0301C4C : 0x99200F38);
        rect(sx, y + 10, sx + sw, y + 11, searchFocus ? BLUE : 0x605BCEFA);
        String sl = search.length() == 0 && !searchFocus ? (compact ? "Search /" : "Search (press /)") : search + (searchFocus && (System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        text(fit(60, sl, sw - 6), sx + 3, y + 2, search.length() == 0 && !searchFocus ? DIM : WHITE);
        if (clicked(sx, y, sw, 11)) searchFocus = true;
        else if (clickX >= 0 && !over) searchFocus = false;

        // lines (newest at the bottom; wheel scrolls back)
        int top = y + 14, rows = Math.max(1, (bottom - top) / LINE);
        if (wheel != 0 && hover(x, top, w, bottom - top)) logScroll += wheel > 0 ? 3 : -3;
        logScroll = Math.max(0, Math.min(logScroll, Math.max(0, VIEW.size() - rows)));
        int end = VIEW.size() - logScroll, start = Math.max(0, end - rows);
        int ly = top;
        if (VIEW.isEmpty()) text(search.length() > 0 ? "nothing matches \"" + search + "\"" : filter == 1 ? "no errors - nice!" : "Loading...", x, ly, DIM);
        for (int i = start; i < end; i++) {
            text(fit(300 + Math.min(200, i - start), VIEW.get(i), w - 6), x, ly, VIEW_C.get(i));
            ly += LINE;
        }
        if (logScroll > 0) {
            String m = "scrolled back " + logScroll + " lines - wheel down to follow";
            rect(x + w - width(m) - 8, top, x + w, top + 10, 0xCC200F38);
            text(m, x + w - width(m) - 4, top + 1, BLUE);
        }
    }

    // ================================================================== #5 problem cards
    static int expanded = -1;
    static final java.util.Set<String> dismissed = new java.util.HashSet<String>();
    private static java.util.List<String> wrapped;
    private static int wrappedFor = -1;

    /** up to 3 "! Potential problem" cards stacked upward from `bottom` in the middle column; returns the top y used */
    static int problems(int x, int w, int bottom) {
        DPBoot.scanProblems();
        java.util.List<DPBoot.Problem> ps = DPBoot.PROBLEMS;
        int shown = 0, y = bottom, hidden = 0;
        for (int i = ps.size() - 1; i >= 0; i--) {
            DPBoot.Problem p = ps.get(i);
            if (dismissed.contains(p.key)) continue;
            if (shown == 3) { hidden++; continue; }
            boolean open = expanded == i;
            if (open && wrappedFor != i) {
                wrappedFor = i;
                wrapped = new java.util.ArrayList<String>();
                String full = p.msg + (p.thrown != null ? "  |  " + p.thrown : "");
                wrapped.addAll(DPBootFont.wrap(full, w - 12));
                if (wrapped.size() > 6) wrapped = wrapped.subList(0, 6);
            }
            int h = open ? 40 + wrapped.size() * LINE : 30;
            y -= h + 4;
            int col = DPBoot.KIND_C[p.kind];
            rect(x, y, x + w, y + h, 0xE0140E22);
            rect(x, y, x + 2, y + h, col);
            text("! Potential problem: " + DPBoot.KIND[p.kind] + (p.count > 1 ? "  (x" + p.count + ")" : ""), x + 6, y + 4, col);
            if (button(x + w - 14, y + 2, 12, 11, "x", false)) { dismissed.add(p.key); if (open) expanded = -1; }
            if (open) {
                for (int k = 0; k < wrapped.size(); k++) text(wrapped.get(k), x + 6, y + 15 + k * LINE, WHITE);
                text(fit(90 + shown, "from " + (p.logger == null ? "?" : p.logger) + "  -  click to close, or see it in the log", w - 12), x + 6, y + h - 24, DIM);
                // #17 startup repair
                int by = y + h - 13, bx = x + 6;
                if (button(bx, by, 96, 11, "Open mods folder", false)) DPSafeMode.openFolder(new java.io.File("mods"));
                bx += 100;
                if (button(bx, by, 76, 11, "Open logs", false)) DPSafeMode.openFolder(new java.io.File("logs"));
                bx += 80;
                String jar = p.kind >= 1 && p.kind <= 4 ? DPBoot.jarOf(p) : null;
                if (jar != null) {
                    String lab = p.repaired ? "turned off - restart to apply" : "Turn off " + jar.replace(".jar", "") + " (next start)";
                    lab = fit(95 + shown, lab, x + w - bx - 14);
                    if (button(bx, by, width(lab) + 10, 11, lab, p.repaired) && !p.repaired) p.repaired = DPSafeMode.turnOffJar(jar);
                }
            } else {
                text(fit(80 + shown, p.msg, w - 12), x + 6, y + 14, WHITE);
                text(fit(85 + shown, "from " + (p.logger == null ? "?" : p.logger) + "  -  click for details", w - 12), x + 6, y + 22, DIM);
            }
            if (clicked(x, y, w, h)) {
                expanded = open ? -1 : i;
                if (!open) { filter = p.kind == 0 ? 0 : 1; search.setLength(0); logScroll = 0; }
            }
            shown++;
        }
        if (hidden > 0) {
            String m = "+" + hidden + " more - see the Errors / Warnings tabs";
            y -= 14;
            if (button(x, y, width(m) + 10, 11, m, false)) { filter = 1; logScroll = 0; }
        }
        return y;
    }

    // ================================================================== #6 stall card
    /** "What's taking so long?" card, bottom edge at `bottom`; only while nothing moved for 10 s */
    static int stall(int x, int w, int bottom) {
        if (!DPBoot.stalled) return bottom;
        boolean peek = !DPBoot.stallWhere.isEmpty();
        int h = peek ? 52 : 32, y = bottom - h - 4;
        rect(x, y, x + w, y + h, 0xE0140E22);
        int pulse = (System.currentTimeMillis() / 500) % 2 == 0 ? PINK : BLUE;
        rect(x, y, x + w, y + 2, pulse);
        text("What's taking so long?", x + 6, y + 5, PINK);
        text(fit(100, DPBoot.stallFor, w - 12 - width("What's taking so long?") - 10), x + 6 + width("What's taking so long?") + 10, y + 5, WARN);
        text(fit(101, "Working on: " + DPBoot.stallWhat, w - 12), x + 6, y + 17, WHITE);
        if (peek) {
            text(fit(102, "Loading thread is " + DPBoot.stallWhere, w - 12), x + 6, y + 29, WHITE);
            text(fit(103, "Looks like: " + DPBoot.stallHint + "  -  big modpacks have a few slow steps, it is not frozen", w - 12), x + 6, y + 41, DIM);
        }
        return y;
    }

    // ================================================================== #8 disk panel
    static int disk(int x, int y, int w) {
        DPBoot.disk();
        int h = HEAD + 3 + DPBoot.DISK_L.length * LINE + 4;
        int cy = panel(x, y, w, h, "Disk activity");
        rows(x + 5, cy, w - 10, DPBoot.DISK_L, DPBoot.DISK_V, 62, 10);
        return y + h;
    }

    // ================================================================== #9 asset preview
    static int assets(int x, int y, int w) {
        DPBootAssets.frame();
        if (DPBootAssets.count == 0) return y;
        int sz = 20, h = HEAD + 3 + sz + 4 + LINE + 2;
        int cy = panel(x, y, w, h, "Now loading");
        DPBootAssets.draw(x + 5, cy, sz, (w - 10) / (sz + 3));
        String n = DPBootAssets.newest();
        text(fit(14, n, w - 10), x + 5, cy + sz + 4, DIM);
        return y + h;
    }

    // ================================================================== #10 music player (top-right)
    private static java.util.Map<String, Object> ctl;
    static boolean showSongs;
    private static String trackSrc, trackTitle = "", timeStr = "";
    private static int timeKey = -1;

    private static float num(Object o, float d) { return o instanceof Number ? ((Number) o).floatValue() : d; }

    static int music(int x, int y, int w) {
        if (ctl == null) ctl = DPMusic.ctl();
        String[] pl = ctl.get("playlist") instanceof String[] ? (String[]) ctl.get("playlist") : DPMusic.LOADING_PLAYLIST;
        boolean off = DPMusicPause.paused(), playing = ctl.get("thread") != null;
        int list = showSongs ? pl.length : 0;
        int h = HEAD + 3 + LINE + 6 + 2 * 14 + list * LINE + 2 + (DPBootSettings.on("visualizer", true) ? 15 : 0);
        int cy = panel(x, y, w, h, "Music");
        if (off || !playing) {
            text(off ? "Music is off" : "Not playing", x + 5, cy, DIM);
            if (button(x + w - 70, cy - 2, 65, 12, "Turn on", false)) {
                if (off) DPMusicPause.toggle(); else DPMusic.start(DPMusic.LOADING_PLAYLIST);
            }
            return y + HEAD + 3 + LINE + 4;
        }
        String tr = ctl.get("track") instanceof String ? (String) ctl.get("track") : null;
        if (tr != trackSrc) { trackSrc = tr; trackTitle = tr == null ? "starting..." : DPMusic.title(tr); }
        float pos = num(ctl.get("pos"), 0), len = num(ctl.get("len"), -1);
        int key = (int) pos * 10000 + (int) len;
        if (key != timeKey) {
            timeKey = key;
            timeStr = (int) pos / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", (int) pos % 60)
                    + (len > 0 ? " / " + (int) len / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", (int) len % 60) : "");
        }
        text(fit(15, trackTitle, w - 14 - width(timeStr)), x + 5, cy, WHITE);
        text(timeStr, x + w - 5 - width(timeStr), cy, DIM);
        bar(x + 5, cy + LINE + 1, w - 10, 3, len > 0 ? pos / len : 0, PINK);
        if (DPBootSettings.on("visualizer", true)) vizBars(x + 5, cy + LINE + 6, w - 10, 12);   // #27

        // row 1: previous / next / shuffle / repeat
        int by = cy + LINE + 7 + (DPBootSettings.on("visualizer", true) ? 15 : 0), gap = 3, bw = (w - 10 - 3 * gap) / 4;
        boolean shuffle = Boolean.TRUE.equals(ctl.get("shuffle")), rep1 = Boolean.TRUE.equals(ctl.get("repeat1"));
        if (button(x + 5, by, bw, 12, "<< Prev", false)) {
            int cur = (int) num(ctl.get("index"), 0);
            DPMusic.command(pos > 3 ? "jump:" + cur : "prev");             // like every player: first press restarts the song
        }
        if (button(x + 5 + (bw + gap), by, bw, 12, "Next >>", false)) DPMusic.command("next");
        if (button(x + 5 + 2 * (bw + gap), by, bw, 12, "Shuffle", shuffle)) ctl.put("shuffle", !shuffle);
        if (button(x + 5 + 3 * (bw + gap), by, bw, 12, rep1 ? "Repeat 1" : "Repeat all", rep1)) ctl.put("repeat1", !rep1);

        // row 2: volume - / + , off, song list
        by += 14;
        float vol = num(ctl.get("volume"), 1f);
        String vs = "Vol " + Math.round(vol * 100) + "%";
        int sb = 14;
        if (button(x + 5, by, sb, 12, "-", false)) DPMusic.setVolume(Math.round((vol - 0.1f) * 10) / 10f);
        text(vs, x + 5 + sb + 4, by + 2, WHITE);
        int px = x + 5 + sb + 8 + width("Vol 100%");
        if (button(px, by, sb, 12, "+", false)) DPMusic.setVolume(Math.round((vol + 0.1f) * 10) / 10f);
        int rest = x + w - 5 - (px + sb + gap), hb = (rest - gap) / 2;
        if (button(px + sb + gap, by, hb, 12, "Turn off", false)) DPMusicPause.toggle();
        if (button(px + sb + gap + hb + gap, by, hb, 12, "Songs", showSongs)) showSongs = !showSongs;

        // song list: click one to jump to it
        if (showSongs) {
            int cur = (int) num(ctl.get("index"), -1);
            for (int i = 0; i < pl.length; i++) {
                int ly = by + 14 + i * LINE;
                boolean over = hover(x + 5, ly, w - 10, LINE);
                if (over) rect(x + 4, ly - 1, x + w - 4, ly + LINE - 1, 0x40F5A9B8);
                text((i == cur ? "> " : "  ") + DPMusic.title(pl[i]), x + 5, ly, i == cur ? PINK : over ? WHITE : DIM);
                if (clicked(x + 5, ly, w - 10, LINE)) DPMusic.command("jump:" + i);
            }
        }
        return y + h;
    }

    // ================================================================== #11 games launcher (left column)
    static int gamesPanel(int x, int y, int w) {
        int[] gs = visible("g.", DPBootGames.NAMES), vs = visible("v.", DPBootViews.NAMES);   // Options > Loading Screen hides single games
        if (gs.length + vs.length == 0) return y;
        int bw = (w - 10 - 2 * 3) / 3, rowsN = (gs.length + 2) / 3, vRows = (vs.length + 2) / 3;
        int h = HEAD + 3 + rowsN * 14 + 4 + (vs.length > 0 ? LINE + vRows * 14 : 0) + 2;
        int cy = panel(x, y, w, h, "Play while you wait");
        int vy = cy + rowsN * 14 + 4;
        if (vs.length > 0) text("Explore", x + 5, vy, PINK);
        for (int k = 0; k < vs.length; k++) {
            int i = vs[k], bx = x + 5 + (k % 3) * (bw + 3), by = vy + LINE + (k / 3) * 14;
            if (button(bx, by, bw, 12, DPBootViews.NAMES[i], DPBootViews.view == i)) DPBootViews.open(i);
        }
        for (int k = 0; k < gs.length; k++) {
            int i = gs[k], bx = x + 5 + (k % 3) * (bw + 3), by = cy + (k / 3) * 14;
            if (button(bx, by, bw, 12, DPBootGames.NAMES[i], DPBootGames.game == i)) {
                if (DPBootGames.game == i) { DPBootGames.game = -1; DPBootGames.save(); } else { DPBootGames.open(i); DPBootViews.view = -1; }
            }
        }
        return y + h;
    }

    private static final java.util.Map<String, int[]> VIS = new java.util.HashMap<String, int[]>();
    private static long visAt;

    /** indexes of the names not switched off ("g.Snake=false"); re-checked once a second */
    static int[] visible(String prefix, String[] names) {
        long now = System.currentTimeMillis();
        if (now - visAt > 1000) { visAt = now; VIS.clear(); }
        int[] r = VIS.get(prefix);
        if (r != null) return r;
        int n = 0;
        int[] all = new int[names.length];
        for (int i = 0; i < names.length; i++) if (DPBootSettings.show(prefix + names[i])) all[n++] = i;
        VIS.put(prefix, r = java.util.Arrays.copyOf(all, n));
        return r;
    }

    // ================================================================== #13 What's New (left column, fills down to `bottom`)
    static int newsScroll;

    static int whatsNew(int x, int y, int w, int bottom) {
        java.util.List<String> l = DPBoot.whatsNew();
        int rowsFit = (bottom - y - HEAD - 7) / LINE;
        if (rowsFit < 2 || l.isEmpty()) return y;
        int rowsN = Math.min(rowsFit, l.size()), h = HEAD + 3 + rowsN * LINE + 4;
        int cy = panel(x, y, w, h, "What's New");
        if (wheel != 0 && hover(x, y, w, h)) newsScroll += wheel > 0 ? -2 : 2;
        newsScroll = Math.max(0, Math.min(newsScroll, l.size() - rowsN));
        for (int i = 0; i < rowsN; i++) {
            String s = l.get(newsScroll + i);
            char c = s.isEmpty() ? ' ' : s.charAt(0);
            int col = c == '+' ? GOOD : c == '-' ? BAD : c == '~' ? BLUE : c == '#' ? PINK : WHITE;
            String shown = c == '#' ? s.substring(1) : c == '+' ? "+ " + s.substring(1) : c == '-' ? "- " + s.substring(1) : c == '~' ? "updated " + s.substring(1) : s;
            text(fit(200 + Math.min(40, i), shown, w - 10), x + 5, cy + i * LINE, col);
        }
        if (l.size() > rowsN) {
            String m = (newsScroll + rowsN) + "/" + l.size();
            text(m, x + w - 5 - width(m), y + 5, DIM);
        }
        return y + h;
    }

    // ================================================================== #14 tip line (under the big box)
    static void tip(int w, int y) {
        boolean clk = DPBootSettings.show("clock"), play = DPBootSettings.show("playtime"), tips = DPBootSettings.show("tips");
        DPBoot.clock();                                     // #26 clock bottom-left, playtime bottom-right
        if (clk) text(DPBoot.clock, 12, y, PINK);
        if (play) text(DPBoot.playtime, w - 12 - width(DPBoot.playtime), y, DIM);
        if (!tips) return;
        DPBoot.tips();
        int side = Math.max(clk ? width(DPBoot.clock) : 0, play ? width(DPBoot.playtime) : 0) + 20;
        String t = fit(250, DPBoot.tip, w - 2 * side);
        text(t, (w - width(t)) / 2, y, 0xFFE8D8F0);
    }

    // ================================================================== right column tabs: System / JVM / Threads (#18 #19 #20)
    static final String[] RTABS = {"System", "JVM", "Threads"};
    static int rtab;

    static int rightColumn(int x, int y, int w) {
        int tx = x, tw = (w - 4) / RTABS.length;
        for (int i = 0; i < RTABS.length; i++, tx += tw + 2) if (button(tx, y, tw, 11, RTABS[i], rtab == i)) rtab = i;
        y += 15;
        DPBootJvm.wantThreads = rtab == 2;
        DPBootJvm.sample();                                 // 1x a second always, so the heap graph has history
        if (rtab == 0) {
            y = meters(x, y, w);
            y = disk(x, y + 6, w);
            return assets(x, y + 6, w);
        }
        if (rtab == 1) return heapGraph(x, jvm(x, y, w) + 6, w);
        return threads(x, y, w);
    }

    /** #18 JVM diagnostics */
    static int jvm(int x, int y, int w) {
        int n = DPBootJvm.L.length, h = HEAD + 3 + n * LINE + 4;
        int cy = panel(x, y, w, h, "Java");
        rows(x + 5, cy, w - 10, DPBootJvm.L, DPBootJvm.V, 50, 160);
        return y + h;
    }

    /** #19 busiest threads over the last second */
    private static final String[] T_PCT_S = new String[DPBootJvm.TN];
    private static final float[] T_PCT_SRC = new float[DPBootJvm.TN];

    static int threads(int x, int y, int w) {
        int h = HEAD + 3 + DPBootJvm.TN * LINE + 4;
        int cy = panel(x, y, w, h, "Busiest threads (CPU)");
        if (DPBootJvm.tCount == 0) text("measuring...", x + 5, cy, DIM);
        for (int i = 0; i < DPBootJvm.tCount; i++) {
            int ry = cy + i * LINE;
            float p = DPBootJvm.T_PCT[i];
            if (T_PCT_S[i] == null || T_PCT_SRC[i] != p) { T_PCT_SRC[i] = p; T_PCT_S[i] = Math.round(p) + "%"; }
            int col = p < 50 ? GOOD : p < 90 ? WARN : BAD;
            rect(x + 5, ry + 8, x + 5 + (int) ((w - 10) * Math.min(1f, p / 100f)), ry + 9, col);
            text(fit(170 + i, DPBootJvm.T_NAME[i], w - 44), x + 5, ry, WHITE);
            text(T_PCT_S[i], x + w - 5 - width(T_PCT_S[i]), ry, col);
        }
        return y + h;
    }

    /** #20 heap used over the last 2 minutes, pink ticks where a garbage collection ran */
    static int heapGraph(int x, int y, int w) {
        int gh = 40, h = HEAD + 3 + gh + 4;
        int cy = panel(x, y, w, h, "Heap + GC (2 min)");
        int gx = x + 5, gw = w - 10;
        rect(gx, cy, gx + gw, cy + gh, 0x40000000);
        rect(gx, cy + gh - (int) (gh * 0.85f), gx + gw, cy + gh - (int) (gh * 0.85f) + 1, 0x40FFC040);   // 85% line
        int n = DPBootJvm.HIST;
        float bw = gw / (float) n;
        for (int i = 0; i < n; i++) {
            int k = (DPBootJvm.head + 1 + i) % n;             // oldest -> newest, left -> right
            float v = DPBootJvm.HEAP[k];
            if (v <= 0) continue;
            int x1 = gx + (int) (i * bw), x2 = Math.max(x1 + 1, gx + (int) ((i + 1) * bw));
            rect(x1, cy + gh - (int) (gh * Math.min(1f, v)), x2, cy + gh, v < 0.8f ? 0xC05BCEFA : v < 0.92f ? WARN : BAD);
            if (DPBootJvm.GC[k]) rect(x1, cy, x2, cy + 4, PINK);
        }
        return y + h;
    }

    // ================================================================== #16 Safe Mode question on the splash (no dialog possible)
    private static java.util.List<String> askChanged;
    static String askDone;

    /** returns true while the question covers the middle */
    static boolean safeAsk(int x, int y, int w) {
        String fails = System.getProperty("pride.boot.ask");
        if (fails == null) return false;
        if (askChanged == null) askChanged = DPSafeMode.changedSinceGoodStart();
        int h = 92;
        int cy = panel(x, y, w, h, "Startup trouble");
        if (askDone != null) {
            text(fit(110, askDone, w - 10), x + 5, cy, GOOD);
            if (button(x + 5, cy + 16, 80, 12, "OK", false)) System.clearProperty("pride.boot.ask");
            return true;
        }
        text(fit(111, "Minecraft didn't reach the main menu the last " + fails + " times it started.", w - 10), x + 5, cy, WHITE);
        text(fit(112, askChanged.isEmpty() ? "No mods were added or updated since the last good start."
                : askChanged.size() + " mod(s) changed since the last good start, e.g. " + askChanged.get(0).replace(".jar", ""), w - 10), x + 5, cy + LINE, DIM);
        int by = cy + 2 * LINE + 6, bx = x + 5;
        if (button(bx, by, 90, 12, "Keep loading", false)) System.clearProperty("pride.boot.ask");
        bx += 94;
        if (!askChanged.isEmpty()) {
            String l = "Safe mode: turn those " + askChanged.size() + " off (next start)";
            if (button(bx, by, width(l) + 10, 12, l, false)) { DPSafeMode.turnOff(askChanged); askDone = "Done - restart Minecraft to start without them. Turn them back on from the 'Loaded in' report."; }
            bx += width(l) + 14;
        }
        if (button(bx, by, 70, 12, "Diagnostic", false)) { filter = 1; DPBootViews.view = 3; DPBootViews.devTab = 2; System.clearProperty("pride.boot.ask"); }
        return true;
    }

    // ================================================================== #27 music visualizer
    static final float[] VIZ = new float[16];
    static float bass;

    /** pick the analysed block matching what's audible now (the audio line plays ~3 s behind the decoder) */
    static void vizUpdate() {
        try {
            if (ctl == null) ctl = DPMusic.ctl();
            Object pf = ctl.get("posFrames"), pa = ctl.get("posAt"), rt = ctl.get("rate");
            long[] pos = ctl.get("vizPos") instanceof long[] ? (long[]) ctl.get("vizPos") : null;
            float[][] bands = ctl.get("vizBands") instanceof float[][] ? (float[][]) ctl.get("vizBands") : null;
            boolean on = ctl.get("thread") != null && !DPMusicPause.paused() && pf instanceof Long && pos != null && bands != null;
            int best = -1;
            if (on) {
                long cur = (Long) pf + Math.min(500, System.currentTimeMillis() - ((Long) pa)) * ((Integer) rt) / 1000;
                long bp = -1;
                for (int i = 0; i < pos.length; i++) if (pos[i] <= cur && pos[i] > bp) { bp = pos[i]; best = i; }
            }
            for (int b = 0; b < VIZ.length; b++) {
                float v = best >= 0 ? (float) Math.sqrt(bands[best][b]) : 0;
                VIZ[b] = Math.max(v, VIZ[b] * 0.85f);              // fast up, slow fall
            }
            bass = (VIZ[0] + VIZ[1] + VIZ[2]) / 3f;
        } catch (Throwable ignored) { }
    }

    static void vizBars(int x, int y, int w, int h) {
        int n = VIZ.length, bw = Math.max(1, (w - (n - 1)) / n);
        for (int b = 0; b < n; b++) {
            int bh = Math.max(1, (int) (h * Math.min(1f, VIZ[b])));
            int bx = x + b * (bw + 1);
            rect(bx, y + h - bh, bx + bw, y + h, b < 5 ? PINK : b < 11 ? 0xFFE8D8F0 : BLUE);
        }
    }

    // ================================================================== the "Music: ON/OFF" switch (requested feature)
    // Its own reserved top-right corner: nothing else is drawn there, its click is taken FIRST each frame and it is
    // drawn LAST, so no panel can ever cover it.
    private static String musicLabel(boolean paused) {
        if (compact) return paused ? "Music: OFF" : "Music: ON";
        return paused ? "Music: OFF - click to turn on" : "Music: ON - click to turn off";
    }
    static final int MUSIC_BTN_H = 16;

    static void musicButtonClick(int w) {
        String label = musicLabel(DPMusicPause.paused());
        int bw = width(label) + 14;
        if (clicked(w - bw - 10, 10, bw, MUSIC_BTN_H)) DPMusicPause.toggle();
    }

    static void musicButtonDraw(int w) {
        String label = musicLabel(DPMusicPause.paused());
        int bw = width(label) + 14, x1 = w - bw - 10, y1 = 10, x2 = x1 + bw, y2 = y1 + MUSIC_BTN_H;
        boolean over = hover(x1, y1, bw, MUSIC_BTN_H);
        rect(x1, y1, x2, y2, over ? 0xEE3A1F5C : 0xDD200F38);
        rect(x1, y1, x2, y1 + 1, PINK); rect(x1, y2 - 1, x2, y2, PINK);
        rect(x1, y1, x1 + 1, y2, PINK); rect(x2 - 1, y1, x2, y2, PINK);
        text(label, x1 + 7, y1 + 4, WHITE);
    }

    // ================================================================== clipping: a panel can never draw outside its rectangle
    static void clip(int x, int y, int w, int h) {
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glScissor(x * clipScale, clipRealH - (y + h) * clipScale, Math.max(0, w * clipScale), Math.max(0, h * clipScale));
    }

    static void unclip() { org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST); }
}
