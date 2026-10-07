package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * DogPound Mod Browser — one screen, split down the middle.
 *   LEFT  = every mod in the active mod-group folder (the folder THIS jar loaded from):
 *           search bar, scrollable list, click a row to enable/disable it, click the X to
 *           remove it. Changes rename X.jar <-> X.jar.disabled right in the group folder.
 *   RIGHT = the download page: a Modrinth/CurseForge toggle + a search box that opens the
 *           real page in the desktop browser (a live web view isn't possible in-GUI without
 *           a native browser mod, and this stays 100% self-contained).
 * Closing with unsaved changes pops a "restart to apply" box: Restart Now / Quit / Keep editing.
 */
public class DPModBrowser extends GuiScreen {
    private static final int GREEN   = 0xFFF5A9B8;
    private static final int GREEN_LT= 0xFF8CE06A;
    private static final int GRAY    = 0xFF3D2168;
    private static final int OFFRED  = 0xFFC0504D;
    private static final int PANEL    = 0xC8101A0A;   // translucent panel behind each half
    private static final int MODRINTH = 0xFF1BD96A;
    private static final int CURSE    = 0xFFF16436;

    private final GuiScreen parent;
    private final File modDir;
    private final List<Mod> all = new ArrayList<Mod>();
    private GuiTextField leftSearch, rightSearch;
    private int scroll = 0;
    private boolean dirty = false;
    private int site = 0;            // 0 = Modrinth, 1 = CurseForge
    private boolean restartPrompt = false;

    private int midX, listTop, rowH = 22;
    /** House-style panel edges (2026-09-28): everything that used to hug the screen edges now sits inside a
     *  centred PrideFrame. L/R = panel left/right, T = top offset (old y + T), H = panel bottom (old height). */
    private int L, R, T, H;

    // right half = API search results (Prism-style). The web browser is opt-in via the Browser button.
    private int sort = 1;                    // index into DPModApi.SORT_LABEL; 1 = Downloads
    private int rScroll = 0;
    private int rRowH = 30;
    private int seenDownloads = 0;           // last DPModApi.downloadsDone we reacted to
    private boolean browserMode = false;     // true = show the live web browser instead of the list
    private int dragBar = 0;                 // 0 none, 1 dragging left bar, 2 dragging right bar

    // in-window MCEF browser (right half) — live only if mcef-legacy + its natives are present
    private boolean mcefOn;
    private int rgX1, rgY1, rgX2, rgY2;     // right-half browser rectangle
    private int lastMouseX, lastMouseY;

    private static final class Mod {
        final String id;      // filename with the trailing .disabled* stripped (always ends .jar)
        File file;            // current on-disk file
        boolean enabled;
        Mod(String id, File file, boolean enabled) { this.id = id; this.file = file; this.enabled = enabled; }
        String display() {
            String s = id.endsWith(".jar") ? id.substring(0, id.length() - 4) : id;
            return s.replace('_', ' ');
        }
    }

    public DPModBrowser(GuiScreen parent) {
        this.parent = parent;
        this.modDir = resolveModDir();
        reload();
    }

    /** The folder this jar was loaded from = the active mod-group bind-mounted over mods/. */
    private File resolveModDir() {
        try {
            File self = new File(DPModBrowser.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            File dir = self.getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        } catch (Throwable ignored) {}
        return new File(this.mc != null ? this.mc.mcDataDir : new File("."), "mods");
    }

    private void reload() {
        all.clear();
        File[] files = modDir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".jar")) {
                    all.add(new Mod(n, f, true));
                } else {
                    int i = n.indexOf(".jar.disabled");
                    if (i > 0) all.add(new Mod(n.substring(0, i + 4), f, false));
                }
            }
        }
        Collections.sort(all, new Comparator<Mod>() {
            public int compare(Mod a, Mod b) { return a.id.compareToIgnoreCase(b.id); }
        });
    }

    @Override
    public void initGui() {
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        L = f.x; R = f.x + f.w; T = f.y + 22; H = f.y + f.h;
        this.midX = (L + R) / 2;
        this.listTop = T + 58;
        leftSearch = new GuiTextField(1, this.fontRenderer, L + 12, T + 34, midX - L - 24, 16);
        rightSearch = new GuiTextField(2, this.fontRenderer, midX + 12, T + 34, R - midX - 24, 16);
        for (GuiTextField t : new GuiTextField[]{leftSearch, rightSearch}) {
            t.setEnableBackgroundDrawing(false);
            t.setMaxStringLength(64);
            t.setTextColor(0xFFFFFFFF);
        }
        this.buttonList.clear();
        int rx = midX + 12, rw = R - midX - 24, half = (rw - 4) / 2;
        // right-half controls: Site | Sort on one row, Browser underneath
        this.buttonList.add(new DPButton(10, rx, H - 64, half, 20, "Site: " + siteName()).plain());
        this.buttonList.add(new DPButton(12, rx + half + 4, H - 64, rw - half - 4, 20,
                "Sort: " + DPModApi.sortLabel(site, sort)).plain());
        this.buttonList.add(new DPButton(13, rx, H - 40, rw, 20,
                browserMode ? "< Back to Mod List" : "\u2726 Browse & Install").plain());
        // back
        this.buttonList.add(new DPButton(0, L + 12, H - 28, 120, 20, "Back").plain());
        if (restartPrompt) buildRestartButtons();

        rgX1 = midX + 8; rgY1 = T + 58; rgX2 = R - 8; rgY2 = H - 82;   // -82: the results footer gets its own line

        // browserMode is only ever true when MCEF really is embedding a page here, so the button
        // label can never lie. The floating-window fallback is fired from actionPerformed instead.
        if (browserMode) {
            mcefOn = true;
            DPMcef.open(currentUrl(), rgX2 - rgX1, rgY2 - rgY1);
        } else {
            mcefOn = false;
            DPMcef.close();
            if (DPModApi.results.isEmpty() && !DPModApi.loading) doSearch();
        }
    }

    private void doSearch() {
        rScroll = 0;
        DPModApi.searchAsync(site, rightSearch == null ? "" : rightSearch.getText().trim(), sort);
    }

    private String currentUrl() {
        String q = rightSearch != null ? rightSearch.getText().trim().replace(" ", "+") : "";
        if (site == 0)
            return q.isEmpty() ? "https://modrinth.com/mods?v=1.12.2"
                               : "https://modrinth.com/mods?q=" + q + "&v=1.12.2";
        return q.isEmpty() ? "https://www.curseforge.com/minecraft/search?class=mc-mods&gameVersion=1.12.2"
                           : "https://www.curseforge.com/minecraft/search?class=mc-mods&search=" + q;
    }

    private void buildRestartButtons() {
        int cx = this.width / 2, y = this.height / 2 + 8;
        this.buttonList.add(new DPButton(20, cx - 155, y, 100, 20, "Restart Now").plain());
        this.buttonList.add(new DPButton(21, cx - 50,  y, 100, 20, "Quit Game").plain());
        this.buttonList.add(new DPButton(22, cx + 55,  y, 100, 20, "Keep Editing").plain());
    }

    private String siteName() { return site == 0 ? "Modrinth" : "CurseForge"; }

    // ---------------- drawing ----------------
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        DPBackdrop.draw(this.mc, this.width, this.height);

        // the house-style panel, split in two halves
        int on = 0;
        for (Mod m : all) if (m.enabled) on++;
        PrideFrame.fit(this.width, this.height).drawOver("Mod Browser",
                on + " on · " + (all.size() - on) + " off · " + all.size() + " mods" + (dirty ? " · §erestart needed" : ""));
        Gui.drawRect(L + 6, T + 8, midX - 3, H - 6, 0x40000000);
        Gui.drawRect(midX + 3, T + 8, R - 6, H - 6, 0x40000000);
        Gui.drawRect(midX - 1, T + 8, midX + 1, H - 6, GREEN);   // divider

        // headers
        drawCenteredString(this.fontRenderer, "Your Mods (" + all.size() + ")", (L + midX) / 2, T + 12, GREEN_LT);
        drawCenteredString(this.fontRenderer, "Download - " + siteName(),
                midX + (R - midX) / 2, T + 12, site == 0 ? MODRINTH : CURSE);

        // search boxes (custom slot look behind them)
        DPStyle.slot(leftSearch.x - 4, leftSearch.y - 4, leftSearch.width + 8, 22, false, true);
        DPStyle.slot(rightSearch.x - 4, rightSearch.y - 4, rightSearch.width + 8, 22, false, true);
        if (leftSearch.getText().isEmpty() && !leftSearch.isFocused())
            this.fontRenderer.drawString("search your mods...", leftSearch.x + 2, leftSearch.y + 4, 0xFF9AA79A);
        if (rightSearch.getText().isEmpty() && !rightSearch.isFocused())
            this.fontRenderer.drawString("search " + siteName() + "...", rightSearch.x + 2, rightSearch.y + 4, 0xFF9AA79A);
        leftSearch.drawTextBox();
        rightSearch.drawTextBox();

        lastMouseX = mouseX; lastMouseY = mouseY;
        drawLeftList(mouseX, mouseY);
        if (mcefOn && DPMcef.ready()) {
            if (inRight(mouseX, mouseY)) DPMcef.mouseMove(mouseX - rgX1, mouseY - rgY1);
            DPMcef.draw(rgX1, rgY1, rgX2, rgY2);            // the live web page, right half
            GlStateManager.color(1F, 1F, 1F, 1F);
            Gui.drawRect(rgX1 - 1, rgY1 - 1, rgX2 + 1, rgY1, GREEN);   // thin green frame
            Gui.drawRect(rgX1 - 1, rgY2, rgX2 + 1, rgY2 + 1, GREEN);
        } else {
            drawRightInfo();
        }

        if (restartPrompt) drawRestartPopup(mouseX, mouseY, partialTicks);   // dim + box UNDER the buttons

        super.drawScreen(mouseX, mouseY, partialTicks);   // buttons render on top
    }

    private List<Mod> filtered() {
        String q = leftSearch == null ? "" : leftSearch.getText().toLowerCase().trim();
        List<Mod> out = new ArrayList<Mod>();
        for (Mod m : all) if (q.isEmpty() || m.id.toLowerCase().contains(q)) out.add(m);
        return out;
    }

    private int listBottom() { return H - 34; }

    // ---------------- scrollbars ----------------
    // The wheel was the only way to move these lists, which is no use on a handheld. Both lists now
    // have a real draggable bar; SB_W is also the click target width.
    private static final int SB_W = 6;

    private int leftMaxScroll()  { return Math.max(0, filtered().size() * rowH - (listBottom() - listTop)); }
    private int rightMaxScroll() { return Math.max(0, DPModApi.results.size() * rRowH - (rgY2 - rgY1)); }

    /** Draw a track + thumb down the right edge of a list. */
    private void drawScrollbar(int xRight, int top, int bottom, int scrollPos, int maxScroll, int contentH) {
        if (maxScroll <= 0) return;                       // everything fits; no bar
        int x1 = xRight - SB_W, x2 = xRight;
        Gui.drawRect(x1, top, x2, bottom, 0x66000000);    // track
        int viewH = bottom - top;
        int thumbH = Math.max(16, viewH * viewH / Math.max(contentH, 1));
        int thumbY = top + (int) ((long) (viewH - thumbH) * scrollPos / maxScroll);
        Gui.drawRect(x1, thumbY, x2, thumbY + thumbH, PrideFrame.PINK);   // house-style pink knob
    }

    /** Map a mouse Y inside the track to a scroll offset (centres the thumb on the cursor). */
    private int scrollFromMouse(int mouseY, int top, int bottom, int maxScroll, int contentH) {
        int viewH = bottom - top;
        int thumbH = Math.max(16, viewH * viewH / Math.max(contentH, 1));
        int usable = Math.max(1, viewH - thumbH);
        int rel = mouseY - top - thumbH / 2;
        int s = (int) ((long) rel * maxScroll / usable);
        return Math.max(0, Math.min(maxScroll, s));
    }

    private boolean onLeftBar(int mx)  { return mx >= (midX - 12) - SB_W && mx <= midX - 12; }
    private boolean onRightBar(int mx) { return mx >= (R - 12) - SB_W && mx <= R - 12; }

    private void drawLeftList(int mouseX, int mouseY) {
        List<Mod> list = filtered();
        int x0 = L + 12, x1 = midX - 12;
        int y = listTop - scroll;
        int clipTop = listTop, clipBot = listBottom();
        GlStateManager.enableBlend();
        PrideFrame.clip(x0, clipTop, x1 - x0, clipBot - clipTop);
        for (Mod m : list) {
            if (y + rowH >= clipTop && y <= clipBot) {
                boolean hover = mouseX >= x0 && mouseX <= x1 - 22 && mouseY >= y && mouseY < y + rowH
                        && mouseY >= clipTop && mouseY <= clipBot;
                DPStyle.slot(x0, y, x1 - x0, rowH - 2, hover, true);
                // block icon
                DPBlocks.draw(this.mc, DPBlocks.indexForLabel(m.id), x0 + 1, y + 1, rowH - 4, rowH - 4);
                // on/off pill
                int pillX = x1 - 20;
                Gui.drawRect(pillX, y + 3, x1 - 2, y + rowH - 5, m.enabled ? GREEN : 0xFF444444);
                String pill = m.enabled ? "ON" : "OFF";
                this.fontRenderer.drawString(pill, pillX + (18 - this.fontRenderer.getStringWidth(pill)) / 2,
                        y + (rowH - 8) / 2 - 1, 0xFFFFFFFF);
                // name (trim to fit before pill)
                String nm = this.fontRenderer.trimStringToWidth(m.display(), (pillX - 24) - (x0 + rowH));
                this.fontRenderer.drawString(nm, x0 + rowH, y + (rowH - 8) / 2 - 1,
                        m.enabled ? 0xFFF2F2F2 : 0xFF9A9A9A);
                // remove X
                int xx = x1 - 20 - 12;
                boolean xh = mouseX >= xx && mouseX <= xx + 10 && mouseY >= y + 4 && mouseY <= y + rowH - 6;
                this.fontRenderer.drawString("x", xx, y + (rowH - 8) / 2 - 1, xh ? 0xFFFF6666 : OFFRED);
            }
            y += rowH;
        }
        PrideFrame.unclip();
        // hint
        this.fontRenderer.drawString("click = enable/disable   X = remove", x0, listBottom() + 2, 0xFF7A8A6A);
        drawScrollbar(x1, clipTop, clipBot, scroll, leftMaxScroll(), list.size() * rowH);
    }

    /** The Prism-style result list: one row per mod, click a row to download it into this group. */
    private void drawRightInfo() {
        int x0 = midX + 12, x1 = R - 12;
        int col = site == 0 ? MODRINTH : CURSE;

        // a download finished -> refresh the left list and arm the restart prompt
        if (DPModApi.downloadsDone != seenDownloads) {
            seenDownloads = DPModApi.downloadsDone;
            reload();
            dirty = true;
        }

        if (DPModApi.loading) {
            drawCenteredString(this.fontRenderer, "Searching " + siteName() + "...",
                    (x0 + x1) / 2, rgY1 + 30, GREEN_LT);
            return;
        }

        List<DPModApi.Hit> hits = DPModApi.results;
        if (DPModApi.error != null && hits.isEmpty()) {
            drawCenteredString(this.fontRenderer, DPModApi.error, (x0 + x1) / 2, rgY1 + 30, OFFRED);
            return;
        }

        int y = rgY1 - rScroll;
        PrideFrame.clip(x0, rgY1, x1 - x0, rgY2 - rgY1);
        for (DPModApi.Hit h : hits) {
            if (y + rRowH >= rgY1 && y <= rgY2) {
                boolean hover = lastMouseX >= x0 && lastMouseX <= x1
                        && lastMouseY >= y && lastMouseY < y + rRowH
                        && lastMouseY >= rgY1 && lastMouseY <= rgY2;
                boolean busy = DPModApi.downloading == h;
                DPStyle.slot(x0, y, x1 - x0, rRowH - 2, hover, true);

                String dl = DPModApi.humanCount(h.downloads) + " dl";
                int dlW = this.fontRenderer.getStringWidth(dl);
                this.fontRenderer.drawString(dl, x1 - dlW - 6, y + 4, 0xFF7A8A6A);

                String title = this.fontRenderer.trimStringToWidth(
                        h.title == null ? "?" : h.title, (x1 - dlW - 12) - (x0 + 6));
                this.fontRenderer.drawString(title, x0 + 6, y + 4, busy ? GREEN_LT : 0xFFF2F2F2);

                String sub = busy ? "downloading..."
                        : (hover ? "click to download" : (h.desc == null ? "" : h.desc));
                sub = this.fontRenderer.trimStringToWidth(sub, x1 - x0 - 12);
                this.fontRenderer.drawString(sub, x0 + 6, y + 16, busy ? GREEN_LT : 0xFF9AA79A);
            }
            y += rRowH;
        }
        PrideFrame.unclip();

        drawScrollbar(x1, rgY1, rgY2, rScroll, rightMaxScroll(), hits.size() * rRowH);

        if (hits.isEmpty())
            drawCenteredString(this.fontRenderer, "No 1.12.2 mods found.", (x0 + x1) / 2, rgY1 + 30, GRAY);

        String foot = DPModApi.error != null ? DPModApi.error
                : hits.size() + " results - Enter in the search box to refine";
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(foot, x1 - x0),
                x0, H - 78, DPModApi.error != null ? OFFRED : 0xFF7A8A6A);
    }

    private void drawRestartPopup(int mouseX, int mouseY, float pt) {
        Gui.drawRect(0, 0, this.width, this.height, 0x99140C1F);
        PrideFrame p = PrideFrame.sized(this.width, this.height, 340, 110);
        p = p.moveTo(p.x, p.y - 12);                           // Restart/Quit/Keep row sits at height/2 + 8
        p.drawOver("Mods changed", null);
        drawCenteredString(this.fontRenderer, "Restart the game to apply your changes.",
                this.width / 2, p.cy + 4, 0xFFFFFFFF);
        // re-draw the popup buttons on top (they're in buttonList; super already drew them, this keeps them visible)
    }

    // ---------------- input ----------------
    @Override
    public void updateScreen() {
        if (leftSearch != null) leftSearch.updateCursorCounter();
        if (rightSearch != null) rightSearch.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {   // ESC
            onBack();
            return;
        }
        if (rightSearch.isFocused() && keyCode == 28) {   // Enter in right search
            if (browserMode) { if (mcefOn && DPMcef.ready()) DPMcef.loadURL(currentUrl()); else openSite(); }
            else doSearch();
            return;
        }
        // Up/Down/PageUp/PageDown/Home/End scroll the mod list when no search box has focus.
        if (!leftSearch.isFocused() && !rightSearch.isFocused()) {
            int page = listBottom() - listTop, max = leftMaxScroll();
            switch (keyCode) {
                case 200: scroll -= rowH; break;          // up arrow
                case 208: scroll += rowH; break;          // down arrow
                case 201: scroll -= page; break;          // page up
                case 209: scroll += page; break;          // page down
                case 199: scroll = 0; break;              // home
                case 207: scroll = max; break;            // end
                default: break;
            }
            if (scroll < 0) scroll = 0;
            if (scroll > max) scroll = max;
        }

        if (leftSearch.textboxKeyTyped(typedChar, keyCode)) { scroll = 0; return; }
        if (rightSearch.textboxKeyTyped(typedChar, keyCode)) return;
        // neither search box focused -> type into the live web page
        if (mcefOn && DPMcef.ready()) {
            DPMcef.keyPressed(keyCode, typedChar);
            if (typedChar >= 32 && typedChar != 127) DPMcef.keyTyped(typedChar);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean inRight(int x, int y) {
        return x >= rgX1 && x <= rgX2 && y >= rgY1 && y <= rgY2;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);   // buttons + focus handled below
        if (restartPrompt) return;

        // scrollbar grab (before row hit-testing, so a bar click never toggles a mod)
        if (onLeftBar(mouseX) && mouseY >= listTop && mouseY <= listBottom() && leftMaxScroll() > 0) {
            dragBar = 1;
            scroll = scrollFromMouse(mouseY, listTop, listBottom(), leftMaxScroll(), filtered().size() * rowH);
            return;
        }
        if (!browserMode && onRightBar(mouseX) && mouseY >= rgY1 && mouseY <= rgY2 && rightMaxScroll() > 0) {
            dragBar = 2;
            rScroll = scrollFromMouse(mouseY, rgY1, rgY2, rightMaxScroll(), DPModApi.results.size() * rRowH);
            return;
        }

        leftSearch.mouseClicked(mouseX, mouseY, mouseButton);
        rightSearch.mouseClicked(mouseX, mouseY, mouseButton);
        // click into the live page
        if (browserMode && mcefOn && DPMcef.ready() && inRight(mouseX, mouseY)) {
            DPMcef.mouseButton(mouseX - rgX1, mouseY - rgY1, mouseButton, true);
            return;
        }
        // click a search result -> download it into this group
        if (!browserMode && inRight(mouseX, mouseY) && !DPModApi.loading && DPModApi.downloading == null) {
            List<DPModApi.Hit> hits = DPModApi.results;
            int idx = (mouseY - (rgY1 - rScroll)) / rRowH;
            if (idx >= 0 && idx < hits.size()) DPModApi.downloadAsync(site, hits.get(idx), modDir);
            return;
        }
        // left list hit-testing
        int x0 = L + 12, x1 = midX - 12;
        if (mouseX >= x0 && mouseX <= x1 && mouseY >= listTop && mouseY <= listBottom()) {
            List<Mod> list = filtered();
            int idx = (mouseY - (listTop - scroll)) / rowH;
            if (idx >= 0 && idx < list.size()) {
                Mod m = list.get(idx);
                int xx = x1 - 20 - 12;
                if (mouseX >= xx && mouseX <= xx + 12) removeMod(m);
                else toggleMod(m);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldMs) {
        if (dragBar == 1) {
            scroll = scrollFromMouse(mouseY, listTop, listBottom(), leftMaxScroll(), filtered().size() * rowH);
            return;
        }
        if (dragBar == 2) {
            rScroll = scrollFromMouse(mouseY, rgY1, rgY2, rightMaxScroll(), DPModApi.results.size() * rRowH);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, heldMs);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragBar = 0;
        super.mouseReleased(mouseX, mouseY, state);
        if (mcefOn && DPMcef.ready() && inRight(mouseX, mouseY))
            DPMcef.mouseButton(mouseX - rgX1, mouseY - rgY1, state, false);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getDWheel();
        if (dWheel == 0) return;
        if (browserMode && mcefOn && DPMcef.ready() && inRight(lastMouseX, lastMouseY)) {
            DPMcef.mouseWheel(lastMouseX - rgX1, lastMouseY - rgY1, dWheel > 0 ? 1 : -1);
            return;
        }
        if (!browserMode && inRight(lastMouseX, lastMouseY)) {   // scroll the results list
            rScroll -= dWheel / 4;
            int rMax = Math.max(0, DPModApi.results.size() * rRowH - (rgY2 - rgY1));
            if (rScroll < 0) rScroll = 0;
            if (rScroll > rMax) rScroll = rMax;
            return;
        }
        scroll -= dWheel / 4;
        int content = filtered().size() * rowH;
        int max = Math.max(0, content - (listBottom() - listTop));
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: onBack(); break;
            case 10:                                                   // Modrinth <-> CurseForge
                site = 1 - site;
                if (sort >= DPModApi.sortLabels(site).length) sort = 0;
                DPModApi.results = new java.util.ArrayList<DPModApi.Hit>();
                DPModApi.error = null;
                this.initGui();
                if (!browserMode) doSearch();
                break;
            case 12:                                                   // cycle sort order
                sort = (sort + 1) % DPModApi.sortLabels(site).length;
                this.initGui();
                if (!browserMode) doSearch();
                break;
            case 13:
                if (!browserMode) { this.mc.displayGuiScreen(new DPContentBrowser(this)); break; }   // the all-custom browser
                if (browserMode) {                    // close the embedded browser, back to the list
                    browserMode = false;
                    DPMcef.close();
                    this.initGui();
                } else if (DPMcef.available()) {      // embed it in the right half
                    browserMode = true;
                    this.initGui();
                } else {                              // no MCEF natives -> floating window, right half.
                    openSite();                       // label stays "Open Web Browser"; it has its own Close.
                }
                break;
            case 20: doRestart(); break;                               // restart now
            case 21: this.mc.shutdown(); break;                        // quit
            case 22: restartPrompt = false; this.initGui(); break;     // keep editing
            default: break;
        }
    }

    @Override
    public void onGuiClosed() {
        DPMcef.close();
    }

    private void onBack() {
        if (dirty && !restartPrompt) { restartPrompt = true; this.initGui(); }
        else this.mc.displayGuiScreen(parent);
    }

    // ---------------- mod actions ----------------
    private void toggleMod(Mod m) {
        try {
            File dest;
            if (m.enabled) dest = new File(modDir, m.id + ".disabled");
            else           dest = new File(modDir, m.id);   // strip .disabled* -> plain .jar
            if (m.file.renameTo(dest)) {
                m.file = dest; m.enabled = !m.enabled; dirty = true;
            }
        } catch (Throwable ignored) {}
    }

    private void removeMod(Mod m) {
        try {
            File trash = new File(modDir, ".removed");
            if (!trash.exists()) trash.mkdirs();
            File dest = new File(trash, m.file.getName());
            if (m.file.renameTo(dest)) { all.remove(m); dirty = true; }
        } catch (Throwable ignored) {}
    }

    /** Pop the small floating Chromium browser (~/bin/dpmod-browser) over the right half —
     *  downloads land straight in this group folder. Falls back to xdg-open if it's missing. */
    private void openSite() {
        String q = rightSearch.getText().trim();
        String siteArg = site == 0 ? "modrinth" : "curseforge";
        File browser = new File(System.getProperty("user.home", "/home/user"), "bin/dpmod-browser");
        try {
            if (browser.canExecute()) {
                new ProcessBuilder(browser.getAbsolutePath(),
                        "--site", siteArg, "--query", q,
                        "--dir", modDir.getAbsolutePath(), "--half", "right").start();
                return;
            }
        } catch (Throwable ignored) {}
        String enc = q.replace(" ", "+");
        String url = site == 0
                ? (q.isEmpty() ? "https://modrinth.com/mods?v=1.12.2" : "https://modrinth.com/mods?q=" + enc + "&v=1.12.2")
                : (q.isEmpty() ? "https://www.curseforge.com/minecraft/search?class=mc-mods&gameVersion=1.12.2"
                               : "https://www.curseforge.com/minecraft/search?class=mc-mods&search=" + enc);
        try { new ProcessBuilder("xdg-open", url).start(); } catch (Throwable ignored) {}
    }

    /** Best-effort: relaunch the exact same process after a short delay, then shut down cleanly.
     *  Reads /proc/self/cmdline; if that fails we just quit (she relaunches). */
    private void doRestart() { restartGame(); }

    /** start the same game again (same java, same args) a few seconds after this one quits */
    static void restartGame() {
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(new File("/proc/self/cmdline").toPath());
            List<String> args = new ArrayList<String>();
            StringBuilder cur = new StringBuilder();
            for (byte b : raw) {
                if (b == 0) { if (cur.length() > 0) { args.add(cur.toString()); cur.setLength(0); } }
                else cur.append((char) b);
            }
            if (!args.isEmpty()) {
                StringBuilder cmd = new StringBuilder("sleep 4; exec");
                for (String a : args) cmd.append(' ').append("'").append(a.replace("'", "'\\''")).append("'");
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd.toString());
                pb.directory(new File(System.getProperty("user.dir", ".")));
                pb.inheritIO();
                pb.start();
            }
        } catch (Throwable ignored) {}
        net.minecraft.client.Minecraft.getMinecraft().shutdown();
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
