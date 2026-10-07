package com.dogpound.canvas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Inserts a call to com.dogpound.canvas.DPLoadingHook at the end of the loading
 * screen's draw, so the DogPound overlay paints on top. Only inserts a CALL
 * instruction (no class loading at transform time). Any failure returns the
 * original bytes unchanged — it can never break the game.
 */
public class DPTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        try {
            if ("net.minecraft.client.LoadingScreenRenderer".equals(transformedName)) {
                byte[] out = patchWorldLoad(basicClass);
                System.out.println("[DogPound] LoadingScreenRenderer patched for custom loading screen");
                return out;
            }
            if ("net.minecraftforge.fml.client.SplashProgress".equals(transformedName)) {
                return patchSplash(basicClass);
            }
            // The Runnable that runs Forge's splash render loop is the SplashProgress$2
            // anonymous inner class. We rewrite ITS run() to draw the DogPound boot
            // screen natively (replaces customloadingscreen + resourceloader).
            if ("net.minecraftforge.fml.client.SplashProgress$2".equals(transformedName)) {
                return patchSplashLoop(basicClass);
            }
            if ("net.minecraft.client.gui.GuiScreen".equals(transformedName)) {
                return patchBoxMouse(patchWorldBackground(basicClass));
            }
            if ("net.minecraftforge.client.ForgeHooksClient".equals(transformedName)) {
                return patchDrawScreenMouse(basicClass);
            }
            if ("net.minecraft.client.gui.GuiSlot".equals(transformedName)) {
                return patchSlot(basicClass);
            }
            if ("net.minecraft.world.gen.ChunkProviderServer".equals(transformedName)
                    && net.minecraftforge.fml.relauncher.FMLLaunchHandler.side().isClient()) {   // singleplayer only; dedicated servers never get it
                return patchCancelHook(basicClass);
            }
            if ("alexiil.mc.mod.load.CustomLoadingScreen".equals(transformedName)) {
                return forceDarkMode(basicClass);
            }
            if ("alexiil.mc.mod.load.render.MainSplashRenderer".equals(transformedName)) {
                return patchMainSplash(basicClass);
            }
        } catch (Throwable t) {
            System.out.println("[DogPound] transform skipped (safe): " + t);
        }
        return basicClass;
    }

    /** ChunkProviderServer.provideChunk(II) (SRG func_186025_d): call DPWorldCancel.check() first, so the
     *  loading-screen Cancel button can stop a world that's generating chunks (requested feature). */
    private byte[] patchCancelHook(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hit = false;
        for (MethodNode m : cn.methods) {
            if (!(m.name.equals("func_186025_d") || m.name.equals("provideChunk")) || !m.desc.equals("(II)Lnet/minecraft/world/chunk/Chunk;")) continue;
            m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPWorldCancel", "check", "()V", false));
            hit = true;
        }
        if (!hit) return basic;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[DogPound] world-load Cancel hook added");
        return cw.toByteArray();
    }

    // Force Forge/Cleanroom SplashProgress's backgroundColor to DogPound dark-green.
    // SplashProgress draws the hardcoded Mojang logo (mojang.png, loaded straight from
    // the jar) TINTED by backgroundColor on a backgroundColor clear — for ~3s before
    // CustomLoadingScreen's DogPound render takes over. Its default backgroundColor is
    // 0xFFFFFF (white) and our splash.properties isn't being applied to it, so it shows
    // the white Mojang flash. We override backgroundColor right after it's read from
    // config, so that pre-DogPound frame clears dark-green AND tints the Mojang logo
    // dark-green (near-invisible) — it blends into the DogPound boot instead of flashing.
    private byte[] patchSplash(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hit = false, preload = false;
        for (MethodNode m : cn.methods) {
            if (!"start".equals(m.name) || !"()V".equals(m.desc)) continue;
            // Pride loading screen: load every class the splash thread will use HERE, on the main thread, before the
            // splash thread exists - first-time class loads on the splash thread raced Mixin (CME crash, 2026-10-05)
            m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPBootPreload", "run", "()V", false));
            preload = true;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) insn;
                    if (fi.getOpcode() == Opcodes.PUTSTATIC
                            && fi.owner.endsWith("SplashProgress")
                            && "backgroundColor".equals(fi.name)) {
                        InsnList ins = new InsnList();
                        ins.add(new LdcInsnNode(Integer.valueOf(0x14210D))); // DogPound dark green
                        ins.add(new FieldInsnNode(Opcodes.PUTSTATIC, fi.owner, "backgroundColor", "I"));
                        m.instructions.insert(insn, ins); // override right after the config read
                        hit = true;
                        System.out.println("[DogPound] splash background forced dark — white Mojang flash neutralized");
                        break;
                    }
                }
            }
        }
        if (!hit) System.out.println("[DogPound] SplashProgress.backgroundColor not found — background unchanged");
        if (!hit && !preload) return basic;
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // Rewrite SplashProgress$2.run() so the splash render loop draws the DogPound boot
    // screen NATIVELY instead of the Mojang logo + Forge logo + memory bar + progress bars.
    //
    // The loop body (per frame) is: ... ProgressManager iterate ... glClear ... matrix/ortho
    // setup ... [MOJANG LOGO] [MEMORY BAR] [PROGRESS BARS] [FORGE LOGO] ... mutex.acquire ...
    // Display.update ... sync. We KEEP the matrix/ortho setup (our hook draws in that ortho)
    // and DELETE everything from right after the modelview reset up to the mutex acquire,
    // inserting one call: DPSplashHook.render(SplashProgress.fontRenderer-equivalent).
    //
    // The fontRenderer is a private static field on the OUTER SplashProgress class, read here
    // via its synthetic accessor access$300() (the same accessor used to SET it earlier in run).
    private byte[] patchSplashLoop(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Find the synthetic accessor that RETURNS the SplashFontRenderer, so we can fetch it.
        // It's a static method on SplashProgress: ()Lnet/minecraftforge/fml/client/SplashProgress$SplashFontRenderer;
        String frAccessorName = null, frAccessorOwner = "net/minecraftforge/fml/client/SplashProgress",
               frAccessorDesc = "()Lnet/minecraftforge/fml/client/SplashProgress$SplashFontRenderer;";

        // Locate the synthetic GETTER accessor (access$NNN() returning the font renderer) by
        // scanning EVERY method of this inner class (run() only SETS it; the drawBar/drawMemoryBar
        // helpers are the ones that GET it). We pick an INVOKESTATIC on SplashProgress with the
        // font-renderer return desc — that is the getter.
        for (MethodNode any : cn.methods) {
            for (AbstractInsnNode insn = any.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (mi.getOpcode() == Opcodes.INVOKESTATIC
                            && mi.owner.equals(frAccessorOwner)
                            && mi.desc.equals(frAccessorDesc)) {
                        frAccessorName = mi.name;
                        break;
                    }
                }
            }
            if (frAccessorName != null) break;
        }

        boolean hit = false;
        for (MethodNode m : cn.methods) {
            if (!"run".equals(m.name) || !"()V".equals(m.desc)) continue;

            // 1) find glClear
            AbstractInsnNode glClear = null;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (mi.getOpcode() == Opcodes.INVOKESTATIC
                            && mi.owner.equals("org/lwjgl/opengl/GL11")
                            && mi.name.equals("glClear")) { glClear = insn; break; }
                }
            }
            if (glClear == null) continue;

            // 2) after glClear, find glOrtho, then the FIRST glLoadIdentity after it
            //    (= the modelview reset). We keep everything up to and including it.
            AbstractInsnNode glOrtho = null;
            for (AbstractInsnNode insn = glClear; insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (mi.getOpcode() == Opcodes.INVOKESTATIC
                            && mi.owner.equals("org/lwjgl/opengl/GL11")
                            && mi.name.equals("glOrtho")) { glOrtho = insn; break; }
                }
            }
            if (glOrtho == null) continue;
            AbstractInsnNode modelviewReset = null;
            for (AbstractInsnNode insn = glOrtho; insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (mi.getOpcode() == Opcodes.INVOKESTATIC
                            && mi.owner.equals("org/lwjgl/opengl/GL11")
                            && mi.name.equals("glLoadIdentity")) { modelviewReset = insn; break; }
                }
            }
            if (modelviewReset == null) continue;

            // 3) find the mutex getstatic (end of the drawing region)
            AbstractInsnNode mutexGet = null;
            for (AbstractInsnNode insn = modelviewReset; insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) insn;
                    if (fi.getOpcode() == Opcodes.GETSTATIC && "mutex".equals(fi.name)) { mutexGet = insn; break; }
                }
            }
            if (mutexGet == null) continue;

            // 4) delete everything strictly between modelviewReset and mutexGet
            AbstractInsnNode cur = modelviewReset.getNext();
            while (cur != null && cur != mutexGet) {
                AbstractInsnNode next = cur.getNext();
                m.instructions.remove(cur);
                cur = next;
            }

            // 5) insert our render call right after the modelview reset.
            InsnList call = new InsnList();
            if (frAccessorName != null) {
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, frAccessorOwner, frAccessorName, frAccessorDesc, false));
            } else {
                call.add(new InsnNode(Opcodes.ACONST_NULL)); // fallback: no font, box+bar still draw
            }
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/dogpound/canvas/DPSplashHook", "render",
                    "(Lnet/minecraft/client/gui/FontRenderer;)V", false));
            m.instructions.insert(modelviewReset, call);

            hit = true;
            System.out.println("[DogPound] SplashProgress$2.run rewritten -> native DogPound boot screen (font="
                    + (frAccessorName != null) + ")");
        }

        if (!hit) {
            System.out.println("[DogPound] SplashProgress$2.run anchors not found — boot splash unchanged");
            return basic;
        }
        // COMPUTE_FRAMES: we deleted branchy code (forge-logo if/else, bar conditionals),
        // so stack-map frames must be recomputed. Use a ClassWriter that never throws on an
        // unresolvable common superclass during coremod transform (returns Object instead).
        ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** ClassWriter whose getCommonSuperClass never fails (avoids classload crashes at transform time). */
    private static final class SafeClassWriter extends ClassWriter {
        SafeClassWriter(ClassReader cr, int flags) { super(cr, flags); }
        /** Walks the class hierarchy by READING class files, never loading classes: loading one here re-entered
         *  Mixin's transformer when PolyPatcher/OneConfig were present ("Re-entrance error", 2026-10-05). */
        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                if (type1.equals(type2)) return type1;
                java.util.List<String> a = chain(type1), b = chain(type2);
                if (a == null || b == null) return "java/lang/Object";
                for (String s : b) if (a.contains(s)) return s;
            } catch (Throwable ignored) {}
            return "java/lang/Object";
        }

        /** the type and its superclasses, or null for interfaces / unreadable types */
        private static java.util.List<String> chain(String type) {
            java.util.List<String> out = new java.util.ArrayList<>();
            String t = type;
            for (int guard = 0; t != null && guard < 64; guard++) {
                out.add(t);
                if (t.equals("java/lang/Object")) return out;
                byte[] bytes = read(t);
                if (bytes == null) { out.add("java/lang/Object"); return out; }
                ClassReader r = new ClassReader(bytes);
                if ((r.getAccess() & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0) return null;
                t = r.getSuperName();
            }
            if (!out.contains("java/lang/Object")) out.add("java/lang/Object");
            return out;
        }

        private static byte[] read(String type) {
            String res = type + ".class";
            java.io.InputStream in = null;
            try {
                ClassLoader cl = net.minecraft.launchwrapper.Launch.classLoader;
                if (cl != null) in = cl.getResourceAsStream(res);
                if (in == null) in = ClassLoader.getSystemResourceAsStream(res);
                if (in == null) return null;
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                return o.toByteArray();
            } catch (Throwable t) {
                return null;
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
            }
        }
    }

    // CustomLoadingScreen hardcodes darkMode=false -> its loading screen clears to
    // WHITE behind the Mojang placeholder. Force it true so the pre-DogPound frames
    // clear BLACK. The DogPound wallpaper draws over it once loaded, so it's harmless.
    private byte[] forceDarkMode(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hit = false;
        for (MethodNode m : cn.methods) {
            if (!"<clinit>".equals(m.name)) continue;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) insn;
                    if (fi.getOpcode() == Opcodes.PUTSTATIC && "darkMode".equals(fi.name) && "Z".equals(fi.desc)) {
                        InsnList ins = new InsnList();
                        ins.add(new InsnNode(Opcodes.POP));      // drop the hardcoded false
                        ins.add(new InsnNode(Opcodes.ICONST_1)); // push true
                        m.instructions.insertBefore(insn, ins);
                        hit = true;
                        System.out.println("[DogPound] CLS darkMode forced true (black clear, not white)");
                    }
                }
            }
        }
        if (!hit) {
            System.out.println("[DogPound] CLS darkMode field not found");
            return basic;
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // The MAIN fix for the startup flash: keep the game WINDOW HIDDEN during the early
    // (Mojang/black) phase and only SHOW it once the DogPound loading screen actually
    // draws — so the window simply appears already showing DogPound. We hide in run()
    // (splash loop start) and show in renderFrame() (DogPound draws) + finish() (safety,
    // guarantees the window is visible by the time the splash ends). Direct GLFW calls
    // on Display.getWindow() (already-loaded classes — no new class load to crash on).
    //
    // FALLBACKS (in case the hide ever fails): renderMojangFrame is no-op'd (no Mojang
    // logo) and CLS.darkMode is forced true elsewhere (black clear, not white).
    private byte[] patchMainSplash(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hit = false;
        for (MethodNode m : cn.methods) {
            if ("renderMojangFrame".equals(m.name) && "()V".equals(m.desc)) {
                // No Mojang logo AND hide the window each pre-DogPound frame. Replace the
                // whole body with: DPWindow.hide(); return;  (DPWindow guards a not-ready
                // handle, so this is safe to call every frame on the splash thread.)
                m.instructions.clear();
                if (m.tryCatchBlocks != null) m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/dogpound/canvas/DPWindow", "hide", "()V", false));
                m.instructions.add(new InsnNode(Opcodes.RETURN));
                hit = true;
                System.out.println("[DogPound] renderMojangFrame -> hide window (Mojang frame killed)");
            } else if (("renderFrame".equals(m.name) || "finish".equals(m.name)) && "()V".equals(m.desc)) {
                // DogPound is drawing now (or splash finishing) -> show the window.
                m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/dogpound/canvas/DPWindow", "show", "()V", false));
                hit = true;
                System.out.println("[DogPound] window SHOW injected into " + m.name);
            }
        }
        if (!hit) {
            System.out.println("[DogPound] MainSplashRenderer target methods not found");
            return basic;
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // Bytecode for: GLFW.<glfwMethod>(Display.getWindow()) — inserted at a method start.
    private InsnList windowCall(String glfwMethod) {
        InsnList ins = new InsnList();
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjglx/opengl/Display", "getWindow", "()J", false));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW", glfwMethod, "(J)V", false));
        return ins;
    }

    // LoadingScreenRenderer.setLoadingProgress(int) -> SRG func_73718_a (I)V
    private byte[] patchWorldLoad(byte[] basic) {
        ClassReader cr = new ClassReader(basic);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hit = false;
        for (MethodNode m : cn.methods) {
            if (("func_73718_a".equals(m.name) || "setLoadingProgress".equals(m.name)) && "(I)V".equals(m.desc)) {
                // BEST spot: right before Framebuffer.unbindFramebuffer() (SRG
                // func_147609_e) — verified from the real bytecode, the dirt (offset
                // 670) and title/message text (offset 786) have JUST been drawn into
                // the still-bound framebuffer, before it's blitted to screen (offset
                // 832). Our overlay lands on the visible frame here.
                //
                // CRITICAL: there are TWO Framebuffer ()V calls. The FIRST is
                // bindFramebufferTexture (func_147614_f) at the very START, before the
                // dirt — injecting there drew us UNDER the dirt (the bug). So target
                // unbindFramebuffer by name, else fall back to the LAST ()V call.
                boolean inserted = false;
                AbstractInsnNode target = null;
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode) insn;
                        if (mi.owner.endsWith("Framebuffer") && "()V".equals(mi.desc)) {
                            if ("func_147609_e".equals(mi.name) || "unbindFramebuffer".equals(mi.name)) {
                                target = insn; // exact unbindFramebuffer — best
                                break;
                            }
                            target = insn; // else remember the LAST ()V (= unbind)
                        }
                    }
                }
                if (target != null) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ILOAD, 1)); // the 'progress' arg
                    call.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/dogpound/canvas/DPLoadingHook", "onProgress", "(I)V", false));
                    m.instructions.insertBefore(target, call);
                    inserted = true;
                }
                // fallback: before each RETURN (old behavior) if the framebuffer call wasn't found
                if (!inserted) {
                    for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
                        AbstractInsnNode next = insn.getNext();
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            InsnList call = new InsnList();
                            call.add(new VarInsnNode(Opcodes.ILOAD, 1));
                            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "com/dogpound/canvas/DPLoadingHook", "onProgress", "(I)V", false));
                            m.instructions.insertBefore(insn, call);
                        }
                        insn = next;
                    }
                }
                System.out.println("[DogPound] setLoadingProgress hooked (framebuffer=" + inserted + ")");
                hit = true;
            }
        }
        if (!hit) return basic;
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * GuiSlot: at the start of drawContainerBackground(Tessellator) and overlayBackground(int,int,int,int) (SRG
     * func_148136_c) ask DPHooks first; if it returns true, return early (Pride drew it). Nothing else changes.
     */
    private byte[] patchSlot(byte[] bytes) {
        ClassNode cn = new ClassNode();
        ClassReader cr = new ClassReader(bytes);
        cr.accept(cn, 0);
        int done = 0;
        for (MethodNode m : cn.methods) {
            InsnList in = new InsnList();
            org.objectweb.asm.tree.LabelNode go = new org.objectweb.asm.tree.LabelNode();
            if (m.name.equals("drawContainerBackground") && m.desc.equals("(Lnet/minecraft/client/renderer/Tessellator;)V")) {
                in.add(new VarInsnNode(Opcodes.ALOAD, 0));
                in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPHooks", "slotBackground", "(Lnet/minecraft/client/gui/GuiSlot;)Z", false));
            } else if ((m.name.equals("func_148136_c") || m.name.equals("overlayBackground")) && m.desc.equals("(IIII)V")) {
                in.add(new VarInsnNode(Opcodes.ILOAD, 1));
                in.add(new VarInsnNode(Opcodes.ILOAD, 2));
                in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPHooks", "slotOverlay", "(II)Z", false));
            } else continue;
            in.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, go));
            in.add(new InsnNode(Opcodes.RETURN));
            in.add(go);
            m.instructions.insert(in);
            done++;
        }
        if (done == 0) return bytes;
        ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[Pride UI] GuiSlot patched (" + done + " methods) for themed lists");
        return cw.toByteArray();
    }

    /** GuiScreen.drawWorldBackground(int) (SRG func_146270_b): ask DPHooks.worldBackground(this) first; true = skip. */
    private byte[] patchWorldBackground(byte[] bytes) {
        ClassNode cn = new ClassNode();
        ClassReader cr = new ClassReader(bytes);
        cr.accept(cn, 0);
        boolean done = false;
        for (MethodNode m : cn.methods) {
            if (!(m.name.equals("func_146270_b") || m.name.equals("drawWorldBackground")) || !m.desc.equals("(I)V")) continue;
            InsnList in = new InsnList();
            org.objectweb.asm.tree.LabelNode go = new org.objectweb.asm.tree.LabelNode();
            in.add(new VarInsnNode(Opcodes.ALOAD, 0));
            in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPHooks", "worldBackground", "(Lnet/minecraft/client/gui/GuiScreen;)Z", false));
            in.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, go));
            in.add(new InsnNode(Opcodes.RETURN));
            in.add(go);
            m.instructions.insert(in);
            done = true;
        }
        if (!done) return bytes;
        ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[Pride UI] GuiScreen.drawWorldBackground patched (clear game behind Pride sub-menus)");
        return cw.toByteArray();
    }

    /** GuiScreen.handleMouseInput (func_146274_d): boxed sub-menus get box coordinates (DPBoxLayout.handleMouse). */
    private byte[] patchBoxMouse(byte[] bytes) {
        ClassNode cn = new ClassNode();
        ClassReader cr = new ClassReader(bytes);
        cr.accept(cn, 0);
        boolean done = false;
        for (MethodNode m : cn.methods) {
            if (!(m.name.equals("func_146274_d") || m.name.equals("handleMouseInput")) || !m.desc.equals("()V")) continue;
            InsnList in = new InsnList();
            org.objectweb.asm.tree.LabelNode go = new org.objectweb.asm.tree.LabelNode();
            in.add(new VarInsnNode(Opcodes.ALOAD, 0));
            in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPBoxLayout", "handleMouse", "(Lnet/minecraft/client/gui/GuiScreen;)Z", false));
            in.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, go));
            in.add(new InsnNode(Opcodes.RETURN));
            in.add(go);
            m.instructions.insert(in);
            done = true;
        }
        if (!done) return bytes;
        ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[Pride UI] GuiScreen.handleMouseInput patched (boxed sub-menus)");
        return cw.toByteArray();
    }

    /** ForgeHooksClient.drawScreen(screen, mouseX, mouseY, pt): move the mouse into box coordinates first. */
    private byte[] patchDrawScreenMouse(byte[] bytes) {
        ClassNode cn = new ClassNode();
        ClassReader cr = new ClassReader(bytes);
        cr.accept(cn, 0);
        boolean done = false;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("drawScreen") || !m.desc.equals("(Lnet/minecraft/client/gui/GuiScreen;IIF)V")) continue;
            InsnList in = new InsnList();
            in.add(new VarInsnNode(Opcodes.ALOAD, 0));
            in.add(new VarInsnNode(Opcodes.ILOAD, 1));
            in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPBoxLayout", "mapX", "(Lnet/minecraft/client/gui/GuiScreen;I)I", false));
            in.add(new VarInsnNode(Opcodes.ISTORE, 1));
            in.add(new VarInsnNode(Opcodes.ALOAD, 0));
            in.add(new VarInsnNode(Opcodes.ILOAD, 2));
            in.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/dogpound/canvas/DPBoxLayout", "mapY", "(Lnet/minecraft/client/gui/GuiScreen;I)I", false));
            in.add(new VarInsnNode(Opcodes.ISTORE, 2));
            m.instructions.insert(in);
            done = true;
        }
        if (!done) return bytes;
        ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[Pride UI] ForgeHooksClient.drawScreen patched (boxed sub-menus)");
        return cw.toByteArray();
    }
}
