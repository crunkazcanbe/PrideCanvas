package com.dogpound.canvas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every Pride HUD option (requested feature). config/pridecanvas/hud.json */
public final class DPHudSettings {
    // layout
    public int stripHeight = 54;
    // 2026-10-05 Requested: one colour across the whole screen, 50 % see-through, nothing flashing or moving
    public boolean solidBar = true;
    public int barColor = -1;                   // -1 = the theme's panel colour, else 0xRRGGBB
    public int barOpacity = 50;                 // percent, the whole HUD (bar + cards) in every theme
    public boolean animateDecorations = false;  // theme pictures drift/twinkle (off = they stay still)
    public int cardOpacity = -1;                // % for the cards, -1 = same as the bar
    public boolean stripAtTop = false;          // the strip across the TOP of the screen instead of above the hotbar
    public int stripWidth = 100;                // % of the screen width, centred
    public boolean middleCard = true;           // hearts / food / XP card in the middle
    public int cardSide = 0;                    // 0 both sides of the middle, 1 right only, 2 left only
    public int cardGap = 3;                     // space between cards
    public boolean textShadow = true;
    public boolean hideInMenus = false, hideThirdPerson = false, hideCreative = false;
    public boolean mapLeft = false, mapBorder = true, mapWords = true;
    public int mapOpacity = 100;
    public int speedUnit = 0;                   // 0 blocks/s, 1 km/h, 2 mph
    public int infoTextScale = 115;             // % text size on the info cards (her 10-05: 'make the text bigger')
    public boolean infoBars = true;             // coloured bars on the info cards
    public int smallTextScale = 62;             // % size of the small text on every HUD card (was a fixed 50)
    public boolean wDayBar = true;              // world card: the day as a bar with the sun/moon on it
    public boolean rainbowBand = true, bandShimmer = false, hurtFlash = false, cardFade = true, decorations = true, pageDots = true, lowHealthGlow = false;
    public int autoPageSec = 0;                 // 0 = only the arrow keys flip pages
    // player card
    public boolean pFace = true, pName = true, pLevel = true, pMode = true, pFacing = true, pHealthBar = true;
    // world card
    public boolean wClock = true, wDay = true, wCoords = true, wBiome = true, wFps = true, wWeather = true, wLight = true, wDimension = false, wPing = false;
    // vitals
    public boolean xpBar = true;
    // effects + achievements
    public boolean effects = true, effectTimers = true, effectsBadFirst = true, hideVanillaEffects = true, effectBlink = false;
    public int effectsMax = 10;
    public boolean achievements = true, achSound = true;
    public int achSeconds = 6;
    // hotbar + chat
    public boolean chatAbove = true;
    // mod cards
    public boolean modCards = true, modNames = true, mapSquare = true;
    public boolean mapAbove = true;             // her 2026-10-05: the minimap big, above the strip on the right
    public int mapSize = 26;                    // % of the screen height
    public Map<String, Integer> modes = new LinkedHashMap<String, Integer>();
    public Map<String, Boolean> widgets = new LinkedHashMap<String, Boolean>();   // info card id -> on/off (DPHudWidgets)   // modid -> 0 in the HUD, 1 draws normally, 2 hidden
    // performance
    public int scanEvery = 12, rescanFrames = 120;
    public boolean probeIdle = true;

    public static final String[] MODE_NAMES = { "§aIn the HUD", "§eDraws normally", "§cHidden" };

    private static final File FILE = new File("config/pridecanvas/hud.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DPHudSettings cur;

    public static DPHudSettings get() {
        if (cur == null) {
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { cur = GSON.fromJson(r, DPHudSettings.class); } catch (Throwable ignored) { }
            if (cur == null) cur = new DPHudSettings();
            if (cur.modes == null) cur.modes = new LinkedHashMap<String, Integer>();
            if (cur.widgets == null) cur.widgets = new LinkedHashMap<String, Boolean>();
        }
        return cur;
    }

    public int mode(String modid) { Integer m = modes.get(modid); return m == null ? 0 : m; }

    public void save() {
        try { FILE.getParentFile().mkdirs(); try (Writer w = new FileWriter(FILE)) { GSON.toJson(this, w); } } catch (Throwable ignored) { }
    }

    public static void reset() { cur = new DPHudSettings(); cur.save(); }
}
