package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sub-menus in an Esc-menu-sized box (her ask 2026-09-30: "their little box in the center, not stretching all the
 * way up and down the screen — the same size box as the one you made for the escape menu").
 *
 * The screen is told it is only box-sized (width/height set in InitGuiEvent.Pre, before its initGui runs), so
 * vanilla's own layout code — lists, buttons, titles — arranges everything inside the box. It is then drawn
 * translated into the middle of the real screen, and every mouse position it sees is moved into box coordinates
 * (ForgeHooksClient.drawScreen + GuiScreen.handleMouseInput are patched to ask us, see core/DPTransformer).
 */
public final class DPBoxLayout {
    private static final Map<GuiScreen, int[]> BOXED = new WeakHashMap<>();   // {offsetX, offsetY, fullW, fullH}
    /** offset of the boxed screen being drawn right now (scissor rectangles need it — they ignore GL transforms) */
    public static int curX, curY;

    static boolean wants(GuiScreen g) {
        if (!DPConfig.blendAllScreens || !DPConfig.boxSubMenus || g == null) return false;
        if (g.getClass().getName().startsWith("com.dogpound.")) return false;          // ours lay themselves out
        String n = g.getClass().getSimpleName();
        if (n.equals("GuiMultiplayer") || n.equals("GuiWorldSelection") || n.equals("GuiMainMenu")) return false;
        return DPChrome.themable(g);
    }

    public static int[] of(GuiScreen g) { return g == null ? null : BOXED.get(g); }

    /** the box size: the same as the Pride pause menu's */
    private static int[] size(int fullW, int fullH) {
        PrideFrame f = PrideFrame.sized(fullW, fullH, 600, 330);
        return new int[]{f.w, f.h};
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void initPre(GuiScreenEvent.InitGuiEvent.Pre e) {
        GuiScreen g = e.getGui();
        if (!wants(g)) { BOXED.remove(g); return; }
        int fullW = g.width, fullH = g.height;
        int[] prev = BOXED.get(g);
        if (prev != null && g.width == prev[2] - 2 * prev[0] && g.height == prev[3] - 2 * prev[1]) return;   // already box-sized
        int[] s = size(fullW, fullH);
        if (fullW - s[0] < 40 && fullH - s[1] < 40) { BOXED.remove(g); return; }        // window is small: just use it all
        g.width = s[0];
        g.height = s[1];
        BOXED.put(g, new int[]{(fullW - s[0]) / 2, (fullH - s[1]) / 2, fullW, fullH});
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void drawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        int[] b = of(e.getGui());
        if (b == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(b[0], b[1], 0);
        curX = b[0]; curY = b[1];
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void drawPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        int[] b = of(e.getGui());
        if (b == null) return;
        GlStateManager.popMatrix();
        curX = 0; curY = 0;
    }

    // ------------------------------------------------------------------ mouse (called from patched vanilla code)
    public static int mapX(GuiScreen g, int x) { int[] b = of(g); return b == null ? x : x - b[0]; }
    public static int mapY(GuiScreen g, int y) { int[] b = of(g); return b == null ? y : y - b[1]; }

    private static Field fEventButton, fLastEvent, fTouch;
    private static Method mClicked, mReleased, mMove;

    /** GuiScreen.handleMouseInput for a boxed screen: vanilla's logic, with real-screen coordinates moved into the box */
    public static boolean handleMouse(GuiScreen g) {
        int[] b = of(g);
        if (b == null) return false;
        try {
            if (fEventButton == null) {
                fEventButton = ReflectionHelper.findField(GuiScreen.class, "eventButton", "field_146287_f");
                fLastEvent = ReflectionHelper.findField(GuiScreen.class, "lastMouseEvent", "field_146288_g");
                fTouch = ReflectionHelper.findField(GuiScreen.class, "touchValue", "field_146298_h");
                mClicked = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
                mReleased = ReflectionHelper.findMethod(GuiScreen.class, "mouseReleased", "func_146286_b", int.class, int.class, int.class);
                mMove = ReflectionHelper.findMethod(GuiScreen.class, "mouseClickMove", "func_146273_a", int.class, int.class, int.class, long.class);
            }
            Minecraft mc = Minecraft.getMinecraft();
            int i = Mouse.getEventX() * b[2] / mc.displayWidth - b[0];
            int j = b[3] - Mouse.getEventY() * b[3] / mc.displayHeight - 1 - b[1];
            int k = Mouse.getEventButton();
            boolean touch = mc.gameSettings.touchscreen;
            if (Mouse.getEventButtonState()) {
                int tv = fTouch.getInt(g);
                fTouch.setInt(g, tv + 1);
                if (touch && tv > 0) return true;
                fEventButton.setInt(g, k);
                fLastEvent.setLong(g, Minecraft.getSystemTime());
                mClicked.invoke(g, i, j, k);
            } else if (k != -1) {
                int tv = fTouch.getInt(g) - 1;
                fTouch.setInt(g, tv);
                if (touch && tv > 0) return true;
                fEventButton.setInt(g, -1);
                mReleased.invoke(g, i, j, k);
            } else if (fEventButton.getInt(g) != -1 && fLastEvent.getLong(g) > 0L) {
                mMove.invoke(g, i, j, fEventButton.getInt(g), Minecraft.getSystemTime() - fLastEvent.getLong(g));
            }
            return true;
        } catch (Throwable t) {
            return false;                                       // vanilla handles it (unboxed coordinates) — never break input
        }
    }

    /** the real screen size and the box offset, for things drawn outside the box (wallpaper, transitions) */
    public static int fullW(GuiScreen g) { int[] b = of(g); return b == null ? g.width : b[2]; }
    public static int fullH(GuiScreen g) { int[] b = of(g); return b == null ? g.height : b[3]; }
    public static int offX(GuiScreen g) { int[] b = of(g); return b == null ? 0 : b[0]; }
    public static int offY(GuiScreen g) { int[] b = of(g); return b == null ? 0 : b[1]; }
}
