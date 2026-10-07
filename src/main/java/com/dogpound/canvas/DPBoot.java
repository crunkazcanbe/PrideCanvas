package com.dogpound.canvas;

import net.minecraftforge.fml.common.ProgressManager;

import java.util.Iterator;
import java.util.Locale;

/**
 * Pride Boot Sequence — the data behind the loading screen (her 36-point list, docs/LOADING-SCREEN-ROADMAP.md).
 * Runs on the splash thread. RULE #35: never slow loading — everything here is refreshed at most 5x a second and
 * the drawing code only reads the cached strings below (no string building per frame).
 */
public final class DPBoot {
    private DPBoot() {}

    static final long JVM_START = jvmStart();
    private static long jvmStart() {
        try { return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(); } catch (Throwable t) { return System.currentTimeMillis(); }
    }

    // ---- #1 live dashboard: label / value rows, rebuilt by tick() ----
    static final String[] DASH_L = {"Phase", "Now", "Mods", "Progress", "ETA", "This step", "Speed", "Usually", "Screen"};
    static final String[] DASH_V = {"Starting", "", "-", "0%", "-", "-", "-", "-", "-"};
    static final String[] CARD = {"Starting", "", "", "", ""};    // the compact layout's 5 big lines
    static String curMod = "", curBarTitle = "", curBarMsg = "";
    static int modsDone, modsTotal, modPhasesSeen;
    static boolean inModPhase;
    private static String lastPhaseTitle = "";

    private static long lastTick;
    private static String opKey = "";
    private static long opSince = System.currentTimeMillis();
    // speed: innermost bar steps advanced, measured over a ~5 s window
    private static String speedBar = "";
    private static int speedStep0;
    private static long speedT0;
    private static float speed;

    /** refresh the cached numbers; cheap no-op unless 200 ms passed */
    static void tick(int progressPct) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 200) return;
        lastTick = now;
        try {
            ProgressManager.ProgressBar outer = null, phaseBar = null, inner = null;
            Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
            while (it.hasNext()) {
                ProgressManager.ProgressBar b = it.next();
                if (outer == null) outer = b;
                String t = b.getTitle() == null ? "" : b.getTitle();
                if (isModPhase(t)) phaseBar = b;
                inner = b;
            }
            if (inner == null) return;
            curBarTitle = nz(inner.getTitle());
            timelineStep(curBarTitle, now);
            curBarMsg = nz(inner.getMessage());

            // phase: FML's outer "Loading" bar counts 7 steps; the mod phases are their own bars
            String ph = phaseBar != null ? pretty(phaseBar.getTitle()) : pretty(curBarTitle);
            if (outer != null && outer != inner && outer.getSteps() > 0)
                ph += "  (" + Math.min(outer.getSteps(), outer.getStep()) + "/" + outer.getSteps() + ")";
            DASH_V[0] = ph;
            DASH_V[1] = curBarMsg.isEmpty() ? curBarTitle : curBarTitle + ": " + curBarMsg;

            // stage timing for the report: the bar under FML's outer one (or the only bar)
            String stage = pretty(stageTitle(outer, inner));
            if (!stage.equals(curStage)) { if (!curStage.isEmpty()) STAGE_MS.merge(curStage, now - stageSince, Long::sum); curStage = stage; stageSince = now; }

            inModPhase = phaseBar != null;
            if (phaseBar != null && !nz(phaseBar.getTitle()).equals(lastPhaseTitle)) { lastPhaseTitle = nz(phaseBar.getTitle()); modPhasesSeen++; }
            if (phaseBar != null) {
                modsDone = phaseBar.getStep(); modsTotal = phaseBar.getSteps();
                String m = nz(phaseBar.getMessage());
                if (!m.equals(curMod)) { endMod(now); modSince = now; }
                curMod = m;
                DASH_V[2] = modsDone + " / " + modsTotal + (curMod.isEmpty() ? "" : "  - " + curMod);
            } else if (!curMod.isEmpty()) {
                endMod(now); curMod = "";
            } else if (modsTotal > 0) {
                DASH_V[2] = "all " + modsTotal + " loaded";
            }

            long elapsed = now - JVM_START;
            DASH_V[3] = progressPct + "%   elapsed " + fmt(elapsed);
            DASH_V[4] = eta(elapsed, progressPct);
            int paren = DASH_V[4].indexOf("  (");
            etaShort = paren > 0 ? DASH_V[4].substring(0, paren) : DASH_V[4].startsWith("learning") ? null : DASH_V[4];

            // how long the current operation has been going (bar title + message changing = new op)
            String key = curBarTitle + '\u0000' + curBarMsg + '\u0000' + inner.getStep();
            if (!key.equals(opKey)) { opKey = key; opSince = now; }
            DASH_V[5] = String.format(Locale.ROOT, "%.1f s", (now - opSince) / 1000f);
            stall(now);
            CARD[0] = DASH_V[0] + (inModPhase && modsTotal > 0 ? "   -   mod " + modsDone + " / " + modsTotal : "");
            CARD[1] = DASH_V[1];
            CARD[2] = progressPct + "%   -   " + fmt(elapsed) + "   -   " + (etaShort != null ? etaShort : "learning the ETA");
            CARD[3] = stalled ? "Stuck " + stallFor.replace("no progress for ", "") + (stallWhere.isEmpty() ? "" : " - " + stallWhere) : "Usually: " + DASH_V[7];
            int np = PROBLEMS.size();
            CARD[4] = (np == 0 ? "No problems found" : np + " possible problem" + (np == 1 ? "" : "s") + " - see Issues") + "   -   screen " + String.format(Locale.ROOT, "%.1f ms", costMs);

            // speed on the innermost bar
            String sb = curBarTitle;
            if (!sb.equals(speedBar)) { speedBar = sb; speedStep0 = inner.getStep(); speedT0 = now; speed = 0; }
            else if (now - speedT0 >= 1000) {
                float s = (inner.getStep() - speedStep0) * 1000f / (now - speedT0);
                speed = speed == 0 ? s : speed * 0.6f + s * 0.4f;
                if (now - speedT0 > 5000) { speedStep0 = inner.getStep(); speedT0 = now; }
            }
            String unit = phaseBar == inner ? "mods/s" : curBarTitle.toLowerCase(Locale.ROOT).contains("textur") ? "textures/s"
                    : curBarTitle.toLowerCase(Locale.ROOT).contains("model") ? "models/s" : "steps/s";
            DASH_V[6] = String.format(Locale.ROOT, "%.1f %s   step %d/%d", speed, unit, inner.getStep(), inner.getSteps());
            // #35: what this screen itself costs (ms per frame, share of one CPU core) and its quality tier
            DASH_V[8] = String.format(Locale.ROOT, "%.1f ms/frame @ %d fps = %.1f%% of 1 core%s", costMs, fpsCap, costMs * fpsCap / 10f,
                    turbo ? "  (Turbo)" : tier == 2 ? "  (auto: minimal)" : tier == 1 ? "  (auto: lighter)" : "");
            assets(inner);
        } catch (Throwable ignored) { }
    }

    // ---- #2 meters: CPU / GPU / VRAM / RAM / heap bars + disk / network / memory-pressure text (1x a second) ----
    static final String[] METER_L = {"CPU", "GPU", "VRAM", "RAM", "Heap"};
    static final String[] METER_V = {"", "", "", "", ""};
    static final float[] METER_F = new float[5];
    static final int[] METER_C = new int[5];
    static String diskLine = "", netLine = "", pressure = "", pressureTip = "";
    static int pressureColor = DPBootUI.GOOD;
    private static long lastMeters;

    static void meters() {
        long now = System.currentTimeMillis();
        DPSysInfo.intervalMs = 1000;
        DPSysInfo.want();                              // background sampler, MIN_PRIORITY, 1 s period
        if (now - lastMeters < 1000) return;
        lastMeters = now;
        try {
            meter(0, DPSysInfo.cpu / 100f, DPSysInfo.cpu < 0 ? "n/a" : Math.round(DPSysInfo.cpu) + "%");
            meter(1, DPSysInfo.gpu / 100f, DPSysInfo.gpu < 0 ? "n/a" : Math.round(DPSysInfo.gpu) + "%");
            meter(2, DPSysInfo.vramTotal > 0 ? DPSysInfo.vramUsed / (float) DPSysInfo.vramTotal : -1,
                    DPSysInfo.vramTotal > 0 ? DPSysInfo.bytes(DPSysInfo.vramUsed) + " / " + DPSysInfo.bytes(DPSysInfo.vramTotal) : "n/a");
            long ramUsed = DPSysInfo.ramTotal - DPSysInfo.ramAvail;
            meter(3, DPSysInfo.ramTotal > 0 ? ramUsed / (float) DPSysInfo.ramTotal : -1,
                    DPSysInfo.ramTotal > 0 ? DPSysInfo.bytes(ramUsed) + " / " + DPSysInfo.bytes(DPSysInfo.ramTotal) : "n/a");
            Runtime rt = Runtime.getRuntime();
            long hu = rt.totalMemory() - rt.freeMemory(), hm = rt.maxMemory();
            meter(4, hu / (float) hm, DPSysInfo.bytes(hu) + " / " + DPSysInfo.bytes(hm));
            diskLine = "read " + DPSysInfo.speed(DPSysInfo.diskRead) + "   write " + DPSysInfo.speed(DPSysInfo.diskWrite);
            netLine = "down " + DPSysInfo.speed(DPSysInfo.netDown) + "   up " + DPSysInfo.speed(DPSysInfo.netUp);
            // memory pressure: heap nearly full = GC thrash; system RAM nearly gone = swapping / OOM killer
            long gcN = 0, gcMs = 0;
            for (java.lang.management.GarbageCollectorMXBean g : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                if (g.getCollectionCount() > 0) gcN += g.getCollectionCount();
                if (g.getCollectionTime() > 0) gcMs += g.getCollectionTime();
            }
            float heap = hu / (float) hm, ram = DPSysInfo.ramTotal > 0 ? ramUsed / (float) DPSysInfo.ramTotal : 0;
            if (heap > 0.92f || ram > 0.95f) { pressure = "HIGH"; pressureColor = DPBootUI.BAD; }
            else if (heap > 0.80f || ram > 0.88f) { pressure = "rising"; pressureColor = DPBootUI.WARN; }
            else { pressure = "OK"; pressureColor = DPBootUI.GOOD; }
            pressureTip = "GC " + gcN + "x, " + String.format(Locale.ROOT, "%.1f s", gcMs / 1000f) + " total";
        } catch (Throwable ignored) { }
    }

    private static void meter(int i, float frac, String v) {
        METER_F[i] = frac;
        METER_V[i] = v;
        METER_C[i] = frac < 0 ? DPBootUI.DIM : frac < 0.6f ? DPBootUI.GOOD : frac < 0.85f ? DPBootUI.WARN : DPBootUI.BAD;
    }

    // ---- #3 per-mod load times: Zoomies' profiler live (reflection), else our own timing of the mod-phase bars ----
    static final java.util.Map<String, Long> OWN_MOD_MS = new java.util.HashMap<String, Long>();
    static final java.util.Map<String, Long> STAGE_MS = new java.util.LinkedHashMap<String, Long>();
    private static String curStage = "";
    private static long stageSince = System.currentTimeMillis(), modSince = System.currentTimeMillis();
    static final int TOP_N = 8;
    static final String[] TOP_NAME = new String[TOP_N], TOP_V = new String[TOP_N];
    static final float[] TOP_F = new float[TOP_N];
    static int topCount;
    static boolean topFromZoomies;
    private static long lastTop;
    private static Object zReport;                 // com.dogpound.zoomies.Profiler.STARTUP
    private static java.lang.reflect.Field zMods;
    private static boolean zTried;

    private static String stageTitle(ProgressManager.ProgressBar outer, ProgressManager.ProgressBar inner) {
        try {
            Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
            ProgressManager.ProgressBar first = it.hasNext() ? it.next() : null;
            ProgressManager.ProgressBar second = it.hasNext() ? it.next() : null;
            if (first != null && "Loading".equals(first.getTitle()) && second != null) return nz(second.getTitle());
            return first == null ? "" : nz(first.getTitle());
        } catch (Throwable t) { return ""; }
    }

    /** during loading (splash thread) = PrideCanvas' own timing ONLY: touching Zoomies' classes from the splash thread
     *  loaded classes off the main thread and crashed Mixin (ConcurrentModificationException, 2026-10-05) */
    static java.util.Map<String, Long> modTimes() {
        java.util.Map<String, Long> out = new java.util.HashMap<String, Long>();
        synchronized (OWN_MOD_MS) { out.putAll(OWN_MOD_MS); }
        topFromZoomies = false;
        return out;
    }

    /** main thread, after loading: Zoomies' profiler numbers when it's installed, else our own */
    @SuppressWarnings("unchecked")
    static java.util.Map<String, Long> modTimesAfterLoad() {
        if (!zTried) {
            zTried = true;
            try {
                Class<?> p = Class.forName("com.dogpound.zoomies.Profiler");
                zReport = p.getField("STARTUP").get(null);
                zMods = zReport.getClass().getField("mods");
            } catch (Throwable t) { zReport = null; }
        }
        java.util.Map<String, Long> out = new java.util.HashMap<String, Long>();
        if (zReport != null) {
            try {
                synchronized (zReport) {
                    for (java.util.Map.Entry<String, java.util.Map<String, Long>> e : ((java.util.Map<String, java.util.Map<String, Long>>) zMods.get(zReport)).entrySet()) {
                        long n = 0;
                        for (Long v : e.getValue().values()) n += v;
                        out.put(e.getKey(), n / 1_000_000L);
                    }
                }
                topFromZoomies = true;
                return out;
            } catch (Throwable t) { zReport = null; }
        }
        return modTimes();
    }

    static java.util.List<java.util.Map.Entry<String, Long>> ranked(java.util.Map<String, Long> m) {
        java.util.List<java.util.Map.Entry<String, Long>> l = new java.util.ArrayList<java.util.Map.Entry<String, Long>>(m.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return l;
    }

    /** refresh the "slowest mods" panel, once every 2 s */
    static void top() {
        long now = System.currentTimeMillis();
        if (now - lastTop < 2000) return;
        lastTop = now;
        try {
            java.util.List<java.util.Map.Entry<String, Long>> l = ranked(modTimes());
            topCount = Math.min(TOP_N, l.size());
            long max = topCount > 0 ? Math.max(1, l.get(0).getValue()) : 1;
            for (int i = 0; i < topCount; i++) {
                TOP_NAME[i] = l.get(i).getKey();
                TOP_V[i] = String.format(Locale.ROOT, "%.1f s", l.get(i).getValue() / 1000f);
                TOP_F[i] = l.get(i).getValue() / (float) max;
            }
        } catch (Throwable ignored) { }
    }

    // ---- load finished (first main menu): history + the data the "Why so long?" report shows ----
    static long totalMs = -1;
    static java.util.List<java.util.Map.Entry<String, Long>> finalMods;
    static java.util.Map<String, Long> finalStages;

    /** called once from the main menu (game thread) */
    static void finish() {
        if (totalMs >= 0) return;
        long now = System.currentTimeMillis();
        totalMs = now - JVM_START;
        if (!curStage.isEmpty()) STAGE_MS.merge(curStage, now - stageSince, Long::sum);
        long staged = 0;
        for (long v : STAGE_MS.values()) staged += v;
        if (totalMs - staged > 1000) STAGE_MS.put("Java start + after the bars", totalMs - staged);
        finalStages = new java.util.LinkedHashMap<String, Long>(STAGE_MS);
        finalMods = ranked(modTimesAfterLoad());
        DPSysInfo.intervalMs = 500;
        history();                                     // read the earlier launches BEFORE adding this one
        try {
            java.io.File f = new java.io.File("config/pride-boot-history.txt");
            f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), (System.currentTimeMillis() + "\t" + totalMs + "\t" + modsTotal + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
        System.out.println("[Pride UI] boot took " + fmt(totalMs));
        if (frames > 0) System.out.println(String.format(Locale.ROOT, "[Pride UI] loading screen cost: %d frames, %.1f ms/frame average, %.1f s drawing in total (%.2f%% of the load time on one core)",
                frames, totalCostMs / frames, totalCostMs / 1000, 100 * totalCostMs / Math.max(1, totalMs)));
        DPSafeMode.onFinished();
        saveModSnapshot();
    }

    private static void endMod(long now) {
        if (curMod.isEmpty()) return;
        synchronized (OWN_MOD_MS) { OWN_MOD_MS.merge(curMod, now - modSince, Long::sum); }
    }

    // ---- earlier launches: our own config/pride-boot-history.txt, plus Zoomies' saved startup reports ----
    private static java.util.List<long[]> history;   // {epochMs, totalMs}, oldest first, NOT including this launch

    static synchronized java.util.List<long[]> history() {
        if (history != null) return history;
        java.util.List<long[]> h = new java.util.ArrayList<long[]>();
        try {
            java.io.File f = new java.io.File("config/pride-boot-history.txt");
            if (f.isFile()) for (String l : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] p = l.split("\t");
                if (p.length >= 2) h.add(new long[]{Long.parseLong(p[0].trim()), Long.parseLong(p[1].trim())});
            }
        } catch (Throwable ignored) { }
        long oldest = h.isEmpty() ? Long.MAX_VALUE : h.get(0)[0];
        try {   // Zoomies' older reports (only the ones from before our own history starts)
            java.io.File[] fs = new java.io.File("config/zoomies-profile").listFiles();
            java.util.regex.Pattern tm = java.util.regex.Pattern.compile("\"totalMs\":\\s*(\\d+)");
            if (fs != null) for (java.io.File z : fs) {
                if (!z.getName().startsWith("startup-") || z.lastModified() >= oldest - 60000) continue;
                byte[] head = new byte[400];
                int n;
                try (java.io.InputStream in = new java.io.FileInputStream(z)) { n = in.read(head); }
                java.util.regex.Matcher m = tm.matcher(new String(head, 0, Math.max(0, n), java.nio.charset.StandardCharsets.UTF_8));
                if (m.find()) h.add(new long[]{z.lastModified(), Long.parseLong(m.group(1))});
            }
        } catch (Throwable ignored) { }
        h.removeIf(e -> e[1] < 5000 || e[1] > 6 * 3600_000L);
        h.sort((a, b) -> Long.compare(a[0], b[0]));
        return history = h;
    }

    static long avgMs(int lastN) {
        java.util.List<long[]> h = history();
        if (h.isEmpty()) return -1;
        long sum = 0; int n = 0;
        for (int i = h.size() - 1; i >= 0 && n < lastN; i--, n++) sum += h.get(i)[1];
        return sum / n;
    }

    /** text rows for the report: this launch vs last / average / best */
    static java.util.List<String> historyLines() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.util.List<long[]> h = history();
        if (totalMs >= 0) out.add("This launch   \u00a7d" + fmt(totalMs));
        if (h.isEmpty()) { out.add("\u00a77first launch we know of - next time you'll see a comparison"); return out; }
        long last = h.get(h.size() - 1)[1], best = Long.MAX_VALUE;
        for (long[] e : h) best = Math.min(best, e[1]);
        long avg = avgMs(10);
        out.add("Last time     \u00a7f" + fmt(last) + diff(totalMs, last));
        out.add("Average (" + Math.min(10, h.size()) + ")   \u00a7f" + fmt(avg) + diff(totalMs, avg));
        out.add("Fastest       \u00a7f" + fmt(best) + (fastestYet() ? "  \u00a7a\u2605 fastest startup yet!" : ""));
        // today / yesterday
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis(), yesterday = today - 86_400_000L;
        long tSum = totalMs >= 0 ? totalMs : 0, ySum = 0; int tN = totalMs >= 0 ? 1 : 0, yN = 0;
        for (long[] e : h) { if (e[0] >= today) { tSum += e[1]; tN++; } else if (e[0] >= yesterday) { ySum += e[1]; yN++; } }
        if (tN > 0) out.add("Today         \u00a7f" + tN + " launch" + (tN == 1 ? "" : "es") + ", avg " + fmt(tSum / tN));
        if (yN > 0) out.add("Yesterday     \u00a7f" + yN + " launch" + (yN == 1 ? "" : "es") + ", avg " + fmt(ySum / yN));
        return out;
    }

    static boolean fastestYet() {
        if (totalMs < 0) return false;
        java.util.List<long[]> h = history();
        if (h.size() < 2) return false;                // one earlier launch isn't much of a record
        for (long[] e : h) if (e[1] <= totalMs) return false;
        return true;
    }

    private static String diff(long now, long then) {
        if (now < 0 || then <= 0) return "";
        long d = now - then;
        if (Math.abs(d) < 3000) return "  \u00a77(about the same)";
        return d < 0 ? "  \u00a7a(" + fmt(-d) + " faster now)" : "  \u00a7c(" + fmt(d) + " slower now)";
    }

    // ---- #5 error detection: warnings / errors sorted into "potential problem" cards (scanned 1x a second) ----
    static final String[] KIND = {"Missing models / textures", "Missing dependency", "Duplicate ID / conflict", "Mod failed to load", "Exception"};
    static final int[] KIND_C = {DPBootUI.WARN, DPBootUI.BAD, DPBootUI.WARN, DPBootUI.BAD, DPBootUI.BAD};
    private static final java.util.regex.Pattern P_ASSET = java.util.regex.Pattern.compile("(?i)(model|texture|blockstate|variant|\\.png|\\.json|sprite)");
    private static final java.util.regex.Pattern P_DEP = java.util.regex.Pattern.compile("(?i)(missing ?mods?|missing dependenc|requires? .*(mod|version)|dependency|not (installed|found).*mod)");
    private static final java.util.regex.Pattern P_DUP = java.util.regex.Pattern.compile("(?i)(duplicate|already (registered|exists|in use|occupied)|id conflict|conflicting|id mismatch|dangerous alternative prefix)");
    private static final java.util.regex.Pattern P_FAIL = java.util.regex.Pattern.compile("(?i)(failed to load|has failed|caught exception from|errored|could not (load|initiali[sz]e|construct)|unable to (load|construct))");
    /** one "potential problem" card (same message again = count goes up) */
    static final class Problem {
        final int kind; final String msg, logger, thrown, key; int count = 1; final long time;
        String jar;            // #17: the mod jar this is about, when we can tell (null = unknown)
        boolean jarTried, repaired;
        Problem(int kind, DPLogBuffer.Line l, String key) { this.kind = kind; msg = l.msg; logger = l.logger; thrown = l.thrown; this.key = key; time = l.time; }
    }

    private static final java.util.regex.Pattern P_PAREN = java.util.regex.Pattern.compile("[(\\[]([A-Za-z0-9_\\-]{2,64})[)\\]]");

    /** #17 startup repair: which mod jar a problem is about — a "(modid)" in the message or the logger name, looked up in Forge's mod list */
    /** mod id / lower-case name -> jar file name in mods/, filled on the MAIN thread at pre-init (DPBootViews.snapshot) */
    static final java.util.Map<String, String> MOD_JAR = new java.util.concurrent.ConcurrentHashMap<String, String>();

    static String jarOf(Problem p) {
        if (p.jarTried) return p.jar;
        p.jarTried = true;
        java.util.List<String> cands = new java.util.ArrayList<String>();
        java.util.regex.Matcher m = P_PAREN.matcher(p.msg);
        while (m.find()) cands.add(m.group(1));
        if (p.logger != null) cands.add(p.logger);
        for (String c : cands) {
            String j = MOD_JAR.get(c);
            if (j == null) j = MOD_JAR.get(c.toLowerCase(Locale.ROOT));
            if (j != null) return p.jar = j;
        }
        return null;
    }
    static final java.util.List<Problem> PROBLEMS = new java.util.ArrayList<Problem>();
    static volatile boolean serious;              // a missing dependency or a mod that failed to load
    private static int problemsVer = -1;
    private static long problemsAt, scannedTo;

    /** "@Mixin target ... was not found" etc.: our own PridePatches / Zoomies mixins aimed at mods this pack doesn't have
     *  - harmless, so no "potential problem" card for them */
    static boolean mixinNoise(DPLogBuffer.Line l, String t) {
        String lg = l.logger == null ? "" : l.logger.toLowerCase(Locale.ROOT), lt = t.toLowerCase(Locale.ROOT);
        return lg.contains("mixin") || lt.contains("@mixin") || lt.contains("mixin target") || lt.contains("mixins.json")
                || (lt.contains("mixin") && (lt.contains("not found") || lt.contains("was not") || lt.contains("could not find")));
    }

    static void scanProblems() {
        long now = System.currentTimeMillis();
        if (now - problemsAt < 1000 || DPLogBuffer.version == problemsVer) return;
        problemsAt = now; problemsVer = DPLogBuffer.version;
        try {
            long newest = scannedTo;
            for (DPLogBuffer.Line l : DPLogBuffer.snapshot(true)) {
                if (l.time <= scannedTo) continue;
                newest = Math.max(newest, l.time);
                String t = l.msg + (l.thrown == null ? "" : " " + l.thrown);
                if (mixinNoise(l, t)) continue;   // stays in the log's Errors / Warnings tabs, just no card
                int kind = P_DEP.matcher(t).find() ? 1 : P_DUP.matcher(t).find() ? 2 : P_FAIL.matcher(t).find() ? 3
                        : P_ASSET.matcher(t).find() ? 0 : l.lvl >= 3 && (l.thrown != null || t.contains("Exception")) ? 4 : -1;
                if (kind < 0) continue;
                String key = kind == 0 ? "asset" : kind + ":" + (l.msg.length() > 60 ? l.msg.substring(0, 60) : l.msg);
                Problem hit = null;
                for (Problem p : PROBLEMS) if (p.key.equals(key)) { hit = p; break; }
                if (hit != null) hit.count++;
                else if (PROBLEMS.size() < 40) PROBLEMS.add(new Problem(kind, l, key));
                if (kind == 1 || kind == 3) serious = true;
            }
            scannedTo = newest;
        } catch (Throwable ignored) { }
    }

    // ---- #6 "What's taking so long?": no progress for 10 s -> say what it's stuck on + peek at the loading thread ----
    static boolean stalled;
    static String stallWhat = "", stallFor = "", stallWhere = "", stallHint = "";
    private static long lastPeek;
    private static Thread mainThread;
    private static final String[] SKIP = {"java.", "javax.", "sun.", "jdk.", "com.sun.", "net.minecraft.", "net.minecraftforge.", "com.google.",
            "org.apache.", "it.unimi.", "org.lwjgl.", "org.spongepowered.", "zone.rong.", "com.cleanroommc.", "com.dogpound.canvas.", "io.netty.",
            "com.mojang.", "paulscode.", "LZMA.", "org.objectweb.", "kotlin.", "scala."};

    private static void stall(long now) {
        long idle = now - opSince;
        stalled = idle >= 10_000;
        if (!stalled) return;
        stallWhat = DASH_V[1];
        stallFor = "no progress for " + fmt(idle) + (curMod.isEmpty() ? "" : "   (mod: " + curMod + ")");
        if (now - lastPeek < 2000) return;   // a stack peek every 2 s, only while stuck
        lastPeek = now;
        try {
            if (mainThread == null) mainThread = findMain();
            StackTraceElement[] st = mainThread == null ? null : mainThread.getStackTrace();
            if (st == null || st.length == 0) { stallWhere = ""; stallHint = ""; return; }
            StackTraceElement top = st[0], mod = null;
            for (StackTraceElement e : st) {
                boolean skip = false;
                for (String p : SKIP) if (e.getClassName().startsWith(p)) { skip = true; break; }
                if (!skip) { mod = e; break; }
            }
            stallWhere = mod != null ? "inside " + shortName(mod) : "inside Minecraft / Forge: " + shortName(top);
            String c = top.getClassName() + "." + top.getMethodName();
            stallHint = c.contains("Inflater") || c.contains("zip") || c.contains("Jar") ? "reading a mod jar"
                    : c.contains("imageio") || c.contains("PNG") || c.contains("png") ? "decoding an image"
                    : c.contains("Socket") || c.contains("sun.nio.ch") || c.contains("http") ? "waiting on the network (a download?)"
                    : c.contains("park") || c.contains(".wait") || c.contains("sleep") || c.contains("Semaphore") || c.contains("Lock") ? "waiting on another thread"
                    : c.contains("FileInputStream") || c.contains("RandomAccessFile") || c.contains("readBytes") ? "reading files from disk"
                    : c.contains("ClassLoader") || c.contains("defineClass") || c.contains("transform") ? "loading / transforming classes"
                    : c.contains("glTex") || c.contains("GL11") ? "uploading textures to the GPU"
                    : "working (CPU)";
        } catch (Throwable ignored) { }
    }

    private static String shortName(StackTraceElement e) {
        String c = e.getClassName();
        int dot = c.lastIndexOf('.');
        String pkg = dot > 0 ? c.substring(0, dot) : "";
        return c.substring(dot + 1) + "." + e.getMethodName() + (pkg.isEmpty() ? "" : "  (" + pkg + ")");
    }

    static Thread findMain() {
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (g.getParent() != null) g = g.getParent();
        Thread[] all = new Thread[g.activeCount() + 16];
        int n = g.enumerate(all, true);
        for (String want : new String[]{"Client thread", "Render thread", "main"})
            for (int i = 0; i < n; i++) if (all[i] != null && want.equals(all[i].getName())) return all[i];
        return null;
    }

    // ---- #7 ETA learned from earlier launches ----
    // Step-matched like Zoomies' Eta, but read straight from its saved file (config/zoomies-timeline.txt: "offsetMs<TAB>title",
    // last line "totalMs<TAB>#END") - plain Java on the splash thread, never Zoomies' classes.
    private static java.util.List<Object[]> tl;
    private static long tlTotal = -1, tlOffset = -1, tlAt, tlFrom;
    private static String tlTitle = "";

    private static void timelineStep(String title, long now) {
        if (title.equals(tlTitle)) return;
        tlTitle = title;
        if (tl == null) {
            tl = new java.util.ArrayList<Object[]>();
            try {
                for (String l : java.nio.file.Files.readAllLines(new java.io.File("config/zoomies-timeline.txt").toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    String[] p = l.split("\t", 2);
                    if (p.length < 2) continue;
                    if (p[1].equals("#END")) tlTotal = Long.parseLong(p[0]); else tl.add(new Object[]{Long.parseLong(p[0]), p[1]});
                }
            } catch (Throwable ignored) { }
        }
        for (Object[] o : tl) {                                  // first time this step happened at/after where we are now
            long off = (Long) o[0];
            if (off >= tlFrom && title.equals(o[1])) { tlOffset = off; tlFrom = off; tlAt = now; return; }
        }
    }

    /** ms left by last launch's timeline, or -1 when there's nothing to match */
    private static long timelineLeft() {
        if (tlTotal <= 0 || tlOffset < 0) return -1;
        return Math.max(0, tlTotal - tlOffset - (System.currentTimeMillis() - tlAt));
    }

    private static String usual;
    static volatile String etaShort;          // for the right end of the load bar

    /** Zoomies' stage-matched ETA first (best), else your average launch, else a guess from the progress bar */
    static String eta(long elapsed, int pct) {
        if (usual == null) {
            java.util.List<long[]> h = history();
            if (h.isEmpty()) usual = "first launch we know of - learning";
            else {
                long avg = avgMs(10), best = Long.MAX_VALUE;
                for (long[] e : h) best = Math.min(best, e[1]);
                usual = "avg " + fmt(avg) + " (" + Math.min(10, h.size()) + " runs)   last " + fmt(h.get(h.size() - 1)[1]) + "   best " + fmt(best) + "   first " + fmt(h.get(0)[1]);
            }
            DASH_V[7] = usual;
        }
        long z = timelineLeft();
        if (z >= 0) return z < 5000 ? "almost done  (step-matched)" : "about " + fmt(z) + " left  (step-matched)";
        long avg = avgMs(10);
        if (avg > 0) {
            long left = avg - elapsed;
            return left > 5000 ? "about " + fmt(left) + " left  (from your average)" : "any moment now (past your average)";
        }
        if (pct >= 8 && pct < 98) return "roughly " + fmt(elapsed * (100 - pct) / pct) + " left  (guess from progress)";
        return "learning (first launch)";
    }

    // ---- #8 disk activity: the game's own reads/writes (/proc/self/io), assets loaded, the file being loaded now ----
    static final String[] DISK_L = {"Game reads", "Game writes", "Assets", "Now"};
    static final String[] DISK_V = {"-", "-", "0", "-"};
    static int assetsLoaded;
    static String assetNow = "";
    private static String assetBar = "";
    private static int assetStep;
    private static long lastIo, ioR, ioRDisk, ioW, ioT, ioTotalR;
    private static boolean noProcIo;

    private static boolean isAssetBar(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.contains("textur") || l.contains("model") || l.contains("sound") || l.contains("resource");
    }

    private static void assets(ProgressManager.ProgressBar inner) {
        String t = nz(inner.getTitle());
        if (!isAssetBar(t)) return;
        int st = inner.getStep();
        if (t.equals(assetBar)) { if (st > assetStep) assetsLoaded += st - assetStep; }
        else assetBar = t;
        assetStep = st;
        String m = nz(inner.getMessage());
        if (!m.isEmpty()) assetNow = m;
        DISK_V[2] = String.format(Locale.ROOT, "%,d loaded   (%s)", assetsLoaded, t);
        DISK_V[3] = assetNow;
    }

    static void disk() {
        long now = System.currentTimeMillis();
        if (now - lastIo < 1000 || noProcIo) return;
        lastIo = now;
        try {
            long r = 0, rd = 0, w = 0;
            for (String l : java.nio.file.Files.readAllLines(new java.io.File("/proc/self/io").toPath())) {
                if (l.startsWith("rchar:")) r = Long.parseLong(l.substring(6).trim());
                else if (l.startsWith("read_bytes:")) rd = Long.parseLong(l.substring(11).trim());
                else if (l.startsWith("write_bytes:")) w = Long.parseLong(l.substring(12).trim());
            }
            if (ioT > 0) {
                double dt = (now - ioT) / 1000.0;
                DISK_V[0] = DPSysInfo.speed((r - ioR) / dt) + "   (" + DPSysInfo.speed((rd - ioRDisk) / dt) + " from the drive, rest cached)";
                DISK_V[1] = DPSysInfo.speed((w - ioW) / dt) + "   total read " + DPSysInfo.bytes(r);
            }
            ioR = r; ioRDisk = rd; ioW = w; ioT = now;
        } catch (Throwable t) {
            noProcIo = true;   // not Linux: the System panel still shows whole-disk speed
            DISK_V[0] = "n/a here (see System > Disk)";
            DISK_V[1] = "n/a here";
        }
    }

    // ---- #13 "What's New": the pack's own notes (config/pride-whats-new.txt) + mods added / updated / removed since
    //      the last launch that reached the menu (mods/ folder vs config/pride-mods-snapshot.txt) ----
    static java.util.List<String> news;                // lines; first char = '+' added, '-' removed, '~' updated, '#' heading

    static java.util.List<String> whatsNew() {
        if (news != null) return news;
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            java.io.File notes = new java.io.File("config/pride-whats-new.txt");
            if (notes.isFile()) {
                out.add("#Pack notes");
                for (String l : java.nio.file.Files.readAllLines(notes.toPath(), java.nio.charset.StandardCharsets.UTF_8))
                    if (!l.trim().isEmpty() && !l.startsWith("//")) out.add(" " + l.trim());
            }
            java.io.File snap = new java.io.File("config/pride-mods-snapshot.txt");
            if (snap.isFile()) {
                java.util.List<String> before = java.nio.file.Files.readAllLines(snap.toPath(), java.nio.charset.StandardCharsets.UTF_8), now = DPSafeMode.modJars();
                java.util.Map<String, String> oldBy = new java.util.HashMap<String, String>(), newBy = new java.util.HashMap<String, String>();
                for (String j : before) if (!j.trim().isEmpty()) oldBy.put(DPSafeMode.baseName(j), j);
                for (String j : now) newBy.put(DPSafeMode.baseName(j), j);
                java.util.List<String> add = new java.util.ArrayList<String>(), rem = new java.util.ArrayList<String>(), upd = new java.util.ArrayList<String>();
                for (java.util.Map.Entry<String, String> e : newBy.entrySet()) {
                    String o = oldBy.get(e.getKey());
                    if (o == null) add.add("+" + e.getValue().replace(".jar", ""));
                    else if (!o.equals(e.getValue())) upd.add("~" + e.getValue().replace(".jar", ""));
                }
                for (java.util.Map.Entry<String, String> e : oldBy.entrySet()) if (!newBy.containsKey(e.getKey())) rem.add("-" + e.getValue().replace(".jar", ""));
                java.util.Collections.sort(add); java.util.Collections.sort(upd); java.util.Collections.sort(rem);
                if (add.isEmpty() && upd.isEmpty() && rem.isEmpty()) out.add("#No mod changes since last time (" + now.size() + " mods)");
                else {
                    out.add("#Since last time: " + add.size() + " added, " + upd.size() + " updated, " + rem.size() + " removed");
                    out.addAll(add); out.addAll(upd); out.addAll(rem);
                }
            } else out.add("#First launch with Pride What's New - changes show from next time");
            java.util.List<String> off = DPSafeMode.offList();
            if (!off.isEmpty()) {
                out.add(0, "#Safe mode: " + off.size() + " mod(s) turned off");
                for (int k = 0; k < off.size(); k++) out.add(1 + k, "-" + off.get(k).replace(".jar", ""));
            }
        } catch (Throwable t) { out.add("#couldn't read the mods folder"); }
        return news = out;
    }

    private static void saveModSnapshot() {
        try {
            whatsNew();   // diff against the OLD snapshot first
            StringBuilder b = new StringBuilder();
            for (String j : DPSafeMode.modJars()) b.append(j).append('\n');
            java.nio.file.Files.write(new java.io.File("config/pride-mods-snapshot.txt").toPath(), b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    // ---- #14 context-aware tips: a new one every 9 s, picked from what's happening right now ----
    static String tip = "";
    private static long tipAt;
    private static int tipN;
    private static final String[] TIPS = {
            "Trans rights are human rights. Also: Minecraft is loading.",
            "Tip: press / to search the log, the Errors tab shows only the scary stuff.",
            "Tip: play Tetris over there on the left - nobody is watching.",
            "Fun fact: every mod here politely waits its turn. Mostly.",
            "Tip: the 'Why so long?' chip on the main menu shows who took the time.",
            "Loading screens are just suspense with extra steps.",
            "Hydrate! The block game will still be here.",
            "Tip: creepers hate hugs. Respect their boundaries.",
            "You are valid, and so is this progress bar.",
            "Tip: the music player has shuffle. Live a little.",
            "Somewhere a villager just went 'hrmm'.",
            "Fun fact: a big pack does more work here than the first Moon landing computer did. Probably.",
    };

    static void tips() {
        long now = System.currentTimeMillis();
        if (now - tipAt < 9000) return;
        tipAt = now;
        java.util.List<String> ctx = new java.util.ArrayList<String>();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour >= 1 && hour < 6) ctx.add("It's " + hour + " AM. Sleep is also a valid mod. Just saying.");
        if (stalled) ctx.add("Still going - " + (curMod.isEmpty() ? "this step" : curMod) + " is thinking very hard. It's not frozen.");
        if (pressure.equals("HIGH")) ctx.add("Memory is nearly full - if loading crawls, give Java more RAM (or fewer tabs).");
        if (topCount > 0) ctx.add(TOP_NAME[0] + " is the slowest mod so far (" + TOP_V[0] + "). Rude.");
        if (modsTotal > 300) ctx.add("Loading " + modsTotal + " mods. That's " + modsTotal + " tiny miracles.");
        String b = curBarTitle.toLowerCase(Locale.ROOT);
        if (b.contains("textur")) ctx.add("Stitching textures into one giant atlas, like a very nerdy quilt.");
        if (b.contains("model")) ctx.add("Baking models. Not the cake kind, sadly.");
        if (b.contains("sound")) ctx.add("Loading sounds - get ready for the bloop.");
        if (!PROBLEMS.isEmpty()) ctx.add(PROBLEMS.size() + " possible problem" + (PROBLEMS.size() == 1 ? "" : "s") + " found - click a card above the log to see it.");
        if (history().isEmpty()) ctx.add("First launch we know of: next time the ETA learns from this one.");
        long avg = avgMs(10);
        if (avg > 0 && System.currentTimeMillis() - JVM_START < avg * 6 / 10) ctx.add("Usually takes " + fmt(avg) + ". You've got time for a game.");
        // every other tip is about right now, the rest are just for fun
        tip = (tipN++ % 2 == 0 && !ctx.isEmpty()) ? ctx.get((int) (now / 9000 % ctx.size())) : TIPS[(int) (now / 9000 % TIPS.length)];
        if (!DPBootSettings.on("prideMsgs", true) && (tip.startsWith("Trans rights") || tip.startsWith("You are valid"))) tip = TIPS[2];
    }

    // ---- #26 clock + playtime (Prism/MultiMC keep the total in ../instance.cfg)
    static String clock = "", playtime = "";
    private static long clockAt, playedSec = -2;

    static void clock() {
        long now = System.currentTimeMillis();
        if (now - clockAt < 1000) return;
        clockAt = now;
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hr = c.get(java.util.Calendar.HOUR);
        clock = (hr == 0 ? 12 : hr) + ":" + String.format(Locale.ROOT, "%02d", c.get(java.util.Calendar.MINUTE)) + (c.get(java.util.Calendar.AM_PM) == 0 ? " AM" : " PM");
        if (playedSec == -2) {
            playedSec = -1;
            try {
                for (String l : java.nio.file.Files.readAllLines(new java.io.File("../instance.cfg").toPath(), java.nio.charset.StandardCharsets.ISO_8859_1))
                    if (l.startsWith("totalTimePlayed=")) playedSec = Long.parseLong(l.substring(16).trim());
            } catch (Throwable ignored) { }
        }
        long session = (now - JVM_START) / 1000;
        playtime = (playedSec >= 0 ? "played " + (playedSec + session) / 3600 + "h " + ((playedSec + session) % 3600) / 60 + "m in this pack   " : "") + "this start " + fmt(now - JVM_START);
    }

    // ---- #30 / #31 registry counters + achievement toasts ----
    static final String[] REG_L = {"Blocks", "Items", "Entities", "Biomes", "Sounds", "Recipes", "Enchantments", "Potions", "Professions"};
    static final int[] REG_N = new int[REG_L.length];
    static final float[] REG_SHOWN = new float[REG_L.length];          // eased toward REG_N for the rolling counters
    static final java.util.concurrent.ConcurrentLinkedDeque<String> TOASTS = new java.util.concurrent.ConcurrentLinkedDeque<String>();
    static final int[] MILESTONES = {100, 500, 1000, 2500, 5000, 10000, 20000, 50000};
    static final int[] REG_MILE = new int[REG_L.length];

    static boolean isModPhase(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.endsWith(" mods") || l.startsWith("mod loading complete");
    }

    private static String pretty(String t) {
        if (t == null) return "";
        String l = t.toLowerCase(Locale.ROOT);
        if (l.startsWith("constructing")) return "Constructing mods";
        if (l.startsWith("pre-init")) return "Pre-init";
        if (l.startsWith("initializing")) return "Init";
        if (l.startsWith("post-init")) return "Post-init";
        return t;
    }

    static String nz(String s) { return s == null ? "" : s; }

    static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    // ---- #35 never slow loading: measure our own frame cost, cap the frame rate, drop quality if we get expensive ----
    private static long frameStart, lastFrameEnd;
    static float costMs;              // smoothed ms per frame spent drawing
    static int tier;                  // 0 full, 1 lighter, 2 minimal
    static double totalCostMs;
    static long frames;
    static int fpsCap = 30;
    static boolean lowQuality;

    static void frameBegin() { frameStart = System.nanoTime(); }
    static long excludeNs;

    // ---- #34 Turbo: everything for loading - 10 fps, still picture, no side panels / sampling / visualizer, the
    //      loading thread first in line for the CPU (thread priorities only count where the OS honours them)
    static boolean turbo;
    private static boolean turboApplied;

    static void turbo(boolean on) {
        turbo = on;
        if (turboApplied == on) return;
        turboApplied = on;
        try {
            Thread main = findMain();
            if (main != null) main.setPriority(on ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
            Thread.currentThread().setPriority(on ? Thread.MIN_PRIORITY : Thread.NORM_PRIORITY);
        } catch (Throwable ignored) { }
    }

    /** end of our drawing: record the cost, then sleep so the splash thread runs at fpsCap (frees CPU for loading) */
    static void frameEnd() {
        long t = System.nanoTime();
        float c = Math.max(0, (t - frameStart - excludeNs)) / 1e6f;   // one-off wallpaper decodes don't count (they tripped minimal mode)
        excludeNs = 0;
        costMs = costMs == 0 ? c : costMs * 0.95f + c * 0.05f;
        totalCostMs += c; frames++;
        // adaptive (#35): tier 1 = 15 fps + still wallpaper, tier 2 = 10 fps + side panels off; back down when cheap
        boolean adaptive = DPBootSettings.show("adaptive");      // Options > Loading Screen: adaptive quality (#35) + max FPS
        if (!adaptive) tier = 0;
        else if (tier == 0 && costMs > 10f) tier = 1;
        else if (tier == 1 && costMs > 20f) tier = 2;
        else if (tier == 2 && costMs < 12f) tier = 1;
        else if (tier == 1 && costMs < 6f) tier = 0;
        lowQuality = tier >= 1;
        fpsCap = Math.max(5, Math.min(DPBootSettings.getInt("fps", 30), turbo || tier == 2 ? 10 : tier == 1 ? 15 : 60));
        long budget = 1_000_000_000L / fpsCap;
        long wait = budget - (t - lastFrameEnd);
        if (lastFrameEnd != 0 && wait > 1_000_000L) {
            try { Thread.sleep(wait / 1_000_000L); } catch (InterruptedException ignored) { }
        }
        lastFrameEnd = System.nanoTime();
    }
}
