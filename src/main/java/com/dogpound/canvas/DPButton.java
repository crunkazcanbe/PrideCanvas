package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Translucent menu button - draws a see-through panel so the moving wallpaper
 * shows through. Animated (2026-09-30): lifts + grows on hover with a light sweeping across,
 * squishes when clicked with a rainbow ripple from the click point, and plays the Pride chimes.
 */
public class DPButton extends GuiButton {
    /** When true the grid already drew the slot backdrop, so we only draw icon + label. */
    public boolean noSlot = false;
    /** Plain dark button, no block-sprite backdrop. DPIcons picks its sprite from the label text,
     *  which turns utility buttons ("Sort: Downloads") into loud gold/redstone slabs. */
    public boolean flat = false;
    /** which note of the hover scale this button plays (tiles in a row climb a scale) */
    public int soundIndex = -1;
    /** reskinned vanilla buttons: long thin bars draw as the plain pride bar (no block icon) */
    public boolean autoFlat = false;
    /** the click sound: CLICK normally, BACK for cancel/back buttons, CONFIRM for big actions */
    public net.minecraft.util.SoundEvent clickSound;

    private boolean wasHovered;
    private long shineAt, pressAt;

    /** the mod's own button this one replaced on screen: a mod button subclass may do its work in mousePressed
     *  (Pride Tweaks' opener did, and stopped opening once reskinned, her 2026-10-05 report), so clicks go to it too */
    public GuiButton orig;

    public DPButton(int id, int x, int y, int w, int h, String text) {
        super(id, x, y, w, h, text);
    }

    /** Chainable: new DPButton(...).plain() */
    public DPButton plain() { this.flat = true; return this; }
    public DPButton sound(net.minecraft.util.SoundEvent s) { this.clickSound = s; return this; }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        if (hovered && !wasHovered && enabled) {
            shineAt = DPAnim.now();
            DPSounds.hover(soundIndex >= 0 ? soundIndex : Math.floorMod(x / Math.max(1, width), 8));
        }
        wasHovered = hovered;

        boolean anim = DPConfig.animations;
        float h = anim ? DPAnim.approach(this, hovered && enabled ? 1f : 0f, 14f) : (hovered ? 1 : 0);
        float press = anim && pressAt > 0 ? DPAnim.clamp01((DPAnim.now() - pressAt) / 200f) : 1f;
        float squish = press < 1 ? 1f - 0.07f * (float) Math.sin(Math.PI * press) : 1f;
        boolean big = height >= 24;
        float scale = (1f + (big ? 0.045f : 0.02f) * h) * squish, lift = big ? 2.5f * h : 0.8f * h;

        GlStateManager.pushMatrix();
        if (anim) {
            float cx = x + width / 2f, cy = y + height / 2f;
            GlStateManager.translate(cx, cy - lift, 0);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-cx, -cy, 0);
            if (h > 0.01f) DPStyle.glow(x, y, width, height, 0xF5A9B8, (int) (70 * h), 3);   // soft pink halo, fades in
        }

        if (flat || (autoFlat && width > height * 2.2f)) {
            DPStyle.slotPlain(this.x, this.y, this.width, this.height, this.hovered, this.enabled);
        } else {
            if (!noSlot) DPStyle.slot(this.x, this.y, this.width, this.height, this.hovered, this.enabled);
            if (hovered && noSlot) DPStyle.slotHover(this.x, this.y, this.width, this.height);
            // flat icon fills the whole slot, the button's words drawn ON TOP of it
            DPIcons.draw(mc, this.displayString, this.x, this.y, this.width, this.height);
        }
        if (anim) shine();
        DPStyle.label(mc, this.x, this.y, this.width, this.height, this.hovered, this.enabled, this.displayString);
        GlStateManager.popMatrix();
    }

    /** a band of light that sweeps left→right across the button when the mouse arrives */
    private void shine() {
        float p = (DPAnim.now() - shineAt) / 480f;
        if (p < 0 || p >= 1) return;
        float e = DPAnim.easeOutCubic(p);
        int band = Math.max(6, width / 5);
        int bx = (int) (x - band + (width + band) * e);
        int alpha = (int) (110 * (1 - p));
        for (int i = 0; i < band; i++) {
            int lx = bx + i;
            if (lx < x || lx >= x + width) continue;
            float k = 1f - Math.abs(i - band / 2f) / (band / 2f);                  // brightest in the middle
            Gui.drawRect(lx, y + 1, lx + 1, y + height - 1, ((int) (alpha * k) << 24) | 0xFFFFFF);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean hit = super.mousePressed(mc, mouseX, mouseY);
        if (hit && orig != null) {
            orig.x = x; orig.y = y; orig.width = width; orig.height = height; orig.enabled = enabled; orig.visible = true;
            try { orig.mousePressed(mc, mouseX, mouseY); } catch (Throwable ignored) {}
        }
        if (hit && DPConfig.animations) {
            pressAt = DPAnim.now();
            int[] rainbow = PrideFrame.RAINBOW;
            DPAnim.ripple(mouseX, mouseY, Math.max(width, height) * 1.1f, rainbow[(int) (Math.random() * rainbow.length)], new int[]{x, y, width, height});
        }
        return hit;
    }

    @Override
    public void playPressSound(SoundHandler handler) {
        if (DPConfig.uiSounds) DPSounds.play(clickSound != null ? clickSound : soundFor(displayString));
        else super.playPressSound(handler);
    }

    /** back/cancel/done-ish labels get the falling chime, everything else the rising one */
    static net.minecraft.util.SoundEvent soundFor(String label) {
        String s = label == null ? "" : net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(label).toLowerCase();
        if (s.equals("cancel") || s.equals("back") || s.startsWith("< ") || s.equals("no")) return DPSounds.BACK;
        if (s.equals("done") || s.startsWith("save") || s.startsWith("create") || s.equals("yes")) return DPSounds.CONFIRM;
        return DPSounds.CLICK;
    }
}
