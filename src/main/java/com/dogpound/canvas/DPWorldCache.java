package com.dogpound.canvas;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSummary;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast world list for the Singleplayer screen (requested feature).
 *
 * Why vanilla is slow here: for every world it decompresses the WHOLE level.dat (~400 KB-1 MB each in this
 * pack, because Forge stores every mod's block/item IDs in it) and runs the data fixer over it, ~2 s a world.
 * We only need the name, game mode and last-played time, so:
 *  - each world's summary is cached by its level.dat size + modified time (config/dpcanvas-worlds.cache),
 *    so only worlds that changed since last time are read again,
 *  - reading skips the data fixer (it's only for converting very old worlds, not for listing them).
 */
final class DPWorldCache {
    private static final File CACHE = new File("config/dpcanvas-worlds.cache");
    private static final int VERSION = 1;

    private static final class Entry {
        long stamp, size, lastPlayed, sizeOnDisk;
        String name;
        int gameType;
        boolean hardcore, cheats;
    }

    private DPWorldCache() {}

    static List<WorldSummary> list(File savesDir) {
        Map<String, Entry> cache = load();
        Map<String, Entry> fresh = new HashMap<>();
        List<WorldSummary> out = new ArrayList<>();
        File[] dirs = savesDir.listFiles(File::isDirectory);
        if (dirs != null) for (File dir : dirs) {
            File dat = new File(dir, "level.dat");
            if (!dat.isFile()) dat = new File(dir, "level.dat_old");
            if (!dat.isFile()) continue;
            Entry e = cache.get(dir.getName());
            if (e == null || e.stamp != dat.lastModified() || e.size != dat.length()) e = read(dat);
            if (e == null) continue;
            fresh.put(dir.getName(), e);
            out.add(summary(dir.getName(), e));
        }
        save(fresh);
        Collections.sort(out);
        return out;
    }

    private static Entry read(File dat) {
        try {
            NBTTagCompound root = CompressedStreamTools.readCompressed(new FileInputStream(dat));
            NBTTagCompound d = root.getCompoundTag("Data");
            Entry e = new Entry();
            e.stamp = dat.lastModified();
            e.size = dat.length();
            e.name = d.getString("LevelName");
            e.lastPlayed = d.getLong("LastPlayed");
            e.sizeOnDisk = d.getLong("SizeOnDisk");
            e.gameType = d.getInteger("GameType");
            e.hardcore = d.getBoolean("hardcore");
            e.cheats = d.getBoolean("allowCommands");
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    private static WorldSummary summary(String folder, Entry e) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("LevelName", e.name == null || e.name.isEmpty() ? folder : e.name);
        d.setLong("LastPlayed", e.lastPlayed);
        d.setLong("SizeOnDisk", e.sizeOnDisk);
        d.setInteger("GameType", e.gameType);
        d.setBoolean("hardcore", e.hardcore);
        d.setBoolean("allowCommands", e.cheats);
        d.setInteger("version", 19133);
        WorldInfo info = new WorldInfo(d);
        return new WorldSummary(info, folder, info.getWorldName(), 0L, false);
    }

    private static Map<String, Entry> load() {
        Map<String, Entry> m = new HashMap<>();
        if (!CACHE.isFile()) return m;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(CACHE)))) {
            if (in.readInt() != VERSION) return m;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String key = in.readUTF();
                Entry e = new Entry();
                e.stamp = in.readLong(); e.size = in.readLong(); e.lastPlayed = in.readLong(); e.sizeOnDisk = in.readLong();
                e.name = in.readUTF(); e.gameType = in.readInt(); e.hardcore = in.readBoolean(); e.cheats = in.readBoolean();
                m.put(key, e);
            }
        } catch (Throwable t) {
            m.clear();   // corrupt cache -> just re-read everything once
        }
        return m;
    }

    private static void save(Map<String, Entry> m) {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(CACHE)))) {
            out.writeInt(VERSION);
            out.writeInt(m.size());
            for (Map.Entry<String, Entry> me : m.entrySet()) {
                Entry e = me.getValue();
                out.writeUTF(me.getKey());
                out.writeLong(e.stamp); out.writeLong(e.size); out.writeLong(e.lastPlayed); out.writeLong(e.sizeOnDisk);
                out.writeUTF(e.name == null ? "" : e.name); out.writeInt(e.gameType); out.writeBoolean(e.hardcore); out.writeBoolean(e.cheats);
            }
        } catch (Throwable ignored) {}
    }
}
