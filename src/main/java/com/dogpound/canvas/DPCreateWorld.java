package com.dogpound.canvas;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Pride "Create World" (rebuilt 2026-09-30 — her words: "the world creation, I know that's not changed").
 * Extends vanilla GuiCreateWorld so every rule stays vanilla's (name → folder, seed parsing, game-mode cycling,
 * world types, customize screens, hardcore, cheats), but nothing of vanilla's is drawn: a centred PrideFrame
 * under the logo with two tabs —
 *   ✦ Basics        world name, where it's saved, the game mode tile + what that mode means
 *   ⚙ More Options  seed, structures, world type, cheats, bonus chest, customize
 * — and Create / Cancel along the bottom. Switching tabs presses vanilla's "More World Options" button, so
 * vanilla's own state is always the truth; this screen only moves widgets and paints them.
 */
public class DPCreateWorld extends GuiCreateWorld {
    private static final int CREATE = 0, CANCEL = 1, MODE = 2, MORE = 3, STRUCTURES = 4, TYPE = 5, CHEATS = 6, BONUS = 7, CUSTOMIZE = 8;

    private GuiTextField nameField, seedField;
    private long openedAt, pageAt;
    private boolean lastMore;

    public DPCreateWorld(GuiScreen parent) {
        super(parent);
    }

    private boolean more() { return bool("inMoreWorldOptionsDisplay", "field_146344_y"); }

    private PrideFrame frame() {
        PrideFrame f = PrideFrame.sized(this.width, this.height, 430, 250);
        int logoW = Math.min((int) (this.width * 0.42F), 360), logoBottom = 10 + logoW * 300 / 939;
        int y = Math.max(f.y, logoBottom + 4);
        y = Math.max(0, Math.min(y, this.height - f.h - 4));
        return f.moveTo(f.x, y);
    }

    @Override
    public void initGui() {
        super.initGui();                                       // vanilla builds its fields + buttons
        if (openedAt == 0) { openedAt = DPAnim.now(); pageAt = openedAt; DPSounds.play(DPSounds.OPEN, 1.1f, 0.6f); }
        DPSkin.reskinAll(this.buttonList, this);               // Pride tiles; the screen's own fields follow the swap
        nameField = (GuiTextField) get("worldNameField", "field_146333_g");
        seedField = (GuiTextField) get("worldSeedField", "field_146335_h");
        lastMore = more();
        layout();
    }

    /** put every widget in its place for the current page */
    private void layout() {
        PrideFrame f = frame();
        boolean more = more();
        int x = f.cx + 6, w = f.cw - 12, top = f.cy + 22;       // under the tabs
        for (GuiTextField t : new GuiTextField[]{nameField, seedField}) {
            if (t == null) continue;
            t.setEnableBackgroundDrawing(false);
            t.x = x + 6; t.width = w - 12; t.height = 12;
        }
        if (nameField != null) nameField.y = top + 16;
        if (seedField != null) seedField.y = top + 16;

        int tileTop = top + 44, gap = 6;
        for (GuiButton b : buttonList) {
            if (b instanceof DPButton) ((DPButton) b).autoFlat = false;
            switch (b.id) {
                case MORE: b.visible = false; break;                       // the tabs do this now
                case MODE:
                    b.visible = !more;
                    b.x = x; b.y = tileTop; b.width = Math.min(150, w / 2 - 4); b.height = 56;
                    break;
                case STRUCTURES: case TYPE: case CHEATS: case BONUS: case CUSTOMIZE: {
                    int i = b.id - STRUCTURES, cols = w >= 330 ? 5 : 3;
                    int tw = (w - (cols - 1) * gap) / cols;
                    b.x = x + (i % cols) * (tw + gap); b.y = tileTop + (i / cols) * (50 + gap);
                    b.width = tw; b.height = 50;
                    if (b.id != CUSTOMIZE) b.visible = more;                  // CUSTOMIZE: vanilla decides (only some world types)
                    break;
                }
                case CREATE: case CANCEL: {
                    int bw = (w - gap) / 2;
                    b.x = x + (b.id == CREATE ? 0 : bw + gap); b.y = f.cy + f.ch - 22; b.width = bw; b.height = 22;
                    if (b instanceof DPButton) { ((DPButton) b).flat = true; ((DPButton) b).sound(b.id == CREATE ? DPSounds.CONFIRM : DPSounds.BACK); }
                    break;
                }
                default:                                                         // a mod's extra button: tile row under ours
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);
        if (this.mc.currentScreen != this) return;
        if (more() != lastMore) { lastMore = more(); pageAt = DPAnim.now(); }
        layout();                                                              // vanilla may have changed labels/visibility
    }

    // ------------------------------------------------------------------ drawing (vanilla's drawScreen is never called)
    @Override
    public void drawScreen(int mx, int my, float pt) {
        DPBackdrop.draw(this.mc, this.width, this.height);
        DPChrome.drawBrand(this.mc, this.width);
        if (DPConfig.sparkles && this.mc.world == null) DPAnim.sparkles(this.width, this.height, 22);

        boolean anim = DPConfig.animations, more = more();
        float open = anim ? DPAnim.easeOutBack(DPAnim.progress(openedAt, 0, 320)) : 1;
        PrideFrame f = frame();
        GlStateManager.pushMatrix();
        float cx = f.x + f.w / 2f, cy = f.y + f.h / 2f;
        GlStateManager.translate(cx, cy + (1 - open) * 16, 0);
        GlStateManager.scale(0.92f + 0.08f * open, 0.92f + 0.08f * open, 1);
        GlStateManager.translate(-cx, -cy, 0);

        f.drawOver("Create New World", more ? "more options" : "the basics");
        drawTabs(f, mx, my, more);

        // page content slides in from the side it came from
        float page = anim ? DPAnim.easeOutCubic(DPAnim.progress(pageAt, 0, 260)) : 1;
        GlStateManager.pushMatrix();
        GlStateManager.translate((1 - page) * (more ? 30 : -30), 0, 0);
        FontRenderer fr = this.fontRenderer;
        int x = f.cx + 6, w = f.cw - 12, top = f.cy + 22;
        if (!more) {
            label("✦ World Name", x, top + 2);
            field(nameField, x, top + 11, w);
            String dir = (String) get("saveDirName", "field_146336_i");
            if (dir != null) fr.drawStringWithShadow("§8saved as §7" + dir, x + w - fr.getStringWidth("saved as " + dir), top + 2, 0xFFFFFF);
            GuiButton mode = find(MODE);
            if (mode != null) {
                int dx = mode.x + mode.width + 10, dw = x + w - dx;
                String d1 = (String) get("gameModeDesc1", "field_146323_G"), d2 = (String) get("gameModeDesc2", "field_146328_H");
                int ty = mode.y + 6;
                fr.drawStringWithShadow("§d§l" + modeName(), dx, ty, 0xFFFFFF);
                ty += 13;
                for (String d : new String[]{d1, d2})
                    if (d != null) for (String l : fr.listFormattedStringToWidth(d, dw)) { fr.drawStringWithShadow("§7" + l, dx, ty, 0xFFFFFF); ty += 10; }
            }
        } else {
            label("✦ Seed", x, top + 2);
            fr.drawStringWithShadow("§8blank = random", x + w - fr.getStringWidth("blank = random"), top + 2, 0xFFFFFF);
            field(seedField, x, top + 11, w);
        }
        for (GuiButton b : buttonList) {
            if (!b.visible || b.id == CREATE || b.id == CANCEL) continue;
            float pop = anim ? DPAnim.easeOutBack(DPAnim.progress(pageAt, 40 + (b.id % 5) * 35L, 260)) : 1;
            GlStateManager.pushMatrix();
            float bx = b.x + b.width / 2f, by = b.y + b.height / 2f;
            GlStateManager.translate(bx, by, 0);
            GlStateManager.scale(pop, pop, 1);
            GlStateManager.translate(-bx, -by, 0);
            b.drawButton(this.mc, mx, my, pt);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();

        for (GuiButton b : buttonList) if (b.visible && (b.id == CREATE || b.id == CANCEL)) b.drawButton(this.mc, mx, my, pt);
        GlStateManager.popMatrix();
        DPMemoryBar.draw(0, this.height - 2, this.width, 2);
    }

    private void drawTabs(PrideFrame f, int mx, int my, boolean more) {
        String[] names = {"✦ Basics", "⚙ More Options"};
        int tx = f.cx + 6;
        for (int i = 0; i < 2; i++) {
            int tw = this.fontRenderer.getStringWidth(names[i]) + 16;
            boolean on = (i == 1) == more, over = mx >= tx && my >= f.cy && mx < tx + tw && my < f.cy + 16;
            float glow = DPConfig.animations ? DPAnim.approach(names[i], on ? 1f : over ? 0.5f : 0f, 12f) : (on ? 1 : 0);
            PrideFrame.tile(tx, f.cy, tw, 16, PrideFrame.RAINBOW[i == 0 ? 7 : 6], over, false);
            if (glow > 0.01f) Gui.drawRect(tx, f.cy, tx + tw, f.cy + 16, ((int) (140 * glow) << 24) | 0x6A3FA0);
            this.fontRenderer.drawStringWithShadow(names[i], tx + 8, f.cy + 4, on ? 0xFFFFFF : 0xCFC6DD);
            tx += tw + 4;
        }
    }

    private void label(String s, int x, int y) { this.fontRenderer.drawStringWithShadow("§d" + s, x, y, 0xFFFFFF); }

    /** a Pride text box: dark glass, pink edge that glows while you type in it */
    private void field(GuiTextField t, int x, int y, int w) {
        if (t == null) return;
        float focus = DPConfig.animations ? DPAnim.approach(t, t.isFocused() ? 1f : 0f, 10f) : (t.isFocused() ? 1 : 0);
        Gui.drawRect(x, y, x + w, y + 18, 0xB0140E22);
        int edge = ((int) (120 + 135 * focus) << 24) | 0xF5A9B8;
        Gui.drawRect(x, y, x + w, y + 1, edge);
        Gui.drawRect(x, y + 17, x + w, y + 18, edge);
        Gui.drawRect(x, y, x + 1, y + 18, edge);
        Gui.drawRect(x + w - 1, y, x + w, y + 18, edge);
        if (focus > 0.01f) DPStyle.glow(x, y, w, 18, 0xF5A9B8, (int) (80 * focus), 2);
        t.y = y + 5;
        t.drawTextBox();
    }

    private String modeName() {
        String m = (String) get("gameMode", "field_146342_r");
        if (m == null) return "";
        return m.substring(0, 1).toUpperCase() + m.substring(1);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        // the two tabs: a tab that isn't open presses vanilla's "More World Options"
        PrideFrame f = frame();
        int tx = f.cx + 6;
        for (int i = 0; i < 2; i++) {
            int tw = this.fontRenderer.getStringWidth(i == 0 ? "✦ Basics" : "⚙ More Options") + 16;
            if (mx >= tx && my >= f.cy && mx < tx + tw && my < f.cy + 16) {
                if ((i == 1) != more()) {
                    DPSounds.play(DPSounds.CLICK);
                    GuiButton b = find(MORE);
                    if (b != null) actionPerformed(b);
                }
                return;
            }
            tx += tw + 4;
        }
        super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ reflection helpers (vanilla's private state)
    private GuiButton find(int id) { for (GuiButton b : buttonList) if (b.id == id) return b; return null; }

    private Object get(String dev, String srg) {
        try {
            Field fl = ReflectionHelper.findField(GuiCreateWorld.class, dev, srg);
            return fl.get(this);
        } catch (Throwable t) { return null; }
    }

    private boolean bool(String dev, String srg) { Object o = get(dev, srg); return o instanceof Boolean && (Boolean) o; }
}
