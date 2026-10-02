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
    private static final String[] QUOTES = {"trans rights are human rights", "you are loved ❤", "you are valid", "love is love",
            "you belong here", "protect trans kids", "made with love", "be proud of who you are"};

    @Override
    public void initGui() {
        this.buttonList.clear();
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
        DPCharacter.draw(this.mc, 10, 8, 3);
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
                float pop = anim ? DPAnim.easeOutBack(DPAnim.progress(openedAt, 280 + i++ * 55L, 300)) : 1;
                if (pop <= 0.01f) continue;
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                float cx = b.x + b.width / 2f, cy = b.y + b.height / 2f;
                net.minecraft.client.renderer.GlStateManager.translate(cx, cy, 0);
                net.minecraft.client.renderer.GlStateManager.scale(pop, pop, 1);
                net.minecraft.client.renderer.GlStateManager.translate(-cx, -cy, 0);
                b.drawButton(this.mc, mouseX, mouseY, partialTicks);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        } else {
            // Dim the wallpaper without hiding it, then the house-style dialog popping in.
            float p = anim ? DPAnim.easeOutBack(DPAnim.progress(popupAt, 0, 260)) : 1;
            drawRect(0, 0, this.width, this.height, ((int) (0x99 * Math.min(1f, p)) << 24) | 0x140C1F);
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
        String[] all = first.isEmpty() || first.equalsIgnoreCase("v3") ? QUOTES : prepend(first, QUOTES);
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
                this.mc.displayGuiScreen(new DPModBrowser(this));   // native page, not the old external mod
                break;
            case 7:
                openConfig();
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
