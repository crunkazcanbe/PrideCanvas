package com.dogpound.canvas.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * dpmenu's coremod entry point. Registers DPTransformer, which ASM-patches
 * Minecraft's loading screens to draw the DogPound overlay. This is the
 * technique real 1.12.2 loading-screen mods use (loads early, right classloader);
 * the transformer only inserts a CALL to our hook, so our classes load lazily at
 * runtime (avoids the mixin's transform-time ClassNotFound crash).
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("Pride Core")
@IFMLLoadingPlugin.SortingIndex(1001)
public class DPCoreMod implements IFMLLoadingPlugin, zone.rong.mixinbooter.IEarlyMixinLoader {

    /** early mixins: vanilla GUI classes (Pride Item FX on GuiContainer) */
    @Override public java.util.List<String> getMixinConfigs() { return java.util.Collections.singletonList("pridecanvas.mixins.json"); }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.dogpound.canvas.core.DPTransformer" };
    }

    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {
        // Install the log capture buffer NOW (coremod load = before the splash starts),
        // so the native boot splash already has scrolling log lines to show. Guarded so
        // it can never break early loading.
        // Safe Mode (loading screen #16): count unfinished starts, ask after two in a row - BEFORE Forge reads mods/
        try { com.dogpound.canvas.DPSafeMode.onCoremodStart(); } catch (Throwable ignored) {}
        try { com.dogpound.canvas.DPLogBuffer.install(); } catch (Throwable ignored) {}
        // Pride loading music starts here, so it plays from the very first loading bar
        try { com.dogpound.canvas.DPMusic.startFromCoremod(); } catch (Throwable ignored) {}
    }
    @Override public String getAccessTransformerClass() { return null; }
}
