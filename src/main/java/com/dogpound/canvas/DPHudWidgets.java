package com.dogpound.canvas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.DifficultyInstance;

/**
 * Small status cards for the Pride HUD, each switched on/off in HUD Settings > Info cards (requested feature). Every card is a little
 * widget: a big coloured icon, a big value, a detail line and, where it means something, a coloured bar.
 * Widths only grow while shown (DPHud.sticky), so nothing slides around.
 */
final class DPHudWidgets {
    private DPHudWidgets() {}

    /** what a card shows right now */
    static final class View {
        String icon, value, sub;
        int iconColor;
        float bar = -1;                    // 0..1, or -1 = no bar
        int barFrom = 0xFF8CE06A, barTo = 0xFF4CB020;
        View(String icon, int iconColor, String value, String sub) { this.icon = icon; this.iconColor = iconColor; this.value = value; this.sub = sub; }
        View bar(float f) { bar = Math.max(0, Math.min(1, f)); return this; }
        /** green when full, amber in the middle, red when low */
        View health(float f) { bar(f); if (f <= 0.25F) { barFrom = 0xFFFF5A5A; barTo = 0xFFB01030; } else if (f <= 0.5F) { barFrom = 0xFFFFD23A; barTo = 0xFFE09010; } return this; }
        View colors(int from, int to) { barFrom = from; barTo = to; return this; }
    }

    private static final View QUIET = new View("·", 0x606060, "§8—", "");

    static final class Widget implements DPHudPanels.Panel {
        final String id, title; final int accent; final boolean onByDefault;
        final Function<Minecraft, View> view;
        Widget(String id, String title, int accent, boolean onByDefault, Function<Minecraft, View> view) {
            this.id = id; this.title = title; this.accent = accent; this.onByDefault = onByDefault; this.view = view;
        }
        boolean on() { Boolean b = DPHudSettings.get().widgets.get(id); return b == null ? onByDefault : b; }
        private View get(Minecraft mc) {
            if (!on() || mc.player == null || mc.world == null) return null;
            // a card that's on never vanishes (her "stuff moves around"): nothing to say right now = a quiet dash
            try { View v = view.apply(mc); return v == null ? QUIET : v; } catch (Throwable t) { return QUIET; }
        }
        @Override public String title() { return title; }
        @Override public int accent() { return accent; }
        @Override public int width(Minecraft mc) {
            View v = get(mc);
            if (v == null) return 0;
            FontRenderer f = mc.fontRenderer;
            float ts = textScale();
            int text = (int) Math.max(f.getStringWidth(v.value) * ts, f.getStringWidth(v.sub) * ts * 0.72F);
            return Math.max(54, 8 + iconW(ts) + 4 + text + 6);
        }
        static float textScale() { return Math.max(0.6F, Math.min(1.6F, DPHudSettings.get().infoTextScale / 100F)); }
        static int iconW(float ts) { return (int) (11 * ts * 1.5F); }

        @Override public void draw(Minecraft mc, int x, int y, int w, int h, float a) {
            View v = get(mc);
            if (v == null) return;
            FontRenderer f = mc.fontRenderer;
            float ts = textScale();
            int alpha = Math.max(4, (int) (a * 255)) << 24;
            int top = y + DPHud.LABEL_H + 2, bottom = y + h - 2;
            boolean bar = v.bar >= 0 && DPHudSettings.get().infoBars;
            int barH = bar ? 5 : 0;
            int area = bottom - top - (bar ? barH + 2 : 0);
            // icon: big, coloured, centred in its column
            float is = ts * 1.5F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 5, top + (area - 8 * is) / 2F, 0);
            GlStateManager.scale(is, is, 1);
            text(f, v.icon, alpha | (v.iconColor & 0xFFFFFF));
            GlStateManager.popMatrix();
            // value + detail
            float tx = x + 5 + iconW(ts) + 3;
            boolean two = !v.sub.isEmpty();
            float lineV = 9 * ts, lineS = 9 * ts * 0.72F, total = lineV + (two ? lineS + 1 : 0);
            float ty = top + (area - total) / 2F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx, ty, 0);
            GlStateManager.scale(ts, ts, 1);
            text(f, f.trimStringToWidth(v.value, Math.max(4, (int) ((x + w - tx - 3) / ts))), alpha | 0xFFFFFF);
            GlStateManager.popMatrix();
            if (two) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(tx, ty + lineV + 1, 0);
                GlStateManager.scale(ts * 0.72F, ts * 0.72F, 1);
                text(f, f.trimStringToWidth("§7" + v.sub, Math.max(4, (int) ((x + w - tx - 3) / (ts * 0.72F)))), alpha | 0xFFFFFF);
                GlStateManager.popMatrix();
            }
            // the bar: dark track, gradient fill, light top edge, bright end cap
            if (bar) {
                int bx = x + 5, bw = w - 10, by = bottom - barH;
                Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + barH + 1, 0x90000000);
                int fill = (int) (bw * v.bar);
                PrideFrame.gradient(bx, by, bx + bw, by + barH, 0xFF201828, 0xFF140E1C);
                if (fill > 0) {
                    gradientH(bx, by, bx + fill, by + barH, v.barFrom, v.barTo);
                    Gui.drawRect(bx, by, bx + fill, by + 1, 0x60FFFFFF);
                    Gui.drawRect(bx + fill - 1, by, bx + fill, by + barH, 0xC0FFFFFF);
                }
            }
            GlStateManager.color(1F, 1F, 1F, 1F);
        }

        private static void text(FontRenderer f, String s, int color) {
            if (DPHudSettings.get().textShadow) f.drawStringWithShadow(s, 0, 0, color); else f.drawString(s, 0, 0, color);
        }

        /** left-to-right gradient (PrideFrame's goes top to bottom) */
        private static void gradientH(int x0, int y0, int x1, int y1, int from, int to) {
            int steps = Math.max(1, Math.min(24, (x1 - x0) / 3));
            for (int i = 0; i < steps; i++) {
                float k = i / (float) Math.max(1, steps - 1);
                int a = (int) (((from >>> 24) & 255) * (1 - k) + ((to >>> 24) & 255) * k), r = (int) (((from >> 16) & 255) * (1 - k) + ((to >> 16) & 255) * k),
                        g = (int) (((from >> 8) & 255) * (1 - k) + ((to >> 8) & 255) * k), b = (int) ((from & 255) * (1 - k) + (to & 255) * k);
                int sx = x0 + (x1 - x0) * i / steps, ex = x0 + (x1 - x0) * (i + 1) / steps;
                Gui.drawRect(sx, y0, ex, y1, a << 24 | r << 16 | g << 8 | b);
            }
        }
    }

    static final List<Widget> ALL = new ArrayList<Widget>();

    private static float wear(ItemStack s) { return s.isEmpty() || !s.isItemStackDamageable() ? -1 : (s.getMaxDamage() - s.getItemDamage()) / (float) Math.max(1, s.getMaxDamage()); }
    private static final String[] DIRS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
    private static final String[] DIR_NAMES = { "South", "South-west", "West", "North-west", "North", "North-east", "East", "South-east" };
    private static final String[] MOON = { "Full moon", "Waning gibbous", "Last quarter", "Waning crescent", "New moon", "Waxing crescent", "First quarter", "Waxing gibbous" };
    private static final String[] MOON_ICON = { "●", "◕", "◑", "◔", "○", "◔", "◐", "◕" };
    private static double lastX, lastZ; private static long lastT; private static double speed;

    private static String dir(float yaw) { return DIRS[MathHelper.floor((MathHelper.wrapDegrees(yaw) + 22.5F) / 45F) & 7]; }

    static void init() {
        if (!ALL.isEmpty()) return;
        ALL.add(new Widget("speed", "Speed", 0xFF7CC8FF, true, mc -> {
            EntityPlayerSP p = mc.player; Entity e = p.getRidingEntity() != null ? p.getLowestRidingEntity() : p;
            long now = System.currentTimeMillis();
            if (now - lastT >= 250) {
                double d = Math.sqrt((e.posX - lastX) * (e.posX - lastX) + (e.posZ - lastZ) * (e.posZ - lastZ));
                speed = lastT == 0 ? 0 : d / ((now - lastT) / 1000.0); lastX = e.posX; lastZ = e.posZ; lastT = now;
            }
            int u = DPHudSettings.get().speedUnit;
            String main = u == 1 ? String.format("%.0f km/h", speed * 3.6) : u == 2 ? String.format("%.0f mph", speed * 2.237) : String.format("%.1f b/s", speed);
            String state = speed < 0.2 ? "standing" : speed < 4.4 ? "walking" : speed < 6 ? "running" : speed < 12 ? "fast" : "flying!";
            return new View("»", 0x7CC8FF, main, state).bar((float) (speed / 20.0)).colors(0xFF7CC8FF, 0xFFB59CFF);
        }));
        ALL.add(new Widget("compass", "Compass", 0xFFFFD23A, true, mc -> {
            float yaw = MathHelper.wrapDegrees(mc.player.rotationYaw);
            int i = MathHelper.floor((yaw + 22.5F) / 45F) & 7;
            return new View("➤", 0xFFD23A, DIRS[i] + " " + Math.round((yaw + 360) % 360) + "°", DIR_NAMES[i]);
        }));
        ALL.add(new Widget("held", "Tool wear", 0xFFFF9A3C, true, mc -> {
            ItemStack m = mc.player.getHeldItemMainhand();
            float wm = wear(m), wo = wear(mc.player.getHeldItemOffhand());
            if (wm < 0 && wo < 0) return new View("⚒", 0x707070, "§7no tool", "");
            ItemStack s = wm >= 0 ? m : mc.player.getHeldItemOffhand();
            float w = wm >= 0 ? wm : wo;
            int left = s.getMaxDamage() - s.getItemDamage();
            return new View("⚒", w <= 0.15F ? 0xFF5A5A : 0xFF9A3C, Math.round(w * 100) + "%", left + " uses left").health(w);
        }));
        ALL.add(new Widget("armor", "Armor", 0xFFB0B8C8, true, mc -> {
            float worst = 2; int pieces = 0;
            EntityEquipmentSlot[] sl = { EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET };
            for (EntityEquipmentSlot e : sl) { float w = wear(mc.player.getItemStackFromSlot(e)); if (w >= 0) { pieces++; worst = Math.min(worst, w); } }
            int pts = mc.player.getTotalArmorValue();
            if (pieces == 0) return new View("⛨", 0x707070, "§7no armor", "");
            return new View("⛨", 0xC8D0DC, pts + " armor", pieces + " pieces, worst " + Math.round(worst * 100) + "%").health(worst);
        }));
        ALL.add(new Widget("bag", "Bag space", 0xFFA6E36B, true, mc -> {
            int free = 0, n = mc.player.inventory.mainInventory.size();
            for (ItemStack s : mc.player.inventory.mainInventory) if (s.isEmpty()) free++;
            return new View("▣", free == 0 ? 0xFF5A5A : 0xA6E36B, free + " free", "of " + n + " slots").health(free / (float) n);
        }));
        ALL.add(new Widget("target", "Looking at", 0xFFFF7FAE, true, mc -> {
            RayTraceResult r = mc.objectMouseOver;
            if (r == null) return null;
            if (r.typeOfHit == RayTraceResult.Type.ENTITY && r.entityHit instanceof EntityLivingBase) {
                EntityLivingBase e = (EntityLivingBase) r.entityHit;
                return new View(e instanceof IMob ? "☠" : "♥", e instanceof IMob ? 0xFF5A5A : 0xFF7FAE, e.getDisplayName().getUnformattedText(),
                        Math.round(e.getHealth()) + " / " + Math.round(e.getMaxHealth()) + " ❤ · " + String.format("%.0f", mc.player.getDistance(e)) + "m").health(e.getHealth() / Math.max(1F, e.getMaxHealth()));
            }
            if (r.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos bp = r.getBlockPos();
                net.minecraft.block.state.IBlockState st = mc.world.getBlockState(bp);
                ItemStack pick = st.getBlock().getPickBlock(st, r, mc.world, bp, mc.player);
                String name = pick.isEmpty() ? st.getBlock().getLocalizedName() : pick.getDisplayName();
                String mod = st.getBlock().getRegistryName() == null ? "" : st.getBlock().getRegistryName().getResourceDomain();
                String tool = st.getBlock().getHarvestTool(st);
                return new View("◆", 0xFF7FAE, name, mod + (tool == null ? "" : " · " + tool));
            }
            return null;
        }));
        ALL.add(new Widget("height", "Height", 0xFF8CE0A0, true, mc -> {
            int y = MathHelper.floor(mc.player.posY), sea = mc.world.getSeaLevel();
            return new View("▲", y >= sea ? 0x8CE0A0 : 0x7C9CFF, "Y " + y, (y >= sea ? "+" : "") + (y - sea) + " from sea").bar(y / 256F).colors(0xFF7C9CFF, 0xFF8CE0A0);
        }));
        ALL.add(new Widget("moon", "Moon", 0xFFE6E6FF, true, mc -> {
            int ph = mc.world.getMoonPhase() & 7;
            long t = mc.world.getWorldTime() % 24000L;
            return new View(MOON_ICON[ph], 0xE6E6FF, MOON[ph], mc.world.isDaytime() ? "daytime" : "night: mobs out").bar(t / 24000F).colors(0xFFFFE070, 0xFF24408E);
        }));
        ALL.add(new Widget("xp", "XP", 0xFFA8FF60, true, mc -> {
            EntityPlayerSP p = mc.player; int need = p.xpBarCap(), have = (int) (p.experience * need);
            return new View("✦", 0xA8FF60, "Level " + p.experienceLevel, (need - have) + " to next").bar(p.experience).colors(0xFFA8FF60, 0xFF4CB020);
        }));
        ALL.add(new Widget("food", "Food", 0xFFFFB347, true, mc -> {
            int food = mc.player.getFoodStats().getFoodLevel();
            float sat = mc.player.getFoodStats().getSaturationLevel();
            return new View("✿", 0xFFB347, food + " / 20", String.format("saturation %.1f", sat)).health(food / 20F);
        }));
        ALL.add(new Widget("hp", "Health", 0xFFFF5070, false, mc -> {
            float hp = mc.player.getHealth(), max = mc.player.getMaxHealth(), abs = mc.player.getAbsorptionAmount();
            return new View("❤", 0xFF5070, Math.round(hp) + " / " + Math.round(max), abs > 0 ? "+" + Math.round(abs) + " absorption" : "").health(hp / Math.max(1F, max));
        }));
        ALL.add(new Widget("mount", "Ride", 0xFFC9A27E, false, mc -> {
            Entity m = mc.player.getRidingEntity();
            if (!(m instanceof EntityLivingBase)) return new View("♞", 0x707070, "§7on foot", "");
            EntityLivingBase l = (EntityLivingBase) m;
            String sub = Math.round(l.getHealth()) + " / " + Math.round(l.getMaxHealth()) + " ❤";
            if (m instanceof AbstractHorse) sub += String.format(" · %.1f b/s", ((AbstractHorse) m).getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue() * 43.17);
            return new View("♞", 0xC9A27E, m.getDisplayName().getUnformattedText(), sub).health(l.getHealth() / Math.max(1F, l.getMaxHealth()));
        }));
        ALL.add(new Widget("mobs", "Nearby", 0xFFE02040, true, mc -> {
            int hostile = 0, all = 0;
            for (Entity e : mc.world.loadedEntityList) {
                if (e == mc.player || !(e instanceof EntityLivingBase) || e.getDistanceSq(mc.player) > 32 * 32) continue;
                all++; if (e instanceof IMob) hostile++;
            }
            return new View(hostile > 0 ? "☠" : "☺", hostile > 0 ? 0xFF5A5A : 0x8CE06A, hostile + " hostile", all + " creatures close").bar(Math.min(1F, hostile / 10F)).colors(0xFFFFD23A, 0xFFE02040);
        }));
        ALL.add(new Widget("spawn", "Home", 0xFF7CC8FF, true, mc -> {
            BlockPos s = mc.player.getBedLocation(mc.player.dimension);
            boolean bed = s != null;
            if (s == null) s = mc.world.getSpawnPoint();
            double d = Math.sqrt(mc.player.getDistanceSq(s));
            double ang = Math.toDegrees(Math.atan2(-(s.getX() + 0.5 - mc.player.posX), s.getZ() + 0.5 - mc.player.posZ));
            return new View("⌂", 0x7CC8FF, Math.round(d) + "m " + dir((float) ang), bed ? "to your bed" : "to world spawn");
        }));
        ALL.add(new Widget("ammo", "Arrows", 0xFFE0C080, false, mc -> {
            int n = 0;
            for (ItemStack s : mc.player.inventory.mainInventory) if (s.getItem() == Items.ARROW || s.getItem() == Items.TIPPED_ARROW || s.getItem() == Items.SPECTRAL_ARROW) n += s.getCount();
            for (ItemStack s : mc.player.inventory.offHandInventory) if (s.getItem() == Items.ARROW || s.getItem() == Items.TIPPED_ARROW || s.getItem() == Items.SPECTRAL_ARROW) n += s.getCount();
            return new View("➶", n == 0 ? 0x707070 : 0xE0C080, n + " arrows", n == 0 ? "out of arrows!" : "").bar(Math.min(1F, n / 64F)).colors(0xFFE0C080, 0xFFB08040);
        }));
        ALL.add(new Widget("clock", "Real time", 0xFFD6C8FF, false, mc -> new View("⌚", 0xD6C8FF,
                new java.text.SimpleDateFormat(DPConfig.hudClock24 ? "HH:mm" : "h:mm a").format(new java.util.Date()),
                new java.text.SimpleDateFormat("EEE MMM d").format(new java.util.Date()))));
        ALL.add(new Widget("difficulty", "Difficulty", 0xFFFF6060, false, mc -> {
            String d = mc.world.getDifficulty().name().toLowerCase();
            if (mc.getIntegratedServer() == null) return new View("⚠", 0xFF6060, d, "");
            DifficultyInstance di = mc.getIntegratedServer().getWorld(mc.player.dimension).getDifficultyForLocation(new BlockPos(mc.player));
            return new View("⚠", 0xFF6060, d, String.format("local %.2f", di.getAdditionalDifficulty())).bar(di.getAdditionalDifficulty() / 6.75F).colors(0xFFFFD23A, 0xFFE02040);
        }));
        ALL.add(new Widget("slime", "Slime chunk", 0xFF7BD86A, false, mc -> {
            if (mc.getIntegratedServer() == null || mc.player.dimension != 0) return null;
            long seed = mc.getIntegratedServer().getWorld(0).getSeed();
            int cx = MathHelper.floor(mc.player.posX) >> 4, cz = MathHelper.floor(mc.player.posZ) >> 4;
            boolean slime = new java.util.Random(seed + (long) (cx * cx * 4987142) + (long) (cx * 5947611) + (long) (cz * cz) * 4392871L + (long) (cz * 389711) ^ 987234911L).nextInt(10) == 0;
            return new View("●", slime ? 0x7BD86A : 0x707070, slime ? "Slimes here" : "No slimes", "chunk " + cx + ", " + cz);
        }));
        ALL.add(new Widget("weatherTimer", "Weather", 0xFF9FD3FF, false, mc -> {
            if (mc.getIntegratedServer() == null) return null;
            net.minecraft.world.storage.WorldInfo wi = mc.getIntegratedServer().getWorld(0).getWorldInfo();
            String now = wi.isThundering() ? "Storm" : wi.isRaining() ? "Rain" : "Clear";
            return new View(wi.isThundering() ? "⚡" : wi.isRaining() ? "☂" : "☀", wi.isRaining() ? 0x9FD3FF : 0xFFE070, now, "changes in " + (wi.getRainTime() / 1200) + " min");
        }));
        ALL.add(new Widget("played", "World age", 0xFFB59CFF, false, mc -> new View("⌛", 0xB59CFF, "Day " + (mc.world.getWorldTime() / 24000L + 1), (mc.world.getTotalWorldTime() / 72000L) + " hours played")));
        ALL.add(new Widget("tps", "Server", 0xFF5CE0C8, false, mc -> {
            if (mc.getIntegratedServer() == null) return null;
            double ms = net.minecraft.util.math.MathHelper.average(mc.getIntegratedServer().tickTimeArray) * 1.0E-6D;
            double tps = Math.min(20.0, 1000.0 / Math.max(1.0, ms));
            return new View("⚙", 0x5CE0C8, String.format("%.1f TPS", tps), String.format("%.1f ms a tick", ms)).health((float) (tps / 20.0));
        }));
        ALL.add(new Widget("memory", "Memory", 0xFFCCCCCC, false, mc -> {
            Runtime r = Runtime.getRuntime(); long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
            float k = used / (float) Math.max(1, max);
            return new View("▤", 0xCCCCCC, Math.round(k * 100) + "% RAM", used + " / " + max + " MB").health(1 - k);
        }));
        ALL.add(new Widget("nether", "Portal coords", 0xFFFF5A3C, false, mc -> {
            double x = mc.player.posX, z = mc.player.posZ; boolean nether = mc.player.dimension == -1;
            return nether ? new View("◈", 0x8CE0A0, Math.round(x * 8) + ", " + Math.round(z * 8), "overworld")
                          : new View("◈", 0xFF5A3C, Math.round(x / 8) + ", " + Math.round(z / 8), "in the nether");
        }));
        ALL.add(new Widget("light", "Light", 0xFFFFE070, false, mc -> {
            BlockPos bp = new BlockPos(mc.player);
            int block = mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, bp), sky = mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, bp);
            return new View("☼", block <= 7 ? 0xFF5A5A : 0xFFE070, "Light " + block, block <= 7 ? "mobs can spawn" : "safe · sky " + sky).bar(block / 15F).colors(0xFF806020, 0xFFFFE070);
        }));
        ALL.add(new Widget("daytimer", "Day / night", 0xFFFFC14D, false, mc -> {
            long t = mc.world.getWorldTime() % 24000L; boolean day = t < 12542;
            long left = day ? 12542 - t : 24000 - t;
            return new View(day ? "☀" : "☾", day ? 0xFFC14D : 0x9FB0FF, (left / 1200) + "m " + (left % 1200) / 20 + "s", day ? "until night" : "until morning")
                    .bar(day ? t / 12542F : (t - 12542) / 11458F).colors(day ? 0xFFFFE070 : 0xFF5060C0, day ? 0xFFFF9A3C : 0xFF24408E);
        }));
        ALL.add(new Widget("players", "Players", 0xFF8CE06A, false, mc -> {
            if (mc.getConnection() == null) return null;
            int n = mc.getConnection().getPlayerInfoMap().size();
            net.minecraft.client.network.NetworkPlayerInfo me = mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
            int ping = me == null ? 0 : me.getResponseTime();
            return new View("☺", 0x8CE06A, n + " online", "ping " + ping + " ms").health(1 - Math.min(1F, ping / 400F));
        }));
        ALL.add(new Widget("chunk", "Chunk", 0xFFB0B8C8, false, mc -> {
            int x = MathHelper.floor(mc.player.posX), z = MathHelper.floor(mc.player.posZ);
            return new View("▦", 0xB0B8C8, (x >> 4) + ", " + (z >> 4), "block " + (x & 15) + ", " + (z & 15) + " inside");
        }));
        ALL.add(new Widget("climate", "Climate", 0xFF9FD3FF, false, mc -> {
            BlockPos bp = new BlockPos(mc.player);
            net.minecraft.world.biome.Biome b = mc.world.getBiome(bp);
            float temp = b.getTemperature(bp);
            String fall = !b.canRain() && !b.getEnableSnow() ? "dry: never rains" : temp < 0.15F ? "snows here" : "rains here";
            return new View(temp < 0.15F ? "❄" : temp > 1.0F ? "☀" : "☁", temp < 0.15F ? 0xCFEFFF : temp > 1.0F ? 0xFFB347 : 0x9FD3FF, b.getBiomeName(), fall)
                    .bar(Math.min(1F, Math.max(0F, (temp + 0.5F) / 2.5F))).colors(0xFF7CC8FF, 0xFFFF6A4A);
        }));
        ALL.add(new Widget("fps", "FPS", 0xFF5CE0C8, false, mc -> {
            int fps = Minecraft.getDebugFPS();
            return new View("▶", 0x5CE0C8, fps + " fps", "").health(Math.min(1F, fps / 120F));
        }));
        ALL.add(new Widget("lookpos", "Block pos", 0xFFFF7FAE, false, mc -> {
            RayTraceResult r = mc.objectMouseOver;
            if (r == null || r.typeOfHit != RayTraceResult.Type.BLOCK) return null;
            BlockPos p = r.getBlockPos();
            return new View("✚", 0xFF7FAE, p.getX() + " " + p.getY() + " " + p.getZ(), "face " + r.sideHit.getName());
        }));
        ALL.add(new Widget("air", "Air", 0xFF7CC8FF, false, mc -> {
            int air = mc.player.getAir();
            return air >= 300 ? new View("○", 0x707070, "§7full air", "") : new View("○", 0x7CC8FF, Math.max(0, air / 20) + "s left", "under water").health(air / 300F);
        }));
    }
}
