package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Her report 2026-09-28: PolyPatcher "absolutely does not work, no menu you can open". The menu is fine (/patcher
 * opens it), but OneConfig's own hotkey (F12 in her Preferences.json; Right Shift by default) never fires in this
 * pack — its key listener doesn't see Cleanroom's keyboard. A plain Minecraft key does, so this one runs /patcher.
 * Shows in Controls under "Pride UI"; rebind it there.
 */
public class DPPatcherKey {
    private static final KeyBinding KEY = new KeyBinding("Open PolyPatcher / OneConfig menu", Keyboard.KEY_RSHIFT, "Pride UI");

    static void register() {
        ClientRegistry.registerKeyBinding(KEY);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        while (KEY.isPressed())
            if (mc.player != null && mc.currentScreen == null) ClientCommandHandler.instance.executeCommand(mc.player, "/patcher");
    }
}
