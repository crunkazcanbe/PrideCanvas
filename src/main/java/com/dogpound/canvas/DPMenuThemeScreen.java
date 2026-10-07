package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Esc menu / Options > Themes: every pack-wide menu theme as a little live mock-up of a menu (requested feature).
 * Click a card and the whole pack repaints at once, this screen included. Options on the right: logo, wallpaper,
 * HUD and loading screen follow.
 */
public class DPMenuThemeScreen extends GuiScreen {
    private final GuiScreen parent;
    private int scroll, contentH, viewTop, viewH;
    private final List<Object[]> hits = new ArrayList<>();
    private String hint = "";

    public DPMenuThemeScreen(GuiScreen parent) { this.parent = parent; }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        hint = "";
        PrideFrame f = PrideFrame.fit(width, height);
        DPMenuTheme.T cur = DPMenuTheme.current();
        f.draw(this, "Themes", "§7using: §f" + cur.name);
        int side = Math.min(170, Math.max(130, f.cw / 4)), gridW = f.cw - side - 12;
        int cols = Math.max(2, Math.min(4, gridW / 120)), gap = 6, cardW = (gridW - (cols - 1) * gap) / cols, cardH = 64;
        viewTop = f.cy + 2;
        viewH = f.ch - 30;
        int rows = (DPMenuTheme.ALL.length + cols - 1) / cols;
        contentH = rows * (cardH + gap);
        scroll = Math.max(0, Math.min(Math.max(0, contentH - viewH), scroll));
        PrideFrame.clip(f.cx, viewTop, gridW, viewH);
        for (int i = 0; i < DPMenuTheme.ALL.length; i++) {
            DPMenuTheme.T t = DPMenuTheme.ALL[i];
            int x = f.cx + (i % cols) * (cardW + gap), y = viewTop + (i / cols) * (cardH + gap) - scroll;
            if (y + cardH < viewTop || y > viewTop + viewH) continue;
            boolean on = t == cur, over = mx >= x && my >= y && mx < x + cardW && my < y + cardH && my >= viewTop && my < viewTop + viewH;
            mock(t, x, y, cardW, cardH, on, over);
            if (over) hint = t.name + ": " + t.desc;
            hits.add(new Object[]{x, Math.max(y, viewTop), cardW, Math.min(y + cardH, viewTop + viewH) - Math.max(y, viewTop), (Runnable) () -> {
                DPMenuTheme.choose(t.id);
                DPSounds.play(DPSounds.CONFIRM, 1.1F, 0.7F);
            }});
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + gridW + 2, viewTop, viewH, scroll, viewH, contentH);

        // ---- options column
        int sx = f.cx + gridW + 12, sy = viewTop;
        PrideFrame.card(sx - 4, sy - 2, side + 4, viewH + 4, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow("§lOptions", sx + 2, sy + 3, 0xFFFFFF);
        sy += 16;
        sy = chips(mx, my, sx, sy, side, "Logo", new String[]{"Pride", "Tinted", "Hidden"}, DPConfig.menuLogo,
                v -> { DPConfig.menuLogo = v; DPBootSettings.set("logo", !"Hidden".equals(v)); }, "Pride = the logo's own colours, Tinted = in this theme's colours, Hidden = no logo");
        sy = chips(mx, my, sx, sy, side, "Wallpaper", new String[]{"Animated", "Theme", "Dark"}, DPConfig.menuWallpaper,
                v -> { DPConfig.menuWallpaper = v; }, "Behind the main menu: the moving wallpaper, this theme's colours, or plain dark");
        sy = toggle(mx, my, sx, sy, side, "Pride messages", DPConfig.themePrideMessages, () -> DPMenuTheme.setPrideMessages(!DPConfig.themePrideMessages),
                "The main-menu quotes and loading tips about Pride and trans rights (off = friendly neutral ones)");
        sy = toggle(mx, my, sx, sy, side, "Character on the menu", DPConfig.themeCharacter, () -> DPConfig.themeCharacter = !DPConfig.themeCharacter,
                "The little character in the top-left corner of the main menu");
        sy = toggle(mx, my, sx, sy, side, "HUD matches", DPConfig.themeMatchHud, () -> DPConfig.themeMatchHud = !DPConfig.themeMatchHud,
                "Picking a theme also switches the HUD to its partner (" + cur.hud + ")");
        sy = toggle(mx, my, sx, sy, side, "Loading screen matches", DPConfig.themeMatchLoading, () -> DPConfig.themeMatchLoading = !DPConfig.themeMatchLoading,
                "Picking a theme also recolours the loading screen (next start)");

        int by = f.y + f.h - 24;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint.isEmpty() ? "§8Click a theme to use it. Pride is the default." : "§7" + hint, f.cw - 100), f.cx, by + 5, 0xFFFFFF);
        button(mx, my, f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, () -> mc.displayGuiScreen(parent));
        button(mx, my, f.cx + f.cw - 210, by, 124, 18, "⚙ All HUD options", PrideFrame.PINK, () -> mc.displayGuiScreen(DPHudSettingsScreen.at(this, 1)));
        super.drawScreen(mx, my, pt);
    }

    /** a tiny menu drawn in theme t: panel gradient, band, title, two tiles, a button */
    private void mock(DPMenuTheme.T t, int x, int y, int w, int h, boolean on, boolean over) {
        PrideFrame.gradient(x, y, x + w, y + h, t.top | 0xFF000000, t.bottom | 0xFF000000);
        int sw = w / t.band.length;
        for (int i = 0; i < t.band.length; i++) Gui.drawRect(x + i * sw, y, i == t.band.length - 1 ? x + w : x + (i + 1) * sw, y + 3, t.band[i]);
        fontRenderer.drawStringWithShadow((on ? "✔ " : "") + t.name, x + 5, y + 7, 0xFFFFFF);
        int tw = (w - 16) / 3;
        for (int k = 0; k < 3; k++) {
            int tx = x + 5 + k * (tw + 3), ty = y + 20;
            Gui.drawRect(tx, ty, tx + tw, ty + 18, k == 1 ? t.on : k == 2 && over ? t.hover : t.tile);
            Gui.drawRect(tx, ty, tx + tw, ty + 2, t.band[(k * 3) % t.band.length]);
        }
        Gui.drawRect(x + 5, y + 43, x + w - 5, y + 56, t.button);
        String b = "Button";
        fontRenderer.drawString(b, x + (w - fontRenderer.getStringWidth(b)) / 2, y + 46, 0xFFFFFF);
        Gui.drawRect(x + 5, y + 58, x + 5 + (w - 10) * 2 / 3, y + 60, t.accent);
        Gui.drawRect(x + 5 + (w - 10) * 2 / 3, y + 58, x + w - 5, y + 60, t.accent2);
        int edge = on ? 0xFFFFFFFF : over ? 0x80FFFFFF : 0x30FFFFFF;
        Gui.drawRect(x - 1, y - 1, x + w + 1, y, edge);
        Gui.drawRect(x - 1, y + h, x + w + 1, y + h + 1, edge);
        Gui.drawRect(x - 1, y, x, y + h, edge);
        Gui.drawRect(x + w, y, x + w + 1, y + h, edge);
    }

    private int chips(int mx, int my, int x, int y, int w, String label, String[] opts, String cur, java.util.function.Consumer<String> set, String tip) {
        fontRenderer.drawString("§7" + label, x + 2, y, 0xFFFFFF);
        y += 10;
        int cx = x + 2;
        for (String o : opts) {
            int cw = fontRenderer.getStringWidth(o) + 8;
            if (cx + cw > x + w - 4) { cx = x + 2; y += 14; }
            boolean on = o.equals(cur), over = mx >= cx && my >= y && mx < cx + cw && my < y + 12;
            Gui.drawRect(cx, y, cx + cw, y + 12, on ? PrideFrame.TILE_ON : over ? PrideFrame.TILE_HOVER : PrideFrame.TILE);
            fontRenderer.drawString(o, cx + 4, y + 2, on ? 0xFFFFFF : 0xC8C0D8);
            if (over) hint = tip;
            hits.add(new Object[]{cx, y, cw, 12, (Runnable) () -> { set.accept(o); save(); }});
            cx += cw + 3;
        }
        return y + 17;
    }

    private int toggle(int mx, int my, int x, int y, int w, String label, boolean on, Runnable r, String tip) {
        boolean over = mx >= x && my >= y && mx < x + w && my < y + 13;
        Gui.drawRect(x + 2, y + 3, x + 18, y + 11, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? x + 11 : x + 3, y + 4, on ? x + 17 : x + 9, y + 10, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, w - 26), x + 22, y + 3, on ? 0xFFFFFF : 0xA79FBF);
        if (over) hint = tip;
        hits.add(new Object[]{x, y, w, 13, (Runnable) () -> { r.run(); save(); }});
        return y + 16;
    }

    private void button(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r) {
        PrideFrame.button(x, y, w, h, label, color, mx, my);
        hits.add(new Object[]{x, y, w, h, r});
    }

    private static void save() {
        net.minecraftforge.common.config.ConfigManager.sync(DPMenuMod.MODID, net.minecraftforge.common.config.Config.Type.INSTANCE);
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) {
                ((Runnable) h[4]).run();
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
