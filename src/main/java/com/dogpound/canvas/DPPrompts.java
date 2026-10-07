package com.dogpound.canvas;

import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.fml.client.GuiNotification;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Popups that ask a question (Forge "missing registries?", yes/no boxes, error/disconnect screens)
 * get written to pride-prompts/prompt.txt + the log + a screenshot, so Claude can SEE them
 * (requested feature).
 * Answer by writing a button label (or number) to pride-prompts/answer — ~/bin/mcprompt does that.
 */
public final class DPPrompts {
    private static final File DIR = new File("pride-prompts");
    private static final File ANSWER = new File(DIR, "answer");
    private static GuiScreen last;

    /** Forge's startup questions are drawn THROUGH the loading-screen renderer — our overlay must stand aside. */
    public static boolean isQuestion(GuiScreen g) {
        return g instanceof GuiNotification || g instanceof GuiYesNo || g instanceof GuiErrorScreen || g instanceof GuiDisconnected;
    }

    /** From the loading loop (Forge questions) — framebuffer is the loading renderer's own. */
    static void fromLoading(Minecraft mc) {
        Framebuffer fb = null;
        try { fb = ObfuscationReflectionHelper.getPrivateValue(LoadingScreenRenderer.class, mc.loadingScreen, "field_146588_g"); } catch (Throwable ignored) {}
        check(mc, mc.currentScreen, fb);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        check(mc, mc.currentScreen, mc.getFramebuffer());
    }

    private static void check(Minecraft mc, GuiScreen g, Framebuffer fb) {
        try {
            if (g == null || !isQuestion(g)) { if (last != null) { last = null; ANSWER.delete(); } return; }
            if (g != last) {
                last = g;
                ANSWER.delete();                 // a stale answer must never click the NEW question
                report(mc, g, fb);
            }
            if (ANSWER.isFile()) {
                String want = new String(Files.readAllBytes(ANSWER.toPath()), StandardCharsets.UTF_8).trim();
                ANSWER.delete();
                press(g, want);
            }
        } catch (Throwable t) {
            System.out.println("[Pride Prompt] error: " + t);
        }
    }

    private static void report(Minecraft mc, GuiScreen g, Framebuffer fb) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(new java.util.Date()).append('\n');
        sb.append("screen: ").append(g.getClass().getName()).append('\n');
        sb.append("--- text ---\n").append(text(g)).append('\n');
        sb.append("--- buttons (answer with label or number) ---\n");
        List<GuiButton> bl = buttons(g);
        for (int i = 0; i < bl.size(); i++) sb.append(i).append(": ").append(bl.get(i).displayString).append('\n');
        String shot = "pride-prompt-" + System.currentTimeMillis() + ".png";
        try {
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, shot, mc.displayWidth, mc.displayHeight, fb != null ? fb : mc.getFramebuffer());
            sb.append("screenshot: screenshots/").append(shot).append('\n');
        } catch (Throwable t) { sb.append("screenshot failed: ").append(t).append('\n'); }
        DIR.mkdirs();
        Files.write(new File(DIR, "prompt.txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("[Pride Prompt] QUESTION ON SCREEN:\n" + sb);
    }

    /** every String / List<String> / StartupQuery text on the screen, walking up to GuiScreen */
    private static String text(GuiScreen g) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c = g.getClass(); c != null && c != GuiScreen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(g);
                    if (v instanceof String) sb.append(v).append('\n');
                    else if (v instanceof List) { for (Object o : (List<?>) v) if (o instanceof String) sb.append(o).append('\n'); }
                    else if (v != null && v.getClass().getName().endsWith("StartupQuery")) sb.append(v.getClass().getMethod("getText").invoke(v)).append('\n');
                    else if (v instanceof net.minecraft.util.text.ITextComponent) sb.append(((net.minecraft.util.text.ITextComponent) v).getUnformattedText()).append('\n');
                } catch (Throwable ignored) {}
            }
        }
        return sb.toString().trim();
    }

    private static List<GuiButton> buttons(GuiScreen g) {
        return ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, g, "field_146292_n");
    }

    private static void press(GuiScreen g, String want) throws Exception {
        List<GuiButton> bl = buttons(g);
        GuiButton hit = null;
        for (int i = 0; i < bl.size(); i++) {
            GuiButton b = bl.get(i);
            if (want.equals(String.valueOf(i)) || b.displayString.equalsIgnoreCase(want)) { hit = b; break; }
        }
        if (hit == null) { System.out.println("[Pride Prompt] no button '" + want + "'"); return; }
        System.out.println("[Pride Prompt] answering '" + hit.displayString + "'");
        Method m = ObfuscationReflectionHelper.findMethod(GuiScreen.class, "func_146284_a", void.class, GuiButton.class);
        m.invoke(g, hit);
    }
}
