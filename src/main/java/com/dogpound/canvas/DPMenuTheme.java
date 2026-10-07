package com.dogpound.canvas;

/**
 * Pack-wide menu themes (requested feature). Pride stays the default; every other theme repaints
 * the same screens: the panel, the colour band along the top, tiles, buttons, accents, the main-menu logo and wallpaper.
 * <p>
 * Works by rewriting PrideFrame's / DPStyle's colour fields (no longer final) and the contents of PrideFrame.RAINBOW and
 * DPStyle.FLAG, so every screen that already uses them follows without changes. The palette is also published in
 * System properties ("pride.theme.*") for the other Pride mods' own menus.
 */
public final class DPMenuTheme {
    private DPMenuTheme() {}

    /** one theme: id, name, description, 8 band colours, panel top/bottom, tile, tile hover, tile picked, button,
     *  accent, accent 2, dim text; the HUD theme and loading-screen theme it pairs with */
    public static final class T {
        public final String id, name, desc, hud, boot;
        public final int[] band;
        public final int top, bottom, tile, hover, on, button, accent, accent2, dim;

        T(String id, String name, String desc, int[] band, int top, int bottom, int tile, int hover, int on, int button,
          int accent, int accent2, int dim, String hud, String boot) {
            this.id = id; this.name = name; this.desc = desc; this.band = band; this.top = top; this.bottom = bottom;
            this.tile = tile; this.hover = hover; this.on = on; this.button = button; this.accent = accent; this.accent2 = accent2;
            this.dim = dim; this.hud = hud; this.boot = boot;
        }
    }

    private static int[] b(int... c) { return c; }

    public static final T[] ALL = {
            new T("pride", "Pride", "the trans + rainbow look the pack ships with",
                    b(0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982, 0xFF5BCEFA, 0xFFF5A9B8),
                    0xF2140E22, 0xF2080510, 0xFF1C1530, 0xFF2A2140, 0xFF6A3FA0, 0xFF2A2238, 0xFFF5A9B8, 0xFF5BCEFA, 0xFF8A8499, "pride", "Pride"),
            new T("classic", "Minecraft Classic", "stone, dirt and grass - like vanilla menus",
                    b(0xFF5D9C3A, 0xFF4A7D2E, 0xFF79553A, 0xFF5E4029, 0xFF7D7D7D, 0xFF646464, 0xFF8B8B8B, 0xFF565656),
                    0xF2262626, 0xF2141414, 0xFF2E2E2E, 0xFF3C3C3C, 0xFF4A6B2E, 0xFF3A3A3A, 0xFF8FD14F, 0xFFC6C6C6, 0xFF9A9A9A, "minecraft", "Vanilla"),
            new T("midnight", "Midnight", "deep navy with cool cyan",
                    b(0xFF0F2A4A, 0xFF16406E, 0xFF1F5A96, 0xFF2878BE, 0xFF3296DC, 0xFF46B4F0, 0xFF64C8FF, 0xFF8CDCFF),
                    0xF20B1424, 0xF2050A14, 0xFF111C30, 0xFF1A2A46, 0xFF1F5A96, 0xFF16243C, 0xFF64C8FF, 0xFF8CA0FF, 0xFF7A8AA0, "bars", "Futuristic"),
            new T("forest", "Forest", "mossy greens and bark browns",
                    b(0xFF1E4D2B, 0xFF2E6B3A, 0xFF3F8A4A, 0xFF5AA65A, 0xFF7BBF6A, 0xFF8B6B3E, 0xFF6E4F2A, 0xFF4E3820),
                    0xF2141F16, 0xF20A110B, 0xFF1A2A1C, 0xFF243A27, 0xFF3F8A4A, 0xFF22331F, 0xFF8FD18A, 0xFFC9A66B, 0xFF889A86, "paws", "Medieval"),
            new T("ocean", "Ocean", "teal waves and sea foam",
                    b(0xFF003F5C, 0xFF00577A, 0xFF007094, 0xFF0089A8, 0xFF00A3B8, 0xFF2EC4C4, 0xFF6FDCD0, 0xFFB2F0E6),
                    0xF20A1C24, 0xF2041016, 0xFF0E2630, 0xFF143644, 0xFF007094, 0xFF102C38, 0xFF6FDCD0, 0xFF5BB6FF, 0xFF7C9CA4, "potions", "Futuristic"),
            new T("sunset", "Sunset", "warm orange, coral and dusk purple",
                    b(0xFFFFD166, 0xFFFFB347, 0xFFFF8C42, 0xFFFF6B4A, 0xFFE8505B, 0xFFC0436E, 0xFF8E3B7A, 0xFF5B2E6E),
                    0xF2241420, 0xF2140A12, 0xFF2C1A26, 0xFF3C2434, 0xFFC0436E, 0xFF34202C, 0xFFFF9E5E, 0xFFFFD166, 0xFFA08890, "cloud", "Pride"),
            new T("mono", "Monochrome", "clean greys, no colour at all",
                    b(0xFFF0F0F0, 0xFFD0D0D0, 0xFFB0B0B0, 0xFF909090, 0xFF707070, 0xFF909090, 0xFFB0B0B0, 0xFFD0D0D0),
                    0xF21C1C1C, 0xF20E0E0E, 0xFF262626, 0xFF333333, 0xFF555555, 0xFF2C2C2C, 0xFFE6E6E6, 0xFFAAAAAA, 0xFF8C8C8C, "numbers", "Minimal"),
            new T("nether", "Nether", "lava, netherrack and soul fire",
                    b(0xFF3A0A0A, 0xFF6B1010, 0xFF9C1C12, 0xFFC8341A, 0xFFE85A1E, 0xFFFF8C1A, 0xFF5AD1E8, 0xFF2A9CB8),
                    0xF2200A0A, 0xF2100404, 0xFF2A1010, 0xFF3C1616, 0xFF9C1C12, 0xFF331212, 0xFFFF8C1A, 0xFF5AD1E8, 0xFFA08080, "potions", "Nuclear"),
            new T("end", "The End", "end stone, purpur and void",
                    b(0xFF0A0614, 0xFF241238, 0xFF3E1E5E, 0xFF6A3E8C, 0xFF9C6EB4, 0xFFC4A4D0, 0xFFE6E2A8, 0xFFDCD68C),
                    0xF20E0818, 0xF206040C, 0xFF180F26, 0xFF241838, 0xFF6A3E8C, 0xFF1E1430, 0xFFE6E2A8, 0xFFC4A4D0, 0xFF8E84A0, "crystals", "LCARS"),
            new T("cherry", "Cherry Blossom", "soft pinks and white petals",
                    b(0xFFFFF0F5, 0xFFFFD6E4, 0xFFFFBCD3, 0xFFFFA3C2, 0xFFF78AB0, 0xFFE8739E, 0xFFC95C86, 0xFF8E3B5E),
                    0xF22A1620, 0xF2180C12, 0xFF33202A, 0xFF462C3A, 0xFFC95C86, 0xFF3A2430, 0xFFFFA3C2, 0xFFFFF0F5, 0xFFB098A4, "ribbon", "Pride"),
            new T("candy", "Candy", "pastel sweets, sprinkles everywhere",
                    b(0xFFFF9AA2, 0xFFFFB7B2, 0xFFFFDAC1, 0xFFE2F0CB, 0xFFB5EAD7, 0xFFC7CEEA, 0xFFD5AAFF, 0xFFFFC8DD),
                    0xF2241A2C, 0xF2140E1A, 0xFF2C2236, 0xFF3C2E4A, 0xFF8E6AB8, 0xFF342840, 0xFFFFB7B2, 0xFFB5EAD7, 0xFFA89CB4, "sweets", "Pride"),
            new T("cyber", "Cyberpunk", "neon magenta and cyan on black",
                    b(0xFFFF00A0, 0xFFE000C0, 0xFFA000E0, 0xFF6000FF, 0xFF0080FF, 0xFF00C0FF, 0xFF00FFE0, 0xFFF0FF00),
                    0xF20A0412, 0xF2040208, 0xFF120A1E, 0xFF1E1032, 0xFFA000E0, 0xFF180C28, 0xFFFF2EB8, 0xFF00F0FF, 0xFF8C80A8, "bars", "Cyberpunk"),
            new T("steampunk", "Steampunk", "brass, copper and old leather",
                    b(0xFF3E2A14, 0xFF5E3E1C, 0xFF8A5A24, 0xFFB5822E, 0xFFD4A73E, 0xFFB87333, 0xFF8C5428, 0xFF5A3618),
                    0xF2201810, 0xF2100C06, 0xFF2A2016, 0xFF3A2C1E, 0xFF8A5A24, 0xFF33261A, 0xFFD4A73E, 0xFFB87333, 0xFFA0907A, "rings", "Industrial"),
            new T("terminal", "Terminal", "green phosphor on black, like an old computer",
                    b(0xFF003300, 0xFF004D00, 0xFF006600, 0xFF008000, 0xFF00A000, 0xFF00C000, 0xFF33E033, 0xFF80FF80),
                    0xF2020802, 0xF2000400, 0xFF051005, 0xFF0A1C0A, 0xFF006600, 0xFF081408, 0xFF33FF33, 0xFFA0FFA0, 0xFF3C8C3C, "numbers", "Terminal"),
            new T("royal", "Royal", "gold leaf on deep purple velvet",
                    b(0xFF2E0F4A, 0xFF471A6E, 0xFF5E2690, 0xFF7A36B0, 0xFFB08A2E, 0xFFD4AF37, 0xFFF0D060, 0xFFFFF0A0),
                    0xF2180A26, 0xF20C0414, 0xFF221034, 0xFF301848, 0xFF5E2690, 0xFF2A1440, 0xFFD4AF37, 0xFFC8A0F0, 0xFF9C8CB0, "crystals", "Pride"),
            new T("ice", "Ice", "frost, glacier blue and snow",
                    b(0xFFFFFFFF, 0xFFE6F6FF, 0xFFCCEEFF, 0xFFA8DFFF, 0xFF7FCBF2, 0xFF5AB2E0, 0xFF3C94C8, 0xFF2A74A8),
                    0xF2101C28, 0xF2080E16, 0xFF162432, 0xFF203446, 0xFF3C94C8, 0xFF1C2C3C, 0xFFA8DFFF, 0xFFFFFFFF, 0xFF8AA0B4, "crystals", "Futuristic"),
            new T("autumn", "Autumn", "falling leaves and pumpkin spice",
                    b(0xFF6B2A0E, 0xFF8E3A12, 0xFFB5501A, 0xFFD46A22, 0xFFE8892E, 0xFFF0A93C, 0xFFC28A2E, 0xFF7A5420),
                    0xF2201410, 0xF2100A06, 0xFF2A1C14, 0xFF3A281C, 0xFFB5501A, 0xFF332218, 0xFFF0A93C, 0xFFD46A22, 0xFFA09080, "picnic", "Medieval"),
            new T("lavender", "Lavender", "calm lilac and soft grey",
                    b(0xFFE6E0F8, 0xFFD2C6F0, 0xFFBEACE6, 0xFFAA92DC, 0xFF9678D2, 0xFF8264BE, 0xFF6E52A6, 0xFF5A428C),
                    0xF21C1828, 0xF20E0C16, 0xFF241F32, 0xFF322A46, 0xFF8264BE, 0xFF2C2640, 0xFFBEACE6, 0xFFE6E0F8, 0xFF9C94AC, "cloud", "Minimal"),
    };

    public static T byId(String id) {
        for (T t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    public static T current() { return byId(DPConfig.menuTheme); }

    /** repaint every Pride screen with this theme (and remember it) */
    public static void apply(String id) {
        T t = byId(id);
        System.arraycopy(t.band, 0, PrideFrame.RAINBOW, 0, Math.min(t.band.length, PrideFrame.RAINBOW.length));
        PrideFrame.PINK = t.accent;
        PrideFrame.BLUE = t.accent2;
        PrideFrame.DIM = t.dim;
        PrideFrame.TILE = t.tile;
        PrideFrame.TILE_ON = t.on;
        PrideFrame.TILE_HOVER = t.hover;
        PrideFrame.BUTTON = t.button;
        PrideFrame.PANEL_TOP = t.top;
        PrideFrame.PANEL_BOTTOM = t.bottom;
        DPStyle.PINK = t.accent & 0xFFFFFF;
        DPStyle.BLUE = t.accent2 & 0xFFFFFF;
        DPStyle.ROSE = t.accent & 0xFFFFFF;
        DPStyle.DEEP = t.top & 0xFFFFFF;
        DPStyle.MID = t.tile & 0xFFFFFF;
        DPStyle.VIOLET = t.on & 0xFFFFFF;
        int[] flag = {DPStyle.BLUE, DPStyle.PINK, 0xFFFFFF, DPStyle.PINK, DPStyle.BLUE};
        if (!"pride".equals(t.id)) flag = new int[]{t.band[1] & 0xFFFFFF, t.band[3] & 0xFFFFFF, t.band[5] & 0xFFFFFF, t.band[3] & 0xFFFFFF, t.band[1] & 0xFFFFFF};
        System.arraycopy(flag, 0, DPStyle.FLAG, 0, Math.min(flag.length, DPStyle.FLAG.length));
        publish(t);
    }

    /** for the other Pride mods' menus (PrideChat, PrideInventory, IR Extras...): they read these if present */
    private static void publish(T t) {
        java.util.Properties p = System.getProperties();
        p.put("pride.theme.id", t.id);
        p.put("pride.theme.band", t.band.clone());
        p.put("pride.theme.palette", new int[]{t.top, t.bottom, t.tile, t.hover, t.on, t.button, t.accent, t.accent2, t.dim});
        Object v = p.get("pride.theme.version");
        p.put("pride.theme.version", v instanceof Integer ? (Integer) v + 1 : 1);
    }

    /** pick a theme from a menu: apply, save, and (if wanted) switch the HUD + loading screen to match */
    public static void choose(String id) {
        T t = byId(id);
        boolean wasPride = "pride".equals(DPConfig.menuTheme);
        DPConfig.menuTheme = t.id;
        apply(t.id);
        if (wasPride != "pride".equals(t.id)) setPrideMessages("pride".equals(t.id));   // neutral lines go with a neutral theme
        if (DPConfig.themeMatchHud) {
            DPConfig.hudTheme = t.hud;
            try { DPCrosshairConfig.hudThemeChanged(t.hud); } catch (Throwable ignored) { }
        }
        if (DPConfig.themeMatchLoading) loading(t);
        net.minecraftforge.common.config.ConfigManager.sync(DPMenuMod.MODID, net.minecraftforge.common.config.Config.Type.INSTANCE);
    }

    public static void setPrideMessages(boolean on) {
        DPConfig.themePrideMessages = on;
        DPBootSettings.set("prideMsgs", on);
    }

    /** the loading screen reads config/pride-boot.txt before Forge starts: use a matching preset, or this exact palette */
    private static void loading(T t) {
        int idx = -1;
        for (int i = 0; i < DPBootTheme.NAMES.length; i++) if (DPBootTheme.NAMES[i].equals(t.boot)) idx = i;
        boolean exact = !"pride".equals(t.id) && !"classic".equals(t.id) && !"terminal".equals(t.id) && !"cyber".equals(t.id);
        if (exact) {
            StringBuilder bar = new StringBuilder();
            for (int c : t.band) bar.append(bar.length() == 0 ? "" : ", ").append(String.format("\"#%06X\"", c & 0xFFFFFF));
            String json = String.format("{\n  \"panel\": \"#%06X\",\n  \"accent\": \"#%06X\",\n  \"accent2\": \"#%06X\",\n  \"text\": \"#FFFFFF\",\n  \"dim\": \"#%06X\",\n  \"bar\": [%s]\n}\n",
                    t.top & 0xFFFFFF, t.accent & 0xFFFFFF, t.accent2 & 0xFFFFFF, t.dim & 0xFFFFFF, bar);
            try { java.nio.file.Files.write(new java.io.File("config/pride-boot-theme.json").toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
            idx = DPBootTheme.NAMES.length - 1;                // "Custom"
        }
        if (idx >= 0) DPBootSettings.set("theme", idx);
    }
}
