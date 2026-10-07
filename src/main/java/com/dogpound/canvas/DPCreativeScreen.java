package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pride Creative: the creative inventory, rebuilt in the Pride look (requested feature).
 * <pre>
 *  ┌ ✦ Pride Creative ───────────── [search……………] A-Z Z-A Mod Rarity Recent  ⊞ groups ┐
 *  │ All        │                                                                    │
 *  │ ★ Favorites│   every item in one scroll box, hover = pop + wiggle + chime,     │
 *  │ ⟲ Recent   │   rare items glow, enchanted shimmer; groups fan out on hover     │
 *  │ Building … │                                                                    │
 *  │ Mods (812) │────────────────────────────────────────────────────────────────────│
 *  │  …         │  armour · offhand · inventory · hotbar · trash · stack size          │
 *  └──────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Click an item: it rides your cursor; click an inventory slot to put it there (shift-click: straight into your
 * inventory, middle-click: a full stack). Right-click an item: favourite. Drop the cursor on the trash or the grid to
 * delete it. Uses the creative "set slot" packet like vanilla, so it works on servers too.
 */
public class DPCreativeScreen extends GuiScreen {
    // ---------------------------------------------------------------- saved state (favourites, recent, choices)

    public static final class Saved {
        public List<String> favorites = new ArrayList<>(), recent = new ArrayList<>();
        public String sort = "default";
        public boolean groups = true, families = true;
        public int stack = 64;
        public String filter = "All";
        public String size = "M";
    }

    private static final File FILE = new File("config/pridecanvas/creative.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Saved SV;

    static Saved sv() {
        if (SV == null) {
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { SV = GSON.fromJson(r, Saved.class); } catch (Throwable ignored) { }
            if (SV == null) SV = new Saved();
        }
        return SV;
    }

    static void save() { try { FILE.getParentFile().mkdirs(); try (Writer w = new FileWriter(FILE)) { GSON.toJson(sv(), w); } } catch (Throwable ignored) { } }

    // ---------------------------------------------------------------- the item catalogue (built once)

    static final class Entry {
        final ItemStack stack; final String name, lower, mod, tab, key; final int order;
        Entry(ItemStack s, String tab, int order) {
            stack = s; this.tab = tab; this.order = order;
            String n;
            try { n = s.getDisplayName(); } catch (Throwable t) { n = String.valueOf(s.getItem().getRegistryName()); }
            name = n == null ? "?" : n;
            ResourceLocation rl = s.getItem().getRegistryName();
            mod = rl == null ? "minecraft" : rl.getResourceDomain();
            lower = (name + " @" + mod + " " + rl).toLowerCase(Locale.ROOT);
            key = rl + "@" + s.getMetadata();
        }
    }

    private static List<Entry> ALL;
    private static final Map<String, Integer> MODS = new LinkedHashMap<>();
    private static final Map<String, String> MOD_NAMES = new LinkedHashMap<>();

    // Building the list walks every creative tab of every mod (954 mods ≈ 90 s in one go, 2026-10-06), so it is done
    // in slices: ~8 ms per client tick while she plays (tickBuild), and build() finishes whatever is left on demand.
    private static List<Entry> BUILDING;
    private static Set<String> SEEN;
    private static int nextTab, buildOrder;
    private static java.util.Iterator<net.minecraft.item.Item> regIt;

    static void build() {
        if (ALL != null) return;
        while (!step(Long.MAX_VALUE)) { }
    }

    /** a slice of the build; called every client tick from DPCreativeHook while in a world */
    static void tickBuild() {
        if (ALL != null || Minecraft.getMinecraft().world == null) return;
        step(8_000_000L);
    }

    /** returns true when the list is finished */
    private static boolean step(long budgetNs) {
        long t0 = System.nanoTime();
        if (BUILDING == null) { BUILDING = new ArrayList<>(); SEEN = new LinkedHashSet<>(); nextTab = 0; buildOrder = 0; regIt = null; }
        CreativeTabs[] tabs = CreativeTabs.CREATIVE_TAB_ARRAY;
        while (nextTab < tabs.length) {
            CreativeTabs tab = tabs[nextTab++];
            if (tab == null || tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY || tab == CreativeTabs.HOTBAR) continue;
            NonNullList<ItemStack> list = NonNullList.create();
            try { tab.displayAllRelevantItems(list); } catch (Throwable ignored) { }
            String label;
            try { label = net.minecraft.client.resources.I18n.format(tab.getTranslatedTabLabel()); } catch (Throwable t) { label = tab.getTabLabel(); }
            for (ItemStack s : list) {
                if (s.isEmpty()) continue;
                String k = s.getItem().getRegistryName() + "@" + s.getMetadata() + (s.hasTagCompound() ? s.getTagCompound().toString() : "");
                if (!SEEN.add(k)) continue;
                BUILDING.add(new Entry(s.copy(), label, buildOrder++));
            }
            if (System.nanoTime() - t0 > budgetNs) return false;
        }
        // items the mods hid from every creative tab still count for their mod (they're in the registry)
        if (regIt == null) {
            Set<String> withItems = new java.util.HashSet<>();
            for (Entry e : BUILDING) withItems.add(e.mod);
            SEEN.add("§mods:" + String.join(",", withItems));
            regIt = net.minecraft.item.Item.REGISTRY.iterator();
        }
        Set<String> haveMod = new java.util.HashSet<>();
        for (Entry e : BUILDING) haveMod.add(e.mod);
        while (regIt.hasNext()) {
            net.minecraft.item.Item it = regIt.next();
            ResourceLocation rl = it.getRegistryName();
            if (rl == null || haveMod.contains(rl.getResourceDomain())) continue;
            ItemStack st;
            try { st = new ItemStack(it); } catch (Throwable t) { continue; }
            if (!st.isEmpty() && SEEN.add(rl + "@0")) BUILDING.add(new Entry(st, "Hidden items", buildOrder++));
            if (System.nanoTime() - t0 > budgetNs) return false;
        }
        Map<String, Integer> mods = new LinkedHashMap<>();
        for (Entry e : BUILDING) mods.merge(e.mod, 1, Integer::sum);
        for (net.minecraftforge.fml.common.ModContainer m : net.minecraftforge.fml.common.Loader.instance().getModList()) MOD_NAMES.put(m.getModId(), m.getName());
        MOD_NAMES.put("minecraft", "Minecraft");
        // Requested: "make sure it lists every mod" - mods with no items (libraries, tweaks) too, with 0
        for (net.minecraftforge.fml.common.ModContainer m : net.minecraftforge.fml.common.Loader.instance().getActiveModList())
            mods.putIfAbsent(m.getModId(), 0);
        MODS.clear();
        MODS.putAll(mods);
        ALL = BUILDING;
        BUILDING = null; SEEN = null; regIt = null;
        return true;
    }

    // ---------------------------------------------------------------- view state

    private GuiTextField search;
    private List<Object> view = new ArrayList<>();            // Entry, or List<Entry> for a group
    private int scroll, sideScroll, cols, rows, gx, gy, cell = 18;
    private static int invScroll;
    private int[] invBox = {0, 0, 0, 0};
    /** where the grid is drawn (in rows): glides toward `scroll` each frame so the wheel feels smooth */
    private float smooth;
    private long smoothAt;
    private ItemStack cursor = ItemStack.EMPTY;
    private Object hoveredTile, groupOpen;
    private long hoverStart, groupOpenedAt;
    private int groupX, groupY;
    private String tip;
    private ItemStack tipStack = ItemStack.EMPTY;
    private final List<Object[]> hits = new ArrayList<>();
    private static final String[] SORTS = {"default", "a-z", "z-a", "mod", "rarity", "recent"};

    @Override public boolean doesGuiPauseGame() { return false; }

    private net.minecraft.client.gui.inventory.GuiContainerCreative hidden;
    private List<net.minecraft.client.gui.GuiButton> modButtons = new ArrayList<>();

    @Override
    public void initGui() {
        build();
        // every mod's inventory buttons (Baubles, bubbles, money, maps…): let them set up on a hidden vanilla screen
        if (hidden != null) hidden.onGuiClosed();
        hidden = new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.player);
        // Baubles & co. only show their buttons on the inventory tab: the hidden screen sits on it while we're open
        if (savedTab < 0) savedTab = creativeTab(net.minecraft.creativetab.CreativeTabs.INVENTORY.getTabIndex());
        modButtons = DPModButtons.capture(hidden, width, height, 101, 102);
        Keyboard.enableRepeatEvents(true);
        search = new GuiTextField(0, fontRenderer, 0, 0, 120, 12);
        search.setMaxStringLength(80);
        search.setFocused(false);                      // click the box to type (requested feature)
        search.setEnableBackgroundDrawing(false);
        refilter();
    }

    private static int savedTab = -1;

    /** vanilla's remembered creative tab (static); sets it, returns the old one */
    private static int creativeTab(int i) {
        try {
            int old = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainerCreative.class, null, "field_147058_w");
            net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.client.gui.inventory.GuiContainerCreative.class, null, i, "field_147058_w");
            return old;
        } catch (Throwable t) { return -1; }
    }

    @Override public void onGuiClosed() { if (savedTab >= 0) { creativeTab(savedTab); savedTab = -1; } Keyboard.enableRepeatEvents(false); save(); if (!cursor.isEmpty()) cursor = ItemStack.EMPTY; if (hidden != null) hidden.onGuiClosed(); }

    private void refilter() {
        Saved s = sv();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> list = new ArrayList<>();
        Set<String> favs = new LinkedHashSet<>(s.favorites);
        if (s.filter.equals("★ Favorites")) { for (Entry e : ALL) if (favs.contains(e.key)) list.add(e); }
        else if (s.filter.equals("⟲ Recent")) { for (String k : s.recent) for (Entry e : ALL) if (e.key.equals(k)) { list.add(e); break; } }
        else for (Entry e : ALL) {
            if (s.filter.startsWith("mod:") ? !e.mod.equals(s.filter.substring(4)) : !s.filter.equals("All") && !e.tab.equals(s.filter)) continue;
            list.add(e);
        }
        if (!q.isEmpty()) {
            String[] words = q.split("\\s+");
            list.removeIf(e -> { for (String w : words) if (!e.lower.contains(w)) return true; return false; });
        }
        switch (s.sort) {
            case "a-z": list.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT))); break;
            case "z-a": list.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT)).reversed()); break;
            case "mod": list.sort(Comparator.comparing((Entry e) -> e.mod).thenComparingInt(e -> e.order)); break;
            case "rarity": list.sort(Comparator.comparingInt((Entry e) -> -e.stack.getRarity().ordinal()).thenComparingInt(e -> e.order)); break;
            case "recent": { List<String> rc = s.recent; list.sort(Comparator.comparingInt(e -> { int i = rc.indexOf(e.key); return i < 0 ? 99999 : i; })); break; }
            default:
        }
        view.clear();
        if (s.groups && q.isEmpty() && !s.filter.equals("⟲ Recent")) {
            // compact as much as possible (requested feature): every item's variants fold into one tile, and with smart families a
            // mod's stairs / slabs / walls / facades / ingots… fold together too. Hovering a tile fans it out.
            Map<String, List<Entry>> by = new LinkedHashMap<>();
            for (Entry e : list) by.computeIfAbsent(groupKey(e, s.families), k -> new ArrayList<>()).add(e);
            Set<String> done = new LinkedHashSet<>();
            for (Entry e : list) {
                String k = groupKey(e, s.families);
                if (!done.add(k)) continue;
                List<Entry> g = by.get(k);
                view.add(g.size() > 1 ? g : g.get(0));
            }
        } else view.addAll(list);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** words at the end of a registry name that make a "family" worth folding together */
    static final Set<String> FAMILIES = new LinkedHashSet<>(java.util.Arrays.asList(("stairs slab slabs wall walls fence gate pane panes door doors trapdoor button plate carpet "
            + "glass wool planks log logs leaves sapling ore ores ingot ingots nugget nuggets dust dusts gear gears rod rods block blocks brick bricks tile tiles "
            + "lamp facade facades cover covers cable cables pipe pipes chest sword pickaxe axe shovel hoe helmet chestplate leggings boots bucket seeds "
            + "fluid crystal gem gems shard shards wire wires casing frame frames pillar statue sign bed banner pot vase concrete terracotta").split(" ")));

    static String groupKey(Entry e, boolean families) {
        ResourceLocation rl = e.stack.getItem().getRegistryName();
        String item = String.valueOf(rl);
        if (!families || rl == null) return item;
        String path = rl.getResourcePath();
        int us = path.lastIndexOf('_');
        String last = us >= 0 ? path.substring(us + 1) : path;
        return FAMILIES.contains(last) && us >= 0 ? rl.getResourceDomain() + ":*_" + last : item;
    }

    /** a group's title: the item name, or "Stairs · ModName" for a family */
    static String label(List<?> g) {
        Entry a = (Entry) g.get(0), b = (Entry) g.get(g.size() - 1);
        if (a.stack.getItem() == b.stack.getItem()) return a.name;
        String path = String.valueOf(a.stack.getItem().getRegistryName().getResourcePath());
        String last = path.substring(path.lastIndexOf('_') + 1);
        return Character.toUpperCase(last.charAt(0)) + last.substring(1) + " · " + MOD_NAMES.getOrDefault(a.mod, a.mod);
    }

    private int maxScroll() { return cols <= 0 ? 0 : Math.max(0, (view.size() + cols - 1) / cols - rows); }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        tip = null;
        tipStack = ItemStack.EMPTY;
        Saved s = sv();
        // a compact window in the middle that fits any screen (requested feature): S / M (default) / L
        PrideFrame f = s.size.equals("L") ? PrideFrame.fit(width, height) : s.size.equals("S") ? PrideFrame.sized(width, height, 470, 262) : PrideFrame.sized(width, height, 560, 300);
        f.draw(this, "Pride Creative", "§7" + count() + " items · " + MODS.size() + " mods with items (of " + net.minecraftforge.fml.common.Loader.instance().getActiveModList().size() + ") · §d" + view.size() + " tiles");
        int side = Math.min(120, Math.max(90, f.cw / 6));
        int invH = 18 * 4 + 22 + (extraRowCount() > 0 ? EXTRA_VIS * 18 + 22 : 0);   // + PrideInventory's extra-rows panel
        int top = f.cy + 18, left = f.cx + side + 8, right = f.cx + f.cw, bottom = f.cy + f.ch - invH - 6;
        if (!modButtons.isEmpty()) left += modStrip(mx, my, pt, left, top, f.cy + f.ch - top) + 6;

        // -------- top bar: search + sorts + groups
        int sx = left;
        PrideFrame.card(sx, f.cy, 150, 14, PrideFrame.PINK);
        search.x = sx + 4; search.y = f.cy + 3; search.width = 142;
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) fontRenderer.drawString("§8Search… (@mod)", sx + 4, f.cy + 3, 0xFFFFFF);
        else if (search.getText().isEmpty()) fontRenderer.drawString("§8Search… type, or @mod", sx + 6, f.cy + 3, 0xFFFFFF);
        int bx = sx + 156;
        for (String so : SORTS) {
            String label = so.equals("default") ? "Tabs" : so.equals("a-z") ? "A-Z" : so.equals("z-a") ? "Z-A" : Character.toUpperCase(so.charAt(0)) + so.substring(1);
            int w = fontRenderer.getStringWidth(label) + 10;
            if (bx + w > right - 172) break;
            boolean on = s.sort.equals(so);
            btn(mx, my, bx, f.cy, w, 14, label, on ? PrideFrame.TILE_ON : PrideFrame.BUTTON, () -> { s.sort = so; refilter(); }, "Sort: " + label);
            bx += w + 3;
        }
        btn(mx, my, right - 66, f.cy, 66, 14, (s.groups ? "§a⊞" : "§7⊟") + " Groups", PrideFrame.BUTTON, () -> { s.groups = !s.groups; refilter(); },
                "Fold variants (all wool colours, all planks…) into one tile that fans out when you hover it");
        btn(mx, my, right - 168, f.cy, 30, 14, "§d" + s.size, PrideFrame.BUTTON, () -> { s.size = s.size.equals("S") ? "M" : s.size.equals("M") ? "L" : "S"; save(); },
                "Window size: S / M / L (S fits small screens)");
        btn(mx, my, right - 136, f.cy, 66, 14, (s.families ? "§a✦" : "§7✧") + " Families", PrideFrame.BUTTON, () -> { s.families = !s.families; refilter(); },
                "Fold even more: a mod's stairs, slabs, walls, facades, ingots… each become one tile");

        // -------- left: categories + mods
        sidebar(mx, my, f.cx, f.cy, side, f.y + f.h - 8 - f.cy);

        // -------- grid
        cols = Math.max(1, (right - left - 6) / cell);
        rows = Math.max(1, (bottom - top) / cell);
        gx = left; gy = top;
        PrideFrame.card(left - 3, top - 3, cols * cell + 6, rows * cell + 6, PrideFrame.RAINBOW[5]);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        long now = System.currentTimeMillis();
        float k = 1f - (float) Math.pow(0.0001, Math.min(100, now - smoothAt) / 1000.0);   // ~frame-rate independent ease-out
        smoothAt = now;
        smooth += (scroll - smooth) * k;
        if (Math.abs(scroll - smooth) < 0.01f) smooth = scroll;
        int first = (int) Math.floor(smooth), shift = Math.round((smooth - first) * cell);
        Object hov = null;
        int hovX = 0, hovY = 0;
        scissor(gx, gy, cols * cell, rows * cell);
        RenderHelper.enableGUIStandardItemLighting();
        for (int r = 0; r <= rows; r++) for (int c = 0; c < cols; c++) {
            int i = (first + r) * cols + c;
            if (i >= view.size()) break;
            Object o = view.get(i);
            int x = gx + c * cell, y = gy + r * cell - shift;
            boolean over = mx >= x && my >= Math.max(y, gy) && mx < x + cell && my < Math.min(y + cell, gy + rows * cell) && groupOpen == null;
            if (over) { hov = o; hovX = x; hovY = y; }
            drawTile(o, x, y, over);
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (hov != hoveredTile) {
            hoveredTile = hov;
            hoverStart = System.currentTimeMillis();
            if (hov != null) DPItemFx.sound(first(hov).stack, (hovX - gx) / cell);
            if (hov instanceof List) { groupOpen = hov; groupOpenedAt = System.currentTimeMillis(); groupX = hovX; groupY = hovY; }
        }
        if (hov instanceof Entry) { tipStack = ((Entry) hov).stack; }
        else if (hov instanceof List) tip = "§d⊞ " + label((List<?>) hov) + " §7· " + ((List<?>) hov).size() + " kinds";
        PrideFrame.scrollbar(right - 3, top, rows * cell, scroll, rows, (view.size() + cols - 1) / cols);
        if (view.isEmpty()) fontRenderer.drawStringWithShadow("§7Nothing here" + (s.filter.equals("★ Favorites") ? " yet: right-click any item to make it a favourite ★" : ""), left + 6, top + 6, 0xFFFFFF);

        // -------- bottom: inventory, trash, stack size
        inventory(mx, my, left, bottom + 8, right);

        // -------- open group fan-out (on top of everything)
        if (groupOpen != null) drawGroup(mx, my);

        // -------- cursor item + tooltip
        if (!cursor.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 400);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(cursor, mx - 8, my - 8);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, cursor, mx - 8, my - 8, null);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        } else if (!tipStack.isEmpty()) {
            renderToolTip(tipStack, mx, my);
        }
        if (tip != null && tipStack.isEmpty()) drawHoveringText(tip, mx, my);
        super.drawScreen(mx, my, pt);
    }

    /** the mods' buttons in a Pride strip (wraps into more columns if there are lots); returns its width */
    /** where each strip button sits (its own x/y stay where its mod put them, so its clicks still land) */
    private final java.util.Map<net.minecraft.client.gui.GuiButton, int[]> slots = new java.util.IdentityHashMap<>();

    private int modStrip(int mx, int my, float pt, int x, int y, int h) {
        java.util.List<net.minecraft.client.gui.GuiButton> strip = new ArrayList<>();
        for (net.minecraft.client.gui.GuiButton b : modButtons) {
            if (!b.visible) continue;                         // its mod hid it (wrong tab, feature off…)
            if (!DPModButtons.edge(b)) { strip.add(b); continue; }
            DPModButtons.draw(hidden, b, mx, my, pt);         // FTB's sidebar: where it always is
        }
        slots.clear();
        if (strip.isEmpty()) return 0;
        final int S = 18;
        int perCol = Math.max(1, (h - 6) / (S + 2)), colsN = (strip.size() + perCol - 1) / perCol, w = colsN * (S + 2) + 6;
        PrideFrame.card(x, y - 3, w, Math.min(strip.size(), perCol) * (S + 2) + 6, PrideFrame.BLUE);
        for (int i = 0; i < strip.size(); i++) {
            net.minecraft.client.gui.GuiButton b = strip.get(i);
            int bx = x + 3 + (i / perCol) * (S + 2), by = y + (i % perCol) * (S + 2);
            slots.put(b, new int[]{bx, by});
            boolean over = mx >= bx && my >= by && mx < bx + S && my < by + S;
            PrideFrame.tile(bx, by, S, S, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, false);
            GlStateManager.color(1, 1, 1, 1);
            // the mod's own button, shrunk/centred into one even 16x16 slot
            int ox = b.x, oy = b.y;
            float sc = Math.min(1f, 16f / Math.max(b.width, b.height));
            GlStateManager.pushMatrix();
            GlStateManager.translate(bx + 1 + (16 - b.width * sc) / 2f, by + 1 + (16 - b.height * sc) / 2f, 0);
            GlStateManager.scale(sc, sc, 1);
            b.x = 0; b.y = 0;
            DPModButtons.draw(hidden, b, over ? b.width / 2 : -1000, over ? b.height / 2 : -1000, pt);
            b.x = ox; b.y = oy;
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
            if (over) tip = b instanceof DPModButtons.Tab ? ((DPModButtons.Tab) b).name
                    : b.displayString != null && !b.displayString.trim().isEmpty() ? modOf(b) + ": " + b.displayString : modOf(b);
        }
        return w;
    }

    private static String modOf(net.minecraft.client.gui.GuiButton b) {
        String c = b.getClass().getName();
        for (net.minecraftforge.fml.common.ModContainer m : net.minecraftforge.fml.common.Loader.instance().getModList())
            if (m.getMod() != null && c.startsWith(m.getMod().getClass().getPackage().getName())) return m.getName();
        return c.substring(c.lastIndexOf('.') + 1);
    }

    /** clip drawing to a GUI-space box */
    private void scissor(int x, int y, int w, int h) {
        double sx = mc.displayWidth / (double) width, sy = mc.displayHeight / (double) height;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * sx), (int) (mc.displayHeight - (y + h) * sy), (int) Math.ceil(w * sx), (int) Math.ceil(h * sy));
    }

    private int count() { return ALL == null ? 0 : ALL.size(); }

    private static Set<String> FAVSET;
    private static int favSetSize = -1;

    private static Set<String> favSet() {
        if (FAVSET == null || favSetSize != sv().favorites.size()) { FAVSET = new java.util.HashSet<>(sv().favorites); favSetSize = sv().favorites.size(); }
        return FAVSET;
    }

    private static Entry first(Object o) { return o instanceof Entry ? (Entry) o : (Entry) ((List<?>) o).get(0); }

    /** one grid tile: rarity glow, favourite star, group badge, and the hover pop + wiggle */
    private void drawTile(Object o, int x, int y, boolean over) {
        Entry e = first(o);
        int rc = DPItemFx.rarityColor(e.stack);
        if (over) Gui.drawRect(x, y, x + cell, y + cell, 0x60F5A9B8);
        else if (rc >= 0 && DPItemFx.get().rarityGlow) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 350.0 + e.order);
            Gui.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, ((int) (70 * pulse) << 24) | rc);
        }
        GlStateManager.pushMatrix();
        if (over) {
            DPItemFx.Settings s = DPItemFx.get();
            float t = (System.currentTimeMillis() - hoverStart) / 1000f, k = s.wiggleStrength / 100f;
            float angle = s.wiggle ? (float) (24 * k * Math.exp(-t * 3.2) * Math.sin(t * 30) + 4 * k * Math.sin(t * 5)) : 0;
            float sc = s.popOut ? 1f + (float) (0.35 * k * Math.exp(-t * 6) + 0.18 * k) : 1f;
            float bob = s.bob ? (float) (Math.sin(t * 4.2) * 0.9 * k) * (float) Math.min(1, t * 2) : 0;
            GlStateManager.translate(x + 9, y + 9 + bob, 50);
            GlStateManager.rotate(angle, 0, 0, 1);
            GlStateManager.scale(sc, sc, 1);
            GlStateManager.translate(-(x + 9), -(y + 9), 0);
        }
        itemRender.renderItemAndEffectIntoGUI(e.stack, x + 1, y + 1);
        GlStateManager.popMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        if (o instanceof List) {          // group badge: a little stack of coloured bars + count
            Gui.drawRect(x + 11, y + 12, x + 17, y + 17, 0xC0140E22);
            fontRenderer.drawStringWithShadow(String.valueOf(Math.min(99, ((List<?>) o).size())), x + 18 - fontRenderer.getStringWidth(String.valueOf(Math.min(99, ((List<?>) o).size()))), y + 10, 0xF5A9B8);
        }
        if (favSet().contains(e.key)) fontRenderer.drawStringWithShadow("★", x + 1, y, 0xFFE14A);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
    }

    /** a group's items fanned out in a little panel next to the tile, growing open */
    private void drawGroup(int mx, int my) {
        @SuppressWarnings("unchecked") List<Entry> g = (List<Entry>) groupOpen;
        int n = g.size(), c = Math.min(n > 81 ? 12 : 9, n), rAll = (n + c - 1) / c, r = Math.min(rAll, 8);
        groupScroll = Math.max(0, Math.min(groupScroll, rAll - r));
        float t = Math.min(1f, (System.currentTimeMillis() - groupOpenedAt) / 140f);
        float ease = 1 - (1 - t) * (1 - t);
        int w = c * cell + 8, h = r * cell + 18;
        int x = Math.min(width - w - 4, groupX - 4), y = groupY + cell + 2;
        if (y + h > height - 4) y = groupY - h - 2;
        // close it once the mouse leaves both the tile and the panel
        boolean inTile = mx >= groupX && my >= groupY && mx < groupX + cell && my < groupY + cell;
        boolean inPanel = mx >= x && my >= y - 4 && mx < x + w && my < y + h;
        if (!inTile && !inPanel) { groupOpen = null; hoveredTile = null; groupScroll = 0; return; }
        groupBox = new int[]{x, y, w, h};
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        GlStateManager.translate(groupX + 9, groupY + 9, 0);
        GlStateManager.scale(ease, ease, 1);
        GlStateManager.translate(-(groupX + 9), -(groupY + 9), 0);
        PrideFrame.gradient(x, y, x + w, y + h, 0xF41C1530, 0xF4080510);
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) { int sw = w / PrideFrame.RAINBOW.length; Gui.drawRect(x + i * sw, y, i == PrideFrame.RAINBOW.length - 1 ? x + w : x + (i + 1) * sw, y + 2, PrideFrame.RAINBOW[i]); }
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth("§d" + label(g) + " §7(" + n + (rAll > r ? ", scroll" : "") + ")", w - 8), x + 4, y + 5, 0xFFFFFF);
        RenderHelper.enableGUIStandardItemLighting();
        Entry hov = null;
        for (int i = groupScroll * c; i < n && i < (groupScroll + r) * c; i++) {
            int ix = x + 4 + (i % c) * cell, iy = y + 15 + (i / c - groupScroll) * cell;
            boolean over = t >= 1 && mx >= ix && my >= iy && mx < ix + cell && my < iy + cell;
            if (over) { hov = g.get(i); Gui.drawRect(ix, iy, ix + cell, iy + cell, 0x60F5A9B8); }
            // fan-out: each item slides out from the group tile in turn
            float d = Math.max(0, Math.min(1, ease * 1.6f - i * 0.03f));
            int ox = (int) ((groupX - ix) * (1 - d)), oy = (int) ((groupY - iy) * (1 - d));
            itemRender.renderItemAndEffectIntoGUI(g.get(i).stack, ix + 1 + ox, iy + 1 + oy);
            final Entry pick = g.get(i);
            hits.add(new Object[]{ix, iy, cell, cell, (Runnable) () -> take(pick, false), pick});
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        if (hov != null) { tipStack = hov.stack; if (hov != hoveredSub) { hoveredSub = hov; DPItemFx.sound(hov.stack, g.indexOf(hov)); } }
    }

    private Entry hoveredSub;
    private int groupScroll;
    private int[] groupBox = {0, 0, 0, 0};

    private void sidebar(int mx, int my, int x, int y, int w, int h) {
        Saved s = sv();
        PrideFrame.card(x, y, w, h, PrideFrame.PINK);
        List<String[]> items = sidebarItems();
        int rowH = 11, vis = (h - 6) / rowH;
        sideScroll = Math.max(0, Math.min(sideScroll, items.size() - vis));
        PrideFrame.clip(x, y + 2, w, h - 4);
        for (int i = 0; i < vis && i + sideScroll < items.size(); i++) {
            String[] it = items.get(i + sideScroll);
            int ry = y + 4 + i * rowH;
            if (it[0].equals("-")) { fontRenderer.drawString(it[1], x + 4, ry + 2, 0xFFFFFF); continue; }
            boolean on = s.filter.equals(it[0]), over = mx >= x && my >= ry && mx < x + w && my < ry + rowH;
            if (on || over) Gui.drawRect(x + 2, ry, x + w - 5, ry + rowH, on ? PrideFrame.TILE_ON : 0xFF2A2140);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(it[1], w - 30), x + 5, ry + 2, on ? 0xFFFFFF : 0xD8D0E8);
            if (!it[2].isEmpty()) fontRenderer.drawString("§8" + it[2], x + w - 7 - fontRenderer.getStringWidth(it[2]), ry + 2, 0xFFFFFF);
            final String key = it[0];
            hits.add(new Object[]{x, ry, w, rowH, (Runnable) () -> { s.filter = key; scroll = 0; refilter(); }});
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 4, y + 2, h - 4, sideScroll, vis, items.size());
        sideBox = new int[]{x, y, w, h};
    }

    private int[] sideBox = {0, 0, 0, 0};
    private static List<String[]> SIDE;
    private static int sideFavs = -1, sideRecent = -1;

    /** the sidebar rows, built once (it used to walk every item every frame: the stutter and HUD flicker, 2026-10-04) */
    private static List<String[]> sidebarItems() {
        Saved s = sv();
        if (SIDE != null && sideFavs == s.favorites.size() && sideRecent == s.recent.size()) return SIDE;
        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"All", "All", String.valueOf(ALL.size())});
        items.add(new String[]{"★ Favorites", "★ Favorites", String.valueOf(s.favorites.size())});
        items.add(new String[]{"⟲ Recent", "⟲ Recent", String.valueOf(s.recent.size())});
        Set<String> tabs = new LinkedHashSet<>();
        for (Entry e : ALL) tabs.add(e.tab);
        items.add(new String[]{"-", "§8TABS", ""});
        for (String tb : tabs) items.add(new String[]{tb, tb, ""});
        items.add(new String[]{"-", "§8MODS", ""});
        List<Map.Entry<String, Integer>> mods = new ArrayList<>(MODS.entrySet());
        mods.sort(Comparator.comparing(m -> MOD_NAMES.getOrDefault(m.getKey(), m.getKey()).toLowerCase(Locale.ROOT)));
        for (Map.Entry<String, Integer> m : mods) items.add(new String[]{"mod:" + m.getKey(), MOD_NAMES.getOrDefault(m.getKey(), m.getKey()), String.valueOf(m.getValue())});
        SIDE = items;
        sideFavs = s.favorites.size();
        sideRecent = s.recent.size();
        return items;
    }

    private static final int EXTRA_VIS = 2;   // extra rows visible at once (the rest scroll)

    private int extraRowCount() {
        return mc.player == null ? 0 : Math.max(0, (mc.player.inventoryContainer.inventorySlots.size() - 46) / 9);
    }

    /** PrideInventory's extra rows under the hotbar, like its own panel in the survival inventory: scroll with the
     *  wheel, page arrows that flip the hotbar through the rows, and the unlock button (her 2026-10-05 ask) */
    private void extraPanel(int mx, int my, int x, int y, int extraRows) {
        EntityPlayer p = mc.player;
        invScroll = Math.max(0, Math.min(invScroll, Math.max(0, extraRows - EXTRA_VIS)));
        invBox = new int[]{x, y, 9 * 18, EXTRA_VIS * 18};
        for (int r = 0; r < EXTRA_VIS && invScroll + r < extraRows; r++) {
            int row = invScroll + r;
            for (int c = 0; c < 9; c++) {
                int cs = 46 + row * 9 + c;
                net.minecraft.inventory.Slot sl = p.inventoryContainer.getSlot(cs);
                boolean open = sl.isItemValid(new ItemStack(net.minecraft.init.Items.STICK));
                slot(mx, my, x + c * 18, y + r * 18, cs, sl.getStack(), -1);
                if (!open) {
                    Gui.drawRect(x + c * 18 + 1, y + r * 18 + 1, x + c * 18 + 17, y + r * 18 + 17, 0xA0100818);
                    if (mx >= x + c * 18 && my >= y + r * 18 && mx < x + c * 18 + 18 && my < y + r * 18 + 18) tip = "§7Row " + (row + 1) + " is locked: unlock it with the §d+ Unlock§7 button";
                }
            }
            fontRenderer.drawString("§d" + (row + 1), x - 12, y + r * 18 + 5, 0xFFFFFF);
        }
        if (extraRows > EXTRA_VIS) {                      // scroll bar
            int bh = EXTRA_VIS * 18, th = Math.max(6, bh * EXTRA_VIS / extraRows), ty = y + (bh - th) * invScroll / Math.max(1, extraRows - EXTRA_VIS);
            Gui.drawRect(x + 9 * 18 + 1, y, x + 9 * 18 + 3, y + bh, 0x40FFFFFF);
            Gui.drawRect(x + 9 * 18 + 1, ty, x + 9 * 18 + 3, ty + th, 0xFFF5A9B8);
        }
        // footer: ◀ hotbar page ▶   + Unlock
        int fy = y + EXTRA_VIS * 18 + 4;
        int[] pg = pageInfo();
        btn(mx, my, x, fy, 14, 12, "◀", PrideFrame.BUTTON, () -> invMsg("Flip", -1), "Previous hotbar page (your hotbar swaps with an extra row)");
        String lbl = pg == null ? "Hotbar" : "Hotbar page " + (pg[0] + 1) + "/" + (pg[1] + 1);
        fontRenderer.drawString("§7" + lbl, x + 18, fy + 2, 0xFFFFFF);
        int ax = x + 22 + fontRenderer.getStringWidth(lbl);
        btn(mx, my, ax, fy, 14, 12, "▶", PrideFrame.BUTTON, () -> invMsg("Flip", 1), "Next hotbar page");
        btn(mx, my, ax + 20, fy, 52, 12, "+ Unlock", PrideFrame.BUTTON, () -> invMsg("Buy", 0), "Unlock another row (costs coins / progress, see the survival inventory tooltip)");
        fontRenderer.drawString("§8" + extraRows + " rows · wheel to scroll", ax + 78, fy + 2, 0xFFFFFF);
    }

    /** {page shown, page rows} from PrideInventory, or null when it isn't installed */
    private int[] pageInfo() {
        try {
            Class<?> ei = Class.forName("com.dogpound.prideinventory.ExtraInv");
            Object inv = ei.getMethod("of", net.minecraft.entity.player.EntityPlayer.class).invoke(null, mc.player);
            if (inv == null) return null;
            int page = ei.getField("page").getInt(inv), unlocked = ei.getField("unlocked").getInt(inv);
            int max = Class.forName("com.dogpound.prideinventory.Config").getField("hotbarPages").getInt(null);
            return new int[]{page, Math.max(0, Math.min(max, unlocked))};
        } catch (Throwable t) { return null; }
    }

    /** send PrideInventory's own packet (Flip ±1 / Buy) so the server does the swap, exactly like its own buttons */
    private void invMsg(String kind, int arg) {
        try {
            Class<?> c = Class.forName("com.dogpound.prideinventory.Net$" + kind);
            Object msg = kind.equals("Buy") ? c.getConstructor().newInstance() : c.getConstructor(int.class).newInstance(arg);
            Object ch = Class.forName("com.dogpound.prideinventory.Net").getField("CH").get(null);
            ch.getClass().getMethod("sendToServer", net.minecraftforge.fml.common.network.simpleimpl.IMessage.class).invoke(ch, msg);
            DPSounds.play(DPSounds.CLICK, 1.2f, 0.6f);
        } catch (Throwable t) {
            tip = "§cPrideInventory isn't installed";
        }
    }

    /** armour, offhand, main inventory, hotbar + trash + stack size, all clickable */
    private void inventory(int mx, int my, int left, int top, int right) {
        EntityPlayer p = mc.player;
        int extraRows = extraRowCount();
        PrideFrame.card(left - 3, top - 3, right - left + 3, 18 * 4 + 14 + (extraRows > 0 ? EXTRA_VIS * 18 + 22 : 0), PrideFrame.BLUE);
        RenderHelper.enableGUIStandardItemLighting();
        // armour column + offhand
        EntityEquipmentSlot[] arm = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) slot(mx, my, left, top + i * 18, 5 + i, p.inventory.armorInventory.get(3 - i), 39 - i);
        slot(mx, my, left + 20, top + 3 * 18, 45, p.inventory.offHandInventory.get(0), 40);
        int ix = left + 44;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++)
            slot(mx, my, ix + c * 18, top + r * 18, 9 + r * 9 + c, p.inventory.mainInventory.get(9 + r * 9 + c), 9 + r * 9 + c);
        if (extraRows > 0) extraPanel(mx, my, ix, top + 4 * 18 + 10, extraRows);
        for (int c = 0; c < 9; c++) slot(mx, my, ix + c * 18, top + 3 * 18 + 4, 36 + c, p.inventory.mainInventory.get(c), c);
        RenderHelper.disableStandardItemLighting();
        int ox = ix + 9 * 18 + 10;
        // trash
        Gui.drawRect(ox, top, ox + 18, top + 18, 0xFF5A1E2A);
        fontRenderer.drawStringWithShadow("✕", ox + 6, top + 5, 0xFFFFFF);
        hits.add(new Object[]{ox, top, 18, 18, (Runnable) () -> cursor = ItemStack.EMPTY});
        if (mx >= ox && my >= top && mx < ox + 18 && my < top + 18) tip = "Trash: drop the item on your cursor here (shift-click: empty your whole inventory)";
        // stack size
        int sx = ox + 24;
        fontRenderer.drawString("§7Take", sx, top + 2, 0xFFFFFF);
        int bx = sx;
        for (int n : new int[]{1, 16, 64}) {
            final int nn = n;
            btn(mx, my, bx, top + 12, 20, 12, String.valueOf(n), sv().stack == n ? PrideFrame.TILE_ON : PrideFrame.BUTTON, () -> sv().stack = nn, "Clicking an item gives " + n);
            bx += 22;
        }
        btn(mx, my, sx, top + 30, 64, 14, "Clear inv.", 0xFF8A1E2A, () -> { for (int i = 9; i < 45; i++) set(i, ItemStack.EMPTY); }, "Empty your inventory (not armour or hotbar? everything except armour)");
        btn(mx, my, sx, top + 48, 64, 14, "Vanilla tabs", PrideFrame.BUTTON, () -> DPCreativeHook.openVanilla(), "The normal creative inventory, just this once");
        fontRenderer.drawString("§8L: take · Shift: to inv · Middle: stack · Right-click: ★ · §dR§8: recipes · §dU§8: uses", left, top + 18 * 4 + 6 - 1 + (extraRowCount() > 0 ? EXTRA_VIS * 18 + 22 : 0), 0xFFFFFF);
    }

    private void slot(int mx, int my, int x, int y, int containerSlot, ItemStack st, int invIndex) {
        Gui.drawRect(x, y, x + 18, y + 18, 0xFF1C1530);
        Gui.drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF2A2238);
        boolean over = mx >= x && my >= y && mx < x + 18 && my < y + 18;
        if (over) Gui.drawRect(x + 1, y + 1, x + 17, y + 17, 0x70F5A9B8);
        if (!st.isEmpty()) {
            itemRender.renderItemAndEffectIntoGUI(st, x + 1, y + 1);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, st, x + 1, y + 1, null);
            if (over && cursor.isEmpty()) tipStack = st;
        }
        hits.add(new Object[]{x, y, 18, 18, (Runnable) () -> clickSlot(containerSlot, st)});
    }

    private void clickSlot(int containerSlot, ItemStack inSlot) {
        if (cursor.isEmpty()) {                     // pick up what's there
            if (inSlot.isEmpty()) return;
            cursor = inSlot.copy();
            set(containerSlot, ItemStack.EMPTY);
        } else {                                    // put down (swap)
            if (!mc.player.inventoryContainer.getSlot(containerSlot).isItemValid(cursor)) return;   // a locked extra row
            ItemStack old = inSlot.copy();
            set(containerSlot, cursor);
            cursor = old;
        }
    }

    /** creative set-slot packet (works on servers), plus the client copy */
    private void set(int containerSlot, ItemStack st) {
        mc.player.inventoryContainer.getSlot(containerSlot).putStack(st.copy());
        mc.playerController.sendSlotPacket(st.copy(), containerSlot);
    }

    private void take(Entry e, boolean full) {
        ItemStack st = e.stack.copy();
        st.setCount(full ? st.getMaxStackSize() : Math.min(st.getMaxStackSize(), sv().stack));
        List<String> rc = sv().recent;
        rc.remove(e.key);
        rc.add(0, e.key);
        while (rc.size() > 54) rc.remove(rc.size() - 1);
        if (isShiftKeyDown()) {                     // straight into the inventory
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 36; i++) {
                int cs = i < 9 ? 36 + i : i;
                ItemStack have = mc.player.inventory.mainInventory.get(i);
                if (pass == 0 && have.isEmpty() || pass == 1 && false) { set(cs, st); DPSounds.play(DPSounds.CLICK, 1.4f, 0.6f); return; }
            }
            return;
        }
        cursor = st;
        DPSounds.play(DPSounds.CLICK, 1.3f, 0.6f);
    }

    private void btn(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String help) {
        if (PrideFrame.button(x, y, w, h, label, color, mx, my) && help != null) tip = help;
        hits.add(new Object[]{x, y, w, h, r});
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        search.mouseClicked(mx, my, button);
        if (button == 0) for (net.minecraft.client.gui.GuiButton b : modButtons) {
            int[] sl = slots.get(b);
            if (sl == null) {                                  // edge UI: clicks land where it drew itself
                if (DPModButtons.edge(b)) DPModButtons.click(hidden, b, mx, my);
                continue;
            }
            if (b.enabled && mx >= sl[0] && my >= sl[1] && mx < sl[0] + 18 && my < sl[1] + 18) { DPModButtons.click(hidden, b, b.x + b.width / 2, b.y + b.height / 2); return; }
        }
        // grid
        if (groupOpen == null && mx >= gx && my >= gy && mx < gx + cols * cell && my < gy + rows * cell) {
            int i = (int) Math.floor(smooth + (my - gy) / (float) cell) * cols + (mx - gx) / cell;
            if (!cursor.isEmpty()) { cursor = ItemStack.EMPTY; return; }                  // dropping on the grid = delete
            if (i < view.size()) {
                Object o = view.get(i);
                Entry e = first(o);
                if (button == 1) { toggleFav(e); return; }
                take(e, button == 2);
            }
            return;
        }
        for (int k = hits.size() - 1; k >= 0; k--) {
            Object[] h = hits.get(k);
            if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) {
                if (h.length > 5 && button == 1) { toggleFav((Entry) h[5]); return; }
                if (h.length > 5 && button == 2) { take((Entry) h[5], true); return; }
                ((Runnable) h[4]).run();
                return;
            }
        }
    }

    private void toggleFav(Entry e) {
        List<String> f = sv().favorites;
        if (!f.remove(e.key)) { f.add(e.key); DPSounds.play(DPSounds.CONFIRM, 1.4f, 0.6f); }
        else DPSounds.play(DPSounds.BACK, 1f, 0.5f);
        save();
        if (sv().filter.equals("★ Favorites")) refilter();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (groupOpen != null && mx >= groupBox[0] && my >= groupBox[1] && mx < groupBox[0] + groupBox[2] && my < groupBox[1] + groupBox[3]) { groupScroll += w > 0 ? -1 : 1; return; }
        if (mx >= invBox[0] && my >= invBox[1] && mx < invBox[0] + invBox[2] + 4 && my < invBox[1] + invBox[3]) { invScroll += w > 0 ? -1 : 1; return; }
        if (mx < sideBox[0] + sideBox[2]) sideScroll += w > 0 ? -3 : 3;
        else scroll = Math.max(0, Math.min(maxScroll(), scroll + (w > 0 ? -1 : 1) * (isShiftKeyDown() ? rows : 3)));
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || (key == mc.gameSettings.keyBindInventory.getKeyCode() && !search.isFocused())) { mc.displayGuiScreen(null); return; }
        // the mouse on an item wins (like JEI/EMI): R / U show recipes even while the search box has the cursor (2026-10-06)
        if (!tipStack.isEmpty() && (key == Keyboard.KEY_R || key == Keyboard.KEY_U)) {
            showRecipes(tipStack, key == Keyboard.KEY_U);       // like EMI/JEI: R = how to make it, U = what it's used in
            return;
        }
        if (search.textboxKeyTyped(c, key)) { scroll = 0; refilter(); return; }
        super.keyTyped(c, key);
    }

    /** recipes (or uses) of an item, every mod's machines included: our own Pride Recipes screen, EMI only as the data */
    private void showRecipes(ItemStack st, boolean uses) {
        if (!DPRecipeScreen.available()) { tip = "§cNo recipe data (EMI missing)"; return; }
        mc.displayGuiScreen(new DPRecipeScreen(this, st, uses));
    }

    @Override public void updateScreen() { search.updateCursorCounter(); }
}
