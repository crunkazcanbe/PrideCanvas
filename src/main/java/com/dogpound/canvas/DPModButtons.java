package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Every mod's inventory buttons, in our Pride screens (requested feature). A real vanilla inventory screen is set up off-screen so every mod
 * adds its buttons to it exactly as it always does (InitGuiEvent); we show those buttons, and a click runs exactly
 * what a click on the vanilla screen would: the mod's own mousePressed, the Forge ActionPerformed events, and the
 * screen's actionPerformed.
 */
public final class DPModButtons {
    private DPModButtons() {}

    private static Method actionPerformed;

    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    /** set `hidden` up at this size (fires every mod's InitGuiEvent) and return the buttons the mods added */
    @SuppressWarnings("unchecked")
    public static List<GuiButton> capture(GuiScreen hidden, int width, int height, int... vanillaIds) {
        List<GuiButton> out = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen prev = mc.currentScreen;
        try {
            mc.currentScreen = hidden;                 // many mods only add buttons to the screen on show
            hidden.setWorldAndResolution(mc, width, height);
            List<GuiButton> list = (List<GuiButton>) ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, hidden, "field_146292_n");
            outer:
            for (GuiButton b : list) {
                for (int v : vanillaIds) if (b.id == v && b.getClass() == GuiButton.class) continue outer;
                String cn = b.getClass().getName();
                if (cn.startsWith("com.dogpound.patches.client.SideDock")) continue;      // PridePatches' dock copies; we add the tabs ourselves
                if (b.width > 40 || b.height > 40) continue;   // ponytail: whole tab panels (Immersive Intelligence) don't fit a button slot
                if (b.visible) out.add(b);
                if (!LOGGED.contains(b.getClass().getName())) {
                    LOGGED.add(b.getClass().getName());
                    org.apache.logging.log4j.LogManager.getLogger("PrideCanvas").info("Pride: mod button {} id={} '{}' {}x{} visible={}", b.getClass().getName(), b.id, b.displayString, b.width, b.height, b.visible);
                }
            }
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("PrideCanvas").warn("Pride: couldn't collect mod buttons from {}: {}", hidden.getClass().getSimpleName(), t.toString());
        } finally {
            mc.currentScreen = prev;
        }
        out.addAll(tabs());
        return out;
    }

    /** draw a captured button (its mod may check that its own screen is the one on show) */
    public static void draw(GuiScreen hidden, GuiButton b, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen prev = mc.currentScreen;
        int x = b.x, y = b.y;
        boolean dock = System.getProperty("pridepatches.sidedock") == null;   // tells RealmCoin & co. we place their buttons
        try {
            if (dock) System.setProperty("pridepatches.sidedock", "pridecanvas");
            mc.currentScreen = hidden;
            b.drawButton(mc, mx, my, pt);
        } catch (Throwable ignored) {
        } finally {
            if (dock) System.clearProperty("pridepatches.sidedock");
            if (mc.currentScreen == hidden) mc.currentScreen = prev;
            b.x = x; b.y = y;                          // keep it where we put it, even if it moved itself
        }
    }

    /** FTB's sidebar and the like: no size of its own, it lays itself out against the screen edge */
    public static boolean edge(GuiButton b) { return b.width <= 0 || b.height <= 0; }

    /**
     * Techguns, Custom NPCs factions/quests, Lucraft… put their tabs (the shared Galacticraft tab list) only on the
     * survival inventory; here each becomes a button with the mod's icon that does what the tab does.
     */
    public static final class Tab extends GuiButton {
        final Object tab;
        public final String name;
        final net.minecraft.item.ItemStack icon;

        Tab(int id, Object tab, String name, net.minecraft.item.ItemStack icon) {
            super(id, 0, 0, 16, 16, "");
            this.tab = tab; this.name = name; this.icon = icon;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my, float pt) {
            if (!icon.isEmpty()) {
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(icon, x, y);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            } else mc.fontRenderer.drawStringWithShadow(name.substring(0, 1), x + 5, y + 4, 0xFFFFFF);
        }

        void open() {
            try { tab.getClass().getMethod("onTabClicked").invoke(tab); }
            catch (Throwable t) { org.apache.logging.log4j.LogManager.getLogger("PrideCanvas").warn("Pride: tab {} failed: {}", name, t.toString()); }
        }
    }

    static List<GuiButton> tabs() {
        List<GuiButton> out = new ArrayList<>();
        List<?> list;
        try { list = (List<?>) Class.forName("micdoodle8.mods.galacticraft.api.client.tabs.TabRegistry").getMethod("getTabList").invoke(null); }
        catch (Throwable t) { return out; }
        int id = 0x7C00;
        for (Object tab : list) {
            try {
                if (tab.getClass().getSimpleName().equals("InventoryTabVanilla")) continue;
                if (!(Boolean) tab.getClass().getMethod("shouldAddToList").invoke(tab)) continue;
                net.minecraft.item.ItemStack icon = net.minecraft.item.ItemStack.EMPTY;
                for (Class<?> c = tab.getClass(); c != null && icon.isEmpty(); c = c.getSuperclass())
                    for (java.lang.reflect.Field f : c.getDeclaredFields())
                        if (f.getType() == net.minecraft.item.ItemStack.class) { f.setAccessible(true); Object v = f.get(tab); if (v != null) icon = (net.minecraft.item.ItemStack) v; break; }
                String cls = tab.getClass().getSimpleName().replace("InventoryTab", "").replace("Tab", "").replaceAll("([a-z])([A-Z])", "$1 $2");
                String[] pk = tab.getClass().getName().split("\\.");
                String mod = pk.length > 2 && (pk[0].equals("com") || pk[0].equals("net") || pk[0].equals("org")) ? pk[1] : pk[0];
                out.add(new Tab(id++, tab, Character.toUpperCase(mod.charAt(0)) + mod.substring(1) + (cls.isEmpty() ? "" : ": " + cls), icon));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** click a captured button as the vanilla screen would */
    @SuppressWarnings("unchecked")
    public static void click(GuiScreen hidden, GuiButton b, int mx, int my) {
        if (b instanceof Tab) { b.playPressSound(Minecraft.getMinecraft().getSoundHandler()); ((Tab) b).open(); return; }
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen prev = mc.currentScreen;
        try {
            mc.currentScreen = hidden;
            if (!b.mousePressed(mc, mx, my)) return;
            List<GuiButton> list = (List<GuiButton>) ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, hidden, "field_146292_n");
            GuiScreenEvent.ActionPerformedEvent.Pre pre = new GuiScreenEvent.ActionPerformedEvent.Pre(hidden, b, list);
            if (MinecraftForge.EVENT_BUS.post(pre)) return;
            b = pre.getButton();
            b.playPressSound(mc.getSoundHandler());
            if (actionPerformed == null) actionPerformed = ObfuscationReflectionHelper.findMethod(GuiScreen.class, "func_146284_a", void.class, GuiButton.class);
            actionPerformed.invoke(hidden, b);
            MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Post(hidden, b, list));
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("PrideCanvas").warn("Pride: mod button {} failed: {}", b.displayString, t.toString());
        } finally {
            if (mc.currentScreen == hidden) mc.currentScreen = prev;   // the button opened nothing: stay on our screen
        }
    }
}
