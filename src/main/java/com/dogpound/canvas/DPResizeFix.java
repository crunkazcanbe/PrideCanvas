package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Patch: resizing the game window turns the world black and never comes back.
 *
 * Minecraft's own resize() does re-create the main framebuffer, so vanilla is fine.
 * The chunk renderer is not: Celeritas/Sodium (and the shader group, when a pack is
 * on) cache GL state sized to the framebuffer they were built against and never
 * notice that it was swapped out from under them. Result: the world renders into a
 * buffer nobody is reading -> black, forever, until you press F3+A.
 *
 * So do the F3+A ourselves, automatically, once the drag stops. Nothing here is
 * mod-specific -- it repairs the renderer whatever broke it, which is why it also
 * covers the shader-pack case and any future perf mod with the same blind spot.
 *
 * Debounced on purpose: dragging a window edge fires a resize every single frame,
 * and rebuilding every chunk on each of those would lock the game up hard.
 */
public class DPResizeFix {

    private static int lastW = -1, lastH = -1;
    private static long settleAt = 0L;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // ClientTick, NOT RenderTick. Touching the framebuffer or the chunk renderers
        // while a frame is still in flight is how you segfault the GL/Vulkan driver.
        // This is the same point in the loop where F3+A is handled.
        if (event.phase != TickEvent.Phase.END) return;
        if (!DPConfig.fixResizeBlackScreen) return;

        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth, h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;

        if (w != lastW || h != lastH) {
            lastW = w;
            lastH = h;
            settleAt = System.currentTimeMillis() + Math.max(0, DPConfig.resizeSettleMs);
            return;     // still moving -- wait for the drag to stop
        }

        if (settleAt == 0L || System.currentTimeMillis() < settleAt) return;
        settleAt = 0L;
        repair(mc, w, h);
    }

    /** Rebuild the chunk renderers -- the one thing Minecraft's own resize() does NOT do.
     *  The framebuffer and the shader group are deliberately left alone: resize() already
     *  handles both, and re-doing them from here segfaulted the driver. */
    private static void repair(Minecraft mc, int w, int h) {
        try {
            // The actual cure. Same call F3+A makes.
            if (mc.renderGlobal != null && mc.world != null) {
                mc.renderGlobal.loadRenderers();
            }
        } catch (Throwable t) {
            System.out.println("[DogPound] resize fix: renderer reload skipped: " + t);
        }
        System.out.println("[DogPound] resize fix: repaired renderer for " + w + "x" + h);
    }
}
