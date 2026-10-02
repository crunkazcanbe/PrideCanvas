package com.dogpound.canvas;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.common.ProgressManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * NATIVE game-start (mod-loading) splash renderer for DogPound. Called by the coremod
 * transformer from inside Forge's SplashProgress render loop, on the splash thread, with
 * the splash's own GL context + 320/240-centered ortho already set.
 *
 * Draws (matching the main menu so the hand-off is seamless): the DogPound WALLPAPER
 * (frame f001 stretched full-screen, exactly like DPBackground draws it on the menu),
 * a translucent green box with a MEMORY bar stacked on top of a 0->100 game-LOAD bar,
 * a readable white scrolling log, a spinner, and the title. Wallpaper is loaded raw on
 * the splash thread; everything is guarded so it can never crash mod loading.
 */
public final class DPSplashHook {
    private DPSplashHook() {}

    private static final int GREEN     = 0xFFF5A9B8; // border / bar fill / title
    private static final int GREEN_DIM = 0x66102808; // translucent green bar track (see-through)
    private static final int BOX_FILL  = 0x0A0A1A06; // clearly translucent -> the moving wallpaper shows through
    private static final int WHITE     = 0xFFFFFFFF; // log / labels (drawn with shadow = readable anywhere)
    private static final int LINE_H    = 10;
    private static final int BAR_H     = 11;         // both bars this tall (fits the label text)

    private static boolean loggedOk = false, loggedErr = false;

    // wallpaper FRAMES, loaded lazily on the splash thread (guarded), ANIMATED like the menu
    private static final int BG_COUNT = 60;
    private static final long BG_FRAME_MS = 83L;   // ~12 fps, same clock as DPBackground
    private static int[] bgFrames;                 // GL ids; 0 = not tried, -1 = failed, >0 = id

    // monotonic progress so the load bar only ever moves forward (0..100), never bounces
    private static float shownPct = 0f;
    // forward-only loading phase (0 construct .. 4 complete) so a stray bar can't slam us to 100
    private static int maxPhase = 0;
    private static final float[] PBASE = { 5f, 25f, 50f, 72f, 88f };
    private static final float[] PSPAN = { 20f, 25f, 22f, 16f, 10f };

    // scrolling log fed from live mod-loading status (version-proof; no log4j appender)
    private static final java.util.Deque<String> LOG = new java.util.concurrent.ConcurrentLinkedDeque<String>();
    private static String lastLine = "";
    private static final int LOG_MAX = 80;

    private static void pushLog(String s) {
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty() || s.equals(lastLine)) return;   // only add when the status actually changes
        lastLine = s;
        LOG.addLast(s);
        while (LOG.size() > LOG_MAX) LOG.pollFirst();
    }

    private static List<String> recentLog(int n) {
        List<String> all = new java.util.ArrayList<String>(LOG);
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }

    public static void render(FontRenderer fr) {
        try {
            if (!DPConfig.enableLoading) return;
            if (!loggedOk) { loggedOk = true; System.out.println("[DogPound] native splash render reached"); }

            // The coremod installs the log buffer on the AppClassLoader; THIS class runs on the
            // LaunchClassLoader, so install here too (idempotent) — otherwise we read an empty
            // buffer and the log box stays blank. This is what makes the log actually show.
            try { DPLogBuffer.install(); } catch (Throwable ignored) {}

            int w = Display.getWidth();
            int h = Display.getHeight();
            if (w <= 0 || h <= 0) return;

            float left = 320 - w / 2f;
            float topY = 240 - h / 2f;

            GL11.glPushMatrix();
            GL11.glTranslatef(left, topY, 0); // (0,0) = top-left of screen, w x h

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // ---- wallpaper, full-screen stretch (same placement as the main menu) ----
            drawWallpaper(w, h);

            // ---- progress: phase-weighted + monotonic so it goes 0->100, never bounces ----
            int progress = Math.round(updateProgress());
            String status = currentStatus();
            pushLog(status);   // build the scrolling log from live mod-loading status (no log4j needed)

            // ---- memory usage % ----
            long maxMem  = Runtime.getRuntime().maxMemory();
            long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            int memPct = maxMem > 0 ? (int) Math.max(0, Math.min(100, 100L * usedMem / maxMem)) : 0;

            // ---- DogPound logo (same art / size / position as the main menu) ----
            drawLogo(w, h);

            // ---- green see-through box: MEMORY bar stacked on the game-LOAD bar + live log ----
            int boxW = Math.min(w - 60, 820);
            int bx = (w - boxW) / 2;
            int top = (int) (h * 0.56);
            int bottom = h - 30;

            int memTop  = top;            // memory bar on top
            int loadTop = top + BAR_H;    // load bar directly under it (same size)

            // translucent fill first -> the wallpaper shows through the inside
            rect(bx, top, bx + boxW, bottom, BOX_FILL);
            // green border as a 2px OUTLINE only (a filled rect here was the opaque slab
            // that made the box look solid no matter the fill alpha)
            rect(bx - 2, top - 2, bx + boxW + 2, top,        GREEN);  // top
            rect(bx - 2, bottom,  bx + boxW + 2, bottom + 2, GREEN);  // bottom
            rect(bx - 2, top - 2, bx,            bottom + 2, GREEN);  // left
            rect(bx + boxW, top - 2, bx + boxW + 2, bottom + 2, GREEN); // right

            // memory bar (top)
            rect(bx, memTop, bx + boxW, memTop + BAR_H, GREEN_DIM);           // track
            int memFill = (int) (boxW * (memPct / 100.0));
            if (memFill > 0) rect(bx, memTop, bx + memFill, memTop + BAR_H, GREEN);
            // game-load bar (under it)
            rect(bx, loadTop, bx + boxW, loadTop + BAR_H, GREEN_DIM);         // track
            int fill = (int) (boxW * (Math.max(1, Math.min(100, progress)) / 100.0));
            if (fill > 0) rect(bx, loadTop, bx + fill, loadTop + BAR_H, GREEN);

            // ---- text (white + shadow = always readable) ----
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            FontRenderer font = splashFont(fr);
            if (font != null) {
                // label inside the MEMORY bar
                font.drawStringWithShadow("MEMORY " + memPct + "%", bx + 4, memTop + 1, WHITE);
                // label inside the LOAD bar
                font.drawStringWithShadow("LOAD " + progress + "%", bx + 4, loadTop + 1, WHITE);
                String eta = DPEta.text();                                   // Zoomies' "about 3m 20s left"
                if (eta != null) font.drawStringWithShadow(eta, bx + boxW - 4 - font.getStringWidth(eta), loadTop + 1, WHITE);

                // scrolling log inside the box — white + shadow so it's readable on the wallpaper
                int logTop = loadTop + BAR_H + 3;
                int avail = bottom - logTop;
                int rows = Math.max(1, avail / LINE_H);
                List<String> lines = recentLog(rows);
                int ly = logTop;
                if (lines.isEmpty()) {
                    font.drawStringWithShadow("Loading…", bx + 5, ly, WHITE);
                } else {
                    for (String ln : lines) {
                        font.drawStringWithShadow(font.trimStringToWidth(ln, boxW - 10), bx + 5, ly, WHITE);
                        ly += LINE_H;
                    }
                }
            }

            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glPopMatrix();
        } catch (Throwable t) {
            if (!loggedErr) { loggedErr = true; System.out.println("[DogPound] native splash ERROR: " + t); }
        }
    }

    private static FontRenderer SPLASH_FR = null;
    private static boolean SPLASH_FR_TRIED = false;
    // Forge runs the boot splash on its own GL context where the normal FontRenderer is
    // unusable, but its private static SplashFontRenderer IS bound there. The transformer
    // could not find the accessor (passes null), so fetch it by reflection -> log/text draws.
    private static FontRenderer splashFont(FontRenderer passed) {
        if (passed != null) return passed;
        if (!SPLASH_FR_TRIED) {
            SPLASH_FR_TRIED = true;
            try {
                Class<?> sp = Class.forName("net.minecraftforge.fml.client.SplashProgress");
                java.lang.reflect.Field f = sp.getDeclaredField("fontRenderer");
                f.setAccessible(true);
                Object o = f.get(null);
                if (o instanceof FontRenderer) SPLASH_FR = (FontRenderer) o;
            } catch (Throwable t) { /* leave null -> box still draws */ }
        }
        return SPLASH_FR;
    }

    // ---- DogPound brand logo (textures/gui/logo.png) — identical art the main menu uses ----
    private static int logoTex = 0;       // 0=untried, -1=failed, >0=GL id
    private static int logoW = 0, logoH = 0;

    private static void drawLogo(int w, int h) {
        try {
            if (logoTex == 0) { logoTex = loadImage("/assets/dpcanvas/textures/gui/logo.png"); if (logoTex <= 0) logoTex = -1; }
            if (logoTex <= 0 || logoW <= 0) return;
            int scale = guiScale(w, h);
            int guiW = w / scale;
            float tw = Math.min(guiW * 0.42f, 360f);          // GUI units, same as DPChrome.drawBrand
            float th = tw * logoH / (float) logoW;
            float maxW = guiW - 20;
            if (tw > maxW) { th = maxW * logoH / (float) logoW; tw = maxW; }
            int pw = Math.round(tw * scale), ph = Math.round(th * scale);   // -> real pixels
            int x = w / 2 - pw / 2, y = 10 * scale;            // LOGO_TOP = 10 GUI px
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, logoTex);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + ph);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + pw, y + ph);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + pw, y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } catch (Throwable t) { /* never crash loading */ }
    }

    // effective GUI scale (mirrors net.minecraft.client.gui.ScaledResolution)
    private static int guiScale(int w, int h) {
        int gs = 2;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.gameSettings != null) gs = mc.gameSettings.guiScale;
        } catch (Throwable t) {}
        int max = (gs == 0) ? 1000 : gs;
        int sf = 1;
        while (sf < max && w / (sf + 1) >= 320 && h / (sf + 1) >= 240) sf++;
        return sf;
    }

    private static int loadImage(String path) {
        try {
            InputStream is = DPSplashHook.class.getResourceAsStream(path);
            if (is == null) return 0;
            BufferedImage img = ImageIO.read(is);
            is.close();
            if (img == null) return 0;
            int iw = img.getWidth(), ih = img.getHeight();
            logoW = iw; logoH = ih;
            int[] px = new int[iw * ih];
            img.getRGB(0, 0, iw, ih, px, 0, iw);
            ByteBuffer buf = ByteBuffer.allocateDirect(iw * ih * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) {
                int p = px[y * iw + x];
                buf.put((byte) ((p >> 16) & 0xFF)); buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF)); buf.put((byte) ((p >> 24) & 0xFF));
            }
            buf.flip();
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, iw, ih, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Throwable t) { return 0; }
    }

    // ---- wallpaper: ANIMATED. Cycle frames f001..f060 on the same ~12fps clock as the menu,
    //      stretched full-screen. Frames load lazily (one per draw) so there's no startup hitch. ----
    private static void drawWallpaper(int w, int h) {
        try {
            if (bgFrames == null) bgFrames = new int[BG_COUNT];
            int idx = (int) ((System.currentTimeMillis() / BG_FRAME_MS) % BG_COUNT);
            int tex = bgFrames[idx];
            if (tex == 0) {                       // not tried yet -> load this frame now
                tex = loadFrame(idx + 1);         // files are 1-based: f001..f060
                bgFrames[idx] = (tex > 0 ? tex : -1);
            }
            if (tex <= 0) {                        // this frame failed -> fall back to frame 1
                int f1 = bgFrames[0];
                if (f1 == 0) { f1 = loadFrame(1); bgFrames[0] = (f1 > 0 ? f1 : -1); }
                if (f1 <= 0) return;
                tex = f1;
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, h);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(w, h);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(w, 0);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } catch (Throwable t) {
            // ignore — never let the wallpaper crash mod loading
        }
    }

    /** Load one wallpaper frame (1-based index) as a GL texture; returns id or 0 on failure. */
    private static int loadFrame(int n) {
        try {
            String path = String.format(Locale.ROOT, "/assets/dpcanvas/textures/bg/f%03d.png", n);
            InputStream is = DPSplashHook.class.getResourceAsStream(path);
            if (is == null) return 0;
            BufferedImage img = ImageIO.read(is);
            is.close();
            if (img == null) return 0;
            int iw = img.getWidth(), ih = img.getHeight();
            int[] px = new int[iw * ih];
            img.getRGB(0, 0, iw, ih, px, 0, iw);
            ByteBuffer buf = ByteBuffer.allocateDirect(iw * ih * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < ih; y++) {
                for (int x = 0; x < iw; x++) {
                    int p = px[y * iw + x];
                    buf.put((byte) ((p >> 16) & 0xFF)); // R
                    buf.put((byte) ((p >> 8) & 0xFF));  // G
                    buf.put((byte) (p & 0xFF));         // B
                    buf.put((byte) ((p >> 24) & 0xFF)); // A
                }
            }
            buf.flip();
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, iw, ih, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---- progress (0..100): forward-only PHASE index + per-phase fraction, eased so it can
    //      neither bounce nor slam to 100 (a stray early "resource"/"reload" bar used to do that) ----
    private static float updateProgress() {
        try {
            Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
            ProgressManager.ProgressBar last = null;
            while (it.hasNext()) last = it.next();

            float frac = 0f;
            if (last != null) {
                String title = last.getTitle();
                String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
                int p = phaseOf(t);
                // advance forward only; never jump to the "complete" phase (4) before init (2)
                if (p > maxPhase && !(p == 4 && maxPhase < 2)) maxPhase = p;
                if (p == maxPhase) {
                    int step = last.getStep(), steps = last.getSteps();
                    if (steps > 0) frac = Math.max(0f, Math.min(1f, (float) step / steps));
                }
            }
            float target = PBASE[maxPhase] + PSPAN[maxPhase] * frac;
            if (target > shownPct) shownPct = Math.min(target, shownPct + 1.5f); // ease up, no slam
            if (shownPct > 98) shownPct = 98;
            if (shownPct < 1) shownPct = 1;
            return shownPct;
        } catch (Throwable t) {
            return shownPct;
        }
    }

    private static int phaseOf(String t) {
        if (t.contains("construct")) return 0;            // constructing mods
        if (t.contains("pre"))       return 1;            // pre-initialization
        if (t.contains("post"))      return 3;            // post-initialization (before generic "init")
        if (t.contains("init"))      return 2;            // initialization
        if (t.contains("complet") || t.contains("finish") || t.contains("freez")) return 4; // final
        if (t.contains("resourc") || t.contains("avail") || t.contains("reload")) return 3; // late, but texture stitching still to come
        return maxPhase;                                   // unknown -> stay in current phase
    }

    private static String currentStatus() {
        try {
            Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
            ProgressManager.ProgressBar last = null;
            while (it.hasNext()) last = it.next();
            if (last == null) return "Loading";
            String title = last.getTitle();
            String msg = last.getMessage();
            if (msg != null && !msg.isEmpty()) return title + " - " + msg;
            return title;
        } catch (Throwable t) {
            return "Loading";
        }
    }

    private static void rect(int x1, int y1, int x2, int y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
    }

    private static void drawSpinner(int cx, int cy, int radius, long t) {
        final int n = 12;
        final int sq = Math.max(6, radius / 4);
        int head = (int) ((t / 70L) % n);
        for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2.0 * i) / n - Math.PI / 2.0;
            int x = cx + (int) Math.round(Math.cos(ang) * radius);
            int y = cy + (int) Math.round(Math.sin(ang) * radius);
            int d = (head - i + n) % n;
            int alpha = Math.max(0x26, 0xFF - d * 26);
            int col = (alpha << 24) | 0xF5A9B8;
            rect(x - sq / 2, y - sq / 2, x + sq / 2, y + sq / 2, col);
        }
    }
}
