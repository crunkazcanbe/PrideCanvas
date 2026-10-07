package com.dogpound.canvas;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MAIN THREAD ONLY. Everything the loading screen shows about mods and registries is copied here into plain Java
 * arrays/ints by DPMenuMod's FML event handlers (pre-init, init, post-init, load complete). The splash thread then
 * reads only those copies — it never touches Forge/mod objects or registries itself (that loaded classes off the main
 * thread and crashed Mixin, 2026-10-05). This class is deliberately NOT preloaded and never used by the splash.
 */
public final class DPBootMain {
    private DPBootMain() {}

    /** pre-init: the mod list for Mod web / Mods carousel / startup repair */
    public static void snapshotMods() {
        try {
            List<ModContainer> mods = Loader.instance().getActiveModList();
            int n = mods.size();
            String[] ids = new String[n], names = new String[n], vers = new String[n], auth = new String[n], desc = new String[n], logo = new String[n], url = new String[n];
            int[] deps = new int[n], users = new int[n];
            Map<String, Integer> idx = new HashMap<String, Integer>();
            for (int i = 0; i < n; i++) {
                ModContainer mc = mods.get(i);
                ids[i] = mc.getModId(); names[i] = mc.getName(); vers[i] = mc.getDisplayVersion();
                idx.put(ids[i], i);
                net.minecraftforge.fml.common.ModMetadata md = mc.getMetadata();
                auth[i] = md != null && md.authorList != null && !md.authorList.isEmpty() ? String.join(", ", md.authorList) : "";
                desc[i] = md != null && md.description != null ? md.description.replace("\r", "").replace("\n", " ").trim() : "";
                logo[i] = md != null && md.logoFile != null ? md.logoFile.trim() : "";
                url[i] = md != null && md.url != null ? md.url : "";
                try {
                    java.io.File src = mc.getSource();
                    String id = ids[i];
                    if (src != null && src.isFile() && src.getParentFile() != null && src.getParentFile().getName().equals("mods")
                            && !id.equals("minecraft") && !id.equals("forge") && !id.equals("FML") && !id.equals("mcp") && !id.equals("dpcanvas")) {
                        DPBoot.MOD_JAR.put(id, src.getName());
                        DPBoot.MOD_JAR.put(id.toLowerCase(Locale.ROOT), src.getName());
                        DPBoot.MOD_JAR.put(names[i].toLowerCase(Locale.ROOT), src.getName());
                    }
                } catch (Throwable ignored) { }
            }
            List<int[]> e = new ArrayList<int[]>();
            for (int i = 0; i < n; i++) {
                java.util.Set<String> seen = new java.util.HashSet<String>();
                List<ArtifactVersion> all = new ArrayList<ArtifactVersion>();
                try { all.addAll(mods.get(i).getRequirements()); } catch (Throwable ignored) { }
                try { all.addAll(mods.get(i).getDependencies()); } catch (Throwable ignored) { }
                for (ArtifactVersion v : all) {
                    String d = v.getLabel();
                    Integer j = idx.get(d);
                    if (j == null || j == i || !seen.add(d) || d.equals("forge") || d.equals("minecraft") || d.equals("FML") || d.equals("mcp")) continue;
                    e.add(new int[]{i, j});
                    deps[i]++; users[j]++;
                }
            }
            int[] a = new int[e.size()], b = new int[e.size()];
            for (int k = 0; k < e.size(); k++) { a[k] = e.get(k)[0]; b[k] = e.get(k)[1]; }
            DPBootViews.setMods(ids, names, vers, auth, desc, logo, url, a, b, deps, users);
        } catch (Throwable t) { System.out.println("[Pride UI] loading-screen mod snapshot skipped: " + t); }
    }

    /** init / post-init / load complete: registry sizes for Facts + achievement toasts */
    public static void countRegistries() {
        try {
            net.minecraftforge.registries.IForgeRegistry<?>[] rs = {net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS,
                    net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS, net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES,
                    net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES, net.minecraftforge.fml.common.registry.ForgeRegistries.SOUND_EVENTS,
                    net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES, net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS,
                    net.minecraftforge.fml.common.registry.ForgeRegistries.POTIONS, net.minecraftforge.fml.common.registry.ForgeRegistries.VILLAGER_PROFESSIONS};
            for (int i = 0; i < rs.length; i++) {
                DPBoot.REG_N[i] = rs[i].getKeys().size();
                while (DPBoot.REG_MILE[i] < DPBoot.MILESTONES.length && DPBoot.REG_N[i] >= DPBoot.MILESTONES[DPBoot.REG_MILE[i]]) {
                    if (DPBoot.REG_MILE[i] >= 2 || i == 3 || i >= 6)        // skip the boring small ones, except small registries
                        DPBoot.TOASTS.add(String.format(Locale.ROOT, "%,d %s registered!", DPBoot.MILESTONES[DPBoot.REG_MILE[i]], DPBoot.REG_L[i].toLowerCase(Locale.ROOT)));
                    DPBoot.REG_MILE[i]++;
                }
            }
            while (DPBoot.TOASTS.size() > 6) DPBoot.TOASTS.pollFirst();
        } catch (Throwable t) { System.out.println("[Pride UI] loading-screen registry count skipped: " + t); }
    }
}
