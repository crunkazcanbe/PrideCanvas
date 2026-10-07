package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;

import static com.dogpound.canvas.DPDraw.*;

/**
 * The HUD themes (her pick 2026-10-04: "I like every one of them… add an option to change the theme"). Each theme draws
 * the middle card — health, food, water (Tough As Nails), armour, temperature — in a 520 x 130 box that is scaled into
 * the HUD. Theme "minecraft" = Minecraft's own icons, and the real Scaling Health / Feast / AppleSkin drawings.
 */
final class DPHudThemes {
    private DPHudThemes() {}

    /** what a theme shows; filled from the player each frame (or made up for previews) */
    static final class V {
        float hp, max, abs, food, maxFood = 20, sat, water = -1, armor, temp = -1, xp, mana = -1, maxMana = 1; int lvl; boolean hurt;
    }

    interface Theme { void draw(V v, long t); }

    static final String[][] LIST = {
            {"minecraft", "Minecraft", "Minecraft's own hearts and food, plus Scaling Health, Feast, AppleSkin and Tough As Nails exactly as they draw them"},
            {"pride", "Pride", "matches every Pride menu: dark panel, rainbow band, rainbow hearts"},
            {"teddy", "Teddy Hug", "a teddy bear in the middle hugging your level"},
            {"candy", "Candy Hearts", "shiny candy hearts, gumdrops and sparkles"},
            {"picnic", "Teddy Picnic", "gingham picnic bars with a teddy"},
            {"cloud", "Cloud Dream", "clouds, stars and a pastel rainbow"},
            {"ribbon", "Ribbon & Bows", "satin ribbon bars with bows"},
            {"kawaii", "Kawaii Orbs", "little faces that get sad when you're low"},
            {"paws", "Bear Paws", "paw prints and honey pots"},
            {"sweets", "Sweet Shop", "lollipops, cupcakes and sprinkles"},
            {"classic", "Classic Icons", "Minecraft-style pixel hearts, drumsticks and drops with numbers"},
            {"bars", "Pride Bars", "smooth gradient bars with the numbers inside"},
            {"potions", "Potion Orbs", "glass orbs that slosh like liquid"},
            {"pills", "Segmented Pills", "battery-style segments, a colour per extra 20 health"},
            {"rings", "Ring Gauges", "rings round each icon that empty clockwise"},
            {"numbers", "Big Numbers", "big readable numbers with thin bars"},
            {"crystals", "Heart Crystals", "gem rows that dim as you lose them"},
            {"wings", "Mirror Wings", "health and food growing out of your level like wings"},
    };

    static final int W = 520, H = 130;
    static final int[] TIER = {0xFFE8304E, 0xFFFF8C00, 0xFFFFD23A, 0xFF5AD15A, 0xFF3F8FE8, 0xFF9A5AE8, 0xFFF5A9B8, 0xFFFFFFFF};

    static V fromPlayer(Minecraft mc) {
        V v = new V();
        EntityPlayerSP p = mc.player;
        v.hp = p.getHealth(); v.max = Math.max(1, p.getMaxHealth()); v.abs = p.getAbsorptionAmount();
        v.food = p.getFoodStats().getFoodLevel(); v.sat = p.getFoodStats().getSaturationLevel();
        v.armor = p.getTotalArmorValue(); v.lvl = p.experienceLevel; v.xp = p.experience; v.hurt = p.hurtTime > 0;
        tan(p, v);
        feastAndMana(p, v);
        return v;
    }

    static V sample() {
        V v = new V();
        v.hp = 26; v.max = 40; v.abs = 4; v.food = 14; v.sat = 5; v.water = 12; v.armor = 8; v.temp = 12; v.lvl = 27; v.xp = .6F; v.mana = 30; v.maxMana = 50;
        return v;
    }

    private static Object thirstCap, tempCap;
    private static boolean tanTried;

    /** Tough As Nails thirst + temperature, read by reflection so the HUD never needs TAN to run */
    private static void tan(EntityPlayerSP p, V v) {
        try {
            if (!tanTried) {
                tanTried = true;
                Class<?> c = Class.forName("toughasnails.api.TANCapabilities");
                thirstCap = c.getField("THIRST").get(null);
                tempCap = c.getField("TEMPERATURE").get(null);
            }
            if (thirstCap != null) {
                Object th = p.getCapability((net.minecraftforge.common.capabilities.Capability<?>) thirstCap, null);
                if (th != null) v.water = ((Number) th.getClass().getMethod("getThirst").invoke(th)).floatValue();
            }
            if (tempCap != null) {
                Object tm = p.getCapability((net.minecraftforge.common.capabilities.Capability<?>) tempCap, null);
                if (tm != null) {
                    Object temp = tm.getClass().getMethod("getTemperature").invoke(tm);
                    v.temp = ((Number) temp.getClass().getMethod("getRawValue").invoke(temp)).floatValue() * 20F / 25F;
                }
            }
        } catch (Throwable ignored) { thirstCap = null; tempCap = null; }
    }

    private static boolean feastTried, manaTried;
    private static Object feastAccessor, soulCap;

    /** Scaling Feast (max hunger above 20) and Player Mana (mana / max mana), by reflection */
    private static void feastAndMana(EntityPlayerSP p, V v) {
        try {
            if (!feastTried) { feastTried = true; feastAccessor = Class.forName("yeelp.scalingfeast.api.ScalingFeastAPI").getField("accessor").get(null); }
            if (feastAccessor != null) {
                Object st = feastAccessor.getClass().getMethod("getSFFoodStats", net.minecraft.entity.player.EntityPlayer.class).invoke(feastAccessor, p);
                Object attr = st.getClass().getMethod("getMaxHungerAttribute").invoke(st);
                v.maxFood = Math.max(1F, (float) ((net.minecraft.entity.ai.attributes.IAttributeInstance) attr).getAttributeValue());
            }
        } catch (Throwable ignored) { feastAccessor = null; }
        try {
            if (!manaTried) { manaTried = true; soulCap = Class.forName("zettasword.player_mana.cap.SoulProvider").getField("SOUL_CAP").get(null); }
            if (soulCap != null) {
                Object soul = p.getCapability((net.minecraftforge.common.capabilities.Capability<?>) soulCap, null);
                if (soul != null) {
                    double mp = (Double) soul.getClass().getMethod("getMP").invoke(soul), max = (Double) soul.getClass().getMethod("getMaxMP").invoke(soul);
                    if (max > 0) { v.mana = (float) mp; v.maxMana = (float) max; }
                }
            }
        } catch (Throwable ignored) { soulCap = null; }
    }

    /** a slim XP pill across the top of the card with the level in a badge, coloured for the theme */
    static void xpBar(String id, V v) {
        Style st = style(id);
        pill(70, 6, W - 140, 8, v.xp, st.xpFrom, st.xpTo, 0x66000000);
        String lv = String.valueOf(v.lvl);
        float bw = Math.max(26, Minecraft.getMinecraft().fontRenderer.getStringWidth(lv) * 14 / 9F + 12);
        fill(rrect(W / 2F - bw / 2, 1, bw, 18, 9), st.xpTo); stroke(rrect(W / 2F - bw / 2, 1, bw, 18, 9), 0xAAFFFFFF, 1.5F);
        txt(lv, W / 2F, 10, 14, 0xFFFFFF, 0, 0x000000);
    }

    // ------------------------------------------------------------------ the whole strip follows the theme
    static final class Style {
        int panelTop = 0xF2140E22, panelBottom = 0xF2080510, cardTop = 0xE62A2040, cardBottom = 0xE6140E22, xpFrom = 0xFFA8FF60, xpTo = 0xFF4CB020;
        boolean rainbow = true; String deco = "";
        Style(int pt, int pb, int ct, int cb, int xf, int xt, boolean rb, String deco) { panelTop = pt; panelBottom = pb; cardTop = ct; cardBottom = cb; xpFrom = xf; xpTo = xt; rainbow = rb; this.deco = deco; }
        Style() {}
    }

    static Style style(String id) {
        switch (id == null ? "" : id) {
            case "pride": return new Style(0xF2140E22, 0xF2080510, 0xE62A2040, 0xE6140E22, 0xFFF5A9B8, 0xFF5BCEFA, true, "pride");
            case "sweets": return new Style(0xF2FFE4F0, 0xF2FFC8DE, 0xEEE86A9E, 0xEEB8457A, 0xFFFFB0D0, 0xFFE0457E, false, "sprinkles");
            case "teddy": return new Style(0xF2FFD6E6, 0xF2F7A8C8, 0xEEC98A5A, 0xEE8A5A3A, 0xFFFF9EC4, 0xFFE0457E, false, "hearts");
            case "candy": return new Style(0xF2E8D4FF, 0xF2C7A8F5, 0xEE9A6AD0, 0xEE6A3AA8, 0xFFFFB0E0, 0xFFC04AB0, false, "sparkles");
            case "picnic": return new Style(0xF2FFF6E0, 0xF2FFE0B8, 0xEEE8406A, 0xEEB02A50, 0xFFFF9090, 0xFFC02A3A, false, "gingham");
            case "cloud": return new Style(0xF2B8E4FF, 0xF2D8C8FF, 0xEE6A8AE0, 0xEE4A5AB8, 0xFFFFF09A, 0xFFFFB0D0, false, "clouds");
            case "ribbon": return new Style(0xF2FFE8F2, 0xF2FFD0E4, 0xEEFF7FAE, 0xEEE0457E, 0xFFFFC0D8, 0xFFE0457E, false, "scallop");
            case "kawaii": return new Style(0xF22E2040, 0xF21E1430, 0xEE4A3358, 0xEE2E2040, 0xFFFF9EC4, 0xFFB57EDC, false, "stars");
            case "paws": return new Style(0xF2F6E2C8, 0xF2E8C8A0, 0xEE8A5A2A, 0xEE5A3A1A, 0xFFFFD060, 0xFFE09A20, false, "paws");
            default: return new Style();
        }
    }

    /** little decorations sprinkled over the strip (GUI coordinates) */
    static void decorate(Style st, int x0, int y0, int w, int h, long t) {
        switch (st.deco) {
            case "sprinkles": { int[] c = {0xCCFF7FAE, 0xCC7CC8FF, 0xCCFFD23A, 0xCC8CE0A0}; for (int i = 0; i < w / 9; i++) { float x = x0 + (i * 97) % w, y = y0 + 3 + (i * 53) % (h - 6); float a = i, dx = (float) Math.cos(a) * 2, dy = (float) Math.sin(a) * 2; fill(new float[]{x - dx, y - dy, x - dx + .8F, y - dy + .8F, x + dx + .8F, y + dy + .8F, x + dx, y + dy}, c[i % 4]); } break; }
            case "hearts": for (int i = 0; i < w / 40; i++) fill(heart(x0 + (i * 131) % w, y0 + 4 + (i * 37) % (h - 10), 5), 0x55FF7FAE); break;
            case "sparkles": for (int i = 0; i < w / 50; i++) fill(sparkle(x0 + (i * 151) % w, y0 + 5 + (i * 41) % (h - 10), 2.5F + (float) Math.sin(t / 400.0 + i) * 1F), 0xAAFFFFFF); break;
            case "gingham": for (int x = x0; x < x0 + w; x += 8) for (int r = 0; r < 2; r++) if (((x - x0) / 8 + r) % 2 == 0) rect(x, y0 + h - 8 + r * 4, 8, 4, 0x44FF7090); break;
            case "clouds": for (int i = 0; i < w / 120; i++) { float cx = x0 + (i * 211 + t / 80F) % w, cy = y0 + 8 + (i * 29) % (h - 16); for (int k = 0; k < 3; k++) fill(circle(cx + k * 6, cy, 5), 0x66FFFFFF); } break;
            case "scallop": for (int x = x0 + 4; x < x0 + w; x += 8) fill(clip(circle(x, y0 + 2, 4), false, y0 + 2, false), 0xCCFFFFFF); break;
            case "stars": for (int i = 0; i < w / 45; i++) fill(star(x0 + (i * 113) % w, y0 + 4 + (i * 31) % (h - 8), 2 + (i % 3) * .6F), 0x88FFE8A0); break;
            case "paws": for (int i = 0; i < w / 60; i++) { float px = x0 + (i * 173) % w, py = y0 + 6 + (i * 41) % (h - 12); fill(ellipse(px, py + 1, 2.5F, 2, 0), 0x448A5A2A); for (float[] o : new float[][]{{-2.5F, -1.5F}, {-1, -3}, {1, -3}, {2.5F, -1.5F}}) fill(circle(px + o[0], py + o[1], 1), 0x448A5A2A); } break;
            default: break;
        }
    }

    static float foodN(V v) { return v.food * 20F / v.maxFood; }
    static boolean hasMana(V v) { return v.mana >= 0; }

    /** draw theme `id` into the box (x, y, w, h); false = "minecraft" (the HUD does its own thing) */
    static boolean draw(String id, V v, float x, float y, float w, float h, long t) {
        Theme th = theme(id);
        if (th == null) return false;
        float k = Math.min(w / W, h / H);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (w - W * k) / 2, y + (h - H * k) / 2, 0);
        GlStateManager.scale(k, k, 1);
        try {
            th.draw(v, t);
            if (DPHudSettings.get().xpBar && !id.equals("classic") && !id.equals("bars") && !id.equals("wings")) xpBar(id, v);   // every theme shows XP (her 2026-10-04 "where's the XP bar")
        } catch (Throwable e) { /* a theme must never break the HUD */ }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        return true;
    }

    // ------------------------------------------------------------------ shared pieces
    static float clamp(float f) { return Math.max(0, Math.min(1, f)); }
    static float hpF(V v, int i) { return clamp((v.hp / v.max * 20 - i * 2) / 2); }
    static float statF(float val, int i) { return clamp((val - i * 2) / 2); }
    static int tierIdx(V v) { return Math.max(0, Math.min(TIER.length - 1, (int) Math.ceil(v.hp / 20) - 1)); }
    static float inTier(V v, int i) { return clamp((v.hp - tierIdx(v) * 20 - i * 2) / 2); }
    static String n(float f) { return String.valueOf((int) Math.ceil(f)); }
    static boolean hasWater(V v) { return v.water >= 0; }
    static String tempWord(float t) { return t < 0 ? "" : t < 5 ? "Icy" : t < 9 ? "Cold" : t < 13 ? "Mild" : t < 17 ? "Warm" : "Hot"; }

    static void base(int top, int bottom) { fillGrad(rrect(0, 0, W, H, 22), top, bottom, 0, H); }
    static void scallop(int col) { for (int x = 8; x < W; x += 16) { fill(clip(circle(x, 0, 8), false, 0, false), col); fill(clip(circle(x, H, 8), false, H, true), col); } }

    static void heartIcon(float x, float y, float s, int fill, float frac, int empty, int outline) {
        float[] h = heart(x, y, s);
        DPDraw.fill(h, empty);
        if (frac > 0) DPDraw.fill(clip(h, true, x - s * .62F + s * 1.24F * frac, true), fill);
        stroke(h, outline, 1.5F);
        DPDraw.fill(ellipse(x - s * .3F, y + s * .12F, s * .12F, s * .08F, -.6), 0xAAFFFFFF);
    }

    static void bear(float x, float y, float s, boolean sleepy) {
        int body = 0xFFC98A5A, light = 0xFFF0C9A0, line = 0xFF5A3020;
        for (float ex : new float[]{-.62F, .62F}) { fill(circle(x + ex * s, y - .6F * s, .32F * s), body); stroke(circle(x + ex * s, y - .6F * s, .32F * s), line, 1.5F); fill(circle(x + ex * s, y - .6F * s, .17F * s), 0xFFF7A8B8); }
        fill(circle(x, y, s), body); stroke(circle(x, y, s), line, 1.5F);
        fill(ellipse(x, y + .3F * s, .42F * s, .32F * s, 0), light);
        if (sleepy) { rect(x - .5F * s, y - .12F * s, .24F * s, .05F * s, 0xFF3A1A10); rect(x + .26F * s, y - .12F * s, .24F * s, .05F * s, 0xFF3A1A10); }
        else { fill(circle(x - .38F * s, y - .12F * s, .11F * s), 0xFF3A1A10); fill(circle(x + .38F * s, y - .12F * s, .11F * s), 0xFF3A1A10);
            fill(circle(x - .35F * s, y - .16F * s, .04F * s), 0xFFFFFFFF); fill(circle(x + .41F * s, y - .16F * s, .04F * s), 0xFFFFFFFF); }
        fill(ellipse(x, y + .18F * s, .13F * s, .09F * s, 0), 0xFF3A1A10);
        fill(ellipse(x - .62F * s, y + .15F * s, .16F * s, .1F * s, 0), 0x66FF8FB0); fill(ellipse(x + .62F * s, y + .15F * s, .16F * s, .1F * s, 0), 0x66FF8FB0);
    }

    static void cookie(float x, float y, float s, float frac) {
        fill(circle(x, y, s), 0xFF5A3A40);
        if (frac > 0) {
            int steps = Math.max(3, (int) (24 * frac));
            float[] p = new float[(steps + 2) * 2];
            p[0] = x; p[1] = y;
            for (int i = 0; i <= steps; i++) { double a = -Math.PI / 2 + Math.PI * 2 * frac * i / steps; p[2 + 2 * i] = (float) (x + Math.cos(a) * s); p[3 + 2 * i] = (float) (y + Math.sin(a) * s); }
            fill(p, 0xFFE8B06A);
            for (float[] c : new float[][]{{-.3F, -.3F}, {.35F, -.1F}, {-.1F, .35F}, {.3F, .4F}, {-.45F, .15F}}) fill(circle(x + c[0] * s, y + c[1] * s, s * .12F), 0xFF6A3A1A);
        }
        stroke(circle(x, y, s), 0xFF7A4A2A, 1.5F);
    }

    static void dropIcon(float x, float y, float s, float frac, boolean face) {
        float[] d = drop(x, y, s);
        fill(d, 0xFF3A3A60);
        if (frac > 0) fill(clip(d, false, y + s - 2 * s * frac, false), 0xFF7CC8FF);
        stroke(d, 0xFF2A5A9A, 1.5F);
        if (face && frac > .3F) { fill(circle(x - s * .25F, y + s * .35F, s * .08F), 0xFF1A2A4A); fill(circle(x + s * .25F, y + s * .35F, s * .08F), 0xFF1A2A4A); }
    }

    static void pill(float x, float y, float w, float h, float frac, int c1, int c2, int back) {
        fill(rrect(x, y, w, h, h / 2), back);
        if (frac > 0) {
            float[] r = clip(rrect(x, y, w, h, h / 2), true, x + w * clamp(frac), true);
            fillGrad(r, c1, c2, y, y + h);
            if (w * frac > 10) fill(rrect(x + 4, y + 3, w * clamp(frac) - 8, h * .28F, h), 0x55FFFFFF);
        }
        stroke(rrect(x, y, w, h, h / 2), 0x55FFFFFF, 1.5F);
    }

    static void bow(float x, float y, float s, int col) {
        fill(new float[]{x, y, x - s * 1.3F, y - s, x - s * 1.25F, y + s * .9F}, col);
        fill(new float[]{x, y, x + s * 1.3F, y - s, x + s * 1.25F, y + s * .9F}, col);
        fill(circle(x, y, s * .32F), col); stroke(circle(x, y, s * .32F), 0xFFA83A6A, 1.5F);
    }

    static void txt(String s, float x, float y, float size, int col, int align, int outline) { DPDraw.text(s, x, y, size, col, align, outline); }

    // ------------------------------------------------------------------ the themes
    static Theme theme(String id) {
        if (id == null) return null;
        switch (id) {
            case "pride": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 10), 0xFF2A2040, 0xFF140E22, 0, H);
                for (int i = 0; i < 8; i++) rect(i * W / 8F, 0, W / 8F + 1, 4, PrideFrame.RAINBOW[i]);
                for (int i = 0; i < 10; i++) heartIcon(30 + i * 23, 40, 13, PrideFrame.RAINBOW[i % 8], hpF(v, i), 0xFF2A2040, 0xFF0A0610);
                txt("\u2665 " + n(v.hp) + " / " + n(v.max) + (v.abs > 0 ? "  +" + n(v.abs) : ""), 26, 68, 14, 0xF5A9B8, -1, 0);
                pill(W - 250, 30, 236, 16, v.food / v.maxFood, 0xFFFFC890, 0xFFF08A3A, 0xFF1A1226); txt("food " + n(v.food) + " / " + n(v.maxFood), W - 240, 38, 12, 0xFFFFFF, -1, 0);
                if (hasWater(v)) { pill(W - 250, 52, 236, 16, v.water / 20, 0xFF5BCEFA, 0xFF2A7AC8, 0xFF1A1226); txt("water " + n(v.water), W - 240, 60, 12, 0xFFFFFF, -1, 0); }
                pill(14, 84, (W - 42) / 2F, 12, v.armor / 20, 0xFFFFFFFF, 0xFFB8B0D0, 0xFF1A1226); txt("armour " + n(v.armor), 20, 106, 12, 0xC8C0D8, -1, 0);
                if (hasMana(v)) { pill(28 + (W - 42) / 2F, 84, (W - 42) / 2F, 12, v.mana / v.maxMana, 0xFFF5A9B8, 0xFF732982, 0xFF1A1226); txt("mana " + n(v.mana), W - 20, 106, 12, 0xC8C0D8, 1, 0); }
                else if (v.temp >= 0) txt(tempWord(v.temp), W - 20, 106, 12, 0xC8C0D8, 1, 0);
            };
            case "teddy": return (v, t) -> {
                base(0xFFFFD6E6, 0xFFF7A8C8); scallop(0xFFFFF0F6);
                bear(W / 2F, 72, 34, v.hp / v.max < .3F);
                heartIcon(W / 2F, 92, 26, 0xFFFF4F86, 1, 0xFF5A3A55, 0xFF7A2850);
                txt(String.valueOf(v.lvl), W / 2F, 108, 16, 0xFFFFFF, 0, 0x5A2A48);
                for (int i = 0; i < 10; i++) heartIcon(24 + (i % 5) * 34, 36 + (i / 5) * 34, 15, TIER[tierIdx(v)], inTier(v, i), 0xFF5A3A55, 0xFF7A2850);
                for (int i = 0; i < 5; i++) cookie(W - 30 - i * 34, 46, 13, clamp((foodN(v) - i * 4) / 4));
                if (hasWater(v)) for (int i = 0; i < 5; i++) dropIcon(W - 30 - i * 34, 86, 13, clamp((v.water - i * 4) / 4), true);
                txt(n(v.hp) + "/" + n(v.max) + (v.abs > 0 ? " +" + n(v.abs) : ""), 96, 118, 15, 0xFFFFFF, 0, 0x5A2A48);
                txt(n(v.food) + (hasWater(v) ? " · " + n(v.water) : ""), W - 96, 118, 15, 0xFFFFFF, 0, 0x5A2A48);
            };
            case "candy": return (v, t) -> {
                base(0xFFE8D4FF, 0xFFC7A8F5); scallop(0xFFF6EEFF);
                for (int i = 0; i < 10; i++) heartIcon(30 + i * 24, 38, 13, i % 2 == 0 ? 0xFFFF6FA5 : 0xFFFF9EC4, hpF(v, i), 0xFF8A6AA8, 0xFF7A3A8A);
                for (int i = 0; i < 10; i++) { float f = statF(foodN(v), i); float x = W - 30 - i * 24; fill(circle(x, 88, 10), f > 0 ? (i % 2 == 0 ? 0xFFFFC46A : 0xFFFF8F6A) : 0xFF8A6AA8); stroke(circle(x, 88, 10), 0xFF7A3A8A, 1.5F); }
                if (hasWater(v)) for (int i = 0; i < 10; i++) dropIcon(30 + i * 24, 88, 10, statF(v.water, i), false);
                fill(sparkle(W / 2F + 40, 30, 9), 0xFFFFFFFF); fill(sparkle(W / 2F - 30, 112, 6), 0xFFFFFFFF); fill(sparkle(W - 60, 28, 7), 0xFFFFFFFF);
                txt("♥ " + n(v.hp), W - 60, 40, 18, 0xFF4F86, 0, 0xFFFFFF);
            };
            case "picnic": return (v, t) -> {
                base(0xFFFFF6E0, 0xFFFFE0B8);
                for (int x = 0; x < W; x += 20) for (int r = 0; r < 2; r++) if (((x / 20) + r) % 2 == 0) rect(x, H - 24 + r * 12, 20, 12, 0x55FF9CB0);
                bear(54, 66, 30, v.hp / v.max < .3F);
                pill(104, 26, W - 130, 22, v.hp / v.max, 0xFFFF7FA0, 0xFFE8406A, 0xFF4A3358); txt("♥ " + n(v.hp) + " / " + n(v.max), 120, 37, 15, 0xFFFFFF, -1, 0x5A2A48);
                float half = (W - 140) / 2F;
                pill(104, 58, hasWater(v) ? half : W - 130, 18, v.food / v.maxFood, 0xFFFF6060, 0xFFC02A3A, 0xFF4A3358); txt("food " + n(v.food), 116, 67, 13, 0xFFFFFF, -1, 0x5A2A48);
                if (hasWater(v)) { pill(112 + half, 58, half, 18, v.water / 20, 0xFF8AD0FF, 0xFF3A8AE0, 0xFF4A3358); txt("water " + n(v.water), 124 + half, 67, 13, 0xFFFFFF, -1, 0x2A4A7A); }
                pill(104, 86, W - 130, 10, v.armor / 20, 0xFFE8E8F8, 0xFF9A9AB8, 0xFF4A3358);
            };
            case "cloud": return (v, t) -> {
                base(0xFFB8E4FF, 0xFFD8C8FF);
                int[] rb = {0xFFFF9AA8, 0xFFFFC890, 0xFFFFF09A, 0xFFA8F0B0, 0xFF9AD8FF, 0xFFC8A8FF};
                for (int i = 0; i < rb.length; i++) arc(W / 2F, H + 20, 120 - i * 6, 5, Math.PI, Math.PI * 2, rb[i]);
                for (int side = 0; side < 2; side++) { float x0 = side == 0 ? 20 : W - 180; for (int i = 0; i <= 5; i++) { fill(circle(x0 + i * 32, 32, 20), 0xDDFFFFFF); fill(circle(x0 + i * 32, 52, 20), 0xDDFFFFFF); } }
                for (int i = 0; i < 10; i++) heartIcon(34 + (i % 5) * 30, 28 + (i / 5) * 28, 12, TIER[tierIdx(v)], inTier(v, i), 0xFFD8C8E8, 0xFFC05080);
                for (int i = 0; i < 10; i++) fill(star(W - 34 - (i % 5) * 30, 36 + (i / 5) * 28, 11), statF(foodN(v), i) > 0 ? 0xFFFFD23A : 0xFFD8C8E8);
                txt(n(v.hp) + "♥", W / 2F, 64, 22, 0xFF6F9A, 0, 0xFFFFFF);
                txt(n(v.food) + "★" + (hasWater(v) ? "  " + n(v.water) + " water" : ""), W / 2F, 96, 15, 0x6A7AD0, 0, 0xFFFFFF);
            };
            case "ribbon": return (v, t) -> {
                base(0xFFFFE8F2, 0xFFFFD0E4); scallop(0xFFFFFFFF);
                int rows = hasWater(v) ? 3 : 2; float gap = 110F / rows;
                Object[][] r = {{v.hp / v.max, 0xFFFF7FAE, 0xFFE0457E, "health " + n(v.hp) + " / " + n(v.max)}, {v.food / v.maxFood, 0xFFFFB46A, 0xFFE07A2A, "food " + n(v.food)}, {v.water / 20, 0xFF8AC8FF, 0xFF3A7AE0, "water " + n(v.water)}};
                for (int i = 0; i < rows; i++) {
                    float y = 14 + i * gap + (gap - 24) / 2;
                    pill(40, y, W - 80, 24, (Float) r[i][0], (Integer) r[i][1], (Integer) r[i][2], 0xFFF0C8D8);
                    bow(40, y + 12, 14, (Integer) r[i][1]);
                    txt((String) r[i][3], W / 2F, y + 12, 15, 0xFFFFFF, 0, 0xA83A6A);
                }
            };
            case "kawaii": return (v, t) -> {
                base(0xFF2E2040, 0xFF1E1430);
                java.util.List<Object[]> o = new java.util.ArrayList<Object[]>();
                o.add(new Object[]{v.hp / v.max, 0xFFFF6F9A, "health"}); o.add(new Object[]{v.food / v.maxFood, 0xFFFFB46A, "food"});
                if (hasWater(v)) o.add(new Object[]{v.water / 20, 0xFF7CC8FF, "water"});
                if (hasMana(v)) o.add(new Object[]{v.mana / v.maxMana, 0xFFB57EDC, "mana"});
                o.add(new Object[]{v.armor / 20, 0xFFD8D8F0, "armour"});
                float step = W / (float) o.size();
                for (int i = 0; i < o.size(); i++) {
                    float f = clamp((Float) o.get(i)[0]), x = step * (i + .5F), y = 56, r = 38;
                    fill(circle(x, y, r), 0xFF4A3358);
                    if (f > 0) fill(clip(circle(x, y, r), false, y + r - 2 * r * f, false), (Integer) o.get(i)[1]);
                    stroke(circle(x, y, r), 0x88FFFFFF, 2);
                    fill(circle(x - 12, y - 4, 4.5F), 0xFF3A1A2A); fill(circle(x + 12, y - 4, 4.5F), 0xFF3A1A2A);
                    if (f > .5F) arc(x, y + 4, 8, 3, .2, Math.PI - .2, 0xFF3A1A2A); else arc(x, y + 16, 8, 3, Math.PI + .3, Math.PI * 2 - .3, 0xFF3A1A2A);
                    fill(ellipse(x - 22, y + 6, 6, 4, 0), 0x88FF8FB0); fill(ellipse(x + 22, y + 6, 6, 4, 0), 0x88FF8FB0);
                    txt((String) o.get(i)[2], x, y + r + 14, 14, 0xFFFFFF, 0, 0x5A2A48);
                }
            };
            case "paws": return (v, t) -> {
                base(0xFFF6E2C8, 0xFFE8C8A0);
                for (int i = 0; i < 10; i++) {
                    float x = 26 + i * 25, y = 40 + (i % 2) * 8, s = 10; int col = hpF(v, i) > 0 ? 0xFFFF9CC0 : 0xFF8A6A75;
                    fill(ellipse(x, y + s * .25F, s * .5F, s * .42F, 0), col);
                    for (float[] c : new float[][]{{-.55F, -.2F}, {-.2F, -.55F}, {.2F, -.55F}, {.55F, -.2F}}) fill(circle(x + c[0] * s, y + c[1] * s, s * .2F), col);
                }
                for (int i = 0; i < 6; i++) {
                    float f = clamp(v.food / v.maxFood * 6 - i), x = W - 30 - i * 32, y = 90;
                    fill(rrect(x - 12, y - 12, 24, 26, 8), 0xFF8A5A2A);
                    if (f > 0) fill(clip(rrect(x - 12, y - 12, 24, 26, 8), false, y + 14 - 26 * f, false), 0xFFFFB820);
                    fill(rrect(x - 14, y - 15, 28, 6, 3), 0xFFC86A3A);
                }
                bear(W - 50, 40, 24, v.hp / v.max < .3F);
                txt("♥ " + n(v.hp) + "/" + n(v.max), 70, 92, 18, 0xFF6F9A, 0, 0x5A3020);
                if (hasWater(v)) txt("water " + n(v.water), 180, 92, 15, 0x5AB0FF, 0, 0x2A4A7A);
            };
            case "sweets": return (v, t) -> {
                base(0xFFFFE4F0, 0xFFFFD2E6);
                int[] sp = {0xFFFF7FAE, 0xFF7CC8FF, 0xFFFFD23A, 0xFF8CE0A0};
                for (int i = 0; i < 40; i++) { float x = (i * 97) % W, y = (i * 53) % H; double a = i; float c = (float) Math.cos(a) * 4, s = (float) Math.sin(a) * 4; fill(new float[]{x - c - s * .3F, y - s + c * .3F, x + c - s * .3F, y + s + c * .3F, x + c + s * .3F, y + s - c * .3F, x - c + s * .3F, y - s - c * .3F}, sp[i % 4]); }
                for (int i = 0; i < 10; i++) { float f = hpF(v, i), x = 26 + (i % 5) * 34, y = 32 + (i / 5) * 40;
                    rect(x - 1.5F, y + 8, 3, 14, 0xFFFFFFFF); fill(circle(x, y, 11), f > 0 ? 0xFFFF5A9A : 0xFFC8A8B8); arc(x, y, 6, 2, 0, 5, 0xFFFFFFFF); }
                for (int i = 0; i < 5; i++) { boolean f = foodN(v) > i * 4; float x = W - 30 - i * 36, y = 62;
                    fill(new float[]{x - 12, y, x + 12, y, x + 9, y + 18, x - 9, y + 18}, f ? 0xFFA8E0FF : 0xFFC8A8B8);
                    fill(clip(circle(x, y - 2, 13), false, y - 2, true), f ? 0xFFFF9EC4 : 0xFFD8C0CC); fill(circle(x, y - 16, 4), 0xFFFF3A6A); }
                txt(n(v.hp) + "/" + n(v.max) + " ♥   " + n(v.food) + " food" + (hasWater(v) ? "   " + n(v.water) + " water" : ""), W / 2F, 118, 15, 0xFFFFFF, 0, 0xC04A7A);
            };
            case "classic": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H); rect(6, 0, W - 12, 3, 0xFFFFFFFF);
                pill(14, 12, W - 28, 7, v.xp, 0xFFA8FF60, 0xFF4CB020, 0xFF0E1A0A); txt(String.valueOf(v.lvl), W / 2F, 16, 15, 0x80FF20, 0, 0x14240C);
                for (int i = 0; i < 10; i++) heartIcon(26 + i * 24, 42, 14, TIER[tierIdx(v)], inTier(v, i), 0xFF3A2030, 0xFF1A0D14);
                for (int i = 0; i < 10; i++) { float x = W - 26 - i * 24; fill(new float[]{x - 8, 44, x + 4, 34, x + 9, 40, x - 3, 50}, statF(foodN(v), i) > 0 ? 0xFFC8642A : 0xFF3A2A20); fill(circle(x - 7, 48, 3), 0xFFF2E6D0); }
                if (hasWater(v)) for (int i = 0; i < 10; i++) dropIcon(W - 26 - i * 24, 76, 9, statF(v.water, i), false);
                for (int i = 0; i < 10; i++) fill(rrect(26 + i * 24 - 8, 70, 16, 14, 3), statF(v.armor, i) > 0 ? 0xFFC9D1E0 : 0xFF2C3040);
                txt(n(v.hp) + "/" + n(v.max), 20, 108, 15, 0xFFB3C1, -1, 0);
                txt(n(v.food) + " food" + (hasWater(v) ? " · " + n(v.water) + " water" : ""), W - 20, 108, 15, 0xFFD49A, 1, 0);
            };
            case "bars": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H); rect(6, 0, W - 12, 3, 0xFFFFFFFF);
                pill(14, 12, W - 28, 7, v.xp, 0xFFA8FF60, 0xFF4CB020, 0xFF0E1A0A); txt(String.valueOf(v.lvl), W / 2F, 16, 15, 0x80FF20, 0, 0x14240C);
                float bw = (W - 42) / 2F;
                pill(14, 34, bw, 24, v.hp / v.max, 0xFFFF5070, 0xFFB01030, 0xFF1A1226); txt("♥ " + n(v.hp) + " / " + n(v.max) + (v.abs > 0 ? "  +" + n(v.abs) : ""), 24, 46, 15, 0xFFFFFF, -1, 0);
                pill(28 + bw, 34, bw, 24, v.food / v.maxFood, 0xFFFFC060, 0xFFC07010, 0xFF1A1226); txt("food " + n(v.food) + " / " + n(v.maxFood), 38 + bw, 46, 15, 0xFFFFFF, -1, 0);
                pill(14, 68, bw, 24, v.armor / 20, 0xFFDFE6F2, 0xFF8A94A8, 0xFF1A1226); txt("armour " + n(v.armor), 24, 80, 15, 0xFFFFFF, -1, 0);
                if (hasWater(v)) { pill(28 + bw, 68, bw, 24, v.water / 20, 0xFF7EC4FF, 0xFF2A6CC8, 0xFF1A1226); txt("water " + n(v.water) + " / 20", 38 + bw, 80, 15, 0xFFFFFF, -1, 0); }
                if (hasMana(v)) { pill(14, 98, W - 28, 10, v.mana / v.maxMana, 0xFFD0A0FF, 0xFF8A4AD0, 0xFF1A1226); txt("mana " + n(v.mana) + " / " + n(v.maxMana), W / 2F, 118, 12, 0xD8C0FF, 0, 0); }
                else if (v.temp >= 0) txt(tempWord(v.temp), W / 2F, 110, 14, v.temp > 16 ? 0xFF8A50 : v.temp < 6 ? 0x9AD8FF : 0xD0C8E0, 0, 0);
            };
            case "potions": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                java.util.List<Object[]> o = new java.util.ArrayList<Object[]>();
                o.add(new Object[]{v.hp / v.max, 0xFFE8304E, 0xFF7A0A20, n(v.hp), "HEALTH"}); o.add(new Object[]{v.food / v.maxFood, 0xFFF0A040, 0xFF7A4010, n(v.food), "FOOD"});
                if (hasWater(v)) o.add(new Object[]{v.water / 20, 0xFF4AA0FF, 0xFF123C80, n(v.water), "WATER"});
                if (hasMana(v)) o.add(new Object[]{v.mana / v.maxMana, 0xFFB57EDC, 0xFF4A2A7A, n(v.mana), "MANA"});
                o.add(new Object[]{v.armor / 20, 0xFFC9D1E0, 0xFF4A5468, n(v.armor), "ARMOUR"});
                float step = W / (float) o.size(), r = 42;
                for (int i = 0; i < o.size(); i++) {
                    float f = clamp((Float) o.get(i)[0]), cx = step * (i + .5F), cy = 56, lvl = cy + r - 2 * r * f;
                    fill(circle(cx, cy, r), 0xFF0C0814);
                    float[] liquid = clip(circle(cx, cy, r), false, lvl + (float) Math.sin(t / 300.0 + i) * 2, false);
                    if (f > 0) fillGrad(liquid, (Integer) o.get(i)[1], (Integer) o.get(i)[2], lvl, cy + r);
                    stroke(circle(cx, cy, r), 0xFF3A2E52, 3);
                    fill(ellipse(cx - r * .35F, cy - r * .45F, r * .28F, r * .16F, -.6), 0x38FFFFFF);
                    txt((String) o.get(i)[3], cx, cy, 20, 0xFFFFFF, 0, 0);
                    txt((String) o.get(i)[4], cx, cy + r + 12, 12, 0xB8AED0, 0, 0);
                }
            };
            case "pills": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                float half = (W - 42) / 2F;
                segs(14, 30, half, v.hp, v.max, true, 0); txt(n(v.hp) + "/" + n(v.max) + (v.max > 20 ? " · layer " + n(v.hp / 20) : ""), 40, 60, 13, 0xFFB3C1, -1, 0);
                segs(28 + half, 30, half, foodN(v), 20, false, 0xFFF0A040);
                segs(14, 76, half, v.armor, 20, false, 0xFFC9D1E0);
                if (hasWater(v)) segs(28 + half, 76, half, v.water, 20, false, 0xFF4AA0FF);
                txt(n(v.food) + " food" + (hasWater(v) ? "   " + n(v.water) + " water" : ""), W - 14, 60, 13, 0xFFD49A, 1, 0);
            };
            case "rings": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                java.util.List<Object[]> g = new java.util.ArrayList<Object[]>();
                g.add(new Object[]{v.hp / v.max, 0xFFFF4D6D, n(v.hp) + "/" + n(v.max)});
                if (v.abs > 0) g.add(new Object[]{v.abs / 20, 0xFFF5C542, "+" + n(v.abs)});
                g.add(new Object[]{v.food / v.maxFood, 0xFFF0A040, n(v.food) + " food"});
                if (hasWater(v)) g.add(new Object[]{v.water / 20, 0xFF4AA0FF, n(v.water) + " water"});
                if (hasMana(v)) g.add(new Object[]{v.mana / v.maxMana, 0xFFB57EDC, n(v.mana) + " mana"});
                g.add(new Object[]{v.armor / 20, 0xFFC9D1E0, n(v.armor) + " armour"});
                if (v.temp >= 0) g.add(new Object[]{v.temp / 20, v.temp > 14 ? 0xFFFF7A40 : v.temp < 7 ? 0xFF7AD0FF : 0xFFB8E08A, tempWord(v.temp)});
                float step = W / (float) g.size(), r = Math.min(30, step / 2.8F);
                for (int i = 0; i < g.size(); i++) {
                    float cx = step * (i + .5F), cy = 50, f = clamp((Float) g.get(i)[0]);
                    arc(cx, cy, r, 7, 0, Math.PI * 2, 0xFF2A2040);
                    if (f > 0) arc(cx, cy, r, 7, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * f, (Integer) g.get(i)[1]);
                    fill(circle(cx, cy, r * .45F), (Integer) g.get(i)[1]);
                    txt((String) g.get(i)[2], cx, cy + r + 16, 13, 0xE8E0F4, 0, 0);
                }
            };
            case "numbers": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                java.util.List<Object[]> c = new java.util.ArrayList<Object[]>();
                c.add(new Object[]{"♥", n(v.hp), "/" + n(v.max), v.hp / v.max, 0xFFFF5070}); c.add(new Object[]{"food", n(v.food), "/" + n(v.maxFood), v.food / v.maxFood, 0xFFF0A040});
                if (hasWater(v)) c.add(new Object[]{"water", n(v.water), "/20", v.water / 20, 0xFF4AA0FF});
                if (hasMana(v)) c.add(new Object[]{"mana", n(v.mana), "/" + n(v.maxMana), v.mana / v.maxMana, 0xFFB57EDC});
                c.add(new Object[]{"armour", n(v.armor), "", v.armor / 20, 0xFFC9D1E0});
                float cw = (W - 20) / (float) c.size();
                for (int i = 0; i < c.size(); i++) {
                    float x = 10 + i * cw;
                    txt((String) c.get(i)[0], x + 12, 24, 14, (Integer) c.get(i)[4] & 0xFFFFFF, -1, 0);
                    txt((String) c.get(i)[1], x + 12, 56, 38, i == 0 && v.hurt ? 0xFF8098 : 0xFFFFFF, -1, 0);
                    float nw = Minecraft.getMinecraft().fontRenderer.getStringWidth((String) c.get(i)[1]) * 38 / 9F;
                    txt((String) c.get(i)[2], x + 16 + nw, 62, 15, 0x9A91B0, -1, 0);
                    pill(x + 10, 84, cw - 24, 6, (Float) c.get(i)[3], (Integer) c.get(i)[4], (Integer) c.get(i)[4], 0xFF24192F);
                }
                if (v.temp >= 0 || v.abs > 0) txt((v.temp >= 0 ? "Feels " + tempWord(v.temp).toLowerCase() : "") + (v.abs > 0 ? "   absorption +" + n(v.abs) : ""), W / 2F, 112, 14, 0xB8AED0, 0, 0);
            };
            case "crystals": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                gems(40, inTierSum(v), TIER[tierIdx(v)], "Health " + n(v.hp) + "/" + n(v.max), false);
                gems(40, foodN(v), 0xFFF0A040, "Food " + n(v.food), true);
                gems(84, v.armor, 0xFFC9D1E0, "Armour " + n(v.armor), false);
                if (hasWater(v)) gems(84, v.water, 0xFF4AA0FF, "Water " + n(v.water), true);
            };
            case "wings": return (v, t) -> {
                fillGrad(rrect(0, 0, W, H, 8), 0xFF2A2040, 0xFF140E22, 0, H);
                float cx = W / 2F, by = 56, wing = cx - 50;
                fill(rrect(cx - 30, by - 26, 60, 52, 10), 0xFF14240C); stroke(rrect(cx - 30, by - 26, 60, 52, 10), 0xFF80FF20, 2);
                txt(String.valueOf(v.lvl), cx, by - 4, 24, 0x80FF20, 0, 0); txt("LEVEL", cx, by + 16, 10, 0x80FF20, 0, 0);
                wingBar(cx, wing, -1, v.hp / v.max, 0xFFFF5070, 0xFFB01030, by - 22, 26);
                wingBar(cx, wing, 1, v.food / v.maxFood, 0xFFFFC060, 0xFFC07010, by - 22, 26);
                wingBar(cx, wing, -1, v.armor / 20, 0xFFDFE6F2, 0xFF8A94A8, by + 12, 8);
                if (hasWater(v)) wingBar(cx, wing, 1, v.water / 20, 0xFF7EC4FF, 0xFF2A6CC8, by + 12, 8);
                txt("♥ " + n(v.hp) + "/" + n(v.max), cx - 52, by - 9, 15, 0xFFFFFF, 1, 0);
                txt(n(v.food) + "/" + n(v.maxFood) + " food", cx + 52, by - 9, 15, 0xFFFFFF, -1, 0);
                txt("armour " + n(v.armor), cx - 52, by + 36, 12, 0xB8AED0, 1, 0);
                if (hasWater(v)) txt("water " + n(v.water), cx + 52, by + 36, 12, 0xB8AED0, -1, 0);
            };
            default: return null;
        }
    }

    private static float inTierSum(V v) { return Math.min(20, v.hp - tierIdx(v) * 20); }

    private static void segs(float x, float y, float w, float val, float max, boolean tiers, int col) {
        float sx = x, sw = (w - 9 * 3) / 10;
        int top = tiers ? Math.max(0, Math.min(TIER.length - 1, (int) Math.ceil(val / 20) - 1)) : 0;
        float in = tiers ? val - top * 20 : val;
        for (int i = 0; i < 10; i++) {
            float f = clamp((in - i * 2) / 2), bx = sx + i * (sw + 3);
            fill(rrect(bx, y, sw, 16, 5), tiers && top > 0 ? (TIER[top - 1] & 0xFFFFFF) | 0x88000000 : 0xFF24192F);
            if (f > 0) fill(clip(rrect(bx, y, sw, 16, 5), true, bx + sw * f, true), tiers ? TIER[top] : col);
        }
    }

    private static void gems(float y, float val, int col, String label, boolean right) {
        float step = 22, x0 = right ? W - 18 - 9 * step : 22;
        for (int i = 0; i < 10; i++) {
            float x = x0 + i * step, f = statF(val, i);
            float[] d = diamond(x, y, 9);
            fill(d, 0xFF24192F);
            if (f > 0) fillGrad(clip(d, false, y + 9 - 18 * f, false), 0xFFFFFFFF, col, y - 9, y);
            stroke(d, 0x88000000, 1.5F);
        }
        txt(label, right ? W - 14 : 14, y + 20, 12, 0xB8AED0, right ? 1 : -1, 0);
    }

    private static void wingBar(float cx, float wing, int dir, float f, int c1, int c2, float y, float h) {
        float bx = dir < 0 ? cx - 40 - wing : cx + 40;
        fill(rrect(bx, y, wing, h, h / 2), 0xFF1A1226);
        float w = wing * clamp(f);
        if (w > 1) fillGrad(rrect(dir < 0 ? cx - 40 - w : cx + 40, y, w, h, h / 2), c1, c2, y, y + h);
    }
}
