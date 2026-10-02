package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The Pride pause menu (her ask 2026-09-30: "do away with the esc menu… make a complete custom one… look at every
 * mod in the menu… very pretty like the main menu… smooth transitions and animations… pretty sounds").
 *
 * How every mod keeps working: a REAL GuiIngameMenu is built off-screen (so Forge fires InitGuiEvent and every
 * mod adds its buttons to it — Quark, PolyPatcher, OneConfig, Indicatia, Universal Tweaks, LOTR, Mystcraft…).
 * Each of its buttons becomes a tile here; clicking a tile clicks that real button (Forge's ActionPerformedEvent
 * fires on the real menu, so the mods' own handlers run exactly as before). Screens those buttons open return to
 * the real menu, which DPMenuMod swaps for this one again.
 */
public class DPPauseMenu extends GuiScreen {
    private static final int BACK = 4, QUIT = 1, OPTIONS = 0, ADV = 5, STATS = 6, LAN = 7, MODS = 12;
    private static final int PRIDE_BASE = 900, POP_QUIT = 950, POP_EXIT = 951, POP_CANCEL = 952;

    private final GuiIngameMenu real;                 // the off-screen vanilla menu the mods decorate
    private final List<GuiButton> realButtons = new ArrayList<>();
    private final List<String[]> prideTiles = new ArrayList<>();   // {label, command}
    private final List<DPButton> tiles = new ArrayList<>();
    private DPButton backTile, quitTile;
    private long openedAt, closingAt, popupAt;
    private boolean popup;
    private Runnable afterClose;
    private int scroll, contentH, gridTop, gridH;
    private final DPPlayerModel model = new DPPlayerModel();
    private int[] modelBox;                                          // where the spinnable character is
    private long sessionStart = SESSION_START;
    private static final long SESSION_START = System.currentTimeMillis();

    public DPPauseMenu(GuiIngameMenu real) {
        this.real = real == null ? new GuiIngameMenu() : real;
    }

    // ------------------------------------------------------------------ layout
    private PrideFrame frame() { return PrideFrame.sized(width, height, 600, 330); }
    private int sideW(PrideFrame f) { return f.cw >= 340 ? Math.min(150, Math.max(110, f.cw / 3)) : 0; }

    @Override
    public void initGui() {
        boolean first = openedAt == 0;
        if (first) { openedAt = DPAnim.now(); DPSounds.play(DPSounds.OPEN, 1.05f, 0.7f); }
        buttonList.clear(); tiles.clear(); realButtons.clear(); prideTiles.clear();

        // the real menu gets its buttons (and every mod's) at our size
        real.setWorldAndResolution(mc, width, height);
        for (GuiButton b : realList()) if (b.visible) realButtons.add(b);

        PrideFrame f = frame();
        int side = sideW(f), gx = f.cx + side + (side > 0 ? 8 : 0), gw = f.cx + f.cw - gx;
        int y = f.cy;

        // big "Back to Game" across the top
        backTile = new DPButton(BACK, gx, y, gw, 24, "▶  Back to Game").sound(DPSounds.CONFIRM);
        backTile.flat = true;
        buttonList.add(backTile);
        y += 30;

        // every other real button (vanilla + mods), then the Pride shortcuts, as square tiles
        List<GuiButton> order = new ArrayList<>();
        for (int id : new int[]{OPTIONS, ADV, STATS, LAN, MODS})
            for (GuiButton b : realButtons) if (b.id == id) order.add(b);
        for (GuiButton b : realButtons) if (b.id != BACK && b.id != QUIT && !order.contains(b)) order.add(b);   // mods' buttons
        prideTiles.add(new String[]{"Browser", "#web"});                  // the website, in-game
        addPride("Pride Hub", "/pride", "realmcoin");
        addPride("Pride Store", "/store", "realmcoin");
        addPride("Commands", "/pcmds", "realmcoin");
        addPride("Land Map", "/landmap", "realmcoin");
        addPride("Permissions", "/perms menu", "prideperms");
        addPride("Block History", "/pp gui", "prideprism");
        addPride("Crash Reports", "/pridecrash", "pridecrash");
        addPride("Quest Book", "/quests", "pridequests");
        addPride("Tails & Ears", "#config:ears", "ears");             // Ears' own editor (the Ears Manipulator)
        addPride("Tails & Ears", "#config:tails", "tails");           // the Tails mod's editor, if that one is on instead

        int n = order.size() + prideTiles.size();
        gridTop = y; gridH = f.cy + f.ch - 30 - y;
        int tile = Math.max(40, Math.min(58, gw / 5 - 6)), gap = 6;
        cols = Math.max(1, (gw + gap) / (tile + gap));
        tile = (gw - (cols - 1) * gap) / cols;                        // stretch to fill the row exactly
        int th = Math.min(tile, 52);
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            String label;
            int id;
            if (i < order.size()) { GuiButton b = order.get(i); label = clean(b.displayString); id = 1000 + realButtons.indexOf(b); }
            else { label = prideTiles.get(i - order.size())[0]; id = PRIDE_BASE + (i - order.size()); }
            DPButton t = new DPButton(id, gx + c * (tile + gap), y + r * (th + gap), tile, th, label);
            t.soundIndex = c;
            t.enabled = i >= order.size() || order.get(i).enabled;
            t.noSlot = false;
            tiles.add(t);
            buttonList.add(t);
        }
        contentH = ((n + cols - 1) / cols) * (th + gap) - gap;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - gridH)));
        placeTiles();

        // Save & Quit along the bottom
        boolean sp = mc.isIntegratedServerRunning();
        quitTile = new DPButton(QUIT, gx, f.cy + f.ch - 24, gw, 24, sp ? "✖  Save and Quit to Title" : "✖  Disconnect").sound(DPSounds.POPUP);
        quitTile.flat = true;
        buttonList.add(quitTile);

        if (popup) {
            PrideFrame p = popupFrame();
            int bw = (p.cw - 12) / 3, by = p.cy + p.ch - 26;
            buttonList.add(new DPButton(POP_QUIT, p.cx, by, bw, 22, sp ? "Save & Quit" : "Disconnect").plain().sound(DPSounds.CONFIRM));
            buttonList.add(new DPButton(POP_EXIT, p.cx + bw + 6, by, bw, 22, "Quit Game").plain().sound(DPSounds.CONFIRM));
            buttonList.add(new DPButton(POP_CANCEL, p.cx + 2 * (bw + 6), by, bw, 22, "Cancel").plain().sound(DPSounds.BACK));
            for (GuiButton b : buttonList) if (b.id < POP_QUIT) b.enabled = false;
        }
    }

    private void addPride(String label, String cmd, String modid) {
        if (Loader.isModLoaded(modid)) prideTiles.add(new String[]{label, cmd});
    }

    private static String clean(String s) {
        String t = TextFormatting.getTextWithoutFormattingCodes(s == null ? "" : s).trim();
        return t.endsWith("...") ? t.substring(0, t.length() - 3) : t;
    }

    /** scroll the tile grid: rows out of the grid area are hidden so they can't be clicked */
    private void placeTiles() {
        int th = tiles.isEmpty() ? 0 : tiles.get(0).height;
        for (int i = 0; i < tiles.size(); i++) {
            DPButton t = tiles.get(i);
            t.y = gridTop + (i / cols) * (th + 6) - scroll;
            t.visible = t.y >= gridTop - 1 && t.y + t.height <= gridTop + gridH + 1;
        }
    }
    private int cols = 1;

    /** the real menu's buttonList (protected in GuiScreen; SRG name at runtime) */
    @SuppressWarnings("unchecked")
    private List<GuiButton> realList() {
        try {
            java.lang.reflect.Field f = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            return (List<GuiButton>) f.get(real);
        } catch (Throwable t) { return new ArrayList<>(); }
    }

    private PrideFrame popupFrame() { return PrideFrame.sized(width, height, 300, 112); }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        boolean anim = DPConfig.animations;
        float open = anim ? DPAnim.easeOutCubic(DPAnim.progress(openedAt, 0, 260)) : 1;
        float close = closingAt > 0 ? DPAnim.easeInCubic(DPAnim.progress(closingAt, 0, 170)) : 0;
        float vis = open * (1 - close);
        if (closingAt > 0 && close >= 1) { finishClose(); return; }

        // the world stays visible, softly tinted in the trans colours
        int dim = (int) (150 * vis);
        PrideFrame.gradient(0, 0, width, height, (dim << 24) | 0x16092F, ((int) (dim * 1.2f) << 24) | 0x2B1745);
        if (DPConfig.sparkles) DPAnim.sparkles(width, height, 26);

        PrideFrame f = frame();
        GlStateManager.pushMatrix();
        float slide = (1 - vis) * 24, sc = 0.94f + 0.06f * vis;
        GlStateManager.translate(width / 2f, height / 2f + slide, 0);
        GlStateManager.scale(sc, sc, 1);
        GlStateManager.translate(-width / 2f, -height / 2f, 0);

        f.drawOver("Paused", worldName());
        int side = sideW(f);
        if (side > 0) { mouseOverModel = modelBox != null && model.over(mx, my, modelBox[0], modelBox[1], modelBox[2], modelBox[3]); drawInfo(f.cx, f.cy, side, f.ch); }

        // tiles: staggered pop-in
        backTile.drawButton(mc, mx, my, pt);
        PrideFrame.clip(f.cx + side, gridTop - 4, f.cw - side, gridH + 8);
        for (int i = 0; i < tiles.size(); i++) {
            DPButton t = tiles.get(i);
            if (!t.visible) continue;
            float pop = anim ? DPAnim.easeOutBack(DPAnim.progress(openedAt, 60 + i * 28L, 280)) : 1;
            if (pop <= 0.01f) continue;
            GlStateManager.pushMatrix();
            float cx = t.x + t.width / 2f, cy = t.y + t.height / 2f;
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(pop, pop, 1);
            GlStateManager.translate(-cx, -cy, 0);
            t.drawButton(mc, mx, my, pt);
            GlStateManager.popMatrix();
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw - 3, gridTop, gridH, scroll, gridH, contentH);
        quitTile.drawButton(mc, mx, my, pt);
        GlStateManager.popMatrix();

        if (popup) drawPopup(mx, my, pt);
        DPAnim.drawRipples();
        // tooltip: the full name of a tile whose label had to shrink
        for (DPButton t : tiles) if (t.visible && t.isMouseOver() && !popup && fontRenderer.getStringWidth(t.displayString) > t.width - 4)
            drawHoveringText(java.util.Collections.singletonList(t.displayString), mx, my);
    }

    private void drawPopup(int mx, int my, float pt) {
        float p = DPConfig.animations ? DPAnim.easeOutBack(DPAnim.progress(popupAt, 0, 240)) : 1;
        drawRect(0, 0, width, height, ((int) (120 * Math.min(1, p)) << 24) | 0x0B0518);
        PrideFrame f = popupFrame();
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f, height / 2f, 0);
        GlStateManager.scale(p, p, 1);
        GlStateManager.translate(-width / 2f, -height / 2f, 0);
        f.drawOver(mc.isIntegratedServerRunning() ? "Leave this world?" : "Leave this server?", null);
        drawCenteredString(fontRenderer, mc.isIntegratedServerRunning() ? "Your world is saved first. ❤" : "You can come back any time. ❤", width / 2, f.cy + 6, 0xFFFFFF);
        drawCenteredString(fontRenderer, "§7" + sessionText() + " this session", width / 2, f.cy + 20, 0xFFFFFF);
        for (GuiButton b : buttonList) if (b.id >= POP_QUIT) b.drawButton(mc, mx, my, pt);
        GlStateManager.popMatrix();
    }

    /** left card: you, where you are, and how the game is doing */
    private void drawInfo(int x, int y, int w, int h) {
        PrideFrame.card(x, y, w, h, PrideFrame.PINK);
        EntityPlayerSP p = mc.player;
        FontRenderer fr = fontRenderer;
        // your real character: grab it to spin, scroll to zoom
        int figH = Math.max(60, Math.min(120, h - 130));
        modelBox = new int[]{x + 4, y + 4, w - 8, figH + 8};
        Gui.drawRect(x + 4, y + 4, x + w - 4, y + figH + 12, 0x30000000);
        if (p != null) model.draw(p, x + w / 2, y + figH + 6, figH);
        if (!model.dragging() && mouseOverModel) fr.drawStringWithShadow("§8⟲ drag to spin", x + (w - fr.getStringWidth("⟲ drag to spin")) / 2f, y + figH + 3, 0xFFFFFF);
        int s = figH / 32;
        int ty = y + figH + 16;
        String name = p == null ? mc.getSession().getUsername() : p.getName();
        fr.drawStringWithShadow("§l" + name, x + (w - fr.getStringWidth("§l" + name)) / 2f, ty, 0xFFFFFF);
        ty += 13;
        if (p != null) {
            BlockPos pos = p.getPosition();
            ty = line(x, ty, w, "§b◉", String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            ty = line(x, ty, w, "§a✿", mc.world.getBiome(pos).getBiomeName());
            ty = line(x, ty, w, "§d✦", dimName());
            long time = mc.world.getWorldTime();
            ty = line(x, ty, w, "§e☀", String.format("Day %d  %02d:%02d", time / 24000 + 1, (int) ((time / 1000 + 6) % 24), (int) (time % 1000 * 60 / 1000)));
            ty = line(x, ty, w, "§c❤", String.format("%.0f / %.0f", p.getHealth(), p.getMaxHealth()));
            ty = line(x, ty, w, "§6⌛", sessionText());
            ty = line(x, ty, w, "§7▣", Minecraft.getDebugFPS() + " fps");
        }
        DPMemoryBar.draw(x + 4, y + h - 5, w - 8, 2);
        String mem = DPMemoryBar.label();
        if (mem != null) fr.drawStringWithShadow("§8" + mem, x + 4, y + h - 15, 0xFFFFFF);
    }

    private boolean mouseOverModel;

    private int line(int x, int y, int w, String icon, String text) {
        fontRenderer.drawStringWithShadow(icon, x + 6, y, 0xFFFFFF);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(text, w - 22), x + 18, y, 0xE8E0F0);
        return y + 11;
    }

    private String dimName() {
        try { return mc.world.provider.getDimensionType().getName().replace('_', ' '); } catch (Throwable t) { return "dimension " + mc.player.dimension; }
    }

    private String worldName() {
        try {
            if (mc.isIntegratedServerRunning()) return mc.getIntegratedServer().getWorldName();
            if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverName;
        } catch (Throwable ignored) {}
        return "";
    }

    private String sessionText() {
        long m = (System.currentTimeMillis() - sessionStart) / 60000;
        return m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
    }

    // ------------------------------------------------------------------ actions
    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (closingAt > 0) return;
        if (b.id == BACK) { close(null); return; }
        if (b.id == QUIT) { popup = true; popupAt = DPAnim.now(); initGui(); return; }
        if (b.id == POP_CANCEL) { popup = false; initGui(); return; }
        if (b.id == POP_QUIT) { for (GuiButton r : realButtons) if (r.id == QUIT) { clickReal(r); return; } return; }
        if (b.id == POP_EXIT) { mc.shutdown(); return; }
        if (b.id >= 1000) {
            GuiButton r = realButtons.get(b.id - 1000);
            later(() -> clickReal(r));
            return;
        }
        if (b.id >= PRIDE_BASE && b.id < PRIDE_BASE + prideTiles.size()) {
            String cmd = prideTiles.get(b.id - PRIDE_BASE)[1];
            if (cmd.startsWith("#config:")) { later(() -> openModConfig(cmd.substring(8))); return; }
            if (cmd.equals("#web")) { later(() -> DPWeb.open(this, DPWeb.homeUrl())); return; }
            close(() -> runCommand(cmd));
        }
    }

    /** let the press animation play for a moment before the next screen appears */
    private void later(Runnable r) {
        if (!DPConfig.animations) { r.run(); return; }
        pendingAt = DPAnim.now(); pending = r;
    }
    private Runnable pending;
    private long pendingAt;

    @Override
    public void updateScreen() {
        super.updateScreen();
        real.updateScreen();
        if (pending != null && DPAnim.now() - pendingAt >= 110) { Runnable r = pending; pending = null; r.run(); }
    }

    /**
     * Press the REAL button on the real menu the way GuiScreen.mouseClicked does — ActionPerformedEvent.Pre, the
     * menu's actionPerformed, then .Post — but without its click sound (our tile already chimed) and with .Post
     * fired even though the real menu isn't the screen showing (vanilla only posts it for the current screen,
     * and most mods listen on Post).
     */
    private void clickReal(GuiButton r) {
        try {
            List<GuiButton> list = realList();
            net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre pre =
                    new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre(real, r, list);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(pre)) return;
            GuiButton b = pre.getButton();
            GuiScreen before = mc.currentScreen;
            Method act = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
            act.invoke(real, b);
            if (mc.currentScreen == before)
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Post(real, b, list));
            if (mc.currentScreen == before) initGui();                // e.g. a toggle changed its label — show it
        } catch (Throwable t) {
            System.out.println("[Pride UI] pause-menu button '" + r.displayString + "' failed: " + t);
        }
    }

    /** a mod's own settings screen (the one its "Config" button opens in the mod list), back to this menu after */
    private void openModConfig(String modid) {
        try {
            net.minecraftforge.fml.common.ModContainer c = Loader.instance().getIndexedModList().get(modid);
            net.minecraftforge.fml.client.IModGuiFactory gf = net.minecraftforge.fml.client.FMLClientHandler.instance().getGuiFactoryFor(c);
            GuiScreen s = gf == null ? null : gf.createConfigGui(this);
            if (s != null) mc.displayGuiScreen(s);
        } catch (Throwable t) {
            System.out.println("[Pride UI] couldn't open " + modid + "'s settings: " + t);
        }
    }

    private void runCommand(String cmd) {
        if (mc.player == null) return;
        if (net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.player, cmd) == 0) mc.player.sendChatMessage(cmd);
    }

    /** slide/fade out, then back to the game (and run `then`) */
    private void close(Runnable then) {
        afterClose = then;
        DPSounds.play(DPSounds.CLOSE, 1f, 0.7f);
        if (!DPConfig.animations) { finishClose(); return; }
        closingAt = DPAnim.now();
    }

    private void finishClose() {
        mc.displayGuiScreen(null);
        mc.setIngameFocus();
        DPTransition.resumed();
        if (afterClose != null) { Runnable r = afterClose; afterClose = null; r.run(); }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        int[] b = modelBox;
        if (b != null && !popup && model.over(mx, my, b[0], b[1], b[2], b[3])) { model.press(mx, my); return; }
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long t) { model.drag(mx, my); }

    @Override
    protected void mouseReleased(int mx, int my, int state) { model.release(); super.mouseReleased(mx, my, state); }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (popup) { popup = false; DPSounds.play(DPSounds.BACK); initGui(); }
            else close(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        int emx = Mouse.getEventX() * width / mc.displayWidth, emy = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (d != 0 && modelBox != null && model.over(emx, emy, modelBox[0], modelBox[1], modelBox[2], modelBox[3])) { model.scroll(d); return; }
        if (d != 0 && contentH > gridH) {
            scroll = Math.max(0, Math.min(contentH - gridH, scroll - Integer.signum(d) * 30));
            placeTiles();
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return real.doesGuiPauseGame(); }
}
