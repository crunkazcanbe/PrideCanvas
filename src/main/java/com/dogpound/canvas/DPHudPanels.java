package com.dogpound.canvas;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * PrideHUD's own cards (not copies of a mod's drawing): they read a mod's data and show more than the mod does
 * itself (requested feature).
 * Each panel is optional: it only appears if its mod is loaded and it has something to show.
 */
final class DPHudPanels {
    private DPHudPanels() {}

    interface Panel {
        String title();
        int accent();
        /** preferred width at the strip's height, or 0 = nothing to show right now */
        int width(Minecraft mc);
        void draw(Minecraft mc, int x, int y, int w, int h, float alpha);
    }

    static final List<Panel> ALL = new ArrayList<Panel>();

    static void init() {
        ALL.add(new Effects());
        if (Loader.isModLoaded("sereneseasons")) ALL.add(new Seasons());
        ALL.add(new Achievements());
        if (Loader.isModLoaded("mmorpg")) ALL.add(new MineAndSlash());
        if (Loader.isModLoaded("gunsrpg")) ALL.add(new GunsRpg());
        if (Loader.isModLoaded("vampirism")) ALL.add(new Vampirism());
        DPHudWidgets.init();
        ALL.addAll(DPHudWidgets.ALL);                  // the switchable info cards (HUD Settings > Info cards)
    }

    // ------------------------------------------------------------------ Serene Seasons (it has no HUD of its own)

    /** season + sub-season + day of the season + a bar through the year, read by reflection (requested feature) */
    static final class Seasons implements Panel {
        private static java.lang.reflect.Method get;
        private Object state(Minecraft mc) {
            try {
                if (get == null) get = Class.forName("sereneseasons.api.season.SeasonHelper").getMethod("getSeasonState", net.minecraft.world.World.class);
                return get.invoke(null, mc.world);
            } catch (Throwable t) { return null; }
        }
        @Override public String title() { return "Season"; }
        @Override public int accent() { return 0xFF8CE06A; }
        @Override public int width(Minecraft mc) { return mc.world != null && state(mc) != null ? 96 : 0; }
        @Override public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            Object st = state(mc);
            if (st == null) return;
            try {
                String sub = String.valueOf(st.getClass().getMethod("getSubSeason").invoke(st)).toLowerCase();   // EARLY_SPRING …
                int ticks = (Integer) st.getClass().getMethod("getSeasonCycleTicks").invoke(st), cycle = Math.max(1, (Integer) st.getClass().getMethod("getCycleDuration").invoke(st));
                int dayLen = Math.max(1, (Integer) st.getClass().getMethod("getDayDuration").invoke(st)), seasonLen = Math.max(1, (Integer) st.getClass().getMethod("getSeasonDuration").invoke(st));
                String[] parts = sub.split("_");
                String season = parts.length > 1 ? parts[1] : sub, when = parts.length > 1 ? parts[0] : "";
                int col = season.startsWith("spr") ? 0xFF8CE06A : season.startsWith("sum") ? 0xFFFFD23A : season.startsWith("aut") ? 0xFFE0802A : 0xFF9AD8FF;
                String icon = season.startsWith("spr") ? "\u273F" : season.startsWith("sum") ? "\u2600" : season.startsWith("aut") ? "\u2766" : "\u2744";
                FontRenderer fr = mc.fontRenderer;
                fr.drawStringWithShadow(icon + " " + Character.toUpperCase(season.charAt(0)) + season.substring(1), x + 4, y + 11, col & 0xFFFFFF);
                int day = (ticks % seasonLen) / dayLen + 1, days = seasonLen / dayLen;
                DPHud.small(fr, "\u00A77" + when + " \u00B7 day \u00A7f" + day + "\u00A77/" + days, x + 4, y + 22, 0xFFFFFF);
                Gui.drawRect(x + 4, y + h - 9, x + w - 4, y + h - 6, 0xFF1A1226);
                Gui.drawRect(x + 4, y + h - 9, x + 4 + (int) ((w - 8) * (ticks / (float) cycle)), y + h - 6, col);
            } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ status effects (requested feature)

    /** every active potion effect: its icon, level and time left; Minecraft's own top-right icons are hidden */
    static final class Effects implements Panel {
        @Override public String title() { return "Effects"; }
        @Override public int accent() { return 0xFF8CE06A; }
        private static java.util.List<net.minecraft.potion.PotionEffect> list(Minecraft mc) {
            java.util.List<net.minecraft.potion.PotionEffect> l = new java.util.ArrayList<net.minecraft.potion.PotionEffect>();
            if (mc.player != null) for (net.minecraft.potion.PotionEffect e : mc.player.getActivePotionEffects())
                if (e.doesShowParticles() || e.getPotion().shouldRenderHUD(e)) l.add(e);
            if (DPHudSettings.get().effectsBadFirst) l.sort((a, b) -> Boolean.compare(b.getPotion().isBadEffect(), a.getPotion().isBadEffect()));
            while (l.size() > Math.max(1, DPHudSettings.get().effectsMax)) l.remove(l.size() - 1);
            return l;
        }
        @Override public int width(Minecraft mc) { if (!DPHudSettings.get().effects) return 0; int n = list(mc).size(); return n == 0 ? 0 : 8 + n * 22; }
        @Override public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            FontRenderer fr = mc.fontRenderer;
            int ix = x + 5;
            for (net.minecraft.potion.PotionEffect e : list(mc)) {
                if (ix + 20 > x + w) break;
                net.minecraft.potion.Potion p = e.getPotion();
                boolean bad = p.isBadEffect();
                Gui.drawRect(ix - 1, y + 10, ix + 19, y + 30, bad ? 0x60E02040 : 0x408CE06A);
                GlStateManager.color(1F, 1F, 1F, 1F);
                GlStateManager.enableBlend();
                if (p.hasStatusIcon()) {
                    mc.getTextureManager().bindTexture(net.minecraft.client.gui.inventory.GuiContainer.INVENTORY_BACKGROUND);
                    int idx = p.getStatusIconIndex();
                    mc.ingameGUI.drawTexturedModalRect(ix + 1, y + 11, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18);
                }
                try { p.renderHUDEffect(ix + 1, y + 11, e, mc, a); } catch (Throwable ignored) {}
                String lv = e.getAmplifier() > 0 ? net.minecraft.client.resources.I18n.format("enchantment.level." + (e.getAmplifier() + 1)) : "";
                if (!lv.isEmpty()) DPHud.small(fr, "§f" + lv, ix + 17 - fr.getStringWidth(lv) / 2F, y + 11, 0xFFFFFF);
                String time = e.getIsPotionDurationMax() ? "\u221E" : net.minecraft.potion.Potion.getPotionDurationString(e, 1F);
                boolean ending = DPHudSettings.get().effectBlink && !e.getIsPotionDurationMax() && e.getDuration() < 200 && (System.currentTimeMillis() / 300) % 2 == 0;
                if (DPHudSettings.get().effectTimers) DPHud.small(fr, (ending ? "§c" : bad ? "§c" : "§a") + time, ix + 9 - fr.getStringWidth(time) / 4F, y + 33, 0xFFFFFF);
                ix += 22;
            }
            mc.getTextureManager().bindTexture(Gui.ICONS);
        }
    }

    // ------------------------------------------------------------------ achievements (requested feature)

    /** "Achievement unlocked!" with its icon and name for a few seconds, one after another; the top-right pop-up is dropped */
    static final class Achievements implements Panel {
        static final java.util.ArrayDeque<Object[]> QUEUE = new java.util.ArrayDeque<Object[]>();   // {ItemStack, title, frame, shownAt}
        static long showMs() { return Math.max(2, DPHudSettings.get().achSeconds) * 1000L; }
        private static java.lang.reflect.Field advField;

        static void add(net.minecraft.client.gui.toasts.AdvancementToast t) {
            try {
                if (advField == null) for (java.lang.reflect.Field f : t.getClass().getDeclaredFields())
                    if (net.minecraft.advancements.Advancement.class.isAssignableFrom(f.getType())) { f.setAccessible(true); advField = f; }
                net.minecraft.advancements.Advancement adv = (net.minecraft.advancements.Advancement) advField.get(t);
                net.minecraft.advancements.DisplayInfo d = adv.getDisplay();
                if (d == null) return;
                for (Object[] q : QUEUE) if (q[1].equals(d.getTitle().getFormattedText())) return;   // a toast is offered every tick until gone
                QUEUE.add(new Object[]{ d.getIcon(), d.getTitle().getFormattedText(), d.getFrame().getName(), 0L });
                while (QUEUE.size() > 20) QUEUE.removeFirst();
            } catch (Throwable ignored) {}
        }

        private static Object[] current() {
            long now = System.currentTimeMillis();
            Object[] c = QUEUE.peekFirst();
            if (c == null) return null;
            if ((Long) c[3] == 0L) {
                c[3] = now;
                if (DPHudSettings.get().achSound) Minecraft.getMinecraft().getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4F));
            }
            if (now - (Long) c[3] > showMs()) { QUEUE.removeFirst(); return current(); }
            return c;
        }

        @Override public String title() { Object[] c = current(); return c == null ? "" : "challenge".equals(c[2]) ? "Challenge complete!" : "goal".equals(c[2]) ? "Goal reached!" : "Achievement!"; }
        @Override public int accent() { Object[] c = current(); return c != null && "challenge".equals(c[2]) ? 0xFFB57EDC : 0xFFFFD23A; }
        @Override public int width(Minecraft mc) {
            Object[] c = current();
            return c == null ? 0 : Math.max(90, mc.fontRenderer.getStringWidth((String) c[1]) + 34) + (QUEUE.size() > 1 ? 12 : 0);
        }
        @Override public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            Object[] c = current();
            if (c == null) return;
            long age = System.currentTimeMillis() - (Long) c[3];
            float glow = 0.5F + 0.5F * (float) Math.sin(age / 180.0);
            Gui.drawRect(x + 3, y + 12, x + 25, y + 34, (int) (0x40 + 0x40 * glow) << 24 | 0xFFD23A);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) c[0], x + 6, y + 15);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            mc.fontRenderer.drawStringWithShadow((String) c[1], x + 30, y + 19, 0xFFFFFF);
            if (QUEUE.size() > 1) DPHud.small(mc.fontRenderer, "§7+" + (QUEUE.size() - 1), x + w - 12, y + 34, 0xFFFFFF);
            Gui.drawRect(x + 30, y + 31, x + 30 + (int) ((w - 36) * (1F - Math.min(1F, age / (float) showMs()))), y + 32, 0xFFFFD23A);
        }
    }

    // ------------------------------------------------------------------ Mine and Slash

    /** level, XP to next level, mana + energy, and every spell you carry with its cooldown sweeping */
    static final class MineAndSlash implements Panel {
        private final List<ItemStack> spells = new ArrayList<ItemStack>();

        @Override public String title() { return "Mine and Slash"; }
        @Override public int accent() { return 0xFFE0A030; }

        private void collect(EntityPlayerSP p) {
            spells.clear();
            for (int i = 0; i < 9; i++) addIfSpell(p.inventory.getStackInSlot(i));
            addIfSpell(p.getHeldItemOffhand());
        }

        private void addIfSpell(ItemStack s) {
            if (!s.isEmpty() && s.getItem() instanceof com.robertx22.items.gearitems.bases.BaseSpellItem && spells.size() < 6) spells.add(s);
        }

        @Override
        public int width(Minecraft mc) {
            if (mc.player == null) return 0;
            collect(mc.player);
            return 92 + spells.size() * 20;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            EntityPlayerSP p = mc.player;
            FontRenderer fr = mc.fontRenderer;
            int lvl = 0, exp = 0, need = 1;
            float mana = 0, energy = 0;
            try {
                com.robertx22.uncommon.capability.EntityData.UnitData d = com.robertx22.uncommon.datasaving.Load.Unit(p);
                if (d != null) { lvl = d.getLevel(); exp = d.getExp(); need = Math.max(1, d.GetExpRequiredForLevelUp()); mana = d.getCurrentMana(); energy = d.getCurrentEnergy(); }
            } catch (Throwable ignored) {}
            // level badge
            String lv = String.valueOf(lvl);
            Gui.drawRect(x + 4, y + 10, x + 26, y + h - 4, 0xFF2A1A08);
            Gui.drawRect(x + 4, y + 10, x + 26, y + 11, 0xFFE0A030);
            DPHud.small(fr, "§6LVL", x + 9, y + 13, 0xFFFFFF);
            fr.drawStringWithShadow(lv, x + 15 - fr.getStringWidth(lv) / 2F, y + 20, 0xFFD060);
            // xp bar + mana/energy numbers
            int bx = x + 30, bw = 58;
            Gui.drawRect(bx, y + 12, bx + bw, y + 16, 0xFF1A1206);
            Gui.drawRect(bx, y + 12, bx + (int) (bw * Math.min(1F, exp / (float) need)), y + 16, 0xFFE0A030);
            DPHud.small(fr, "§7XP §f" + exp + "§7/" + need, bx, y + 18, 0xFFFFFF);
            DPHud.small(fr, "§9✦ §f" + (int) mana + "   §e⚡ §f" + (int) energy, bx, y + 25, 0xFFFFFF);
            // spells with cooldowns
            int sx = x + 92;
            RenderHelper.enableGUIStandardItemLighting();
            for (ItemStack s : spells) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(p, s, sx, y + 14);
                mc.getRenderItem().renderItemOverlays(fr, s, sx, y + 14);    // draws the cooldown sweep too
                sx += 20;
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
        }
    }

    // ------------------------------------------------------------------ Guns RPG

    /** your gun-skill bonuses + any injury (bleeding, poison, infection, broken bone) drawn the mod's own way */
    static final class GunsRpg implements Panel {
        @Override public String title() { return "Guns RPG"; }
        @Override public int accent() { return 0xFFB04040; }

        private static dev.toma.gunsrpg.common.capability.PlayerData data(Minecraft mc) {
            return mc.player == null ? null : dev.toma.gunsrpg.common.capability.PlayerDataFactory.get(mc.player);
        }

        private static int injuries(dev.toma.gunsrpg.common.capability.PlayerData d) {
            int n = 0;
            for (dev.toma.gunsrpg.common.debuffs.Debuff b : d.getDebuffData().getDebuffs()) if (b != null && !b.isInvalid()) n++;
            return n;
        }

        @Override
        public int width(Minecraft mc) {
            dev.toma.gunsrpg.common.capability.PlayerData d = data(mc);
            return d == null ? 0 : 96 + injuries(d) * 22;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            dev.toma.gunsrpg.common.capability.PlayerData d = data(mc);
            if (d == null) return;
            FontRenderer fr = mc.fontRenderer;
            dev.toma.gunsrpg.common.capability.object.PlayerSkills s = d.getSkills();
            DPHud.small(fr, "§c⚔ §f+" + s.extraDamage + " dmg   §d☠ §f" + Math.round(s.instantKillChance * 100) + "%", x + 4, y + 12, 0xFFFFFF);
            DPHud.small(fr, "§a» §f" + Math.round(s.agilitySpeed * 100) + "% speed", x + 4, y + 19, 0xFFFFFF);
            DPHud.small(fr, "§7resist §2☣" + s.poisonResistance + " §6✚" + s.infectionResistance + " §c🩸" + s.bleedResistance + " §f🦴" + s.brokenBoneResistance, x + 4, y + 26, 0xFFFFFF);
            int dx = x + 96;
            for (dev.toma.gunsrpg.common.debuffs.Debuff b : d.getDebuffData().getDebuffs()) {
                if (b == null || b.isInvalid()) continue;
                b.draw(dx, y + 11, 20, h - 14, 0F, fr);
                dx += 22;
            }
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    // ------------------------------------------------------------------ Vampirism

    /** vampire or hunter: faction, level and progress (only once you've joined one) */
    static final class Vampirism implements Panel {
        @Override public String title() { return "Vampirism"; }
        @Override public int accent() { return 0xFF8A1020; }

        private static de.teamlapen.vampirism.api.entity.factions.IPlayableFaction faction(Minecraft mc) {
            if (mc.player == null) return null;
            de.teamlapen.vampirism.entity.factions.FactionPlayerHandler h = de.teamlapen.vampirism.entity.factions.FactionPlayerHandler.get(mc.player);
            return h == null ? null : h.getCurrentFaction();
        }

        @Override public int width(Minecraft mc) { return faction(mc) == null ? 0 : 90; }

        @Override
        public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            de.teamlapen.vampirism.entity.factions.FactionPlayerHandler fh = de.teamlapen.vampirism.entity.factions.FactionPlayerHandler.get(mc.player);
            de.teamlapen.vampirism.api.entity.factions.IPlayableFaction f = fh.getCurrentFaction();
            if (f == null) return;
            FontRenderer fr = mc.fontRenderer;
            int col = 0xFF000000 | f.getColor();
            String name = net.minecraft.client.resources.I18n.format(f.getUnlocalizedName());
            fr.drawStringWithShadow(fr.trimStringToWidth(name, w - 8), x + 4, y + 11, col & 0xFFFFFF);
            int lvl = fh.getCurrentLevel(), max = f.getHighestReachableLevel();
            DPHud.small(fr, "§7Level §f" + lvl + "§7/" + max, x + 4, y + 22, 0xFFFFFF);
            Gui.drawRect(x + 4, y + 28, x + w - 4, y + 31, 0xFF200608);
            Gui.drawRect(x + 4, y + 28, x + 4 + (int) ((w - 8) * Math.min(1F, fh.getCurrentLevelRelative())), y + 31, col);
        }
    }
}
