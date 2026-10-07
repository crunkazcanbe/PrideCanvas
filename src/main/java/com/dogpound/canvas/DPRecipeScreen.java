package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pride Recipes (requested feature). Our own recipe screen in the
 * Pride style; EMI stays installed only as the data source (it knows every mod's crafting + machine recipes, JEI
 * plugins included). R = how to make it, U = what it's used in, click an ingredient to dig deeper, Back to return.
 * Everything EMI is reached by reflection, so PrideCanvas still runs without it.
 */
public class DPRecipeScreen extends GuiScreen {
    /** one recipe, flattened to plain item stacks */
    static final class R {
        String category = "", id = "";
        final List<List<ItemStack>> inputs = new ArrayList<>(), catalysts = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
    }

    private final GuiScreen parent;
    private final List<Object[]> history = new ArrayList<>();      // {ItemStack, Boolean uses}
    private ItemStack focus = ItemStack.EMPTY;
    private boolean uses;
    private List<R> recipes = Collections.emptyList();
    private List<String> cats = new ArrayList<>();
    private int cat, page;
    private ItemStack hover = ItemStack.EMPTY;
    private String note = "";

    public DPRecipeScreen(GuiScreen parent, ItemStack stack, boolean uses) {
        this.parent = parent;
        open(stack, uses, false);
    }

    /** true if EMI is there to read from */
    public static boolean available() {
        try { Class.forName("dev.emi.emi.api.EmiApi"); return true; } catch (Throwable t) { return false; }
    }

    private void open(ItemStack st, boolean u, boolean remember) {
        if (st == null || st.isEmpty()) return;
        if (remember && !focus.isEmpty()) history.add(new Object[]{focus, uses});
        focus = st.copy(); focus.setCount(1);
        uses = u;
        recipes = load(focus, u);
        cats = new ArrayList<>();
        for (R r : recipes) if (!cats.contains(r.category)) cats.add(r.category);
        cat = 0; page = 0;
        note = recipes.isEmpty() ? (u ? "Nothing uses this" : "No recipe — found in the world, from mobs or loot") : "";
        DPSounds.play(DPSounds.OPEN, 1.2f, 0.5f);
    }

    // ---------------------------------------------------------------- EMI data (reflection)
    private static List<R> load(ItemStack st, boolean uses) {
        List<R> out = new ArrayList<>();
        try {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi"), emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Object es = emiStack.getMethod("of", ItemStack.class).invoke(null, st);
            Object mgr = api.getMethod("getRecipeManager").invoke(null);
            Method by = mgr.getClass().getMethod(uses ? "getRecipesByInput" : "getRecipesByOutput", emiStack);
            by.setAccessible(true);
            List<?> list = (List<?>) by.invoke(mgr, es);
            for (Object rec : list) {
                if (out.size() >= 400) break;
                R r = new R();
                try {
                    Object c = call(rec, "getCategory");
                    Object cid = call(c, "getId");
                    r.category = pretty(String.valueOf(cid));
                    r.id = String.valueOf(call(rec, "getId"));
                } catch (Throwable ignored) { }
                for (Object ing : (List<?>) call(rec, "getInputs")) r.inputs.add(stacks(ing));
                try { for (Object ing : (List<?>) call(rec, "getCatalysts")) r.catalysts.add(stacks(ing)); } catch (Throwable ignored) { }
                for (Object o : (List<?>) call(rec, "getOutputs")) {
                    List<ItemStack> s = stacks(o);
                    if (!s.isEmpty()) out(r, s.get(0));
                }
                out.add(r);
            }
        } catch (Throwable t) {
            net.minecraftforge.fml.common.FMLLog.log.warn("[Pride Recipes] couldn't read recipes for {}: {}", st, t.toString());
        }
        return out;
    }

    private static void out(R r, ItemStack s) { r.outputs.add(s); }

    private static Object call(Object o, String m) throws Exception {
        Method mm = o.getClass().getMethod(m);
        mm.setAccessible(true);
        return mm.invoke(o);
    }

    /** every alternative of an EMI ingredient as item stacks (fluids etc. are skipped) */
    private static List<ItemStack> stacks(Object ing) {
        List<ItemStack> l = new ArrayList<>();
        try {
            long amount = 1;
            try { amount = (Long) call(ing, "getAmount"); } catch (Throwable ignored) { }
            for (Object e : (List<?>) call(ing, "getEmiStacks")) {
                Object is = call(e, "getItemStack");
                if (is instanceof ItemStack && !((ItemStack) is).isEmpty()) {
                    ItemStack c = ((ItemStack) is).copy();
                    c.setCount((int) Math.max(1, Math.min(64, amount)));
                    l.add(c);
                }
                if (l.size() >= 24) break;
            }
        } catch (Throwable ignored) { }
        return l;
    }

    private static String pretty(String id) {
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        p = p.replace('/', ' ').replace('_', ' ').trim();
        return p.isEmpty() ? "Recipe" : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private List<R> shown() {
        if (cats.isEmpty()) return recipes;
        String c = cats.get(Math.min(cat, cats.size() - 1));
        List<R> l = new ArrayList<>();
        for (R r : recipes) if (r.category.equals(c)) l.add(r);
        return l;
    }

    // ---------------------------------------------------------------- drawing
    private PrideFrame f;
    private final List<int[]> slots = new ArrayList<>();          // x, y, kind(0 input,1 output,2 catalyst), index
    private final List<ItemStack> slotStacks = new ArrayList<>();

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f = PrideFrame.sized(width, height, 460, 300);
        f.draw(this, uses ? "Pride Recipes · Uses" : "Pride Recipes", focus.getDisplayName());
        slots.clear(); slotStacks.clear(); hover = ItemStack.EMPTY;
        int x0 = f.cx, y0 = f.cy, w = f.cw, h = f.ch;
        // focus item + back
        item(focus, x0, y0, 2, -1, mx, my);
        fontRenderer.drawStringWithShadow((uses ? "Used in " : "How to make ") + "§d" + focus.getDisplayName() + "§r · " + recipes.size() + " recipe" + (recipes.size() == 1 ? "" : "s"), x0 + 22, y0 + 5, 0xFFFFFF);
        if (!history.isEmpty()) PrideFrame.button(x0 + w - 60, y0, 60, 16, "◀ Back", PrideFrame.BUTTON, mx, my);
        PrideFrame.button(x0 + w - 124, y0, 60, 16, uses ? "Recipes (R)" : "Uses (U)", PrideFrame.BUTTON, mx, my);
        // category tabs (left)
        int ty = y0 + 24;
        for (int i = 0; i < cats.size() && ty < y0 + h - 14; i++, ty += 15) {
            boolean on = i == cat;
            PrideFrame.tile(x0, ty, 110, 13, PrideFrame.RAINBOW[i % 6], mx >= x0 && mx < x0 + 110 && my >= ty && my < ty + 13, on);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(cats.get(i), 104), x0 + 3, ty + 3, on ? 0xFFFFFF : 0xCCCCCC);
        }
        // recipes (right): as many as fit, 70 px tall each
        List<R> list = shown();
        int ax = x0 + 118, aw = w - 118, per = Math.max(1, (h - 44) / 70);
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        long tick = Minecraft.getSystemTime() / 1000;
        for (int k = 0; k < per; k++) {
            int idx = page * per + k;
            if (idx >= list.size()) break;
            R r = list.get(idx);
            int ry = y0 + 24 + k * 70;
            PrideFrame.card(ax, ry, aw, 66, PrideFrame.RAINBOW[(idx + 2) % 6]);
            int n = r.inputs.size();
            int cols = n <= 1 ? 1 : n <= 4 ? 2 : n <= 9 ? 3 : Math.min(6, (n + 2) / 3);
            for (int i = 0; i < n; i++) {
                List<ItemStack> alts = r.inputs.get(i);
                ItemStack s = alts.isEmpty() ? ItemStack.EMPTY : alts.get((int) (tick % alts.size()));
                item(s, ax + 6 + (i % cols) * 18, ry + 4 + (i / cols) * 18, 0, i, mx, my);
            }
            int arrowX = ax + 12 + cols * 18;
            fontRenderer.drawStringWithShadow("§l→", arrowX, ry + 24, 0xF5A9B8);
            for (int i = 0; i < r.outputs.size() && i < 6; i++) item(r.outputs.get(i), arrowX + 16 + i * 20, ry + 20, 1, i, mx, my);
            if (!r.catalysts.isEmpty()) {
                fontRenderer.drawString("§7made in", ax + aw - 92, ry + 6, 0xFFFFFF);
                for (int i = 0; i < r.catalysts.size() && i < 4; i++) {
                    List<ItemStack> alts = r.catalysts.get(i);
                    if (!alts.isEmpty()) item(alts.get((int) (tick % alts.size())), ax + aw - 92 + i * 20, ry + 18, 2, i, mx, my);
                }
            }
            fontRenderer.drawString("§8" + fontRenderer.trimStringToWidth(r.id, aw - 12), ax + 6, ry + 56, 0xFFFFFF);
        }
        if (!note.isEmpty()) fontRenderer.drawStringWithShadow("§7" + note, ax + 6, y0 + 30, 0xFFFFFF);
        // pager
        if (pages > 1) {
            int py = y0 + h - 14;
            PrideFrame.button(ax, py, 30, 14, "◀", PrideFrame.BUTTON, mx, my);
            PrideFrame.button(ax + aw - 30, py, 30, 14, "▶", PrideFrame.BUTTON, mx, my);
            String pg = (page + 1) + " / " + pages;
            fontRenderer.drawStringWithShadow(pg, ax + (aw - fontRenderer.getStringWidth(pg)) / 2f, py + 3, 0xFFFFFF);
        }
        fontRenderer.drawString("§8click: recipe · right-click: uses · scroll: page · Esc: back", x0, y0 + h + 4, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
        if (!hover.isEmpty()) renderToolTip(hover, mx, my);
    }

    private void item(ItemStack s, int x, int y, int kind, int index, int mx, int my) {
        Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0x60000000);
        if (s.isEmpty()) return;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        itemRender.renderItemAndEffectIntoGUI(s, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, s, x, y, s.getCount() > 1 ? String.valueOf(s.getCount()) : null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        slots.add(new int[]{x, y, kind, index});
        slotStacks.add(s);
        if (mx >= x && my >= y && mx < x + 16 && my < y + 16) {
            Gui.drawRect(x, y, x + 16, y + 16, 0x50FFFFFF);
            hover = s;
        }
    }

    // ---------------------------------------------------------------- input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (f == null) return;
        int x0 = f.cx, y0 = f.cy, w = f.cw, h = f.ch;
        if (!history.isEmpty() && mx >= x0 + w - 60 && my >= y0 && mx < x0 + w && my < y0 + 16) { back(); return; }
        if (mx >= x0 + w - 124 && my >= y0 && mx < x0 + w - 64 && my < y0 + 16) { open(focus, !uses, true); return; }
        int ty = y0 + 24;
        for (int i = 0; i < cats.size(); i++, ty += 15)
            if (mx >= x0 && mx < x0 + 110 && my >= ty && my < ty + 13) { cat = i; page = 0; DPSounds.play(DPSounds.OPEN, 1.5f, 0.3f); return; }
        int ax = x0 + 118, aw = w - 118, py = y0 + h - 14;
        if (my >= py && my < py + 14) {
            if (mx >= ax && mx < ax + 30) { page--; return; }
            if (mx >= ax + aw - 30 && mx < ax + aw) { page++; return; }
        }
        for (int i = 0; i < slots.size(); i++) {
            int[] s = slots.get(i);
            if (mx >= s[0] && my >= s[1] && mx < s[0] + 16 && my < s[1] + 16) { open(slotStacks.get(i), button == 1, true); return; }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) page += d > 0 ? -1 : 1;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK) { if (!history.isEmpty() && key == Keyboard.KEY_BACK) back(); else mc.displayGuiScreen(parent); return; }
        if (!hover.isEmpty() && (key == Keyboard.KEY_R || key == Keyboard.KEY_U)) { open(hover, key == Keyboard.KEY_U, true); return; }
        if (key == Keyboard.KEY_LEFT) page--;
        if (key == Keyboard.KEY_RIGHT) page++;
    }

    private void back() {
        Object[] o = history.remove(history.size() - 1);
        focus = ItemStack.EMPTY;
        open((ItemStack) o[0], (Boolean) o[1], false);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @SuppressWarnings("unused") private static String lc(String s) { return s.toLowerCase(Locale.ROOT); }
}
