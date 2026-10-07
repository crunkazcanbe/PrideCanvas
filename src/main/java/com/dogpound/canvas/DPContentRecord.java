package com.dogpound.canvas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/** What the in-game browser installed: config/pridecanvas/installed.json, "mr:<id>" / "cf:<id>" -> file, title, version. */
final class DPContentRecord {
    private DPContentRecord() {}

    static final class Entry { String file, title, version, when; }

    private static final File FILE = new File("config/pridecanvas/installed.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Entry> map;

    private static synchronized Map<String, Entry> map() {
        if (map == null) {
            map = new LinkedHashMap<String, Entry>();
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) {
                Map<String, Entry> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Entry>>() {}.getType());
                if (m != null) map.putAll(m);
            } catch (Throwable ignored) { }
        }
        return map;
    }

    static synchronized String file(String key) {
        Entry e = map().get(key);
        return e == null ? null : e.file;
    }

    static synchronized void put(String key, String file, String title, String version) {
        Entry e = new Entry();
        e.file = file; e.title = title; e.version = version;
        e.when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        map().put(key, e);
        try {
            FILE.getParentFile().mkdirs();
            try (Writer w = new FileWriter(FILE)) { GSON.toJson(map, w); }
        } catch (Throwable ignored) { }
    }
}
