package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * DogPound UI - one mod, toggleable custom screens (menu, world list, multiplayer),
 * all sharing a moving wallpaper and theme. Configurable via config/dpcanvas.cfg.
 *
 * NOTE (2026-06-17): the loading-screen feature was REMOVED from this build — it was
 * crashing world load. The visual code (DPLoadingScreen/DPLogBuffer/mixin) still lives
 * in the source for later, but is NOT wired in here, so it cannot affect the game.
 */
@Mod(modid = DPMenuMod.MODID, name = "Pride UI", version = "0.2.0",
        clientSideOnly = true,
        updateJSON = "https://pride.example.com/dpmenu/update.json")
public class DPMenuMod {
    public static final String MODID = "dpcanvas";

    // Loading screen data, copied on the MAIN thread (the splash thread may never touch Forge objects / registries)
    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent e) { DPBootPreload.title(); DPBootMain.snapshotMods(); }

    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent e) { DPBootPreload.title(); DPBootMain.countRegistries(); }

    @Mod.EventHandler
    public void loadComplete(net.minecraftforge.fml.common.event.FMLLoadCompleteEvent e) { DPBootPreload.title(); DPBootMain.countRegistries(); }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        DPBootMain.countRegistries();
        MinecraftForge.EVENT_BUS.register(this);
        DPMenuTheme.apply(DPConfig.menuTheme);   // pack-wide menu colours (Esc / Options > Themes)
        DPIcon.apply();          // Pride window icon + "Pride" title bar
        DPLogBuffer.install();   // capture log lines for the loading-screen box
        MinecraftForge.EVENT_BUS.register(new DPResizeFix());   // window resize -> black world, patched here
        MinecraftForge.EVENT_BUS.register(new DPToasts());
        MinecraftForge.EVENT_BUS.register(new DPAdvancements.Hook());
        MinecraftForge.EVENT_BUS.register(new DPCrosshair());
        MinecraftForge.EVENT_BUS.register(new DPSysMonitor.Overlay());   // corner readout (System Monitor)       // Pride Crosshair + fancy block outline // L key -> Pride advancement list      // top-right pop-ups she switched off (Config → Pop-ups)
        DPPatcherKey.register();                                  // PolyPatcher's menu on a key that actually works
        MinecraftForge.EVENT_BUS.register(new DPPatcherKey());
        MinecraftForge.EVENT_BUS.register(new DPPrompts());
        MinecraftForge.EVENT_BUS.register(new DPTransition());
        DPCommand.register();
        MinecraftForge.EVENT_BUS.register(new DPMenuScale());
        MinecraftForge.EVENT_BUS.register(new DPWorkingScreen());       // no vanilla % box between our loading screens
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DPHud.KEY);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DPHud.PAGE_LEFT);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DPHud.PAGE_RIGHT);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DPHud.BIGGER);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DPHud.SMALLER);
        MinecraftForge.EVENT_BUS.register(new DPHud());
        MinecraftForge.EVENT_BUS.register(new DPMouse());                // Rival 700 vibration (pridemouse helper)                  // Pride HUD strip: every HUD in one bar
        MinecraftForge.EVENT_BUS.register(new DPDhSettings.Opener());   // Distant Horizons settings as a Pride menu
        MinecraftForge.EVENT_BUS.register(new DPBoxLayout());                     // sub-menus in an Esc-sized box                     // after DPMenuMod's own GuiOpenEvent handler                    // every screen glides in; game fades back in                       // popups -> pride-prompts/ so Claude can see + answer
        // PRELOAD the loading-screen classes NOW, while the classloader is healthy.
        // The coremod injects a call to DPLoadingHook into LoadingScreenRenderer; if
        // that class is loaded LAZILY mid-world-load, Forge's transformer chain can
        // read it as 0 bytes and hard-crash (NoClassDefFoundError). Eager-loading here
        // means it's already cached when the injected call fires — no lazy load, no crash.
        try {
            Class.forName("com.dogpound.canvas.DPBackground");
            Class.forName("com.dogpound.canvas.DPLoadingScreen");
            Class.forName("com.dogpound.canvas.DPLoadingHook");
            Class.forName("com.dogpound.canvas.DPPrompts");
            Class.forName("com.dogpound.canvas.DPWindow");
        } catch (Throwable t) {
            System.out.println("[DogPound] loading-screen preload skipped (safe): " + t);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)   // run LAST so we beat ModBrowser's menu takeover
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui == null) return;

        boolean onMenu = Minecraft.getMinecraft().world == null;

        if (gui instanceof GuiMainMenu) {                 // loading screen #3/#15: boot time -> history + "why so long?" report
            try { DPBoot.finish(); } catch (Throwable t) { System.out.println("[Pride UI] boot stats skipped: " + t); }   // never break opening a screen
        }

        // Requested: go straight into the world after loading (once per launch, Shift skips)
        if (onMenu && gui instanceof GuiMainMenu && !autoTried) {
            autoTried = true;
            if (DPConfig.autoLoadWorld && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                    && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT)
                    && !new java.io.File("realmcoin-buttondump").isFile()) {       // the dev button test opens its own world
                net.minecraft.world.storage.WorldSummary w = autoWorld();
                if (w != null) {
                    System.out.println("[Pride UI] auto-loading world '" + w.getDisplayName() + "' (" + w.getFileName() + ")");
                    Minecraft.getMinecraft().addScheduledTask(() ->           // next tick: the menu finishes opening first
                        net.minecraftforge.fml.client.FMLClientHandler.instance().tryLoadExistingWorld(
                            new net.minecraft.client.gui.GuiWorldSelection(new DPMainMenu()), w));
                } else if (new java.io.File("pride-create-world").isFile() && !DPConfig.autoLoadWorldName.trim().isEmpty()) {
                    // test harness only (mctest --lite): the asked-for world doesn't exist → make it, creative + cheats
                    String name = DPConfig.autoLoadWorldName.trim();
                    System.out.println("[Pride UI] creating test world '" + name + "'");
                    net.minecraft.world.WorldSettings ws = new net.minecraft.world.WorldSettings(20261001L,
                            net.minecraft.world.GameType.CREATIVE, true, false, net.minecraft.world.WorldType.DEFAULT);
                    ws.enableCommands();
                    Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().launchIntegratedServer(name, name, ws));
                }
            }
        }

        // instanceof (not == GuiMainMenu.class): also catches ModBrowser's GuiMainMenu SUBCLASS,
        // which it swaps in on its own GuiOpenEvent handler. DPMainMenu extends GuiScreen, so no loop.
        // in-game Esc menu → the Pride pause menu (a mod's own GuiIngameMenu subclass is kept as the "real" one)
        if (DPConfig.pauseMenu && !onMenu && gui instanceof net.minecraft.client.gui.GuiIngameMenu) {
            event.setGui(new DPPauseMenu((net.minecraft.client.gui.GuiIngameMenu) gui));
            return;
        }
        // Options + Video Settings are the Pride versions in-game too (they go back to whatever opened them)
        if (DPConfig.enableMenu && !onMenu && gui.getClass() == GuiOptions.class) {
            event.setGui(new DPOptions(field(gui, GuiOptions.class, "field_146441_g", null), Minecraft.getMinecraft().gameSettings));
            return;
        }
        // Skin + Chat settings as Pride tile panels (requested feature), from the title screen and in-game alike
        if (DPConfig.enableMenu && gui.getClass() == net.minecraft.client.gui.GuiCustomizeSkin.class) {
            event.setGui(new DPSubMenus.Skin(screenField(gui)));
            return;
        }
        if (DPConfig.enableMenu && gui.getClass() == net.minecraft.client.gui.ScreenChatOptions.class) {
            event.setGui(new DPSubMenus.Chat(screenField(gui), Minecraft.getMinecraft().gameSettings));
            return;
        }
        if (DPConfig.enableMenu && !onMenu && gui.getClass() == GuiVideoSettings.class) {
            event.setGui(new DPVideoSettings(field(gui, GuiVideoSettings.class, "field_146498_f", null), Minecraft.getMinecraft().gameSettings));
            return;
        }

        if (DPConfig.enableMenu && gui instanceof GuiMainMenu) {
            event.setGui(new DPMainMenu());
        } else if (DPConfig.enableWorlds && gui.getClass() == GuiWorldSelection.class) {
            event.setGui(new DPWorldSelect(new DPMainMenu()));
        } else if (DPConfig.enableServers && gui.getClass() == GuiMultiplayer.class) {
            event.setGui(new DPServerSelect(new DPMainMenu()));
        } else if (DPConfig.enableMenu && onMenu && gui.getClass() == GuiOptions.class) {
            event.setGui(new DPOptions(new DPMainMenu(), Minecraft.getMinecraft().gameSettings));
        } else if (DPConfig.enableMenu && onMenu && gui.getClass() == GuiVideoSettings.class) {
            GuiScreen parent = parentOf(gui);   // the screen Done returns to (the themed Options)
            event.setGui(new DPVideoSettings(parent, Minecraft.getMinecraft().gameSettings));
        }
    }

    private static boolean autoTried;

    /** the configured world, or the one played most recently (never the dev test world) */
    private static net.minecraft.world.storage.WorldSummary autoWorld() {
        try {
            java.util.List<net.minecraft.world.storage.WorldSummary> all = Minecraft.getMinecraft().getSaveLoader().getSaveList();
            String want = DPConfig.autoLoadWorldName == null ? "" : DPConfig.autoLoadWorldName.trim();
            net.minecraft.world.storage.WorldSummary best = null;
            for (net.minecraft.world.storage.WorldSummary w : all) {
                if (w.getFileName().equals("buttondump")) continue;
                if (want.isEmpty() && w.getFileName().endsWith("-test")) continue;   // never auto-load the test harness worlds
                if (!want.isEmpty()) { if (w.getFileName().equalsIgnoreCase(want) || w.getDisplayName().equalsIgnoreCase(want)) return w; continue; }
                if (best == null || w.getLastTimePlayed() > best.getLastTimePlayed()) best = w;
            }
            return best;
        } catch (Exception e) {
            System.out.println("[Pride UI] couldn't pick a world to auto-load: " + e);
            return null;
        }
    }

    /** a screen-typed private field (a screen's parent), or `fallback` */
    private static GuiScreen field(Object o, Class<?> owner, String srg, GuiScreen fallback) {
        try {
            java.lang.reflect.Field f = owner.getDeclaredField(srg);
            f.setAccessible(true);
            Object v = f.get(o);
            if (v instanceof GuiScreen) return (GuiScreen) v;
        } catch (Throwable ignored) {}
        return fallback;
    }

    /** Read a settings screen's parent screen (field_146498_f on GuiVideoSettings), best-effort. */
    /** the screen a vanilla sub-menu goes back to: its one GuiScreen field (no name lookups needed) */
    private static GuiScreen screenField(GuiScreen gui) {
        for (java.lang.reflect.Field f : gui.getClass().getDeclaredFields()) {
            if (!GuiScreen.class.isAssignableFrom(f.getType())) continue;
            try { f.setAccessible(true); Object p = f.get(gui); if (p instanceof GuiScreen) return (GuiScreen) p; } catch (Throwable ignored) {}
        }
        return new DPOptions(new DPMainMenu(), Minecraft.getMinecraft().gameSettings);
    }

    private static GuiScreen parentOf(GuiScreen gui) {
        try {
            java.lang.reflect.Field f = gui.getClass().getDeclaredField("field_146498_f");
            f.setAccessible(true);
            Object p = f.get(gui);
            if (p instanceof GuiScreen) return (GuiScreen) p;
        } catch (Throwable ignored) {
        }
        return new DPOptions(new DPMainMenu(), Minecraft.getMinecraft().gameSettings);
    }

    /** Buttons a screen adds later (Stats makes its tabs when the numbers arrive) and buttons inside list rows
     *  (Controls' key buttons) never pass through InitGuiEvent — catch them here, once each. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!DPConfig.blendAllScreens) return;
        GuiScreen g = event.getGui();
        if (!DPChrome.themable(g) && !(Minecraft.getMinecraft().world == null && DPChrome.shouldSkinWidgets(g))) return;
        try {
            java.lang.reflect.Field bl = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            @SuppressWarnings("unchecked") java.util.List<net.minecraft.client.gui.GuiButton> list = (java.util.List<net.minecraft.client.gui.GuiButton>) bl.get(g);
            boolean plain = false;
            for (net.minecraft.client.gui.GuiButton b : list) if (!(b instanceof DPButton) && !(b instanceof DPOptionButton) && !(b instanceof DPOptionSlider)) { plain = true; break; }
            if (plain) DPSkin.reskinAll(list, g);
            for (net.minecraft.client.gui.GuiSlot s : DPBox.slots(g)) DPSkin.reskinRows(s);
        } catch (Throwable ignored) {}
    }

    /** Paint the time-synced wallpaper behind the other menu screens (options, mods, ...). */
    @SubscribeEvent
    public void onBackgroundDrawn(GuiScreenEvent.BackgroundDrawnEvent event) {
        if (!DPConfig.blendAllScreens) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen g = event.getGui();
        if (g instanceof DPMainMenu || g instanceof DPWorldSelect || g instanceof DPServerSelect) return;
        if (mc.world != null && !DPChrome.themable(g)) return;                  // in-game: only the themed sub-screens
        // the backdrop covers the whole real screen, even when the screen itself is drawn into a box
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(-DPBoxLayout.offX(g), -DPBoxLayout.offY(g), 0);
        DPBackdrop.draw(mc, DPBoxLayout.fullW(g), DPBoxLayout.fullH(g));
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        if (DPChrome.themable(g) || DPChrome.shouldSkinWidgets(g)) DPBox.drawFor(g);   // the sub-menu sits in a Pride box
    }

    /** Reflow the Options page buttons into inventory-slot rows (runs on open + resize).
     *  LOWEST priority + DPOptions handling so late mod-added buttons (PymTech, Inv Tweaks) get caught. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!DPConfig.blendAllScreens) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen g = event.getGui();
        // title-screen pages always; in-game our own settings screens + every themable vanilla/Forge sub-screen
        if (mc.world != null && !(g instanceof DPOptions) && !(g instanceof DPVideoSettings) && !DPChrome.themable(g)) return;
        if (DPChrome.shouldSkinWidgets(g) || g instanceof DPOptions) {
            DPSkin.reskinAll(event.getButtonList(), g);           // inventory-slot look
        }
        if (DPChrome.shouldReflow(g) || g instanceof DPOptions || g instanceof DPVideoSettings) {
            DPChrome.reflowOptions(event.getButtonList(), g.width, g.height);  // slot rows + right column
        }
    }

    /** Draw the page title banner + live memory bar on every menu screen. */
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        DPAnim.drawRipples();                                    // click ripples on any screen with Pride buttons
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null) return;
        GuiScreen g = event.getGui();
        if (g instanceof DPMainMenu || g instanceof DPWorldSelect || g instanceof DPServerSelect
                || g instanceof DPOptions || g instanceof DPVideoSettings) return;  // these draw their own
        // (the old full-width title banner is gone: every sub-menu is a box now, with its own title inside)
        DPMemoryBar.draw(0, g.height - 2, g.width, 2);
    }
}
