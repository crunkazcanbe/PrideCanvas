package com.dogpound.canvas;

import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.settings.GameSettings;

/**
 * Skin Customization and Chat Settings in the Pride tile panel, like Options and Video Settings (requested feature). Vanilla keeps
 * building and running every button; we only restyle, lay them out as tiles and draw the panel.
 */
public final class DPSubMenus {
    private DPSubMenus() {}

    private static void dress(GuiScreen s, java.util.List<net.minecraft.client.gui.GuiButton> buttons, int w, int h) {
        DPSkin.reskinAll(buttons, s);
        DPChrome.reflowOptions(buttons, w, h);
    }

    private static void paint(GuiScreen s, String title, java.util.List<net.minecraft.client.gui.GuiButton> buttons,
                              java.util.List<net.minecraft.client.gui.GuiLabel> labels, int mx, int my, float pt) {
        DPBackdrop.draw(s.mc, s.width, s.height);
        DPGrid.draw(s, title);
        DPChrome.paintWidgets(s, buttons, labels, mx, my, pt);
        DPMemoryBar.draw(0, s.height - 2, s.width, 2);
    }

    public static class Skin extends GuiCustomizeSkin {
        public Skin(GuiScreen parent) { super(parent); }

        @Override public void initGui() { super.initGui(); dress(this, buttonList, width, height); }

        @Override public void drawScreen(int mx, int my, float pt) { paint(this, "Skin Customization", buttonList, labelList, mx, my, pt); }

        @Override
        public void handleMouseInput() throws java.io.IOException {
            super.handleMouseInput();
            DPGrid.wheel(org.lwjgl.input.Mouse.getDWheel());
        }
    }

    public static class Chat extends ScreenChatOptions {
        public Chat(GuiScreen parent, GameSettings settings) { super(parent, settings); }

        @Override public void initGui() { super.initGui(); dress(this, buttonList, width, height); }

        @Override public void drawScreen(int mx, int my, float pt) { paint(this, "Chat Settings", buttonList, labelList, mx, my, pt); }

        @Override
        public void handleMouseInput() throws java.io.IOException {
            super.handleMouseInput();
            DPGrid.wheel(org.lwjgl.input.Mouse.getDWheel());
        }
    }
}
