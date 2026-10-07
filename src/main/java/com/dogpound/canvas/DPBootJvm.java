package com.dogpound.canvas;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * Loading screen #18-#20: JVM diagnostics, thread activity and a heap/GC graph. Sampled once a second on the splash
 * thread from the JVM's own MXBeans (no files, no processes); threads only while that tab is showing.
 */
final class DPBootJvm {
    private DPBootJvm() {}

    // ---- #18 rows
    static final String[] L = {"Java", "VM", "Heap", "Non-heap", "GC", "Pauses", "Classes", "Threads", "JIT", "Uptime"};
    static final String[] V = new String[L.length];

    // ---- #20 heap graph: 120 samples (2 minutes), GC marks where a collection happened
    static final int HIST = 120;
    static final float[] HEAP = new float[HIST];
    static final boolean[] GC = new boolean[HIST];
    static int head;
    static long heapMax;

    // ---- #19 busiest threads
    static final int TN = 9;
    static final String[] T_NAME = new String[TN];
    static final float[] T_PCT = new float[TN];
    static int tCount;
    static boolean wantThreads;

    private static long last, lastGcN, lastGcMs;
    private static final java.util.Map<Long, Long> CPU = new java.util.HashMap<Long, Long>();
    private static long lastThreadSample;

    static void sample() {
        long now = System.currentTimeMillis();
        if (now - last < 1000) return;
        last = now;
        try {
            if (V[0] == null) {
                V[0] = System.getProperty("java.version") + "  (" + System.getProperty("java.vendor", "?") + ")";
                V[1] = System.getProperty("java.vm.name", "?") + "  " + Runtime.getRuntime().availableProcessors() + " cpus";
            }
            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            long hu = mem.getHeapMemoryUsage().getUsed(), hc = mem.getHeapMemoryUsage().getCommitted();
            heapMax = Runtime.getRuntime().maxMemory();
            V[2] = DPSysInfo.bytes(hu) + " used / " + DPSysInfo.bytes(hc) + " committed / " + DPSysInfo.bytes(heapMax) + " max";
            V[3] = DPSysInfo.bytes(mem.getNonHeapMemoryUsage().getUsed()) + " (metaspace, code cache)";
            long n = 0, ms = 0;
            StringBuilder names = new StringBuilder();
            for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
                n += Math.max(0, g.getCollectionCount()); ms += Math.max(0, g.getCollectionTime());
                if (names.length() > 0) names.append(", ");
                names.append(g.getName());
            }
            V[4] = names.toString();
            long dn = n - lastGcN, dms = ms - lastGcMs;
            V[5] = n + " collections, " + String.format(Locale.ROOT, "%.1f s", ms / 1000f) + " total"
                    + (lastGcN > 0 && dn > 0 ? "   last second: " + dn + " (" + dms + " ms)" : "");
            int h = (head + 1) % HIST;
            HEAP[h] = heapMax > 0 ? hu / (float) heapMax : 0;
            GC[h] = lastGcN > 0 && dn > 0;
            head = h;
            lastGcN = n; lastGcMs = ms;
            V[6] = String.format(Locale.ROOT, "%,d loaded  (%,d unloaded)", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount(),
                    ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount());
            ThreadMXBean tb = ManagementFactory.getThreadMXBean();
            V[7] = tb.getThreadCount() + " live, peak " + tb.getPeakThreadCount() + ", " + tb.getDaemonThreadCount() + " daemon";
            try { V[8] = ManagementFactory.getCompilationMXBean().getName() + ", " + String.format(Locale.ROOT, "%.1f s", ManagementFactory.getCompilationMXBean().getTotalCompilationTime() / 1000f) + " compiling"; }
            catch (Throwable t) { V[8] = "n/a"; }
            V[9] = DPBoot.fmt(ManagementFactory.getRuntimeMXBean().getUptime());
            if (wantThreads) threads(tb, now);
            wantThreads = false;
        } catch (Throwable ignored) { }
    }

    /** #19: CPU % of every thread over the last second, busiest first */
    private static void threads(ThreadMXBean tb, long now) {
        if (!tb.isThreadCpuTimeSupported()) { tCount = 0; return; }
        if (!tb.isThreadCpuTimeEnabled()) tb.setThreadCpuTimeEnabled(true);
        long dt = lastThreadSample == 0 ? 0 : (now - lastThreadSample) * 1_000_000L;
        lastThreadSample = now;
        long[] ids = tb.getAllThreadIds();
        java.lang.management.ThreadInfo[] infos = tb.getThreadInfo(ids);
        java.util.Map<Long, Long> seen = new java.util.HashMap<Long, Long>();
        tCount = 0;
        for (int i = 0; i < ids.length; i++) {
            long cpu = tb.getThreadCpuTime(ids[i]);
            if (cpu < 0 || infos[i] == null) continue;
            seen.put(ids[i], cpu);
            Long before = CPU.get(ids[i]);
            if (before == null || dt <= 0) continue;
            float pct = 100f * (cpu - before) / dt;
            // insert into the top list
            int at = tCount;
            while (at > 0 && T_PCT[at - 1] < pct) at--;
            if (at >= TN) continue;
            for (int k = Math.min(tCount, TN - 1); k > at; k--) { T_PCT[k] = T_PCT[k - 1]; T_NAME[k] = T_NAME[k - 1]; }
            T_PCT[at] = pct; T_NAME[at] = infos[i].getThreadName();
            if (tCount < TN) tCount++;
        }
        CPU.clear();
        CPU.putAll(seen);
    }
}
