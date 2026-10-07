package com.dogpound.canvas;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading screen "Explore" views (#28 mod web, #29 mod carousel, #30 facts): opened from the left column like the
 * games, drawn in the middle. Built once from Forge's mod list; drawing is plain lines and squares.
 */
final class DPBootViews {
    private DPBootViews() {}

    static final String[] NAMES = {"Mod web", "Mods", "Facts", "Dev"};
    static int view = -1;

    static void open(int v) { view = view == v ? -1 : v; if (view >= 0) { DPBootGames.game = -1; DPBootSettings.open = false; } }

    static boolean draw(int x, int y, int w, int h) {
        if (view < 0) return false;
        int cy = DPBootUI.panel(x, y, w, h, NAMES[view]);
        if (DPBootUI.button(x + w - 16, y + 3, 13, 11, "x", false)) { view = -1; return false; }
        if (!DPBootUI.searchFocus) for (int i = 0; i < DPBootUI.nKeys; i++) if (DPBootUI.KEYS[i] == org.lwjgl.input.Keyboard.KEY_ESCAPE) { view = -1; return false; }
        if (view == 0) web(x + 6, cy, w - 12, y + h - cy - 6);
        if (view == 1) carousel(x + 6, cy, w - 12, y + h - cy - 6);
        if (view == 2) facts(x + 6, cy, w - 12, y + h - cy - 6);
        if (view == 3) dev(x + 6, cy, w - 12, y + h - cy - 6);
        return true;
    }

    // ================================================================== #28 mod dependency web
    // the mod list, copied on the MAIN thread at pre-init by DPBootMain (the splash never reads Forge objects)
    private static volatile String[] ids, names, vers, auths, descs, logos, urls;
    private static int[] eA, eB, deps, users;

    static void setMods(String[] i, String[] n, String[] v, String[] a, String[] d, String[] l, String[] u, int[] ea, int[] eb, int[] dp, int[] us) {
        names = n; vers = v; auths = a; descs = d; logos = l; urls = u; eA = ea; eB = eb; deps = dp; users = us;
        ids = i;                                              // last: the splash checks ids != null
    }

    /** a mod is lit once its turn in the current loading phase has passed (all of them after the mod phases) */
    private static boolean lit(int i) {
        if (DPBoot.modsTotal <= 0) return false;
        if (!DPBoot.inModPhase) return DPBoot.modPhasesSeen > 0;
        return i < DPBoot.modsDone;
    }

    private static void web(int x, int y, int w, int h) {
        if (ids == null) { DPBootUI.text("the mod list shows up at pre-init...", x, y, DPBootUI.DIM); return; }
        int n = ids.length, cx = x + w / 2, cy = y + h / 2, r = Math.min(w, h) / 2 - 8;
        float[] px = PX.length == n ? PX : (PX = new float[n]), py = PY.length == n ? PY : (PY = new float[n]);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n - Math.PI / 2;
            float rr = r * (users[i] > 4 ? 0.55f : users[i] > 0 ? 0.8f : 1f);   // libraries everyone needs sit nearer the middle
            px[i] = cx + (float) Math.cos(a) * rr; py[i] = cy + (float) Math.sin(a) * rr;
        }
        int hover = -1;
        float best = 36;
        for (int i = 0; i < n; i++) {
            float dx = px[i] - DPBootUI.mx, dy = py[i] - DPBootUI.my, d = dx * dx + dy * dy;
            if (d < best) { best = d; hover = i; }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_LINES);
        for (int k = 0; k < eA.length; k++) {
            int a = eA[k], b = eB[k];
            boolean on = lit(a) && lit(b), hl = hover == a || hover == b;
            if (hl) GL11.glColor4f(1f, 1f, 1f, 0.8f);
            else if (on) GL11.glColor4f(0.96f, 0.66f, 0.72f, 0.30f);
            else GL11.glColor4f(0.36f, 0.81f, 0.98f, 0.10f);
            GL11.glVertex2f(px[a], py[a]);
            GL11.glVertex2f(px[b], py[b]);
        }
        GL11.glEnd();
        String cur = DPBoot.curMod;
        for (int i = 0; i < n; i++) {
            boolean now = DPBoot.inModPhase && i == DPBoot.modsDone;
            int s = now ? 3 : users[i] > 4 ? 2 : 1;
            DPBootUI.rect((int) px[i] - s, (int) py[i] - s, (int) px[i] + s + 1, (int) py[i] + s + 1,
                    now ? DPBootUI.WHITE : lit(i) ? DPBootUI.PINK : 0xFF4A4060);
        }
        String head = n + " mods, " + eA.length + " dependency links" + (cur.isEmpty() ? "" : "  -  now: " + cur);
        DPBootUI.text(DPBootUI.fit(120, head, w), x, y, DPBootUI.DIM);
        if (hover >= 0) {
            String t = names[hover] + " (" + ids[hover] + ")  needs " + deps[hover] + ", needed by " + users[hover];
            int tw = DPBootUI.width(t) + 8, tx = Math.min(x + w - tw, (int) px[hover] + 6), ty = (int) py[hover] - 14;
            DPBootUI.rect(tx, ty, tx + tw, ty + 11, 0xE0140E22);
            DPBootUI.text(t, tx + 4, ty + 2, DPBootUI.WHITE);
        }
    }

    private static float[] PX = new float[0], PY = new float[0];

    // ================================================================== #29 mod carousel
    private static int carIdx = -1, carShown = -2, logoTex, logoW, logoH;
    private static long carAt;
    private static boolean follow = true;
    private static volatile java.nio.ByteBuffer logoReady;
    private static volatile int logoRW, logoRH, logoFor = -1;
    private static String[] carLines;

    private static void carousel(int x, int y, int w, int h) {
        String[] mods = ids;
        if (mods == null || mods.length == 0) { DPBootUI.text("the mod list shows up at pre-init...", x, y, DPBootUI.DIM); return; }
        int n = mods.length;
        long now = System.currentTimeMillis();
        // follows the mod that's loading right now; otherwise turns a page every 5 s; arrows take over
        if (follow && DPBoot.inModPhase) { if (now - carAt > 1500) { carIdx = Math.min(n - 1, DPBoot.modsDone); carAt = now; } }   // max one page (and logo decode) per 1.5 s
        else if (follow && now - carAt > 5000) { carIdx = (carIdx + 1) % n; carAt = now; }
        if (carIdx < 0) carIdx = 0;
        int by = y + h - 14;
        if (DPBootUI.button(x, by, 40, 12, "<", false)) { follow = false; carIdx = (carIdx - 1 + n) % n; }
        if (DPBootUI.button(x + 44, by, 40, 12, ">", false)) { follow = false; carIdx = (carIdx + 1) % n; }
        if (DPBootUI.button(x + 88, by, 90, 12, follow ? "following" : "follow loading", follow)) { follow = !follow; carAt = now; }
        String pos = (carIdx + 1) + " / " + n;
        DPBootUI.text(pos, x + w - DPBootUI.width(pos), by + 2, DPBootUI.DIM);

        if (carShown != carIdx) {                          // new page: text lines + ask for the logo
            carShown = carIdx;
            List<String> l = new ArrayList<String>();
            l.add(names[carIdx]);
            l.add("version " + vers[carIdx] + "   (" + ids[carIdx] + ")");
            if (!auths[carIdx].isEmpty()) l.add("by " + auths[carIdx]);
            if (!descs[carIdx].isEmpty())
                for (String d : DPBootFont.wrap(descs[carIdx], w - 150)) { if (l.size() > 12) break; l.add(d); }
            if (!urls[carIdx].isEmpty()) l.add(urls[carIdx]);
            carLines = l.toArray(new String[0]);
            final String logo = logos[carIdx];
            final int want = carIdx;
            logoW = 0;
            if (logo != null && !logo.trim().isEmpty()) {
                Thread t = new Thread(() -> decodeLogo(logo.trim(), want), "Pride-BootLogo");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        }
        java.nio.ByteBuffer b = logoReady;
        if (b != null && logoFor == carIdx) {
            logoReady = null;
            if (logoTex <= 0) logoTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, logoTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, logoRW, logoRH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
            logoW = logoRW; logoH = logoRH;
        }
        int tx = x;
        if (logoW > 0 && logoTex > 0) {                     // logo on the left, max 128 x 128
            float k = Math.min(128f / logoW, 128f / logoH);
            int lw = (int) (logoW * k), lh = (int) (logoH * k);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, logoTex);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y + 4);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + 4 + lh);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + lw, y + 4 + lh);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + lw, y + 4);
            GL11.glEnd();
            tx = x + 140;
        } else tx = x + 4;
        for (int i = 0; i < carLines.length && y + 4 + i * 11 < by - 10; i++)
            DPBootUI.text(DPBootUI.fit(130 + Math.min(i, 15), carLines[i], x + w - tx), tx, y + 4 + i * 11, i == 0 ? DPBootUI.PINK : i == 1 ? DPBootUI.BLUE : DPBootUI.WHITE);
    }

    private static void decodeLogo(String path, int idx) {
        try {
            java.io.InputStream in = DPBootViews.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path);
            if (in == null) return;
            java.awt.image.BufferedImage img;
            try (java.io.InputStream is = in) { img = javax.imageio.ImageIO.read(is); }
            if (img == null) return;
            int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 256), ow = img.getWidth() / step, oh = img.getHeight() / step;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(ow * oh * 4).order(java.nio.ByteOrder.nativeOrder());
            for (int yy = 0; yy < oh; yy++) for (int xx = 0; xx < ow; xx++) {
                int p = img.getRGB(xx * step, yy * step);
                buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            }
            buf.flip();
            logoRW = ow; logoRH = oh; logoFor = idx;
            logoReady = buf;
        } catch (Throwable ignored) { }
    }

    // ================================================================== #31 animated registry counters + #30 facts
    private static final String[] SHOWN_S = new String[DPBoot.REG_L.length];
    private static final int[] SHOWN_I = new int[DPBoot.REG_L.length];

    /** rolling counters: each frame the shown number moves 15% of the way to the real one */
    static String counter(int i) {
        DPBoot.REG_SHOWN[i] += (DPBoot.REG_N[i] - DPBoot.REG_SHOWN[i]) * 0.15f;
        if (Math.abs(DPBoot.REG_N[i] - DPBoot.REG_SHOWN[i]) < 0.5f) DPBoot.REG_SHOWN[i] = DPBoot.REG_N[i];
        int v = Math.round(DPBoot.REG_SHOWN[i]);
        if (SHOWN_S[i] == null || SHOWN_I[i] != v) { SHOWN_I[i] = v; SHOWN_S[i] = String.format(java.util.Locale.ROOT, "%,d", v); }
        return SHOWN_S[i];
    }

    private static String[] factLines;
    private static long factAt;

    private static void facts(int x, int y, int w, int h) {
        if (DPBoot.REG_N[1] == 0) { DPBootUI.text("the registries are counted at Init - check back in a moment", x, y + 4, DPBootUI.DIM); return; }
        int cols = 3, cw = w / cols, ch = 34;
        for (int i = 0; i < DPBoot.REG_L.length; i++) {
            int cx = x + (i % cols) * cw, cy = y + 4 + (i / cols) * (ch + 6);
            DPBootUI.rect(cx, cy, cx + cw - 6, cy + ch, 0x60000000);
            DPBootUI.rect(cx, cy, cx + cw - 6, cy + 1, i % 2 == 0 ? DPBootUI.PINK : DPBootUI.BLUE);
            DPBootUI.text(DPBoot.REG_L[i], cx + 5, cy + 5, DPBootUI.DIM);
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glTranslatef(cx + 5, cy + 16, 0);
            org.lwjgl.opengl.GL11.glScalef(1.5f, 1.5f, 1f);
            DPBootUI.text(counter(i), 0, 0, DPBootUI.WHITE);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
        long now = System.currentTimeMillis();
        if (factLines == null || now - factAt > 3000) {
            factAt = now;
            int mods = Math.max(1, DPBoot.modsTotal);
            int[] r = DPBoot.REG_N;
            factLines = new String[]{
                    String.format(java.util.Locale.ROOT, "That's %.1f items for every mod in the pack.", r[1] / (float) mods),
                    String.format(java.util.Locale.ROOT, "Seeing every item for 1 second each would take %s.", DPBoot.fmt(r[1] * 1000L)),
                    String.format(java.util.Locale.ROOT, "%,d recipes - a crafting table's worst nightmare.", r[5]),
                    String.format(java.util.Locale.ROOT, "%,d kinds of mobs and things that move.", r[2]),
                    String.format(java.util.Locale.ROOT, "%,d biomes to get lost in.", r[3])};
        }
        int fy = y + 4 + ((DPBoot.REG_L.length + cols - 1) / cols) * (ch + 6) + 4;
        for (int i = 0; i < factLines.length && fy + i * 11 < y + h - 8; i++) DPBootUI.text(DPBootUI.fit(150 + i, factLines[i], w), x, fy + i * 11, DPBootUI.WHITE);
    }

    // ---- #30 achievement toast: slides in above the box on the right, 4 s each
    private static String toast;
    private static long toastAt;

    static void toasts(int right, int bottom) {
        long now = System.currentTimeMillis();
        if ((toast == null || now - toastAt > 4000) && !DPBoot.TOASTS.isEmpty()) { toast = DPBoot.TOASTS.pollFirst(); toastAt = now; }
        if (toast == null || now - toastAt > 4000) return;
        float k = Math.min(1f, (now - toastAt) / 250f) * Math.min(1f, (4000 - (now - toastAt)) / 250f);   // slide in + out
        int tw = Math.max(160, DPBootUI.width(toast) + 40), th = 28, x = right - (int) (tw * k), y = bottom - th;
        DPBootUI.rect(x, y, x + tw, y + th, 0xEE140E22);
        DPBootUI.rect(x, y, x + 2, y + th, 0xFFFFED00);
        DPBootUI.rect(x + 6, y + 6, x + 22, y + 22, 0xFFFFED00);   // a little gold "trophy" square
        DPBootUI.rect(x + 9, y + 9, x + 19, y + 19, 0xFFFF8C00);
        DPBootUI.text("Achievement get!", x + 28, y + 5, 0xFFFFED00);
        DPBootUI.text(toast, x + 28, y + 16, DPBootUI.WHITE);
    }

    // ================================================================== #36 developer mode
    static final String[] DEV = {"Stages", "Main thread", "Exceptions", "Forge events", "ASM", "Memory pools"};
    static int devTab;
    private static final List<String> DEV_LINES = new ArrayList<String>();
    private static final List<Integer> DEV_COL = new ArrayList<Integer>();
    private static long devAt;
    private static int devFor = -1;

    private static void dl(String s, int c) { DEV_LINES.add(s); DEV_COL.add(c); }

    private static void dev(int x, int y, int w, int h) {
        int tx = x;
        for (int i = 0; i < DEV.length; i++) {
            int tw = DPBootUI.width(DEV[i]) + 8;
            if (DPBootUI.button(tx, y, tw, 11, DEV[i], devTab == i)) devTab = i;
            tx += tw + 2;
        }
        long now = System.currentTimeMillis();
        if (now - devAt > 1000 || devFor != devTab) {      // rebuilt once a second
            devAt = now; devFor = devTab;
            DEV_LINES.clear(); DEV_COL.clear();
            try { devBuild(); } catch (Throwable t) { dl("couldn't read: " + t, DPBootUI.BAD); }
        }
        int ly = y + 15;
        for (int i = 0; i < DEV_LINES.size() && ly < y + h - 8; i++, ly += 10)
            DPBootUI.text(DPBootUI.fit(400 + Math.min(100, i), DEV_LINES.get(i), w), x, ly, DEV_COL.get(i));
    }

    private static void devBuild() {
        switch (devTab) {
            case 0: {                                        // where the time went so far, per loading stage
                java.util.Map<String, Long> st = new java.util.LinkedHashMap<String, Long>(DPBoot.STAGE_MS);
                for (java.util.Map.Entry<String, Long> e : DPBoot.ranked(st)) dl(String.format(java.util.Locale.ROOT, "%8.1f s  %s", e.getValue() / 1000f, e.getKey()), DPBootUI.WHITE);
                dl("now: " + DPBoot.DASH_V[1] + "  (" + DPBoot.DASH_V[5] + ")", DPBootUI.PINK);
                break;
            }
            case 1: {                                        // the loading thread's stack right now
                Thread m = DPBoot.findMain();
                if (m == null) { dl("loading thread not found", DPBootUI.DIM); break; }
                dl(m.getName() + "  (" + m.getState() + ")", DPBootUI.PINK);
                StackTraceElement[] st = m.getStackTrace();
                for (int i = 0; i < Math.min(30, st.length); i++) dl("  at " + st[i], i == 0 ? DPBootUI.WHITE : DPBootUI.DIM);
                break;
            }
            case 2: {                                        // exceptions with their first stack frames
                List<DPLogBuffer.Line> ls = DPLogBuffer.snapshot(true);
                int shown = 0;
                for (int i = ls.size() - 1; i >= 0 && shown < 12; i--) {
                    DPLogBuffer.Line l = ls.get(i);
                    if (l.thrown == null) continue;
                    shown++;
                    dl((l.logger == null ? "" : "[" + l.logger + "] ") + l.msg, l.lvl >= 3 ? DPBootUI.BAD : DPBootUI.WARN);
                    dl("  " + l.thrown, DPBootUI.WHITE);
                    if (l.stack != null) for (String f : l.stack) dl("    at " + f, DPBootUI.DIM);
                }
                if (shown == 0) dl("no exceptions logged - nice", DPBootUI.GOOD);
                break;
            }
            case 3: {                                        // the latest Forge / FML log lines
                List<DPLogBuffer.Line> ls = DPLogBuffer.snapshot(false);
                List<DPLogBuffer.Line> f = new ArrayList<DPLogBuffer.Line>();
                for (DPLogBuffer.Line l : ls) if (l.forge) f.add(l);
                for (int i = Math.max(0, f.size() - 40); i < f.size(); i++) dl(f.get(i).msg, f.get(i).lvl >= 3 ? DPBootUI.BAD : f.get(i).lvl == 2 ? DPBootUI.WARN : DPBootUI.WHITE);
                break;
            }
            case 4: {                                        // class transformers (coremods + mixin) in order
                List<net.minecraft.launchwrapper.IClassTransformer> tr = net.minecraft.launchwrapper.Launch.classLoader.getTransformers();
                dl(tr.size() + " class transformers run on every class, in this order:", DPBootUI.PINK);
                for (net.minecraft.launchwrapper.IClassTransformer t : tr) dl("  " + t.getClass().getName(), DPBootUI.WHITE);
                dl(String.format(java.util.Locale.ROOT, "%,d classes loaded so far", java.lang.management.ManagementFactory.getClassLoadingMXBean().getLoadedClassCount()), DPBootUI.BLUE);
                break;
            }
            default: {                                       // every JVM memory pool
                for (java.lang.management.MemoryPoolMXBean p : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
                    java.lang.management.MemoryUsage u = p.getUsage();
                    dl(String.format(java.util.Locale.ROOT, "%-28s %10s used  %10s max", p.getName(), DPSysInfo.bytes(u.getUsed()), u.getMax() < 0 ? "-" : DPSysInfo.bytes(u.getMax())),
                            u.getMax() > 0 && u.getUsed() > u.getMax() * 0.9 ? DPBootUI.WARN : DPBootUI.WHITE);
                }
                break;
            }
        }
    }
}
