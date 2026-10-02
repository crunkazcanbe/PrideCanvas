package com.dogpound.canvas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod search + download straight off the Modrinth / CurseForge REST APIs -- the way Prism does it,
 * no browser involved. All network work happens on a worker thread; the GUI polls the volatile
 * fields. Neither site needs credentials.
 *
 * CurseForge goes through the public api.curse.tools mirror, NOT api.curseforge.com. Their official
 * API refuses /v1/mods/search for Minecraft (gameId 432) on ordinary Studios keys -- verified: a
 * valid key gets 200 on /v1/games/432 and on /v1/mods/{id}, but 403 on every search variant. The
 * mirror serves the identical payload, keyless. If a key ever gets Minecraft-search approval, drop
 * it in ~/.config/dogpound/cf-api-key and it is sent along.
 */
public final class DPModApi {
    private DPModApi() {}

    public static final int MODRINTH = 0, CURSEFORGE = 1;
    private static final String UA = "dogpound-canvas/1.0 (Minecraft 1.12.2 mod browser)";
    private static final String MC = "1.12.2";
    /** Keyless CurseForge mirror; same routes as api.curseforge.com/v1. */
    private static final String CF_BASE = "https://api.curse.tools/v1";

    // Each site exposes its own sort orders; don't pretend they're the same list.
    public static final String[] SORT_MR_LABEL = {"Relevance", "Downloads", "Follows", "Newest", "Updated"};
    private static final String[] MR_INDEX     = {"relevance", "downloads", "follows", "newest", "updated"};

    // CurseForge sortField: 1=Featured 2=Popularity 3=LastUpdated 4=Name 5=Author 6=TotalDownloads
    public static final String[] SORT_CF_LABEL = {"Popularity", "Downloads", "Name A-Z", "Updated", "Featured"};
    private static final int[]    CF_FIELD     = {2, 6, 4, 3, 1};
    private static final String[] CF_ORDER     = {"desc", "desc", "asc", "desc", "desc"};

    public static String[] sortLabels(int site) { return site == CURSEFORGE ? SORT_CF_LABEL : SORT_MR_LABEL; }
    public static String sortLabel(int site, int sort) {
        String[] l = sortLabels(site);
        return l[(sort < 0 || sort >= l.length) ? 0 : sort];
    }

    /** One search result. */
    public static class Hit {
        public String id, title, desc, author;
        public long downloads;
        /** Resolved lazily on download. */
        public String fileUrl, fileName;
    }

    // ---- results the GUI reads (volatile: written by the worker, read by the render thread) ----
    public static volatile List<Hit> results = new ArrayList<Hit>();
    public static volatile boolean loading = false;
    public static volatile String error = null;
    /** Set to the Hit currently downloading, else null. */
    public static volatile Hit downloading = null;
    /** Bumped whenever a mod finishes downloading, so the GUI can rescan the folder. */
    public static volatile int downloadsDone = 0;

    public static File cfKeyFile() {
        return new File(System.getProperty("user.home", "/home/user"),
                ".config/dogpound/cf-api-key");
    }

    public static String cfKey() {
        File f = cfKeyFile();
        if (!f.isFile()) return null;
        try {
            byte[] b = new byte[(int) f.length()];
            InputStream in = new java.io.FileInputStream(f);
            try { //noinspection ResultOfMethodCallIgnored
                in.read(b);
            } finally { in.close(); }
            String s = new String(b, "UTF-8").trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) { return null; }
    }

    /** Always true: the mirror needs no key. Kept so the GUI can stay dumb. */
    public static boolean cfAvailable() { return true; }

    // ---------------- search ----------------

    /** Kick off a search on a worker thread. Safe to call from the render thread. */
    public static void searchAsync(final int site, final String query, final int sort) {
        loading = true;
        error = null;
        results = new ArrayList<Hit>();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    List<Hit> out = (site == CURSEFORGE) ? searchCurse(query, sort) : searchModrinth(query, sort);
                    results = out;
                    if (out.isEmpty()) error = "No 1.12.2 mods matched.";
                } catch (Throwable t) {
                    error = shortError(t);
                } finally {
                    loading = false;
                }
            }
        }, "DPModApi-search");
        t.setDaemon(true);
        t.start();
    }

    private static String shortError(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) m = t.getClass().getSimpleName();
        return m.length() > 60 ? m.substring(0, 60) + "..." : m;
    }

    private static List<Hit> searchModrinth(String query, int sort) throws Exception {
        String facets = "[[\"versions:" + MC + "\"],[\"project_type:mod\"]]";
        String url = "https://api.modrinth.com/v2/search"
                + "?limit=40"
                + "&index=" + MR_INDEX[clampSort(sort, MR_INDEX.length)]
                + "&facets=" + enc(facets)
                + (query.isEmpty() ? "" : "&query=" + enc(query));

        JsonObject root = getJson(url, null).getAsJsonObject();
        JsonArray hits = root.getAsJsonArray("hits");
        List<Hit> out = new ArrayList<Hit>();
        for (JsonElement e : hits) {
            JsonObject o = e.getAsJsonObject();
            Hit h = new Hit();
            h.id = str(o, "project_id");
            h.title = str(o, "title");
            h.desc = str(o, "description");
            h.author = str(o, "author");
            h.downloads = o.has("downloads") && !o.get("downloads").isJsonNull() ? o.get("downloads").getAsLong() : 0L;
            if (h.id != null) out.add(h);
        }
        return out;
    }

    private static List<Hit> searchCurse(String query, int sort) throws Exception {
        String key = cfKey();   // optional; the mirror ignores it
        String url = CF_BASE + "/mods/search"
                + "?gameId=432&classId=6&pageSize=40"
                + "&gameVersion=" + MC
                + "&sortField=" + CF_FIELD[clampSort(sort, CF_FIELD.length)]
                + "&sortOrder=" + CF_ORDER[clampSort(sort, CF_ORDER.length)]
                + (query.isEmpty() ? "" : "&searchFilter=" + enc(query));

        JsonObject root = getJson(url, key).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        List<Hit> out = new ArrayList<Hit>();
        for (JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            Hit h = new Hit();
            h.id = str(o, "id");
            h.title = str(o, "name");
            h.desc = str(o, "summary");
            h.downloads = o.has("downloadCount") && !o.get("downloadCount").isJsonNull()
                    ? (long) o.get("downloadCount").getAsDouble() : 0L;
            JsonArray authors = o.getAsJsonArray("authors");
            h.author = (authors != null && authors.size() > 0)
                    ? str(authors.get(0).getAsJsonObject(), "name") : "?";
            // CurseForge hands us the files inline; grab a 1.12.2 one now.
            JsonArray files = o.getAsJsonArray("latestFiles");
            if (files != null) {
                for (JsonElement fe : files) {
                    JsonObject f = fe.getAsJsonObject();
                    JsonArray gv = f.getAsJsonArray("gameVersions");
                    if (gv == null) continue;
                    for (JsonElement v : gv) {
                        if (MC.equals(v.getAsString())) {
                            h.fileUrl = str(f, "downloadUrl");
                            h.fileName = str(f, "fileName");
                            break;
                        }
                    }
                    if (h.fileUrl != null) break;
                }
            }
            if (h.id != null) out.add(h);
        }
        return out;
    }

    private static int clampSort(int s, int n) { return (s < 0 || s >= n) ? 0 : s; }

    // ---------------- download ----------------

    /** Resolve the jar URL if we don't have it yet, fetch it into dir, then bump downloadsDone. */
    public static void downloadAsync(final int site, final Hit h, final File dir) {
        if (downloading != null) return;   // one at a time; keeps it simple and the UI honest
        downloading = h;
        error = null;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    if (h.fileUrl == null) {
                        if (site == MODRINTH) resolveModrinthFile(h);
                        else                  resolveCurseFile(h);
                    }
                    if (h.fileUrl == null) throw new Exception("No 1.12.2 Forge file for " + h.title);
                    String name = (h.fileName != null && !h.fileName.isEmpty())
                            ? h.fileName : (h.title.replaceAll("[^A-Za-z0-9._-]", "_") + ".jar");
                    fetch(h.fileUrl, new File(dir, name));
                    downloadsDone++;
                } catch (Throwable t) {
                    error = shortError(t);
                } finally {
                    downloading = null;
                }
            }
        }, "DPModApi-download");
        t.setDaemon(true);
        t.start();
    }

    private static void resolveModrinthFile(Hit h) throws Exception {
        String url = "https://api.modrinth.com/v2/project/" + h.id + "/version"
                + "?loaders=" + enc("[\"forge\"]")
                + "&game_versions=" + enc("[\"" + MC + "\"]");
        JsonElement root = getJson(url, null);
        JsonArray versions = root.getAsJsonArray();
        if (versions.size() == 0) throw new Exception("No 1.12.2 Forge build published.");
        JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
        if (files == null || files.size() == 0) throw new Exception("Version has no files.");
        JsonObject pick = files.get(0).getAsJsonObject();
        for (JsonElement fe : files) {                       // prefer the primary jar
            JsonObject f = fe.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) { pick = f; break; }
        }
        h.fileUrl = str(pick, "url");
        h.fileName = str(pick, "filename");
    }

    /** Some CurseForge mods don't inline a 1.12.2 file in search results; ask for their file list. */
    private static void resolveCurseFile(Hit h) throws Exception {
        String url = CF_BASE + "/mods/" + h.id + "/files?gameVersion=" + MC + "&pageSize=20";
        JsonObject root = getJson(url, cfKey()).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null || data.size() == 0) throw new Exception("No 1.12.2 file published.");
        for (JsonElement fe : data) {
            JsonObject f = fe.getAsJsonObject();
            String u = str(f, "downloadUrl");
            if (u != null) { h.fileUrl = u; h.fileName = str(f, "fileName"); return; }
        }
        throw new Exception("CurseForge withheld the download link.");
    }

    private static void fetch(String url, File dest) throws Exception {
        HttpURLConnection c = open(url, null);
        InputStream in = c.getInputStream();
        File tmp = new File(dest.getAbsolutePath() + ".part");
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            try { out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
        if (dest.exists()) dest.delete();
        if (!tmp.renameTo(dest)) throw new Exception("Could not write " + dest.getName());
    }

    // ---------------- plumbing ----------------

    private static HttpURLConnection open(String url, String cfKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        if (cfKey != null) c.setRequestProperty("x-api-key", cfKey);
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new Exception("HTTP " + code);
        return c;
    }

    private static JsonElement getJson(String url, String cfKey) throws Exception {
        HttpURLConnection c = open(url, cfKey);
        InputStreamReader r = new InputStreamReader(c.getInputStream(), "UTF-8");
        try {
            return new JsonParser().parse(r);
        } finally {
            try { r.close(); } catch (Throwable ignored) {}
        }
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    /** 132012241 -> "132.0M" */
    public static String humanCount(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1e9);
        if (n >= 1_000_000L)     return String.format("%.1fM", n / 1e6);
        if (n >= 1_000L)         return String.format("%.1fK", n / 1e3);
        return Long.toString(n);
    }
}
