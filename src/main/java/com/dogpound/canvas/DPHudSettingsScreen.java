package com.dogpound.canvas;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Esc menu > HUD Settings: every Pride HUD option, page by page (requested feature). */
public class DPHudSettingsScreen extends GuiScreen {
    private final GuiScreen parent;
    public DPHudSettingsScreen(GuiScreen parent) { this.parent = parent; }
    @Override public boolean doesGuiPauseGame() { return false; }

    static final String[][] TABS = {
            {"Theme", "17 looks for the whole HUD"}, {"Layout", "size, height, band, animations"}, {"Player card", "face, name, level, compass"},
            {"World card", "clock, coords, biome, fps, weather"}, {"Vitals", "hearts, food, XP"}, {"Effects", "potion effects in the HUD"},
            {"Achievements", "unlocks shown in the HUD"}, {"Hotbar & chat", "matching hotbar, chat above"}, {"Mods", "every mod that draws on screen"},
            {"Info cards", "speed, compass, tool wear, home..."}, {"Performance", "speed and video memory"}};
    private static int tab = 0;
    static final String[] BAR_COLOURS = { "Theme", "Black", "Night", "Purple", "Pink", "Trans blue", "Trans pink", "Blue", "Green", "Red", "White" };
    private static final int[] BAR_RGB = { -1, 0x000000, 0x140E22, 0x5B2A86, 0xFF7FAE, 0x5BCEFA, 0xF5A9B8, 0x1E3A6E, 0x1F5E3A, 0x6E1E2A, 0xFFFFFF };
    static String barColourName(int rgb) { for (int i = 0; i < BAR_RGB.length; i++) if (BAR_RGB[i] == rgb) return BAR_COLOURS[i]; return "Theme"; }
    static int barColourValue(String n) { for (int i = 0; i < BAR_COLOURS.length; i++) if (BAR_COLOURS[i].equals(n)) return BAR_RGB[i]; return -1; }
    /** opens straight on a tab (the theme pickers' "All HUD options" button) */
    static DPHudSettingsScreen at(net.minecraft.client.gui.GuiScreen parent, int t) { tab = t; return new DPHudSettingsScreen(parent); }
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int px, pw, oy, top, bottom, scroll, contentH;
    private String hint = "";
    private String dragKey; private int dragX, dragW, dragMin, dragMax; private Consumer<Integer> dragSet;

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear(); hint = "";
        DPHudSettings S = DPHudSettings.get();
        PrideFrame f = PrideFrame.fit(width, height);
        f.draw(this, "HUD Settings", "§7theme: §d" + themeName());
        int tw = Math.min(130, Math.max(96, f.cw / 6)), ty = f.cy, th = Math.max(16, Math.min(28, (f.ch - 30) / TABS.length - 3));
        for (int i = 0; i < TABS.length; i++) {
            boolean over = in(mx, my, f.cx, ty, tw, th), on = tab == i;
            PrideFrame.tile(f.cx, ty, tw, th, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, on);
            fontRenderer.drawStringWithShadow(TABS[i][0], f.cx + 6, ty + (th > 22 ? 4 : (th - 8) / 2f), on ? 0xFFFFFF : 0xD8D0E8);
            if (th > 22) fontRenderer.drawString(fontRenderer.trimStringToWidth(TABS[i][1], tw - 10), f.cx + 6, ty + 15, 0x8A8499);
            if (over) hint = TABS[i][1];
            final int k = i;
            hits.add(new Object[]{ f.cx, ty, tw, th, (Runnable) () -> { tab = k; scroll = 0; } });
            ty += th + 3;
        }
        px = f.cx + tw + 12; pw = f.cx + f.cw - px - 6; top = f.cy; bottom = f.cy + f.ch - 30;
        PrideFrame.card(px - 6, top - 2, pw + 12, bottom - top + 4, PrideFrame.RAINBOW[tab % PrideFrame.RAINBOW.length]);
        PrideFrame.clip(px - 6, top, pw + 12, bottom - top);
        oy = top + 6 - scroll;
        int start = hits.size();
        page(mx, my, S);
        contentH = oy + scroll - top;
        PrideFrame.unclip();
        for (int i = hits.size() - 1; i >= start; i--) { Object[] h = hits.get(i); int y = (Integer) h[1]; if (y + (Integer) h[3] < top || y > bottom) hits.remove(i); }
        PrideFrame.scrollbar(px + pw + 3, top, bottom - top, scroll, bottom - top, contentH);
        int by = f.y + f.h - 24;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint.isEmpty() ? "§8Every change saves right away. Hover anything to see what it does." : "§7" + hint, f.cw - 190), f.cx, by + 5, 0xFFFFFF);
        button(mx, my, f.cx + f.cw - 180, by, 96, 18, "Reset all", 0xFF8A2238, () -> { DPHudSettings.reset(); }, "Put every HUD option back to how it started");
        button(mx, my, f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, () -> mc.displayGuiScreen(parent), null);
        super.drawScreen(mx, my, pt);
    }

    private String themeName() { for (String[] t : DPHudThemes.LIST) if (t[0].equals(DPConfig.hudTheme)) return t[1]; return DPConfig.hudTheme; }

    private void page(int mx, int my, DPHudSettings S) {
        switch (tab) {
            case 0:
                head("Theme", "How the whole HUD looks: colours, the middle card, decorations and the hotbar.");
                button(mx, my, px, oy, 200, 18, "✦ Open the theme gallery", PrideFrame.BUTTON, () -> mc.displayGuiScreen(new DPHudThemeScreen(this)), "Every theme as a live preview"); oy += 24;
                chips(mx, my, "Quick pick", themeIds(), () -> DPConfig.hudTheme, v -> { DPConfig.hudTheme = v; syncCfg(); DPCrosshairConfig.hudThemeChanged(v); }, "Switch theme right here");
                tog(mx, my, "Decorations on the strip (sprinkles, clouds, paws...)", () -> S.decorations, () -> S.decorations = !S.decorations);
                tog(mx, my, "The crosshair matches the HUD theme", () -> DPCrosshairConfig.get().followHud, () -> { DPCrosshairConfig c = DPCrosshairConfig.get(); c.followHud = !c.followHud; c.save(); });
                break;
            case 1:
                head("Layout", "Size and look of the strip.");
                tog(mx, my, "Pride HUD on (F9)", () -> DPConfig.hudEnabled, () -> { DPConfig.hudEnabled = !DPConfig.hudEnabled; syncCfg(); });
                stepper(mx, my, "HUD size: §f" + DPConfig.hudScale + "x", () -> { DPConfig.hudScale = Math.max(.5, DPConfig.hudScale - .25); syncCfg(); }, () -> { DPConfig.hudScale = Math.min(6, DPConfig.hudScale + .25); syncCfg(); }, "1 = your normal GUI scale (Page Up / Page Down in game)");
                slider(mx, my, "Strip height", "h", 40, 96, () -> S.stripHeight, v -> S.stripHeight = v, "How tall the strip is");
                slider(mx, my, "Background see-through %", "o", 0, 100, () -> DPConfig.hudOpacity, v -> { DPConfig.hudOpacity = v; syncCfg(); }, "0 = invisible background, 100 = solid");
                tog(mx, my, "One colour bar (no gradient)", () -> S.solidBar, () -> S.solidBar = !S.solidBar);
                chips(mx, my, "Bar colour", BAR_COLOURS, () -> barColourName(S.barColor), v -> S.barColor = barColourValue(v), "Theme = each theme's own colour");
                slider(mx, my, "Whole HUD see-through % (every theme)", "bo", 0, 100, () -> S.barOpacity, v -> S.barOpacity = v, "50 = half see-through (default)");
                tog(mx, my, "Theme pictures move", () -> S.animateDecorations, () -> S.animateDecorations = !S.animateDecorations);
                slider(mx, my, "Cards see-through % (-1 = same as the bar)", "co", -1, 100, () -> S.cardOpacity, v -> S.cardOpacity = v, "The cards on the strip");
                tog(mx, my, "Strip at the TOP of the screen", () -> S.stripAtTop, () -> S.stripAtTop = !S.stripAtTop);
                slider(mx, my, "Strip width (% of the screen)", "sw", 30, 100, () -> S.stripWidth, v -> S.stripWidth = v, "Narrower = centred in the middle");
                tog(mx, my, "Middle card (hearts, food, XP)", () -> S.middleCard, () -> S.middleCard = !S.middleCard);
                chips(mx, my, "Cards fill", new String[]{ "Both sides", "Right", "Left" }, () -> new String[]{ "Both sides", "Right", "Left" }[Math.max(0, Math.min(2, S.cardSide))],
                        v -> S.cardSide = "Right".equals(v) ? 1 : "Left".equals(v) ? 2 : 0, "Which side of the middle card new cards go");
                slider(mx, my, "Space between cards", "cg", 0, 16, () -> S.cardGap, v -> S.cardGap = v, "Pixels between cards");
                tog(mx, my, "Text shadows", () -> S.textShadow, () -> S.textShadow = !S.textShadow);
                tog(mx, my, "Hide the HUD while a menu is open", () -> S.hideInMenus, () -> S.hideInMenus = !S.hideInMenus);
                tog(mx, my, "Hide the HUD in third person", () -> S.hideThirdPerson, () -> S.hideThirdPerson = !S.hideThirdPerson);
                tog(mx, my, "Hide the HUD in creative", () -> S.hideCreative, () -> S.hideCreative = !S.hideCreative);
                tog(mx, my, "Rainbow band on top", () -> S.rainbowBand, () -> S.rainbowBand = !S.rainbowBand);
                tog(mx, my, "Band shimmers", () -> S.bandShimmer, () -> S.bandShimmer = !S.bandShimmer);
                tog(mx, my, "Red flash when you get hurt", () -> S.hurtFlash, () -> S.hurtFlash = !S.hurtFlash);
                tog(mx, my, "Red glow when health is low", () -> S.lowHealthGlow, () -> S.lowHealthGlow = !S.lowHealthGlow);
                tog(mx, my, "Cards fade in", () -> S.cardFade, () -> S.cardFade = !S.cardFade);
                tog(mx, my, "Page dots when cards don't fit", () -> S.pageDots, () -> S.pageDots = !S.pageDots);
                slider(mx, my, "Flip pages by itself (seconds, 0 = off)", "ap", 0, 30, () -> S.autoPageSec, v -> S.autoPageSec = v, "Left / Right arrows always flip pages too");
                tog(mx, my, "Player card (left end)", () -> DPConfig.hudShowPlayer, () -> { DPConfig.hudShowPlayer = !DPConfig.hudShowPlayer; syncCfg(); });
                tog(mx, my, "World card (right end)", () -> DPConfig.hudShowWorld, () -> { DPConfig.hudShowWorld = !DPConfig.hudShowWorld; syncCfg(); });
                break;
            case 2:
                head("Player card", "The card at the left end.");
                tog(mx, my, "Your face", () -> S.pFace, () -> S.pFace = !S.pFace);
                tog(mx, my, "Your name", () -> S.pName, () -> S.pName = !S.pName);
                tog(mx, my, "Level", () -> S.pLevel, () -> S.pLevel = !S.pLevel);
                tog(mx, my, "Game mode", () -> S.pMode, () -> S.pMode = !S.pMode);
                tog(mx, my, "Compass direction", () -> S.pFacing, () -> S.pFacing = !S.pFacing);
                tog(mx, my, "Little health bar", () -> S.pHealthBar, () -> S.pHealthBar = !S.pHealthBar);
                break;
            case 3:
                head("World card", "The card at the right end.");
                tog(mx, my, "Clock", () -> S.wClock, () -> S.wClock = !S.wClock);
                tog(mx, my, "Day/night bar with the sun and moon", () -> S.wDayBar, () -> S.wDayBar = !S.wDayBar);
                tog(mx, my, "24-hour clock", () -> DPConfig.hudClock24, () -> { DPConfig.hudClock24 = !DPConfig.hudClock24; syncCfg(); });
                tog(mx, my, "Day number", () -> S.wDay, () -> S.wDay = !S.wDay);
                tog(mx, my, "Coordinates", () -> S.wCoords, () -> S.wCoords = !S.wCoords);
                tog(mx, my, "Biome", () -> S.wBiome, () -> S.wBiome = !S.wBiome);
                tog(mx, my, "FPS", () -> S.wFps, () -> S.wFps = !S.wFps);
                tog(mx, my, "Weather", () -> S.wWeather, () -> S.wWeather = !S.wWeather);
                tog(mx, my, "Light level", () -> S.wLight, () -> S.wLight = !S.wLight);
                tog(mx, my, "Dimension", () -> S.wDimension, () -> S.wDimension = !S.wDimension);
                tog(mx, my, "Ping (on servers)", () -> S.wPing, () -> S.wPing = !S.wPing);
                break;
            case 4:
                head("Vitals", "Hearts, food, water, armour and XP in the middle card.");
                tog(mx, my, "XP bar with your level", () -> S.xpBar, () -> S.xpBar = !S.xpBar);
                tog(mx, my, "With the Minecraft theme: show Scaling Health / Feast / AppleSkin exactly as they draw", () -> DPConfig.hudVitalsFromMods, () -> { DPConfig.hudVitalsFromMods = !DPConfig.hudVitalsFromMods; syncCfg(); });
                info("§7Every other theme reads the real numbers: Scaling Health's extra hearts, Scaling Feast's bigger hunger, Tough As Nails' thirst and temperature, Player Mana's mana.");
                break;
            case 5:
                head("Effects", "Potion effects live in the HUD instead of the top-right corner.");
                tog(mx, my, "Show effects in the HUD", () -> S.effects, () -> S.effects = !S.effects);
                tog(mx, my, "Hide Minecraft's top-right effect icons", () -> S.hideVanillaEffects, () -> S.hideVanillaEffects = !S.hideVanillaEffects);
                tog(mx, my, "Time left under each effect", () -> S.effectTimers, () -> S.effectTimers = !S.effectTimers);
                tog(mx, my, "Blink when almost over", () -> S.effectBlink, () -> S.effectBlink = !S.effectBlink);
                tog(mx, my, "Bad effects first", () -> S.effectsBadFirst, () -> S.effectsBadFirst = !S.effectsBadFirst);
                slider(mx, my, "Most effects shown", "em", 1, 24, () -> S.effectsMax, v -> S.effectsMax = v, "How many effect icons fit in the card");
                break;
            case 6:
                head("Achievements", "Unlocks show as a card in the HUD; the top-right pop-ups are gone.");
                tog(mx, my, "Show achievements in the HUD", () -> S.achievements, () -> S.achievements = !S.achievements);
                tog(mx, my, "Little sound when one shows", () -> S.achSound, () -> S.achSound = !S.achSound);
                slider(mx, my, "Seconds each one stays", "as", 2, 20, () -> S.achSeconds, v -> S.achSeconds = v, "They queue up and show one after another");
                tog(mx, my, "When the HUD is off: still show the normal pop-up", () -> DPConfig.toastAdvancements, () -> { DPConfig.toastAdvancements = !DPConfig.toastAdvancements; syncCfg(); });
                break;
            case 7:
                head("Hotbar & chat", "Make them part of the HUD.");
                tog(mx, my, "Hotbar wears the HUD theme", () -> DPConfig.hudThemeHotbar, () -> { DPConfig.hudThemeHotbar = !DPConfig.hudThemeHotbar; syncCfg(); });
                tog(mx, my, "Hotbar grows with the HUD size", () -> DPConfig.hudScaleHotbar, () -> { DPConfig.hudScaleHotbar = !DPConfig.hudScaleHotbar; syncCfg(); });
                tog(mx, my, "Chat sits above the HUD", () -> S.chatAbove, () -> S.chatAbove = !S.chatAbove);
                break;
            case 8:
                head("Mods", "Every mod that draws on your screen. Click to switch: in the HUD → draws normally → hidden.");
                tog(mx, my, "Show mod cards", () -> S.modCards, () -> S.modCards = !S.modCards);
                tog(mx, my, "Mod names on the cards", () -> S.modNames, () -> S.modNames = !S.modNames);
                tog(mx, my, "Minimap big, above the HUD on the right", () -> S.mapAbove, () -> S.mapAbove = !S.mapAbove);
                slider(mx, my, "Minimap size (% of screen height)", "ms", 10, 60, () -> S.mapSize, v -> S.mapSize = v, "How big the minimap above the HUD is");
                tog(mx, my, "Minimap on the LEFT instead", () -> S.mapLeft, () -> S.mapLeft = !S.mapLeft);
                slider(mx, my, "Minimap see-through %", "mo", 10, 100, () -> S.mapOpacity, v -> S.mapOpacity = v, "100 = solid");
                tog(mx, my, "Frame round the minimap", () -> S.mapBorder, () -> S.mapBorder = !S.mapBorder);
                tog(mx, my, "Words under the minimap (coords, biome...)", () -> S.mapWords, () -> S.mapWords = !S.mapWords);
                tog(mx, my, "Minimaps fill a square card (words beside)", () -> S.mapSquare, () -> S.mapSquare = !S.mapSquare);
                oy += 4;
                List<String[]> mods = DPHud.modList();
                if (mods.isEmpty()) info("§8Join a world first, then the list fills in.");
                for (String[] m : mods) {
                    int mode = S.mode(m[0]);
                    boolean over = in(mx, my, px, oy, pw, 15);
                    Gui.drawRect(px, oy, px + pw, oy + 15, over ? 0x40FFFFFF : 0x18FFFFFF);
                    fontRenderer.drawStringWithShadow((m[2].equals("drawing") ? "§a● " : "§8○ ") + "§f" + m[1] + " §8" + m[0], px + 4, oy + 4, 0xFFFFFF);
                    String st = DPHudSettings.MODE_NAMES[mode];
                    fontRenderer.drawStringWithShadow(st, px + pw - 6 - fontRenderer.getStringWidth(st), oy + 4, 0xFFFFFF);
                    if (over) hint = m[2].equals("drawing") ? "Drawing something right now" : "Hooked in, but drawing nothing at the moment";
                    hits.add(new Object[]{ px, oy, pw, 15, (Runnable) () -> { int n = (S.mode(m[0]) + 1) % 3; if (n == 0) S.modes.remove(m[0]); else S.modes.put(m[0], n); } });
                    oy += 17;
                }
                break;
            case 9:
                head("Info cards", "Extra cards for the strip. Each one only shows while it has something to say.");
                DPHudWidgets.init();
                chips(mx, my, "Speed in", new String[]{ "blocks/s", "km/h", "mph" }, () -> new String[]{ "blocks/s", "km/h", "mph" }[Math.max(0, Math.min(2, S.speedUnit))],
                        v -> S.speedUnit = "km/h".equals(v) ? 1 : "mph".equals(v) ? 2 : 0, "The Speed card");
                tog(mx, my, "24-hour clocks", () -> DPConfig.hudClock24, () -> { DPConfig.hudClock24 = !DPConfig.hudClock24; syncCfg(); });
                slider(mx, my, "Info card text size %", "its", 60, 160, () -> S.infoTextScale, v -> S.infoTextScale = v, "Bigger = easier to read, wider cards");
                slider(mx, my, "Small text size % (every card)", "sts", 40, 100, () -> S.smallTextScale, v -> S.smallTextScale = v, "The little lines on the player, world and mod cards");
                tog(mx, my, "Coloured bars on the cards", () -> S.infoBars, () -> S.infoBars = !S.infoBars);
                tog(mx, my, "§aTurn ALL info cards on", () -> false, () -> { for (DPHudWidgets.Widget w : DPHudWidgets.ALL) S.widgets.put(w.id, true); });
                tog(mx, my, "§cTurn ALL info cards off", () -> false, () -> { for (DPHudWidgets.Widget w : DPHudWidgets.ALL) S.widgets.put(w.id, false); });
                for (DPHudWidgets.Widget w : DPHudWidgets.ALL)
                    tog(mx, my, w.title, w::on, () -> { S.widgets.put(w.id, !w.on()); });
                break;
            default:
                head("Performance", "How hard the HUD works. The defaults are tuned for your big pack.");
                long[] mem = DPHud.memory();
                info("Right now: §f" + mem[0] + "§7 picture buffers, §f" + mem[1] + " MB§7 of video memory (one shared depth buffer).");
                slider(mx, my, "Look for new mod HUDs every (frames)", "se", 2, 60, () -> S.scanEvery, v -> S.scanEvery = v, "Higher = less work, new mod cards appear a little later");
                slider(mx, my, "Re-measure each mod every (frames)", "re", 20, 600, () -> S.rescanFrames, v -> S.rescanFrames = v, "How often a card's size is checked again");
                tog(mx, my, "Check quiet mods now and then (catches HUDs that appear later)", () -> S.probeIdle, () -> S.probeIdle = !S.probeIdle);
                info("§7Mods that draw nothing give their buffer back automatically. 'Hidden' mods in the Mods page cost nothing at all.");
                break;
        }
    }

    private String[] themeIds() { String[] a = new String[DPHudThemes.LIST.length]; for (int i = 0; i < a.length; i++) a[i] = DPHudThemes.LIST[i][0]; return a; }

    private static void syncCfg() { net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE); }

    // ------------------------------------------------------------------ widgets
    private void head(String t, String sub) {
        fontRenderer.drawStringWithShadow("§l" + t, px, oy, 0xF5A9B8); oy += 12;
        for (String l : fontRenderer.listFormattedStringToWidth(sub, pw - 8)) { fontRenderer.drawString(l, px, oy, 0xA79FBF); oy += 10; }
        oy += 6;
    }

    private void info(String t) { for (String l : fontRenderer.listFormattedStringToWidth(t, pw - 8)) { fontRenderer.drawStringWithShadow(l, px, oy, 0xC8C0D8); oy += 10; } oy += 4; }

    private void tog(int mx, int my, String label, BooleanSupplier v, Runnable r) {
        boolean on = v.getAsBoolean();
        if (in(mx, my, px, oy, pw, 16)) Gui.drawRect(px - 2, oy - 1, px + pw, oy + 15, 0x30FFFFFF);
        Gui.drawRect(px, oy + 4, px + 16, oy + 12, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? px + 9 : px + 1, oy + 5, on ? px + 15 : px + 7, oy + 11, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, pw - 30), px + 22, oy + 4, on ? 0xFFFFFF : 0xA79FBF);
        hits.add(new Object[]{ px, oy, pw, 16, r });
        oy += 18;
    }

    private void stepper(int mx, int my, String label, Runnable minus, Runnable plus, String tip) {
        button(mx, my, px, oy, 18, 15, "-", PrideFrame.BUTTON, minus, tip);
        button(mx, my, px + 22, oy, 18, 15, "+", PrideFrame.BUTTON, plus, tip);
        fontRenderer.drawStringWithShadow(label, px + 46, oy + 4, 0xFFFFFF);
        oy += 19;
    }

    private void slider(int mx, int my, String label, String key, int min, int max, IntSupplier get, Consumer<Integer> set, String tip) {
        int v = Math.max(min, Math.min(max, get.getAsInt()));
        int lw = Math.min(220, pw / 2), x = px + lw, w = pw - lw - 40;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, lw - 6), px, oy + 3, 0xFFFFFF);
        boolean over = in(mx, my, x - 3, oy, w + 6, 14);
        Gui.drawRect(x, oy + 6, x + w, oy + 8, 0xFF3D2168);
        int kx = x + (int) ((long) (v - min) * w / Math.max(1, max - min));
        Gui.drawRect(x, oy + 6, kx, oy + 8, PrideFrame.PINK);
        Gui.drawRect(kx - 3, oy + 1, kx + 3, oy + 13, over || key.equals(dragKey) ? 0xFFFFFFFF : 0xFFE0D8F0);
        fontRenderer.drawStringWithShadow(String.valueOf(v), x + w + 8, oy + 3, 0xF5A9B8);
        if (over) hint = tip + "  §8(drag, or scroll over it)";
        final int fx = x, fw = w;
        hits.add(new Object[]{ x - 3, oy, w + 6, 14, (Runnable) () -> { dragKey = key; dragX = fx; dragW = fw; dragMin = min; dragMax = max; dragSet = set; drag(lastMx); }, key, min, max, set, get });
        oy += 18;
    }

    private void chips(int mx, int my, String label, String[] opts, java.util.function.Supplier<String> get, Consumer<String> set, String tip) {
        fontRenderer.drawString("§7" + label, px, oy + 4, 0xFFFFFF);
        int lx = px + 70, x = lx;
        for (String o : opts) {
            String name = o; for (String[] t : DPHudThemes.LIST) if (t[0].equals(o)) name = t[1];
            int w = fontRenderer.getStringWidth(name) + 10;
            if (x + w > px + pw) { x = lx; oy += 17; }
            boolean on = o.equals(get.get()), over = in(mx, my, x, oy, w, 15);
            Gui.drawRect(x, oy, x + w, oy + 15, on ? 0xFF6A3FA0 : over ? 0xFF3A2E52 : 0xFF241C33);
            fontRenderer.drawStringWithShadow(name, x + 5, oy + 4, on ? 0xFFFFFF : 0xC8C0D8);
            if (over) hint = tip;
            hits.add(new Object[]{ x, oy, w, 15, (Runnable) () -> set.accept(o) });
            x += w + 3;
        }
        oy += 21;
    }

    private void button(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String tip) {
        if (PrideFrame.button(x, y, w, h, label, color, mx, my) && tip != null) hint = tip;
        hits.add(new Object[]{ x, y, w, h, r });
    }

    private void drag(int mx) { if (dragKey != null) dragSet.accept(Math.max(dragMin, Math.min(dragMax, dragMin + Math.round((float) (mx - dragX) * (dragMax - dragMin) / Math.max(1, dragW))))); }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private int lastMx;

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        lastMx = mx;
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                ((Runnable) h[4]).run();
                DPHudSettings.get().save();
                DPSounds.play(DPSounds.CLICK, 1.1F, 0.6F);
                return;
            }
        }
    }

    @Override protected void mouseClickMove(int mx, int my, int b, long t) { lastMx = mx; drag(mx); }
    @Override protected void mouseReleased(int mx, int my, int s) { if (dragKey != null) { dragKey = null; DPHudSettings.get().save(); } }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1, d = wheel > 0 ? 1 : -1;
        for (Object[] h : hits)
            if (h.length > 9 && in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                int v = ((IntSupplier) h[9]).getAsInt() + d * (isShiftKeyDown() ? 5 : 1);
                ((Consumer<Integer>) h[8]).accept(Math.max((Integer) h[6], Math.min((Integer) h[7], v)));
                DPHudSettings.get().save();
                return;
            }
        scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top) + 8), scroll - d * 24));
    }
}
