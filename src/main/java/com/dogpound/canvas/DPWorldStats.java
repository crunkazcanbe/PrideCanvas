package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.File;

/**
 * Loading screen #21-#23: what's happening while you join — server connection (state, ping, time), server resource
 * pack downloads (speed + ETA), and world loading stats. Drawn by DPLoadingScreen as a Pride card; numbers refresh
 * 4x a second, reading only sizes/counters (never walking big collections every frame).
 */
final class DPWorldStats {
    private DPWorldStats() {}

    static final int MAX = 12;
    static final String[] L = new String[MAX], V = new String[MAX];
    static int n;
    static String title = "";
    private static long lastAt, phaseStart;
    private static String phase = "";

    private static void row(String l, String v) { if (n < MAX) { L[n] = l; V[n] = v; n++; } }

    static void refresh(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastAt < 250) return;
        lastAt = now;
        n = 0;
        title = "";
        try {
            GuiScreen g = mc.currentScreen;
            String sn = g == null ? "" : g.getClass().getSimpleName();
            if (!sn.equals(phase)) { phase = sn; phaseStart = now; }
            if (sn.equals("GuiConnecting")) connecting(mc, g, now);
            else if (sn.equals("GuiScreenWorking")) working(g, now);
            if (n == 0) world(mc, now);
        } catch (Throwable ignored) { }
    }

    // ---- #21 server connection
    private static void connecting(Minecraft mc, GuiScreen g, long now) {
        title = "Connecting";
        net.minecraft.client.multiplayer.ServerData sd = mc.getCurrentServerData();
        if (sd != null) {
            row("Server", sd.serverName);
            row("Address", sd.serverIP);
            if (sd.pingToServer > 0) row("Last ping", sd.pingToServer + " ms" + (sd.pingToServer < 80 ? "  (great)" : sd.pingToServer < 200 ? "  (ok)" : "  (slow)"));
            if (sd.gameVersion != null && !sd.gameVersion.isEmpty()) row("Version", sd.gameVersion);
        }
        Object nm = null;
        try { nm = ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.multiplayer.GuiConnecting.class, (net.minecraft.client.multiplayer.GuiConnecting) g, "field_146371_g", "networkManager"); } catch (Throwable ignored) { }
        String state = nm == null ? "looking up the server + opening a connection" : "logging in";
        if (nm instanceof net.minecraft.network.NetworkManager) {
            net.minecraft.network.INetHandler h = ((net.minecraft.network.NetworkManager) nm).getNetHandler();
            if (h != null && h.getClass().getSimpleName().contains("Play")) state = "joined - receiving the world";
            else if (h != null && h.getClass().getSimpleName().contains("Login")) state = "logging in (mods + registries handshake)";
        }
        row("State", state);
        row("Waiting", DPBoot.fmt(now - phaseStart));
    }

    // ---- #21 server resource pack download (GuiScreenWorking) — speed from the file growing on disk
    private static long dlBytes, dlAt;
    private static File dlFile;
    private static double dlSpeed;

    private static void working(GuiScreen g, long now) {
        String t = "", st = "";
        int pct = 0;
        try {
            t = String.valueOf((Object) ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreenWorking.class, (net.minecraft.client.gui.GuiScreenWorking) g, "field_146591_a", "title"));
            st = String.valueOf((Object) ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreenWorking.class, (net.minecraft.client.gui.GuiScreenWorking) g, "field_146589_f", "stage"));
            pct = (Integer) ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreenWorking.class, (net.minecraft.client.gui.GuiScreenWorking) g, "field_146590_g", "progress");
        } catch (Throwable ignored) { }
        title = t.isEmpty() || t.equals("null") ? "Working" : t;
        if (!st.isEmpty() && !st.equals("null")) row("Step", st);
        row("Progress", pct + "%");
        row("Elapsed", DPBoot.fmt(now - phaseStart));
        if (title.toLowerCase(java.util.Locale.ROOT).contains("resource") || st.toLowerCase(java.util.Locale.ROOT).contains("resource")) {
            File newest = newest(new File(Minecraft.getMinecraft().mcDataDir, "server-resource-packs"));
            if (newest != null) {
                if (!newest.equals(dlFile)) { dlFile = newest; dlBytes = newest.length(); dlAt = now; dlSpeed = 0; }
                else if (now - dlAt >= 500) {
                    long b = newest.length();
                    double sp = (b - dlBytes) * 1000.0 / (now - dlAt);
                    dlSpeed = dlSpeed == 0 ? sp : dlSpeed * 0.6 + sp * 0.4;
                    dlBytes = b; dlAt = now;
                }
                row("Downloaded", DPSysInfo.bytes(newest.length()) + (pct > 2 ? " of about " + DPSysInfo.bytes(newest.length() * 100.0 / pct) : ""));
                row("Speed", DPSysInfo.speed(dlSpeed));
                if (pct > 2 && pct < 100) row("ETA", "about " + DPBoot.fmt((now - phaseStart) * (100 - pct) / pct));
            }
        }
    }

    // ---- #22 world loading: chunks, generation rate, entities, tile entities, dimensions, slowest world-gen mods (Zoomies)
    private static long lastChunks, rateAt;
    private static float rate;

    private static void world(Minecraft mc, long now) {
        net.minecraft.server.integrated.IntegratedServer srv = mc.getIntegratedServer();
        if (srv != null && srv.worlds != null && srv.worlds.length > 0) {
            title = "World";
            long chunks = 0, ents = 0, tes = 0;
            int dims = 0;
            for (net.minecraft.world.WorldServer w : srv.worlds) {
                if (w == null) continue;
                dims++;
                chunks += w.getChunkProvider().getLoadedChunkCount();
                ents += w.loadedEntityList.size();
                tes += w.loadedTileEntityList.size();
            }
            if (rateAt == 0 || chunks < lastChunks) { rateAt = now; lastChunks = chunks; rate = 0; }
            else if (now - rateAt >= 1000) {
                float r = (chunks - lastChunks) * 1000f / (now - rateAt);
                rate = rate == 0 ? r : rate * 0.6f + r * 0.4f;
                rateAt = now; lastChunks = chunks;
            }
            row("Chunks", String.format(java.util.Locale.ROOT, "%,d loaded", chunks));
            row("Generating", String.format(java.util.Locale.ROOT, "%.1f chunks/s", rate));
            row("Entities", String.format(java.util.Locale.ROOT, "%,d", ents));
            row("Tile ents", String.format(java.util.Locale.ROOT, "%,d", tes));
            row("Dimensions", dims + " loaded");
            String top = topWorldGen();
            if (top != null) row("Slowest gen", top);
            mapSnapshot(srv.worlds[0], now);
            return;
        }
        if (mc.world != null) {                                // multiplayer: what's arrived from the server
            title = "Downloading terrain";
            row("Chunks", mc.world.getChunkProvider().makeString());
            row("Entities", String.format(java.util.Locale.ROOT, "%,d", mc.world.loadedEntityList.size()));
            row("Tile ents", String.format(java.util.Locale.ROOT, "%,d", mc.world.loadedTileEntityList.size()));
        }
    }

    /** Zoomies' live world-creation profiler: the 3 slowest mods so far (null without Zoomies) */
    @SuppressWarnings("unchecked")
    private static String topWorldGen() {
        try {
            Object rep = Class.forName("com.dogpound.zoomies.Profiler").getField("world").get(null);
            if (rep == null) return null;
            java.util.Map<String, Long> tot = new java.util.HashMap<String, Long>();
            synchronized (rep) {
                for (java.util.Map.Entry<String, java.util.Map<String, Long>> e : ((java.util.Map<String, java.util.Map<String, Long>>) rep.getClass().getField("mods").get(rep)).entrySet()) {
                    long t = 0;
                    for (Long v : e.getValue().values()) t += v;
                    tot.put(e.getKey(), t);
                }
            }
            StringBuilder b = new StringBuilder();
            int k = 0;
            for (java.util.Map.Entry<String, Long> e : DPBoot.ranked(tot)) {
                if (k++ == 3) break;
                if (b.length() > 0) b.append(", ");
                b.append(e.getKey()).append(String.format(java.util.Locale.ROOT, " %.1fs", e.getValue() / 1e9));
            }
            return b.length() == 0 ? null : b.toString();
        } catch (Throwable t) { return null; }
    }

    // ---- #23 live mini map: every loaded overworld chunk as a biome-coloured square around spawn
    static final int MAP_MAX = 4096;
    static final int[] MX = new int[MAP_MAX], MZ = new int[MAP_MAX], MC = new int[MAP_MAX];
    static int mapN, spawnCX, spawnCZ, mapR = 8;
    private static long mapAt;
    private static final java.util.Map<Long, Integer> COLOR = new java.util.HashMap<Long, Integer>();

    private static void mapSnapshot(net.minecraft.world.WorldServer w, long now) {
        if (w == null || now - mapAt < 500) return;
        mapAt = now;
        try {
            net.minecraft.util.math.BlockPos sp = w.getSpawnPoint();
            spawnCX = sp.getX() >> 4; spawnCZ = sp.getZ() >> 4;
            int k = 0, r = 4;
            // the server thread is adding chunks while we look: copy what we can, a hiccup just skips this snapshot
            for (net.minecraft.world.chunk.Chunk c : new java.util.ArrayList<net.minecraft.world.chunk.Chunk>(w.getChunkProvider().getLoadedChunks())) {
                if (c == null || k >= MAP_MAX) continue;
                MX[k] = c.x; MZ[k] = c.z;
                long key = ((long) c.x << 32) ^ (c.z & 0xFFFFFFFFL);
                Integer col = COLOR.get(key);
                if (col == null) { col = biomeColor(c); if (COLOR.size() < 50000) COLOR.put(key, col); }
                MC[k] = col;
                r = Math.max(r, Math.max(Math.abs(c.x - spawnCX), Math.abs(c.z - spawnCZ)));
                k++;
            }
            mapN = k;
            mapR = Math.min(r, 48);
        } catch (Throwable ignored) { }
    }

    private static int biomeColor(net.minecraft.world.chunk.Chunk c) {
        try {
            byte[] ba = c.getBiomeArray();
            net.minecraft.world.biome.Biome b = net.minecraft.world.biome.Biome.getBiome(ba[8 * 16 + 8] & 255);
            if (b == null) return 0xFF5DA040;
            String n = b.getBiomeName().toLowerCase(java.util.Locale.ROOT);
            if (n.contains("ocean")) return n.contains("deep") ? 0xFF1E3A8C : 0xFF2850B0;
            if (n.contains("river")) return 0xFF3A78D8;
            if (n.contains("beach") || n.contains("shore")) return 0xFFE6D8A0;
            if (n.contains("desert")) return 0xFFD8C890;
            if (n.contains("snow") || n.contains("ice") || n.contains("frozen") || n.contains("cold") || n.contains("taiga") && b.isSnowyBiome()) return 0xFFE8F0F8;
            if (n.contains("jungle")) return 0xFF2E8B2E;
            if (n.contains("swamp") || n.contains("marsh") || n.contains("bayou")) return 0xFF4C6A3A;
            if (n.contains("mesa") || n.contains("badland") || n.contains("canyon")) return 0xFFC06030;
            if (n.contains("mountain") || n.contains("extreme") || n.contains("hills") || n.contains("peak") || n.contains("alps")) return 0xFF8A8A8A;
            if (n.contains("savanna")) return 0xFFB0A850;
            if (n.contains("mushroom")) return 0xFFA070B0;
            if (n.contains("forest") || n.contains("wood") || n.contains("grove") || n.contains("taiga")) return 0xFF3C8030;
            float t = b.getDefaultTemperature();
            return t > 1.2f ? 0xFFC8B860 : t < 0.3f ? 0xFF9AB0A0 : 0xFF5DA040;
        } catch (Throwable t) { return 0xFF5DA040; }
    }

    /** the map square; returns its bottom (or y when there's nothing to show) */
    static int drawMap(FontRenderer fr, int x, int y, int size) {
        if (mapN == 0 || n == 0 || size < 40 || !"World".equals(title)) return y;
        int h = size + 22;
        Gui.drawRect(x, y, x + size + 10, y + h, 0xD0140E22);
        fr.drawStringWithShadow("World generation", x + 5, y + 5, PrideFrame.PINK);
        int mx = x + 5, my = y + 17, cells = 2 * mapR + 1;
        float cs = size / (float) cells;
        Gui.drawRect(mx, my, mx + size, my + size, 0xFF080510);
        for (int i = 0; i < mapN; i++) {
            int gx = MX[i] - spawnCX + mapR, gz = MZ[i] - spawnCZ + mapR;
            if (gx < 0 || gz < 0 || gx >= cells || gz >= cells) continue;
            int x1 = mx + (int) (gx * cs), y1 = my + (int) (gz * cs);
            Gui.drawRect(x1, y1, Math.max(x1 + 1, mx + (int) ((gx + 1) * cs)), Math.max(y1 + 1, my + (int) ((gz + 1) * cs)), MC[i]);
        }
        int c = mx + (int) ((mapR + 0.5f) * cs), d = Math.max(2, (int) cs);   // spawn marker
        Gui.drawRect(c - d, c - d + (my - mx), c + d, c + d + (my - mx), PrideFrame.PINK);
        return y + h;
    }

    private static File newest(File dir) {
        File[] fs = dir.listFiles();
        File best = null;
        if (fs != null) for (File f : fs) if (f.isFile() && (best == null || f.lastModified() > best.lastModified())) best = f;
        return best;
    }

    /** the card: left side of the loading screen, in its scaled units */
    static int draw(FontRenderer fr, int x, int y, int w) {
        if (n == 0) return y;
        int h = 22 + n * 10 + 4;
        Gui.drawRect(x, y, x + w, y + h, 0xD0140E22);
        int sw = w / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) Gui.drawRect(x + i * sw, y, i == PrideFrame.RAINBOW.length - 1 ? x + w : x + (i + 1) * sw, y + 2, PrideFrame.RAINBOW[i]);
        fr.drawStringWithShadow(title, x + 5, y + 6, PrideFrame.PINK);
        Gui.drawRect(x + 4, y + 17, x + w - 4, y + 18, 0x40FFFFFF);
        for (int i = 0; i < n; i++) {
            fr.drawStringWithShadow(L[i], x + 5, y + 22 + i * 10, PrideFrame.BLUE);
            fr.drawStringWithShadow(fr.trimStringToWidth(V[i] == null ? "" : V[i], w - 75), x + 70, y + 22 + i * 10, 0xFFFFFF);
        }
        return y + h;
    }
}
