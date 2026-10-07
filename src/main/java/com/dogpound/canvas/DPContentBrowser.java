package com.dogpound.canvas;

import com.dogpound.canvas.DPContentApi.Category;
import com.dogpound.canvas.DPContentApi.Job;
import com.dogpound.canvas.DPContentApi.Kind;
import com.dogpound.canvas.DPContentApi.Project;
import com.dogpound.canvas.DPContentApi.Version;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pride Content Browser (requested feature): Modrinth + CurseForge inside the game, in our own menus — no web page.
 * Tabs for Mods / Resource Packs / Shaders / Worlds, categories down the left, result cards with icons and one-click
 * Install (required dependencies come along), a full project page (About / Versions + changelogs / Gallery), a download
 * queue along the bottom, and "Restart to apply" when mods were added. Click areas are recorded while drawing, so what
 * you see is exactly what you can click.
 */
public class DPContentBrowser extends GuiScreen {
    private final GuiScreen parent;
    private PrideFrame f;
    private GuiTextField search;
    private String keepQuery = "";

    private static int site = DPContentApi.MODRINTH, sort = 1, kindIdx = 0;
    private static String category;                  // null = all
    private Project open;                            // the project page being shown, null = results
    private int detailTab;                           // 0 about, 1 versions, 2 gallery
    private int listScroll, catScroll, detailScroll, galleryIdx;
    private String openVersion;                      // version whose changelog is unfolded
    private long typedAt;
    private boolean typedDirty, confirmRestart;

    private static final int CARD_H = 50, GAP = 4, SIDE_W = 132, TAB_H = 18, BOTTOM_H = 24;
    private static final int MR_GREEN = 0xFF1BD96A, CF_ORANGE = 0xFFF16436, OK_GREEN = 0xFF4CB050;

    /** click areas recorded during the last draw */
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int[] listArea = new int[4], catArea = new int[4], detailArea = new int[4];

    public DPContentBrowser(GuiScreen parent) {
        this.parent = parent;
        if (DPContentApi.modDir == null) DPContentApi.modDir = findModDir();
    }

    /** the folder this jar was loaded from = the active mod group (same rule as the mod list screen) */
    private static File findModDir() {
        try {
            File self = new File(DPContentBrowser.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (self.isFile() && self.getParentFile() != null) return self.getParentFile();
        } catch (Throwable ignored) { }
        return null;
    }

    private static Kind kind() { return Kind.values()[kindIdx]; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        f = PrideFrame.fit(width, height);
        int sw = Math.min(260, f.cw - 4 * 100 - 110);
        search = new GuiTextField(1, fontRenderer, f.cx + f.cw - sw - 96, f.cy + 2, sw, 14);
        search.setMaxStringLength(80);
        search.setText(keepQuery);
        search.setFocused(true);
        if (DPContentApi.results.isEmpty() && !DPContentApi.searching) refresh();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void refresh() {
        listScroll = 0;
        DPContentApi.loadCategories(site, kind());
        DPContentApi.search(site, kind(), search == null ? keepQuery : search.getText().trim(), sort, category, false);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }

    @Override
    public void updateScreen() {
        if (search != null) search.updateCursorCounter();
        if (typedDirty && System.currentTimeMillis() - typedAt > 350) { typedDirty = false; open = null; refresh(); }
    }

    // ================================================================== drawing

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Object[]{ x, y, w, h, r }); }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        int installing = 0;
        synchronized (DPContentApi.jobs) { for (Job j : DPContentApi.jobs) if (!j.finished) installing++; }
        f.draw(this, "Add Content", null);
        drawSitePills(mx, my);
        if (installing > 0) {
            String s = "⬇ " + installing + " installing";
            fontRenderer.drawStringWithShadow(s, f.x + 130, f.y + 11, PrideFrame.BLUE);
        }
        drawKindTabs(mx, my);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) fontRenderer.drawString("Search " + kind().label.toLowerCase() + "…", search.x + 4, search.y + 3, 0xFF8A8499);
        // sort cycle button
        String sl = "⇅ " + DPContentApi.SORTS[site][sort];
        boolean sh = PrideFrame.button(f.cx + f.cw - 90, f.cy, 90, 18, sl, PrideFrame.BUTTON, mx, my);
        hit(f.cx + f.cw - 90, f.cy, 90, 18, () -> { sort = (sort + 1) % DPContentApi.SORTS[site].length; open = null; refresh(); });
        if (sh) tip("Change the order", mx, my);

        int top = f.cy + TAB_H + 8, bottom = f.y + f.h - BOTTOM_H - 6;
        drawCategories(mx, my, f.cx, top, SIDE_W, bottom - top);
        int mainX = f.cx + SIDE_W + 8, mainW = f.cx + f.cw - mainX;
        if (open == null) drawResults(mx, my, mainX, top, mainW, bottom - top);
        else drawProject(mx, my, mainX, top, mainW, bottom - top);
        drawBottomBar(mx, my, f.cx, f.y + f.h - BOTTOM_H - 2, f.cw, BOTTOM_H - 4);
        if (confirmRestart) drawRestartBox(mx, my);
        super.drawScreen(mx, my, pt);
    }

    private void drawSitePills(int mx, int my) {
        String[] names = { "Modrinth", "CurseForge" };
        int[] colors = { MR_GREEN, CF_ORANGE };
        int x = f.x + f.w - 10;
        for (int i = 1; i >= 0; i--) {
            int w = fontRenderer.getStringWidth(names[i]) + 16;
            x -= w + 4;
            boolean on = site == i, hov = in(mx, my, x, f.y + 7, w, 16);
            Gui.drawRect(x, f.y + 7, x + w, f.y + 23, on ? (colors[i] & 0x00FFFFFF) | 0xC0000000 : hov ? 0xFF2A2140 : 0xFF1C1530);
            Gui.drawRect(x, f.y + 22, x + w, f.y + 23, colors[i]);
            fontRenderer.drawStringWithShadow(names[i], x + 8, f.y + 11, on ? 0xFFFFFF : 0xC8C0DC);
            final int s = i;
            hit(x, f.y + 7, w, 16, () -> { if (site != s) { site = s; sort = s == 0 ? 1 : 0; category = null; open = null; refresh(); } });
        }
    }

    private void drawKindTabs(int mx, int my) {
        Kind[] ks = Kind.values();
        for (int i = 0; i < ks.length; i++) {
            int x = f.cx + i * 100;
            boolean avail = ks[i].on(site);
            PrideFrame.tile(x, f.cy, 96, TAB_H, PrideFrame.RAINBOW[(i * 2) % PrideFrame.RAINBOW.length], in(mx, my, x, f.cy, 96, TAB_H), kindIdx == i);
            String l = ks[i].icon + " " + ks[i].label;
            fontRenderer.drawStringWithShadow(l, x + (96 - fontRenderer.getStringWidth(l)) / 2f, f.cy + 5, avail ? 0xFFFFFF : 0x7A7490);
            final int k = i;
            hit(x, f.cy, 96, TAB_H, () -> { if (kindIdx != k) { kindIdx = k; category = null; open = null; refresh(); } });
        }
    }

    private void drawCategories(int mx, int my, int x, int y, int w, int h) {
        PrideFrame.card(x, y, w, h, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow("§lCategories", x + 6, y + 5, PrideFrame.PINK);
        int ly = y + 18, lh = h - 20, row = 13;
        catArea = new int[]{ x, ly, w, lh };
        List<Category> cats = DPContentApi.categories;
        int total = (cats.size() + 1) * row;
        catScroll = Math.max(0, Math.min(catScroll, Math.max(0, total - lh)));
        PrideFrame.clip(x, ly, w, lh);
        for (int i = -1; i < cats.size(); i++) {
            int ry = ly + (i + 1) * row - catScroll;
            if (ry + row < ly || ry > ly + lh) continue;
            String id = i < 0 ? null : cats.get(i).id, name = i < 0 ? "★ Everything" : cats.get(i).name;
            boolean on = id == null ? category == null : id.equals(category), hov = in(mx, my, x, ry, w, row) && my >= ly && my < ly + lh;
            if (on) Gui.drawRect(x + 2, ry, x + w - 4, ry + row, 0x806A3FA0);
            else if (hov) Gui.drawRect(x + 2, ry, x + w - 4, ry + row, 0x30FFFFFF);
            if (on) Gui.drawRect(x + 2, ry, x + 4, ry + row, PrideFrame.PINK);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(name, w - 14), x + 8, ry + 3, on ? 0xFFFFFF : 0xC8C0DC);
            if (ry >= ly && ry + row <= ly + lh) hit(x, ry, w, row, () -> { category = id; open = null; refresh(); });
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 4, ly, lh, catScroll, lh, total);
    }

    private void drawResults(int mx, int my, int x, int y, int w, int h) {
        listArea = new int[]{ x, y, w, h };
        List<Project> res = DPContentApi.results;
        int total = res.size() * (CARD_H + GAP) + (DPContentApi.searching || DPContentApi.more ? 30 : 0);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, total - h)));
        if (res.isEmpty()) {
            String msg = DPContentApi.searching ? "Looking… " + spinner() : DPContentApi.searchError != null ? DPContentApi.searchError : "";
            drawCenteredString(fontRenderer, msg, x + w / 2, y + h / 2 - 4, 0xC8C0DC);
            return;
        }
        PrideFrame.clip(x, y, w, h);
        for (int i = 0; i < res.size(); i++) {
            int cy = y + i * (CARD_H + GAP) - listScroll;
            if (cy + CARD_H < y || cy > y + h) continue;
            drawCard(mx, my, res.get(i), x, cy, w - 6, y, h);
        }
        int endY = y + res.size() * (CARD_H + GAP) - listScroll;
        if (DPContentApi.searching) drawCenteredString(fontRenderer, "Loading more… " + spinner(), x + w / 2, endY + 10, 0xC8C0DC);
        else if (DPContentApi.more && endY < y + h + 40) DPContentApi.search(site, kind(), search.getText().trim(), sort, category, true);   // reached the end: next page
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, y, h, listScroll, h, total);
    }

    private void drawCard(int mx, int my, Project p, int x, int y, int w, int clipTop, int clipH) {
        boolean visible = my >= clipTop && my < clipTop + clipH;
        boolean hov = visible && in(mx, my, x, y, w, CARD_H);
        Gui.drawRect(x, y, x + w, y + CARD_H, hov ? 0xE02A2140 : 0xC81C1530);
        Gui.drawRect(x, y, x + 2, y + CARD_H, site == 0 ? MR_GREEN : CF_ORANGE);
        drawIcon(p, x + 6, y + 6, 38);
        int tx = x + 50;
        fontRenderer.drawStringWithShadow("§l" + p.title, tx, y + 5, 0xFFFFFF);
        int tw = fontRenderer.getStringWidth("§l" + p.title);
        if (p.author != null) fontRenderer.drawStringWithShadow("by " + p.author, tx + tw + 6, y + 5, 0x8A8499);
        int btnW = 84, textW = w - 50 - btnW - 14;
        List<String> d = fontRenderer.listFormattedStringToWidth(p.desc == null ? "" : p.desc, textW);
        for (int i = 0; i < Math.min(2, d.size()); i++) fontRenderer.drawString(d.get(i) + (i == 1 && d.size() > 2 ? "…" : ""), tx, y + 17 + i * 9, 0xC8C0DC);
        String stats = "§b⬇ " + DPContentApi.count(p.downloads) + (p.follows > 0 ? "  §d♥ " + DPContentApi.count(p.follows) : "") + (p.updated == null || p.updated.isEmpty() ? "" : "  §7⟳ " + p.updated);
        small(stats, tx, y + 39, 0xFFFFFF);
        int cx = tx + fontRenderer.getStringWidth(stats) / 2 + 8;
        for (String c : p.categories) {
            int cw = fontRenderer.getStringWidth(c) / 2 + 6;
            if (cx + cw > x + w - btnW - 10) break;
            Gui.drawRect(cx, y + 38, cx + cw, y + 45, 0x506A3FA0);
            small(c, cx + 3, y + 39, 0xE0D0FF);
            cx += cw + 3;
        }
        drawInstallButton(mx, my, p, null, x + w - btnW - 6, y + (CARD_H - 18) / 2, btnW, 18, visible);
        if (hov) hit(x, y, w - btnW - 10, CARD_H, () -> { open = p; detailTab = 0; detailScroll = 0; galleryIdx = 0; openVersion = null; DPContentApi.details(p); });
    }

    /** Install / Installing 45% / Installed ✓ / Retry, for a project (version null = newest) */
    private void drawInstallButton(int mx, int my, Project p, Version v, int x, int y, int w, int h, boolean clickable) {
        Job job = null;
        synchronized (DPContentApi.jobs) { for (Job j : DPContentApi.jobs) if (j.project.key().equals(p.key())) job = j; }
        if (job != null && !job.finished) {
            Gui.drawRect(x, y, x + w, y + h, 0xFF1C1530);
            Gui.drawRect(x, y, x + (int) (w * job.progress()), y + h, 0xFF6A3FA0);
            String s = job.total > 0 ? (int) (job.progress() * 100) + "%" : job.state;
            fontRenderer.drawStringWithShadow(s, x + (w - fontRenderer.getStringWidth(s)) / 2f, y + (h - 8) / 2f, 0xFFFFFF);
            return;
        }
        boolean have = v == null && DPContentApi.installed(p);
        if (have) {
            Gui.drawRect(x, y, x + w, y + h, 0xFF1E3A1E);
            Gui.drawRect(x, y + h - 1, x + w, y + h, OK_GREEN);
            String s = "✔ Installed";
            fontRenderer.drawStringWithShadow(s, x + (w - fontRenderer.getStringWidth(s)) / 2f, y + (h - 8) / 2f, 0x8CE06A);
            return;
        }
        boolean failed = job != null && job.failed;
        PrideFrame.button(x, y, w, h, failed ? "↻ Retry" : "⬇ Install", failed ? 0xFF7A2A30 : 0xFFB0447A, mx, my);
        if (failed && in(mx, my, x, y, w, h)) tip(job.state, mx, my);
        if (clickable) hit(x, y, w, h, () -> DPContentApi.install(p, v));
    }

    private void drawIcon(Project p, int x, int y, int s) {
        DPNetImage.Img img = DPNetImage.get(p.iconUrl);
        if (img != null) {
            GlStateManager.enableBlend();
            GlStateManager.color(1F, 1F, 1F, 1F);
            mc.getTextureManager().bindTexture(img.loc);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, s, s, s, s);
            return;
        }
        int c = PrideFrame.RAINBOW[(p.title == null ? 0 : p.title.hashCode() & 0x7FFFFFFF) % PrideFrame.RAINBOW.length];
        PrideFrame.gradient(x, y, x + s, y + s, c, 0xFF140E22);
        String l = p.title == null || p.title.isEmpty() ? "?" : p.title.substring(0, 1).toUpperCase();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + s / 2f, y + s / 2f - 6, 0);
        GlStateManager.scale(1.6F, 1.6F, 1F);
        fontRenderer.drawStringWithShadow(l, -fontRenderer.getStringWidth(l) / 2f, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ project page

    private void drawProject(int mx, int my, int x, int y, int w, int h) {
        Project p = open;
        PrideFrame.card(x, y, w, h, site == 0 ? MR_GREEN : CF_ORANGE);
        PrideFrame.button(x + 6, y + 6, 54, 16, "◀ Back", PrideFrame.BUTTON, mx, my);
        hit(x + 6, y + 6, 54, 16, () -> open = null);
        drawIcon(p, x + 66, y + 6, 56);
        int tx = x + 130;
        GlStateManager.pushMatrix();
        GlStateManager.translate(tx, y + 8, 0);
        GlStateManager.scale(1.5F, 1.5F, 1F);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(p.title, (int) ((w - 240) / 1.5F)), 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
        fontRenderer.drawStringWithShadow((p.author != null ? "by " + p.author + "   " : "") + "§b⬇ " + DPContentApi.count(p.downloads)
                + (p.follows > 0 ? "   §d♥ " + DPContentApi.count(p.follows) : "") + (p.license != null ? "   §7" + p.license : ""), tx, y + 24, 0xC8C0DC);
        List<String> d = fontRenderer.listFormattedStringToWidth(p.desc == null ? "" : p.desc, w - 240);
        for (int i = 0; i < Math.min(2, d.size()); i++) fontRenderer.drawString(d.get(i), tx, y + 36 + i * 9, 0xA79FBF);
        drawInstallButton(mx, my, p, null, x + w - 106, y + 8, 100, 20, true);
        String where = p.kind == Kind.MODS ? "→ your mods folder (restart after)" : p.kind == Kind.RESOURCEPACKS ? "→ resource packs" : p.kind == Kind.SHADERS ? "→ shader packs" : "→ a new world in your saves";
        small("§7" + where, x + w - 106, y + 32, 0xFFFFFF);

        // tabs
        String[] tabs = { "About", "Versions" + (p.detailsLoaded ? " (" + p.versions.size() + ")" : ""), "Gallery" + (p.gallery.isEmpty() ? "" : " (" + p.gallery.size() + ")") };
        int ty = y + 66;
        for (int i = 0; i < tabs.length; i++) {
            int bx = x + 6 + i * 104;
            PrideFrame.tile(bx, ty, 100, 16, PrideFrame.RAINBOW[(i * 3 + 1) % PrideFrame.RAINBOW.length], in(mx, my, bx, ty, 100, 16), detailTab == i);
            fontRenderer.drawStringWithShadow(tabs[i], bx + (100 - fontRenderer.getStringWidth(tabs[i])) / 2f, ty + 4, 0xFFFFFF);
            final int t = i;
            hit(bx, ty, 100, 16, () -> { detailTab = t; detailScroll = 0; });
        }
        int by = ty + 22, bh = y + h - by - 6;
        detailArea = new int[]{ x + 6, by, w - 12, bh };
        if (!p.detailsLoaded) {
            drawCenteredString(fontRenderer, p.detailsError != null ? "Couldn't load: " + p.detailsError : "Loading " + spinner(), x + w / 2, by + bh / 2, 0xC8C0DC);
            return;
        }
        if (detailTab == 0) drawAbout(p, x + 8, by, w - 16, bh);
        else if (detailTab == 1) drawVersions(mx, my, p, x + 6, by, w - 12, bh);
        else drawGallery(mx, my, p, x + 6, by, w - 12, bh);
    }

    private void drawAbout(Project p, int x, int y, int w, int h) {
        List<String> lines = new ArrayList<String>(fontRenderer.listFormattedStringToWidth(p.body == null || p.body.isEmpty() ? p.desc : p.body, w - 8));
        if (!p.links.isEmpty()) {
            lines.add("");
            lines.add("§d§lLinks");
            synchronized (p.links) { for (java.util.Map.Entry<String, String> e : p.links.entrySet()) lines.add("  §7" + e.getKey() + ": §b" + e.getValue()); }
        }
        int total = lines.size() * 10;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, total - h)));
        PrideFrame.clip(x, y, w, h);
        for (int i = 0; i < lines.size(); i++) {
            int ly = y + i * 10 - detailScroll;
            if (ly + 10 < y || ly > y + h) continue;
            fontRenderer.drawStringWithShadow(lines.get(i), x, ly, 0xE6E0F0);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, y, h, detailScroll, h, total);
    }

    private void drawVersions(int mx, int my, Project p, int x, int y, int w, int h) {
        List<Version> vs;
        synchronized (p.versions) { vs = new ArrayList<Version>(p.versions); }
        if (vs.isEmpty()) { drawCenteredString(fontRenderer, "No 1.12.2 files published.", x + w / 2, y + 20, 0xC8C0DC); return; }
        int row = 22, total = 0;
        List<int[]> layout = new ArrayList<int[]>();
        List<List<String>> logs = new ArrayList<List<String>>();
        for (Version v : vs) {
            List<String> log = v.id != null && v.id.equals(openVersion) ? fontRenderer.listFormattedStringToWidth(v.changelog == null || v.changelog.isEmpty() ? "§7No changelog." : v.changelog, w - 30) : null;
            logs.add(log);
            int hgt = row + (log == null ? 0 : log.size() * 9 + 6);
            layout.add(new int[]{ total, hgt });
            total += hgt + 2;
        }
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, total - h)));
        PrideFrame.clip(x, y, w, h);
        for (int i = 0; i < vs.size(); i++) {
            Version v = vs.get(i);
            int ry = y + layout.get(i)[0] - detailScroll, rh = layout.get(i)[1];
            if (ry + rh < y || ry > y + h) continue;
            boolean hov = in(mx, my, x, ry, w, row) && my >= y && my < y + h;
            Gui.drawRect(x, ry, x + w - 6, ry + rh, hov ? 0xE02A2140 : 0xC81C1530);
            int tc = "release".equals(v.type) ? OK_GREEN : "beta".equals(v.type) ? 0xFF4A90E2 : 0xFFE0A030;
            Gui.drawRect(x, ry, x + 2, ry + rh, tc);
            String badge = v.type == null ? "" : v.type.substring(0, 1).toUpperCase() + v.type.substring(1);
            small(badge, x + 6, ry + 4, tc);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth((logs.get(i) == null ? "▸ " : "▾ ") + nz(v.name), w - 210), x + 6, ry + 10, 0xFFFFFF);
            small("§7" + nz(v.date) + "  " + DPContentApi.size(v.size) + (v.downloads > 0 ? "  ⬇ " + DPContentApi.count(v.downloads) : "") + (v.requires.isEmpty() ? "" : "  §d+" + v.requires.size() + " needed"), x + w - 200, ry + 4, 0xFFFFFF);
            drawInstallButton(mx, my, p, v, x + w - 84, ry + 3, 74, 16, hov);
            if (hov) hit(x, ry, w - 90, row, () -> openVersion = v.id != null && v.id.equals(openVersion) ? null : v.id);
            List<String> log = logs.get(i);
            if (log != null) for (int l = 0; l < log.size(); l++) fontRenderer.drawString(log.get(l), x + 16, ry + row + 2 + l * 9, 0xC8C0DC);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, y, h, detailScroll, h, total);
    }

    private void drawGallery(int mx, int my, Project p, int x, int y, int w, int h) {
        List<String[]> g;
        synchronized (p.gallery) { g = new ArrayList<String[]>(p.gallery); }
        if (g.isEmpty()) { drawCenteredString(fontRenderer, "No pictures.", x + w / 2, y + 20, 0xC8C0DC); return; }
        galleryIdx = Math.max(0, Math.min(galleryIdx, g.size() - 1));
        int thumbs = 34, bigH = h - thumbs - 18;
        DPNetImage.Img img = DPNetImage.get(g.get(galleryIdx)[0]);
        if (img == null) drawCenteredString(fontRenderer, "Loading picture " + spinner(), x + w / 2, y + bigH / 2, 0xC8C0DC);
        else {
            float k = Math.min((w - 40) / (float) img.w, bigH / (float) img.h);
            int iw = (int) (img.w * k), ih = (int) (img.h * k), ix = x + (w - iw) / 2, iy = y + (bigH - ih) / 2;
            GlStateManager.color(1F, 1F, 1F, 1F);
            mc.getTextureManager().bindTexture(img.loc);
            Gui.drawModalRectWithCustomSizedTexture(ix, iy, 0, 0, iw, ih, iw, ih);
        }
        PrideFrame.button(x, y + bigH / 2 - 10, 16, 20, "◀", PrideFrame.BUTTON, mx, my);
        hit(x, y + bigH / 2 - 10, 16, 20, () -> galleryIdx = (galleryIdx - 1 + g.size()) % g.size());
        PrideFrame.button(x + w - 16, y + bigH / 2 - 10, 16, 20, "▶", PrideFrame.BUTTON, mx, my);
        hit(x + w - 16, y + bigH / 2 - 10, 16, 20, () -> galleryIdx = (galleryIdx + 1) % g.size());
        String title = g.get(galleryIdx)[1];
        drawCenteredString(fontRenderer, (galleryIdx + 1) + " / " + g.size() + (title.isEmpty() ? "" : "  ·  " + title), x + w / 2, y + bigH + 3, 0xC8C0DC);
        int tw = 48, tx0 = x + (w - Math.min(g.size(), (w / (tw + 4))) * (tw + 4)) / 2;
        for (int i = 0; i < g.size() && i < w / (tw + 4); i++) {
            int tx = tx0 + i * (tw + 4), ty = y + h - thumbs;
            DPNetImage.Img t = DPNetImage.get(g.get(i)[0]);
            Gui.drawRect(tx - 1, ty - 1, tx + tw + 1, ty + thumbs - 4 + 1, i == galleryIdx ? PrideFrame.PINK : 0x40FFFFFF);
            if (t != null) { GlStateManager.color(1F, 1F, 1F, 1F); mc.getTextureManager().bindTexture(t.loc); Gui.drawModalRectWithCustomSizedTexture(tx, ty, 0, 0, tw, thumbs - 4, tw, thumbs - 4); }
            else Gui.drawRect(tx, ty, tx + tw, ty + thumbs - 4, 0xFF1C1530);
            final int gi = i;
            hit(tx, ty, tw, thumbs - 4, () -> galleryIdx = gi);
        }
    }

    // ------------------------------------------------------------------ bottom bar: queue + what to do next

    private void drawBottomBar(int mx, int my, int x, int y, int w, int h) {
        Gui.drawRect(x, y - 3, x + w, y - 2, 0x30FFFFFF);
        List<Job> js;
        synchronized (DPContentApi.jobs) { js = new ArrayList<Job>(DPContentApi.jobs); }
        int jx = x;
        for (int i = Math.max(0, js.size() - 3); i < js.size(); i++) {
            Job j = js.get(i);
            int jw = 150;
            Gui.drawRect(jx, y, jx + jw, y + h, 0xC81C1530);
            Gui.drawRect(jx, y + h - 2, jx + (int) (jw * j.progress()), y + h, j.failed ? 0xFFC0504D : j.finished ? OK_GREEN : PrideFrame.PINK);
            small("§f" + fontRenderer.trimStringToWidth(j.label, (jw - 8) * 2), jx + 4, y + 3, 0xFFFFFF);
            small((j.failed ? "§c" : j.finished ? "§a" : "§7") + fontRenderer.trimStringToWidth(j.state, (jw - 8) * 2), jx + 4, y + 10, 0xFFFFFF);
            jx += jw + 4;
        }
        int bx = x + w;
        bx -= 64;
        PrideFrame.button(bx, y, 64, h, "Done", PrideFrame.BUTTON, mx, my);
        hit(bx, y, 64, h, this::close);
        bx -= 104;
        PrideFrame.button(bx, y, 100, h, "☰ My mods", PrideFrame.BUTTON, mx, my);
        hit(bx, y, 100, h, () -> mc.displayGuiScreen(new DPModBrowser(this)));
        if (DPContentApi.newPacks > 0) {
            bx -= 124;
            PrideFrame.button(bx, y, 120, h, "✿ Turn packs on", 0xFF2A5A8A, mx, my);
            hit(bx, y, 120, h, () -> { DPContentApi.newPacks = 0; mc.displayGuiScreen(new GuiScreenResourcePacks(this)); });
        }
        if (DPContentApi.needRestart > 0) {
            bx -= 144;
            float pulse = 0.75F + 0.25F * (float) Math.sin(System.currentTimeMillis() / 300.0);
            PrideFrame.button(bx, y, 140, h, "↻ Restart to apply (" + DPContentApi.needRestart + ")", 0xFF000000 | ((int) (0xB0 * pulse) << 16) | 0x2A60, mx, my);
            hit(bx, y, 140, h, () -> confirmRestart = true);
        }
    }

    private void drawRestartBox(int mx, int my) {
        hits.clear();                                                       // the box is modal
        Gui.drawRect(0, 0, width, height, 0xA0000000);
        PrideFrame b = PrideFrame.sized(width, height, 360, 120);
        b.drawOver("Restart to use the new mods?", null);
        drawCenteredString(fontRenderer, DPContentApi.needRestart + " mod" + (DPContentApi.needRestart == 1 ? " was" : "s were") + " added. Minecraft loads mods only at start.", b.x + b.w / 2, b.cy + 10, 0xE6E0F0);
        drawCenteredString(fontRenderer, "§7The game closes and opens again by itself.", b.x + b.w / 2, b.cy + 24, 0xFFFFFF);
        int bw = 120, by = b.y + b.h - 30;
        PrideFrame.button(b.x + b.w / 2 - bw - 4, by, bw, 20, "↻ Restart now", 0xFFB0447A, mx, my);
        hit(b.x + b.w / 2 - bw - 4, by, bw, 20, DPModBrowser::restartGame);
        PrideFrame.button(b.x + b.w / 2 + 4, by, bw, 20, "Later", PrideFrame.BUTTON, mx, my);
        hit(b.x + b.w / 2 + 4, by, bw, 20, () -> confirmRestart = false);
    }

    // ================================================================== input

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (!confirmRestart) search.mouseClicked(mx, my, button);
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
        if (key == Keyboard.KEY_ESCAPE) {
            if (confirmRestart) { confirmRestart = false; return; }
            if (open != null) { open = null; return; }
            close();
            return;
        }
        if (search.textboxKeyTyped(c, key)) { typedAt = System.currentTimeMillis(); typedDirty = true; keepQuery = search.getText(); return; }
        if (key == Keyboard.KEY_RETURN) { typedDirty = false; open = null; refresh(); }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0 || confirmRestart) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int step = Integer.signum(d) * 30;
        if (in(mx, my, catArea[0], catArea[1], catArea[2], catArea[3])) catScroll -= step / 2;
        else if (open != null) {
            if (detailTab == 2) galleryIdx += d > 0 ? -1 : 1;
            else detailScroll -= step;
        } else listScroll -= step;
    }

    private void close() {
        keepQuery = search.getText();
        mc.displayGuiScreen(parent);
    }

    // ================================================================== bits

    private void small(String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        fontRenderer.drawStringWithShadow(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void tip(String s, int mx, int my) {
        int w = fontRenderer.getStringWidth(s) + 8;
        int x = Math.min(mx + 8, width - w - 2), y = my - 14;
        Gui.drawRect(x, y, x + w, y + 12, 0xF0140E22);
        Gui.drawRect(x, y, x + w, y + 1, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow(s, x + 4, y + 2, 0xFFFFFF);
    }

    private static String spinner() { return "◐◓◑◒".charAt((int) (System.currentTimeMillis() / 150 % 4)) + ""; }

    private static String nz(String s) { return s == null ? "" : s; }
}
