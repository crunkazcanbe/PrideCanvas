package com.dogpound.canvas;

import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * The loading screen's OWN text renderer: Minecraft's ascii.png drawn with immediate-mode GL on the splash thread.
 *
 * Why: any FontRenderer (even Forge's SplashFontRenderer) can be patched by mods to batch glyphs into the SHARED
 * Tessellator/BufferBuilder (PolyPatcher does). Using it from the splash thread while the main thread draws grew that
 * buffer to 172 MB and crashed the main menu ("newPosition > limit", 2026-10-05). This class never touches Tessellator,
 * BufferBuilder, Gui or any Minecraft rendering class.
 *
 * The PNG is decoded on the main thread (DPBootPreload); the GL texture is created on the splash thread at first use.
 */
final class DPBootFont {
    private DPBootFont() {}

    private static final int[] WIDTH = new int[256];
    private static ByteBuffer pixels;
    private static int imgW, imgH, tex;
    private static boolean failed;

    /** main thread: read ascii.png from the Minecraft jar + measure every glyph like vanilla's readFontTexture */
    static void prepare() {
        if (pixels != null || failed) return;
        try (java.io.InputStream in = DPBootFont.class.getResourceAsStream("/assets/minecraft/textures/font/ascii.png")) {
            BufferedImage img = in == null ? null : javax.imageio.ImageIO.read(in);
            if (img == null) { failed = true; return; }
            imgW = img.getWidth(); imgH = img.getHeight();
            int[] px = img.getRGB(0, 0, imgW, imgH, null, 0, imgW);
            int cw = imgW / 16, ch = imgH / 16;
            for (int c = 0; c < 256; c++) {
                int col = c % 16, row = c / 16, last = -1;
                for (int x = cw - 1; x >= 0 && last < 0; x--)
                    for (int y = 0; y < ch; y++) if (((px[(row * ch + y) * imgW + col * cw + x] >>> 24) & 0xFF) != 0) { last = x; break; }
                WIDTH[c] = c == 32 ? 4 : last < 0 ? 0 : (int) Math.ceil((last + 2) * 8.0 / cw);
            }
            ByteBuffer b = ByteBuffer.allocateDirect(imgW * imgH * 4).order(ByteOrder.nativeOrder());
            for (int p : px) b.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            b.flip();
            pixels = b;
        } catch (Throwable t) { failed = true; System.out.println("[Pride UI] loading-screen font: " + t); }
    }

    private static int glyph(char c) { return c < 256 && WIDTH[c] > 0 || c == ' ' ? c : '?'; }

    static int width(String s) {
        if (s == null) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            w += WIDTH[glyph(c)];
        }
        return w;
    }

    /** the longest start of s that fits in w */
    static String trim(String s, int w) {
        int x = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            x += WIDTH[glyph(c)];
            if (x > w) return s.substring(0, i);
        }
        return s;
    }

    /** word-wrap to lines of at most w */
    static List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (width(test) <= w) { line.setLength(0); line.append(test); continue; }
            if (line.length() > 0) out.add(line.toString());
            line.setLength(0);
            String rest = word;
            while (width(rest) > w) { String part = trim(rest, w); if (part.isEmpty()) break; out.add(part); rest = rest.substring(part.length()); }
            line.append(rest);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** splash thread: draw with a drop shadow (immediate mode, its own texture) */
    static void draw(String s, float x, float y, int argb) {
        if (s == null || s.isEmpty() || failed) return;
        if (tex == 0) {
            if (pixels == null) prepare();
            if (pixels == null) return;
            tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, imgW, imgH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        }
        float a = ((argb >>> 24) & 0xFF) / 255f;
        if (a == 0) a = 1;
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glBegin(GL11.GL_QUADS);
        quads(s, x + 1, y + 1, r * 0.25f, g * 0.25f, b * 0.25f, a);
        quads(s, x, y, r, g, b, a);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void quads(String s, float x, float y, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            int k = glyph(c);
            if (k != ' ') {
                float u = (k % 16) / 16f, v = (k / 16) / 16f, d = 1 / 16f - 0.0001f;
                GL11.glTexCoord2f(u, v); GL11.glVertex2f(x, y);
                GL11.glTexCoord2f(u, v + d); GL11.glVertex2f(x, y + 8);
                GL11.glTexCoord2f(u + d, v + d); GL11.glVertex2f(x + 8, y + 8);
                GL11.glTexCoord2f(u + d, v); GL11.glVertex2f(x + 8, y);
            }
            x += WIDTH[k];
        }
    }
}
