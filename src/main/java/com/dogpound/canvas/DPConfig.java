package com.dogpound.canvas;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = DPMenuMod.MODID, name = "dpcanvas")
public class DPConfig {

    @Config.Name("Auto-Load World On Start")
    @Config.Comment("Go straight from the loading screen into your world - no clicking through the main menu (only once per launch; quitting to the menu later stays on the menu). Hold SHIFT while the game finishes loading to skip it once.")
    public static boolean autoLoadWorld = true;

    @Config.Name("Auto-Load Which World")
    @Config.Comment("Folder or display name of the world to open. Empty = the world you played most recently.")
    public static String autoLoadWorldName = "";

    @Config.Name("Mine and Slash Bars Position")
    @Config.Comment("Where the Mine and Slash level badge + health/mana/energy/xp bars sit, all together: top_center, top_left, top_right, "
            + "middle_left, middle_right, bottom_left (above the hotbar). Moved off top_left on 2026-10-02 because the inventory's side buttons covered them.")
    public static String mnsBarsPosition = "top_center";

    @Config.Name("Mine and Slash Bars Nudge X")
    @Config.Comment("Extra sideways shift for the Mine and Slash bars, in GUI pixels (negative = left).")
    public static int mnsBarsNudgeX = 0;

    @Config.Name("Mine and Slash Bars Nudge Y")
    @Config.Comment("Extra up/down shift for the Mine and Slash bars, in GUI pixels (negative = up).")
    public static int mnsBarsNudgeY = 0;

    @Config.Name("Enable Custom Main Menu")
    @Config.Comment("Replace the vanilla main menu with the Pride menu.")
    public static boolean enableMenu = true;

    @Config.Name("Enable Custom World List")
    @Config.Comment("Replace the vanilla world-select screen with the Pride world carousel.")
    public static boolean enableWorlds = true;

    @Config.Name("Enable Custom Multiplayer")
    @Config.Comment("Replace the vanilla multiplayer screen with the Pride server carousel.")
    public static boolean enableServers = true;

    @Config.Name("Menu Title")
    @Config.Comment("Big title text at the top of the main menu.")
    public static String menuTitle = "Pride";

    @Config.Name("Menu Subtitle")
    @Config.Comment("Small text shown under the title.")
    public static String menuSubtitle = "v3";

    @Config.Name("Enable Custom Loading Screen")
    @Config.Comment("Draw the Pride moving wallpaper behind the world-loading screen (\"Building terrain\") so it matches the menu.")
    public static boolean enableLoading = true;

    @Config.Name("Loading Screen Text")
    @Config.Comment("The label shown on the world create/load screen (e.g. 'Creating World').")
    public static String loadingText = "Creating World";

    @Config.Name("Show Log On Loading Screen")
    @Config.Comment("If ON, the world load screen shows scrolling log lines. If OFF, it just shows the label + bar (cleaner).")
    public static boolean loadingShowLog = false;

    @Config.Name("Blend All Menu Screens")
    @Config.Comment("Draw the moving wallpaper behind the other menu screens (options, mods, multiplayer) so they match the main menu's exact moment.")
    public static boolean blendAllScreens = true;

    @Config.Name("Website URL")
    @Config.Comment("Our Pride website (shown as the first link on the Browser start page).")
    public static String websiteUrl = "https://pride.example.com";

    @Config.Name("Browser Home Page")
    @Config.Comment({"What the Browser button opens. Blank = the built-in Pride start page",
            "(config/pridecanvas/start.html — edit it freely; delete it to get the default back)."})
    public static String browserHome = "";

    @Config.Name("Wallpaper Frames Folder")
    @Config.Comment({
            "Absolute path to a folder of wallpaper frames (PNG files, played in sorted name order).",
            "Applies to every Pride screen (menu, world select, ...).",
            "Leave blank to use the built-in by-the-fire wallpaper."
    })
    public static String wallpaperPath = "";

    @Config.Name("Fix Resize Black Screen")
    @Config.Comment({
            "Repairs the world going black after you resize the game window.",
            "Rebuilds the framebuffer and the chunk renderers once the drag stops",
            "(the same thing F3+A does), so you never have to press it yourself."
    })
    public static boolean fixResizeBlackScreen = true;

    @Config.Name("Resize Settle Delay (ms)")
    @Config.Comment({
            "How long to wait after the last size change before repairing.",
            "Dragging an edge fires a resize every frame; repairing on each one would freeze the game.",
            "Raise it if dragging still feels heavy, lower it if the black screen lingers too long."
    })
    @Config.RangeInt(min = 0, max = 3000)
    public static int resizeSettleMs = 300;

    @Config.Name("Custom Pause Menu")
    @Config.Comment("Replace the Esc menu with the Pride pause menu (every mod's Esc-menu button is kept as a tile).")
    public static boolean pauseMenu = true;

    @Config.Name("Menu GUI Scale")
    @Config.Comment({"GUI scale used while a menu is open. -1 = off: menus use your normal GUI Scale (default). 0 = the biggest that fits,",
            "1-8 = that scale, -1 = off (menus use your normal GUI Scale). Your HUD/hotbar keep your normal scale either way."})
    @Config.RangeInt(min = -1, max = 16)
    public static int menuGuiScale = -1;

    @Config.Name("Sub-Menus In A Box")
    @Config.Comment("Options, Controls, Language, Resource Packs, Stats… lay themselves out inside a box the size of the Pride pause menu, in the middle of the screen.")
    public static boolean boxSubMenus = true;

    @Config.Name("Animations")
    @Config.Comment("Tiles lift, glow, squish and ripple; menus slide and pop in.")
    public static boolean animations = true;

    @Config.Name("Screen Transitions")
    @Config.Comment("Every screen (Pride and other mods') glides in when it opens, and the game fades back in when a menu closes.")
    public static boolean transitions = true;

    @Config.Name("Transitions On Inventories")
    @Config.Comment("Also glide in chests/inventories/machines (off = they open instantly, like vanilla).")
    public static boolean transitionContainers = false;

    @Config.Name("Sparkles")
    @Config.Comment("Hearts and stars drifting up behind the Pride menus.")
    public static boolean sparkles = true;

    @Config.Name("Menu Sounds")
    @Config.Comment("Chimes when you click, whooshes when menus open/close.")
    public static boolean uiSounds = true;

    @Config.Name("Hover Sounds")
    @Config.Comment("A tiny chime when the mouse moves onto a button (plays a little scale across a row).")
    public static boolean hoverSounds = true;

    @Config.Name("Menu Sound Volume")
    @Config.Comment("0 = silent, 1 = full.")
    @Config.RangeDouble(min = 0, max = 1)
    public static double uiVolume = 0.55;

    @Config.Name("Loading Music")
    @Config.Comment("Play the Pride theme from the very first loading bar (follows your Master and Music volume).")
    public static boolean loadingMusic = true;

    @Config.Name("Menu Music")
    @Config.Comment("Keep the loading song going on the main menu (and play it again when you quit back to the menu). Off = it fades out when the menu shows.")
    public static boolean menuMusic = true;

    @Config.Name("Pride In-Game Music")
    @Config.Comment("Pride's own songs while you play: caves, night, towns, day, Nether and End, picked by where you are.")
    public static boolean prideGameMusic = true;

    @Config.Name("Pride Music Share")
    @Config.Comment("How often a Pride song plays instead of a vanilla one (0 = never, 1 = always).")
    @Config.RangeDouble(min = 0, max = 1)
    public static double prideMusicChance = 0.75;

    @Mod.EventBusSubscriber(modid = DPMenuMod.MODID)
    private static class Handler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (DPMenuMod.MODID.equals(event.getModID())) {
                ConfigManager.sync(DPMenuMod.MODID, Config.Type.INSTANCE);
                DPBackground.reload(); // pick up a new wallpaper folder without restart
            }
        }
    }
}
