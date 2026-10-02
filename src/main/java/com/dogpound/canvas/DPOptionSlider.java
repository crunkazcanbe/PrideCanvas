package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;

/**
 * A settings slider (FOV, sensitivity, render distance, ...) that paints with the
 * DogPound translucent look but keeps the real vanilla drag/value behavior: it
 * still extends GuiOptionSlider, and we call the inherited drag logic so the knob
 * draws and the value updates exactly as before.
 */
public class DPOptionSlider extends GuiOptionSlider {
    public DPOptionSlider(int id, int x, int y, GameSettings.Options option) {
        super(id, x, y, option);
    }

    public DPOptionSlider(int id, int x, int y, GameSettings.Options option, float min, float max) {
        super(id, x, y, option, min, max);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // 1) translucent panel
        DPStyle.panel(this.x, this.y, this.width, this.height, this.hovered, this.enabled);
        // 2) vanilla knob + live value update (handles dragging, refreshes displayString)
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mouseDragged(mc, mouseX, mouseY);
        // 3) the label (Graphics: Fancy / FOV: 70 ...) on top
        DPStyle.label(mc, this.x, this.y, this.width, this.height, this.hovered, this.enabled, this.displayString);
    }
}
