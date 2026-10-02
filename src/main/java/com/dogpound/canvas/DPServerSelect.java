package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.ServerPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * DogPound multiplayer screen - twin of the world list: moving wallpaper, a
 * bottom button bar (Join / Direct / Add / Edit / Delete / Refresh / Back), and
 * a center carousel of servers with their icon + name + live MOTD (server custom
 * colors/formatting preserved).
 */
public class DPServerSelect extends GuiScreen {
    private static final ResourceLocation DEFAULT_ICON = new ResourceLocation("textures/misc/unknown_server.png");
    private static final int CARD_W = 210, CARD_H = 96, GAP = 10;
    private static final int BAR_H = 30;
    private static final int BAR_BG = 0x99200F38, BAR_LINE = 0xFFF5A9B8;
    private static final int DIM = 0x99140C1F, BOX_BG = 0xE62B1745, BOX_BORDER = 0xFFF5A9B8;

    private final GuiScreen parent;
    private ServerList serverList;
    private final ServerPinger pinger = new ServerPinger();
    private ResourceLocation[] icons = new ResourceLocation[0];
    private boolean[] iconTried = new boolean[0];
    private int scrollY = 0;           // the card grid scrolls up/down inside the panel
    private int selected = -1;
    private boolean confirmDelete = false;

    // pending add/edit/direct flow via GuiScreenAddServer -> confirmClicked
    private int pendingMode = 0; // 1=add 2=edit 3=direct
    private ServerData pendingServer;

    private DPButton joinBtn, editBtn, deleteBtn;

    public DPServerSelect(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        if (serverList == null) {
            serverList = new ServerList(this.mc);
            serverList.loadServerList();
            icons = new ResourceLocation[serverList.countServers()];
            iconTried = new boolean[serverList.countServers()];
            pingAll();
        }

        if (confirmDelete) {
            int by = this.height / 2 + 8;
            this.addButton(new DPButton(20, this.width / 2 - 102, by, 100, 20, "Delete").plain());
            this.addButton(new DPButton(21, this.width / 2 + 2, by, 100, 20, "Cancel").plain());
            return;
        }

        String[] labels = {"Join", "Direct", "Add", "Edit", "Delete", "Refresh", "Back"};
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
        joinBtn = made[0];
        editBtn = made[3];
        deleteBtn = made[4];
        updateEnabled();
    }

    private void updateEnabled() {
        boolean s = selected >= 0 && serverList != null && selected < serverList.countServers();
        if (joinBtn != null) joinBtn.enabled = s;
        if (editBtn != null) editBtn.enabled = s;
        if (deleteBtn != null) deleteBtn.enabled = s;
    }

    private void pingAll() {
        if (serverList == null) return;
        for (int i = 0; i < serverList.countServers(); i++) {
            final ServerData sd = serverList.getServerData(i);
            sd.pinged = false;
            sd.serverMOTD = "§7Pinging...";
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        pinger.ping(sd);
                    } catch (Throwable e) {
                        sd.pingToServer = -1L;
                        sd.serverMOTD = "§4Can't reach server";
                    }
                }
            }, "DP Server Pinger");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public void updateScreen() {
        try { pinger.pingPendingNetworks(); } catch (Throwable t) { }
    }

    @Override
    public void onGuiClosed() {
        try { pinger.clearPendingNetworks(); } catch (Throwable t) { }
    }

    private ResourceLocation iconFor(int i) {
        if (i < 0 || i >= icons.length) return DEFAULT_ICON;
        if (icons[i] != null) return icons[i];
        if (!iconTried[i]) {
            ServerData sd = serverList.getServerData(i);
            String b64 = sd.getBase64EncodedIconData();
            if (b64 != null) {
                try {
                    byte[] bytes = java.util.Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img != null) {
                        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "servericon/" + i);
                        this.mc.getTextureManager().loadTexture(rl, new DynamicTexture(img));
                        icons[i] = rl;
                        return rl;
                    }
                } catch (Throwable t) { }
                iconTried[i] = true;
            }
        }
        return DEFAULT_ICON;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        DPBackground.draw(this.mc, this.width, this.height);

        int count = serverList == null ? 0 : serverList.countServers();
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        f.drawOver("Multiplayer", count + (count == 1 ? " server" : " servers"));
        clampScroll();
        int gy = f.cy, gh = gridH();
        PrideFrame.clip(f.cx, gy, f.cw, gh);
        for (int i = 0; i < count; i++) {
            int x = cardX(i), cy = cardY(i);
            if (cy + CARD_H < gy || cy > gy + gh) continue;
            ServerData sd = serverList.getServerData(i);
            boolean hover = mouseX >= x && mouseX <= x + CARD_W && mouseY >= cy && mouseY <= cy + CARD_H
                    && mouseY >= gy && mouseY <= gy + gh && !confirmDelete;
            boolean sel = (i == selected);
            PrideFrame.tile(x, cy, CARD_W, CARD_H, sel ? PrideFrame.BLUE : PrideFrame.PINK, hover, sel);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(iconFor(i));
            Gui.drawModalRectWithCustomSizedTexture(x + 8, cy + 8, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);

            this.fontRenderer.drawStringWithShadow(trim(sd.serverName, CARD_W - 90), x + 46, cy + 10, 0xFFFFFFFF);
            this.fontRenderer.drawString(trim(sd.serverIP, CARD_W - 54), x + 46, cy + 24, 0xFF8A8499);
            // MOTD with the server's own color/formatting codes (up to 2 lines)
            String motd = sd.serverMOTD == null ? "" : sd.serverMOTD;
            String[] lines = motd.split("\n");
            for (int l = 0; l < lines.length && l < 2; l++) {
                this.fontRenderer.drawString(trim(lines[l], CARD_W - 16), x + 8, cy + 46 + l * 11, 0xFFA0A0A0);
            }
            if (sd.pingToServer >= 0L) {
                String p = sd.pingToServer + "ms";
                this.fontRenderer.drawString(p, x + CARD_W - this.fontRenderer.getStringWidth(p) - 8, cy + 10, 0xFF80FF80);
            }
            // players + version once the ping answers
            if (sd.populationInfo != null && !sd.populationInfo.isEmpty())
                this.fontRenderer.drawString(sd.populationInfo, x + 8, cy + CARD_H - 13, 0xFFF5A9B8);
            if (sd.gameVersion != null && !sd.gameVersion.isEmpty()) {
                String v = trim(sd.gameVersion, CARD_W / 2);
                this.fontRenderer.drawString(v, x + CARD_W - this.fontRenderer.getStringWidth(v) - 8, cy + CARD_H - 13, 0xFF8A8499);
            }
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw - 3, gy, gh, scrollY, gh, contentH());

        if (count == 0) {
            drawCenteredString(this.fontRenderer, "No servers yet — click Add", this.width / 2, gy + gh / 2, 0xFFFFFFFF);
        }

        DPMemoryBar.draw(0, this.height - 2, this.width, 2);

        if (confirmDelete) {
            drawRect(0, 0, this.width, this.height, DIM);
            PrideFrame p = PrideFrame.sized(this.width, this.height, 280, 96);
            p.drawOver("Delete Server", null);
            String nm = selected >= 0 && selected < count ? serverList.getServerData(selected).serverName : "";
            drawCenteredString(this.fontRenderer, "Delete \"" + trim(nm, p.w - 20) + "\"?", this.width / 2, p.cy + 2, 0xFFFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        if (this.fontRenderer.getStringWidth(s) <= max) return s;
        return this.fontRenderer.trimStringToWidth(s, max - 8) + "..";
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (confirmDelete || mouseButton != 0 || serverList == null) return;
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        if (mouseY < f.cy || mouseY > f.cy + gridH()) return;   // outside the scrolling card area
        for (int i = 0; i < serverList.countServers(); i++) {
            int x = cardX(i), cy = cardY(i);
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= cy && mouseY <= cy + CARD_H) {
                if (i == selected) joinServer(i);
                else { selected = i; updateEnabled(); }
                return;
            }
        }
    }

    // ---- card grid geometry (shared by drawing + clicking so they always agree) ----
    private int gridW() { return PrideFrame.fit(this.width, this.height).cw - 8; }   // room for the scroll bar
    private int gridH() { return PrideFrame.fit(this.width, this.height).ch - 26; }  // above the button row
    private int cols() { return Math.max(1, (gridW() + GAP) / (CARD_W + GAP)); }
    private int gridX() {
        PrideFrame f = PrideFrame.fit(this.width, this.height);
        int c = cols();
        return f.cx + (gridW() - (c * CARD_W + (c - 1) * GAP)) / 2;
    }
    private int cardX(int i) { return gridX() + (i % cols()) * (CARD_W + GAP); }
    private int cardY(int i) { return PrideFrame.fit(this.width, this.height).cy + (i / cols()) * (CARD_H + GAP) - scrollY; }
    private int contentH() {
        int count = serverList == null ? 0 : serverList.countServers();
        int rows = (count + cols() - 1) / cols();
        return Math.max(0, rows * (CARD_H + GAP) - GAP);
    }

    private void joinServer(int i) {
        ServerData sd = serverList.getServerData(i);
        this.mc.displayGuiScreen(new GuiConnecting(this, this.mc, sd));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getDWheel();
        if (dWheel != 0) { scrollY -= dWheel / 2; clampScroll(); }
    }

    private void clampScroll() {
        int max = Math.max(0, contentH() - gridH());
        if (scrollY > max) scrollY = max;
        if (scrollY < 0) scrollY = 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        boolean hasSel = selected >= 0 && serverList != null && selected < serverList.countServers();
        switch (button.id) {
            case 0: if (hasSel) joinServer(selected); break;
            case 1: // Direct
                pendingMode = 3;
                pendingServer = new ServerData("Minecraft Server", "", false);
                this.mc.displayGuiScreen(new GuiScreenAddServer(this, pendingServer));
                break;
            case 2: // Add
                pendingMode = 1;
                pendingServer = new ServerData("Minecraft Server", "", false);
                this.mc.displayGuiScreen(new GuiScreenAddServer(this, pendingServer));
                break;
            case 3: // Edit
                if (hasSel) {
                    pendingMode = 2;
                    pendingServer = serverList.getServerData(selected);
                    this.mc.displayGuiScreen(new GuiScreenAddServer(this, pendingServer));
                }
                break;
            case 4: if (hasSel) { confirmDelete = true; this.initGui(); } break; // Delete
            case 5: // Refresh
                serverList = null; selected = -1; this.initGui();
                break;
            case 6: this.mc.displayGuiScreen(parent); break; // Back
            case 20: doDelete(); break;
            case 21: confirmDelete = false; this.initGui(); break;
            default: break;
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        int mode = pendingMode;
        pendingMode = 0;
        if (result && pendingServer != null) {
            if (mode == 1) { // add
                serverList.addServerData(pendingServer);
                serverList.saveServerList();
            } else if (mode == 2) { // edit (mutated in place)
                serverList.saveServerList();
            } else if (mode == 3) { // direct connect
                this.mc.displayGuiScreen(new GuiConnecting(this, this.mc, pendingServer));
                return;
            }
        }
        // rebuild list + return to this screen
        serverList = null;
        this.mc.displayGuiScreen(this);
    }

    private void doDelete() {
        if (selected >= 0 && serverList != null && selected < serverList.countServers()) {
            try {
                serverList.removeServerData(selected);
                serverList.saveServerList();
            } catch (Throwable t) { }
        }
        selected = -1;
        confirmDelete = false;
        serverList = null;
        this.initGui();
    }
}
