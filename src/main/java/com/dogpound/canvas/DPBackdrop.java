package com.dogpound.canvas;

import net.minecraft.client.Minecraft;

/** Behind a Pride screen: the moving wallpaper on the title screen, the world softly tinted in trans colours in-game. */
public final class DPBackdrop {
    private DPBackdrop() {}

    public static void draw(Minecraft mc, int w, int h) {
        if (mc.world == null) { DPBackground.draw(mc, w, h); return; }
        // in-game: nothing — the game stays completely clear around the box, like the Pride pause menu
    }
}
