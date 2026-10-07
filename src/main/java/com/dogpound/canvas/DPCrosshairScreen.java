package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pride Crosshair settings (requested feature): every situation Dynamic Crosshair knows, each with on/off, its own shape
 * from 40+ and its own colour; a big live preview; size, opacity, colour modes, shadow, the extra rings and marks, and
 * the fancy block outline. Opens from Options -> "Crosshair". Changes save as you click.
 */
public class DPCrosshairScreen extends GuiScreen {
    private final GuiScreen parent;
    private PrideFrame f;
    private String sel = "block";
    private int sitScroll, styleScroll, optScroll;
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int[] sitArea = new int[4], styleArea = new int[4], optArea = new int[4];
    private static final int[] SWATCHES = { 0, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA, 0xFFB07CFF, 0xFFFFED00, 0xFF8CE06A, 0xFFFF8C00, 0xFFFF5A64, 0xFF20E0C0, 0xFF101010 };
    private static final String[] COLOR_MODES = { "situation", "rainbow", "pride", "invert" };
    private static final String[] COLOR_MODE_NAMES = { "Per situation", "Rainbow", "Pride flag", "Invert (vanilla)" };
    private static final String[] OUTLINE_MODES = { "rainbow", "pride", "solid", "vanilla" };
    private static final String[] OUTLINE_NAMES = { "Rainbow", "Pride flag", "One colour", "Minecraft's own" };

    public DPCrosshairScreen(GuiScreen parent) { this.parent = parent; }

    private DPCrosshairConfig cfg() { return DPCrosshairConfig.get(); }

    @Override public void initGui() { f = PrideFrame.fit(width, height); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Object[]{ x, y, w, h, r }); }
    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private void changed() { cfg().save(); }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        DPCrosshairConfig c = cfg();
        f.draw(this, "Crosshair", null);
        // mode pills in the title bar
        String[] modes = { "Off", "Dynamic", "Always one" };
        int px = f.x + f.w - 10;
        for (int i = 2; i >= 0; i--) {
            int w = fontRenderer.getStringWidth(modes[i]) + 14;
            px -= w + 3;
            boolean on = c.mode == i;
            PrideFrame.tile(px, f.y + 7, w, 16, PrideFrame.RAINBOW[i * 2], in(mx, my, px, f.y + 7, w, 16), on);
            fontRenderer.drawStringWithShadow(modes[i], px + 7, f.y + 11, 0xFFFFFF);
            final int m = i;
            hit(px, f.y + 7, w, 16, () -> { c.mode = m; changed(); });
        }

        int top = f.cy, h = f.ch - 26;
        int sw = 168, ow = Math.min(270, f.cw / 3), gx = f.cx + sw + 6, gw = f.cw - sw - ow - 12, ox = f.cx + f.cw - ow;
        drawSituations(mx, my, f.cx, top, sw, h, c);
        drawStyles(mx, my, gx, top, gw, h, c);
        drawOptions(mx, my, ox, top, ow, h, c);

        // bottom
        int by = f.y + f.h - 26;
        PrideFrame.button(f.cx, by, 110, 18, "↺ Reset all", 0xFF7A2A30, mx, my);
        hit(f.cx, by, 110, 18, () -> DPCrosshairConfig.reset());
        String tip = "Click a situation, then a shape. Every change saves right away.";
        fontRenderer.drawStringWithShadow(tip, f.cx + 120, by + 5, 0xA79FBF);
        PrideFrame.button(f.cx + f.cw - 90, by, 90, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
        hit(f.cx + f.cw - 90, by, 90, 18, () -> mc.displayGuiScreen(parent));
        super.drawScreen(mx, my, pt);
    }

    // ------------------------------------------------------------------ left: situations

    private void drawSituations(int mx, int my, int x, int y, int w, int h, DPCrosshairConfig c) {
        PrideFrame.card(x, y, w, h, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow("§lWhen…", x + 6, y + 5, PrideFrame.PINK);
        int ly = y + 17, lh = h - 19, row = 22;
        sitArea = new int[]{ x, ly, w, lh };
        String[][] S = DPCrosshairConfig.SITUATIONS;
        int total = S.length * row;
        sitScroll = Math.max(0, Math.min(sitScroll, Math.max(0, total - lh)));
        PrideFrame.clip(x, ly, w, lh);
        for (int i = 0; i < S.length; i++) {
            String id = S[i][0];
            DPCrosshairConfig.Sit s = c.sit(id);
            int ry = ly + i * row - sitScroll;
            if (ry + row < ly || ry > ly + lh) continue;
            boolean on = id.equals(sel), hov = in(mx, my, x, ry, w, row) && my >= ly && my < ly + lh;
            Gui.drawRect(x + 2, ry, x + w - 4, ry + row - 1, on ? 0xC06A3FA0 : hov ? 0x402A2140 : 0x20000000);
            // mini preview
            Gui.drawRect(x + 4, ry + 2, x + 22, ry + 20, 0xFF2A3A2A);
            GlStateManager.disableTexture2D();
            if (s.show) DPCrosshairStyles.draw(s.style, x + 13, ry + 11, 1F, DPCrosshair.colorFor(c, id, s.color), c.shadow);
            GlStateManager.enableTexture2D();
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(S[i][1], w - 50), x + 26, ry + 3, s.show ? 0xFFFFFF : 0x7A7490);
            small("§7" + DPCrosshairStyles.NAMES.get(s.style), x + 26, ry + 13);
            // on/off switch
            int tx = x + w - 22;
            Gui.drawRect(tx, ry + 7, tx + 16, ry + 15, s.show ? 0xFF8CE06A : 0xFF3D2168);
            Gui.drawRect(s.show ? tx + 9 : tx + 1, ry + 8, s.show ? tx + 15 : tx + 7, ry + 14, 0xFFFFFFFF);
            if (ry >= ly && ry + row <= ly + lh) {
                hit(tx - 2, ry, 22, row, () -> { s.show = !s.show; changed(); });
                hit(x, ry, w - 26, row, () -> sel = id);
            }
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, ly, lh, sitScroll, lh, total);
    }

    // ------------------------------------------------------------------ middle: every shape

    private void drawStyles(int mx, int my, int x, int y, int w, int h, DPCrosshairConfig c) {
        PrideFrame.card(x, y, w, h, PrideFrame.BLUE);
        String label = "";
        for (String[] s : DPCrosshairConfig.SITUATIONS) if (s[0].equals(sel)) label = s[1];
        fontRenderer.drawStringWithShadow("§lShape §r§7for §f" + label, x + 6, y + 5, PrideFrame.BLUE);
        DPCrosshairConfig.Sit s = c.sit(sel);
        int ly = y + 17, lh = h - 19, tile = 38, cols = Math.max(1, (w - 8) / (tile + 4));
        styleArea = new int[]{ x, ly, w, lh };
        List<Map.Entry<String, String>> all = new ArrayList<Map.Entry<String, String>>(DPCrosshairStyles.NAMES.entrySet());
        int rows = (all.size() + cols - 1) / cols, total = rows * (tile + 14);
        styleScroll = Math.max(0, Math.min(styleScroll, Math.max(0, total - lh)));
        PrideFrame.clip(x, ly, w, lh);
        int col = DPCrosshair.colorFor(c, sel, s.color);
        for (int i = 0; i < all.size(); i++) {
            String id = all.get(i).getKey();
            int tx = x + 6 + (i % cols) * (tile + 4), ty = ly + (i / cols) * (tile + 14) - styleScroll;
            if (ty + tile + 14 < ly || ty > ly + lh) continue;
            boolean on = id.equals(s.style), hov = in(mx, my, tx, ty, tile, tile + 10) && my >= ly && my < ly + lh;
            Gui.drawRect(tx, ty, tx + tile, ty + tile, on ? 0xFF6A3FA0 : hov ? 0xFF3A2E58 : 0xFF241C38);
            if (on) { Gui.drawRect(tx, ty, tx + tile, ty + 1, PrideFrame.PINK); Gui.drawRect(tx, ty + tile - 1, tx + tile, ty + tile, PrideFrame.PINK); }
            GlStateManager.disableTexture2D();
            DPCrosshairStyles.draw(id, tx + tile / 2F, ty + tile / 2F, 2.6F, col, c.shadow);
            GlStateManager.enableTexture2D();
            small(fontRenderer.trimStringToWidth(all.get(i).getValue(), tile * 2), tx + 1, ty + tile + 2);
            if (ty >= ly && ty + tile <= ly + lh) hit(tx, ty, tile, tile + 10, () -> { s.style = id; s.show = !"none".equals(id); changed(); });
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, ly, lh, styleScroll, lh, total);
    }

    // ------------------------------------------------------------------ right: preview + options

    private int oy;                                                  // running y while laying out options
    private void drawOptions(int mx, int my, int x, int y, int w, int h, DPCrosshairConfig c) {
        PrideFrame.card(x, y, w, h, PrideFrame.RAINBOW[5]);
        // live preview: sky + grass, the chosen situation at 4x and 1x
        int pvh = 74;
        PrideFrame.gradient(x + 4, y + 4, x + w - 4, y + 4 + pvh * 2 / 3, 0xFF79A6FF, 0xFFB8D4FF);
        Gui.drawRect(x + 4, y + 4 + pvh * 2 / 3, x + w - 4, y + 4 + pvh, 0xFF5D9E3A);
        DPCrosshairConfig.Sit s = c.sit(sel);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        if (s.show) {
            DPCrosshairStyles.draw(s.style, x + w / 3F, y + 4 + pvh / 2F, 3F * c.scale * s.size, DPCrosshair.colorFor(c, sel, s.color), c.shadow);
            DPCrosshairStyles.draw(s.style, x + w * 3 / 4F, y + 4 + pvh / 2F, c.scale * s.size, DPCrosshair.colorFor(c, sel, s.color), c.shadow);
        }
        GlStateManager.enableTexture2D();
        small("§0preview: 4x and real size", x + 8, y + 6);

        int ly = y + pvh + 8, lh = h - pvh - 10;
        optArea = new int[]{ x, ly, w, lh };
        PrideFrame.clip(x, ly, w, lh);
        oy = ly - optScroll;
        // colour of this situation
        head("Colour of this situation", x + 6);
        int sx = x + 6;
        for (int i = 0; i < SWATCHES.length; i++) {
            int col = SWATCHES[i], bx = sx + i * 20;
            boolean on = s.color == col;
            Gui.drawRect(bx - 1, oy - 1, bx + 17, oy + 17, on ? 0xFFFFFFFF : 0xFF1C1530);
            if (col == 0) { PrideFrame.gradient(bx, oy, bx + 16, oy + 16, DPCrosshairConfig.defaultColor(sel), 0xFF1C1530); small("auto", bx + 1, oy + 6); }
            else Gui.drawRect(bx, oy, bx + 16, oy + 16, col);
            final int cc = col;
            hit(bx, oy + optScroll >= ly ? oy : oy, 16, 16, () -> { s.color = cc; changed(); });
        }
        oy += 22;
        head("Size of this situation  " + Math.round(s.size * 100) + "%", x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { s.size = Math.max(0.5F, Math.round((s.size - 0.25F) * 4) / 4F); changed(); }, () -> { s.size = Math.min(3F, Math.round((s.size + 0.25F) * 4) / 4F); changed(); }, s.size / 3F);
        head("Cute themes (sets every situation at once)", x + 6);
        for (String[] th : DPCrosshairConfig.THEMES) {
            final String id = th[0];
            boolean on = id.equals(c.theme);
            cycle(mx, my, x + 6, w - 12, (on ? "\u2714 " : "") + th[1], () -> { c.applyTheme(id); changed(); });
        }
        toggle(mx, my, x + 6, w - 12, "Match my HUD theme", c.followHud, () -> { c.followHud = !c.followHud; if (c.followHud) DPCrosshairConfig.hudThemeChanged(DPConfig.hudTheme); changed(); });
        head("Colour mode (all situations)", x + 6);
        cycle(mx, my, x + 6, w - 12, COLOR_MODE_NAMES[idx(COLOR_MODES, c.colorMode)], () -> { c.colorMode = COLOR_MODES[(idx(COLOR_MODES, c.colorMode) + 1) % COLOR_MODES.length]; changed(); });
        head("Size  " + Math.round(c.scale * 100) + "%", x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.scale = Math.max(0.5F, Math.round((c.scale - 0.25F) * 4) / 4F); changed(); }, () -> { c.scale = Math.min(4F, Math.round((c.scale + 0.25F) * 4) / 4F); changed(); }, c.scale / 4F);
        head("See-through  " + c.opacity + "%", x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.opacity = Math.max(20, c.opacity - 10); changed(); }, () -> { c.opacity = Math.min(100, c.opacity + 10); changed(); }, c.opacity / 100F);
        head("Extras", x + 6);
        toggle(mx, my, x + 6, w - 12, "Dark shadow behind it", c.shadow, () -> { c.shadow = !c.shadow; changed(); });
        toggle(mx, my, x + 6, w - 12, "Hide in third person", c.hideInThirdPerson, () -> { c.hideInThirdPerson = !c.hideInThirdPerson; changed(); });
        toggle(mx, my, x + 6, w - 12, "Attack charge ring", c.attackRing, () -> { c.attackRing = !c.attackRing; changed(); });
        toggle(mx, my, x + 6, w - 12, "Mining progress ring", c.miningRing, () -> { c.miningRing = !c.miningRing; changed(); });
        toggle(mx, my, x + 6, w - 12, "Right tool ✔ / can't mine ✘", c.toolMarks, () -> { c.toolMarks = !c.toolMarks; changed(); });
        toggle(mx, my, x + 6, w - 12, "Place-a-block hint", c.placeHint, () -> { c.placeHint = !c.placeHint; changed(); });
        toggle(mx, my, x + 6, w - 12, "Bow draw ring", c.chargeRing, () -> { c.chargeRing = !c.chargeRing; changed(); });
        head("Pretty extras", x + 6);
        toggle(mx, my, x + 6, w - 12, "Soft glow behind it", c.glow, () -> { c.glow = !c.glow; changed(); });
        head("Glow strength  " + c.glowStrength + "%", x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.glowStrength = Math.max(5, c.glowStrength - 10); changed(); }, () -> { c.glowStrength = Math.min(100, c.glowStrength + 10); changed(); }, c.glowStrength / 100F);
        toggle(mx, my, x + 6, w - 12, "Bounce when you hit", c.hitBounce, () -> { c.hitBounce = !c.hitBounce; changed(); });
        toggle(mx, my, x + 6, w - 12, "Gently breathes", c.breathe, () -> { c.breathe = !c.breathe; changed(); });
        head("Breathing speed  " + c.breatheSpeed, x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.breatheSpeed = Math.max(1, c.breatheSpeed - 1); changed(); }, () -> { c.breatheSpeed = Math.min(10, c.breatheSpeed + 1); changed(); }, c.breatheSpeed / 10F);
        toggle(mx, my, x + 6, w - 12, "Sparkles when your hit is ready", c.readySparkles, () -> { c.readySparkles = !c.readySparkles; changed(); });
        toggle(mx, my, x + 6, w - 12, "Hearts float up on a kill", c.killHearts, () -> { c.killHearts = !c.killHearts; changed(); });
        toggle(mx, my, x + 6, w - 12, "Name of what you look at", c.targetName, () -> { c.targetName = !c.targetName; changed(); });
        toggle(mx, my, x + 6, w - 12, "Distance to it", c.targetDistance, () -> { c.targetDistance = !c.targetDistance; changed(); });
        toggle(mx, my, x + 6, w - 12, "Dark box behind the name", c.nameShadowBox, () -> { c.nameShadowBox = !c.nameShadowBox; changed(); });
        toggle(mx, my, x + 6, w - 12, "Eating ring", c.eatRing, () -> { c.eatRing = !c.eatRing; changed(); });
        toggle(mx, my, x + 6, w - 12, "Hit marker when you land a hit", c.hitMarker, () -> { c.hitMarker = !c.hitMarker; changed(); });
        toggle(mx, my, x + 6, w - 12, "Pulse when your hit is ready", c.pulseReady, () -> { c.pulseReady = !c.pulseReady; changed(); });
        toggle(mx, my, x + 6, w - 12, "Mob health bar under it", c.mobHealth, () -> { c.mobHealth = !c.mobHealth; changed(); });
        toggle(mx, my, x + 6, w - 12, "Center dot always", c.centerDot, () -> { c.centerDot = !c.centerDot; changed(); });
        toggle(mx, my, x + 6, w - 12, "Spread out while moving", c.spreadOnMove, () -> { c.spreadOnMove = !c.spreadOnMove; changed(); });
        toggle(mx, my, x + 6, w - 12, "Hide while sprinting", c.hideSprinting, () -> { c.hideSprinting = !c.hideSprinting; changed(); });
        toggle(mx, my, x + 6, w - 12, "Hide while holding a map", c.hideWithMap, () -> { c.hideWithMap = !c.hideWithMap; changed(); });
        head("Spin  " + (c.spin == 0 ? "off" : c.spin + "\u00B0/s"), x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.spin = Math.max(-180, c.spin - 15); changed(); }, () -> { c.spin = Math.min(180, c.spin + 15); changed(); }, (c.spin + 180) / 360F);
        head("Block outline", x + 6);
        toggle(mx, my, x + 6, w - 12, "Fancy outline", c.outline, () -> { c.outline = !c.outline; changed(); });
        cycle(mx, my, x + 6, w - 12, "Colour: " + OUTLINE_NAMES[idx(OUTLINE_MODES, c.outlineColor)], () -> { c.outlineColor = OUTLINE_MODES[(idx(OUTLINE_MODES, c.outlineColor) + 1) % OUTLINE_MODES.length]; changed(); });
        if ("solid".equals(c.outlineColor)) {
            for (int i = 1; i < SWATCHES.length; i++) {
                int col = SWATCHES[i], bx = x + 6 + (i - 1) * 20;
                Gui.drawRect(bx - 1, oy - 1, bx + 17, oy + 17, c.outlineSolid == col ? 0xFFFFFFFF : 0xFF1C1530);
                Gui.drawRect(bx, oy, bx + 16, oy + 16, col);
                final int cc = col;
                hit(bx, oy, 16, 16, () -> { c.outlineSolid = cc; changed(); });
            }
            oy += 22;
        }
        head("Outline thickness  " + String.format("%.1f", c.outlineWidth), x + 6);
        stepper(mx, my, x + 6, w - 12, () -> { c.outlineWidth = Math.max(1F, c.outlineWidth - 0.5F); changed(); }, () -> { c.outlineWidth = Math.min(6F, c.outlineWidth + 0.5F); changed(); }, c.outlineWidth / 6F);
        toggle(mx, my, x + 6, w - 12, "Fill up while mining", c.outlineFill, () -> { c.outlineFill = !c.outlineFill; changed(); });
        toggle(mx, my, x + 6, w - 12, "Soft glow when idle", c.outlinePulse, () -> { c.outlinePulse = !c.outlinePulse; changed(); });
        toggle(mx, my, x + 6, w - 12, "Only while mining", c.outlineOnlyMining, () -> { c.outlineOnlyMining = !c.outlineOnlyMining; changed(); });
        int total = oy + optScroll - ly + 6;
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, ly, lh, optScroll, lh, total);
        optScroll = Math.max(0, Math.min(optScroll, Math.max(0, total - lh)));
        // only keep option clicks that are inside the visible area
        hits.removeIf(o -> o.length == 5 && (Integer) o[0] >= x && (Integer) o[0] < x + w && (Integer) o[1] > y + pvh && ((Integer) o[1] < ly || (Integer) o[1] + (Integer) o[3] > ly + lh));
    }

    private void head(String s, int x) {
        fontRenderer.drawStringWithShadow("§d§l" + s, x, oy + 2, 0xFFFFFF);
        oy += 13;
    }

    private void toggle(int mx, int my, int x, int w, String label, boolean on, Runnable r) {
        boolean hov = in(mx, my, x, oy, w, 14);
        if (hov) Gui.drawRect(x - 2, oy - 1, x + w, oy + 14, 0x30FFFFFF);
        Gui.drawRect(x, oy + 3, x + 16, oy + 11, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? x + 9 : x + 1, oy + 4, on ? x + 15 : x + 7, oy + 10, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow(label, x + 22, oy + 3, on ? 0xFFFFFF : 0xA79FBF);
        hit(x, oy, w, 14, r);
        oy += 15;
    }

    private void cycle(int mx, int my, int x, int w, String label, Runnable r) {
        PrideFrame.button(x, oy, w, 16, "◀  " + label + "  ▶", PrideFrame.BUTTON, mx, my);
        hit(x, oy, w, 16, r);
        oy += 20;
    }

    private void stepper(int mx, int my, int x, int w, Runnable minus, Runnable plus, float frac) {
        PrideFrame.button(x, oy, 18, 14, "-", PrideFrame.BUTTON, mx, my);
        hit(x, oy, 18, 14, minus);
        PrideFrame.button(x + w - 18, oy, 18, 14, "+", PrideFrame.BUTTON, mx, my);
        hit(x + w - 18, oy, 18, 14, plus);
        Gui.drawRect(x + 22, oy + 5, x + w - 22, oy + 9, 0xFF1C1530);
        Gui.drawRect(x + 22, oy + 5, x + 22 + (int) ((w - 44) * Math.max(0F, Math.min(1F, frac))), oy + 9, PrideFrame.PINK);
        oy += 18;
    }

    private static int idx(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return 0; }

    private void small(String s, float x, float y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        fontRenderer.drawStringWithShadow(s, 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ((Runnable) h[4]).run();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1, step = Integer.signum(d) * 30;
        if (in(mx, my, sitArea[0], sitArea[1], sitArea[2], sitArea[3])) sitScroll -= step;
        else if (in(mx, my, styleArea[0], styleArea[1], styleArea[2], styleArea[3])) styleScroll -= step;
        else if (in(mx, my, optArea[0], optArea[1], optArea[2], optArea[3])) optScroll -= step;
    }
}
