package com.dogpound.canvas;

import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loading screen #24 background modes besides the animated wallpaper: a live rainbow aurora (procedural) and a
 * slideshow of her own pictures (config/pride-backgrounds/*.png|jpg). Slides decode on a low-priority thread,
 * scaled down to at most 1280 wide, and upload on the splash thread.
 */
final class DPBootBackdrop {
    private DPBootBackdrop() {}

    private static final int[] RAINBOW = {0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x5BCEFA, 0x732982, 0xF5A9B8};

    /** rainbow aurora: dark sky + 7 soft waving bands (about 300 vertices) */
    static void aurora(int w, int h) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.05f, 0.03f, 0.10f, 1f); GL11.glVertex2f(0, 0); GL11.glVertex2f(w, 0);
        GL11.glColor4f(0.10f, 0.05f, 0.18f, 1f); GL11.glVertex2f(w, h); GL11.glVertex2f(0, h);
        GL11.glEnd();
        double t = (System.currentTimeMillis() % 600_000L) / 1000.0;
        int seg = 40;
        for (int b = 0; b < RAINBOW.length; b++) {
            float r = ((RAINBOW[b] >> 16) & 255) / 255f, g = ((RAINBOW[b] >> 8) & 255) / 255f, bl = (RAINBOW[b] & 255) / 255f;
            float base = h * (0.22f + b * 0.075f), amp = h * 0.05f, thick = h * 0.06f;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= seg; i++) {
                float x = w * i / (float) seg;
                float y = base + (float) (Math.sin(i * 0.35 + t * 0.6 + b * 0.7) * amp + Math.sin(i * 0.11 - t * 0.3) * amp * 0.6);
                GL11.glColor4f(r, g, bl, 0.0f); GL11.glVertex2f(x, y - thick);
                GL11.glColor4f(r, g, bl, 0.28f); GL11.glVertex2f(x, y);
            }
            GL11.glEnd();
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= seg; i++) {
                float x = w * i / (float) seg;
                float y = base + (float) (Math.sin(i * 0.35 + t * 0.6 + b * 0.7) * amp + Math.sin(i * 0.11 - t * 0.3) * amp * 0.6);
                GL11.glColor4f(r, g, bl, 0.28f); GL11.glVertex2f(x, y);
                GL11.glColor4f(r, g, bl, 0.0f); GL11.glVertex2f(x, y + thick);
            }
            GL11.glEnd();
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    // ---- slideshow
    private static File[] slides;
    private static int shown = -1, tex, texW, texH;
    private static volatile ByteBuffer ready;
    private static volatile int readyW, readyH, readyIdx = -1, loading = -1;

    /** draws the current slide; false when there are no pictures (caller falls back to the wallpaper) */
    static boolean slideshow(int w, int h) {
        if (slides == null) {
            File[] fs = new File("config/pride-backgrounds").listFiles((d, n) -> { String l = n.toLowerCase(java.util.Locale.ROOT); return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg"); });
            slides = fs == null ? new File[0] : fs;
            java.util.Arrays.sort(slides);
        }
        if (slides.length == 0) return false;
        int want = (int) ((System.currentTimeMillis() / (1000L * Math.max(2, DPBootSettings.getInt("slidesecs", 12)))) % slides.length);
        ByteBuffer b = ready;
        if (b != null) {
            ready = null;
            if (tex <= 0) tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, readyW, readyH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
            texW = readyW; texH = readyH; shown = readyIdx;
        }
        if (want != shown && loading != want) {
            loading = want;
            final File f = slides[want];
            final int idx = want;
            Thread t = new Thread(() -> decode(f, idx), "Pride-BootSlide");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        if (tex <= 0 || texW <= 0) { aurora(w, h); return true; }   // first picture still decoding
        // "cover": fill the screen, crop the overflow
        float sa = texW / (float) texH, da = w / (float) h, u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (sa > da) { float k = da / sa; u0 = (1 - k) / 2; u1 = 1 - u0; } else { float k = sa / da; v0 = (1 - k) / 2; v1 = 1 - v0; }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(0, h);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(w, 0);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        return true;
    }

    private static void decode(File f, int idx) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) return;
            int step = Math.max(1, img.getWidth() / 1280), ow = img.getWidth() / step, oh = img.getHeight() / step;
            ByteBuffer buf = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) {
                int p = img.getRGB(x * step, y * step);
                buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) 255);
            }
            buf.flip();
            readyW = ow; readyH = oh; readyIdx = idx;
            ready = buf;
        } catch (Throwable ignored) { }
    }

    /** plain dark: the cheapest background */
    static void dark(int w, int h) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.055f, 0.04f, 0.095f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(0, 0); GL11.glVertex2f(0, h); GL11.glVertex2f(w, h); GL11.glVertex2f(w, 0);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    // ---- #25 the sky follows the progress: night -> dawn -> day tint, a sun rising along an arc, rain while stuck
    static void sky(int w, int h, int pct, boolean stuck, boolean drizzle) {
        float p = Math.max(0, Math.min(100, pct)) / 100f;
        float r, g, b, a;
        if (p < 0.5f) { float k = p / 0.5f; r = lerp(0.02f, 0.55f, k); g = lerp(0.02f, 0.22f, k); b = lerp(0.12f, 0.32f, k); a = lerp(0.55f, 0.28f, k); }
        else { float k = (p - 0.5f) / 0.5f; r = lerp(0.55f, 1f, k); g = lerp(0.22f, 0.92f, k); b = lerp(0.32f, 0.75f, k); a = lerp(0.28f, 0.04f, k); }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);                         // tint, stronger at the top like a real sky
        GL11.glColor4f(r, g, b, a); GL11.glVertex2f(0, 0); GL11.glVertex2f(w, 0);
        GL11.glColor4f(r, g, b, a * 0.4f); GL11.glVertex2f(w, h); GL11.glVertex2f(0, h);
        GL11.glEnd();
        // the sun: rises from the bottom-left to high up on the right as loading goes
        double ang = Math.PI * (1.0 - p * 0.85);
        float cx = (float) (w / 2 + Math.cos(ang) * w * 0.45), cy = (float) (h * 0.95 - Math.sin(ang) * h * 0.8), rad = h * 0.035f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(1f, lerp(0.55f, 0.95f, p), lerp(0.3f, 0.7f, p), 0.85f);
        GL11.glVertex2f(cx, cy);
        GL11.glColor4f(1f, 0.6f, 0.3f, 0.0f);
        for (int i = 0; i <= 20; i++) { double t = i * Math.PI * 2 / 20; GL11.glVertex2f(cx + (float) Math.cos(t) * rad * 2.2f, cy + (float) Math.sin(t) * rad * 2.2f); }
        GL11.glEnd();
        if (stuck || drizzle) {                                // rain streaks, no state: positions from time + index
            int drops = stuck ? 90 : 30;
            long now = System.currentTimeMillis() % 1_000_000L;   // small, so float math stays smooth
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 0; i < drops; i++) {
                float sx = ((i * 7919) % 1000) / 1000f * w;
                float sp = 0.6f + ((i * 104729) % 100) / 100f;
                float yy = ((now * sp * 0.6f + i * 137) % (h + 40)) - 20;
                GL11.glColor4f(0.6f, 0.75f, 1f, 0.0f); GL11.glVertex2f(sx, yy);
                GL11.glColor4f(0.6f, 0.75f, 1f, 0.45f); GL11.glVertex2f(sx - 3, yy + 14);
            }
            GL11.glEnd();
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    /** #27 the wallpaper breathes with the bass: a soft pink glow from the bottom */
    static void pulse(int w, int h, float bass) {
        if (bass < 0.05f) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.96f, 0.66f, 0.72f, 0f); GL11.glVertex2f(0, h * 0.35f); GL11.glVertex2f(w, h * 0.35f);
        GL11.glColor4f(0.96f, 0.66f, 0.72f, Math.min(0.22f, bass * 0.25f)); GL11.glVertex2f(w, h); GL11.glVertex2f(0, h);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
