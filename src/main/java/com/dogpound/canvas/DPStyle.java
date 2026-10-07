package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

/**
 * The one place the DogPound button look lives, so the main-menu bar, the Options
 * buttons, the option toggles and the sliders all paint identically.
 *
 * TRANS PRIDE THEME. Every colour comes from the palette below, which matches the
 * rest of her machine (the 'transcendence' SDDM/plymouth theme, the Waybar and the
 * cooler-screen dashboard): trans blue #5BCEFA, trans pink #F5A9B8, white, over the
 * deep purples #16092F / #2B1745.
 *
 * Method signatures are unchanged on purpose -- every existing screen keeps its own
 * layout and click targets, it just paints prettier.
 */
public final class DPStyle {
    private DPStyle() {}

    // ─────────────────────────────────────────────────────────── palette
    public static int BLUE   = 0x5BCEFA;   // trans flag blue
    public static int PINK   = 0xF5A9B8;   // trans flag pink
    public static final int WHITE  = 0xFFFFFF;
    public static int ROSE   = 0xE0629B;   // her Waybar pink, for accents
    public static int DEEP   = 0x16092F;   // theme background, top
    public static int MID    = 0x2B1745;   // theme background, bottom
    public static int VIOLET = 0x3D2168;   // lifted purple for hover fills

    /** The flag, top to bottom. Used for stripe accents and the progress bars. */
    public static final int[] FLAG = { BLUE, PINK, WHITE, PINK, BLUE };

    /** How opaque the menu tiles/buttons are. 1.0 = solid, 0.0 = invisible. The wallpaper (and the
     *  dog) shows through everything below this. Borders stay stronger so cells keep their shape. */
    public static final float ICON_ALPHA = 0.35F;

    /** Rewrite an ARGB colour's alpha to a fraction of ICON_ALPHA. */
    public static int fade(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * ICON_ALPHA * mul);
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
    public static int fade(int argb) { return fade(argb, 1.0F); }

    /** Pack an alpha 0-255 onto one of the RGB palette constants. */
    public static int a(int rgb, int alpha) {
        if (alpha < 0) alpha = 0; if (alpha > 255) alpha = 255;
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int lerpChannel(int c1, int c2, float t, int shift) {
        int v1 = (c1 >> shift) & 0xFF, v2 = (c2 >> shift) & 0xFF;
        return (int) (v1 + (v2 - v1) * t) & 0xFF;
    }

    /** Blend two RGB colours. t=0 -> c1, t=1 -> c2. */
    public static int mix(int rgb1, int rgb2, float t) {
        if (t < 0) t = 0; if (t > 1) t = 1;
        return (lerpChannel(rgb1, rgb2, t, 16) << 16)
             | (lerpChannel(rgb1, rgb2, t, 8) << 8)
             |  lerpChannel(rgb1, rgb2, t, 0);
    }

    // ─────────────────────────────────────────────────────── paint helpers

    /** Vertical gradient, drawn as rows so it needs no tessellator state. */
    public static void vgrad(int x, int y, int w, int h, int rgbTop, int rgbBot, int alpha) {
        if (h <= 0 || w <= 0) return;
        GlStateManager.enableBlend();
        for (int i = 0; i < h; i++) {
            float t = (h == 1) ? 0F : (float) i / (h - 1);
            Gui.drawRect(x, y + i, x + w, y + i + 1, a(mix(rgbTop, rgbBot, t), alpha));
        }
    }

    /** Horizontal gradient (used for hover sweeps: pink on one side, blue on the other). */
    public static void hgrad(int x, int y, int w, int h, int rgbLeft, int rgbRight, int alpha) {
        if (h <= 0 || w <= 0) return;
        GlStateManager.enableBlend();
        for (int i = 0; i < w; i++) {
            float t = (w == 1) ? 0F : (float) i / (w - 1);
            Gui.drawRect(x + i, y, x + i + 1, y + h, a(mix(rgbLeft, rgbRight, t), alpha));
        }
    }

    /** The five flag stripes across a bar. Horizontal bars stripe left-to-right. */
    public static void flagBar(int x, int y, int w, int h, int alpha) {
        if (w <= 0 || h <= 0) return;
        GlStateManager.enableBlend();
        for (int i = 0; i < FLAG.length; i++) {
            int x1 = x + (int) ((long) w * i / FLAG.length);
            int x2 = x + (int) ((long) w * (i + 1) / FLAG.length);
            if (x2 > x1) Gui.drawRect(x1, y, x2, y + h, a(FLAG[i], alpha));
        }
    }

    /** A vertical flag stripe (for the left edge of a button). */
    public static void flagEdge(int x, int y, int w, int h, int alpha) {
        if (w <= 0 || h <= 0) return;
        GlStateManager.enableBlend();
        for (int i = 0; i < FLAG.length; i++) {
            int y1 = y + (int) ((long) h * i / FLAG.length);
            int y2 = y + (int) ((long) h * (i + 1) / FLAG.length);
            if (y2 > y1) Gui.drawRect(x, y1, x + w, y2, a(FLAG[i], alpha));
        }
    }

    /** Knock the 4 corner pixels back out so a square cell reads as softly rounded. */
    private static void softCorners(int x, int y, int w, int h) {
        // nothing is drawn here; callers draw borders inset by 1px at the corners instead
    }

    /** A soft outer glow, strongest against the edge, for hovered elements. */
    public static void glow(int x, int y, int w, int h, int rgb, int alpha, int rings) {
        GlStateManager.enableBlend();
        for (int i = 1; i <= rings; i++) {
            int aa = alpha / (i + 1);
            Gui.drawRect(x - i, y - i, x + w + i, y - i + 1, a(rgb, aa));          // top
            Gui.drawRect(x - i, y + h + i - 1, x + w + i, y + h + i, a(rgb, aa));  // bottom
            Gui.drawRect(x - i, y - i, x - i + 1, y + h + i, a(rgb, aa));          // left
            Gui.drawRect(x + w + i - 1, y - i, x + w + i, y + h + i, a(rgb, aa));  // right
        }
    }

    /** Border with blue along the top/left and pink along the bottom/right, corners left open
     *  by 1px so the cell looks rounded rather than boxed. */
    /** Public wrapper so screens can frame their own panels in the pride border. */
    public static void prideFrame(int x, int y, int w, int h, int alpha) {
        prideBorder(x, y, w, h, alpha, false);
    }

    private static void prideBorder(int x, int y, int w, int h, int alpha, boolean hovered) {
        int x2 = x + w, y2 = y + h;
        int top = hovered ? WHITE : BLUE;
        int bot = hovered ? WHITE : PINK;
        GlStateManager.enableBlend();
        Gui.drawRect(x + 1, y, x2 - 1, y + 1, a(top, alpha));        // top
        Gui.drawRect(x, y + 1, x + 1, y2 - 1, a(top, alpha));        // left
        Gui.drawRect(x + 1, y2 - 1, x2 - 1, y2, a(bot, alpha));      // bottom
        Gui.drawRect(x2 - 1, y + 1, x2, y2 - 1, a(bot, alpha));      // right
    }

    // ────────────────────────────────────────────────────────── the widgets

    /** An inventory-slot-shaped cell, trans themed: deep-purple gradient interior with a
     *  blue/pink pride border. Hovered cells sweep pink -> blue and pick up a soft glow. */
    public static void slot(int x, int y, int w, int h, boolean hovered, boolean enabled) {
        GlStateManager.enableBlend();
        int base = (int) (255 * ICON_ALPHA);
        if (!enabled) {
            vgrad(x, y, w, h, 0x241B33, 0x241B33, base);
            prideBorder(x, y, w, h, 70, false);
            return;
        }
        if (hovered) {
            hgrad(x, y, w, h, PINK, BLUE, (int) (base * 2.1F));
            vgrad(x, y, w, h / 2, WHITE, WHITE, 26);          // top sheen
            glow(x, y, w, h, WHITE, 90, 2);
            prideBorder(x, y, w, h, 235, true);
        } else {
            vgrad(x, y, w, h, MID, DEEP, (int) (base * 1.45F));   // glassier: wallpaper reads through
            vgrad(x, y, w, 1 + h / 8, BLUE, MID, 40);         // faint blue top light
            prideBorder(x, y, w, h, 170, false);
        }
        softCorners(x, y, w, h);
    }

    /** The plain (non-tile) button: translucent deep purple with a trans-flag stripe down the
     *  left edge, so even the unstyled buttons read as part of the pride theme. */
    public static void slotPlain(int x, int y, int w, int h, boolean hovered, boolean enabled) {
        GlStateManager.enableBlend();
        if (!enabled) {
            vgrad(x, y, w, h, 0x1A1026, 0x140C1F, 130);
            prideBorder(x, y, w, h, 60, false);
            flagEdge(x, y, 2, h, 70);
            return;
        }
        if (hovered) {
            hgrad(x, y, w, h, VIOLET, ROSE, 170);
            glow(x, y, w, h, PINK, 110, 2);
            prideBorder(x, y, w, h, 255, true);
        } else {
            vgrad(x, y, w, h, MID, DEEP, 150);
            prideBorder(x, y, w, h, 190, false);
        }
        flagEdge(x, y, 2, h, hovered ? 255 : 200);           // the pride stripe
        flagBar(x + 3, y + h - 2, w - 4, 1, hovered ? 200 : 120);  // thin underline
    }

    /** Hover tint over a grid cell the grid already painted. Pink/blue sweep instead of green. */
    public static void slotHover(int x, int y, int w, int h) {
        GlStateManager.enableBlend();
        hgrad(x + 1, y + 1, w - 2, h - 2, PINK, BLUE, 105);
        glow(x, y, w, h, WHITE, 70, 2);
    }

    /** A see-through panel with a full flag stripe along its top edge. */
    /** A frosted pride tray for a GROUP of slots to sit on.
     *  Deliberately faint: the moving wallpaper is the star, this only separates the
     *  buttons from it enough to read. Flag stripes top and bottom do the decorating. */
    public static void tray(int x, int y, int w, int h) {
        GlStateManager.enableBlend();
        glow(x, y, w, h, PINK, 70, 3);                  // soft pink bloom into the wallpaper
        vgrad(x, y, w, h, MID, DEEP, 96);               // faint frosted slab
        vgrad(x, y, w, 2 + h / 10, WHITE, MID, 16);     // top sheen
        flagBar(x + 2, y, w - 4, 2, 235);               // pride stripe along the top
        flagBar(x + 2, y + h - 2, w - 4, 2, 200);       //   ... and the bottom
        prideBorder(x, y, w, h, 210, false);
        softCorners(x, y, w, h);
    }

    public static void panel(int x, int y, int w, int h, boolean hovered, boolean enabled) {
        GlStateManager.enableBlend();
        if (!enabled) {
            vgrad(x, y, w, h, 0x140C1F, 0x0E0817, 120);
            prideBorder(x, y, w, h, 60, false);
            return;
        }
        if (hovered) {
            vgrad(x, y, w, h, VIOLET, MID, 190);
            glow(x, y, w, h, PINK, 95, 2);
        } else {
            vgrad(x, y, w, h, MID, DEEP, 155);
        }
        prideBorder(x, y, w, h, hovered ? 250 : 185, hovered);
        flagBar(x + 1, y + 1, w - 2, 2, hovered ? 255 : 190);   // flag along the top
    }

    /** Draw the button text WRAPPED into stacked lines that fit inside the slot — e.g.
     *  "Skin Customization" becomes "Skin" over "Customization" — never runs off the edge. */
    public static void label(Minecraft mc, int x, int y, int w, int h, boolean hovered, boolean enabled, String text) {
        if (text == null || text.isEmpty()) return;
        int color = !enabled ? 0xFFB9A8C8 : (hovered ? 0xFFFFFFFF : 0xFFF2E9FF);
        FontRenderer fr = mc.fontRenderer;
        int maxW = w - 4;

        // wrap on spaces into lines that each fit the slot width
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (fr.getStringWidth(trial) <= maxW || cur.length() == 0) {
                cur.setLength(0); cur.append(trial);
            } else {
                lines.add(cur.toString()); cur.setLength(0); cur.append(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        // if a single long word still overflows, scale everything down to fit
        int widest = 0;
        for (String l : lines) widest = Math.max(widest, fr.getStringWidth(l));
        float scale = (widest > maxW) ? Math.max(0.5F, (float) maxW / widest) : 1.0F;

        int lineH = (int) Math.ceil(9 * scale);
        float startY = y + (h - lines.size() * lineH) / 2.0F;
        // Soft shade behind the words: deep purple rather than black, so it melts into the
        // theme instead of boxing the text. The 1px outline does the legibility work.
        int top = (int) startY - 2, bot = (int) (startY + lines.size() * lineH + 1);
        int lx = x + 1, rx = x + w - 1;
        Gui.drawRect(lx, top,     rx, top + 1, a(DEEP, 40));    // feather
        Gui.drawRect(lx, top + 1, rx, top + 2, a(DEEP, 110));
        Gui.drawRect(lx, top + 2, rx, bot - 2, a(DEEP, 185));   // core, under the glyphs
        Gui.drawRect(lx, bot - 2, rx, bot - 1, a(DEEP, 110));
        Gui.drawRect(lx, bot - 1, rx, bot,     a(DEEP, 40));    // feather
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            float lw = fr.getStringWidth(l) * scale;
            float tx = x + (w - lw) / 2.0F;
            float ty = startY + i * lineH;
            drawOutlined(fr, l, tx, ty, scale, color);
        }
    }

    /** Light text with a hard dark 1px outline (all four sides) so it reads over any icon. */
    private static void drawOutlined(FontRenderer fr, String s,
                                     float x, float y, float scale, int color) {
        final int outline = 0xFF0B0518;   // near-black violet, matches the theme
        if (scale == 1.0F) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0) fr.drawString(s, x + dx, y + dy, outline, false);
            fr.drawString(s, x, y, color, false);
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1.0F);
            float sx = x / scale, sy = y / scale;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0) fr.drawString(s, sx + dx, sy + dy, outline, false);
            fr.drawString(s, sx, sy, color, false);
            GlStateManager.popMatrix();
        }
    }
}
