package com.dogpound.canvas;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Swaps the vanilla settings widgets for DogPound-styled equivalents that subclass
 * them, so the look matches the main menu while every behavior (toggles, sliders,
 * the screens' actionPerformed casts) keeps working. Anything unexpected falls back
 * to the original widget — it can never crash a screen.
 *
 * Slider reflection uses the runtime (SRG) field names, since the shipped jar runs
 * against SRG mappings: field_146133_q = option, field_146132_r/s = min/max.
 */
public final class DPSkin {
    private DPSkin() {}

    public static void reskinAll(List<GuiButton> buttons) {
        reskinAll(buttons, null);
    }

    ///
    /// Reskin every button AND keep the owning screen's own references pointing at the
    /// new objects.
    ///
    /// This matters more than it looks. Vanilla screens cache their buttons in private
    /// fields and then write THROUGH those fields:
    ///
    ///     this.btnGameMode.displayString = I18n.format("selectWorld.gameMode." + mode);
    ///     this.btnMapType.visible = this.moreOptions;
    ///
    /// Replacing the entries in buttonList without rebinding those fields leaves every
    /// such write landing on an orphaned button that is no longer drawn. Symptoms seen on
    /// the create-world screen: clicking Game Mode really did change the mode, but the
    /// label stayed "Survival" forever, and the superflat / customize buttons under
    /// "More World Options" never appeared because their .visible was flipped on orphans.
    ///
    public static void reskinAll(List<GuiButton> buttons, GuiScreen owner) {
        if (buttons == null) return;
        final Map<GuiButton, GuiButton> replaced = new IdentityHashMap<GuiButton, GuiButton>();
        for (int i = 0; i < buttons.size(); i++) {
            GuiButton b = buttons.get(i);
            if (b instanceof DPButton || b instanceof DPOptionButton || b instanceof DPOptionSlider) continue;
            GuiButton skinned = reskin(b);
            if (skinned != b) {
                buttons.set(i, skinned);
                replaced.put(b, skinned);
            }
        }
        if (owner != null && !replaced.isEmpty()) rebindOwnerFields(owner, replaced);
    }

    /** Point every GuiButton-typed field of the screen (and its superclasses) at the
     *  replacement, so the screen's own logic keeps driving the button that is drawn. */
    private static void rebindOwnerFields(GuiScreen owner, Map<GuiButton, GuiButton> replaced) {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fs) {
                if (!GuiButton.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object cur = f.get(owner);
                    GuiButton to = (cur == null) ? null : replaced.get(cur);
                    if (to != null) f.set(owner, to);
                } catch (Throwable ignored) {
                    // a field we cannot touch is not worth breaking the screen over
                }
            }
        }
    }

    public static GuiButton reskin(GuiButton b) {
        try {
            if (b instanceof GuiOptionSlider) {
                GameSettings.Options opt = (GameSettings.Options) field(GuiOptionSlider.class, b, "field_146133_q");
                float min = (Float) field(GuiOptionSlider.class, b, "field_146132_r");
                float max = (Float) field(GuiOptionSlider.class, b, "field_146131_s");
                GuiButton n = new DPOptionSlider(b.id, b.x, b.y, opt, min, max);
                copy(b, n);
                return n;
            }
            if (b instanceof GuiOptionButton) {
                GameSettings.Options opt = ((GuiOptionButton) b).getOption();
                GuiButton n = (opt != null)
                        ? new DPOptionButton(b.id, b.x, b.y, opt, b.displayString)
                        : new DPOptionButton(b.id, b.x, b.y, b.displayString);
                copy(b, n);
                return n;
            }
            String label = b.displayString;
            if (b instanceof net.minecraft.client.gui.GuiLockIconButton)          // the difficulty padlock has no words
                label = ((net.minecraft.client.gui.GuiLockIconButton) b).isLocked() ? "Difficulty Locked" : "Lock Difficulty";
            DPButton n = new DPButton(b.id, b.x, b.y, b.width, b.height, label);
            n.autoFlat = true;
            if (b.getClass() != GuiButton.class && !(b instanceof net.minecraft.client.gui.GuiLockIconButton)) n.orig = b;   // mod button: keep its own click code
            copy(b, n);
            return n;
        } catch (Throwable t) {
            return b; // leave the original — never break the screen
        }
    }

    private static void copy(GuiButton from, GuiButton to) {
        to.x = from.x;
        to.y = from.y;
        to.width = from.width;
        to.height = from.height;
        to.enabled = from.enabled;
        to.visible = from.visible;
    }

    private static Object field(Class<?> c, Object o, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /** buttons that live inside a list's rows (Controls: each key's button + Reset): swap them for Pride ones,
     *  pointing the row's own fields at the new buttons so the row keeps driving them */
    public static void reskinRows(net.minecraft.client.gui.GuiSlot s) {
        if (!(s instanceof net.minecraft.client.gui.GuiListExtended)) return;
        net.minecraft.client.gui.GuiListExtended l = (net.minecraft.client.gui.GuiListExtended) s;
        int n;
        try { n = (Integer) net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiSlot.class, "getSize", "func_148127_b").invoke(l); } catch (Throwable t) { return; }
        for (int i = 0; i < n; i++) {
            Object e;
            try { e = l.getListEntry(i); } catch (Throwable t) { continue; }
            if (e == null) continue;
            for (Class<?> c = e.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() != GuiButton.class) continue;                   // exact type only: we can put a DPButton there
                    try {
                        f.setAccessible(true);
                        Object b = f.get(e);
                        if (b instanceof GuiButton && !(b instanceof DPButton)) f.set(e, reskin((GuiButton) b));
                    } catch (Throwable ignored) {}
                }
            }
        }
    }
}
