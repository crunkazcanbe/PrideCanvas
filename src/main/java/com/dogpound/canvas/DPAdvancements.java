package com.dogpound.canvas;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.advancements.GuiScreenAdvancements;
import net.minecraft.client.multiplayer.ClientAdvancementManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pride Advancements (requested feature): every advancement in the game on one Pride page instead of Minecraft's tree.
 * Left: every tab (Minecraft + each mod) with how many you have. Middle: every advancement of that tab as a list (indented
 * by what it follows), progress bars, search and done / not-done filter. Right: the chosen one — what to do (its
 * description and every requirement with ✓/✗), what it comes after and unlocks, and when you got each part.
 * Opens wherever Minecraft's advancement screen would (the L key); "Classic tree" shows the old one.
 */
public class DPAdvancements extends GuiScreen {
    private PrideFrame f;
    private GuiTextField search;
    private static String query = "";
    private static int filter;                    // 0 all, 1 done, 2 not done
    private static String tabId;                  // root advancement id of the chosen tab
    private Advancement sel;
    private int tabScroll, listScroll, detailScroll;
    private final List<Object[]> hits = new ArrayList<Object[]>();
    private int[] tabArea = new int[4], listArea = new int[4], detailArea = new int[4];
    private static final int ROW = 22;
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.ROOT);
    static boolean allowClassic;                  // set while the classic screen is opened on purpose

    /** swap Minecraft's advancement screen for ours */
    public static final class Hook {
        @SubscribeEvent
        public void open(GuiOpenEvent e) {
            if (e.getGui() instanceof GuiScreenAdvancements && DPConfig.prideAdvancements) {
                if (allowClassic) { allowClassic = false; return; }
                e.setGui(new DPAdvancements());
            }
        }
    }

    // ------------------------------------------------------------------ data

    private static ClientAdvancementManager manager() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player == null || mc.player.connection == null ? null : mc.player.connection.getAdvancementManager();
    }

    private static Field progressField;

    @SuppressWarnings("unchecked")
    private static Map<Advancement, AdvancementProgress> progress() {
        ClientAdvancementManager m = manager();
        if (m == null) return Collections.emptyMap();
        try {
            if (progressField == null)
                for (Field fl : ClientAdvancementManager.class.getDeclaredFields())
                    if (Map.class.isAssignableFrom(fl.getType())) { fl.setAccessible(true); progressField = fl; break; }
            return (Map<Advancement, AdvancementProgress>) progressField.get(m);
        } catch (Throwable t) { return Collections.emptyMap(); }
    }

    private static List<Advancement> roots() {
        List<Advancement> out = new ArrayList<Advancement>();
        ClientAdvancementManager m = manager();
        if (m == null) return out;
        for (Advancement a : m.getAdvancementList().getRoots()) if (a.getDisplay() != null) out.add(a);
        out.sort((a, b) -> {
            boolean va = "minecraft".equals(a.getId().getResourceDomain()), vb = "minecraft".equals(b.getId().getResourceDomain());
            if (va != vb) return va ? -1 : 1;
            return title(a).compareToIgnoreCase(title(b));
        });
        return out;
    }

    /** the tab's advancements, depth-first so each sits under the one it follows: {advancement, depth} */
    private static void walk(Advancement a, int depth, List<Object[]> out) {
        if (a.getDisplay() != null) out.add(new Object[]{ a, depth });
        List<Advancement> kids = new ArrayList<Advancement>();
        for (Advancement c : a.getChildren()) kids.add(c);
        kids.sort((x, y) -> title(x).compareToIgnoreCase(title(y)));
        for (Advancement c : kids) walk(c, depth + 1, out);
    }

    private static String title(Advancement a) { return a.getDisplay() == null ? a.getId().toString() : a.getDisplay().getTitle().getFormattedText(); }
    private static String desc(Advancement a) { return a.getDisplay() == null ? "" : a.getDisplay().getDescription().getFormattedText(); }

    private static String modName(Advancement a) {
        String dom = a.getId().getResourceDomain();
        if ("minecraft".equals(dom)) return "Minecraft";
        ModContainer mc = Loader.instance().getIndexedModList().get(dom);
        return mc != null ? mc.getName() : dom;
    }

    private static boolean done(Map<Advancement, AdvancementProgress> p, Advancement a) {
        AdvancementProgress ap = p.get(a);
        return ap != null && ap.isDone();
    }

    /** {done, total} for a tab */
    private static int[] count(Map<Advancement, AdvancementProgress> p, Advancement root) {
        List<Object[]> l = new ArrayList<Object[]>();
        walk(root, 0, l);
        int d = 0;
        for (Object[] o : l) if (done(p, (Advancement) o[0])) d++;
        return new int[]{ d, l.size() };
    }

    // ------------------------------------------------------------------ screen

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        f = PrideFrame.fit(width, height);
        search = new GuiTextField(1, fontRenderer, f.cx + 190, f.cy + 2, 200, 14);
        search.setText(query);
        search.setFocused(true);
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() { search.updateCursorCounter(); }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Object[]{ x, y, w, h, r }); }
    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        Map<Advancement, AdvancementProgress> prog = progress();
        List<Advancement> roots = roots();
        int all = 0, got = 0;
        for (Advancement r : roots) { int[] c = count(prog, r); got += c[0]; all += c[1]; }
        f.draw(this, "Advancements", "§d" + got + "§f / " + all + " done §7(" + (all == 0 ? 0 : got * 100 / all) + "%)");

        // top row: filter pills, search, classic view
        String[] fl = { "All", "✔ Done", "✘ To do" };
        for (int i = 0; i < 3; i++) {
            int x = f.cx + i * 60;
            PrideFrame.tile(x, f.cy, 56, 18, PrideFrame.RAINBOW[i * 2], in(mx, my, x, f.cy, 56, 18), filter == i);
            fontRenderer.drawStringWithShadow(fl[i], x + (56 - fontRenderer.getStringWidth(fl[i])) / 2f, f.cy + 5, 0xFFFFFF);
            final int k = i;
            hit(x, f.cy, 56, 18, () -> { filter = k; listScroll = 0; });
        }
        search.drawTextBox();
        if (search.getText().isEmpty()) fontRenderer.drawString("Search every advancement…", search.x + 4, search.y + 3, 0xFF8A8499);
        PrideFrame.button(f.cx + f.cw - 100, f.cy, 100, 18, "☘ Classic tree", PrideFrame.BUTTON, mx, my);
        hit(f.cx + f.cw - 100, f.cy, 100, 18, () -> { allowClassic = true; mc.displayGuiScreen(new GuiScreenAdvancements(mc.player.connection.getAdvancementManager())); });

        int top = f.cy + 24, h = f.y + f.h - 8 - top;
        if (roots.isEmpty()) { drawCenteredString(fontRenderer, "No advancements yet (join a world first).", width / 2, top + 40, 0xC8C0DC); super.drawScreen(mx, my, pt); return; }
        if (tabId == null || roots.stream().noneMatch(r -> r.getId().toString().equals(tabId))) tabId = roots.get(0).getId().toString();
        boolean searching = !search.getText().trim().isEmpty();

        // ---- left: tabs
        int tw = 150;
        PrideFrame.card(f.cx, top, tw, h, PrideFrame.PINK);
        tabArea = new int[]{ f.cx, top + 2, tw, h - 2 };
        int ttotal = roots.size() * 24;
        tabScroll = Math.max(0, Math.min(tabScroll, Math.max(0, ttotal - h)));
        PrideFrame.clip(f.cx, top + 2, tw, h - 2);
        for (int i = 0; i < roots.size(); i++) {
            Advancement r = roots.get(i);
            int y = top + 3 + i * 24 - tabScroll;
            if (y + 24 < top || y > top + h) continue;
            boolean on = !searching && r.getId().toString().equals(tabId), hov = in(mx, my, f.cx, y, tw, 22) && my > top && my < top + h;
            Gui.drawRect(f.cx + 2, y, f.cx + tw - 4, y + 22, on ? 0xC06A3FA0 : hov ? 0x402A2140 : 0x00000000);
            item(r.getDisplay().getIcon(), f.cx + 5, y + 3);
            int[] c = count(prog, r);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(title(r), tw - 30), f.cx + 25, y + 3, 0xFFFFFF);
            Gui.drawRect(f.cx + 25, y + 15, f.cx + tw - 10, y + 18, 0xFF1C1530);
            Gui.drawRect(f.cx + 25, y + 15, f.cx + 25 + (tw - 35) * c[0] / Math.max(1, c[1]), y + 18, c[0] == c[1] ? 0xFF8CE06A : PrideFrame.PINK);
            small("§7" + c[0] + "/" + c[1], f.cx + tw - 30, y + 3, 0xFFFFFF);
            if (y >= top && y + 22 <= top + h) hit(f.cx, y, tw, 22, () -> { tabId = r.getId().toString(); listScroll = 0; search.setText(""); query = ""; sel = null; });
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + tw - 3, top + 2, h - 2, tabScroll, h - 2, ttotal);

        // ---- middle: the list
        int lx = f.cx + tw + 6, lw = (f.cw - tw - 12) / 2 + 20;
        List<Object[]> rows = new ArrayList<Object[]>();
        if (searching) {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            for (Advancement r : roots) {
                List<Object[]> l = new ArrayList<Object[]>();
                walk(r, 0, l);
                for (Object[] o : l) {
                    Advancement a = (Advancement) o[0];
                    if ((title(a) + " " + desc(a) + " " + modName(a)).toLowerCase(Locale.ROOT).contains(q)) rows.add(new Object[]{ a, 0 });
                }
            }
        } else for (Advancement r : roots) if (r.getId().toString().equals(tabId)) walk(r, 0, rows);
        if (filter != 0) rows.removeIf(o -> done(prog, (Advancement) o[0]) != (filter == 1));
        PrideFrame.card(lx, top, lw, h, PrideFrame.BLUE);
        listArea = new int[]{ lx, top + 2, lw, h - 2 };
        int ltotal = rows.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, ltotal - h + 4)));
        PrideFrame.clip(lx, top + 2, lw, h - 2);
        for (int i = 0; i < rows.size(); i++) {
            Advancement a = (Advancement) rows.get(i)[0];
            int depth = Math.min(6, (Integer) rows.get(i)[1]);
            int y = top + 3 + i * ROW - listScroll;
            if (y + ROW < top || y > top + h) continue;
            AdvancementProgress ap = prog.get(a);
            boolean d = ap != null && ap.isDone(), on = a == sel, hov = in(mx, my, lx, y, lw, ROW - 1) && my > top && my < top + h;
            int x = lx + 4 + depth * 8;
            Gui.drawRect(lx + 2, y, lx + lw - 5, y + ROW - 1, on ? 0xC06A3FA0 : hov ? 0x402A2140 : 0x30000000);
            if (depth > 0) Gui.drawRect(x - 5, y + ROW / 2, x - 1, y + ROW / 2 + 1, 0x40FFFFFF);
            int fc = frameColor(a.getDisplay().getFrame());
            Gui.drawRect(x, y + 2, x + 18, y + 20, d ? fc : 0xFF2A2238);
            Gui.drawRect(x + 1, y + 3, x + 17, y + 19, 0xFF140E22);
            item(a.getDisplay().getIcon(), x + 1, y + 3);
            String t = (d ? "§a✔ §f" : "") + title(a);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(t, lw - (x - lx) - 30), x + 22, y + 3, d ? 0xFFFFFF : 0xC8C0DC);
            float pct = ap == null ? 0F : ap.getPercent();
            if (!d && pct > 0) {
                Gui.drawRect(x + 22, y + 14, lx + lw - 12, y + 17, 0xFF1C1530);
                Gui.drawRect(x + 22, y + 14, x + 22 + (int) ((lx + lw - 12 - x - 22) * pct), y + 17, PrideFrame.BLUE);
            } else small("§7" + (searching ? modName(a) + " · " : "") + frameName(a.getDisplay().getFrame()), x + 22, y + 13, 0xFFFFFF);
            if (y >= top && y + ROW <= top + h) hit(lx, y, lw, ROW - 1, () -> { sel = a; detailScroll = 0; });
        }
        if (rows.isEmpty()) drawCenteredString(fontRenderer, filter == 1 ? "None done here yet." : "Nothing here.", lx + lw / 2, top + 30, 0xC8C0DC);
        PrideFrame.unclip();
        PrideFrame.scrollbar(lx + lw - 4, top + 2, h - 2, listScroll, h - 2, ltotal);

        // ---- right: details
        int dx = lx + lw + 6, dw = f.cx + f.cw - dx;
        drawDetail(mx, my, prog, sel != null ? sel : rows.isEmpty() ? null : (Advancement) rows.get(0)[0], dx, top, dw, h);
        super.drawScreen(mx, my, pt);
    }

    private void drawDetail(int mx, int my, Map<Advancement, AdvancementProgress> prog, Advancement a, int x, int y, int w, int h) {
        PrideFrame.card(x, y, w, h, PrideFrame.RAINBOW[5]);
        detailArea = new int[]{ x, y, w, h };
        if (a == null) return;
        DisplayInfo di = a.getDisplay();
        AdvancementProgress ap = prog.get(a);
        boolean d = ap != null && ap.isDone();
        int fc = frameColor(di.getFrame());
        Gui.drawRect(x + 6, y + 6, x + 38, y + 38, d ? fc : 0xFF2A2238);
        Gui.drawRect(x + 7, y + 7, x + 37, y + 37, 0xFF140E22);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 10, y + 10, 0);
        GlStateManager.scale(1.5F, 1.5F, 1F);
        item(di.getIcon(), 0, 0);
        GlStateManager.popMatrix();
        List<String> tl = fontRenderer.listFormattedStringToWidth("§l" + title(a), w - 50);
        for (int i = 0; i < Math.min(2, tl.size()); i++) fontRenderer.drawStringWithShadow(tl.get(i), x + 44, y + 8 + i * 10, 0xFFFFFF);
        small("§7" + modName(a) + " · " + frameName(di.getFrame()) + (di.isHidden() ? " · secret" : ""), x + 44, y + 30, 0xFFFFFF);

        List<String> lines = new ArrayList<String>();
        lines.add(d ? "§a§l✔ Done" + (ap.getFirstProgressDate() != null ? "§r§7  finished " + when(lastDate(ap)) : "")
                : "§e§l" + (ap == null ? 0 : (int) (ap.getPercent() * 100)) + "% done");
        lines.add("");
        lines.add("§d§lWhat to do");
        lines.addAll(fontRenderer.listFormattedStringToWidth(desc(a).isEmpty() ? "§7(no description)" : desc(a), w - 16));
        // requirements: criteria grouped the way the game checks them (any one of a group, every group)
        String[][] req = a.getRequirements();
        if (req != null && req.length > 0 && !(req.length == 1 && req[0].length == 1)) {
            lines.add("");
            lines.add("§d§lRequirements" + (ap != null ? "§r§7  (" + count(ap, true) + " of " + a.getCriteria().size() + ")" : ""));
            for (String[] group : req) {
                if (group.length == 1) lines.add(crit(ap, group[0]));
                else {
                    lines.add("§7 any one of:");
                    for (String c : group) lines.add("  " + crit(ap, c));
                }
            }
        } else if (ap != null) {
            for (String c : a.getCriteria().keySet()) { CriterionProgress cp = ap.getCriterionProgress(c); if (cp != null && cp.isObtained()) lines.add("§7Got it " + when(cp.getObtained())); }
        }
        if (a.getParent() != null && a.getParent().getDisplay() != null) {
            lines.add("");
            lines.add("§d§lComes after");
            lines.add((done(prog, a.getParent()) ? "§a✔ " : "§7✘ ") + "§f" + title(a.getParent()));
        }
        List<String> next = new ArrayList<String>();
        for (Advancement c : a.getChildren()) if (c.getDisplay() != null) next.add((done(prog, c) ? "§a✔ " : "§7• ") + "§f" + title(c));
        if (!next.isEmpty()) { lines.add(""); lines.add("§d§lUnlocks"); lines.addAll(next); }
        if (di.getFrame() == FrameType.CHALLENGE) { lines.add(""); lines.add("§5✦ Challenge: one of the hardest in its tab."); }

        int ty = y + 44, th = h - 48, total = lines.size() * 10;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, total - th)));
        PrideFrame.clip(x, ty, w, th);
        for (int i = 0; i < lines.size(); i++) {
            int ly = ty + i * 10 - detailScroll;
            if (ly + 10 < ty || ly > ty + th) continue;
            fontRenderer.drawStringWithShadow(lines.get(i), x + 8, ly, 0xE6E0F0);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 4, ty, th, detailScroll, th, total);
    }

    private static int count(AdvancementProgress ap, boolean got) {
        int n = 0;
        for (String c : ap.getCompletedCriteria()) n++;
        return n;
    }

    private static Date lastDate(AdvancementProgress ap) {
        Date last = null;
        for (String c : ap.getCompletedCriteria()) {
            CriterionProgress cp = ap.getCriterionProgress(c);
            if (cp != null && cp.getObtained() != null && (last == null || cp.getObtained().after(last))) last = cp.getObtained();
        }
        return last;
    }

    /** "✔ minecraft:stone  · Oct 4, 1:52 AM" — criterion names made readable */
    private static String crit(AdvancementProgress ap, String c) {
        CriterionProgress cp = ap == null ? null : ap.getCriterionProgress(c);
        boolean got = cp != null && cp.isObtained();
        String name = c.contains(":") ? c.substring(c.indexOf(':') + 1) : c;
        name = DPContentApi.pretty(name.replace('/', ' '));
        return (got ? "§a✔ §f" : "§c✘ §7") + name + (got && cp.getObtained() != null ? "§8  " + when(cp.getObtained()) : "");
    }

    private static String when(Date d) { return d == null ? "" : WHEN.format(d); }

    private static int frameColor(FrameType t) { return t == FrameType.CHALLENGE ? 0xFFB050E0 : t == FrameType.GOAL ? 0xFF5BCEFA : 0xFFF5A9B8; }
    private static String frameName(FrameType t) { return t == FrameType.CHALLENGE ? "Challenge" : t == FrameType.GOAL ? "Goal" : "Task"; }

    private void item(ItemStack s, int x, int y) {
        if (s == null || s.isEmpty()) return;
        RenderHelper.enableGUIStandardItemLighting();
        try { itemRender.renderItemAndEffectIntoGUI(s, x, y); } catch (Throwable ignored) { }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void small(String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        fontRenderer.drawStringWithShadow(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        search.mouseClicked(mx, my, button);
        if (button != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) { ((Runnable) h[4]).run(); return; }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || (mc.gameSettings.keyBindAdvancements.isActiveAndMatches(key) && !search.isFocused())) { mc.displayGuiScreen(null); return; }
        if (search.textboxKeyTyped(c, key)) { query = search.getText(); listScroll = 0; }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1, step = Integer.signum(d) * ROW * 2;
        if (in(mx, my, tabArea[0], tabArea[1], tabArea[2], tabArea[3])) tabScroll -= step;
        else if (in(mx, my, listArea[0], listArea[1], listArea[2], listArea[3])) listScroll -= step;
        else detailScroll -= step;
    }
}
