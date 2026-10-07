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

    @Config.Name("Pride HUD")
    @Config.Comment("One strip across the bottom of the screen (on top of the hotbar) holding every HUD: Minecraft's bars in the middle and every mod's overlay in its own card. Off = every mod draws where it always did.")
    public static boolean hudEnabled = true;

    @Config.Name("Pride HUD Pass-Through Mods")
    @Config.Comment("Comma-separated mod ids that keep drawing in their own place instead of moving into the HUD strip.")
    public static String hudPassthrough = "";

    @Config.Name("HUD size")
    @Config.Comment("How big the Pride HUD strip is drawn. 1 = follows your GUI Scale like the rest of the game (default, her choice 2026-10-04: small). 2 = twice as big, and so on. 0 = automatic (about a tenth of the screen's height). Page Up / Page Down change it in game.")
    @Config.RangeDouble(min = 0, max = 6)
    public static double hudScale = 1;

    @Config.Name("HUD: hotbar grows with the HUD")
    @Config.Comment("Draw the hotbar at the same size as the HUD strip so they match on big screens.")
    public static boolean hudScaleHotbar = true;

    @Config.Name("Menu theme")
    @Config.Comment("Colours of every Pride menu (main menu, Esc menu, options, settings screens): pride, classic, midnight, forest, ocean, sunset, mono, nether, end, cherry, candy, cyber, steampunk, terminal, royal, ice, autumn, lavender. Esc menu > Themes or Options > Themes.")
    public static String menuTheme = "pride";

    @Config.Name("Menu theme: logo")
    @Config.Comment("The big logo on the main menu and loading screen: Pride (its own colours), Tinted (in the theme's colours) or Hidden.")
    public static String menuLogo = "Pride";

    @Config.Name("Menu theme: wallpaper")
    @Config.Comment("Behind the main menu: Animated (the moving wallpaper), Theme (a gradient in the theme's colours) or Dark.")
    public static String menuWallpaper = "Animated";

    @Config.Name("Menu theme: Pride messages")
    @Config.Comment("The main-menu quotes and loading tips about Pride / trans rights. Off = neutral, friendly ones. Picking a non-Pride theme turns this off (switch it back on any time).")
    public static boolean themePrideMessages = true;

    @Config.Name("Menu theme: character")
    @Config.Comment("The little character in the top-left corner of the main menu.")
    public static boolean themeCharacter = true;

    @Config.Name("Menu theme: HUD matches")
    @Config.Comment("Picking a menu theme also switches the HUD theme to its partner.")
    public static boolean themeMatchHud = false;

    @Config.Name("Menu theme: loading screen matches")
    @Config.Comment("Picking a menu theme also recolours the loading screen (from the next start).")
    public static boolean themeMatchLoading = true;

    @Config.Name("HUD theme")
    @Config.Comment("How the hearts / food / water card looks: sweets (her pick 2026-10-04), pride, teddy, candy, picnic, cloud, ribbon, kawaii, paws, classic, bars, potions, pills, rings, numbers, crystals, wings, or minecraft (Minecraft's own icons + Scaling Health etc. as they draw them). Esc menu > HUD Theme.")
    public static String hudTheme = "sweets";

    @Config.Name("HUD: hotbar matches the HUD theme")
    @Config.Comment("The hotbar (and PrideInventory's long hotbar) uses the same colours and decorations as the HUD theme so they look like one piece.")
    public static boolean hudThemeHotbar = true;

    @Config.Name("HUD background opacity %")
    @Config.RangeInt(min = 0, max = 100)
    public static int hudOpacity = 88;

    @Config.Name("HUD: 24-hour clock")
    public static boolean hudClock24 = false;

    @Config.Name("HUD: player card (face, name, level, mode, facing)")
    public static boolean hudShowPlayer = true;

    @Config.Name("HUD: world card (time, day, weather, place, light, fps)")
    public static boolean hudShowWorld = true;

    @Config.Name("HUD: hearts + food from Scaling Health / Scaling Feast / AppleSkin")
    @Config.Comment("With any of those mods installed, the Pride HUD shows their real hearts and food bars (coloured heart rows, bigger hunger, saturation) instead of drawing its own.")
    public static boolean hudVitalsFromMods = true;

    @Config.Name("System Monitor corner readout")
    @Config.Comment("Small FPS / CPU / GPU / VRAM / RAM readout in the top-left corner while playing (switch it in the System Monitor screen).")
    public static boolean sysOverlay = false;

    @Config.Name("Pride Crosshair")
    @Config.Comment("A crosshair that shows what you can do: attack charge, interact, right tool, mining progress, bow draw, throwing, eating, shield.")
    public static boolean prideCrosshair = true;

    @Config.Name("Pride block outline")
    @Config.Comment("The outline round the block you look at shimmers through the Pride colours and fills up while you mine it.")
    public static boolean prideOutline = true;

    @Config.Name("Pride advancement screen")
    @Config.Comment("The L key opens the Pride advancement list (every advancement, what to do, when you got it) instead of Minecraft's tree.")
    public static boolean prideAdvancements = true;

    @Config.Name("Pop-ups: Advancements")
    @Config.Comment("Show the 'Advancement Made!' pop-ups in the top right.")
    public static boolean toastAdvancements = false;

    @Config.Name("Pop-ups: New Recipes")
    @Config.Comment("Show the 'New Recipes Unlocked!' pop-ups in the top right.")
    public static boolean toastRecipes = false;

    @Config.Name("Pop-ups: Tutorial Hints")
    @Config.Comment("Show Minecraft's tutorial hints (move with WASD, punch a tree, open your inventory...).")
    public static boolean toastTutorial = false;

    @Config.Name("Pop-ups: Other Mods")
    @Config.Comment("Show pop-ups that other mods add to the top-right corner.")
    public static boolean toastOther = true;

    @Config.Name("Mouse Vibration")
    @Config.Comment("Buzz a SteelSeries Rival 700 (through the pridemouse helper) when you get hit, die, level up, eat, pick things up, earn advancements and click in menus.")
    public static boolean mouseBuzz = true;

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
