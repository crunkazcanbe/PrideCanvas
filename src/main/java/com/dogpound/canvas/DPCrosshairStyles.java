package com.dogpound.canvas;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pride Crosshair shapes. Every shape is drawn from geometry (no textures), so it stays crisp at any size and takes any
 * colour. u = one "pixel" (1 GUI pixel at size 100%); shapes are about 9u across like the vanilla crosshair.
 * Callers set up blending and disable textures (see begin()).
 */
public final class DPCrosshairStyles {
    private DPCrosshairStyles() {}

    interface Shape { void draw(float x, float y, float u, int c); }

    /** id -> {menu name}; LinkedHashMap keeps the picker order */
    public static final Map<String, String> NAMES = new LinkedHashMap<String, String>();
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<String, Shape>();
    private static final int[] PRIDE = { 0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982 };
    private static final int[] TRANS = { 0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA };

    private static void add(String id, String name, Shape s) { NAMES.put(id, name); SHAPES.put(id, s); }

    public static boolean exists(String id) { return SHAPES.containsKey(id); }

    static {
        add("cross", "Classic cross", (x, y, u, c) -> { rect(x - 4.5F * u, y - 0.5F * u, x + 4.5F * u, y + 0.5F * u, c); rect(x - 0.5F * u, y - 4.5F * u, x + 0.5F * u, y - 0.5F * u, c); rect(x - 0.5F * u, y + 0.5F * u, x + 0.5F * u, y + 4.5F * u, c); });
        add("cross_thick", "Thick cross", (x, y, u, c) -> { rect(x - 5 * u, y - u, x + 5 * u, y + u, c); rect(x - u, y - 5 * u, x + u, y - u, c); rect(x - u, y + u, x + u, y + 5 * u, c); });
        add("cross_open", "Open cross", (x, y, u, c) -> { rect(x - 5 * u, y - 0.5F * u, x - 1.5F * u, y + 0.5F * u, c); rect(x + 1.5F * u, y - 0.5F * u, x + 5 * u, y + 0.5F * u, c); rect(x - 0.5F * u, y - 5 * u, x + 0.5F * u, y - 1.5F * u, c); rect(x - 0.5F * u, y + 1.5F * u, x + 0.5F * u, y + 5 * u, c); });
        add("cross_open_dot", "Open cross + dot", (x, y, u, c) -> { SHAPES.get("cross_open").draw(x, y, u, c); rect(x - 0.5F * u, y - 0.5F * u, x + 0.5F * u, y + 0.5F * u, c); });
        add("cross_diagonal", "Diagonal cross", (x, y, u, c) -> { line(x - 4 * u, y - 4 * u, x + 4 * u, y + 4 * u, u, c); line(x - 4 * u, y + 4 * u, x + 4 * u, y - 4 * u, u, c); });
        add("x_small", "Small X", (x, y, u, c) -> { line(x - 2.5F * u, y - 2.5F * u, x + 2.5F * u, y + 2.5F * u, u, c); line(x - 2.5F * u, y + 2.5F * u, x + 2.5F * u, y - 2.5F * u, u, c); });
        add("x_open", "Open X", (x, y, u, c) -> { for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) line(x + sx * 1.5F * u, y + sy * 1.5F * u, x + sx * 4.5F * u, y + sy * 4.5F * u, u, c); });
        add("plus_dot", "Plus + dot", (x, y, u, c) -> { rect(x - 3 * u, y - 0.5F * u, x - 1.5F * u, y + 0.5F * u, c); rect(x + 1.5F * u, y - 0.5F * u, x + 3 * u, y + 0.5F * u, c); rect(x - 0.5F * u, y - 3 * u, x + 0.5F * u, y - 1.5F * u, c); rect(x - 0.5F * u, y + 1.5F * u, x + 0.5F * u, y + 3 * u, c); rect(x - 0.5F * u, y - 0.5F * u, x + 0.5F * u, y + 0.5F * u, c); });
        add("dot", "Dot", (x, y, u, c) -> disc(x, y, 0.9F * u, c));
        add("dot_big", "Big dot", (x, y, u, c) -> disc(x, y, 1.8F * u, c));
        add("dot_hollow", "Hollow dot", (x, y, u, c) -> ring(x, y, 1.6F * u, 0.8F * u, 1F, c));
        add("circle", "Circle", (x, y, u, c) -> ring(x, y, 3.5F * u, 0.9F * u, 1F, c));
        add("circle_large", "Big circle", (x, y, u, c) -> ring(x, y, 6F * u, 0.9F * u, 1F, c));
        add("ring_dot", "Ring + dot", (x, y, u, c) -> { ring(x, y, 3.5F * u, 0.9F * u, 1F, c); disc(x, y, 0.8F * u, c); });
        add("circle_cross", "Circle + cross", (x, y, u, c) -> { ring(x, y, 4F * u, 0.8F * u, 1F, c); rect(x - 2 * u, y - 0.4F * u, x + 2 * u, y + 0.4F * u, c); rect(x - 0.4F * u, y - 2 * u, x + 0.4F * u, y + 2 * u, c); });
        add("sniper", "Scope", (x, y, u, c) -> { ring(x, y, 5F * u, 0.8F * u, 1F, c); rect(x - 7 * u, y - 0.4F * u, x - 2 * u, y + 0.4F * u, c); rect(x + 2 * u, y - 0.4F * u, x + 7 * u, y + 0.4F * u, c); rect(x - 0.4F * u, y - 7 * u, x + 0.4F * u, y - 2 * u, c); rect(x - 0.4F * u, y + 2 * u, x + 0.4F * u, y + 7 * u, c); disc(x, y, 0.6F * u, c); });
        add("bullseye", "Bullseye", (x, y, u, c) -> { ring(x, y, 5F * u, 0.7F * u, 1F, c); ring(x, y, 2.8F * u, 0.7F * u, 1F, c); disc(x, y, 0.9F * u, c); });
        add("square", "Square", (x, y, u, c) -> box(x, y, 3.5F * u, 0.9F * u, c));
        add("square_large", "Big square", (x, y, u, c) -> box(x, y, 6F * u, 0.9F * u, c));
        add("square_dot", "Square + dot", (x, y, u, c) -> { box(x, y, 3.5F * u, 0.9F * u, c); rect(x - 0.6F * u, y - 0.6F * u, x + 0.6F * u, y + 0.6F * u, c); });
        add("diamond", "Diamond", (x, y, u, c) -> diamond(x, y, 4F * u, u, c));
        add("diamond_large", "Big diamond", (x, y, u, c) -> diamond(x, y, 6.5F * u, u, c));
        add("brackets", "Brackets", (x, y, u, c) -> brackets(x, y, 5 * u, 2.5F * u, 0.9F * u, c));
        add("brackets_round", "Round brackets", (x, y, u, c) -> { for (int i = 0; i < 4; i++) arc(x, y, 5F * u, 0.9F * u, i * 90 + 20, 50, c); });
        add("brackets_side", "Side brackets", (x, y, u, c) -> { for (int s = -1; s <= 1; s += 2) { rect(x + s * 5 * u - 0.45F * u, y - 3.5F * u, x + s * 5 * u + 0.45F * u, y + 3.5F * u, c); rect(Math.min(x + s * 5 * u, x + s * 3.5F * u), y - 3.5F * u, Math.max(x + s * 5 * u, x + s * 3.5F * u), y - 2.6F * u, c); rect(Math.min(x + s * 5 * u, x + s * 3.5F * u), y + 2.6F * u, Math.max(x + s * 5 * u, x + s * 3.5F * u), y + 3.5F * u, c); } });
        add("caret", "Caret ^", (x, y, u, c) -> { line(x - 3.5F * u, y + 2.5F * u, x, y - 1 * u, u, c); line(x, y - 1 * u, x + 3.5F * u, y + 2.5F * u, u, c); });
        add("chevron", "Chevron v", (x, y, u, c) -> { line(x - 3.5F * u, y - 1.5F * u, x, y + 2 * u, u, c); line(x, y + 2 * u, x + 3.5F * u, y - 1.5F * u, u, c); });
        add("t_shape", "T", (x, y, u, c) -> { rect(x - 4.5F * u, y - 0.5F * u, x + 4.5F * u, y + 0.5F * u, c); rect(x - 0.5F * u, y + 1.5F * u, x + 0.5F * u, y + 5 * u, c); });
        add("lines", "Two lines", (x, y, u, c) -> { rect(x - 3.5F * u, y - 3 * u, x - 2.5F * u, y + 3 * u, c); rect(x + 2.5F * u, y - 3 * u, x + 3.5F * u, y + 3 * u, c); });
        add("dash", "Dash", (x, y, u, c) -> rect(x - 3 * u, y - 0.5F * u, x + 3 * u, y + 0.5F * u, c));
        add("arrows", "Four arrows", (x, y, u, c) -> { for (int i = 0; i < 4; i++) { float a = (float) (i * Math.PI / 2), cx = x + (float) Math.cos(a) * 4.5F * u, cy = y + (float) Math.sin(a) * 4.5F * u; tri(cx - (float) Math.cos(a) * 2 * u, cy - (float) Math.sin(a) * 2 * u, cx + (float) -Math.sin(a) * 1.5F * u, cy + (float) Math.cos(a) * 1.5F * u, cx - (float) -Math.sin(a) * 1.5F * u, cy - (float) Math.cos(a) * 1.5F * u, c); } });
        add("triangle", "Triangle", (x, y, u, c) -> { line(x, y - 4 * u, x + 3.5F * u, y + 2.5F * u, 0.9F * u, c); line(x + 3.5F * u, y + 2.5F * u, x - 3.5F * u, y + 2.5F * u, 0.9F * u, c); line(x - 3.5F * u, y + 2.5F * u, x, y - 4 * u, 0.9F * u, c); });
        add("heart", "Heart", (x, y, u, c) -> heart(x, y, u, c));
        add("star", "Star", (x, y, u, c) -> star(x, y, 4.5F * u, 1.9F * u, 5, 0F, c));
        add("sparkle", "Sparkle", (x, y, u, c) -> star(x, y, 5F * u, 1.1F * u, 4, 0F, c));
        add("flower", "Flower", (x, y, u, c) -> { for (int i = 0; i < 5; i++) { double a = i * Math.PI * 2 / 5 - Math.PI / 2; disc(x + (float) Math.cos(a) * 2.3F * u, y + (float) Math.sin(a) * 2.3F * u, 1.4F * u, c); } disc(x, y, 1.1F * u, 0xFFFFED00 | (c & 0xFF000000)); });
        add("shield", "Shield", (x, y, u, c) -> { rect(x - 3 * u, y - 4 * u, x + 3 * u, y - 3 * u, c); rect(x - 3 * u, y - 4 * u, x - 2 * u, y + 1 * u, c); rect(x + 2 * u, y - 4 * u, x + 3 * u, y + 1 * u, c); line(x - 2.5F * u, y + 0.5F * u, x, y + 4 * u, u, c); line(x + 2.5F * u, y + 0.5F * u, x, y + 4 * u, u, c); });
        add("pride_ring", "Pride ring", (x, y, u, c) -> { for (int i = 0; i < 6; i++) arc(x, y, 3.8F * u, 1.2F * u, i * 60, 60, alpha(PRIDE[i], c)); disc(x, y, 0.7F * u, alpha(0xFFFFFFFF, c)); });
        add("trans_ring", "Trans ring", (x, y, u, c) -> { for (int i = 0; i < 5; i++) ring(x, y, (5 - i) * 0.9F * u, 0.9F * u, 1F, alpha(TRANS[i], c)); });
        add("pride_cross", "Pride cross", (x, y, u, c) -> { for (int i = 0; i < 6; i++) { float o = (i - 2.5F) * 1.4F * u; rect(x + o - 0.7F * u, y - 4 * u, x + o + 0.7F * u, y - 1.5F * u, alpha(PRIDE[i], c)); rect(x + o - 0.7F * u, y + 1.5F * u, x + o + 0.7F * u, y + 4 * u, alpha(PRIDE[i], c)); } });
        add("paw", "Paw", (x, y, u, c) -> { disc(x, y + 1.5F * u, 2F * u, c); disc(x - 2.6F * u, y - 1.2F * u, 0.9F * u, c); disc(x - 0.9F * u, y - 2.6F * u, 0.9F * u, c); disc(x + 0.9F * u, y - 2.6F * u, 0.9F * u, c); disc(x + 2.6F * u, y - 1.2F * u, 0.9F * u, c); });
        add("crescent", "Moon", (x, y, u, c) -> { for (int i = 0; i < 24; i++) { double a0 = Math.PI * 0.35 + i * Math.PI * 1.3 / 24; arcSeg(x, y, 3.8F * u, 1.4F * u - (float) Math.abs(i - 12) / 12F * 1.1F * u, (float) Math.toDegrees(a0), 1.3F * 180 / 24, c); } });
        // ---- cute shapes (requested feature)
        add("bow", "Bow", (x, y, u, c) -> { tri(x, y, x - 4.5F * u, y - 3 * u, x - 4.5F * u, y + 3 * u, c); tri(x, y, x + 4.5F * u, y - 3 * u, x + 4.5F * u, y + 3 * u, c); disc(x, y, 1.3F * u, c); line(x - 0.5F * u, y + 1 * u, x - 2 * u, y + 4.5F * u, 0.8F * u, c); line(x + 0.5F * u, y + 1 * u, x + 2 * u, y + 4.5F * u, 0.8F * u, c); });
        add("bear", "Teddy bear", (x, y, u, c) -> { disc(x - 2.7F * u, y - 2.7F * u, 1.4F * u, c); disc(x + 2.7F * u, y - 2.7F * u, 1.4F * u, c); disc(x, y, 3.4F * u, c); int d = (c & 0xFF000000) | 0x3A1A10; disc(x - 1.2F * u, y - 0.6F * u, 0.45F * u, d); disc(x + 1.2F * u, y - 0.6F * u, 0.45F * u, d); disc(x, y + 1.2F * u, 0.9F * u, (c & 0xFF000000) | 0xF0C9A0); disc(x, y + 0.9F * u, 0.35F * u, d); });
        add("kitty", "Kitty", (x, y, u, c) -> { tri(x - 3.2F * u, y - 1 * u, x - 2.6F * u, y - 4.8F * u, x - 0.6F * u, y - 2.6F * u, c); tri(x + 3.2F * u, y - 1 * u, x + 2.6F * u, y - 4.8F * u, x + 0.6F * u, y - 2.6F * u, c); disc(x, y, 3.2F * u, c); int d = (c & 0xFF000000) | 0x2A1A2A; disc(x - 1.2F * u, y - 0.3F * u, 0.45F * u, d); disc(x + 1.2F * u, y - 0.3F * u, 0.45F * u, d); disc(x, y + 0.9F * u, 0.35F * u, (c & 0xFF000000) | 0xFF7FAE); });
        add("lollipop", "Lollipop", (x, y, u, c) -> { line(x, y + 1 * u, x, y + 5.5F * u, 0.8F * u, (c & 0xFF000000) | 0xFFFFFF); disc(x, y - 1 * u, 3 * u, c); arc(x, y - 1 * u, 1.6F * u, 0.6F * u, 0, 270, (c & 0xFF000000) | 0xFFFFFF); });
        add("cupcake", "Cupcake", (x, y, u, c) -> { tri(x - 3 * u, y, x + 3 * u, y, x + 2.2F * u, y + 3.5F * u, (c & 0xFF000000) | 0xA8E0FF); tri(x - 3 * u, y, x - 2.2F * u, y + 3.5F * u, x + 2.2F * u, y + 3.5F * u, (c & 0xFF000000) | 0xA8E0FF); disc(x - 1.5F * u, y - 0.6F * u, 1.8F * u, c); disc(x + 1.5F * u, y - 0.6F * u, 1.8F * u, c); disc(x, y - 1.8F * u, 2 * u, c); disc(x, y - 4 * u, 0.9F * u, (c & 0xFF000000) | 0xFF3A6A); });
        add("candy", "Wrapped candy", (x, y, u, c) -> { tri(x - 2 * u, y, x - 5 * u, y - 2 * u, x - 5 * u, y + 2 * u, c); tri(x + 2 * u, y, x + 5 * u, y - 2 * u, x + 5 * u, y + 2 * u, c); disc(x, y, 2.4F * u, c); line(x - 1 * u, y - 1.8F * u, x + 1 * u, y + 1.8F * u, 0.5F * u, (c & 0xFF000000) | 0xFFFFFF); });
        add("cloud", "Cloud", (x, y, u, c) -> { disc(x - 2.4F * u, y + 0.6F * u, 1.8F * u, c); disc(x, y - 0.6F * u, 2.4F * u, c); disc(x + 2.4F * u, y + 0.6F * u, 1.8F * u, c); rect(x - 2.4F * u, y + 0.6F * u, x + 2.4F * u, y + 2.4F * u, c); });
        add("butterfly", "Butterfly", (x, y, u, c) -> { disc(x - 2.2F * u, y - 1.5F * u, 2 * u, c); disc(x + 2.2F * u, y - 1.5F * u, 2 * u, c); disc(x - 1.8F * u, y + 1.8F * u, 1.4F * u, c); disc(x + 1.8F * u, y + 1.8F * u, 1.4F * u, c); line(x, y - 3 * u, x, y + 3 * u, 0.8F * u, (c & 0xFF000000) | 0x3A1A2A); });
        add("strawberry", "Strawberry", (x, y, u, c) -> { disc(x - 1.2F * u, y - 0.5F * u, 2.2F * u, c); disc(x + 1.2F * u, y - 0.5F * u, 2.2F * u, c); tri(x - 3.2F * u, y, x + 3.2F * u, y, x, y + 4 * u, c); tri(x - 2 * u, y - 2.6F * u, x + 2 * u, y - 2.6F * u, x, y - 1.2F * u, (c & 0xFF000000) | 0x5AD15A); });
        add("cherries", "Cherries", (x, y, u, c) -> { line(x - 1.8F * u, y + 1.5F * u, x + 1 * u, y - 4 * u, 0.6F * u, (c & 0xFF000000) | 0x5AA040); line(x + 1.8F * u, y + 2 * u, x + 1 * u, y - 4 * u, 0.6F * u, (c & 0xFF000000) | 0x5AA040); disc(x - 1.8F * u, y + 2 * u, 1.7F * u, c); disc(x + 1.8F * u, y + 2.5F * u, 1.7F * u, c); });
        add("sakura", "Sakura flower", (x, y, u, c) -> { for (int i = 0; i < 5; i++) { double a = i * Math.PI * 2 / 5 - Math.PI / 2; disc(x + (float) Math.cos(a) * 2.4F * u, y + (float) Math.sin(a) * 2.4F * u, 1.6F * u, c); } disc(x, y, 1 * u, (c & 0xFF000000) | 0xFFF0A0); });
        add("double_heart", "Two hearts", (x, y, u, c) -> { heart(x - 1.8F * u, y - 0.8F * u, 0.7F * u, c); heart(x + 1.8F * u, y + 1 * u, 0.7F * u, (c & 0xFF000000) | 0xFFB0D0); });
        add("bubble_ring", "Bubble ring", (x, y, u, c) -> { for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; disc(x + (float) Math.cos(a) * 4 * u, y + (float) Math.sin(a) * 4 * u, (i % 2 == 0 ? 0.9F : 0.6F) * u, c); } });
        add("none", "Nothing (hidden)", (x, y, u, c) -> { });
    }

    /** draw a shape (and its shadow) — u = size of one "pixel" */
    public static void draw(String id, float x, float y, float u, int color, boolean shadow) {
        Shape s = SHAPES.get(id);
        if (s == null) s = SHAPES.get("cross");
        if (shadow) s.draw(x + 0.5F * Math.max(1F, u), y + 0.5F * Math.max(1F, u), u, ((int) (((color >>> 24) & 0xFF) * 0.45F) << 24));
        s.draw(x, y, u, color);
    }

    private static int alpha(int rgb, int c) { return (c & 0xFF000000) | (rgb & 0xFFFFFF); }

    // ------------------------------------------------------------------ primitives

    static void rect(float x0, float y0, float x1, float y1, int c) {
        BufferBuilder b = begin(GL11.GL_QUADS);
        v(b, x0, y1, c); v(b, x1, y1, c); v(b, x1, y0, c); v(b, x0, y0, c);
        Tessellator.getInstance().draw();
    }

    static void line(float x0, float y0, float x1, float y1, float w, int c) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        BufferBuilder b = begin(GL11.GL_QUADS);
        v(b, x0 + nx, y0 + ny, c); v(b, x1 + nx, y1 + ny, c); v(b, x1 - nx, y1 - ny, c); v(b, x0 - nx, y0 - ny, c);
        Tessellator.getInstance().draw();
    }

    static void tri(float ax, float ay, float bx, float by, float cx, float cy, int c) {
        BufferBuilder b = begin(GL11.GL_TRIANGLES);
        v(b, ax, ay, c); v(b, bx, by, c); v(b, cx, cy, c);
        Tessellator.getInstance().draw();
    }

    static void disc(float x, float y, float r, int c) {
        BufferBuilder b = begin(GL11.GL_TRIANGLE_FAN);
        v(b, x, y, c);
        int n = Math.max(12, (int) (r * 6));
        for (int i = 0; i <= n; i++) { double a = -i * Math.PI * 2 / n; v(b, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, c); }
        Tessellator.getInstance().draw();
    }

    /** ring of radius r, thickness t, clockwise from the top for frac of the way round */
    public static void ring(float x, float y, float r, float t, float frac, int c) { if (frac > 0) arc(x, y, r, t, -90, 360 * Math.min(1F, frac), c); }

    static void arc(float x, float y, float r, float t, float startDeg, float sweepDeg, int c) { arcSeg(x, y, r, t, startDeg, sweepDeg, c); }

    private static void arcSeg(float x, float y, float r, float t, float startDeg, float sweepDeg, int c) {
        int n = Math.max(4, (int) (Math.abs(sweepDeg) / 6));
        BufferBuilder b = begin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * i / n);
            float co = (float) Math.cos(a), si = (float) Math.sin(a);
            v(b, x + co * (r + t / 2), y + si * (r + t / 2), c);
            v(b, x + co * (r - t / 2), y + si * (r - t / 2), c);
        }
        Tessellator.getInstance().draw();
    }

    static void box(float x, float y, float h, float t, int c) {
        rect(x - h, y - h, x + h, y - h + t, c); rect(x - h, y + h - t, x + h, y + h, c);
        rect(x - h, y - h, x - h + t, y + h, c); rect(x + h - t, y - h, x + h, y + h, c);
    }

    static void diamond(float x, float y, float h, float t, int c) {
        line(x, y - h, x + h, y, t, c); line(x + h, y, x, y + h, t, c); line(x, y + h, x - h, y, t, c); line(x - h, y, x, y - h, t, c);
    }

    static void brackets(float x, float y, float d, float len, float t, int c) {
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            float px = x + sx * d, py = y + sy * d;
            rect(Math.min(px, px - sx * len), py - t / 2, Math.max(px, px - sx * len), py + t / 2, c);
            rect(px - t / 2, Math.min(py, py - sy * len), px + t / 2, Math.max(py, py - sy * len), c);
        }
    }

    static void star(float x, float y, float rOut, float rIn, int points, float rotDeg, int c) {
        BufferBuilder b = begin(GL11.GL_TRIANGLE_FAN);
        v(b, x, y, c);
        for (int i = 0; i <= points * 2; i++) {
            double a = Math.toRadians(rotDeg - 90) - i * Math.PI / points;
            float r = i % 2 == 0 ? rOut : rIn;
            v(b, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, c);
        }
        Tessellator.getInstance().draw();
    }

    private static final String[] HEART = { ".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..." };
    static void heart(float x, float y, float u, int c) {
        for (int r = 0; r < HEART.length; r++) for (int col = 0; col < 7; col++)
            if (HEART[r].charAt(col) == 'X') rect(x + (col - 3.5F) * u, y + (r - 3F) * u, x + (col - 2.5F) * u, y + (r - 2F) * u, c);
    }

    private static BufferBuilder begin(int mode) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(mode, DefaultVertexFormats.POSITION_COLOR);
        return b;
    }

    private static void v(BufferBuilder b, float x, float y, int c) {
        b.pos(x, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
    }
}
