package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat, slot-filling icons. Each button maps to a flat item texture (items render flat,
 * unlike blocks), scaled up to nearly fill its slot. Empty cells cycle through a varied
 * set of flat material textures (vanilla + Mekanism/GregTech/etc. when present) so the
 * whole grid reads like an inventory full of ores.
 */
public final class DPIcons {
    private DPIcons() {}

    private static ItemStack[] mats;

    /** Draw the icon for a label. Uses the flat Minecraft-block PNGs from the Tools panel's
     *  Sites page when present (her requested look); falls back to a flat item texture. */
    public static void draw(Minecraft mc, String label, int x, int y, int w, int h) {
        if (DPBlocks.available()) {
            DPBlocks.draw(mc, DPBlocks.indexForLabel(label), x, y, w, h);
        } else {
            drawStack(mc, iconFor(label), x, y, w, h);
        }
    }

    /** Draw a varied block/material icon for an empty cell (picked by cell index). */
    public static void drawBlankAt(Minecraft mc, int index, int x, int y, int w, int h) {
        if (DPBlocks.available()) {
            DPBlocks.draw(mc, index, x, y, w, h);
            return;
        }
        ItemStack[] m = mats();
        if (m.length == 0) return;
        drawStack(mc, m[Math.floorMod(index, m.length)], x, y, w, h);
    }

    private static void drawStack(Minecraft mc, ItemStack stack, int x, int y, int w, int h) {
        if (stack == null || stack.isEmpty()) return;
        int size = Math.min(w, h) - 4;
        if (size < 8) size = Math.min(w, h);
        float scale = size / 16.0F;
        int ix = x + (w - size) / 2, iy = y + (h - size) / 2;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(ix, iy, 0);
        GlStateManager.scale(scale, scale, 1.0F);
        mc.getRenderItem().renderItemIntoGUI(stack, 0, 0);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** Flat item for a button label (keyword match). */
    public static ItemStack iconFor(String label) {
        if (label == null || label.trim().isEmpty()) return ItemStack.EMPTY;
        String s = label.toLowerCase();
        if (s.contains("video") || s.contains("graphic")) return new ItemStack(Items.GLOWSTONE_DUST);
        if (s.contains("control"))                         return new ItemStack(Items.REPEATER);
        if (s.contains("sound") || s.contains("music"))    return new ItemStack(Items.RECORD_CAT);
        if (s.contains("language"))                        return new ItemStack(Items.BOOK);
        if (s.contains("chat"))                            return new ItemStack(Items.PAPER);
        if (s.contains("resource") || s.contains("pack") || s.contains("addon")) return new ItemStack(Items.PAINTING);
        if (s.contains("skin"))                            return new ItemStack(Items.LEATHER_CHESTPLATE);
        if (s.contains("snooper"))                         return new ItemStack(Items.ENDER_EYE);
        if (s.contains("done"))                            return new ItemStack(Items.EMERALD);
        if (s.contains("fov"))                             return new ItemStack(Items.COMPASS);
        if (s.contains("difficulty"))                      return new ItemStack(Items.IRON_SWORD);
        if (s.contains("browser"))                         return new ItemStack(Items.WRITABLE_BOOK);
        if (s.contains("pymtech") || s.contains("pym"))    return new ItemStack(Items.COMPARATOR);
        if (s.contains("tweak") || s.contains("inv"))      return new ItemStack(Items.CHEST_MINECART);
        if (s.contains("mod"))                             return new ItemStack(Items.ENCHANTED_BOOK);
        if (s.contains("config"))                          return new ItemStack(Items.REDSTONE);
        if (s.contains("web"))                             return new ItemStack(Items.FILLED_MAP);
        if (s.contains("option"))                          return new ItemStack(Items.COMPARATOR);
        if (s.contains("single"))                          return new ItemStack(Items.WHEAT_SEEDS);
        if (s.contains("multi"))                           return new ItemStack(Items.NAME_TAG);
        if (s.contains("quit") || s.contains("exit"))      return new ItemStack(Items.BLAZE_POWDER);
        return new ItemStack(Items.IRON_INGOT);
    }

    /** Build the varied material list once: vanilla ingots/gems + mod materials if installed. */
    private static ItemStack[] mats() {
        if (mats != null) return mats;
        List<ItemStack> l = new ArrayList<ItemStack>();
        l.add(new ItemStack(Items.IRON_INGOT));
        l.add(new ItemStack(Items.GOLD_INGOT));
        l.add(new ItemStack(Items.DIAMOND));
        l.add(new ItemStack(Items.EMERALD));
        l.add(new ItemStack(Items.COAL));
        l.add(new ItemStack(Items.REDSTONE));
        l.add(new ItemStack(Items.QUARTZ));
        l.add(new ItemStack(Items.DYE, 1, 4));            // lapis
        // mod materials by registry name — skipped silently if the mod isn't installed
        String[] modItems = {
            "mekanism:ingot", "mekanism:dust", "mekanism:clump",
            "gregtech:meta_ingot", "gregtech:meta_gem",
            "thermalfoundation:material", "immersiveengineering:metal",
            "eln:ore", "hbm:ingot_steel", "hbm:powder_iron"
        };
        for (String name : modItems) {
            Item it = Item.getByNameOrId(name);
            if (it != null) l.add(new ItemStack(it));
        }
        mats = l.toArray(new ItemStack[0]);
        return mats;
    }
}
