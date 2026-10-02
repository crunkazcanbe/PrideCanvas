package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.List;

/** /pridecanvas <pause|options|video|controls|sounds|language|packs|skin|chat|lan|stats|mods>: open a screen the
 *  normal way (so the Pride replacements kick in) — for trying the menus, and for the automatic tests. */
public class DPCommand extends CommandBase {
    private static final String[] SCREENS = {"pause", "options", "video", "controls", "sounds", "language", "packs", "skin", "chat", "lan", "stats", "mods", "create", "web"};
    private static String next;

    public static void register() {
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new DPCommand());
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
                if (e.phase != TickEvent.Phase.END || next == null) return;
                String s = next; next = null;
                open(s);
            }
        });
    }

    @Override public String getName() { return "pridecanvas"; }
    @Override public String getUsage(ICommandSender s) { return "/pridecanvas <" + String.join("|", SCREENS) + ">"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) { next = args.length == 0 ? "pause" : args[0].toLowerCase(); }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SCREENS) : Collections.emptyList();
    }

    static void open(String s) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen pause = new GuiIngameMenu();
        switch (s) {
            case "pause": mc.displayInGameMenu(); return;
            case "options": mc.displayGuiScreen(new GuiOptions(pause, mc.gameSettings)); return;
            case "video": mc.displayGuiScreen(new GuiVideoSettings(new GuiOptions(pause, mc.gameSettings), mc.gameSettings)); return;
            case "controls": mc.displayGuiScreen(new net.minecraft.client.gui.GuiControls(new GuiOptions(pause, mc.gameSettings), mc.gameSettings)); return;
            case "sounds": mc.displayGuiScreen(new GuiScreenOptionsSounds(new GuiOptions(pause, mc.gameSettings), mc.gameSettings)); return;
            case "language": mc.displayGuiScreen(new GuiLanguage(new GuiOptions(pause, mc.gameSettings), mc.gameSettings, mc.getLanguageManager())); return;
            case "packs": mc.displayGuiScreen(new GuiScreenResourcePacks(new GuiOptions(pause, mc.gameSettings))); return;
            case "skin": mc.displayGuiScreen(new GuiCustomizeSkin(new GuiOptions(pause, mc.gameSettings))); return;
            case "chat": mc.displayGuiScreen(new ScreenChatOptions(new GuiOptions(pause, mc.gameSettings), mc.gameSettings)); return;
            case "lan": mc.displayGuiScreen(new GuiShareToLan(pause)); return;
            case "stats": if (mc.player != null) mc.displayGuiScreen(new net.minecraft.client.gui.achievement.GuiStats(pause, mc.player.getStatFileWriter())); return;
            case "web": DPWeb.open(null, DPWeb.homeUrl()); return;
            case "create": mc.displayGuiScreen(new DPCreateWorld(new DPMainMenu())); return;
            case "mods": mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(pause)); return;
            default:
        }
    }
}
