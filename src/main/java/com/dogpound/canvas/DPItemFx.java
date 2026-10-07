package com.dogpound.canvas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Pride Item FX: every inventory slot comes alive (requested feature).
 * <ul>
 *   <li>Hover: the item pops bigger and wiggles (a springy, fading shake), then bobs gently while you stay on it,
 *       with a little sound whose pitch follows the item's rarity.</li>
 *   <li>Rarity glow: uncommon / rare / epic items get a soft pulsing glow in their colour; enchanted items shimmer
 *       with a moving rainbow sheen.</li>
 *   <li>New items (Item Highlighter): anything that just arrived in your inventory twinkles with a pink star until you
 *       hover it.</li>
 * </ul>
 * Hooked from {@code MixinGuiContainerSlotFx} (pre / post of GuiContainer.drawSlot). Settings: Esc menu > Item FX.
 */
@Mod.EventBusSubscriber(modid = DPMenuMod.MODID, value = Side.CLIENT)
public final class DPItemFx {
    private DPItemFx() {}

    // ---------------------------------------------------------------- settings (config/pridecanvas/itemfx.json)

    public static final class Settings {
        public boolean wiggle = true, popOut = true, bob = true, hoverSound = true, rarityGlow = true, enchantShimmer = true, newSparkle = true;
        public boolean everyScreen = true;            // off = creative inventory only
        public boolean prideCreative = true;          // Pride Creative replaces the tabbed creative inventory
        public int wiggleStrength = 100;              // %
        public int soundVolume = 60;                  // %
        public String soundStyle = "chime";           // chime, pop, bubble, pling, click, xylophone
        public int glowStrength = 70;                 // %
    }

    private static final File FILE = new File("config/pridecanvas/itemfx.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings S;

    public static Settings get() {
        if (S == null) {
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { S = GSON.fromJson(r, Settings.class); } catch (Throwable ignored) { }
            if (S == null) S = new Settings();
        }
        return S;
    }

    public static void save() {
        try { FILE.getParentFile().mkdirs(); try (Writer w = new FileWriter(FILE)) { GSON.toJson(get(), w); } } catch (Throwable ignored) { }
    }

    public static void reset() { S = new Settings(); save(); }

    public static final String[] STYLES = {"chime", "pop", "bubble", "pling", "click", "xylophone", "bell"};

    // ---------------------------------------------------------------- hover tracking

    private static Slot hovered;
    private static long hoverStart;
    private static boolean pushed;

    private static boolean active(GuiContainer gui) {
        return get().everyScreen || gui instanceof net.minecraft.client.gui.inventory.GuiContainerCreative;
    }

    /** before a slot draws: rarity glow behind it, then (for the hovered slot) the pop + wiggle transform */
    public static void preSlot(GuiContainer gui, Slot slot) {
        pushed = false;
        try {
            if (!active(gui)) return;
            Settings s = get();
            ItemStack st = slot.getStack();
            if (st.isEmpty()) return;
            if (s.rarityGlow) glow(slot, st, s);
            if (!mouseOver(gui, slot)) return;
            if (hovered != slot) {
                hovered = slot;
                hoverStart = System.currentTimeMillis();
                newItems.remove(key(slot));
                if (s.hoverSound) sound(st, slot.slotNumber);
            }
            if (!s.wiggle && !s.popOut && !s.bob) return;
            float t = (System.currentTimeMillis() - hoverStart) / 1000f;
            float k = s.wiggleStrength / 100f;
            float angle = s.wiggle ? (float) (24 * k * Math.exp(-t * 3.2) * Math.sin(t * 30) + 4 * k * Math.sin(t * 5)) : 0;   // springy shake, then a gentle rock
            float scale = 1f;
            if (s.popOut) scale += (float) (0.35 * k * Math.exp(-t * 6) + 0.15 * k);          // pop, settle a bit bigger
            float bob = s.bob ? (float) (Math.sin(t * 4.2) * 0.9 * k) * (float) Math.min(1, t * 2) : 0;
            GlStateManager.pushMatrix();
            pushed = true;
            float cx = slot.xPos + 8, cy = slot.yPos + 8;
            GlStateManager.translate(cx, cy + bob, 40);                                          // a little above its neighbours
            GlStateManager.rotate(angle, 0, 0, 1);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-cx, -cy, 0);
        } catch (Throwable ignored) { }
    }

    /**
     * Is the mouse on this slot? GuiContainer only works out its hovered slot AFTER drawing each slot, so it can't be
     * asked while drawing (that's why nothing wiggled at first, 2026-10-04). Work it out from the mouse instead.
     */
    static boolean mouseOver(GuiContainer gui, Slot slot) {
        Minecraft mc = Minecraft.getMinecraft();
        int mx = org.lwjgl.input.Mouse.getX() * gui.width / mc.displayWidth;
        int my = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / mc.displayHeight - 1;
        int x = mx - gui.getGuiLeft(), y = my - gui.getGuiTop();
        return slot.isEnabled() && x >= slot.xPos - 1 && x < slot.xPos + 17 && y >= slot.yPos - 1 && y < slot.yPos + 17;
    }

    /** after a slot draws: undo the transform; twinkle new items; shimmer enchanted ones */
    public static void postSlot(GuiContainer gui, Slot slot) {
        try {
            if (pushed) { GlStateManager.popMatrix(); pushed = false; }
            if (!active(gui)) return;
            Settings s = get();
            ItemStack st = slot.getStack();
            if (st.isEmpty()) return;
            if (s.enchantShimmer && st.hasEffect()) shimmer(slot);
            if (s.newSparkle && newItems.containsKey(key(slot))) sparkle(slot);
        } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- looks

    static int rarityColor(ItemStack st) {
        EnumRarity r = st.getRarity();
        if (r == EnumRarity.EPIC) return 0xC060FF;
        if (r == EnumRarity.RARE) return 0x5BCEFA;
        if (r == EnumRarity.UNCOMMON) return 0xFFE14A;
        return -1;
    }

    private static void glow(Slot slot, ItemStack st, Settings s) {
        int c = rarityColor(st);
        if (c < 0) return;
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 350.0 + slot.slotNumber * 0.7);
        int a = (int) (110 * pulse * s.glowStrength / 100f);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        int x = slot.xPos, y = slot.yPos;
        // soft square glow: brighter in the middle, fading to the edges
        for (int i = 0; i < 4; i++) {
            int aa = Math.max(0, a - i * 26) << 24;
            Gui.drawRect(x - 1 + i, y - 1 + i, x + 17 - i, y + 17 - i, aa | c);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    private static void shimmer(Slot slot) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        long t = System.currentTimeMillis();
        int x = slot.xPos, y = slot.yPos;
        int band = (int) ((t / 40 + slot.slotNumber * 7) % 48) - 16;                      // a diagonal light band sweeping across
        int[] rb = PrideFrame.RAINBOW;
        for (int i = 0; i < 16; i++) {
            int d = i + band;
            if (d < 0 || d > 15) continue;
            int col = rb[(i / 3) % rb.length] & 0xFFFFFF;
            Gui.drawRect(x + d, y + i, x + d + 1, y + i + 1, 0x70000000 | col);
        }
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    private static void sparkle(Slot slot) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 320);
        double t = System.currentTimeMillis() / 1000.0;
        float tw = (float) (0.5 + 0.5 * Math.sin(t * 6 + slot.slotNumber));
        int a = (int) (120 + 135 * tw) << 24;
        int x = slot.xPos + 13, y = slot.yPos + 1;
        int pink = 0xF5A9B8, white = 0xFFFFFF;
        Gui.drawRect(x, y - 2, x + 1, y + 3, a | pink);                                    // a little 4-point star
        Gui.drawRect(x - 2, y, x + 3, y + 1, a | pink);
        Gui.drawRect(x, y, x + 1, y + 1, a | white);
        if (tw > 0.7f) { Gui.drawRect(x - 9, y + 9, x - 8, y + 10, (int) (255 * (tw - 0.7f) / 0.3f) << 24 | white); }
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    // ---------------------------------------------------------------- sounds

    static void sound(ItemStack st, int index) {
        Settings s = get();
        float vol = s.soundVolume / 100f;
        if (vol <= 0) return;
        EnumRarity r = st.getRarity();
        float base = r == EnumRarity.EPIC ? 1.5f : r == EnumRarity.RARE ? 1.33f : r == EnumRarity.UNCOMMON ? 1.19f : 1f;
        float[] scale = {1f, 1.122f, 1.26f, 1.335f, 1.498f};
        float pitch = base * scale[Math.floorMod(index, scale.length)];
        SoundEvent ev;
        switch (s.soundStyle) {
            case "pop": ev = SoundEvents.ENTITY_CHICKEN_EGG; pitch *= 1.6f; vol *= 0.5f; break;
            case "bubble": ev = SoundEvents.BLOCK_LAVA_POP; pitch *= 1.4f; vol *= 0.4f; break;
            case "pling": ev = SoundEvents.BLOCK_NOTE_PLING; vol *= 0.35f; break;
            case "click": ev = SoundEvents.UI_BUTTON_CLICK; pitch *= 1.8f; vol *= 0.25f; break;
            case "xylophone": ev = SoundEvents.BLOCK_NOTE_XYLOPHONE; vol *= 0.4f; break;
            case "bell": ev = SoundEvents.BLOCK_NOTE_BELL; vol *= 0.35f; break;
            default: DPSounds.play(DPSounds.HOVER, pitch * 0.9f, vol); return;
        }
        try {
            Minecraft.getMinecraft().getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(ev, Math.min(2f, pitch)));
        } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- Item Highlighter: what's new in your inventory

    /** player inventory index → when it became new */
    private static final Map<Integer, Long> newItems = new HashMap<>();
    private static final Map<Integer, String> seen = new HashMap<>();
    private static boolean primed;

    private static Integer key(Slot slot) {
        return slot.inventory instanceof InventoryPlayer ? slot.getSlotIndex() : Integer.MIN_VALUE + slot.slotNumber;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) { primed = false; seen.clear(); newItems.clear(); return; }
        if (mc.player.ticksExisted % 5 != 0) return;
        InventoryPlayer inv = mc.player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            String id = st.isEmpty() ? "" : st.getItem().getRegistryName() + "@" + st.getMetadata();
            String was = seen.put(i, id);
            if (primed && !id.isEmpty() && !id.equals(was) && !mc.player.isCreative()) newItems.put(i, System.currentTimeMillis());
            if (id.isEmpty()) newItems.remove(i);
        }
        primed = true;
        newItems.values().removeIf(t -> System.currentTimeMillis() - t > 5 * 60_000);       // fade after 5 minutes
    }
}
