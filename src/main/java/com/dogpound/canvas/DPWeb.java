package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;

/**
 * The Pride website from inside the game (her ask 2026-09-30: "bring up the site in the escape menu… and on the
 * main menu"). With MCEF (in-game Chromium) working, the site opens right here in a Pride box with back / forward /
 * reload / home; without it, the Pride-coloured floating browser (~/bin/dpmod-browser --url … --half box) opens
 * centred over the game, and as a last resort the system browser.
 */
public class DPWeb extends GuiScreen {
    private final GuiScreen back;
    private final String home;
    private long openedAt;
    private int bx, by, bw, bh;                 // the page area
    private GuiTextField address;               // type a site or a search, Enter = go
    private int sf = 1;                         // GUI scale: the page is drawn at real screen pixels, not GUI pixels

    private DPWeb(GuiScreen back, String url) { this.back = back; this.home = url; }

    /** open the site the best way available */
    public static void open(GuiScreen back, String url) {
        if (url == null || url.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (DPMcef.available()) { mc.displayGuiScreen(new DPWeb(back, url.trim())); return; }
        DPSounds.play(DPSounds.OPEN, 1.1f, 0.7f);
        File browser = new File(System.getProperty("user.home", ""), "bin/dpmod-browser");
        try {
            if (browser.canExecute()) {
                new ProcessBuilder(browser.getAbsolutePath(), "--url", url.trim(), "--half", "box").start();
                return;
            }
        } catch (Throwable ignored) {}
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url.trim())); }
        catch (Throwable t) { try { new ProcessBuilder("xdg-open", url.trim()).start(); } catch (Exception ignored) {} }
    }

    private PrideFrame frame() { return PrideFrame.sized(width, height, 840, 480); }

    @Override
    public void initGui() {
        boolean first = openedAt == 0;
        if (first) { openedAt = DPAnim.now(); DPSounds.play(DPSounds.OPEN, 1.1f, 0.7f); }
        PrideFrame f = frame();
        buttonList.clear();
        String[] labels = {"◀", "▶", "⟳", "⌂", "↗ Browser", "✖ Close"};
        int x = f.cx;
        for (int i = 0; i < labels.length; i++) {
            int w = i < 4 ? 20 : fontRenderer.getStringWidth(labels[i]) + 14;
            if (i == 4) x = f.cx + f.cw - w - (fontRenderer.getStringWidth(labels[5]) + 14) - 4;
            DPButton b = new DPButton(i, x, f.cy, w, 16, labels[i]).plain();
            if (i == 5) b.sound(DPSounds.BACK);
            buttonList.add(b);
            x += w + 3;
            if (i == 3) {                       // the address bar fills the gap up to "↗ Browser"
                int right = f.cx + f.cw - (fontRenderer.getStringWidth(labels[4]) + 14) - (fontRenderer.getStringWidth(labels[5]) + 14) - 10;
                String keep = address == null ? home : address.getText();
                address = new GuiTextField(99, fontRenderer, x + 2, f.cy + 2, Math.max(40, right - x - 4), 12);
                address.setMaxStringLength(2048);
                address.setText(keep);
                address.setCursorPositionZero();
                x = right + 3;
            }
        }
        Keyboard.enableRepeatEvents(true);
        bx = f.cx; by = f.cy + 20; bw = f.cw; bh = f.ch - 20;
        sf = Math.max(1, new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor());
        if (first) DPMcef.open(home, bw * sf, bh * sf); else DPMcef.resize(bw * sf, bh * sf);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        float open = DPConfig.animations ? DPAnim.easeOutCubic(DPAnim.progress(openedAt, 0, 260)) : 1;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(width / 2f, height / 2f + (1 - open) * 16, 0);
        net.minecraft.client.renderer.GlStateManager.scale(0.95f + 0.05f * open, 0.95f + 0.05f * open, 1);
        net.minecraft.client.renderer.GlStateManager.translate(-width / 2f, -height / 2f, 0);
        PrideFrame f = frame();
        if (mc.world == null) DPBackground.draw(mc, width, height);
        f.drawOver("Pride Browser", home.startsWith("file:") ? "start page" : home.replaceAll("^https?://", "").replaceAll("/.*$", ""));
        drawRect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFFF5A9B8);
        if (DPMcef.ready()) {
            if (mx >= bx && my >= by && mx < bx + bw && my < by + bh) DPMcef.mouseMove((mx - bx) * sf, (my - by) * sf);
            DPMcef.draw(bx, by, bx + bw, by + bh);
        } else drawCenteredString(fontRenderer, "Loading the Pride website…", bx + bw / 2, by + bh / 2 - 4, 0xFFFFFF);
        for (GuiButton b : buttonList) b.drawButton(mc, mx, my, pt);
        if (address != null) address.drawTextBox();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        DPAnim.drawRipples();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case 0: DPMcef.goBack(); break;
            case 1: DPMcef.goForward(); break;
            case 2: DPMcef.loadURL(address != null && !address.getText().trim().isEmpty() ? toUrl(address.getText()) : home); break;
            case 3: DPMcef.loadURL(home); if (address != null) address.setText(home); break;
            case 4: DPMcef.close(); try { java.awt.Desktop.getDesktop().browse(new java.net.URI(home)); } catch (Throwable ignored) {} mc.displayGuiScreen(back); break;
            default: DPMcef.close(); mc.displayGuiScreen(back);
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (address != null) address.mouseClicked(mx, my, button);
        if (mx >= bx && my >= by && mx < bx + bw && my < by + bh) { DPMcef.mouseButton((mx - bx) * sf, (my - by) * sf, button, true); return; }
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (mx >= bx && my >= by && mx < bx + bw && my < by + bh) DPMcef.mouseButton((mx - bx) * sf, (my - by) * sf, state, false);
        super.mouseReleased(mx, my, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (mx >= bx && my >= by && mx < bx + bw && my < by + bh) DPMcef.mouseWheel((mx - bx) * sf, (my - by) * sf, d);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { DPMcef.close(); mc.displayGuiScreen(back); return; }
        if (address != null && address.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                String u = toUrl(address.getText());
                address.setText(u); address.setFocused(false);
                DPMcef.loadURL(u);
            } else address.textboxKeyTyped(c, key);
            return;
        }
        DPMcef.keyPressed(key, c);
        if (c >= 32 && c != 127) DPMcef.keyTyped(c);
        DPMcef.keyReleased(key, c);
    }

    /** the Browser button's page: the configured home, else our start page (copied once to config/pridecanvas/start.html so it can be edited) */
    public static String homeUrl() {
        if (DPConfig.browserHome != null && !DPConfig.browserHome.trim().isEmpty()) return DPConfig.browserHome.trim();
        File f = new File(Minecraft.getMinecraft().mcDataDir, "config/pridecanvas/start.html");
        if (!f.isFile()) {
            try (java.io.InputStream in = DPWeb.class.getResourceAsStream("/assets/pridecanvas/web/start.html")) {
                if (in == null) return DPConfig.websiteUrl;
                f.getParentFile().mkdirs();
                java.nio.file.Files.copy(in, f.toPath());
            } catch (Exception e) { return DPConfig.websiteUrl; }
        }
        return f.toURI().toString();
    }

    /** "pride.example", "https://…" or plain words (→ a search) */
    static String toUrl(String typed) {
        String t = typed.trim();
        if (t.isEmpty()) return t;
        if (t.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) return t;
        if (!t.contains(" ") && t.contains(".")) return "https://" + t;
        try { return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8"); } catch (Exception e) { return t; }
    }

    @Override public void updateScreen() { if (address != null) address.updateCursorCounter(); }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
