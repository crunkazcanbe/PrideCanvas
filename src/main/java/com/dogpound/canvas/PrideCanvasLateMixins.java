package com.dogpound.canvas;

import java.util.Collections;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

/** Patches aimed at other MODS' classes (e.g. moving Mine and Slash's HUD) load late, after mods are found. */
public class PrideCanvasLateMixins implements ILateMixinLoader {
    @Override public List<String> getMixinConfigs() { return Collections.singletonList("pridecanvas.late.mixins.json"); }
}
