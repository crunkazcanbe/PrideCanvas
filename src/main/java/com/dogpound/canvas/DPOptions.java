package com.dogpound.canvas;

import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;

/**
 * DogPound Options screen. Extends vanilla GuiOptions so every option/slider/button
 * keeps its real behavior (super.actionPerformed handles all of it), but draws like
 * the main menu: moving wallpaper, just the green "Options" word up top (no banner,
 * no vanilla white title), and the buttons reskinned + reflowed to the bottom.
 */
public class DPOptions extends GuiOptions {
    public DPOptions(GuiScreen parent, GameSettings settings) {
        super(parent, settings);
    }

    // her ask 2026-10-01: a Shaders button, a Distant Horizons button and a Celeritas button, always there
    private static final int SHADERS = 9100, DISTANT = 9101, CELERITAS = 9102, SHADERS_OFF = 9103;
    /** measured 2026-10-02: with AUSM loaded it draws terrain itself even with NO shader pack (Celeritas sits idle) —
     *  ~56 vs ~100 FPS in the big pack. So the shader engine is opt-in: its jar is renamed with this ending while off. */
    private static final String AUSM_OFF = ".off-shaders";
    private static final String AUSM_GUI = "com.luna.ausm.impl.client.gui.GuiShaders";
    private static final String[][] EXTRA = {   // id, label, screen class, how to open it
            {"9100", "Shaders", "com.luna.ausm.impl.client.gui.GuiShaders", "new"},
            {"9101", "Distant Horizons", "com.seibel.distanthorizons.common.wrappers.gui.GetConfigScreen", "getScreen"},
            {"9102", "Celeritas", "org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen", "new"},
    };

    @Override
    public void initGui() {
        super.initGui();                                         // vanilla builds all the buttons
        for (String[] x : EXTRA) {
            if (x[2].equals(AUSM_GUI)) continue;                 // the shader buttons are added below
            if (!classExists(x[2])) continue;                    // that mod isn't installed
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(Integer.parseInt(x[0]), 0, 0, 150, 20, x[1]));
        }
        if (classExists(AUSM_GUI)) {
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(SHADERS, 0, 0, 150, 20, "Shaders: ON"));
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(SHADERS_OFF, 0, 0, 150, 20, "Shaders off (max FPS)"));
        } else if (!ausmJars(true).isEmpty()) {
            this.buttonList.add(new net.minecraft.client.gui.GuiButton(SHADERS, 0, 0, 150, 20, "Shaders: OFF (max FPS)"));
        }
        DPSkin.reskinAll(this.buttonList);                       // translucent main-menu look
        DPChrome.reflowOptions(this.buttonList, this.width, this.height);  // bottom rows, Done in the grid
    }

    /** mods add their own buttons right after initGui (Forge's InitGuiEvent) — drop theirs if it duplicates ours */
    @Override
    public void setWorldAndResolution(net.minecraft.client.Minecraft mc, int w, int h) {
        super.setWorldAndResolution(mc, w, h);
        boolean changed = false;
        for (java.util.Iterator<net.minecraft.client.gui.GuiButton> it = this.buttonList.iterator(); it.hasNext(); ) {
            net.minecraft.client.gui.GuiButton b = it.next();
            if (b.id >= SHADERS && b.id <= SHADERS_OFF) continue;
            String l = b.displayString == null ? "" : net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(b.displayString).toLowerCase(java.util.Locale.ROOT);
            if (l.startsWith("shader") || l.contains("distant horizons")) { it.remove(); changed = true; }
        }
        if (changed) {
            DPSkin.reskinAll(this.buttonList);
            DPChrome.reflowOptions(this.buttonList, this.width, this.height);
        }
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton b) throws java.io.IOException {
        if (b.id == SHADERS && !classExists(AUSM_GUI)) { askSwitch(true); return; }
        if (b.id == SHADERS_OFF) { askSwitch(false); return; }
        for (String[] x : EXTRA) {
            if (b.id != Integer.parseInt(x[0])) continue;
            try {
                Class<?> c = Class.forName(x[2]);
                net.minecraft.client.gui.GuiScreen next = "new".equals(x[3])
                        ? (net.minecraft.client.gui.GuiScreen) c.getConstructor(net.minecraft.client.gui.GuiScreen.class).newInstance(this)
                        : (net.minecraft.client.gui.GuiScreen) c.getMethod(x[3], net.minecraft.client.gui.GuiScreen.class).invoke(null, this);
                if (next != null) this.mc.displayGuiScreen(next);
            } catch (Throwable t) {
                System.out.println("[PrideCanvas] couldn't open " + x[1] + ": " + t);
            }
            return;
        }
        super.actionPerformed(b);
    }

    /** confirm, then rename the AUSM jar(s); it takes effect at the next start */
    private void askSwitch(boolean on) {
        net.minecraft.client.gui.GuiScreen back = this;
        this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiYesNo((yes, id) -> {
            if (yes) {
                int n = 0;
                for (java.io.File f : ausmJars(on)) {
                    String name = f.getName();
                    java.io.File to = new java.io.File(f.getParentFile(), on ? name.substring(0, name.length() - AUSM_OFF.length()) : name + AUSM_OFF);
                    if (f.renameTo(to)) n++;
                }
                System.out.println("[PrideCanvas] shader engine " + (on ? "ON" : "OFF") + " for the next start (" + n + " jar)");
            }
            this.mc.displayGuiScreen(back);
        }, on ? "Turn shaders on?" : "Turn shaders off for max FPS?",
           on ? "The shader engine (AUSM) loads at the next start. It takes over drawing from Celeritas, so FPS drops even without a shader pack."
              : "At the next start Celeritas draws everything again (about twice the FPS). Turn shaders back on here any time.", 0));
    }

    /** AUSM jars in the mods folder: switched off (off=true) or loaded (off=false) */
    private static java.util.List<java.io.File> ausmJars(boolean off) {
        java.util.List<java.io.File> out = new java.util.ArrayList<>();
        java.io.File[] all = new java.io.File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "mods").listFiles();
        if (all == null) return out;
        for (java.io.File f : all) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!n.startsWith("ausm-")) continue;
            if (off ? n.endsWith(".jar" + AUSM_OFF) : n.endsWith(".jar")) out.add(f);
        }
        return out;
    }

    private static boolean classExists(String name) {
        try { Class.forName(name, false, DPOptions.class.getClassLoader()); return true; } catch (Throwable t) { return false; }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Skip super.drawScreen entirely so the vanilla dirt + white title never draw.
        DPBackdrop.draw(this.mc, this.width, this.height);
        DPGrid.draw(this, "Options");   // centred house-style panel (+ scroll bar when the tiles overflow)
        DPChrome.paintWidgets(this, this.buttonList, this.labelList, mouseX, mouseY, partialTicks);
        DPMemoryBar.draw(0, this.height - 2, this.width, 2);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        DPGrid.wheel(org.lwjgl.input.Mouse.getDWheel());   // scroll the tile grid inside the panel
    }
}
