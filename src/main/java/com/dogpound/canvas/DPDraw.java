package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny vector kit for the HUD themes: polygons (hearts, drops, stars, rounded boxes…) filled flat or with a vertical
 * gradient, clipped for "partly full", outlined, plus outlined text. Coordinates are whatever the caller's matrix says.
 */
final class DPDraw {
    private DPDraw() {}

    // ------------------------------------------------------------------ fills
    static void fill(float[] p, int argb) { fillGrad(p, argb, argb, 0, 1); }

    /** fan from the centre point (fine for hearts, drops, stars, boxes — every shape here is star-shaped) */
    static void fillGrad(float[] p, int top, int bottom, float yTop, float yBot) {
        if (p == null || p.length < 6) return;
        begin();
        float cx = 0, cy = 0;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) { cx += p[2 * i]; cy += p[2 * i + 1]; }
        cx /= n; cy /= n;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vert(b, cx, cy, lerp(top, bottom, (cy - yTop) / (yBot - yTop)));
        for (int i = 0; i <= n; i++) { float x = p[2 * (i % n)], y = p[2 * (i % n) + 1]; vert(b, x, y, lerp(top, bottom, (y - yTop) / (yBot - yTop))); }
        Tessellator.getInstance().draw();
        end();
    }

    static void stroke(float[] p, int argb, float width) {
        if (p == null || p.length < 4) return;
        begin();
        GL11.glLineWidth(Math.max(1F, width));
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < p.length / 2; i++) vert(b, p[2 * i], p[2 * i + 1], argb);
        Tessellator.getInstance().draw();
        GL11.glLineWidth(1F);
        end();
    }

    /** a thick arc (ring gauges): from angle a0 to a1 (radians, 0 = right, clockwise on screen) */
    static void arc(float x, float y, float r, float thick, double a0, double a1, int argb) {
        begin();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int steps = Math.max(2, (int) (Math.abs(a1 - a0) * 12));
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            vert(b, x + c * (r + thick / 2), y + s * (r + thick / 2), argb);
            vert(b, x + c * (r - thick / 2), y + s * (r - thick / 2), argb);
        }
        Tessellator.getInstance().draw();
        end();
    }

    static void rect(float x, float y, float w, float h, int argb) { fill(new float[]{x, y, x + w, y, x + w, y + h, x, y + h}, argb); }

    // ------------------------------------------------------------------ shapes
    static float[] circle(float x, float y, float r) { return ellipse(x, y, r, r, 0); }

    static float[] ellipse(float x, float y, float rx, float ry, double rot) {
        int n = Math.max(12, (int) (Math.max(rx, ry) * 1.2F));
        float[] p = new float[n * 2];
        double c = Math.cos(rot), s = Math.sin(rot);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n, ex = Math.cos(a) * rx, ey = Math.sin(a) * ry;
            p[2 * i] = (float) (x + ex * c - ey * s); p[2 * i + 1] = (float) (y + ex * s + ey * c);
        }
        return p;
    }

    static float[] rrect(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2);
        List<Float> l = new ArrayList<Float>();
        float[][] c = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        for (float[] k : c) for (int i = 0; i <= 5; i++) { double a = Math.toRadians(k[2] + 18 * i); l.add((float) (k[0] + Math.cos(a) * r)); l.add((float) (k[1] + Math.sin(a) * r)); }
        return toArr(l);
    }

    /** heart of width ~1.24*s, from y (top dip) to y+0.85*s (tip) */
    static float[] heart(float x, float y, float s) {
        List<Float> l = new ArrayList<Float>();
        bez(l, x, y + s * .35F, x, y - s * .15F, x - s * .62F, y - s * .2F, x - s * .62F, y + s * .18F);
        bez(l, x - s * .62F, y + s * .18F, x - s * .62F, y + s * .5F, x - s * .2F, y + s * .62F, x, y + s * .85F);
        bez(l, x, y + s * .85F, x + s * .2F, y + s * .62F, x + s * .62F, y + s * .5F, x + s * .62F, y + s * .18F);
        bez(l, x + s * .62F, y + s * .18F, x + s * .62F, y - s * .2F, x, y - s * .15F, x, y + s * .35F);
        return toArr(l);
    }

    static float[] drop(float x, float y, float s) {
        List<Float> l = new ArrayList<Float>();
        bez(l, x, y - s, x + s * .9F, y + s * .1F, x + s * .7F, y + s, x, y + s);
        bez(l, x, y + s, x - s * .7F, y + s, x - s * .9F, y + s * .1F, x, y - s);
        return toArr(l);
    }

    static float[] star(float x, float y, float r) {
        float[] p = new float[20];
        for (int i = 0; i < 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5; float rr = i % 2 == 1 ? r * .45F : r; p[2 * i] = (float) (x + Math.cos(a) * rr); p[2 * i + 1] = (float) (y + Math.sin(a) * rr); }
        return p;
    }

    static float[] diamond(float x, float y, float s) { return new float[]{x, y - s, x + s * .8F, y, x, y + s, x - s * .8F, y}; }

    static float[] sparkle(float x, float y, float r) {
        List<Float> l = new ArrayList<Float>();
        float[][] pts = {{x, y - r}, {x + r, y}, {x, y + r}, {x - r, y}};
        for (int i = 0; i < 4; i++) { float[] a = pts[i], b = pts[(i + 1) % 4]; quad(l, a[0], a[1], x, y, b[0], b[1]); }
        return toArr(l);
    }

    /** keep the part of a polygon on one side of a vertical (xAxis) or horizontal line */
    static float[] clip(float[] p, boolean xAxis, float v, boolean keepLess) {
        if (p == null) return null;
        List<Float> out = new ArrayList<Float>();
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            float ax = p[2 * i], ay = p[2 * i + 1], bx = p[2 * ((i + 1) % n)], by = p[2 * ((i + 1) % n) + 1];
            float ca = xAxis ? ax : ay, cb = xAxis ? bx : by;
            boolean ina = keepLess ? ca <= v : ca >= v, inb = keepLess ? cb <= v : cb >= v;
            if (ina) { out.add(ax); out.add(ay); }
            if (ina != inb) { float t = (v - ca) / (cb - ca); out.add(ax + (bx - ax) * t); out.add(ay + (by - ay) * t); }
        }
        return out.size() < 6 ? null : toArr(out);
    }

    // ------------------------------------------------------------------ text
    /** text centred on y, size in the caller's units (Minecraft's font is 9 high), with an outline */
    static void text(String s, float x, float y, float size, int col, int align, int outline) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        float k = size / 9F;
        float w = fr.getStringWidth(s) * k;
        float x0 = align == 0 ? x - w / 2 : align > 0 ? x - w : x;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x0, y - 4 * k, 0);
        GlStateManager.scale(k, k, 1);
        if (outline != 0) for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) fr.drawString(s, o[0], o[1], outline & 0xFFFFFF | 0xFF000000);
        fr.drawString(s, 0, 0, col | 0xFF000000);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ internals
    static void begin() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    private static void vert(BufferBuilder b, float x, float y, int c) {
        b.pos(x, y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) { int x = (a >>> sh) & 255, y = (b >>> sh) & 255; r |= ((int) (x + (y - x) * t) & 255) << sh; }
        return r;
    }

    private static void bez(List<Float> l, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        for (int i = 0; i < 8; i++) {
            float t = i / 8F, u = 1 - t;
            l.add(u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3);
            l.add(u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3);
        }
    }

    private static void quad(List<Float> l, float x0, float y0, float x1, float y1, float x2, float y2) {
        for (int i = 0; i < 6; i++) { float t = i / 6F, u = 1 - t; l.add(u * u * x0 + 2 * u * t * x1 + t * t * x2); l.add(u * u * y0 + 2 * u * t * y1 + t * t * y2); }
    }

    private static float[] toArr(List<Float> l) { float[] a = new float[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }
}
