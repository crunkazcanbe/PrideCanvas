package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.settings.GameSettings;

/**
 * An option toggle (e.g. "Graphics: Fancy") that keeps every bit of vanilla
 * behavior — it still IS a GuiOptionButton, so the screens' actionPerformed code
 * (which casts to GuiOptionButton) works unchanged — but paints like the DogPound
 * main-menu buttons.
 */
public class DPOptionButton extends GuiOptionButton {
    public DPOptionButton(int id, int x, int y, String text) {
        super(id, x, y, text);
    }

    public DPOptionButton(int id, int x, int y, GameSettings.Options option, String text) {
        super(id, x, y, option, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        DPStyle.panel(this.x, this.y, this.width, this.height, this.hovered, this.enabled);
        DPStyle.label(mc, this.x, this.y, this.width, this.height, this.hovered, this.enabled, this.displayString);
    }
}
