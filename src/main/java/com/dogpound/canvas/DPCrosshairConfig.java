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

/**
 * Pride Crosshair settings (config/pridecanvas/crosshair.json), edited in DPCrosshairScreen. Like Dynamic Crosshair:
 * every situation has its own on/off, shape and colour; plus the extras (attack ring, mining ring, right-tool marks...)
 * and the fancy block outline.
 */
public final class DPCrosshairConfig {
    /** one situation's look */
    public static final class Sit {
        public boolean show = true;
        public String style;
        public int color = 0;                    // 0 = this situation's own colour
        public float size = 1F;                  // on top of the global size
        Sit(String style) { this.style = style; }
    }

    /** situation id -> {label, help, default style, default colour} (order = menu order) */
    public static final String[][] SITUATIONS = {
            { "nothing", "Looking at nothing", "Nothing in reach to hit or use.", "dot", "C0FFFFFF" },
            { "block", "Looking at a block", "A block you could mine.", "cross", "FFFFFFFF" },
            { "interact_block", "Block you can use", "Chests, doors, buttons, levers, machines.", "ring_dot", "FF5BCEFA" },
            { "tool", "Right tool for the block", "Your tool mines this block fast.", "cross", "FF8CE06A" },
            { "wrong_tool", "Can't mine it", "This block won't drop with what you hold.", "x_small", "FFFF5A64" },
            { "place", "Placing a block", "Holding a block, looking where it goes.", "square_dot", "FFF5A9B8" },
            { "entity", "Looking at a mob", "Something you can hit.", "brackets", "FFF5A9B8" },
            { "attack_ready", "Mob + full swing", "Your hit is fully charged.", "brackets", "FFFF5A64" },
            { "interact_entity", "Mob you can use", "Villagers, item frames, boats, horses, minecarts.", "brackets_round", "FF5BCEFA" },
            { "melee", "Holding a weapon", "Sword or axe, nothing targeted.", "cross_open", "FFFFFFFF" },
            { "ranged", "Holding a bow", "Bow in hand, not drawn.", "circle", "C0FFFFFF" },
            { "ranged_charging", "Drawing a bow", "Pulling the string.", "sniper", "FFF5A9B8" },
            { "ranged_charged", "Bow fully drawn", "Let go now for full power.", "sniper", "FF8CE06A" },
            { "throwable", "Something to throw", "Snowballs, eggs, pearls, splash potions.", "caret", "FFFFC040" },
            { "use_item", "Something to use", "Food, potions, buckets and other usable items.", "plus_dot", "FFFFC040" },
            { "eating", "Eating / drinking", "While you eat or drink.", "ring_dot", "FFFFC040" },
            { "shield", "Shield up", "While blocking with a shield.", "shield", "FF5BCEFA" },
    };

    public int mode = 1;                         // 0 off (vanilla), 1 dynamic, 2 always the "block" look
    public float scale = 1F;
    public int opacity = 100;                    // %
    public String colorMode = "situation";       // situation | rainbow | pride | invert
    public boolean shadow = true;
    public boolean hideInThirdPerson = true;
    public boolean attackRing = true, miningRing = true, toolMarks = true, placeHint = true, chargeRing = true, eatRing = true;
    public boolean spreadOnMove = false, pulseReady = true, hitMarker = true, centerDot = false, mobHealth = true;
    public boolean hideSprinting = false, hideWithMap = true;
    public int spin = 0;                         // degrees per second, 0 = still
    public boolean outlineOnlyMining = false;
    // more extras (requested feature)
    public boolean glow = true, hitBounce = true, breathe = false, readySparkles = true, killHearts = true, targetName = false, targetDistance = false, nameShadowBox = true;
    public int glowStrength = 45, breatheSpeed = 4;
    // fancy block outline
    public boolean outline = true, outlineFill = true, outlinePulse = true;
    public String outlineColor = "rainbow";      // rainbow | pride | solid | vanilla
    public int outlineSolid = 0xFFF5A9B8;
    public float outlineWidth = 1.6F;
    public Map<String, Sit> sits = new LinkedHashMap<String, Sit>();
    public String theme = "";                    // last crosshair theme applied ("" = hand-made)
    public boolean followHud = true;             // switch theme together with the HUD theme

    /** crosshair themes: {id, name, main shape, second shape, small shape, colour 1, colour 2, colour 3} */
    public static final String[][] THEMES = {
            { "sweets", "Sweet Shop", "lollipop", "cupcake", "candy", "FFFF5A9A", "FF7CC8FF", "FFFFD23A" },
            { "teddy", "Teddy Hug", "bear", "heart", "paw", "FFC98A5A", "FFFF7FAE", "FFF0C9A0" },
            { "candy", "Candy Hearts", "heart", "candy", "sparkle", "FFFF6FA5", "FFC7A8F5", "FFFFC46A" },
            { "picnic", "Teddy Picnic", "strawberry", "bear", "cherries", "FFFF4A5A", "FFC98A5A", "FFFFB0C0" },
            { "cloud", "Cloud Dream", "cloud", "star", "sparkle", "FFFFFFFF", "FFFFD23A", "FFB8E4FF" },
            { "ribbon", "Ribbon & Bows", "bow", "double_heart", "heart", "FFFF7FAE", "FFFFB46A", "FFFFC0D8" },
            { "kawaii", "Kawaii", "kitty", "sakura", "star", "FFFFB0D0", "FFB57EDC", "FFFFE8A0" },
            { "paws", "Bear Paws", "paw", "bear", "paw", "FFFF9CC0", "FFC98A5A", "FFFFD060" },
            { "butterfly", "Butterfly Garden", "butterfly", "sakura", "sparkle", "FFB57EDC", "FFFFB0D0", "FF8CE0A0" },
            { "pride", "Pride", "pride_ring", "pride_cross", "heart", "FFF5A9B8", "FF5BCEFA", "FFFFFFFF" },
    };

    /** give every situation the theme's look (requested feature) */
    public void applyTheme(String id) {
        for (String[] t : THEMES) {
            if (!t[0].equals(id)) continue;
            int c1 = (int) Long.parseLong(t[5], 16), c2 = (int) Long.parseLong(t[6], 16), c3 = (int) Long.parseLong(t[7], 16);
            int green = 0xFF8CE06A, red = 0xFFFF5A64;
            Object[][] m = {
                    { "nothing", t[4], c3 }, { "block", t[2], c1 }, { "interact_block", t[3], c2 }, { "tool", t[2], green },
                    { "wrong_tool", "x_small", red }, { "place", t[3], c1 }, { "entity", t[2], c2 }, { "attack_ready", t[2], red },
                    { "interact_entity", t[3], c2 }, { "melee", t[4], c1 }, { "ranged", "bubble_ring", c2 }, { "ranged_charging", "bubble_ring", c1 },
                    { "ranged_charged", "bubble_ring", green }, { "throwable", t[4], c3 }, { "use_item", t[3], c3 }, { "eating", t[3], c3 }, { "shield", "shield", c2 } };
            for (Object[] e : m) {
                Sit s = sits.get(e[0]);
                if (s == null) sits.put((String) e[0], s = new Sit((String) e[1]));
                s.style = DPCrosshairStyles.exists((String) e[1]) ? (String) e[1] : "cross";
                s.color = (Integer) e[2];
                s.show = true;
            }
            colorMode = "situation";
            theme = id;
            save();
            return;
        }
    }

    /** called when the HUD theme changes */
    public static void hudThemeChanged(String hudTheme) {
        DPCrosshairConfig c = get();
        if (!c.followHud) return;
        for (String[] t : THEMES) if (t[0].equals(hudTheme)) { c.applyTheme(hudTheme); return; }
    }

    private static final File FILE = new File("config/pridecanvas/crosshair.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DPCrosshairConfig cur;

    public static DPCrosshairConfig get() {
        if (cur == null) {
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { cur = GSON.fromJson(r, DPCrosshairConfig.class); } catch (Throwable ignored) { }
            if (cur == null) cur = new DPCrosshairConfig();
            cur.fill();
        }
        return cur;
    }

    /** every situation present (new ones get defaults) */
    private void fill() {
        if (sits == null) sits = new LinkedHashMap<String, Sit>();
        for (String[] s : SITUATIONS) if (!sits.containsKey(s[0])) sits.put(s[0], new Sit(s[3]));
        for (Sit s : sits.values()) if (s.style == null || !DPCrosshairStyles.exists(s.style)) s.style = "cross";
    }

    public Sit sit(String id) { Sit s = sits.get(id); return s != null ? s : sits.get("block"); }

    public static int defaultColor(String id) {
        for (String[] s : SITUATIONS) if (s[0].equals(id)) return (int) Long.parseLong(s[4], 16);
        return 0xFFFFFFFF;
    }

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (Writer w = new FileWriter(FILE)) { GSON.toJson(this, w); }
        } catch (Throwable ignored) { }
    }

    public static void reset() {
        cur = new DPCrosshairConfig();
        cur.fill();
        cur.save();
    }
}
