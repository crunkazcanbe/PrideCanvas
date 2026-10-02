package com.dogpound.canvas;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * The animation engine every Pride screen shares (her ask 2026-09-30: "smooth transitions and animations for
 * everything when you click buttons… very pretty"). Frame-rate independent: values chase their targets by real
 * time, so 30 fps and 240 fps look the same.
 *
 *   DPAnim.approach(key, target, speed)   a smoothed 0..1 value per object (hover glow, lift, press)
 *   DPAnim.easeOutCubic / easeOutBack     curves for one-shot animations (open, stagger, bounce)
 *   DPAnim.ripple(x, y, …)                a rainbow ring spreading from a click
 *   DPAnim.sparkles(w, h)                 hearts/stars drifting up the screen
 */
public final class DPAnim {
    private DPAnim() {}

    public static long now() { return System.nanoTime() / 1_000_000L; }

    // ------------------------------------------------------------------ curves
    public static float clamp01(float t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
    public static float easeOutCubic(float t) { t = clamp01(t); float u = 1 - t; return 1 - u * u * u; }
    public static float easeInCubic(float t) { t = clamp01(t); return t * t * t; }
    /** overshoots a little then settles — the "pop" */
    public static float easeOutBack(float t) { t = clamp01(t); float c1 = 1.70158f, c3 = c1 + 1; return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2); }
    /** 0..1 progress of something that started at `start` and lasts `ms` (after `delay`) */
    public static float progress(long start, long delay, long ms) { return clamp01((now() - start - delay) / (float) ms); }

    // ------------------------------------------------------------------ smoothed values
    private static final Map<Object, float[]> VALUES = new WeakHashMap<>();   // key → {value, lastTime}

    /** move the key's value toward target; speed = how much of the gap closes per second (8 ≈ snappy, 4 ≈ soft) */
    public static float approach(Object key, float target, float speed) {
        float[] v = VALUES.get(key);
        long t = now();
        if (v == null) { v = new float[]{target, t}; VALUES.put(key, v); return target; }
        float dt = Math.min(0.1f, (t - v[1]) / 1000f);
        v[1] = t;
        v[0] += (target - v[0]) * (1 - (float) Math.exp(-speed * dt));
        if (Math.abs(target - v[0]) < 0.002f) v[0] = target;
        return v[0];
    }

    /** start a value somewhere other than its target (e.g. a tile that should grow in from 0) */
    public static void set(Object key, float value) { VALUES.put(key, new float[]{value, now()}); }

    // ------------------------------------------------------------------ ripples
    private static final class Ripple { float x, y, max; long start; int color; int[] clip; }
    private static final List<Ripple> RIPPLES = new ArrayList<>();

    /** a ring that spreads from (x,y) out to radius max, clipped to the clip rect {x,y,w,h} (or null) */
    public static void ripple(float x, float y, float max, int rgb, int[] clip) {
        Ripple r = new Ripple();
        r.x = x; r.y = y; r.max = max; r.start = now(); r.color = rgb; r.clip = clip;
        RIPPLES.add(r);
    }

    /** draw live ripples (call at the end of a screen's drawScreen) */
    public static void drawRipples() {
        if (RIPPLES.isEmpty()) return;
        Iterator<Ripple> it = RIPPLES.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            float t = (now() - r.start) / 420f;
            if (t >= 1) { it.remove(); continue; }
            float e = easeOutCubic(t), rad = 2 + r.max * e;
            int alpha = (int) (170 * (1 - t));
            if (r.clip != null) PrideFrame.clip(r.clip[0], r.clip[1], r.clip[2], r.clip[3]);
            disc(r.x, r.y, rad, (alpha << 24) | (r.color & 0xFFFFFF), (int) (alpha * 0.25f) << 24 | 0xFFFFFF);
            ring(r.x, r.y, rad, Math.max(1.5f, 3 * (1 - t)), (alpha << 24) | 0xFFFFFF);
            if (r.clip != null) PrideFrame.unclip();
        }
    }

    // ------------------------------------------------------------------ shapes
    public static void disc(float cx, float cy, float r, int centre, int edge) {
        begin();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        col(b.pos(cx, cy, 0), centre).endVertex();
        int seg = Math.max(16, (int) (r * 1.5f));
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            col(b.pos(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0), edge).endVertex();
        }
        Tessellator.getInstance().draw();
        end();
    }

    public static void ring(float cx, float cy, float r, float thick, int argb) {
        begin();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int seg = Math.max(20, (int) (r * 1.5f));
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg, c = Math.cos(a), s = Math.sin(a);
            col(b.pos(cx + c * r, cy + s * r, 0), argb).endVertex();
            col(b.pos(cx + c * (r - thick), cy + s * (r - thick), 0), argb).endVertex();
        }
        Tessellator.getInstance().draw();
        end();
    }

    private static BufferBuilder col(BufferBuilder b, int argb) {
        return b.color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    private static void begin() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();                           // our fans/strips wind either way — never cull them
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ sparkles
    private static final class Spark { float x, y, vy, drift, size, phase, life; int color; char glyph; }
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final char[] GLYPHS = {'✦', '❤', '★', '✧', '·'};
    private static long lastSpark;

    /** hearts and stars drifting upward, trans/rainbow colours, fading in and out. density = sparks alive at once */
    public static void sparkles(int w, int h, int density) {
        long t = now();
        float dt = lastSpark == 0 ? 0 : Math.min(0.1f, (t - lastSpark) / 1000f);
        lastSpark = t;
        while (SPARKS.size() < density) {
            Spark s = new Spark();
            s.x = RNG.nextFloat() * w; s.y = h + RNG.nextFloat() * h * (SPARKS.isEmpty() ? 1 : 0.2f);
            s.vy = 8 + RNG.nextFloat() * 18; s.drift = (RNG.nextFloat() - 0.5f) * 10;
            s.size = 0.6f + RNG.nextFloat() * 0.9f; s.phase = RNG.nextFloat() * 6.28f;
            s.color = RNG.nextInt(3) == 0 ? PrideFrame.RAINBOW[RNG.nextInt(PrideFrame.RAINBOW.length)] : (RNG.nextBoolean() ? PrideFrame.PINK : PrideFrame.BLUE);
            s.glyph = GLYPHS[RNG.nextInt(GLYPHS.length)];
            SPARKS.add(s);
        }
        net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableBlend();
        Iterator<Spark> it = SPARKS.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            s.y -= s.vy * dt;
            s.life += dt;
            s.x += (float) Math.sin(s.life * 1.3f + s.phase) * s.drift * dt;
            if (s.y < -10 || s.x < -10 || s.x > w + 10) { it.remove(); continue; }
            float fadeTop = clamp01(s.y / (h * 0.35f)), fadeIn = clamp01(s.life / 1.2f);
            float twinkle = 0.65f + 0.35f * (float) Math.sin(s.life * 4 + s.phase);
            int a = (int) (200 * fadeTop * fadeIn * twinkle);
            if (a < 8) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(s.x, s.y, 0);
            GlStateManager.scale(s.size, s.size, 1);
            fr.drawString(String.valueOf(s.glyph), -3, -4, (a << 24) | (s.color & 0xFFFFFF), false);
            GlStateManager.popMatrix();
        }
    }

    /** forget the sparkle field (next screen starts fresh) */
    public static void resetSparkles() { SPARKS.clear(); lastSpark = 0; }
}
