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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pride Content: everything the in-game browser needs from Modrinth + CurseForge (requested feature).
 * Search with categories/sort/paging, project pages (description, gallery, 1.12.2 versions, changelogs), and installs into the
 * right folder: mods -> the active mod-group folder, resource packs, shader packs, worlds unpacked into saves/. Required
 * dependencies of mods are installed with them. All network work runs on worker threads; the GUI reads volatile fields.
 * CurseForge goes through the keyless api.curse.tools mirror (see DPModApi for why).
 */
public final class DPContentApi {
    private DPContentApi() {}

    public static final int MODRINTH = 0, CURSEFORGE = 1;
    static final String MC = "1.12.2";
    private static final String UA = "pridecanvas/1.0 (Pride modpack in-game content browser; github.com/crunkazcanbe)";
    private static final String CF = "https://api.curse.tools/v1";
    private static final String MR = "https://api.modrinth.com/v2";

    /** what kind of thing you're browsing */
    public enum Kind {
        MODS("Mods", "✦", "mod", 6),
        RESOURCEPACKS("Resource Packs", "✿", "resourcepack", 12),
        SHADERS("Shaders", "☀", "shader", 6552),
        WORLDS("Worlds", "◉", null, 17);
        public final String label, icon, mrType;
        public final int cfClass;
        Kind(String label, String icon, String mrType, int cfClass) { this.label = label; this.icon = icon; this.mrType = mrType; this.cfClass = cfClass; }
        public boolean on(int site) { return site == CURSEFORGE || mrType != null; }
    }

    public static final String[][] SORTS = {
            { "Relevance", "Downloads", "Follows", "Newest", "Updated" },          // Modrinth index
            { "Popularity", "Downloads", "Updated", "Name A-Z", "Featured" } };   // CurseForge sortField
    private static final String[] MR_INDEX = { "relevance", "downloads", "follows", "newest", "updated" };
    private static final int[] CF_FIELD = { 2, 6, 3, 4, 1 };
    private static final String[] CF_ORDER = { "desc", "desc", "desc", "asc", "desc" };

    // ------------------------------------------------------------------ data

    public static final class Project {
        public int site;
        public Kind kind;
        public String id, slug, title, desc, author, iconUrl, updated, pageUrl;
        public long downloads, follows;
        public final List<String> categories = new ArrayList<String>();
        // filled by details()
        public volatile String body;                                 // plain text, paragraphs separated by \n
        public final List<String[]> gallery = Collections.synchronizedList(new ArrayList<String[]>());   // {url, title}
        public final List<Version> versions = Collections.synchronizedList(new ArrayList<Version>());
        public final Map<String, String> links = Collections.synchronizedMap(new LinkedHashMap<String, String>());
        public volatile String license;
        public volatile boolean detailsLoaded, detailsLoading;
        public volatile String detailsError;
        public String key() { return (site == MODRINTH ? "mr:" : "cf:") + id; }
    }

    public static final class Version {
        public String id, name, number, date, changelog, fileUrl, fileName, type;
        public long size, downloads;
        public final List<String> requires = new ArrayList<String>();   // project ids (same site)
    }

    public static final class Category {
        public final String id, name;
        Category(String id, String name) { this.id = id; this.name = name; }
    }

    /** one running/finished install; the GUI shows these in the queue strip */
    public static final class Job {
        public final Project project;
        public final String label;
        public volatile long done, total = -1;
        public volatile String state = "Waiting";                   // Waiting / Downloading / Unpacking / Done / Failed: why
        public volatile boolean finished, failed;
        Job(Project p, String label) { this.project = p; this.label = label; }
        public float progress() { return total > 0 ? Math.min(1F, done / (float) total) : finished ? 1F : 0F; }
    }

    // ------------------------------------------------------------------ state the GUI reads

    public static volatile List<Project> results = new ArrayList<Project>();
    public static volatile boolean searching, more = true;
    public static volatile String searchError;
    public static volatile int searchGen;
    public static volatile List<Category> categories = new ArrayList<Category>();
    public static final List<Job> jobs = Collections.synchronizedList(new ArrayList<Job>());
    /** something was installed that needs a restart (mods), or a reload (resource packs) */
    public static volatile int needRestart, newPacks;

    private static final ExecutorService NET = Executors.newFixedThreadPool(3, r -> { Thread t = new Thread(r, "PrideContent-net"); t.setDaemon(true); return t; });
    private static final ExecutorService INSTALL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "PrideContent-install"); t.setDaemon(true); return t; });
    private static final Map<String, List<Category>> CAT_CACHE = new HashMap<String, List<Category>>();

    // ------------------------------------------------------------------ search

    public static void search(int site, Kind kind, String query, int sort, String category, boolean append) {
        final int gen = append ? searchGen : ++searchGen;
        final int offset = append ? results.size() : 0;
        if (!append) { results = new ArrayList<Project>(); more = true; }
        if (!kind.on(site)) { searching = false; more = false; searchError = kind.label + " are only on CurseForge."; return; }
        searching = true;
        searchError = null;
        NET.submit(() -> {
            try {
                List<Project> got = site == CURSEFORGE ? searchCurse(kind, query, sort, category, offset) : searchModrinth(kind, query, sort, category, offset);
                if (gen != searchGen) return;                         // a newer search started meanwhile
                List<Project> all = new ArrayList<Project>(append ? results : Collections.<Project>emptyList());
                all.addAll(got);
                results = all;
                more = got.size() >= 30;
                if (all.isEmpty()) searchError = "Nothing for 1.12.2 matched.";
            } catch (Throwable t) {
                if (gen == searchGen) searchError = shortError(t);
            } finally {
                if (gen == searchGen) searching = false;
            }
        });
    }

    private static List<Project> searchModrinth(Kind kind, String q, int sort, String cat, int offset) throws Exception {
        StringBuilder facets = new StringBuilder("[[\"versions:" + MC + "\"],[\"project_type:" + kind.mrType + "\"]");
        if (kind == Kind.MODS) facets.append(",[\"categories:forge\"]");
        if (cat != null) facets.append(",[\"categories:").append(cat).append("\"]");
        facets.append("]");
        String url = MR + "/search?limit=30&offset=" + offset + "&index=" + MR_INDEX[clamp(sort, 5)] + "&facets=" + enc(facets.toString())
                + (q == null || q.isEmpty() ? "" : "&query=" + enc(q));
        JsonArray hits = getJson(url).getAsJsonObject().getAsJsonArray("hits");
        List<Project> out = new ArrayList<Project>();
        for (JsonElement e : hits) {
            JsonObject o = e.getAsJsonObject();
            Project p = new Project();
            p.site = MODRINTH; p.kind = kind;
            p.id = str(o, "project_id"); p.slug = str(o, "slug"); p.title = str(o, "title"); p.desc = str(o, "description");
            p.author = str(o, "author"); p.iconUrl = str(o, "icon_url"); p.updated = day(str(o, "date_modified"));
            p.downloads = lng(o, "downloads"); p.follows = lng(o, "follows");
            p.pageUrl = "https://modrinth.com/" + kind.mrType + "/" + p.slug;
            JsonArray c = o.getAsJsonArray("display_categories");
            if (c != null) for (JsonElement x : c) if (!"forge".equals(x.getAsString())) p.categories.add(x.getAsString());
            if (p.id != null) out.add(p);
        }
        return out;
    }

    private static List<Project> searchCurse(Kind kind, String q, int sort, String cat, int offset) throws Exception {
        String url = CF + "/mods/search?gameId=432&classId=" + kind.cfClass + "&pageSize=30&index=" + offset + "&gameVersion=" + MC
                + "&sortField=" + CF_FIELD[clamp(sort, 5)] + "&sortOrder=" + CF_ORDER[clamp(sort, 5)]
                + (kind == Kind.MODS ? "&modLoaderType=1" : "")
                + (cat != null ? "&categoryId=" + cat : "")
                + (q == null || q.isEmpty() ? "" : "&searchFilter=" + enc(q));
        JsonArray data = getJson(url).getAsJsonObject().getAsJsonArray("data");
        List<Project> out = new ArrayList<Project>();
        for (JsonElement e : data) out.add(curseProject(e.getAsJsonObject(), kind));
        return out;
    }

    private static Project curseProject(JsonObject o, Kind kind) {
        Project p = new Project();
        p.site = CURSEFORGE; p.kind = kind;
        p.id = str(o, "id"); p.slug = str(o, "slug"); p.title = str(o, "name"); p.desc = str(o, "summary");
        p.downloads = lng(o, "downloadCount"); p.follows = lng(o, "thumbsUpCount");
        p.updated = day(str(o, "dateModified"));
        JsonObject logo = o.has("logo") && o.get("logo").isJsonObject() ? o.getAsJsonObject("logo") : null;
        if (logo != null) p.iconUrl = str(logo, "thumbnailUrl");
        JsonArray a = o.getAsJsonArray("authors");
        p.author = a != null && a.size() > 0 ? str(a.get(0).getAsJsonObject(), "name") : "?";
        JsonArray c = o.getAsJsonArray("categories");
        if (c != null) for (JsonElement x : c) p.categories.add(str(x.getAsJsonObject(), "name"));
        JsonObject l = o.has("links") && o.get("links").isJsonObject() ? o.getAsJsonObject("links") : null;
        p.pageUrl = l != null ? str(l, "websiteUrl") : null;
        return p;
    }

    // ------------------------------------------------------------------ categories

    public static void loadCategories(int site, Kind kind) {
        String k = site + ":" + kind;
        synchronized (CAT_CACHE) { if (CAT_CACHE.containsKey(k)) { categories = CAT_CACHE.get(k); return; } }
        categories = new ArrayList<Category>();
        if (!kind.on(site)) return;
        NET.submit(() -> {
            try {
                List<Category> out = new ArrayList<Category>();
                if (site == MODRINTH) {
                    for (JsonElement e : getJson(MR + "/tag/category").getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        String type = str(o, "project_type"), name = str(o, "name"), header = str(o, "header");
                        if (!kind.mrType.equals(type) || "forge".equals(name)) continue;
                        if (kind == Kind.SHADERS && "performance impact".equals(header)) continue;
                        out.add(new Category(name, pretty(name)));
                    }
                } else {
                    for (JsonElement e : getJson(CF + "/categories?gameId=432&classId=" + kind.cfClass).getAsJsonObject().getAsJsonArray("data")) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("isClass") && o.get("isClass").getAsBoolean()) continue;
                        out.add(new Category(str(o, "id"), str(o, "name")));
                    }
                }
                out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                synchronized (CAT_CACHE) { CAT_CACHE.put(k, out); }
                categories = out;
            } catch (Throwable ignored) { }
        });
    }

    // ------------------------------------------------------------------ project page

    public static void details(Project p) {
        if (p.detailsLoaded || p.detailsLoading) return;
        p.detailsLoading = true;
        NET.submit(() -> {
            try {
                if (p.site == MODRINTH) modrinthDetails(p); else curseDetails(p);
                p.detailsLoaded = true;
            } catch (Throwable t) {
                p.detailsError = shortError(t);
            } finally {
                p.detailsLoading = false;
            }
        });
    }

    private static void modrinthDetails(Project p) throws Exception {
        JsonObject o = getJson(MR + "/project/" + p.id).getAsJsonObject();
        p.body = markdownToText(str(o, "body"));
        JsonArray g = o.getAsJsonArray("gallery");
        if (g != null) for (JsonElement e : g) { JsonObject x = e.getAsJsonObject(); p.gallery.add(new String[]{ str(x, "url"), nz(str(x, "title")) }); }
        for (String k : new String[]{ "source_url", "issues_url", "wiki_url", "discord_url" }) { String u = str(o, k); if (u != null) p.links.put(pretty(k.replace("_url", "")), u); }
        if (o.has("license") && o.get("license").isJsonObject()) p.license = str(o.getAsJsonObject("license"), "id");
        String loaders = p.kind == Kind.MODS ? "&loaders=" + enc("[\"forge\"]") : "";
        for (JsonElement e : getJson(MR + "/project/" + p.id + "/version?game_versions=" + enc("[\"" + MC + "\"]") + loaders).getAsJsonArray()) {
            JsonObject v = e.getAsJsonObject();
            Version ver = new Version();
            ver.id = str(v, "id"); ver.name = str(v, "name"); ver.number = str(v, "version_number"); ver.type = str(v, "version_type");
            ver.date = day(str(v, "date_published")); ver.changelog = markdownToText(str(v, "changelog")); ver.downloads = lng(v, "downloads");
            JsonArray files = v.getAsJsonArray("files");
            JsonObject pick = null;
            if (files != null) for (JsonElement fe : files) { JsonObject f = fe.getAsJsonObject(); if (pick == null || (f.has("primary") && f.get("primary").getAsBoolean())) pick = f; }
            if (pick != null) { ver.fileUrl = str(pick, "url"); ver.fileName = str(pick, "filename"); ver.size = lng(pick, "size"); }
            JsonArray deps = v.getAsJsonArray("dependencies");
            if (deps != null) for (JsonElement de : deps) {
                JsonObject d = de.getAsJsonObject();
                if ("required".equals(str(d, "dependency_type")) && str(d, "project_id") != null) ver.requires.add(str(d, "project_id"));
            }
            if (ver.fileUrl != null) p.versions.add(ver);
        }
    }

    private static void curseDetails(Project p) throws Exception {
        try { p.body = htmlToText(getJson(CF + "/mods/" + p.id + "/description").getAsJsonObject().get("data").getAsString()); }
        catch (Throwable t) { p.body = p.desc; }
        JsonObject o = getJson(CF + "/mods/" + p.id).getAsJsonObject().getAsJsonObject("data");
        JsonArray shots = o.getAsJsonArray("screenshots");
        if (shots != null) for (JsonElement e : shots) { JsonObject x = e.getAsJsonObject(); p.gallery.add(new String[]{ str(x, "url"), nz(str(x, "title")) }); }
        JsonObject l = o.has("links") && o.get("links").isJsonObject() ? o.getAsJsonObject("links") : null;
        if (l != null) for (String k : new String[]{ "sourceUrl", "issuesUrl", "wikiUrl" }) { String u = str(l, k); if (u != null && !u.isEmpty()) p.links.put(pretty(k.replace("Url", "")), u); }
        JsonArray data = getJson(CF + "/mods/" + p.id + "/files?gameVersion=" + MC + "&pageSize=50" + (p.kind == Kind.MODS ? "&modLoaderType=1" : "")).getAsJsonObject().getAsJsonArray("data");
        for (JsonElement e : data) {
            JsonObject f = e.getAsJsonObject();
            Version v = new Version();
            v.id = str(f, "id"); v.name = str(f, "displayName"); v.number = v.name; v.fileName = str(f, "fileName");
            v.date = day(str(f, "fileDate")); v.size = lng(f, "fileLength"); v.downloads = lng(f, "downloadCount");
            int rt = f.has("releaseType") ? f.get("releaseType").getAsInt() : 1;
            v.type = rt == 1 ? "release" : rt == 2 ? "beta" : "alpha";
            v.fileUrl = str(f, "downloadUrl");
            if (v.fileUrl == null && v.id != null && v.fileName != null) {             // author turned off 3rd-party links: the CDN still has it
                long fid = Long.parseLong(v.id);
                v.fileUrl = "https://edge.forgecdn.net/files/" + (fid / 1000) + "/" + (fid % 1000) + "/" + v.fileName.replace(" ", "%20");
            }
            JsonArray deps = f.getAsJsonArray("dependencies");
            if (deps != null) for (JsonElement de : deps) {
                JsonObject d = de.getAsJsonObject();
                if (d.has("relationType") && d.get("relationType").getAsInt() == 3) v.requires.add(str(d, "modId"));
            }
            p.versions.add(v);
        }
        p.versions.sort((a, b) -> nz(b.date).compareTo(nz(a.date)));
    }

    /** the CurseForge-side project behind a dependency id, for its name and files */
    private static Project fetchProject(int site, String id, Kind kind) throws Exception {
        if (site == CURSEFORGE) return curseProject(getJson(CF + "/mods/" + id).getAsJsonObject().getAsJsonObject("data"), kind);
        JsonObject o = getJson(MR + "/project/" + id).getAsJsonObject();
        Project p = new Project();
        p.site = MODRINTH; p.kind = kind; p.id = str(o, "id"); p.slug = str(o, "slug"); p.title = str(o, "title");
        p.desc = str(o, "description"); p.iconUrl = str(o, "icon_url");
        return p;
    }

    // ------------------------------------------------------------------ installing

    public static File modDir;                                            // set by the GUI (the active mod-group folder)

    public static File folderFor(Kind k) {
        File game = net.minecraft.client.Minecraft.getMinecraft().mcDataDir;
        switch (k) {
            case MODS: return modDir != null ? modDir : new File(game, "mods");
            case RESOURCEPACKS: return new File(game, "resourcepacks");
            case SHADERS: return new File(game, "shaderpacks");
            default: return new File(game, "saves");
        }
    }

    /** already on disk? by the record we keep, or the exact file name in the folder */
    public static boolean installed(Project p) {
        String f = DPContentRecord.file(p.key());
        if (f != null && (new File(folderFor(p.kind), f).exists() || new File(folderFor(p.kind), f + ".disabled").exists())) return true;
        return false;
    }

    public static boolean busy(Project p) {
        synchronized (jobs) { for (Job j : jobs) if (!j.finished && j.project.key().equals(p.key())) return true; }
        return false;
    }

    /** install the given version (null = newest for 1.12.2), and for mods every required dependency that is missing */
    public static void install(Project p, Version v) {
        Job job = new Job(p, p.title);
        jobs.add(job);
        INSTALL.submit(() -> {
            try {
                Version pick = v;
                if (pick == null) {
                    if (!p.detailsLoaded) { if (p.site == MODRINTH) modrinthDetails(p); else curseDetails(p); p.detailsLoaded = true; }
                    pick = newest(p);
                }
                if (pick == null) throw new Exception("no 1.12.2 file");
                installOne(job, p, pick);
                if (p.kind == Kind.MODS) for (String dep : pick.requires) installDependency(p.site, dep, 0);
            } catch (Throwable t) {
                job.failed = true;
                job.state = "Failed: " + shortError(t);
            } finally {
                job.finished = true;
            }
        });
    }

    private static Version newest(Project p) {
        synchronized (p.versions) {
            for (Version x : p.versions) if ("release".equals(x.type)) return x;
            return p.versions.isEmpty() ? null : p.versions.get(0);
        }
    }

    private static void installDependency(int site, String id, int depth) {
        if (depth > 4 || id == null) return;
        String key = (site == MODRINTH ? "mr:" : "cf:") + id;
        if (DPContentRecord.file(key) != null) return;
        Job job = null;
        try {
            Project d = fetchProject(site, id, Kind.MODS);
            if (installed(d)) return;
            job = new Job(d, d.title + " (needed)");
            jobs.add(job);
            if (site == MODRINTH) modrinthDetails(d); else curseDetails(d);
            Version v = newest(d);
            if (v == null) throw new Exception("no 1.12.2 file");
            if (alreadyHasJar(v.fileName)) { job.state = "Already there"; DPContentRecord.put(key, v.fileName, d.title, v.number); return; }
            installOne(job, d, v);
            for (String more : v.requires) installDependency(site, more, depth + 1);
        } catch (Throwable t) {
            if (job != null) { job.failed = true; job.state = "Failed: " + shortError(t); }
        } finally {
            if (job != null) job.finished = true;
        }
    }

    private static boolean alreadyHasJar(String name) {
        if (name == null) return false;
        File dir = folderFor(Kind.MODS);
        return new File(dir, name).exists() || new File(dir, name + ".disabled").exists();
    }

    private static void installOne(Job job, Project p, Version v) throws Exception {
        File dir = folderFor(p.kind);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("can't make " + dir.getName());
        String name = safeName(v.fileName != null ? v.fileName : p.title + ".zip");
        job.state = "Downloading";
        if (p.kind == Kind.WORLDS) {
            File tmp = new File(dir, "." + name + ".part");
            fetch(v.fileUrl, tmp, job);
            job.state = "Unpacking";
            String folder = unpackWorld(tmp, dir, p.title);
            tmp.delete();
            DPContentRecord.put(p.key(), folder, p.title, v.number);
        } else {
            fetch(v.fileUrl, new File(dir, name), job);
            DPContentRecord.put(p.key(), name, p.title, v.number);
        }
        if (p.kind == Kind.MODS) needRestart++;
        if (p.kind == Kind.RESOURCEPACKS) newPacks++;
        job.state = "Done";
    }

    private static void fetch(String url, File dest, Job job) throws Exception {
        HttpURLConnection c = open(url);
        job.total = c.getContentLengthLong();
        File tmp = new File(dest.getAbsolutePath() + ".dl");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); job.done += n; }
        }
        if (dest.exists() && !dest.delete()) throw new Exception("can't replace " + dest.getName());
        if (!tmp.renameTo(dest)) throw new Exception("can't write " + dest.getName());
    }

    /** unpack a world zip into saves/<name>: finds the folder holding level.dat, refuses paths that climb out */
    private static String unpackWorld(File zip, File saves, String title) throws Exception {
        String root = null;
        try (ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName().replace('\\', '/');
                if (n.equals("level.dat") || n.endsWith("/level.dat")) { String r = n.substring(0, n.length() - "level.dat".length()); if (root == null || r.length() < root.length()) root = r; }
            }
        }
        if (root == null) throw new Exception("no world inside (no level.dat)");
        String folder = safeName(title).replaceAll("\\.zip$", "");
        File target = new File(saves, folder);
        for (int i = 2; target.exists(); i++) target = new File(saves, folder + " (" + i + ")");
        String canon = target.getCanonicalPath() + File.separator;
        try (ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(zip))) {
            byte[] buf = new byte[65536];
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName().replace('\\', '/');
                if (!n.startsWith(root)) continue;
                String rel = n.substring(root.length());
                if (rel.isEmpty()) continue;
                File out = new File(target, rel);
                if (!out.getCanonicalPath().startsWith(canon)) throw new Exception("unsafe path in the zip");
                if (e.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (OutputStream o = new FileOutputStream(out)) { for (int r; (r = z.read(buf)) > 0; ) o.write(buf, 0, r); }
            }
        }
        return target.getName();
    }

    // ------------------------------------------------------------------ text from Markdown / HTML (no web view: plain wrapped text)

    static String markdownToText(String md) {
        if (md == null) return "";
        String s = md.replace("\r", "");
        s = s.replaceAll("(?s)<!--.*?-->", "");
        s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");                 // images
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");               // links -> their text
        s = s.replaceAll("(?m)^#{1,6}\\s*(.*)$", "§d§l$1§r");  // headings in pink bold
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "  • ");                  // bullets
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "§l$1§r").replaceAll("__([^_]+)__", "§l$1§r");
        s = s.replaceAll("`{1,3}", "");
        s = htmlToText(s);
        return s.replaceAll("\n{3,}", "\n\n").trim();
    }

    static String htmlToText(String h) {
        if (h == null) return "";
        String s = h.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|h[1-6]|li|tr)>", "\n").replaceAll("(?i)<li[^>]*>", "  • ");
        s = s.replaceAll("(?i)<h[1-6][^>]*>", "§d§l");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'");
        s = s.replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    // ------------------------------------------------------------------ plumbing

    static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code / 100 == 3) {                                               // http -> https redirects aren't followed by Java
            String loc = c.getHeaderField("Location");
            if (loc != null) return open(loc);
        }
        if (code / 100 != 2) throw new Exception("HTTP " + code);
        return c;
    }

    static JsonElement getJson(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStreamReader r = new InputStreamReader(c.getInputStream(), "UTF-8")) { return new JsonParser().parse(r); }
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String str(JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    private static long lng(JsonObject o, String k) { try { return o.has(k) && !o.get(k).isJsonNull() ? (long) o.get(k).getAsDouble() : 0L; } catch (Throwable t) { return 0L; } }
    private static String nz(String s) { return s == null ? "" : s; }
    private static int clamp(int s, int n) { return s < 0 || s >= n ? 0 : s; }
    private static String day(String iso) { return iso == null || iso.length() < 10 ? "" : iso.substring(0, 10); }
    private static String safeName(String n) { return n.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("^\\.+", "_"); }

    static String pretty(String s) {
        if (s == null || s.isEmpty()) return "";
        s = s.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String shortError(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) m = t.getClass().getSimpleName();
        return m.length() > 70 ? m.substring(0, 70) + "…" : m;
    }

    public static String count(long n) {
        if (n >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000L) return String.format(Locale.ROOT, "%.1fK", n / 1e3);
        return Long.toString(n);
    }

    public static String size(long b) {
        if (b <= 0) return "";
        if (b >= 1 << 20) return String.format(Locale.ROOT, "%.1f MB", b / (double) (1 << 20));
        return String.format(Locale.ROOT, "%d KB", Math.max(1, b >> 10));
    }
}
