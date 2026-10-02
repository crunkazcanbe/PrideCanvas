package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared DogPound screen chrome. Re-skins the vanilla settings screens (Options and
 * every page that opens from it) so they all match the main menu: the same moving
 * wallpaper (drawn behind them by DPMenuMod's blend event, time-synced to the same
 * moment), a top banner with that page's name in the DogPound logo font, and — on the
 * main Options page — the buttons reflowed into resizable rows along the bottom.
 *
 * Pure overlay: it never replaces the vanilla screens, so every option/slider/list
 * keeps its real behavior. Titles + logo are pre-rendered art in textures/gui/.
 */
public final class DPChrome {
    private DPChrome() {}

    private static final int BANNER_BG   = 0xFF1A0E2E;   // opaque so the vanilla white title can't bleed through
    private static final int ACCENT_LINE = 0xFFF5A9B8;
    private static final int BANNER_H    = 30;   // GUI px
    private static final int TITLE_H     = 22;   // drawn title height
    private static final int LOGO_TOP    = 10;

    private static final Map<String, int[]> DIMS = new HashMap<String, int[]>();

    /** Map a screen's class simple-name to its title art key (null = don't skin). */
    private static String key(GuiScreen g) {
        if (g == null) return null;
        String n = g.getClass().getSimpleName();
        if (n.equals("GuiOptions")) return "options";
        if (n.equals("GuiVideoSettings")) return "video";
        if (n.equals("GuiControls")) return "controls";
        if (n.equals("GuiScreenOptionsSounds")) return "sound";
        if (n.equals("GuiLanguage")) return "language";
        if (n.equals("GuiSnooper")) return "snooper";
        if (n.equals("GuiCustomizeSkin")) return "skin";
        if (n.equals("GuiScreenResourcePacks")) return "resourcepacks";
        if (n.equals("GuiModList")) return "mods";
        return null;
    }

    /** The world-generation family. These get the pride widget look, but NOT the image
     *  title banner -- there is no title_*.png for them, and a missing texture renders as
     *  the magenta/black checker. */
    private static boolean isWorldGen(GuiScreen g) {
        if (g == null) return false;
        String n = g.getClass().getSimpleName();
        return n.equals("GuiCreateWorld") || n.equals("DPCreateWorld")
                || n.equals("GuiCreateFlatWorld") || n.equals("GuiFlatPresets")
                || n.equals("GuiCustomizeWorldScreen") || n.equals("GuiScreenCustomizePresets");
    }

    /** Every vanilla/Forge sub-screen that gets Pride buttons + backdrop, on the title screen AND in-game
     *  (her ask 2026-09-30: "theme all the sub menus… any sub menu you can find"). An allowlist on purpose:
     *  containers, chat, books, signs and command blocks keep their own look. */
    private static final java.util.Set<String> THEMABLE = new java.util.HashSet<String>(java.util.Arrays.asList(
            "GuiOptions", "GuiVideoSettings", "GuiControls", "GuiScreenOptionsSounds", "GuiLanguage", "GuiSnooper",
            "GuiCustomizeSkin", "GuiScreenResourcePacks", "ScreenChatOptions", "GuiShareToLan", "GuiStats",
            "GuiScreenAdvancements", "GuiModList", "GuiConfig", "GuiCreateWorld", "GuiCreateFlatWorld", "GuiFlatPresets",
            "GuiCustomizeWorldScreen", "GuiScreenCustomizePresets", "GuiWorldEdit", "GuiYesNo", "GuiGameOver",
            "GuiDisconnected", "GuiErrorScreen", "GuiScreenAddServer", "GuiScreenServerList", "GuiConfirmOpenLink",
            "GuiScreenWorking", "GuiMultiplayer", "GuiWorldSelection", "GuiIngameModOptions", "GuiScrollingList"));

    public static boolean themable(GuiScreen g) {
        return g != null && (THEMABLE.contains(g.getClass().getSimpleName()) || g instanceof net.minecraftforge.fml.client.config.GuiConfig);
    }

    /** True if this screen's widgets should be reskinned (superset of shouldSkin). */
    public static boolean shouldSkinWidgets(GuiScreen g) {
        return shouldSkin(g) || isWorldGen(g) || themable(g);
    }

    /** True if this screen should get the DogPound title banner. */
    public static boolean shouldSkin(GuiScreen g) {
        return key(g) != null;
    }

    /** True if this screen's buttons should be reflowed to the bottom (Options only). */
    public static boolean shouldReflow(GuiScreen g) {
        return g != null && "GuiOptions".equals(g.getClass().getSimpleName());
    }

    private static int[] dims(Minecraft mc, ResourceLocation rl) {
        int[] c = DIMS.get(rl.toString());
        if (c != null) return c;
        try {
            InputStream in = mc.getResourceManager().getResource(rl).getInputStream();
            BufferedImage img = ImageIO.read(in);
            in.close();
            c = new int[]{img.getWidth(), img.getHeight()};
        } catch (Throwable t) {
            c = new int[]{120, 24};
        }
        DIMS.put(rl.toString(), c);
        return c;
    }

    private static void drawArt(Minecraft mc, ResourceLocation rl, int cx, int top, int targetH, int maxW) {
        int[] d = dims(mc, rl);
        int tw = (int) ((float) targetH * d[0] / d[1]);
        int th = targetH;
        if (tw > maxW) { th = (int) ((float) maxW * d[1] / d[0]); tw = maxW; }
        int x = cx - tw / 2;
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(rl);
        Gui.drawScaledCustomSizeModalRect(x, top, 0.0F, 0.0F, d[0], d[1], tw, th, d[0], d[1]);
    }

    /** The brand logo for the main menu (DogPound / VERSION 3). */
    public static void drawBrand(GuiScreen g) {
        drawBrand(Minecraft.getMinecraft(), g.width);
    }

    /** Same brand logo, positioned by explicit width — used by the loading screen (no GuiScreen)
     *  so the DogPound name sits in the exact same spot as on the main menu. */
    public static void drawBrand(Minecraft mc, int width) {
        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "textures/gui/logo.png");
        int targetW = Math.min((int) (width * 0.42F), 360);
        int[] d = dims(mc, rl);
        int targetH = (int) ((float) targetW * d[1] / d[0]);
        drawArt(mc, rl, width / 2, LOGO_TOP, targetH, width - 20);
    }

    /** Just the page's name in the logo font at the very top — no banner, no line. */
    public static void drawBareTitle(GuiScreen g, String key) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "textures/gui/title_" + key + ".png");
        drawArt(mc, rl, g.width / 2, 8, 26, g.width - 24);
    }

    /** Paint a screen's buttons + labels (used by our custom screens that skip super.drawScreen). */
    public static void paintWidgets(GuiScreen g, java.util.List<GuiButton> buttons,
                                    java.util.List<net.minecraft.client.gui.GuiLabel> labels,
                                    int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (buttons != null) {
            for (GuiButton b : buttons) b.drawButton(mc, mouseX, mouseY, partialTicks);
        }
        if (labels != null) {
            for (net.minecraft.client.gui.GuiLabel l : labels) l.drawLabel(mc, mouseX, mouseY);
        }
    }

    /** Top banner + this page's name, covering the vanilla title. */
    public static void drawTitle(GuiScreen g) {
        String k = key(g);
        if (k == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, g.width, BANNER_H, BANNER_BG);
        Gui.drawRect(0, BANNER_H - 1, g.width, BANNER_H, ACCENT_LINE);
        ResourceLocation rl = new ResourceLocation(DPMenuMod.MODID, "textures/gui/title_" + k + ".png");
        drawArt(mc, rl, g.width / 2, (BANNER_H - TITLE_H) / 2, TITLE_H, g.width - 24);
    }

    /**
     * Reflow the Options buttons into resizable rows pinned to the bottom (like the
     * main menu bar). Done (id 200) goes LAST so it fills the empty trailing slot of
     * the grid — same size as the rest, no extra row.
     */
    public static void reflowOptions(List<GuiButton> buttons, int width, int height) {
        if (buttons == null || buttons.isEmpty()) return;

        // Route the mod-added extras (PymTech Addons, DogPound Inv Tweaks) to the RIGHT column;
        // everything else fills the two bottom rows. Done lands last in the bottom rows.
        java.util.List<GuiButton> bottom = new java.util.ArrayList<GuiButton>();
        java.util.List<GuiButton> rightCol = new java.util.ArrayList<GuiButton>();
        GuiButton done = null;
        for (GuiButton b : buttons) {
            if (b.id == 200) { done = b; continue; }
            String d = (b.displayString == null) ? "" : b.displayString.toLowerCase();
            if (d.contains("pymtech") || d.contains("pym") || d.contains("tweak") || d.contains("addon"))
                rightCol.add(b);
            else
                bottom.add(b);
        }
        if (done != null) bottom.add(done);
        if (bottom.isEmpty() && rightCol.isEmpty()) return;

        // Full inventory grid: 2 rows across the bottom + right column, blanks filled with icons.
        DPGrid.layout(bottom, rightCol, width, height);
    }
}
