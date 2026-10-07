package com.dogpound.canvas;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSplashPotion;
import net.minecraft.item.ItemLingeringPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Pride Crosshair (requested feature) + a fancy
 * block outline. The crosshair shows what you can do with what you look at and hold:
 *   mob -> attack brackets + a ring that fills as your swing recharges      chest/door/button/lever/machine -> interact ring
 *   block + the right tool -> green tick, can't harvest -> red cross      block in hand -> little "place" square
 *   breaking -> ring with the mining progress   bow -> draw ring   snowball/pearl/egg/potion -> throw arc   eating -> ring
 *   shield up -> shield mark   nothing -> a tiny dot
 * The outline round the block you look at shimmers through the Pride colours and fills up while you mine it.
 * If another mod (guns, Epic Fight) cancels the crosshair first, it is left to them.
 */
public class DPCrosshair {
    private static final int PINK = 0xFFF5A9B8, BLUE = 0xFF5BCEFA, GREEN = 0xFF8CE06A, RED = 0xFFFF5A64, WHITE = 0xFFFFFFFF, GOLD = 0xFFFFC040;
    private static final int[] RAINBOW = { 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982, 0x5BCEFA, 0xF5A9B8 };
    private static Field damageField;
    private static final Map<Class<?>, Boolean> INTERACTS = new HashMap<Class<?>, Boolean>();

    /** how far the block being broken is, 0..1 (PlayerControllerMP keeps it private) */
    static float miningProgress() {
        PlayerControllerMP pc = Minecraft.getMinecraft().playerController;
        if (pc == null || !pc.getIsHittingBlock()) return 0F;
        try {
            if (damageField == null) {
                for (String n : new String[]{ "field_78770_f", "curBlockDamageMP" }) {
                    try { damageField = PlayerControllerMP.class.getDeclaredField(n); damageField.setAccessible(true); break; } catch (NoSuchFieldException ignored) { }
                }
            }
            return damageField == null ? 0F : damageField.getFloat(pc);
        } catch (Throwable t) { return 0F; }
    }

    /** does right-clicking this block do something? (it has its own onBlockActivated, or holds an inventory) */
    private static boolean interacts(World w, BlockPos pos, IBlockState s) {
        TileEntity te = w.getTileEntity(pos);
        if (te instanceof net.minecraft.inventory.IInventory) return true;
        Class<?> c = s.getBlock().getClass();
        Boolean b = INTERACTS.get(c);
        if (b == null) {
            b = false;
            for (Class<?> k = c; k != null && k != Block.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods())
                    if ((m.getName().equals("func_180639_a") || m.getName().equals("onBlockActivated")) && m.getParameterCount() == 9) { b = true; break; }
                if (b) break;
            }
            INTERACTS.put(c, b);
        }
        return b;
    }

    // ------------------------------------------------------------------ crosshair

    /** what is going on right now -> one of DPCrosshairConfig.SITUATIONS (Dynamic Crosshair's idea, 1.12 rules) */
    static String situation(Minecraft mc, EntityPlayerSP p) {
        if (p.isHandActive()) {
            ItemStack use = p.getActiveItemStack();
            EnumAction a = use.getItemUseAction();
            if (a == EnumAction.BOW) return ItemBow.getArrowVelocity(use.getMaxItemUseDuration() - p.getItemInUseCount()) >= 1F ? "ranged_charged" : "ranged_charging";
            if (a == EnumAction.BLOCK) return "shield";
            if (a == EnumAction.EAT || a == EnumAction.DRINK) return "eating";
        }
        ItemStack main = p.getHeldItemMainhand(), off = p.getHeldItemOffhand();
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            Entity t = hit.entityHit;
            if (t instanceof net.minecraft.entity.IMerchant || t instanceof EntityItemFrame || t instanceof net.minecraft.entity.item.EntityArmorStand
                    || t instanceof net.minecraft.entity.item.EntityBoat || t instanceof net.minecraft.entity.item.EntityMinecart
                    || (t instanceof net.minecraft.entity.passive.AbstractHorse && !p.isSneaking())) return "interact_entity";
            if (t instanceof EntityLivingBase) return p.getCooledAttackStrength(0F) >= 1F ? "attack_ready" : "entity";
            return "interact_entity";
        }
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            IBlockState s = mc.world.getBlockState(pos);
            if (interacts(mc.world, pos, s) && !p.isSneaking()) return "interact_block";
            if (main.getItem() instanceof ItemBlock || (main.isEmpty() && off.getItem() instanceof ItemBlock)) return "place";
            boolean canHarvest = s.getBlockHardness(mc.world, pos) >= 0 && ForgeHooks.canHarvestBlock(s.getBlock(), p, mc.world, pos);
            if (!canHarvest) return "wrong_tool";
            if (!main.isEmpty() && main.getDestroySpeed(s) > 1.5F) return "tool";
            return "block";
        }
        if (throwable(main) || throwable(off)) return "throwable";
        if (main.getItem() instanceof ItemBow || off.getItem() instanceof ItemBow) return "ranged";
        if (main.getItem() instanceof net.minecraft.item.ItemSword || main.getItem() instanceof net.minecraft.item.ItemAxe) return "melee";
        EnumAction ua = main.isEmpty() ? EnumAction.NONE : main.getItemUseAction();
        if (ua == EnumAction.EAT || ua == EnumAction.DRINK || main.getItem() instanceof net.minecraft.item.ItemBucket) return "use_item";
        return "nothing";
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void crosshair(RenderGameOverlayEvent.Pre e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !DPConfig.prideCrosshair) return;
        DPCrosshairConfig cfg = DPCrosshairConfig.get();
        if (cfg.mode == 0) return;                                              // off: Minecraft's own
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.gameSettings.showDebugInfo && !mc.gameSettings.reducedDebugInfo) return;
        e.setCanceled(true);
        if (cfg.hideInThirdPerson && mc.gameSettings.thirdPersonView != 0) return;
        try { draw(mc, p, e.getResolution(), cfg); } catch (Throwable t) { /* never break the HUD */ }
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.getTextureManager().bindTexture(Gui.ICONS);
    }

    /** the colour a situation is drawn in, after the colour mode */
    static int colorFor(DPCrosshairConfig cfg, String sit, int own) {
        int c = own != 0 ? own : DPCrosshairConfig.defaultColor(sit);
        if ("rainbow".equals(cfg.colorMode)) c = rainbow((System.currentTimeMillis() % 3000L) / 3000F) & 0xFFFFFF | (c & 0xFF000000);
        else if ("pride".equals(cfg.colorMode)) c = PRIDE6[(int) ((System.currentTimeMillis() / 500L) % PRIDE6.length)] | (c & 0xFF000000);
        else if ("invert".equals(cfg.colorMode)) c = 0xFFFFFFFF;
        int a = (int) (((c >>> 24) & 0xFF) * Math.max(0, Math.min(100, cfg.opacity)) / 100F);
        return (a << 24) | (c & 0xFFFFFF);
    }
    private static final int[] PRIDE6 = { 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982 };

    private void draw(Minecraft mc, EntityPlayerSP p, ScaledResolution r, DPCrosshairConfig cfg) {
        float cx = r.getScaledWidth() / 2F, cy = r.getScaledHeight() / 2F, u = Math.max(0.25F, cfg.scale);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        if ("invert".equals(cfg.colorMode)) GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        else GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        if (cfg.hideSprinting && p.isSprinting()) return;
        if (cfg.hideWithMap && (p.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemMap || p.getHeldItemOffhand().getItem() instanceof net.minecraft.item.ItemMap)) return;
        String sit = cfg.mode == 2 ? "block" : situation(mc, p);
        DPCrosshairConfig.Sit look = cfg.sit(sit);
        boolean shadow = cfg.shadow && !"invert".equals(cfg.colorMode);
        float su = u * (look.size <= 0 ? 1F : look.size);
        if (cfg.spreadOnMove) {                                                     // opens up while you move, like shooter games
            double v = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ) + Math.abs(p.motionY) * 0.5;
            spread += (Math.min(1F, (float) v * 4F) - spread) * 0.2F;
            su *= 1F + spread * 0.6F;
        }
        if (cfg.pulseReady && "attack_ready".equals(sit)) su *= 1F + 0.08F * (float) Math.sin(System.currentTimeMillis() / 120.0);
        long now = System.currentTimeMillis();
        if (cfg.breathe) su *= 1F + 0.06F * (float) Math.sin(now / (1600.0 / Math.max(1, cfg.breatheSpeed)));
        if (cfg.hitBounce && now - hitAt < 220) su *= 1F + 0.3F * (1F - (now - hitAt) / 220F);
        GlStateManager.pushMatrix();
        if (cfg.spin != 0) {
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.rotate((System.currentTimeMillis() % 360000L) / 1000F * cfg.spin, 0, 0, 1);
            GlStateManager.translate(-cx, -cy, 0);
        }
        int col = colorFor(cfg, sit, look.color);
        if (look.show && cfg.glow && !"invert".equals(cfg.colorMode)) {           // a soft glow of the same colour behind it
            int ga = (int) (((col >>> 24) & 0xFF) * Math.max(5, Math.min(100, cfg.glowStrength)) / 100F * 0.35F);
            DPCrosshairStyles.draw(look.style, cx, cy, su * 1.5F, (ga / 2 << 24) | (col & 0xFFFFFF), false);
            DPCrosshairStyles.draw(look.style, cx, cy, su * 1.25F, (ga << 24) | (col & 0xFFFFFF), false);
        }
        if (look.show) DPCrosshairStyles.draw(look.style, cx, cy, su, col, shadow);
        GlStateManager.popMatrix();
        if (cfg.readySparkles && "attack_ready".equals(sit))                       // little sparkles circling a fully charged hit
            for (int i = 0; i < 4; i++) {
                double a = now / 300.0 + i * Math.PI / 2;
                DPCrosshairStyles.draw("sparkle", cx + (float) Math.cos(a) * 9 * u, cy + (float) Math.sin(a) * 9 * u, u * 0.4F, 0xE0FFFFFF, false);
            }
        if (cfg.killHearts && killed && now - hitAt < 900) {                       // hearts float up when you get a kill
            float k = (now - hitAt) / 900F;
            for (int i = 0; i < 5; i++)
                DPCrosshairStyles.draw("heart", cx + (i - 2) * 4 * u + (float) Math.sin(k * 6 + i) * u, cy - 6 * u - k * 16 * u - (i % 2) * 3 * u, u * 0.45F,
                        ((int) (255 * (1 - k)) << 24) | 0xFF6F9A, false);
        }
        if (cfg.centerDot) DPCrosshairStyles.draw("dot", cx, cy, u, 0xFFFFFFFF, shadow);
        if (cfg.hitMarker && System.currentTimeMillis() - hitAt < 350) {          // you just hit something
            float k = 1F - (System.currentTimeMillis() - hitAt) / 350F;
            int hcol = ((int) (255 * k) << 24) | (killed ? 0xFF5A64 : 0xFFFFFF);
            DPCrosshairStyles.draw("x_open", cx, cy, u * 1.4F, hcol, false);
        }
        if (cfg.mode == 2) return;
        if ((cfg.targetName || cfg.targetDistance) && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.MISS) {
            net.minecraft.util.math.RayTraceResult hit = mc.objectMouseOver;
            String name = "";
            if (cfg.targetName) {
                if (hit.entityHit != null) name = hit.entityHit.getDisplayName().getFormattedText();
                else try { net.minecraft.block.state.IBlockState st = mc.world.getBlockState(hit.getBlockPos());
                    ItemStack pick = st.getBlock().getPickBlock(st, hit, mc.world, hit.getBlockPos(), p);
                    name = pick.isEmpty() ? st.getBlock().getLocalizedName() : pick.getDisplayName(); } catch (Throwable ignored) { }
            }
            if (cfg.targetDistance && hit.hitVec != null) name += (name.isEmpty() ? "" : "  ") + "\u00A77" + String.format(java.util.Locale.ROOT, "%.1f", p.getPositionEyes(1F).distanceTo(hit.hitVec)) + "m";
            if (!name.isEmpty()) {
                GlStateManager.enableTexture2D();
                net.minecraft.client.gui.FontRenderer fr = mc.fontRenderer;
                float sc = 0.5F * Math.max(1F, u), w = fr.getStringWidth(name) * sc, ty = cy + 13 * u;
                if (cfg.nameShadowBox) { GlStateManager.disableTexture2D(); DPCrosshairStyles.rect(cx - w / 2 - 2, ty - 1.5F, cx + w / 2 + 2, ty + 9 * sc + 0.5F, 0x90000000); GlStateManager.enableTexture2D(); }
                GlStateManager.pushMatrix(); GlStateManager.translate(cx - w / 2, ty, 0); GlStateManager.scale(sc, sc, 1);
                fr.drawStringWithShadow(name, 0, 0, 0xFFFFFF);
                GlStateManager.popMatrix();
                GlStateManager.disableTexture2D();
            }
        }
        if (cfg.mobHealth && mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            EntityLivingBase t = (EntityLivingBase) mc.objectMouseOver.entityHit;
            float hp = Math.max(0F, Math.min(1F, t.getHealth() / Math.max(1F, t.getMaxHealth())));
            float bw = 18 * u, by = cy + 9 * u;
            DPCrosshairStyles.rect(cx - bw / 2 - 0.5F, by - 0.5F, cx + bw / 2 + 0.5F, by + 2.5F, 0xA0000000);
            DPCrosshairStyles.rect(cx - bw / 2, by, cx - bw / 2 + bw * hp, by + 2F, hp > 0.5F ? 0xFF8CE06A : hp > 0.25F ? 0xFFFFC040 : 0xFFFF5A64);
        }

        // extras, like Dynamic Crosshair's modifiers
        float cool = p.getCooledAttackStrength(0F);
        if (cfg.attackRing && ("entity".equals(sit) || "melee".equals(sit) || "nothing".equals(sit)) && cool < 1F) {
            DPCrosshairStyles.ring(cx, cy, 7 * u, u, 1F, 0x50000000);
            DPCrosshairStyles.ring(cx, cy, 7 * u, u, cool, 0xC0F5A9B8);
        }
        float prog = miningProgress();
        if (cfg.miningRing && prog > 0F) {
            DPCrosshairStyles.ring(cx, cy, 8 * u, 1.4F * u, 1F, 0x60000000);
            DPCrosshairStyles.ring(cx, cy, 8 * u, 1.4F * u, prog, rainbow(prog));
        }
        if (cfg.toolMarks && "tool".equals(sit)) tick(cx + 7 * u, cy + 6 * u, GREEN);
        if (cfg.toolMarks && "wrong_tool".equals(sit)) xmark(cx + 7 * u, cy + 6 * u, 2, RED);
        if (cfg.placeHint && "place".equals(sit)) square(cx - 10 * u, cy + 5 * u, 3 * u, PINK);
        if (cfg.chargeRing && sit.startsWith("ranged_charg")) {
            float pull = ItemBow.getArrowVelocity(p.getActiveItemStack().getMaxItemUseDuration() - p.getItemInUseCount());
            DPCrosshairStyles.ring(cx, cy, 9 * u, 1.4F * u, 1F, 0x60000000);
            DPCrosshairStyles.ring(cx, cy, 9 * u, 1.4F * u, pull, pull >= 1F ? GREEN : PINK);
        }
        if (cfg.eatRing && "eating".equals(sit)) {
            ItemStack use = p.getActiveItemStack();
            float f = Math.min(1F, (use.getMaxItemUseDuration() - p.getItemInUseCount()) / (float) Math.max(1, use.getMaxItemUseDuration()));
            DPCrosshairStyles.ring(cx, cy, 8 * u, 1.4F * u, 1F, 0x60000000);
            DPCrosshairStyles.ring(cx, cy, 8 * u, 1.4F * u, f, GOLD);
        }
    }

    private float spread;
    private static long hitAt;
    private static boolean killed;

    /** hit marker: you hit something (fires on your side when you swing at it) */
    @SubscribeEvent
    public void hit(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        if (!e.getEntityPlayer().world.isRemote || e.getEntityPlayer() != Minecraft.getMinecraft().player) return;
        hitAt = System.currentTimeMillis();
        killed = e.getTarget() instanceof EntityLivingBase && ((EntityLivingBase) e.getTarget()).getHealth() <= 4F;
    }

    private static boolean throwable(ItemStack s) {
        return s.getItem() instanceof ItemSnowball || s.getItem() instanceof ItemEgg || s.getItem() instanceof ItemEnderPearl
                || s.getItem() instanceof ItemSplashPotion || s.getItem() instanceof ItemLingeringPotion || s.getItem() instanceof ItemExpBottle;
    }

    // ------------------------------------------------------------------ small marks (with a dark shadow)

    private static void rect(float x0, float y0, float x1, float y1, int c) { DPCrosshairStyles.rect(x0, y0, x1, y1, c); }

    private static void shadowed(float x0, float y0, float x1, float y1, int c) {
        rect(x0 - 0.5F, y0 - 0.5F, x1 + 0.5F, y1 + 0.5F, 0x70000000);
        rect(x0, y0, x1, y1, c);
    }

    private static void square(float x, float y, float s, int c) {
        shadowed(x, y, x + s, y + 0.75F, c); shadowed(x, y + s - 0.75F, x + s, y + s, c);
        shadowed(x, y, x + 0.75F, y + s, c); shadowed(x + s - 0.75F, y, x + s, y + s, c);
    }

    private static void tick(float x, float y, int c) {
        for (int i = 0; i < 2; i++) shadowed(x - 2 + i, y + i, x - 1 + i, y + 1 + i, c);
        for (int i = 0; i < 4; i++) shadowed(x + i, y + 1 - i, x + 1 + i, y + 2 - i, c);
    }

    private static void xmark(float x, float y, int s, int c) {
        for (int i = -s; i <= s; i++) { shadowed(x + i, y + i, x + i + 1, y + i + 1, c); shadowed(x + i, y - i, x + i + 1, y - i + 1, c); }
    }

    static int rainbow(float f) {
        int n = RAINBOW.length;
        float p = (f % 1F) * n;
        int i = (int) p, a = RAINBOW[i % n], b = RAINBOW[(i + 1) % n];
        float k = p - i;
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k), g = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k), bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ the fancy block outline

    @SubscribeEvent(priority = EventPriority.LOW)
    public void outline(DrawBlockHighlightEvent e) {
        DPCrosshairConfig cfg = DPCrosshairConfig.get();
        if (!DPConfig.prideOutline || !cfg.outline || "vanilla".equals(cfg.outlineColor) || e.getTarget() == null || e.getTarget().typeOfHit != RayTraceResult.Type.BLOCK) return;
        EntityPlayer p = e.getPlayer();
        World w = p.world;
        BlockPos pos = e.getTarget().getBlockPos();
        IBlockState s = w.getBlockState(pos);
        if (s.getMaterial() == net.minecraft.block.material.Material.AIR || !w.getWorldBorder().contains(pos)) return;
        if (cfg.outlineOnlyMining && miningProgress() <= 0F) return;
        e.setCanceled(true);
        float pt = e.getPartialTicks();
        double dx = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt, dy = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt, dz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        AxisAlignedBB box = s.getSelectedBoundingBox(w, pos).grow(0.002D).offset(-dx, -dy, -dz);
        float t = (System.currentTimeMillis() % 4000L) / 4000F;
        int c = "solid".equals(cfg.outlineColor) ? cfg.outlineSolid : "pride".equals(cfg.outlineColor) ? 0xFF000000 | PRIDE6[(int) ((System.currentTimeMillis() / 600L) % PRIDE6.length)] : rainbow(t);
        float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(cfg.outlineWidth + 1.4F);
        RenderGlobal.drawSelectionBoundingBox(box, 0F, 0F, 0F, 0.35F);         // soft dark edge under the colour
        GlStateManager.glLineWidth(cfg.outlineWidth);
        RenderGlobal.drawSelectionBoundingBox(box, r, g, b, 0.9F);
        float prog = miningProgress();
        if (prog > 0F && cfg.outlineFill) {                                     // fills from the middle out while you mine
            double k = 0.5 * (1 - prog);
            AxisAlignedBB in = box.shrink(Math.min(box.maxX - box.minX, Math.min(box.maxY - box.minY, box.maxZ - box.minZ)) * k);
            RenderGlobal.renderFilledBox(in, r, g, b, 0.18F + 0.22F * prog);
        } else if (cfg.outlinePulse) {
            float pulse = 0.04F + 0.03F * (float) Math.sin(System.currentTimeMillis() / 300.0);
            RenderGlobal.renderFilledBox(box, r, g, b, pulse);
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
