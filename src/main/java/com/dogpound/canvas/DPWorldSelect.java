package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldEdit;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSummary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DogPound world list: moving wallpaper, a bottom button bar (Play / New World /
 * Edit / Delete / Re-Create / Back) like the main menu, and a center carousel of
 * worlds (icon + name) you scroll left/right and tap to select, tap again to play.
 */
public class DPWorldSelect extends GuiScreen {
    private static final ResourceLocation DEFAULT_ICON = new ResourceLocation("textures/misc/unknown_server.png");
    private static final int CARD_W = 120, CARD_H = 112, GAP = 10;
    private static final int BAR_H = 30;
    private static final int BAR_BG = 0x99200F38, BAR_LINE = 0xFFF5A9B8;
    private static final int DIM = 0x99140C1F, BOX_BG = 0xE62B1745, BOX_BORDER = 0xFFF5A9B8;

    private final GuiScreen parent;
    private final List<WorldSummary> worlds = new ArrayList<WorldSummary>();
    private final List<ResourceLocation> icons = new ArrayList<ResourceLocation>();
    private int scrollY = 0;           // the card grid scrolls up/down inside the panel
    private int selected = -1;
    private boolean confirmDelete = false;

    private DPButton playBtn, editBtn, deleteBtn, recreateBtn;

    public DPWorldSelect(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        if (confirmDelete) {
            int by = this.height / 2 + 8;
            this.addButton(new DPButton(20, this.width / 2 - 102, by, 100, 20, "Delete").plain());
            this.addButton(new DPButton(21, this.width / 2 + 2, by, 100, 20, "Cancel").plain());
            return;
        }

        if (worlds.isEmpty()) {
            loadWorlds();
        }

        String[] labels = {"Play", "New World", "Edit", "Delete", "Re-Create", "Back"};
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        int n = labels.length, gap = 4;
        int bw = Math.min(150, (f.cw - (n - 1) * gap) / n);
        int total = n * bw + (n - 1) * gap;
        int startX = f.cx + (f.cw - total) / 2;
        int y = f.cy + f.ch - 20;                      // button row along the bottom of the panel
        DPButton[] made = new DPButton[n];
        for (int i = 0; i < n; i++) {
            made[i] = new DPButton(i, startX + i * (bw + gap), y, bw, 20, labels[i]);
            this.addButton(made[i]);
        }
        playBtn = made[0];
        editBtn = made[2];
        deleteBtn = made[3];
        recreateBtn = made[4];
        updateEnabled();
    }

    private void updateEnabled() {
        boolean s = selected >= 0 && selected < worlds.size();
        if (playBtn != null) playBtn.enabled = s;
        if (editBtn != null) editBtn.enabled = s;
        if (deleteBtn != null) deleteBtn.enabled = s;
        if (recreateBtn != null) recreateBtn.enabled = s;
    }

    private void loadWorlds() {
        worlds.clear();
        icons.clear();
        File savesDir = new File(this.mc.mcDataDir, "saves");
        try {
            worlds.addAll(DPWorldCache.list(savesDir));     // cached + no data fixer: was ~1 minute with 29 modded worlds
        } catch (Throwable t) {
            try {                                           // fallback: vanilla's slow list
                ISaveFormat sf = this.mc.getSaveLoader();
                List<WorldSummary> list = sf.getSaveList();
                Collections.sort(list);
                worlds.addAll(list);
            } catch (Throwable ignored) {}
        }
        for (WorldSummary w : worlds) {
            ResourceLocation loc = DEFAULT_ICON;
            try {
                File icon = new File(new File(savesDir, w.getFileName()), "icon.png");
                if (icon.isFile()) {
                    BufferedImage img = ImageIO.read(icon);
                    if (img != null) {
                        String key = w.getFileName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "worldicon/" + key);
                        this.mc.getTextureManager().loadTexture(rl, new DynamicTexture(img));
                        loc = rl;
                    }
                }
            } catch (Throwable t) {
                // keep default
            }
            icons.add(loc);
        }
        clampScroll();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        DPBackground.draw(this.mc, this.width, this.height);

        PrideFrame f = PrideFrame.fit(this.width, this.height);
        f.drawOver("Select World", worlds.size() + (worlds.size() == 1 ? " world" : " worlds"));
        clampScroll();
        int gx = gridX(), gy = f.cy, gh = gridH();
        PrideFrame.clip(f.cx, gy, f.cw, gh);
        for (int i = 0; i < worlds.size(); i++) {
            int x = cardX(i), cy = cardY(i);
            if (cy + CARD_H < gy || cy > gy + gh) continue;
            boolean hover = mouseX >= x && mouseX <= x + CARD_W && mouseY >= cy && mouseY <= cy + CARD_H
                    && mouseY >= gy && mouseY <= gy + gh && !confirmDelete;
            boolean sel = (i == selected);
            PrideFrame.tile(x, cy, CARD_W, CARD_H, sel ? PrideFrame.BLUE : PrideFrame.PINK, hover, sel);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(icons.get(i));
            Gui.drawModalRectWithCustomSizedTexture(x + CARD_W / 2 - 32, cy + 10, 0.0F, 0.0F, 64, 64, 64.0F, 64.0F);

            String nm = trim(worlds.get(i).getDisplayName(), CARD_W - 10);
            drawCenteredString(this.fontRenderer, nm, x + CARD_W / 2, cy + CARD_H - 28, 0xFFFFFFFF);
            drawCenteredString(this.fontRenderer, trim(detail(worlds.get(i)), CARD_W - 6), x + CARD_W / 2, cy + CARD_H - 15, PrideFrame.DIM);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw - 3, gy, gh, scrollY, gh, contentH());

        if (worlds.isEmpty()) {
            drawCenteredString(this.fontRenderer, "No worlds yet — click New World",
                    this.width / 2, gy + gh / 2, 0xFFFFFFFF);
        }

        DPMemoryBar.draw(0, this.height - 2, this.width, 2);

        if (confirmDelete) {
            drawRect(0, 0, this.width, this.height, DIM);
            PrideFrame p = PrideFrame.sized(this.width, this.height, 280, 96);
            p.drawOver("Delete World", null);
            String nm = selected >= 0 && selected < worlds.size() ? worlds.get(selected).getDisplayName() : "";
            drawCenteredString(this.fontRenderer, "Delete \"" + trim(nm, p.w - 20) + "\"?", this.width / 2, p.cy + 2, 0xFFFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** mode + last played, e.g. "survival · 09/27/26" (extra detail on each card) */
    private static String detail(WorldSummary w) {
        String mode = w.isHardcoreModeEnabled() ? "hardcore" : w.getEnumGameType().getName();
        return mode + (w.getCheatsEnabled() ? "+cheats" : "") + " · "
                + new java.text.SimpleDateFormat("MM/dd/yy").format(new java.util.Date(w.getLastTimePlayed()));
    }

    private String trim(String s, int max) {
        if (this.fontRenderer.getStringWidth(s) <= max) return s;
        return this.fontRenderer.trimStringToWidth(s, max - 8) + "..";
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (confirmDelete || mouseButton != 0) return;
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        if (mouseY < f.cy || mouseY > f.cy + gridH()) return;   // outside the scrolling card area
        for (int i = 0; i < worlds.size(); i++) {
            int x = cardX(i), cy = cardY(i);
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= cy && mouseY <= cy + CARD_H) {
                if (i == selected) {
                    loadWorld(i);
                } else {
                    selected = i;
                    updateEnabled();
                }
                return;
            }
        }
    }

    // ---- card grid geometry (shared by drawing + clicking so they always agree) ----
    private int gridW() { PrideFrame f = PrideFrame.fit(this.width, this.height); return f.cw - 8; }   // room for the scroll bar
    private int gridH() { PrideFrame f = PrideFrame.fit(this.width, this.height); return f.ch - 26; }  // above the button row
    private int cols() { return Math.max(1, (gridW() + GAP) / (CARD_W + GAP)); }
    private int gridX() {
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        int c = cols();
        return f.cx + (gridW() - (c * CARD_W + (c - 1) * GAP)) / 2;
    }
    private int cardX(int i) { return gridX() + (i % cols()) * (CARD_W + GAP); }
    private int cardY(int i) { return PrideFrame.fit(this.width, this.height).cy + (i / cols()) * (CARD_H + GAP) - scrollY; }
    private int contentH() {
        int rows = (worlds.size() + cols() - 1) / cols();
        return Math.max(0, rows * (CARD_H + GAP) - GAP);
    }

    private void loadWorld(int i) {
        WorldSummary w = worlds.get(i);
        if (this.mc.getSaveLoader().canLoadWorld(w.getFileName())) {
            this.mc.displayGuiScreen(null);
            this.mc.launchIntegratedServer(w.getFileName(), w.getDisplayName(), null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getDWheel();
        if (dWheel != 0) {
            scrollY -= dWheel / 2;
            clampScroll();
        }
    }

    private void clampScroll() {
        int max = Math.max(0, contentH() - gridH());
        if (scrollY > max) scrollY = max;
        if (scrollY < 0) scrollY = 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        boolean hasSel = selected >= 0 && selected < worlds.size();
        switch (button.id) {
            case 0: // Play
                if (hasSel) loadWorld(selected);
                break;
            case 1: // New World
                this.mc.displayGuiScreen(new DPCreateWorld(this));
                break;
            case 2: // Edit
                if (hasSel) this.mc.displayGuiScreen(new GuiWorldEdit(this, worlds.get(selected).getFileName()));
                break;
            case 3: // Delete -> confirm
                if (hasSel) { confirmDelete = true; this.initGui(); }
                break;
            case 4: // Re-Create
                if (hasSel) recreate(selected);
                break;
            case 5: // Back
                this.mc.displayGuiScreen(parent);
                break;
            case 20: // confirm delete
                doDelete();
                break;
            case 21: // cancel delete
                confirmDelete = false;
                this.initGui();
                break;
            default:
                break;
        }
    }

    private void recreate(int i) {
        try {
            WorldInfo info = this.mc.getSaveLoader().getWorldInfo(worlds.get(i).getFileName());
            if (info != null) {
                GuiCreateWorld g = new DPCreateWorld(this);
                g.recreateFromExistingWorld(info);
                this.mc.displayGuiScreen(g);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private void doDelete() {
        if (selected >= 0 && selected < worlds.size()) {
            try {
                ISaveFormat sf = this.mc.getSaveLoader();
                sf.flushCache();
                sf.deleteWorldDirectory(worlds.get(selected).getFileName());
            } catch (Throwable t) {
                // ignore
            }
        }
        selected = -1;
        confirmDelete = false;
        worlds.clear();
        this.initGui();
    }
}
