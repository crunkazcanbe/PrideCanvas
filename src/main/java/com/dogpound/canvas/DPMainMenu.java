package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;

import java.io.IOException;
import java.net.URI;

/**
 * DogPound main menu: moving wallpaper, a Waybar-style bottom bar of translucent
 * buttons, and centered popup boxes for actions.
 */
public class DPMainMenu extends GuiScreen {

    private static final int BAR_H = 30;
    private static final int BAR_BG = 0x99200F38;
    private static final int BAR_TOP_LINE = 0xFFF5A9B8;
    private static final int DIM = 0x99140C1F;
    private static final int BOX_BG = 0xE62B1745;
    private static final int BOX_BORDER = 0xFFF5A9B8;

    private boolean quitPopup = false;
    private long openedAt, popupAt;
    private int poppedSounds;
    private static final String[] QUOTES = {"trans rights are human rights", "you are loved ❤", "you are valid", "love is love",
            "you belong here", "protect trans kids", "made with love", "be proud of who you are"};
    /** Themes > Pride messages off: friendly, neutral lines instead */
    private static final String[] NEUTRAL = {"welcome back", "happy mining", "build something amazing", "go explore", "mind the creepers",
            "made with love", "have fun out there", "adventure awaits"};

    @Override
    public void initGui() {
        this.buttonList.clear();
        DPWorldCancel.reset();                                  // a cancelled world load lands here
        if (openedAt == 0) { openedAt = DPAnim.now(); DPSounds.play(DPSounds.OPEN, 0.95f, 0.6f); }

        if (quitPopup) {
            int by = this.height / 2 + 8;
            this.addButton(new DPButton(100, this.width / 2 - 102, by, 100, 20, "Quit Game").plain().sound(DPSounds.CONFIRM));
            this.addButton(new DPButton(101, this.width / 2 + 2, by, 100, 20, "Cancel").plain().sound(DPSounds.BACK));
            return;
        }

        // House style (2026-09-28): the 8 slot buttons sit in a centred PrideFrame panel, 4 x 2,
        // under the logo -- no longer a hotbar stretched along the bottom + a column on the right.
        String[] labels = {"Singleplayer", "Multiplayer", "Mods", "Options", "Mod Browser", "Browser", "Config", "Quit"};
        int[] ids = {0, 1, 2, 3, 6, 5, 7, 4};
        PrideFrame f = frame();
        int gap = 10;
        int tile = Math.max(20, Math.min(64, Math.min((f.cw - 3 * gap) / 4, (f.ch - gap) / 2)));
        int gx = f.cx + (f.cw - (4 * tile + 3 * gap)) / 2;
        int gy = f.cy + (f.ch - (2 * tile + gap)) / 2;
        for (int i = 0; i < labels.length; i++) {
            DPButton b = new DPButton(ids[i], gx + (i % 4) * (tile + gap), gy + (i / 4) * (tile + gap), tile, tile, labels[i]);
            b.soundIndex = i;                                   // the hover chime climbs a scale across the tiles
            if (ids[i] == 4) b.sound(DPSounds.POPUP);
            this.addButton(b);
        }

        // "Music: ON/OFF" switch, bottom-right (shared with the loading screen; see DPMusicPause)
        String ml = DPMusicPause.label();
        int mw = this.fontRenderer.getStringWidth(ml) + 16;
        this.addButton(new DPButton(20, this.width - mw - 6, this.height - 26, mw, 18, ml).plain().sound(DPSounds.CONFIRM));

        // "Loaded in 4m 12s - why?" -> the boot report (loading screen roadmap #3)
        try {
            if (DPBoot.totalMs >= 0 && DPBootSettings.show("loadedin")) {
                String bl = "\u23F1 Loaded in " + DPBoot.fmt(DPBoot.totalMs) + (DPBoot.fastestYet() ? " - \u2605 fastest yet!" : " - why?");
                this.addButton(new DPButton(21, 6, this.height - 26, this.fontRenderer.getStringWidth(bl) + 16, 18, bl).plain().sound(DPSounds.CONFIRM));
            }
        } catch (Throwable t) { /* the chip is optional; the menu must always open */ }
    }

    /** The menu panel: centred, but pushed down below the brand logo when there's room. */
    private PrideFrame frame() {
        PrideFrame f = PrideFrame.sized(this.width, this.height, 360, 190);
        int logoW = Math.min((int) (this.width * 0.42F), 360);
        int logoBottom = 10 + logoW * 300 / 939;                // logo.png is 939 x 300 (see DPChrome.drawBrand)
        int y = Math.max(f.y, logoBottom + 6);
        y = Math.max(0, Math.min(y, this.height - f.h - 6));   // never off the bottom
        return f.moveTo(f.x, y);
    }

    /** Small centred house-style dialog (quit confirm). */
    private PrideFrame popup() {
        return PrideFrame.sized(this.width, this.height, 280, 104);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean anim = DPConfig.animations;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        if (anim) {                                             // parallax: the wallpaper drifts a little with the mouse
            float px = DPAnim.approach("parallaxX", (mouseX - this.width / 2f) * -0.012f, 4f);
            float py = DPAnim.approach("parallaxY", (mouseY - this.height / 2f) * -0.012f, 4f);
            net.minecraft.client.renderer.GlStateManager.translate(this.width / 2f + px, this.height / 2f + py, 0);
            net.minecraft.client.renderer.GlStateManager.scale(1.035f, 1.035f, 1);
            net.minecraft.client.renderer.GlStateManager.translate(-this.width / 2f, -this.height / 2f, 0);
        }
        DPBackground.draw(this.mc, this.width, this.height);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        if (DPConfig.sparkles) DPAnim.sparkles(this.width, this.height, 34);

        // Player character in the top-left, gently bobbing
        float bob = anim ? (float) Math.sin(DPAnim.now() / 650.0) * 1.5f : 0;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, bob, 0);
        if (DPConfig.themeCharacter) DPCharacter.draw(this.mc, 10, 8, 3);
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        // Brand logo drops in with a bounce
        float drop = anim ? DPAnim.easeOutBack(DPAnim.progress(openedAt, 0, 650)) : 1;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, (1 - drop) * -60, 0);
        DPChrome.drawBrand(this);
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        DPMemoryBar.draw(0, this.height - 2, this.width, 2);

        if (!quitPopup) {
            float rise = anim ? DPAnim.easeOutCubic(DPAnim.progress(openedAt, 150, 420)) : 1;
            PrideFrame f = frame();
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(0, (1 - rise) * 30, 0);
            f.drawOver("Main Menu", null);
            drawQuote(f);
            int i = 0;
            for (GuiButton b : this.buttonList) {                   // tiles cascade in, one after another
                int idx = i;
                float pop = anim ? DPAnim.easeOutBack(DPAnim.progress(openedAt, 280 + i++ * 55L, 300)) : 1;
                if (pop <= 0.01f) continue;
                if (anim && idx >= poppedSounds) { poppedSounds = idx + 1; DPSounds.popIn(idx); }   // each tile pops in with a note
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                float cx = b.x + b.width / 2f, cy = b.y + b.height / 2f;
                net.minecraft.client.renderer.GlStateManager.translate(cx, cy, 0);
                net.minecraft.client.renderer.GlStateManager.scale(pop, pop, 1);
                net.minecraft.client.renderer.GlStateManager.translate(-cx, -cy, 0);
                b.drawButton(this.mc, mouseX, mouseY, partialTicks);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
            // drawStartHere();   // removed 2026-10-04: she found the pointer ugly
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        } else {
            // Dim the wallpaper without hiding it, then the house-style dialog popping in.
            float p = anim ? DPAnim.easeOutBack(DPAnim.progress(popupAt, 0, 260)) : 1;
            drawRect(0, 0, this.width, this.height, ((int) (0x99 * Math.min(1f, p)) << 24) | (PrideFrame.PANEL_BOTTOM & 0xFFFFFF));
            PrideFrame pf = popup();
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(this.width / 2f, this.height / 2f, 0);
            net.minecraft.client.renderer.GlStateManager.scale(p, p, 1);
            net.minecraft.client.renderer.GlStateManager.translate(-this.width / 2f, -this.height / 2f, 0);
            pf.drawOver("Quit", null);
            drawCenteredString(this.fontRenderer, "Are you sure you want to quit?", this.width / 2, pf.cy + 2, 0xFFFFFFFF);
            drawCenteredString(this.fontRenderer, "come back soon ❤", this.width / 2, pf.cy + 14, PrideFrame.PINK);
            for (GuiButton b : this.buttonList) b.drawButton(this.mc, mouseX, mouseY, partialTicks);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
        DPAnim.drawRipples();
    }

    /** the panel's top-right: her subtitle first, then a new Pride quote every few seconds, cross-fading */
    private void drawQuote(PrideFrame f) {
        String first = DPConfig.menuSubtitle == null ? "" : DPConfig.menuSubtitle.trim();
        String[] pool = DPConfig.themePrideMessages ? QUOTES : NEUTRAL;
        String[] all = first.isEmpty() || first.equalsIgnoreCase("v3") ? pool : prepend(first, pool);
        long period = 6500, t = DPAnim.now() - openedAt;
        int i = (int) ((t / period) % all.length);
        float phase = (t % period) / (float) period;
        float a = DPConfig.animations ? Math.min(1f, Math.min(phase / 0.08f, (1 - phase) / 0.08f)) : 1;
        if (a < 0.05f) return;
        String q = all[i];
        int col = ((int) (255 * a) << 24) | 0xFFFFFF;
        this.fontRenderer.drawStringWithShadow(q, f.x + f.w - 10 - this.fontRenderer.getStringWidth(q), f.y + 11, col);
    }

    private static String[] prepend(String a, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = a;
        int k = 1;
        for (String r : rest) if (!r.equalsIgnoreCase(a)) out[k++] = r;
        return java.util.Arrays.copyOf(out, k);
    }

    /** the menu theme's colour band (Pride = the rainbow), RGB */
    private static int band(int i) { return PrideFrame.RAINBOW[Math.floorMod(i, PrideFrame.RAINBOW.length)] & 0xFFFFFF; }

    /**
     * Points new players at the server (requested feature): a rainbow border runs round the Multiplayer tile with a soft pulsing glow,
     * a dark Pride badge "\u2726 Join the Pride Server" with a twinkling sparkle, and three chevrons that light up
     * one after another, flowing into the button.
     */
    private void drawStartHere() {
        GuiButton mp = null;
        for (GuiButton b : this.buttonList) if (b.id == 1) mp = b;
        if (mp == null || !mp.visible) return;
        float t = DPAnim.now() - openedAt;
        if (t < 900) return;                                    // after the tiles have popped in
        boolean anim = DPConfig.animations;
        float in = Math.min(1F, (t - 900) / 400F);              // fade in
        long now = System.currentTimeMillis();

        // glow: two soft rings that breathe
        float pulse = anim ? (float) (0.5 + 0.5 * Math.sin(now / 380.0)) : 0.6F;
        for (int r = 3; r >= 1; r--) {
            int a = (int) (in * (18 + 30 * pulse) / r);
            outline(mp.x - r * 2, mp.y - r * 2, mp.width + r * 4, mp.height + r * 4, (a << 24) | (PrideFrame.PINK & 0xFFFFFF));
        }
        // the running rainbow border
        int phase = anim ? (int) (now / 45) : 0;
        rainbowBorder(mp.x - 1, mp.y - 1, mp.width + 2, mp.height + 2, phase, (int) (255 * in));

        // badge + chevrons on the left, or above the tile when there's no room
        String label = "\u2726 Join the Pride Server";
        int lw = this.fontRenderer.getStringWidth(label);
        int bw = lw + 14, bh = 15, chevW = 22;
        int bob = anim ? (int) Math.round(Math.sin(now / 260.0) * 2) : 0;
        boolean left = mp.x - bw - chevW - 6 >= 4;
        int bx, by;
        if (left) { bx = mp.x - bw - chevW - 4 + bob; by = mp.y + mp.height / 2 - bh / 2; }
        else      { bx = mp.x + (mp.width - bw) / 2; by = mp.y - bh - chevW + 4 + bob; }
        int alpha = (int) (230 * in);
        drawRect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, (alpha << 24) | (PrideFrame.PANEL_BOTTOM & 0xFFFFFF));
        drawGradientRect(bx, by, bx + bw, by + bh, (alpha << 24) | (PrideFrame.TILE_ON & 0xFFFFFF), (alpha << 24) | (PrideFrame.TILE & 0xFFFFFF));
        int seg = Math.max(1, bw / PrideFrame.RAINBOW.length);                // rainbow underline
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++)
            drawRect(bx + i * seg, by + bh - 1, i == PrideFrame.RAINBOW.length - 1 ? bx + bw : bx + (i + 1) * seg, by + bh, (alpha << 24) | band(i));
        float tw = anim ? (float) (0.5 + 0.5 * Math.sin(now / 160.0)) : 1F;   // sparkle twinkle
        int spark = blend((PrideFrame.PINK & 0xFFFFFF), 0xFFFFFF, tw);
        this.fontRenderer.drawStringWithShadow("\u2726", bx + 7, by + 4, ((int) (255 * in) << 24) | spark);
        this.fontRenderer.drawStringWithShadow(label.substring(1), bx + 7, by + 4, ((int) (255 * in) << 24) | 0xFFFFFF);

        // three chevrons lighting up in sequence, flowing toward the tile
        int step = anim ? (int) (now / 160) % 4 : 3;
        for (int i = 0; i < 3; i++) {
            float lit = i < step ? 1F : 0.25F;
            int col = ((int) (255 * in * lit) << 24) | band((i * 2 + 1) % PrideFrame.RAINBOW.length);
            if (left) chevron(bx + bw + 3 + i * 6, mp.y + mp.height / 2, true, col);
            else chevron(mp.x + mp.width / 2, by + bh + 2 + i * 6, false, col);
        }
    }

    private void outline(int x, int y, int w, int h, int col) {
        drawRect(x, y, x + w, y + 1, col); drawRect(x, y + h - 1, x + w, y + h, col);
        drawRect(x, y + 1, x + 1, y + h - 1, col); drawRect(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    /** a border made of short rainbow dashes that march around the rectangle */
    private void rainbowBorder(int x, int y, int w, int h, int phase, int alpha) {
        int per = 2 * (w + h), dash = 5;
        for (int d = 0; d < per; d += dash) {
            int col = (alpha << 24) | band(Math.floorMod(d / dash - phase, PrideFrame.RAINBOW.length));
            for (int k = d; k < Math.min(per, d + dash); k++) {
                int px, py;
                if (k < w) { px = x + k; py = y; }
                else if (k < w + h) { px = x + w - 1; py = y + (k - w); }
                else if (k < 2 * w + h) { px = x + w - 1 - (k - w - h); py = y + h - 1; }
                else { px = x; py = y + h - 1 - (k - 2 * w - h); }
                drawRect(px, py, px + 1, py + 1, col);
            }
        }
    }

    /** a 5px chevron: pointing right (at x,y = its tip's row centre) or down */
    private void chevron(int x, int y, boolean right, int col) {
        for (int i = 0; i < 4; i++) {
            if (right) { drawRect(x + i, y - 4 + i, x + i + 2, y - 3 + i, col); drawRect(x + i, y + 3 - i, x + i + 2, y + 4 - i, col); }
            else { drawRect(x - 4 + i, y + i, x - 3 + i, y + i + 2, col); drawRect(x + 3 - i, y + i, x + 4 - i, y + i + 2, col); }
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private void GlStateManagerPush() {
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new DPWorldSelect(this));
                break;
            case 1:
                this.mc.displayGuiScreen(new DPServerSelect(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new DPModBrowser(this));
                break;
            case 3:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case 4:
                quitPopup = true;
                popupAt = DPAnim.now();
                this.initGui();
                break;
            case 5:
                DPWeb.open(this, DPWeb.homeUrl());                             // in-game when Chromium works, else the Pride floating browser
                break;
            case 6:
                this.mc.displayGuiScreen(new DPContentBrowser(this));   // Modrinth + CurseForge in our own menus (2026-10-04)
                break;
            case 7:
                openConfig();
                break;
            case 21:
                this.mc.displayGuiScreen(new DPBootReport(this));
                break;
            case 20:
                DPMusicPause.toggle();
                button.displayString = DPMusicPause.label();
                break;
            case 100:
                this.mc.shutdown();
                break;
            case 101:
                quitPopup = false;
                this.initGui();
                break;
            default:
                break;
        }
    }

    /** Open the OneConfig mod's config GUI (same reflection the DogPound OneConfig Opener uses:
     *  cc.polyfrost.oneconfig.gui.OneConfigGui.create() shown via GuiUtils.displayScreen). */
    private void openConfig() {
        try {
            Class<?> ocGui = Class.forName("cc.polyfrost.oneconfig.gui.OneConfigGui");
            Object screen = ocGui.getMethod("create").invoke(null);
            try {
                Class<?> guiUtils = Class.forName("cc.polyfrost.oneconfig.utils.gui.GuiUtils");
                guiUtils.getMethod("displayScreen", net.minecraft.client.gui.GuiScreen.class)
                        .invoke(null, screen);
            } catch (Throwable inner) {
                this.mc.displayGuiScreen((net.minecraft.client.gui.GuiScreen) screen);
            }
        } catch (Throwable t) {
            // OneConfig not installed — ignore gracefully
        }
    }

    private void openModBrowser() {
        try {
            Class<?> cls = Class.forName("com.deckerpw.modbrowser.GuiMainMenu");
            GuiScreen gui = (GuiScreen) cls.newInstance();
            this.mc.displayGuiScreen(gui);
        } catch (Throwable t) {
            // ModBrowser not installed — do nothing
        }
    }

    private void openWeb(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url.trim()));
        } catch (Throwable t) {
            // ignore - headless / no browser
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
