package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * DogPound Video Settings. Vanilla puts these options in a scrolling list, not normal
 * buttons — so here we rebuild them as real bottom buttons (like the main menu): each
 * video option becomes a translucent DogPound button/slider, plus Done, reflowed into
 * resizable rows. The original scroll list is emptied so it draws/handles nothing.
 *
 * Toggles are applied here (the vanilla list, not actionPerformed, used to do that);
 * sliders update themselves on drag; Done falls through to vanilla (save + go back).
 */
public class DPVideoSettings extends GuiVideoSettings {
    public DPVideoSettings(GuiScreen parent, GameSettings settings) {
        super(parent, settings);
    }

    @Override
    public void initGui() {
        super.initGui();   // builds the vanilla scroll list + the Done button (id 200) in buttonList
        try {
            GameSettings gs = this.mc.gameSettings;
            // the static array of which options this screen shows
            Field of = GuiVideoSettings.class.getDeclaredField("field_146502_i");
            of.setAccessible(true);
            GameSettings.Options[] opts = (GameSettings.Options[]) of.get(null);
            int id = 0;
            for (GameSettings.Options o : opts) {
                if (o.isFloat()) {
                    this.buttonList.add(new DPOptionSlider(id, 0, 0, o));
                } else {
                    this.buttonList.add(new DPOptionButton(id, 0, 0, o, gs.getKeyBinding(o)));
                }
                id++;
            }
            // Empty the scroll list so it never draws or eats clicks.
            Field lf = GuiVideoSettings.class.getDeclaredField("field_146501_h");
            lf.setAccessible(true);
            lf.set(this, new GuiOptionsRowList(this.mc, this.width, this.height, 32, this.height - 32, 25));
        } catch (Throwable t) {
            System.out.println("[DogPound] DPVideoSettings build skipped (safe): " + t);
        }
        DPSkin.reskinAll(this.buttonList);
        DPChrome.reflowOptions(this.buttonList, this.width, this.height);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;
        if (button.id == 200) {                 // Done: let vanilla save + return to parent
            super.actionPerformed(button);
            return;
        }
        if (button instanceof GuiOptionButton) { // a toggle (Graphics, VSync, Fullscreen, ...)
            GameSettings.Options opt = ((GuiOptionButton) button).getOption();
            this.mc.gameSettings.setOptionValue(opt, 1);
            button.displayString = this.mc.gameSettings.getKeyBinding(opt);
        }
        // sliders apply themselves while dragging — nothing to do here
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        DPBackdrop.draw(this.mc, this.width, this.height);
        DPGrid.draw(this, "Video Settings");   // centred house-style panel (+ scroll bar when the tiles overflow)
        DPChrome.paintWidgets(this, this.buttonList, this.labelList, mouseX, mouseY, partialTicks);
        DPMemoryBar.draw(0, this.height - 2, this.width, 2);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        DPGrid.wheel(org.lwjgl.input.Mouse.getDWheel());   // scroll the tile grid inside the panel
    }
}
