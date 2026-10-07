package com.dogpound.canvas;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loading screen #32: themes. Each one is a palette (panel, accent, second accent, text, dim, top bar stripes) plus an
 * optional overlay (CRT scanlines, Matrix rain). "Custom" reads config/pride-boot-theme.json:
 * {"panel":"D0140E22","accent":"F5A9B8","accent2":"5BCEFA","text":"FFFFFF","dim":"B0A8C8","bar":["E40303","FF8C00"]}
 */
final class DPBootTheme {
    private DPBootTheme() {}

    static final String[] NAMES = {"Pride", "Vanilla", "Futuristic", "Terminal", "Cyberpunk", "Industrial", "Nuclear", "Medieval",
            "Minimal", "CRT", "Matrix", "LCARS", "Custom"};
    // panel, accent, accent2, text, dim, then bar stripes
    private static final int[][] P = {
            {0xD0140E22, 0xFFF5A9B8, 0xFF5BCEFA, 0xFFFFFFFF, 0xFFB0A8C8, 0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982, 0xFF5BCEFA, 0xFFF5A9B8},
            {0xC0000000, 0xFFFFFFFF, 0xFFA0A0A0, 0xFFFFFFFF, 0xFF8A8A8A, 0xFF7A7A7A, 0xFFA0A0A0},
            {0xD0061826, 0xFF00E5FF, 0xFF7AF0FF, 0xFFE0FCFF, 0xFF5F9AA8, 0xFF00E5FF, 0xFF0077FF},
            {0xE0000000, 0xFF33FF66, 0xFF22CC55, 0xFF33FF66, 0xFF1F8F3F, 0xFF33FF66},
            {0xD0140028, 0xFFFF2A6D, 0xFF05D9E8, 0xFFF0F0FF, 0xFF9A7AB0, 0xFFFF2A6D, 0xFFD1F7FF, 0xFF05D9E8, 0xFFFFE600},
            {0xD02A2A2A, 0xFFFF9900, 0xFFC8C8C8, 0xFFF0F0F0, 0xFF9A9A9A, 0xFFFF9900, 0xFF202020, 0xFFFF9900, 0xFF202020},
            {0xD0101A08, 0xFFB6FF00, 0xFFFFE000, 0xFFE8FFD0, 0xFF8AA070, 0xFFFFE000, 0xFF101010, 0xFFFFE000, 0xFF101010},
            {0xD02A1C0E, 0xFFD4A84A, 0xFFB08850, 0xFFE8D8B0, 0xFFA08860, 0xFF8B1A1A, 0xFFD4A84A, 0xFF8B1A1A},
            {0x90000000, 0xFFFFFFFF, 0xFFCCCCCC, 0xFFFFFFFF, 0xFF999999, 0x60FFFFFF},
            {0xE0100800, 0xFFFFB000, 0xFFFF8000, 0xFFFFC040, 0xFFA07000, 0xFFFFB000},
            {0xE0000800, 0xFF00FF41, 0xFF008F11, 0xFF00FF41, 0xFF006B0E, 0xFF00FF41},
            {0xE0000000, 0xFFFF9966, 0xFF9999FF, 0xFFFFCC99, 0xFFCC99CC, 0xFFFF9966, 0xFFCC99CC, 0xFF9999FF, 0xFFFFCC66},
            null};

    static int current = -1;
    private static final String[] GLYPH = new String[90];
    static { for (int i = 0; i < GLYPH.length; i++) GLYPH[i] = String.valueOf((char) ('!' + i)); }

    static void apply() {
        int t = Math.max(0, Math.min(NAMES.length - 1, DPBootSettings.getInt("theme", 0)));
        if (t == current) return;
        current = t;
        int[] p = t == NAMES.length - 1 ? custom() : P[t];
        if (p == null) p = P[0];
        DPBootUI.PANEL = p[0]; DPBootUI.PINK = p[1]; DPBootUI.BLUE = p[2]; DPBootUI.WHITE = p[3]; DPBootUI.DIM = p[4];
        int[] bar = new int[p.length - 5];
        System.arraycopy(p, 5, bar, 0, bar.length);
        DPBootUI.RAINBOW = bar;
    }

    private static int[] custom() {
        try {
            String j = new String(Files.readAllBytes(new File("config/pride-boot-theme.json").toPath()), StandardCharsets.UTF_8);
            int[] p = P[0].clone();
            String[] keys = {"panel", "accent", "accent2", "text", "dim"};
            for (int i = 0; i < keys.length; i++) {
                Matcher m = Pattern.compile("\"" + keys[i] + "\"\\s*:\\s*\"#?([0-9A-Fa-f]{6,8})\"").matcher(j);
                if (m.find()) p[i] = hex(m.group(1), i == 0 ? 0xD0 : 0xFF);
            }
            Matcher bar = Pattern.compile("\"bar\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(j);
            if (bar.find()) {
                Matcher h = Pattern.compile("#?([0-9A-Fa-f]{6,8})").matcher(bar.group(1));
                java.util.List<Integer> l = new java.util.ArrayList<Integer>();
                while (h.find()) l.add(hex(h.group(1), 0xFF));
                if (!l.isEmpty()) {
                    int[] out = new int[5 + l.size()];
                    System.arraycopy(p, 0, out, 0, 5);
                    for (int i = 0; i < l.size(); i++) out[5 + i] = l.get(i);
                    return out;
                }
            }
            return p;
        } catch (Throwable t) { return null; }
    }

    private static int hex(String s, int alpha) {
        long v = Long.parseLong(s, 16);
        return s.length() == 8 ? (int) v : (alpha << 24) | (int) v;
    }

    /** CRT scanlines / Matrix rain, drawn over everything except the Music switch */
    static void overlay(int w, int h) {
        String n = NAMES[Math.max(0, current)];
        if (n.equals("CRT")) {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glColor4f(0f, 0f, 0f, 0.18f);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINES);
            for (int y = 0; y < h; y += 3) { org.lwjgl.opengl.GL11.glVertex2f(0, y + 0.5f); org.lwjgl.opengl.GL11.glVertex2f(w, y + 0.5f); }
            org.lwjgl.opengl.GL11.glEnd();
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
        } else if (n.equals("Matrix")) {
            long now = System.currentTimeMillis() % 1_000_000L;
            int cols = 28, rows = 14;
            for (int c = 0; c < cols; c++) {
                int x = (int) ((c + 0.5f) * w / cols);
                float sp = 0.03f + ((c * 7919) % 50) / 1000f;
                int head = (int) ((now * sp + c * 97) % (h + rows * 10));
                for (int r = 0; r < rows; r++) {
                    int y = head - r * 10;
                    if (y < 0 || y > h) continue;
                    int a = Math.max(0x18, 0xB0 - r * 12);
                    DPBootUI.text(GLYPH[(int) ((c * 31 + r * 17 + now / 150) % GLYPH.length)], x, y, (a << 24) | (r == 0 ? 0xCCFFCC : 0x00FF41));
                }
            }
        }
    }
}
