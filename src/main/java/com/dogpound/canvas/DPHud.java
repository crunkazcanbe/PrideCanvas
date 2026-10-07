package com.dogpound.canvas;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.MobEffects;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * PrideHUD (requested feature): one strip across the whole bottom of the screen, ~2.5 hotbars tall, sitting on top of
 * the hotbar, holding EVERY on-screen HUD: Minecraft's own bars redrawn natively in the middle, and every mod's overlay
 * in its own labelled card. Any mod is covered without per-mod code: each mod's RenderGameOverlayEvent listeners are
 * wrapped so that mod draws into its own off-screen buffer (2x GUI resolution); a periodic scan finds where it drew, and
 * that rectangle is placed into the strip. Anything that covers most of the screen (vignettes, blood/frost overlays)
 * is put back full-screen where it was. Mods in DPConfig.hudPassthrough keep drawing normally.
 */
public class DPHud {
    static int STRIP_H = 54;                                // from DPHudSettings.stripHeight
    static final int HOTBAR_H = 22, PAD = 3, LABEL_H = 7;
    static DPHud INSTANCE;
    public DPHud() { INSTANCE = this; }
    /** capture pixels per GUI pixel: 2 keeps 0.5-scaled text sharp; 1 on very wide screens (her 10240 px wide window made
     *  every capture 10240x2632 = ~216 MB of graphics memory with depth; 2026-10-04 RAM hunt) */
    private static int capRes = 2;
    private Framebuffer probeFbo;                           // ONE shared buffer for test-capturing idle mods (no make/delete churn)
    private long probeDrawn = -1;                           // frame an idle mod last drew into the test buffer
    /** a card stays on the strip this many frames after its mod last drew (her 2026-10-05 "it flickers, stuff moves
     *  around all the time"): mods that skip a frame or two no longer make every card jump sideways */
    private static final int HOLD = 240;
    private long lastMemLog;
    private static final int SCAN_EVERY = 12;               // frames between bounding-box scans (one mod per scan)
    private static final int RESCAN_FRAMES = 120;           // a mod already measured is re-measured at most this often
    /** measuring reads pixels back from the GPU, which makes it stop and wait: on her 10240 px screen a full read was
     *  ~100 MB every 4 frames and chunks stopped loading (2026-10-04). Now the GPU shrinks the picture 4x each way first. */
    private static final int SHRINK = 4;
    private Framebuffer small;

    private static final Set<String> ALWAYS_PASS = new HashSet<String>();
    static {
        ALWAYS_PASS.add("dpcanvas");        // us
        ALWAYS_PASS.add("prideinventory");  // the hotbar the strip sits on
        ALWAYS_PASS.add("minecraft");
        ALWAYS_PASS.add("forge");
        ALWAYS_PASS.add("FML");
    }

    /** one mod's off-screen copy of its HUD */
    static final class Capture {
        final String modid, name;
        Framebuffer fbo;
        long frame = -1;            // frame it was last cleared on
        long lastDraw = -1;         // frame it last drew anything into
        long lastScan = -1;         // frame its drawn area was last measured
        int emptyScans;             // scans in a row that found nothing
        boolean idle;               // draws nothing: no buffer, the mod draws straight to the screen (cheap)
        long probeUntil = -1;       // idle mods get re-tested for a few frames now and then
        int x0, y0, x1, y1;         // drawn area in capture pixels (top-left origin), x1<=x0 = nothing
        boolean full;               // covers most of the screen -> draw back full-screen
        int[] zl, zr;               // vital mods: what they drew in the hearts zone (left) / food zone (right), capture px
        long shownAt = -100000;     // frame its card was last on the strip
        Capture(String modid, String name) { this.modid = modid; this.name = name; }
        boolean empty() { return x1 <= x0 || y1 <= y0; }
    }

    private static final java.nio.IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final ByteBuffer MASK = BufferUtils.createByteBuffer(16);
    private final Map<String, Capture> captures = new LinkedHashMap<String, Capture>();
    private final Set<IEventListener> wrapped = new HashSet<IEventListener>();
    private long frame, lastWrapScan = -10000;
    private int scanCursor;
    private ByteBuffer readBuf;
    private Field ownerField, busIdField;
    private boolean broken;

    // ------------------------------------------------------------------ wrapping every mod's overlay listener

    /** stands in for a mod's listener: the mod draws into its own buffer instead of the screen */
    final class Wrapper implements IEventListener {
        final IEventListener inner;
        final Capture cap;
        Wrapper(IEventListener inner, Capture cap) { this.inner = inner; this.cap = cap; }

        @Override public void invoke(Event e) {
            if (DPHudSettings.get().mode(cap.modid) == 2 && active()) return;     // "Hidden" in HUD Settings > Mods
            if (!active() || passthrough(cap.modid) || (cap.idle && frame > cap.probeUntil)
                    || (vitPrevFbo >= 0 && VITAL_MODS.contains(cap.modid))) {
                boolean was = e.isCancelable() && e.isCanceled();
                inner.invoke(e);
                if (!was && e.isCancelable() && e.isCanceled() && e instanceof RenderGameOverlayEvent.Pre
                        && ((RenderGameOverlayEvent.Pre) e).getType() == ElementType.ALL) noteHider(cap.modid);
                return;
            }
            int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            java.nio.IntBuffer vp = VIEWPORT;
            vp.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, vp);
            // Minecraft draws the HUD with alpha writes OFF (so the window never turns see-through); in our buffer that
            // left every pixel at alpha 0 = an invisible, "empty" capture. Alpha on while the mod draws into its buffer.
            GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, MASK);
            boolean bound = bind(cap);
            boolean wasCancelled = e.isCancelable() && e.isCanceled();
            try {
                inner.invoke(e);
            } finally {
                if (!wasCancelled && e.isCancelable() && e.isCanceled() && e instanceof RenderGameOverlayEvent.Pre
                        && ((RenderGameOverlayEvent.Pre) e).getType() == ElementType.ALL) noteHider(cap.modid);
                if (bound) {
                    GlStateManager.colorMask(MASK.get(0) != 0, MASK.get(1) != 0, MASK.get(2) != 0, MASK.get(3) != 0);
                    cap.lastDraw = frame;
                    if (cap.fbo == probeFbo) probeDrawn = frame;
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
                    GL11.glViewport(vp.get(0), vp.get(1), vp.get(2), vp.get(3));
                }
            }
        }
    }

    /** listeners we don't capture still get watched: who cancels the WHOLE HUD every frame (big pack 2026-10-04: the
     *  strip got no mod cards and held 148 buffers because Post(ALL) almost never fired) */
    final class Detector implements IEventListener {
        final IEventListener inner; final String owner;
        Detector(IEventListener inner, String owner) { this.inner = inner; this.owner = owner; }
        @Override public void invoke(Event e) {
            boolean was = e.isCancelable() && e.isCanceled();
            inner.invoke(e);
            if (!was && e.isCancelable() && e.isCanceled() && e instanceof RenderGameOverlayEvent.Pre && ((RenderGameOverlayEvent.Pre) e).getType() == ElementType.ALL) noteHider(owner);
        }
    }

    static final int MAX_BUFFERS = 48;

    /** make room for a mod that IS drawing: give back the buffer of the one that drew longest ago / draws nothing
     *  (2026-10-04: Mine and Slash never got a buffer behind 40 idle mods, so it drew at the top of the screen) */
    private boolean evictOne(Capture wanting) {
        Capture victim = null;
        for (Capture x : captures.values()) {
            if (x == wanting || x.fbo == null || x.fbo == probeFbo) continue;
            boolean useless = x.empty() || frame - x.lastDraw > 200;
            if (!useless) continue;
            if (victim == null || x.lastDraw < victim.lastDraw) victim = x;
        }
        if (victim == null) return false;
        release(victim);
        victim.idle = true;
        return true;
    }
    private int buffers;

    private boolean bind(Capture c) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution r = new ScaledResolution(mc);
        capRes = mc.displayWidth >= 6000 ? 1 : 2;
        int w = Math.min(mc.displayWidth, r.getScaledWidth() * capRes), h = Math.min(mc.displayHeight, r.getScaledHeight() * capRes);
        if (w <= 0 || h <= 0) return false;
        if (c.idle) {                                                            // a test capture: use the shared buffer
            if (probeFbo == null || probeFbo.framebufferWidth != w || probeFbo.framebufferHeight != h) {
                if (probeFbo == null) probeFbo = new Framebuffer(w, h, false); else probeFbo.createBindFramebuffer(w, h);
                probeFbo.setFramebufferColor(0F, 0F, 0F, 0F);
            }
            if (c.fbo != probeFbo) c.frame = -1;
            c.fbo = probeFbo;
        } else if (c.fbo == null || c.fbo == probeFbo || c.fbo.framebufferWidth != w || c.fbo.framebufferHeight != h) {
            // colour only: every capture shares ONE depth buffer (her 2026-10-04 "make sure it runs without too much video RAM")
            if (c.fbo == null || c.fbo == probeFbo) {
                if (buffers >= MAX_BUFFERS && !evictOne(c)) { c.idle = true; c.fbo = null; return false; }   // full and nobody to evict
                c.fbo = new Framebuffer(w, h, false); buffers++;
            }
            else c.fbo.createBindFramebuffer(w, h);
            c.fbo.setFramebufferColor(0F, 0F, 0F, 0F);
            c.frame = -1;
            c.x0 = c.y0 = c.x1 = c.y1 = 0; c.lastScan = -1; c.shownAt = -100000;   // new size: measure again from scratch
        }
        c.fbo.bindFramebuffer(true);
        if (c.fbo != probeFbo) attachSharedDepth(c.fbo, w, h);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.clearDepth(1.0D);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);                  // shared depth: clear it for whoever draws now
        if (c.frame != frame) {
            c.frame = frame;
            GlStateManager.clearColor(0F, 0F, 0F, 0F);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        }
        return true;
    }

    private int sharedDepth = -1, depthW, depthH;
    private final java.util.Map<Framebuffer, Integer> attached = new java.util.WeakHashMap<Framebuffer, Integer>();

    /** one depth buffer for all capture buffers (items in mod HUDs still draw their 3D blocks correctly) */
    private void attachSharedDepth(Framebuffer f, int w, int h) {
        if (sharedDepth < 0 || depthW != w || depthH != h) {
            if (sharedDepth >= 0) GL30.glDeleteRenderbuffers(sharedDepth);
            sharedDepth = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, sharedDepth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24, w, h);
            depthW = w; depthH = h;
            attached.clear();
        }
        Integer was = attached.get(f);
        if (was == null || was != sharedDepth + f.framebufferObject * 31) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, sharedDepth);
            attached.put(f, sharedDepth + f.framebufferObject * 31);
        }
    }

    private String modOf(IEventListener l) {
        if (!(l instanceof ASMEventHandler)) return null;
        try {
            if (ownerField == null) { ownerField = ASMEventHandler.class.getDeclaredField("owner"); ownerField.setAccessible(true); }
            ModContainer mc = (ModContainer) ownerField.get(l);
            return mc == null ? null : mc.getModId();
        } catch (Throwable t) { return null; }
    }

    private int busId() throws Exception {
        if (busIdField == null) { busIdField = EventBus.class.getDeclaredField("busID"); busIdField.setAccessible(true); }
        return busIdField.getInt(MinecraftForge.EVENT_BUS);
    }

    /** find overlay listeners of every mod and swap in Wrappers (re-run now and then for late registrations) */
    private void wrapAll(ScaledResolution res) {
        try {
            int id = busId();
            RenderGameOverlayEvent base = new RenderGameOverlayEvent(0F, res);
            ListenerList baseList = base.getListenerList();
            // a listener on the base event shows up in the Pre and Post lists too, but lives in the base list: wrap it there once
            Set<IEventListener> onBase = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IEventListener, Boolean>());
            for (IEventListener l : baseList.getListeners(id)) onBase.add(l);
            ListenerList[] lists = { baseList,
                    new RenderGameOverlayEvent.Pre(base, ElementType.ALL).getListenerList(),
                    new RenderGameOverlayEvent.Post(base, ElementType.ALL).getListenerList() };
            for (ListenerList list : lists) {
                for (IEventListener l : list.getListeners(id)) {
                    if (l instanceof EventPriority || l instanceof Wrapper || l instanceof Detector || wrapped.contains(l)) continue;
                    if (list != baseList && onBase.contains(l)) continue;
                    String mod = modOf(l);
                    if (mod == null || ALWAYS_PASS.contains(mod)) {
                        EventPriority p0 = l instanceof ASMEventHandler ? ((ASMEventHandler) l).getPriority() : null;
                        if (p0 != null && list != baseList) {
                            list.unregister(id, l);
                            list.register(id, p0, new Detector(l, mod != null ? mod : l.toString()));
                        }
                        wrapped.add(l);
                        continue;
                    }
                    Capture cap = captures.get(mod);
                    if (cap == null) {
                        ModContainer mc = Loader.instance().getIndexedModList().get(mod);
                        captures.put(mod, cap = new Capture(mod, mc == null ? mod : mc.getName()));
                    }
                    EventPriority p = ((ASMEventHandler) l).getPriority();
                    list.unregister(id, l);
                    list.register(id, p, new Wrapper(l, cap));
                    wrapped.add(l);
                    System.out.println("[PrideHUD] catching overlay of " + mod + " (" + p + ")");
                }
            }
        } catch (Throwable t) {
            broken = true;
            System.out.println("[PrideHUD] could not wrap mod overlays, HUD off: " + t);
        }
    }

    // ------------------------------------------------------------------ settings

    static boolean active() {
        Minecraft mc = Minecraft.getMinecraft();
        return DPConfig.hudEnabled && mc.world != null && mc.player != null && !mc.gameSettings.hideGUI;
    }

    private static Set<String> passCache;
    private static String passSrc;

    static boolean passthrough(String modid) {
        String src = DPConfig.hudPassthrough == null ? "" : DPConfig.hudPassthrough;
        if (!src.equals(passSrc)) {
            passSrc = src;
            passCache = new HashSet<String>();
            for (String s : src.split(",")) if (!s.trim().isEmpty()) passCache.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return passCache.contains(modid.toLowerCase(Locale.ROOT)) || DPHudSettings.get().mode(modid) == 1;
    }

    // ------------------------------------------------------------------ events

    static final net.minecraft.client.settings.KeyBinding KEY =
            new net.minecraft.client.settings.KeyBinding("Show / hide the Pride HUD", org.lwjgl.input.Keyboard.KEY_F9, "Pride UI");

    @SubscribeEvent
    public void tick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public void key(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (PAGE_LEFT.isPressed()) page = page <= 0 ? Math.max(0, pages - 1) : page - 1;
        while (PAGE_RIGHT.isPressed()) page = page + 1 >= pages ? 0 : page + 1;
        while (BIGGER.isPressed() || SMALLER.isPressed()) {
            Minecraft m = Minecraft.getMinecraft();
            float now = scale(new ScaledResolution(m));
            boolean up = BIGGER.isKeyDown();
            DPConfig.hudScale = Math.max(0.5F, Math.min(6F, now + (up ? 0.25F : -0.25F)));
            net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE);
            if (m.player != null) m.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§d✦ Pride HUD size " + DPConfig.hudScale + "x §7(Page Up / Page Down)"), true);
        }
        if (!KEY.isPressed()) return;
        DPConfig.hudEnabled = !DPConfig.hudEnabled;
        net.minecraftforge.common.config.ConfigManager.sync("dpcanvas", net.minecraftforge.common.config.Config.Type.INSTANCE);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                DPConfig.hudEnabled ? "§d✦ Pride HUD on" : "§7✦ Pride HUD off (F9 to bring it back)"), true);
    }

    // ------------------------------------------------------------------ mods that switch the whole HUD off some frames
    private final java.util.Set<String> hiders = new java.util.HashSet<String>();
    private long hideCount, postCount;
    private void noteHider(String modid) {
        if (hiders.add(modid)) System.out.println("[PrideHUD] " + modid + " hides the whole HUD on some frames; the Pride strip keeps showing (no flashing)");
        hideCount++;
    }

    /** a mod cancelled the whole HUD (her 2026-10-04 "the HUD keeps flashing on and off"): still draw the strip */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void hudCancelled(RenderGameOverlayEvent.Pre e) {
        if (e.getType() != ElementType.ALL || !e.isCanceled() || broken || !active() || Minecraft.getMinecraft().currentScreen != null) return;
        try { scanOne(); draw(e.getResolution()); } catch (Throwable t) { broken = true; System.out.println("[PrideHUD] draw failed, HUD off: " + t); }
    }

    /** vanilla's own bars move into the strip; wrap mod listeners on the first frames (and every ~10 s after) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void pre(RenderGameOverlayEvent.Pre e) {
        if (broken || !active()) return;
        if (e.getType() == ElementType.ALL && frame - lastWrapScan > 600) { lastWrapScan = frame; wrapAll(e.getResolution()); }
        if (vitalsMode() && vitalBar(e.getType()) && vitPrevFbo < 0) {
            vitPrevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            vitVp.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, vitVp);
            GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, vitMask);
            if (!bind(vitals)) vitPrevFbo = -1;
            else vitals.lastDraw = frame;
        }
    }

    // ------------------------------------------------------------------ vitals: hearts + food drawn by their real owners
    /** Scaling Health (coloured heart rows), Scaling Feast (bigger hunger pool) and AppleSkin (saturation, food preview)
     *  draw on top of / instead of Minecraft's hearts and food; Tough As Nails draws its thirst drops above the food. With any of them installed, those two bars are not redrawn
     *  by us: Minecraft and these mods draw them into one buffer, and the finished picture is placed in the middle panel
     *  (requested feature). */
    static final Set<String> VITAL_MODS = new HashSet<String>(java.util.Arrays.asList("scalinghealth", "scalingfeast", "appleskin", "toughasnails"));
    private static Boolean vitalsMode;
    static boolean vitalsMode() {
        if (vitalsMode == null) {
            boolean any = false;
            for (String m : VITAL_MODS) any |= Loader.isModLoaded(m);
            vitalsMode = any;
        }
        return vitalsMode && DPConfig.hudVitalsFromMods;
    }
    private static boolean vitalBar(ElementType t) { return t == ElementType.HEALTH || t == ElementType.FOOD || t == ElementType.AIR; }  // AIR: Tough As Nails draws thirst there

    private final Capture vitals = new Capture("vitals", "Health & Food");
    private int vitPrevFbo = -1;
    private final java.nio.IntBuffer vitVp = BufferUtils.createIntBuffer(16);
    private final ByteBuffer vitMask = BufferUtils.createByteBuffer(16);
    private int hx0, hy0, hx1, hy1, fx0, fy0, fx1, fy1;      // hearts (left half) / food (right half), capture pixels

    private void vitalsClose() {
        if (vitPrevFbo < 0) return;
        GlStateManager.colorMask(vitMask.get(0) != 0, vitMask.get(1) != 0, vitMask.get(2) != 0, vitMask.get(3) != 0);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, vitPrevFbo);
        GL11.glViewport(vitVp.get(0), vitVp.get(1), vitVp.get(2), vitVp.get(3));
        vitPrevFbo = -1;
    }

    /** a mod cancelled the bar (Scaling Health does, after drawing its own hearts): no Post comes, close here */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void vitalsPreEnd(RenderGameOverlayEvent.Pre e) {
        if (vitPrevFbo >= 0 && vitalBar(e.getType()) && e.isCanceled()) vitalsClose();
    }

    /** every Post listener (AppleSkin's overlays) has drawn into the buffer: close it */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void vitalsPostEnd(RenderGameOverlayEvent.Post e) {
        if (vitPrevFbo >= 0 && vitalBar(e.getType())) vitalsClose();
    }

    /** where the hearts (left of the screen centre) and the food (right of it) were drawn */
    private void scanVitals() {
        Capture c = vitals;
        if (c.fbo == null) return;
        int fw = c.fbo.framebufferWidth, fh = c.fbo.framebufferHeight;
        int[] sz = readShrunk(c.fbo);
        int w = sz[0], h = sz[1], mid = w / 2;
        int[] l = { w, h, -1, -1 }, r = { w, h, -1, -1 };
        for (int row = 0; row < h; row++) {
            int base = row * w * 4, y = h - 1 - row;
            for (int x = 0; x < w; x++) {
                if ((readBuf.get(base + x * 4 + 3) & 0xFF) <= 2) continue;
                int[] b = x < mid ? l : r;
                if (x < b[0]) b[0] = x;
                if (x > b[2]) b[2] = x;
                if (y < b[1]) b[1] = y;
                if (y > b[3]) b[3] = y;
            }
        }
        int k = SHRINK;
        hx0 = Math.max(0, (l[0] - 1) * k); hy0 = Math.max(0, (l[1] - 1) * k); hx1 = l[2] < 0 ? 0 : Math.min(fw, (l[2] + 2) * k); hy1 = l[3] < 0 ? 0 : Math.min(fh, (l[3] + 2) * k);
        fx0 = Math.max(0, (r[0] - 1) * k); fy0 = Math.max(0, (r[1] - 1) * k); fx1 = r[2] < 0 ? 0 : Math.min(fw, (r[2] + 2) * k); fy1 = r[3] < 0 ? 0 : Math.min(fh, (r[3] + 2) * k);
    }

    /** blit one half of the vitals picture into a box, keeping its shape, never bigger than it really is */
    private void placeVitals(int sx0, int sy0, int sx1, int sy1, int bx, int by, int bw, int bh, boolean rightAlign) {
        if (sx1 <= sx0 || sy1 <= sy0) return;
        float gw = (sx1 - sx0) / (float) capRes, gh = (sy1 - sy0) / (float) capRes;
        float sc = Math.min(1F, Math.min(bw / gw, bh / gh));
        float dw = gw * sc, dh = gh * sc;
        float dx = rightAlign ? bx + bw - dw : bx, dy = by + (bh - dh) / 2F;
        blit(vitals, sx0, sy0, sx1, sy1, dx, dy, dx + dw, dy + dh, 1F);
    }

    /** one vital mod's own heart/food drawing, into the middle card's box, same rules as placeVitals */
    private void placeZone(Capture c, int[] z, int bx, int by, int bw, int bh, boolean rightAlign) {
        float gw = (z[2] - z[0]) / (float) capRes, gh = (z[3] - z[1]) / (float) capRes;
        if (gw <= 0 || gh <= 0) return;
        float sc = Math.min(1F, Math.min(bw / gw, bh / gh));
        float dw = gw * sc, dh = gh * sc, dx = rightAlign ? bx + bw - dw : bx, dy = by + (bh - dh) / 2F;
        blit(c, z[0], z[1], z[2], z[3], dx, dy, dx + dw, dy + dh, 1F);
    }

    /** Minecraft's own bars draw into a throw-away buffer instead of being cancelled: cancelling skipped their Post
     *  events, and many mods (Tough As Nails, Mine and Slash...) draw their bars in exactly those Post events. */
    private static boolean vanillaBar(ElementType t) {
        switch (t) {
            case HEALTH: case ARMOR: case FOOD: case AIR: case EXPERIENCE: case HEALTHMOUNT: case JUMPBAR: return true;
            default: return false;
        }
    }

    private Framebuffer sink;
    private int sinkPrevFbo = -1;
    private final java.nio.IntBuffer sinkVp = BufferUtils.createIntBuffer(16);

    /** status effects live in the HUD now: Minecraft's top-right icons are hidden while the HUD is on (requested feature) */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void hidePotionIcons(RenderGameOverlayEvent.Pre e) {
        if (e.getType() == ElementType.POTION_ICONS && !broken && active() && DPHudSettings.get().effects && DPHudSettings.get().hideVanillaEffects) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void preLate(RenderGameOverlayEvent.Pre e) {
        if (broken || !active() || e.isCanceled() || !vanillaBar(e.getType()) || sinkPrevFbo >= 0 || vitPrevFbo >= 0) return;
        sinkPrevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        sinkVp.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, sinkVp);
        if (sink == null) sink = new Framebuffer(4, 4, false);
        sink.bindFramebuffer(true);
    }

    /** vanilla finished the bar: back to the real screen before any mod's Post listener runs */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void postEarly(RenderGameOverlayEvent.Post e) {
        if (sinkPrevFbo < 0 || !vanillaBar(e.getType())) return;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, sinkPrevFbo);
        GL11.glViewport(sinkVp.get(0), sinkVp.get(1), sinkVp.get(2), sinkVp.get(3));
        sinkPrevFbo = -1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void post(RenderGameOverlayEvent.Post e) {
        if (e.getType() != ElementType.ALL) return;
        vitalsClose();
        if (sinkPrevFbo >= 0) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, sinkPrevFbo);
            GL11.glViewport(sinkVp.get(0), sinkVp.get(1), sinkVp.get(2), sinkVp.get(3));
            sinkPrevFbo = -1;
        }
        if (broken || !active()) return;
        postCount++;
        try {
            if (frame % 300 == 0 && new java.io.File("config/pridehud-dump").exists()) dumpCaptures();
            scanOne();
            if (vitalsMode() && frame % 60 == 0 && frame - vitals.lastDraw < 3) scanVitals();
            draw(e.getResolution());
        } catch (Throwable t) {
            broken = true;
            System.out.println("[PrideHUD] draw failed, HUD off: " + t);
            t.printStackTrace();
        }
    }

    /** debug (touch config/pridehud-dump): save what every mod drew this frame as PNGs in screenshots/pridehud-dump/ */
    private void dumpCaptures() {
        java.io.File dir = new java.io.File("screenshots/pridehud-dump");
        dir.mkdirs();
        List<Capture> all = new ArrayList<Capture>(captures.values());
        all.add(vitals);
        for (Capture c : all) {
            if (c.fbo == null || frame - c.lastDraw > 3) continue;
            try {
                java.awt.image.BufferedImage img = net.minecraft.util.ScreenShotHelper.createScreenshot(c.fbo.framebufferWidth, c.fbo.framebufferHeight, c.fbo);
                javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, c.modid + ".png"));
            } catch (Throwable t) { System.out.println("[PrideHUD] dump " + c.modid + " failed: " + t); }
        }
        new java.io.File("config/pridehud-dump").delete();
        System.out.println("[PrideHUD] dumped captures to " + dir.getAbsolutePath());
    }

    // ------------------------------------------------------------------ finding what each mod drew

    private int probeCursor;

    private void scanOne() {
        if (captures.isEmpty()) return;
        List<Capture> list = new ArrayList<Capture>(captures.values());
        // every 8 frames one idle mod is captured again for a few frames, then measured (catches HUDs that appear later)
        if (DPHudSettings.get().probeIdle && frame % 2 == 0) {
            for (int i = 0; i < list.size(); i++) {
                Capture x = list.get((probeCursor + i) % list.size());
                if (x.idle) { x.probeUntil = frame + 3; x.lastScan = -1; probeCursor = (probeCursor + i + 1) % list.size(); break; }
            }
        }
        for (Capture x : list) if (x.idle && x.probeUntil == frame && x.fbo != null) { x.lastScan = frame; scan(x); if (x.idle) release(x); }
        if (frame - lastMemLog > 3600) {                                          // about once a minute: how much graphics memory the strip holds
            lastMemLog = frame;
            int n = 0; long bytes = 0;
            for (Capture x : list) if (x.fbo != null && x.fbo != probeFbo) { n++; bytes += 4L * x.fbo.framebufferWidth * x.fbo.framebufferHeight; }
            if (probeFbo != null) { n++; bytes += 4L * probeFbo.framebufferWidth * probeFbo.framebufferHeight; }
            if (sharedDepth >= 0) bytes += 4L * depthW * depthH;
            System.out.println("[PrideHUD] " + n + " capture buffers, ~" + bytes / (1 << 20) + " MB graphics memory; HUD drawn " + postCount + "x, hidden by " + hiders + " " + hideCount + "x");
        }
        if (frame % Math.max(2, DPHudSettings.get().scanEvery) != 0) return;
        // never-scanned captures that are drawing go first, ONE each (the old rule re-picked the same empty mod forever)
        Capture c = null;
        int quick = 0;                                                       // never-scanned ones: up to 4 per scan tick so 100+ mods sort themselves out fast
        for (Capture x : list) if (!x.idle && x.fbo != null && x.lastScan < 0 && frame - x.lastDraw < 3) {
            if (c == null) { c = x; continue; }
            if (quick++ >= 3) break;
            x.lastScan = frame; scan(x);
        }
        if (c == null) {
            for (int i = 0; i < list.size(); i++) {
                Capture x = list.get((scanCursor + i) % list.size());
                if (!x.idle && x.fbo != null && frame - x.lastDraw < 120 && frame - x.lastScan > Math.max(20, DPHudSettings.get().rescanFrames)) { c = x; scanCursor = (scanCursor + i + 1) % list.size(); break; }
            }
        }
        if (c == null) return;
        c.lastScan = frame;
        scan(c);
    }

    /** let the GPU shrink a capture SHRINK x each way, then read only that: returns {smallW, smallH} with RGBA in readBuf */
    private int[] readShrunk(Framebuffer src) {
        int w = src.framebufferWidth, h = src.framebufferHeight, sw = Math.max(1, w / SHRINK), sh = Math.max(1, h / SHRINK);
        if (small == null || small.framebufferWidth != sw || small.framebufferHeight != sh) {
            if (small == null) small = new Framebuffer(sw, sh, false); else small.createBindFramebuffer(sw, sh);
        }
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, small.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, sw, sh, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, small.framebufferObject);
        if (readBuf == null || readBuf.capacity() < sw * sh * 4) readBuf = BufferUtils.createByteBuffer(sw * sh * 4);
        readBuf.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, sw, sh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuf);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
        return new int[]{ sw, sh };
    }

    /** give back a capture's own buffer (the shared test buffer stays) */
    private void release(Capture c) {
        if (c.fbo != null && c.fbo != probeFbo) { c.fbo.deleteFramebuffer(); buffers = Math.max(0, buffers - 1); }
        c.fbo = null;
    }

    private void scan(Capture c) {
        int fw = c.fbo.framebufferWidth, fh = c.fbo.framebufferHeight;
        int[] sz = readShrunk(c.fbo);
        int w = sz[0], h = sz[1];
        int minX = w, minY = h, maxX = -1, maxY = -1;
        // Scaling Health & co. draw hearts/food/thirst in their own pass, not in the HEALTH/FOOD events: whatever a vital
        // mod draws where Minecraft's bars sit belongs in the middle card, not in that mod's own card (2026-10-04)
        boolean vital = VITAL_MODS.contains(c.modid) && vitalsMode();
        int zx0 = (fw / 2 - 96 * capRes) / SHRINK, zx1 = (fw / 2 + 96 * capRes) / SHRINK, zmid = fw / 2 / SHRINK;
        int zy0 = (fh - (HOTBAR_H + 44) * capRes) / SHRINK, zy1 = (fh - (HOTBAR_H - 2) * capRes) / SHRINK;
        int[] l = { w, h, -1, -1 }, r = { w, h, -1, -1 };
        for (int row = 0; row < h; row++) {
            int base = row * w * 4;
            for (int x = 0; x < w; x++) {
                if ((readBuf.get(base + x * 4 + 3) & 0xFF) > 2) {
                    int y = h - 1 - row;                                // top-left origin
                    if (vital && x >= zx0 && x <= zx1 && y >= zy0 && y <= zy1) {
                        int[] b = x < zmid ? l : r;
                        if (x < b[0]) b[0] = x;
                        if (x > b[2]) b[2] = x;
                        if (y < b[1]) b[1] = y;
                        if (y > b[3]) b[3] = y;
                        continue;
                    }
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        c.zl = l[2] < 0 ? null : new int[]{ Math.max(0, (l[0] - 1) * SHRINK), Math.max(0, (l[1] - 1) * SHRINK), Math.min(fw, (l[2] + 2) * SHRINK), Math.min(fh, (l[3] + 2) * SHRINK) };
        c.zr = r[2] < 0 ? null : new int[]{ Math.max(0, (r[0] - 1) * SHRINK), Math.max(0, (r[1] - 1) * SHRINK), Math.min(fw, (r[2] + 2) * SHRINK), Math.min(fh, (r[3] + 2) * SHRINK) };
        if (maxX < 0) {
            if (!c.empty() && frame - c.lastDraw < HOLD && frame - c.shownAt < HOLD) return;   // drew nothing this time: keep its card as is
            c.x0 = c.y0 = c.x1 = c.y1 = 0; c.full = false;
            if (c.zl != null || c.zr != null) { c.emptyScans = 0; c.idle = false; return; }   // only draws vitals: keep it, no card
            if (++c.emptyScans >= 3 && !c.idle) {     // 235 mods hook the HUD, most draw nothing: stop paying for them
                c.idle = true;
                release(c);
            }
            return;
        }
        // back to full-size pixels, one shrunk pixel of margin round it
        minX = Math.max(0, (minX - 1) * SHRINK); minY = Math.max(0, (minY - 1) * SHRINK);
        maxX = Math.min(fw - 1, (maxX + 2) * SHRINK - 1); maxY = Math.min(fh - 1, (maxY + 2) * SHRINK - 1);
        w = fw; h = fh;
        c.emptyScans = 0;
        c.idle = false;
        boolean was = !c.empty();
        boolean overlaps = maxX + 1 > c.x0 - 64 && minX < c.x1 + 64 && maxY + 1 > c.y0 - 64 && minY < c.y1 + 64;
        if (was && overlaps && frame - c.shownAt < HOLD) {                   // card is up: only ever grow, so nothing beside it moves
            minX = Math.min(minX, c.x0); minY = Math.min(minY, c.y0); maxX = Math.max(maxX, c.x1 - 1); maxY = Math.max(maxY, c.y1 - 1);
        }
        c.x0 = minX; c.y0 = minY; c.x1 = maxX + 1; c.y1 = maxY + 1;
        c.full = (c.x1 - c.x0) > w * 0.6 && (c.y1 - c.y0) > h * 0.5;
        if (!was) System.out.println("[PrideHUD] " + c.modid + " draws at " + c.x0 + "," + c.y0 + " " + (c.x1 - c.x0) + "x" + (c.y1 - c.y0) + (c.full ? " (full-screen effect)" : ""));
    }

    // ------------------------------------------------------------------ drawing the strip

    private static final int PANEL_TOP = 0xF2140E22, PANEL_BOTTOM = 0xF2080510, CARD = 0xCC1C1530, CARD_EDGE = 0x30FFFFFF;
    private static final int LEFT_W = 120, RIGHT_W = 128, VAN_W = 190;
    private float hurtFlash;
    private static DPHudThemes.Style style = new DPHudThemes.Style();
    private int page, pages = 1;
    private boolean panelsInit;

    static final net.minecraft.client.settings.KeyBinding PAGE_LEFT =
            new net.minecraft.client.settings.KeyBinding("Pride HUD: previous page", org.lwjgl.input.Keyboard.KEY_LEFT, "Pride UI");
    static final net.minecraft.client.settings.KeyBinding PAGE_RIGHT =
            new net.minecraft.client.settings.KeyBinding("Pride HUD: next page", org.lwjgl.input.Keyboard.KEY_RIGHT, "Pride UI");
    static final net.minecraft.client.settings.KeyBinding BIGGER =
            new net.minecraft.client.settings.KeyBinding("Pride HUD: bigger", org.lwjgl.input.Keyboard.KEY_PRIOR, "Pride UI");
    static final net.minecraft.client.settings.KeyBinding SMALLER =
            new net.minecraft.client.settings.KeyBinding("Pride HUD: smaller", org.lwjgl.input.Keyboard.KEY_NEXT, "Pride UI");

    /** how much bigger than Minecraft's GUI the strip is drawn (her 2026-10-04: "so compact everything on it so tiny you can't read it") */
    static float scale(ScaledResolution res) {
        if (DPConfig.hudScale > 0) return (float) DPConfig.hudScale;
        float k = res.getScaledHeight() * 0.10F / STRIP_H;
        return Math.max(1F, Math.min(4F, Math.round(k * 4F) / 4F));
    }

    /** the one colour of the strip and the hotbar band: the theme's panel colour (or her own), at barOpacity % */
    static int solid(DPHudThemes.Style st) {
        DPHudSettings S = DPHudSettings.get();
        int rgb = S.barColor >= 0 ? S.barColor & 0xFFFFFF : st.panelBottom & 0xFFFFFF;
        return ((int) (255 * Math.max(0, Math.min(100, S.barOpacity)) / 100F) << 24) | rgb;
    }

    /** card/panel backgrounds: with the one-colour bar every theme's cards are as see-through as the bar (her "50/50 for all the themes") */
    private static int bg(int argb) {
        DPHudSettings S = DPHudSettings.get();
        float k = S.solidBar ? Math.max(0, Math.min(100, S.cardOpacity >= 0 ? S.cardOpacity : S.barOpacity)) / 100F : DPConfig.hudOpacity / 100F;
        return ((int) (((argb >>> 24) & 0xFF) * k) << 24) | (argb & 0xFFFFFF);
    }

    private static void drawPageDots(int cx, int y, int pages, int page) {
        int w = pages * 6;
        for (int i = 0; i < pages; i++) {
            int x = cx - w / 2 + i * 6;
            Gui.drawRect(x, y, x + 4, y + 2, i == page ? PrideFrame.PINK : 0x70FFFFFF);
        }
    }

    private final Capture probeView = new Capture("probe", "probe");

    private void draw(ScaledResolution res) {
        Minecraft mc = Minecraft.getMinecraft();
        STRIP_H = Math.max(40, Math.min(96, DPHudSettings.get().stripHeight));
        float k = scale(res);
        int RW = res.getScaledWidth(), RH = res.getScaledHeight();
        // full-screen effects go back where they were, at the real size, under the strip
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        for (Capture c : captures.values())
            if (c.full && c.fbo != null && frame - c.lastDraw < 3) blit(c, 0, 0, c.fbo.framebufferWidth, c.fbo.framebufferHeight, 0, 0, RW, RH, 1F);
        if (probeDrawn == frame && probeFbo != null) {                 // an idle mod being re-tested: show it as if nothing happened
            probeView.fbo = probeFbo;
            blit(probeView, 0, 0, probeFbo.framebufferWidth, probeFbo.framebufferHeight, 0, 0, RW, RH, 1F);
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(k, k, 1F);
        try { drawScaled(mc, (int) (RW / k), (int) (RH / k), frame - hotbarScaledFrame <= 1 ? HOTBAR_H : HOTBAR_H / k); } finally { GlStateManager.popMatrix(); }
    }

    // ------------------------------------------------------------------ the hotbar grows with the strip, chat moves above it
    private long hotbarScaledFrame = -10;
    private boolean hotbarPushed;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hotbarPre(RenderGameOverlayEvent.Pre e) {
        if (e.getType() != ElementType.HOTBAR || e.isCanceled() || broken || !active() || !DPConfig.hudScaleHotbar || hotbarPushed) return;
        float k = scale(e.getResolution());
        if (k == 1F) return;
        int W = e.getResolution().getScaledWidth(), H = e.getResolution().getScaledHeight();
        GlStateManager.pushMatrix();
        GlStateManager.translate(W / 2F, H, 0F);
        GlStateManager.scale(k, k, 1F);
        GlStateManager.translate(-W / 2F, -H, 0F);
        hotbarPushed = true;
        hotbarScaledFrame = frame;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hotbarPost(RenderGameOverlayEvent.Post e) {
        if (e.getType() == ElementType.HOTBAR && hotbarPushed) { GlStateManager.popMatrix(); hotbarPushed = false; }
    }

    @SubscribeEvent
    public void chat(RenderGameOverlayEvent.Chat e) {
        if (broken || !active() || !DPHudSettings.get().chatAbove || DPHudSettings.get().stripAtTop) return;
        float k = scale(e.getResolution());
        float hb = frame - hotbarScaledFrame <= 1 ? HOTBAR_H * k : HOTBAR_H;
        e.setPosY((int) (e.getResolution().getScaledHeight() - hb - STRIP_H * k - 6));
    }

    private long lastAutoPage;
    private final Map<Object, Long> panelSeen = new java.util.IdentityHashMap<Object, Long>();
    private final Map<Object, Integer> stickyW = new java.util.IdentityHashMap<Object, Integer>();
    private final Map<Object, Long> stickyAt = new java.util.IdentityHashMap<Object, Long>();

    /** a card's width only grows while it is on the strip (text getting shorter used to slide every other card over) */
    private int sticky(Object card, int w) {
        Long at = stickyAt.get(card);
        Integer was = stickyW.get(card);
        int out = was != null && at != null && frame - at < HOLD ? Math.max(was, w) : w;
        stickyW.put(card, out); stickyAt.put(card, frame);
        return out;
    }

    private void drawScaled(Minecraft mc, int W, int H, float hotbar) {
        DPHudSettings S = DPHudSettings.get();
        if (S.hideInMenus && mc.currentScreen != null) return;
        if (S.hideThirdPerson && mc.gameSettings.thirdPersonView != 0) return;
        if (S.hideCreative && mc.player.isCreative()) return;
        // a narrower strip sits in the middle of the screen (stripWidth %)
        int fullW = W;
        W = Math.max(VAN_W + 40, Math.min(fullW, fullW * Math.max(30, Math.min(100, S.stripWidth)) / 100));
        int ox = (fullW - W) / 2;
        GlStateManager.pushMatrix();
        GlStateManager.translate(ox, 0, 0);
        try { drawStrip(mc, S, W, H, hotbar); } finally { GlStateManager.popMatrix(); }
    }

    private void drawStrip(Minecraft mc, DPHudSettings S, int W, int H, float hotbar) {
        int bottom = S.stripAtTop ? STRIP_H + 2 : (int) (H - hotbar), top = bottom - STRIP_H;
        long t = DPAnim.now();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GlStateManager.color(1F, 1F, 1F, 1F);

        // ---- the panel: same dark gradient + rainbow band as every Pride menu, the band shimmering slowly
        DPHudThemes.Style st = DPHudThemes.style(DPConfig.hudTheme);
        style = st;
        if (S.solidBar) Gui.drawRect(0, top, W, bottom, solid(st));
        else PrideFrame.gradient(0, top, W, bottom, bg(st.panelTop), bg(st.panelBottom));
        if (S.decorations) DPHudThemes.decorate(st, 0, top, W, STRIP_H, S.animateDecorations ? t : 0L);   // every theme keeps its look; still unless she turns motion on
        GlStateManager.enableBlend();
        int n = PrideFrame.RAINBOW.length, sw = Math.max(1, W / n);
        for (int i = 0; i < n; i++) {
            if (!S.rainbowBand) break;
            float wave = S.bandShimmer ? 0.82F + 0.18F * (float) Math.sin(t / 600.0 - i * 0.7) : 1F;
            int col = st.rainbow ? PrideFrame.RAINBOW[i] : (i % 2 == 0 ? st.xpFrom : st.xpTo);
            Gui.drawRect(i * sw, top, i == n - 1 ? W : (i + 1) * sw, top + 2, shade(col, wave));
        }
        if (!S.solidBar) {
            Gui.drawRect(0, top + 2, W, top + 3, 0x30FFFFFF);
            Gui.drawRect(0, bottom - 1, W, bottom, 0x50000000);
        }

        // taking damage: a soft red wash over the panel
        EntityPlayerSP p = mc.player;
        hurtFlash = p.hurtTime > 0 ? Math.min(1F, hurtFlash + 0.35F) : Math.max(0F, hurtFlash - 0.06F);
        if (hurtFlash > 0 && S.hurtFlash) Gui.drawRect(0, top + 3, W, bottom, ((int) (hurtFlash * 70) << 24) | 0xE02040);
        if (S.lowHealthGlow && p.getHealth() <= 6 && !p.isCreative()) {
            int a2 = (int) (40 + 40 * Math.sin(t / 160.0));
            PrideFrame.gradient(0, top + 3, W, top + 12, (a2 << 24) | 0xE02040, 0x00E02040);
        }

        int cx = W / 2;
        // any screen size: the side panels give way first on narrow screens, the cards shrink to whatever room is left
        boolean panels = W >= VAN_W + LEFT_W + RIGHT_W + 120;
        boolean showL = panels && DPConfig.hudShowPlayer, showR = panels && DPConfig.hudShowWorld;
        int leftW = showL ? LEFT_W : 0, rightW = showR ? RIGHT_W : 0;
        if (showL) drawPlayerPanel(mc, PAD, top + PAD, LEFT_W, STRIP_H - 2 * PAD);
        if (showR) drawWorldPanel(mc, W - RIGHT_W - PAD, top + PAD, RIGHT_W, STRIP_H - 2 * PAD);
        int vanW = S.middleCard ? VAN_W : 0;
        if (S.middleCard) drawVanilla(mc, cx - VAN_W / 2, top + PAD, VAN_W, STRIP_H - 2 * PAD, t);

        // ---- mod cards: alternate right / left of the middle, working outwards
        List<Capture> show = new ArrayList<Capture>();
        for (Capture c : captures.values())
            if (S.modCards && !c.full && !c.empty() && c.fbo != null && frame - c.lastDraw < HOLD && !passthrough(c.modid)) { show.add(c); c.shownAt = frame; }
        int innerH = STRIP_H - 2 * PAD - LABEL_H - 2;
        // cards = PrideHUD's own panels (they read mod data: Mine and Slash...) first, then every captured mod overlay
        if (DPHudPanels.ALL.isEmpty() && !panelsInit) { panelsInit = true; DPHudPanels.init(); }
        java.util.List<Object> cards = new java.util.ArrayList<Object>();
        java.util.List<Integer> widthList = new java.util.ArrayList<Integer>();
        java.util.List<Float> scaleList = new java.util.ArrayList<Float>();
        int total = 0;
        for (DPHudPanels.Panel pnl : DPHudPanels.ALL) {
            int pw = 0;
            try { pw = pnl.width(mc); } catch (Throwable ex) { pw = 0; }
            if (pw > 0) panelSeen.put(pnl, frame);
            Long seen = panelSeen.get(pnl);
            if (seen == null || frame - seen >= HOLD) { stickyW.remove(pnl); continue; }   // gone for a while: card leaves
            pw = sticky(pnl, pw);
            cards.add(pnl); widthList.add(pw); scaleList.add(1F); total += pw + PAD;
        }
        int sq = STRIP_H - 2 * PAD - 4;
        Capture mapCap = null;                          // her 2026-10-05: the minimap sits big, just above the strip on the right
        for (Capture c : show) {
            if (S.mapAbove && mapCap == null && isMap(c)) { mapCap = c; continue; }
            float gw = (c.x1 - c.x0) / (float) capRes, gh = (c.y1 - c.y0) / (float) capRes;
            if (tall(c)) {                              // a minimap: the map fills a square, its words go beside it (requested feature)
                float capH = gh - gw, capS = Math.min(1F, sq / Math.max(1F, capH));
                int cw0 = sticky(c, sq + 4 + (int) Math.ceil(gw * capS) + 6);
                cards.add(c); widthList.add(cw0); scaleList.add(capS); total += cw0 + PAD;
                continue;
            }
            float sc = Math.min(1F, innerH / gh);
            int cw0 = sticky(c, Math.max(Math.max(34, Minecraft.getMinecraft().fontRenderer.getStringWidth(c.name) / 2 + 14), (int) Math.ceil(gw * sc) + 2 * PAD + 2));
            cards.add(c); widthList.add(cw0); scaleList.add(sc); total += cw0 + PAD;
        }
        int gap = Math.max(0, Math.min(16, S.cardGap));
        int avail = W - vanW - leftW - rightW - 8 * PAD;
        // too many cards: shrink a little (to 80 %), then split into pages flipped with the arrow keys (requested feature)
        float shrink = total > avail && total > 0 ? Math.max(0.8F, avail / (float) total) : 1F;
        java.util.List<Integer> pageStart = new java.util.ArrayList<Integer>();
        pageStart.add(0);
        int used = 0;
        for (int i = 0; i < cards.size(); i++) {
            int cw = Math.max(18, (int) (widthList.get(i) * shrink)) + gap;
            if (used + cw > avail * (S.cardSide == 0 ? 1 : 0.5F) && used > 0) { pageStart.add(i); used = 0; }
            used += cw;
        }
        pages = pageStart.size();
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        int from = pageStart.get(page), to = page + 1 < pages ? pageStart.get(page + 1) : cards.size();
        if (pages > 1 && S.autoPageSec > 0 && System.currentTimeMillis() - lastAutoPage > S.autoPageSec * 1000L) { lastAutoPage = System.currentTimeMillis(); page = (page + 1) % pages; }
        if (pages > 1 && S.pageDots) drawPageDots(cx, bottom - 3, pages, page);
        int rightX = cx + vanW / 2 + PAD, leftX = cx - vanW / 2 - PAD;
        FontRenderer fr = mc.fontRenderer;
        for (int i = from; i < to; i++) {
            Object card = cards.get(i);
            float a = S.cardFade ? DPAnim.approach(card, 1F, 4F) : 1F;   // fades in when it appears
            int cw = Math.max(18, (int) (widthList.get(i) * shrink));
            float s = scaleList.get(i) * shrink;
            int x;
            boolean right = S.cardSide == 1 || (S.cardSide == 0 && (i - from) % 2 == 0);   // 0 both sides, 1 right only, 2 left only
            if (right) { x = rightX; rightX += cw + gap; } else { leftX -= cw + gap; x = leftX + gap; }
            int y = top + PAD, ch = STRIP_H - 2 * PAD;
            String name = card instanceof Capture ? ((Capture) card).name : ((DPHudPanels.Panel) card).title();
            int accent = card instanceof Capture ? PrideFrame.RAINBOW[(((Capture) card).modid.hashCode() & 0x7FFFFFFF) % n] : ((DPHudPanels.Panel) card).accent();
            card(x, y, cw, ch, alpha(accent, a));
            if (card instanceof Capture && tall((Capture) card)) {
                Capture c = (Capture) card;
                int side = c.x1 - c.x0;                                   // map = the top square of what the mod drew
                blit(c, c.x0, c.y0, c.x1, c.y0 + side, x + 2, y + 2, x + 2 + sq, y + 2 + sq, a);
                float capS = scaleList.get(i) * shrink, capW = side / (float) capRes * capS, capH = (c.y1 - c.y0 - side) / (float) capRes * capS;
                float cx0 = x + sq + 6, cy0 = y + 2 + (sq - capH) / 2F;
                blit(c, c.x0, c.y0 + side, c.x1, c.y1, cx0, cy0, cx0 + capW, cy0 + capH, a);
                continue;
            }
            if (S.modNames) {
                Gui.drawRect(x + 3, y + 4, x + 6, y + 7, alpha(accent, a));
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + 8, y + 4, 0);
                GlStateManager.scale(0.5F, 0.5F, 1F);
                fr.drawStringWithShadow(fr.trimStringToWidth(name, (cw - 10) * 2), 0, 0, alpha(0xFFFFD6E0, a) & 0xFFFFFFFF);
                GlStateManager.popMatrix();
            }
            if (card instanceof Capture) {
                Capture c = (Capture) card;
                float gw = (c.x1 - c.x0) / (float) capRes * s, gh = (c.y1 - c.y0) / (float) capRes * s;
                float dx = x + (cw - gw) / 2F, dy = y + LABEL_H + 2 + (innerH - gh) / 2F;
                blit(c, c.x0, c.y0, c.x1, c.y1, dx, dy, dx + gw, dy + gh, a);
            } else {
                try { ((DPHudPanels.Panel) card).draw(mc, x, y, cw, ch, a); } catch (Throwable ex) { /* a panel must never break the HUD */ }
                GlStateManager.color(1F, 1F, 1F, 1F);
            }
        }
        if (mapCap != null) drawMapAbove(mapCap, W, H, S.stripAtTop ? bottom : top);
        GlStateManager.enableDepth();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static final Set<String> MAP_MODS = new HashSet<String>(java.util.Arrays.asList(
            "realmcoin", "xaerominimap", "xaerominimapfair", "journeymap", "voxelmap", "mapwriter", "antiqueatlas", "pridemap"));

    /** a minimap: a known map mod drawing a mostly-square picture, or anything shaped like map + a few lines of words */
    private static boolean isMap(Capture c) {
        int w = c.x1 - c.x0, h = c.y1 - c.y0;
        if (w < 40 * capRes / 2 || h < w * 0.9F) return false;
        return MAP_MODS.contains(c.modid) ? h < w * 2.4F : w >= 80 * capRes / 2 && h > w * 1.2F && h < w * 2.2F;
    }

    /** the map square (and the words under it) scaled up to mapSize % of the screen height, bottom-right above the strip */
    private void drawMapAbove(Capture c, int W, int H, int top) {
        DPHudSettings S = DPHudSettings.get();
        int side = Math.min(c.x1 - c.x0, c.y1 - c.y0);
        float native_ = side / (float) capRes;
        float size = Math.max(40F, H * Math.max(10, Math.min(60, S.mapSize)) / 100F);
        float k = size / Math.max(1F, native_);
        float wordsH = (c.y1 - c.y0 - side) / (float) capRes * Math.min(k, 1.5F);
        float wordsW = (c.x1 - c.x0) / (float) capRes * Math.min(k, 1.5F);
        float total = size + (wordsH > 0 ? wordsH + 3 : 0);
        float x0 = S.mapLeft ? PAD + 2 : W - PAD - 2 - size, x1 = x0 + size;
        float y0 = S.stripAtTop ? top + PAD + 2 : top - PAD - 2 - total, y1 = y0 + total;
        float ma = Math.max(10, Math.min(100, S.mapOpacity)) / 100F;
        if (S.mapBorder) card((int) x0 - 3, (int) y0 - 3, (int) size + 6, (int) (y1 - y0) + 6, PrideFrame.PINK);
        blit(c, c.x0, c.y0, c.x0 + side, c.y0 + side, x0, y0, x1, y0 + size, ma);
        if (wordsH > 0) {
            float wx = x0 + (size - Math.min(size, wordsW)) / 2F, ww = Math.min(size, wordsW), wh = wordsH * (ww / Math.max(1F, wordsW));
            if (S.mapWords) blit(c, c.x0, c.y0 + side, c.x1, c.y1, wx, y0 + size + 3, wx + ww, y0 + size + 3 + wh, ma);
        }
    }

    // ------------------------------------------------------------------ the hotbar wears the HUD theme (requested feature)
    // PrideInventory's wide hotbar calls these by reflection, so both mods stay optional to each other.

    /** the band behind the hotbar: same colours and decorations as the strip above it */
    public static void hotbarBand(int x0, int y, int w, int h) {
        DPHudThemes.Style st = DPHudThemes.style(DPConfig.hudTheme);
        GlStateManager.enableBlend();
        DPHudSettings S = DPHudSettings.get();
        if (S.solidBar) Gui.drawRect(x0, y, x0 + w, y + h, solid(st));   // same single colour as the strip
        else PrideFrame.gradient(x0, y, x0 + w, y + h, bg(st.panelBottom), bg(st.panelBottom));
        if (S.decorations) DPHudThemes.decorate(st, x0, y, w, h, S.animateDecorations ? DPAnim.now() : 0L);
        GlStateManager.enableBlend();
    }

    /** one hotbar slot: a soft rounded square in the card colours; the selected one glows in the theme's accent */
    public static void hotbarSlot(int x, int y, int size, boolean selected) {
        DPHudThemes.Style st = DPHudThemes.style(DPConfig.hudTheme);
        float[] box = DPDraw.rrect(x + 1, y + 1, size - 2, size - 2, 4);
        if (selected) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(DPAnim.now() / 250.0);
            DPDraw.fill(DPDraw.rrect(x - 1, y - 1, size + 2, size + 2, 5), (st.xpTo & 0xFFFFFF) | ((int) (0x90 * pulse) << 24));
        }
        DPDraw.fillGrad(box, bg(st.cardTop), bg(st.cardBottom), y, y + size);
        DPDraw.stroke(box, selected ? 0xFFFFFFFF : 0x55FFFFFF, selected ? 2F : 1F);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** without PrideInventory: Minecraft's own 9-slot hotbar, drawn in the theme */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void themedHotbar(RenderGameOverlayEvent.Pre e) {
        if (e.getType() != ElementType.HOTBAR || e.isCanceled() || broken || !active() || !DPConfig.hudThemeHotbar || Loader.isModLoaded("prideinventory")) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || p.isSpectator()) return;
        e.setCanceled(true);
        int W = e.getResolution().getScaledWidth(), H = e.getResolution().getScaledHeight(), y = H - 22, x0 = W / 2 - 91;
        hotbarBand(0, y - 1, W, 23);
        for (int i = 0; i < 9; i++) hotbarSlot(x0 + 1 + i * 20, y + 1, 20, i == p.inventory.currentItem);
        net.minecraft.item.ItemStack off = p.getHeldItemOffhand();
        if (!off.isEmpty()) hotbarSlot(x0 - 26, y + 1, 20, false);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack s = p.inventory.mainInventory.get(i);
            if (s.isEmpty()) continue;
            mc.getRenderItem().renderItemAndEffectIntoGUI(p, s, x0 + 3 + i * 20, y + 3);
            mc.getRenderItem().renderItemOverlays(mc.fontRenderer, s, x0 + 3 + i * 20, y + 3);
        }
        if (!off.isEmpty()) { mc.getRenderItem().renderItemAndEffectIntoGUI(p, off, x0 - 24, y + 3); mc.getRenderItem().renderItemOverlays(mc.fontRenderer, off, x0 - 24, y + 3); }
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        mc.getTextureManager().bindTexture(Gui.ICONS);
    }

    // ------------------------------------------------------------------ for the HUD Settings screen

    /** every mod that hooks the HUD: {modid, name, "drawing" | "idle"} */
    static java.util.List<String[]> modList() {
        java.util.List<String[]> l = new java.util.ArrayList<String[]>();
        if (INSTANCE == null) return l;
        for (Capture c : INSTANCE.captures.values()) l.add(new String[]{ c.modid, c.name, !c.idle && INSTANCE.frame - c.lastDraw < 30 ? "drawing" : "idle" });
        l.sort((a, b) -> a[2].equals(b[2]) ? a[1].compareToIgnoreCase(b[1]) : a[2].equals("drawing") ? -1 : 1);
        return l;
    }

    /** {buffers, MB of graphics memory} the HUD holds right now */
    static long[] memory() {
        if (INSTANCE == null) return new long[]{ 0, 0 };
        long n = 0, bytes = 0;
        for (Capture x : INSTANCE.captures.values()) if (x.fbo != null && x.fbo != INSTANCE.probeFbo) { n++; bytes += 4L * x.fbo.framebufferWidth * x.fbo.framebufferHeight; }
        if (INSTANCE.probeFbo != null) { n++; bytes += 4L * INSTANCE.probeFbo.framebufferWidth * INSTANCE.probeFbo.framebufferHeight; }
        if (INSTANCE.sharedDepth >= 0) bytes += 4L * INSTANCE.depthW * INSTANCE.depthH;
        return new long[]{ n, bytes >> 20 };
    }

    /** a capture much taller than wide with a square on top = a minimap with text under it */
    private static boolean tall(Capture c) { if (!DPHudSettings.get().mapSquare) return false; int w = c.x1 - c.x0, h = c.y1 - c.y0; return w > 20 && h > w * 1.25F; }

    private static int shade(int argb, float k) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * k)), g = Math.min(255, (int) (((argb >> 8) & 0xFF) * k)), b = Math.min(255, (int) ((argb & 0xFF) * k));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int argb, float a) {
        return ((int) (((argb >>> 24) & 0xFF) * a) << 24) | (argb & 0xFFFFFF);
    }

    /** a small rounded-looking card in the panel */
    private static void card(int x, int y, int w, int h, int accent) {
        // rounded look: body without the 4 corner pixels, soft gradient, accent top line with a glow under it
        PrideFrame.gradient(x + 1, y, x + w - 1, y + h, bg(style.cardTop), bg(style.cardBottom));
        PrideFrame.gradient(x, y + 1, x + 1, y + h - 1, bg(style.cardTop), bg(style.cardBottom));
        PrideFrame.gradient(x + w - 1, y + 1, x + w, y + h - 1, bg(style.cardTop), bg(style.cardBottom));
        Gui.drawRect(x + 1, y, x + w - 1, y + 2, accent);
        PrideFrame.gradient(x + 1, y + 2, x + w - 1, y + 6, (accent & 0xFFFFFF) | 0x40000000, accent & 0xFFFFFF);
        Gui.drawRect(x + 1, y + h - 1, x + w - 1, y + h, CARD_EDGE);
    }

    /** a filled bar with a light top edge */
    private static void bar(int x, int y, int w, int h, float pct, int from, int to, int back) {
        Gui.drawRect(x, y, x + w, y + h, back);
        int f = (int) (w * Math.max(0F, Math.min(1F, pct)));
        if (f > 0) { PrideFrame.gradient(x, y, x + f, y + h, from, to); Gui.drawRect(x, y, x + f, y + 1, 0x50FFFFFF); }
    }

    static void small(FontRenderer fr, String s, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        float k = Math.max(0.4F, Math.min(1F, DPHudSettings.get().smallTextScale / 100F));   // her 10-05: "make the text bigger"
        GlStateManager.scale(k, k, 1F);
        if (DPHudSettings.get().textShadow) fr.drawStringWithShadow(s, 0, 0, color); else fr.drawString(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    /** left end: your face, name, level and game mode */
    private void drawPlayerPanel(Minecraft mc, int x, int y, int w, int h) {
        card(x, y, w, h, PrideFrame.PINK);
        EntityPlayerSP p = mc.player;
        int face = h - 10;
        net.minecraft.client.network.NetworkPlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.getUniqueID());
        DPHudSettings S = DPHudSettings.get();
        if (!S.pFace) face = -4;
        if (info != null && S.pFace) {
            GlStateManager.color(1F, 1F, 1F, 1F);
            mc.getTextureManager().bindTexture(info.getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(x + 4, y + 6, 8, 8, 8, 8, face, face, 64, 64);
            Gui.drawScaledCustomSizeModalRect(x + 4, y + 6, 40, 8, 8, 8, face, face, 64, 64);
        }
        FontRenderer fr = mc.fontRenderer;
        int tx = x + face + 8;
        if (S.pName) fr.drawStringWithShadow(fr.trimStringToWidth(p.getName(), w - face - 10), tx, y + 6, 0xFFFFFF);
        String mode = mc.playerController.isInCreativeMode() ? "Creative" : mc.playerController.isSpectatorMode() ? "Spectator"
                : mc.playerController.gameIsSurvivalOrAdventure() && !mc.playerController.shouldDrawHUD() ? "Adventure" : "Survival";
        small(fr, (S.pLevel ? "§dLv " + p.experienceLevel + "  " : "") + (S.pMode ? "§7" + mode : ""), tx, y + 17, 0xFFFFFF);
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String facing = dirs[MathHelper.floor((MathHelper.wrapDegrees(p.rotationYaw) + 202.5F) / 45F) & 7];
        if (S.pFacing) small(fr, "§b➤ §f" + facing + " §7" + Math.round(MathHelper.wrapDegrees(p.rotationYaw)) + "°", tx, y + 30, 0xFFFFFF);
        float hpPct = p.getHealth() / Math.max(1F, p.getMaxHealth());
        if (S.pHealthBar) bar(tx, y + 24, x + w - 4 - tx, 3, hpPct, 0xFFFF5070, 0xFFE0386A, 0xFF2A0A12);
        mc.getTextureManager().bindTexture(Gui.ICONS);
    }

    /** right end: time, day, where you are, how fast the game runs */
    private void drawWorldPanel(Minecraft mc, int x, int y, int w, int h) {
        card(x, y, w, h, PrideFrame.BLUE);
        FontRenderer fr = mc.fontRenderer;
        long time = mc.world.getWorldTime();
        int hours = (int) ((time / 1000 + 6) % 24), mins = (int) ((time % 1000) * 60 / 1000);
        boolean night = hours < 6 || hours >= 19;
        String clock = DPConfig.hudClock24 ? String.format(Locale.ROOT, "%s %02d:%02d", night ? "☾" : "☀", hours, mins)
                : String.format(Locale.ROOT, "%s %d:%02d %s", night ? "☾" : "☀", hours % 12 == 0 ? 12 : hours % 12, mins, hours < 12 ? "AM" : "PM");
        DPHudSettings S = DPHudSettings.get();
        if (S.wClock) fr.drawStringWithShadow(clock, x + 4, y + 5, night ? 0xB8C8FF : 0xFFE680);
        if (S.wDay) small(fr, "§7Day §f" + (time / 24000 + 1), x + w - 4 - fr.getStringWidth("Day " + (time / 24000 + 1)) / 2F, y + 7, 0xFFFFFF);
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(mc.player);
        java.util.List<String> lines = new java.util.ArrayList<String>();
        if (S.wCoords) lines.add("§bXYZ §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        if (S.wBiome) lines.add("§d✿ §f" + fr.trimStringToWidth(mc.world.getBiome(pos).getBiomeName(), (w - 12) * 2));
        StringBuilder last = new StringBuilder();
        int fps = Minecraft.getDebugFPS();
        if (S.wFps) last.append(fps >= 60 ? "§a" : fps >= 30 ? "§e" : "§c").append(fps).append(" §7fps  ");
        if (S.wWeather) last.append(mc.world.isThundering() ? "§9⛈ storm  " : mc.world.isRaining() ? "§b☂ rain  " : "§e☀ clear  ");
        if (S.wLight) { int light = mc.world.getLightFromNeighbors(pos); last.append("§7☼ ").append(light < 8 ? "§c" : "§f").append(light); }
        if (last.length() > 0) lines.add(last.toString());
        StringBuilder extra = new StringBuilder();
        if (S.wDimension) extra.append("§5◆ §f").append(mc.world.provider.getDimensionType().getName()).append("  ");
        if (S.wPing && mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUniqueID()) != null)
            extra.append("§7ping §f").append(mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getResponseTime()).append("ms");
        if (extra.length() > 0) lines.add(extra.toString());
        int ly = y + 17;
        float lh = 9 * Math.max(0.4F, Math.min(1F, S.smallTextScale / 100F)) + 1.5F;
        for (String l : lines) { if (ly > y + h - 12) break; small(fr, l, x + 4, ly, 0xFFFFFF); ly += lh; }
        // the day as a bar: sunrise -> noon -> sunset -> midnight, with the sun or moon riding along it
        if (S.wDayBar) {
            int bx = x + 4, bw = w - 8, by = y + h - 6;
            long t = time % 24000L;
            Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 4, 0x90000000);
            int half = bw / 2;
            PrideFrame.gradient(bx, by, bx + half, by + 3, 0xFFFFE070, 0xFFFF9A3C);
            PrideFrame.gradient(bx + half, by, bx + bw, by + 3, 0xFF5060C0, 0xFF24408E);
            int mx = bx + (int) (bw * t / 24000F);
            Gui.drawRect(mx - 1, by - 2, mx + 2, by + 5, night ? 0xFFDDE4FF : 0xFFFFFFFF);
        }
        mc.getTextureManager().bindTexture(Gui.ICONS);
    }

    /** draw capture pixels (sx0,sy0)-(sx1,sy1) (top-left origin) into GUI rect (x0,y0)-(x1,y1) */
    private void blit(Capture c, float sx0, float sy0, float sx1, float sy1, float x0, float y0, float x1, float y1, float a) {
        Framebuffer f = c.fbo;
        float tw = f.framebufferTextureWidth, th = f.framebufferTextureHeight, fh = f.framebufferHeight;
        float u0 = sx0 / tw, u1 = sx1 / tw, vTop = (fh - sy0) / th, vBot = (fh - sy1) / th;
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, a);
        GlStateManager.bindTexture(f.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x0, y1, 0).tex(u0, vBot).endVertex();
        b.pos(x1, y1, 0).tex(u1, vBot).endVertex();
        b.pos(x1, y0, 0).tex(u1, vTop).endVertex();
        b.pos(x0, y0, 0).tex(u0, vTop).endVertex();
        t.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ Minecraft's own bars, in the middle

    private void drawVanilla(Minecraft mc, int x, int y, int w, int h, long t) {
        // a chosen theme draws the whole middle card (her 16 designs, Esc menu > HUD Theme)
        if (!"minecraft".equals(DPConfig.hudTheme) && !mc.playerController.isSpectatorMode()
                && DPHudThemes.draw(DPConfig.hudTheme, DPHudThemes.fromPlayer(mc), x, y, w, h, t)) {
            mc.getTextureManager().bindTexture(Gui.ICONS);
            return;
        }
        EntityPlayerSP p = mc.player;
        FontRenderer fr = mc.fontRenderer;
        card(x, y, w, h, 0xFFFFFFFF);
        Gui g = mc.ingameGUI;
        int half = w / 2;

        // row 1: XP bar with a glowing level badge in the middle
        int barX = x + 4, barW = w - 8, barY = y + 5;
        Gui.drawRect(barX, barY, barX + barW, barY + 4, 0xFF0E1A0A);
        int filled = (int) (p.experience * barW);
        PrideFrame.gradient(barX, barY, barX + filled, barY + 4, 0xFFA8FF60, 0xFF4CB020);
        Gui.drawRect(barX, barY, barX + filled, barY + 1, 0x60FFFFFF);
        String lv = String.valueOf(p.experienceLevel);
        int lw = fr.getStringWidth(lv) + 6, lx = x + (w - lw) / 2;
        float glow = 0.6F + 0.4F * (float) Math.sin(t / 400.0);
        Gui.drawRect(lx - 1, barY - 3, lx + lw + 1, barY + 7, alpha(0xFF80FF20, 0.35F * glow));
        Gui.drawRect(lx, barY - 2, lx + lw, barY + 6, 0xFF14240C);
        fr.drawStringWithShadow(lv, lx + 3, barY - 1, 0x80FF20);

        mc.getTextureManager().bindTexture(Gui.ICONS);
        GlStateManager.enableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (!mc.playerController.shouldDrawHUD()) {
            // creative/spectator: Minecraft hides hearts and food, but she still wants to see them (2026-10-04)
            float hpPct = Math.min(1F, p.getHealth() / Math.max(1F, p.getMaxHealth()));
            float foodPct = p.getFoodStats().getFoodLevel() / 20F, satPct = Math.min(1F, p.getFoodStats().getSaturationLevel() / 20F);
            int bw = half - 8, by = y + 15;
            bar(x + 4, by, bw, 8, hpPct, 0xFFFF5070, 0xFFB01030, 0xFF3A0A12);
            fr.drawStringWithShadow("❤ " + (int) Math.ceil(p.getHealth()) + "/" + (int) p.getMaxHealth(), x + 6, by, 0xFFFFFF);
            bar(x + half + 4, by, bw, 8, foodPct, 0xFFFFC060, 0xFFC07010, 0xFF2A1A06);
            Gui.drawRect(x + half + 4, by + 7, x + half + 4 + (int) (bw * satPct), by + 8, 0xFFFFF080);
            fr.drawStringWithShadow("🍗 " + p.getFoodStats().getFoodLevel() + "  +" + (int) p.getFoodStats().getSaturationLevel(), x + half + 6, by, 0xFFFFFF);
            int armor = p.getTotalArmorValue();
            String mode = mc.playerController.isSpectatorMode() ? "§bSpectator" : "§dCreative";
            fr.drawStringWithShadow(mode + (armor > 0 ? "  §7⛨ §f" + armor : "") + (p.capabilities.isFlying ? "  §7flying" : ""), x + 6, y + 28, 0xFFFFFF);
            return;
        }
        int rowA = y + 13, rowB = y + 23, rowC = y + 33;

        // hearts (left) — bars + numbers when the pool is too big for two rows; they shake when you're low
        float max = (float) p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getAttributeValue();
        float hp = p.getHealth(), abs = p.getAbsorptionAmount();
        int hearts = MathHelper.ceil((max + abs) / 2F);
        boolean low = hp <= 6;
        if (low) Gui.drawRect(x + 2, rowA - 1, x + half - 2, rowA + (hearts > 10 ? 19 : 9), alpha(0xFFE02040, 0.18F + 0.12F * (float) Math.sin(t / 120.0)));
        boolean vit = vitalsMode() && vitals.fbo != null && frame - vitals.lastDraw < 3;   // only reached with the "minecraft" theme
        if (vit) {
            placeVitals(hx0, hy0, hx1, hy1, x + 4, rowA - 1, half - 8, 21, false);
            for (Capture c : captures.values()) if (c.zl != null && c.fbo != null && frame - c.lastDraw < 3) placeZone(c, c.zl, x + 4, rowA - 1, half - 8, 21, false);
        } else if (hearts <= 20) {
            int poison = p.isPotionActive(MobEffects.POISON) ? 36 : p.isPotionActive(MobEffects.WITHER) ? 72 : 0;
            for (int i = 0; i < hearts; i++) {
                int hx = x + 4 + (i % 10) * 8, hy = (i < 10 ? rowA : rowB) + (low ? (int) (Math.sin(t / 60.0 + i) * 1.2) : 0);
                g.drawTexturedModalRect(hx, hy, 16, 0, 9, 9);
                int v = i * 2 + 1;
                if (i * 2 >= max) g.drawTexturedModalRect(hx, hy, (i * 2 - max + 1 < abs) ? 160 : 169, 0, 9, 9);
                else if (v < hp) g.drawTexturedModalRect(hx, hy, 52 + poison, 0, 9, 9);
                else if (v == (int) Math.ceil(hp) && hp % 2 != 0) g.drawTexturedModalRect(hx, hy, 61 + poison, 0, 9, 9);
            }
        } else {
            Gui.drawRect(x + 4, rowA + 1, x + half - 4, rowA + 8, 0xFF3A0A0A);
            PrideFrame.gradient(x + 4, rowA + 1, x + 4 + (int) ((half - 8) * Math.min(1F, hp / max)), rowA + 8, 0xFFFF5070, 0xFFB01030);
            fr.drawStringWithShadow((int) Math.ceil(hp) + (abs > 0 ? "+" + (int) abs : "") + "/" + (int) max, x + 6, rowA, 0xFFFFFF);
        }
        mc.getTextureManager().bindTexture(Gui.ICONS);
        GlStateManager.color(1F, 1F, 1F, 1F);
        // armour (left, row C) + its number
        int armor = p.getTotalArmorValue();
        if (armor == 0) GlStateManager.color(1F, 1F, 1F, 0.35F);           // no armour: faint outlines so the row never looks empty
        for (int i = 0; i < 10; i++) g.drawTexturedModalRect(x + 4 + i * 8, rowC, i * 2 + 1 < armor ? 34 : i * 2 + 1 == armor ? 25 : 16, 9, 9, 9);
        GlStateManager.color(1F, 1F, 1F, 1F);
        small(fr, "§7⛨ §f" + armor, x + 86, rowC + 3, 0xFFFFFF);
        mc.getTextureManager().bindTexture(Gui.ICONS);
        // food (right, right-aligned like vanilla) + saturation number
        int food = p.getFoodStats().getFoodLevel();
        boolean hunger = p.isPotionActive(MobEffects.HUNGER);
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (vit) {
            placeVitals(fx0, fy0, fx1, fy1, x + half + 4, rowA - 1, half - 8, p.getRidingEntity() instanceof EntityLivingBase ? 21 : 31, true);   // food + thirst + bubbles
            for (Capture c : captures.values()) if (c.zr != null && c.fbo != null && frame - c.lastDraw < 3) placeZone(c, c.zr, x + half + 4, rowA - 1, half - 8, 31, true);
        }
        else for (int i = 0; i < 10; i++) {
            int fx = x + w - 4 - 9 - i * 8, fy = rowA + (food <= 4 ? (int) (Math.sin(t / 80.0 + i) * 1.2) : 0);
            g.drawTexturedModalRect(fx, fy, hunger ? 133 : 16, 27, 9, 9);
            int idx = i * 2 + 1;
            if (idx < food) g.drawTexturedModalRect(fx, fy, hunger ? 88 : 52, 27, 9, 9);
            else if (idx == food) g.drawTexturedModalRect(fx, fy, hunger ? 97 : 61, 27, 9, 9);
        }
        if (!vit) {
            small(fr, "§6🍗 " + food + " §e+" + (int) p.getFoodStats().getSaturationLevel() + " §7saturation", x + half + 4, rowB + 1, 0xFFFFFF);
            bar(x + half + 4, rowC + 3, half - 8, 3, Math.min(1F, p.getFoodStats().getSaturationLevel() / 20F), 0xFFFFF080, 0xFFE0B020, 0xFF2A2006);
        }
        mc.getTextureManager().bindTexture(Gui.ICONS);
        GlStateManager.color(1F, 1F, 1F, 1F);
        // bubbles (right, row B) while under water
        if (!vit && p.isInsideOfMaterial(Material.WATER)) {
            int air = p.getAir(), full = MathHelper.ceil((air - 2) * 10.0D / 300.0D), part = MathHelper.ceil(air * 10.0D / 300.0D) - full;
            for (int i = 0; i < full + part; i++) g.drawTexturedModalRect(x + w - 4 - 9 - i * 8, rowB, i < full ? 16 : 25, 18, 9, 9);
        }
        // the animal you ride (right, row C)
        if (p.getRidingEntity() instanceof EntityLivingBase) {
            EntityLivingBase m = (EntityLivingBase) p.getRidingEntity();
            int mh = (int) Math.ceil(m.getHealth()), mm = (int) m.getMaxHealth();
            Gui.drawRect(x + half + 2, rowC + 1, x + w - 4, rowC + 8, 0xFF3A0A0A);
            PrideFrame.gradient(x + half + 2, rowC + 1, x + half + 2 + (int) ((half - 6) * Math.min(1F, mh / (float) Math.max(1, mm))), rowC + 8, 0xFFFFA050, 0xFFC05010);
            small(fr, "§f" + mh + "/" + mm, x + half + 4, rowC + 3, 0xFFFFFF);
        }
        mc.getTextureManager().bindTexture(Gui.ICONS);
    }
}
