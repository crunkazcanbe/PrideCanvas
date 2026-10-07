package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Esc menu > HUD Theme: every theme as a live, animated card; click one to use it (requested feature). */
public class DPHudThemeScreen extends GuiScreen {
    private final GuiScreen parent;
    private int scroll, contentH, viewTop, viewH;
    public DPHudThemeScreen(GuiScreen parent) { this.parent = parent; }
    @Override public boolean doesGuiPauseGame() { return false; }

    private int cols, cardW, cardH, gx;

    @Override
    public void drawScreen(int mx, int my, float pt) {
        PrideFrame f = PrideFrame.fit(width, height);
        String cur = DPConfig.hudTheme;
        String curName = cur;
        for (String[] t : DPHudThemes.LIST) if (t[0].equals(cur)) curName = t[1];
        f.draw(this, "HUD Theme", "§7using: §d" + curName);
        cols = Math.max(1, Math.min(4, f.cw / 210));
        int gap = 8;
        cardW = (f.cw - (cols - 1) * gap) / cols;
        cardH = (int) ((cardW - 12) * 130F / 520F) + 34;
        gx = f.cx;
        viewTop = f.cy + 2; viewH = f.ch - 30;
        int rows = (DPHudThemes.LIST.length + cols - 1) / cols;
        contentH = rows * (cardH + gap);
        scroll = Math.max(0, Math.min(Math.max(0, contentH - viewH), scroll));
        PrideFrame.clip(f.cx, viewTop, f.cw, viewH);
        long t = DPAnim.now();
        DPHudThemes.V sample = DPHudThemes.sample();
        sample.hp = 20 + 10 * (float) Math.sin(t / 1400.0);                  // a gentle live wobble so the themes move
        for (int i = 0; i < DPHudThemes.LIST.length; i++) {
            String[] th = DPHudThemes.LIST[i];
            int x = gx + (i % cols) * (cardW + gap), y = viewTop + (i / cols) * (cardH + gap) - scroll;
            if (y + cardH < viewTop || y > viewTop + viewH) continue;
            boolean on = th[0].equals(cur), over = mx >= x && my >= y && mx < x + cardW && my < y + cardH && my >= viewTop && my < viewTop + viewH;
            PrideFrame.tile(x, y, cardW, cardH, on ? PrideFrame.PINK : PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, on);
            fontRenderer.drawStringWithShadow((on ? "✔ " : "") + th[1], x + 6, y + 6, on ? 0xFFFFFF : 0xE8E0F4);
            int px = x + 6, py = y + 18, pw = cardW - 12, ph = cardH - 30;
            if (!DPHudThemes.draw(th[0], sample, px, py, pw, ph, t)) {
                Gui.drawRect(px, py, px + pw, py + ph, 0xFF1C1530);
                fontRenderer.drawStringWithShadow("♥♥♥♥♥  Minecraft's own", px + 6, py + ph / 2 - 4, 0xFF5070);
            }
            if (over) fontRenderer.drawStringWithShadow("§7" + th[2], f.cx, f.y + f.h - 22, 0xFFFFFF);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw + 3, viewTop, viewH, scroll, viewH, contentH);
        PrideFrame.button(f.cx + f.cw - 80, f.y + f.h - 26, 80, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
        PrideFrame.button(f.cx + f.cw - 210, f.y + f.h - 26, 124, 18, "⚙ All HUD options", PrideFrame.PINK, mx, my);
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        PrideFrame f = PrideFrame.fit(width, height);
        if (mx >= f.cx + f.cw - 80 && my >= f.y + f.h - 26 && mx < f.cx + f.cw && my < f.y + f.h - 8) { mc.displayGuiScreen(parent); return; }
        if (mx >= f.cx + f.cw - 210 && my >= f.y + f.h - 26 && mx < f.cx + f.cw - 86 && my < f.y + f.h - 8) { mc.displayGuiScreen(DPHudSettingsScreen.at(this, 1)); return; }
        if (my < viewTop || my >= viewTop + viewH) return;
        for (int i = 0; i < DPHudThemes.LIST.length; i++) {
            int x = gx + (i % cols) * (cardW + 8), y = viewTop + (i / cols) * (cardH + 8) - scroll;
            if (mx >= x && my >= y && mx < x + cardW && my < y + cardH) {
                DPConfig.hudTheme = DPHudThemes.LIST[i][0];
                net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE);
                DPCrosshairConfig.hudThemeChanged(DPConfig.hudTheme);       // the crosshair follows (if "Match my HUD theme" is on)
                DPSounds.play(DPSounds.CONFIRM, 1.1F, 0.7F);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w != 0) scroll += w > 0 ? -30 : 30;
    }
}
