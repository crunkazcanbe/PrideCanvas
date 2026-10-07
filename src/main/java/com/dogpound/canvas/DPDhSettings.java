package com.dogpound.canvas;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Distant Horizons settings as a Pride menu (requested feature). Centred PrideFrame panel like every other menu; EVERY DH option is kept —
 * the screen walks DH's own config tree (com.seibel.distanthorizons.core.config.Config) by reflection, so nothing is
 * hand-listed and nothing can be dropped, and values are saved through DH's own uiSet() (DH saves + applies them).
 * Booleans toggle, enums cycle (right-click = back), numbers use - / + (shift = x10) or click to type, text clicks to type.
 */
public class DPDhSettings extends GuiScreen {
    private static final String ROOT = "com.seibel.distanthorizons.core.config.Config$Client";   // the root only holds "client"
    private static final int ROW = 24;

    private final GuiScreen parent;
    private final Deque<Class<?>> path = new ArrayDeque<>();
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private Row editing;            // number/text row being typed into
    private String editText = "";
    private String hoverTip = "";

    /** one line of the list */
    private static final class Row {
        Object entry;               // DH ConfigEntry (null for categories/comments)
        Class<?> category;          // non-null = opens this sub-category
        String key;                 // lang key base: distanthorizons.config.<nameAndCategory>
        String plain;               // fallback name
        boolean comment;
    }

    public DPDhSettings(GuiScreen parent) {
        this.parent = parent;
        try { path.push(Class.forName(ROOT)); } catch (Throwable t) { /* DH not installed: screen just shows a note */ }
        rebuild();
    }

    // ------------------------------------------------------------------ open instead of DH's own screen
    public static class Opener {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void open(GuiOpenEvent e) {
            if (e.getGui() == null || e.getGui() instanceof DPDhSettings) return;
            String n = e.getGui().getClass().getName();
            if (n.startsWith("com.seibel.distanthorizons.") && (n.contains("ConfigScreen") || n.contains("ClassicConfigGUI")))
                e.setGui(new DPDhSettings(Minecraft.getMinecraft().currentScreen));
        }
    }

    // ------------------------------------------------------------------ reading DH's config tree
    private void rebuild() {
        rows.clear();
        scroll = 0;
        editing = null;
        if (path.isEmpty()) return;
        for (Field f : path.peek().getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
            Object o;
            try { o = f.get(null); } catch (Throwable t) { continue; }
            if (o == null) continue;
            String cls = o.getClass().getSimpleName();
            if (cls.equals("ConfigUiLinkedEntry")) {                      // quick option that points at a real one
                Object wrapped = call(o, "get");
                if (wrapped != null) { o = wrapped; cls = o.getClass().getSimpleName(); }
            }
            Row r = new Row();
            r.plain = pretty(f.getName());
            Object nc = call(o, "getNameAndCategory");
            r.key = "distanthorizons.config." + (nc != null ? nc : f.getName());
            if (cls.equals("ConfigCategory")) {
                Object c = call(o, "get");
                if (!(c instanceof Class)) continue;
                r.category = (Class<?>) c;
            } else if (cls.equals("ConfigEntry")) {
                Object app = call(o, "getAppearance");
                if (app != null && (app.toString().equals("ONLY_IN_FILE") || app.toString().equals("ONLY_IN_API"))) continue;
                r.entry = o;
            } else if (cls.equals("ConfigUIComment")) {
                r.comment = true;
            } else continue;                                               // spacers, debug buttons
            rows.add(r);
        }
    }

    private static Object call(Object o, String m, Object... args) {
        try {
            for (Method x : o.getClass().getMethods())
                if (x.getName().equals(m) && x.getParameterCount() == args.length) return x.invoke(o, args);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String pretty(String camel) {
        String s = camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String tr(String key, String fallback) {
        return I18n.hasKey(key) ? I18n.format(key) : fallback;
    }

    private String label(Row r) { return tr(r.key, r.plain).replaceAll("§.", ""); }

    private static String show(Object v) {
        if (v instanceof Double || v instanceof Float) return String.format(Locale.ROOT, "%.3f", ((Number) v).doubleValue()).replaceAll("0+$", "").replaceAll("\\.$", "");
        if (v instanceof Boolean) return (Boolean) v ? "ON" : "OFF";
        if (v instanceof Enum) return pretty(((Enum<?>) v).name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------ changing values
    private void set(Row r, Object v) {
        if (call(r.entry, "uiSet", v) == null) call(r.entry, "set", v);
    }

    private void step(Row r, int dir, boolean big) {
        Object v = call(r.entry, "get");
        Object min = call(r.entry, "getMin"), max = call(r.entry, "getMax");
        if (v instanceof Boolean) { set(r, !(Boolean) v); return; }
        if (v instanceof Enum) {
            Object[] all = v.getClass().getEnumConstants();
            if (all == null) all = ((Enum<?>) v).getDeclaringClass().getEnumConstants();
            int i = ((Enum<?>) v).ordinal();
            set(r, all[Math.floorMod(i + dir, all.length)]);
            return;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            long n = ((Number) v).longValue() + (long) dir * (big ? 10 : 1);
            if (min instanceof Number) n = Math.max(n, ((Number) min).longValue());
            if (max instanceof Number) n = Math.min(n, ((Number) max).longValue());
            set(r, v instanceof Integer ? (Object) (int) n : v instanceof Short ? (Object) (short) n : (Object) n);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue() + dir * (big ? 1.0 : 0.1);
            if (min instanceof Number) d = Math.max(d, ((Number) min).doubleValue());
            if (max instanceof Number) d = Math.min(d, ((Number) max).doubleValue());
            d = Math.round(d * 1000) / 1000.0;
            set(r, v instanceof Float ? (Object) (float) d : (Object) d);
        }
    }

    private void commitEdit() {
        if (editing == null) return;
        Object v = call(editing.entry, "get");
        try {
            String t = editText.trim();
            if (v instanceof Integer) set(editing, Integer.parseInt(t));
            else if (v instanceof Long) set(editing, Long.parseLong(t));
            else if (v instanceof Double) set(editing, Double.parseDouble(t));
            else if (v instanceof Float) set(editing, Float.parseFloat(t));
            else if (v instanceof String) set(editing, editText);
        } catch (NumberFormatException ignored) {}
        editing = null;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        PrideFrame f = PrideFrame.fit(width, height);
        String crumb = "Distant Horizons";
        List<String> names = new ArrayList<>();
        for (Class<?> c : path) names.add(0, pretty(c.getSimpleName()));
        if (names.size() > 1) crumb += "  ›  " + String.join("  ›  ", names.subList(1, names.size()));
        f.draw(this, crumb, rows.size() + " settings");

        int listX = f.cx, listY = f.cy + 26, listW = f.cw * 62 / 100, listH = f.ch - 26 - 30;
        int infoX = listX + listW + 10, infoW = f.cx + f.cw - infoX;
        // top bar: back / done
        boolean sub = path.size() > 1;
        PrideFrame.button(f.cx, f.cy, 90, 20, sub ? "‹ Back" : "‹ Done", PrideFrame.BUTTON, mx, my);
        if (path.isEmpty()) { drawCenteredString(fontRenderer, "Distant Horizons isn't installed.", width / 2, height / 2, 0xFFFFFF); super.drawScreen(mx, my, pt); return; }

        int total = rows.size() * ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - listH)));
        hoverTip = "";
        PrideFrame.clip(listX, listY, listW, listH);
        int y = listY - scroll;
        for (Row r : rows) {
            if (y + ROW > listY && y < listY + listH) drawRow(r, listX, y, listW - 8, mx, my, my >= listY && my < listY + listH);
            y += ROW;
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(listX + listW - 4, listY, listH, scroll, listH, total);

        // right side: what the hovered setting does
        PrideFrame.card(infoX, listY, infoW, listH, PrideFrame.PINK);
        String tip = hoverTip.isEmpty() ? "Point at a setting to see what it does.\n\nLeft-click: change   Right-click: back\nShift + click: big steps\nClick a number to type it." : hoverTip;
        int ty = listY + 8;
        for (String para : tip.replace("\\n", "\n").split("\n"))
            for (String line : fontRenderer.listFormattedStringToWidth(para, infoW - 16)) { fontRenderer.drawStringWithShadow(line, infoX + 8, ty, 0xE0E0E0); ty += 10; }
        fontRenderer.drawStringWithShadow("§7Changes save right away", f.cx, f.cy + f.ch - 12, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }

    private void drawRow(Row r, int x, int y, int w, int mx, int my, boolean inList) {
        boolean over = inList && mx >= x && mx < x + w && my >= y && my < y + ROW - 2;
        PrideFrame.tile(x, y, w, ROW - 2, r.category != null ? PrideFrame.BLUE : r.comment ? PrideFrame.DIM : PrideFrame.PINK, over, false);
        String name = label(r);
        if (r.comment) {
            fontRenderer.drawStringWithShadow("§7" + name, x + 8, y + 7, 0xFFFFFF);
            return;
        }
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(name, w / 2 - 12), x + 8, y + 7, 0xFFFFFF);
        if (over) hoverTip = "§l" + name + "§r\n\n" + tr(r.key + ".@tooltip", r.category != null ? "Opens more settings." : "").replaceAll("§.", "");
        if (r.category != null) {
            fontRenderer.drawStringWithShadow("open ›", x + w - 8 - fontRenderer.getStringWidth("open ›"), y + 7, PrideFrame.BLUE);
            return;
        }
        Object v = call(r.entry, "get");
        boolean number = v instanceof Number, text = v instanceof String;
        int cw = 150, cx = x + w - cw - 6;
        if (number) {
            boolean minus = PrideFrame.button(cx, y + 2, 20, ROW - 6, "-", PrideFrame.BUTTON, mx, my);
            boolean plus = PrideFrame.button(cx + cw - 20, y + 2, 20, ROW - 6, "+", PrideFrame.BUTTON, mx, my);
            String s = editing == r ? editText + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : " ") : show(v);
            PrideFrame.button(cx + 22, y + 2, cw - 44, ROW - 6, s, editing == r ? PrideFrame.TILE_ON : PrideFrame.TILE, mx, my);
            if (minus || plus) { /* hover only */ }
        } else {
            String s = text && editing == r ? editText + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : " ") : show(v);
            int col = v instanceof Boolean ? ((Boolean) v ? 0xFF2E7D32 : 0xFF6A2A2A) : editing == r ? PrideFrame.TILE_ON : PrideFrame.BUTTON;
            PrideFrame.button(cx, y + 2, cw, ROW - 6, fontRenderer.trimStringToWidth(s, cw - 8), col, mx, my);
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        PrideFrame f = PrideFrame.fit(width, height);
        if (mx >= f.cx && mx < f.cx + 90 && my >= f.cy && my < f.cy + 20) { back(); return; }
        int listX = f.cx, listY = f.cy + 26, listW = f.cw * 62 / 100, listH = f.ch - 26 - 30;
        if (mx < listX || mx >= listX + listW - 8 || my < listY || my >= listY + listH) { commitEdit(); return; }
        int i = (my - listY + scroll) / ROW;
        if (i < 0 || i >= rows.size()) return;
        Row r = rows.get(i);
        int x = listX, w = listW - 8, cw = 150, cx = x + w - cw - 6;
        if (editing != null && editing != r) commitEdit();
        if (r.category != null) { path.push(r.category); rebuild(); return; }
        if (r.entry == null) return;
        Object v = call(r.entry, "get");
        boolean big = isShiftKeyDown();
        int dir = button == 1 ? -1 : 1;
        if (v instanceof Number) {
            if (mx >= cx && mx < cx + 20) step(r, -1, big);
            else if (mx >= cx + cw - 20 && mx < cx + cw) step(r, 1, big);
            else if (mx >= cx + 22 && mx < cx + cw - 22) { editing = r; editText = show(v); }
        } else if (v instanceof String) {
            if (mx >= cx) { editing = r; editText = (String) v; }
        } else if (mx >= cx) step(r, dir, big);
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (editing != null) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { commitEdit(); return; }
            if (key == Keyboard.KEY_ESCAPE) { editing = null; return; }
            if (key == Keyboard.KEY_BACK) { if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1); return; }
            if (c >= ' ' && c != 127) editText += c;
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) { back(); return; }
        super.keyTyped(c, key);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll -= Integer.signum(d) * ROW * 2;
    }

    private void back() {
        commitEdit();
        if (path.size() > 1) { path.pop(); rebuild(); }
        else mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
