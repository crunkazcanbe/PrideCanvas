package com.dogpound.canvas;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Pride System Monitor numbers (requested feature): CPU (total, every core, speed, temperature), GPU (busy %, VRAM, GTT,
 * temperature, clock, power, fan), RAM + swap, Minecraft's own memory, disk and network speeds. Read twice a second on
 * a background thread from Linux's /proc and /sys (AMD GPUs expose everything there); on other systems the Java
 * management beans fill in what they can and the rest shows "n/a". Values are -1 when unknown.
 */
public final class DPSysInfo {
    private DPSysInfo() {}

    public static final int HIST = 120;                     // 60 s of history at 2 samples a second

    // CPU
    public static volatile String cpuName = "CPU";
    public static volatile float cpu = -1, cpuGhz = -1, cpuMaxGhz = -1, cpuTemp = -1;
    public static volatile float[] cores = new float[0];
    // GPU
    public static volatile String gpuName = "GPU";
    public static volatile float gpu = -1, gpuTemp = -1, gpuHotspot = -1, gpuMhz = -1, gpuWatts = -1, gpuFan = -1;
    public static volatile long vramUsed = -1, vramTotal = -1, gttUsed = -1, gttTotal = -1;
    // memory
    public static volatile long ramTotal = -1, ramAvail = -1, swapTotal = -1, swapFree = -1, cached = -1;
    public static volatile long gameRss = -1, gameSwap = -1;
    public static volatile int threads = -1;
    // speeds (bytes per second)
    public static volatile double diskRead = -1, diskWrite = -1, netDown = -1, netUp = -1;
    public static volatile long uptimeSec = -1;
    // history (0..100 or raw)
    public static final float[] hCpu = new float[HIST], hGpu = new float[HIST], hRam = new float[HIST], hVram = new float[HIST], hFps = new float[HIST], hDisk = new float[HIST], hNet = new float[HIST];
    public static volatile int head;

    /** sampling period; the boot splash slows it to 1 s so it never competes with loading (#35) */
    public static volatile int intervalMs = 500;
    private static Thread thread;
    private static volatile long lastWanted;
    private static File gpuDir, gpuHwmon, cpuHwmon;
    private static long[] lastCpu;
    private static long[][] lastCores;
    private static long lastDiskR, lastDiskW, lastNetR, lastNetT, lastT;

    /** call every frame something shows these numbers: the sampler runs only while wanted */
    public static void want() {
        lastWanted = System.currentTimeMillis();
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(DPSysInfo::loop, "Pride-SysMonitor");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");

    private static void loop() {
        try { if (WINDOWS) DPSysInfoWin.find(); else if (MAC) DPSysInfoWin.findMac(); else find(); } catch (Throwable ignored) { }
        while (System.currentTimeMillis() - lastWanted < 15000) {
            try { sample(); } catch (Throwable ignored) { }
            try { Thread.sleep(intervalMs); } catch (InterruptedException e) { return; }
        }
        thread = null;
    }

    private static void find() {
        File drm = new File("/sys/class/drm");
        File[] cards = drm.listFiles();
        if (cards != null) for (File c : cards) {
            File d = new File(c, "device");
            if (c.getName().matches("card\\d+") && new File(d, "mem_info_vram_total").exists()) {
                gpuDir = d;
                File[] h = new File(d, "hwmon").listFiles();
                if (h != null && h.length > 0) gpuHwmon = h[0];
                String prod = read(new File(d, "product_name"));
                if (prod != null && !prod.isEmpty()) gpuName = prod;
                break;
            }
        }
        File[] hw = new File("/sys/class/hwmon").listFiles();
        if (hw != null) for (File h : hw) {
            String n = read(new File(h, "name"));
            if (n != null && (n.equals("k10temp") || n.equals("coretemp") || n.equals("zenpower"))) { cpuHwmon = h; break; }
        }
        try {
            for (String l : Files.readAllLines(new File("/proc/cpuinfo").toPath()))
                if (l.startsWith("model name")) { cpuName = l.substring(l.indexOf(':') + 1).trim().replaceAll("\\s+", " "); break; }
        } catch (Throwable ignored) { cpuName = System.getProperty("os.arch", "CPU"); }
        double maxKhz = 0;
        for (int i = 0; i < 512; i++) {
            String m = read(new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"));
            if (m == null) break;
            maxKhz = Math.max(maxKhz, Double.parseDouble(m));
        }
        if (maxKhz > 0) cpuMaxGhz = (float) (maxKhz / 1e6);
    }

    private static void sample() throws Exception {
        long now = System.nanoTime();
        double dt = lastT == 0 ? 0 : (now - lastT) / 1e9;
        lastT = now;
        if (WINDOWS || MAC) { DPSysInfoWin.sample(dt); history(); return; }
        // ---- CPU usage from /proc/stat
        File stat = new File("/proc/stat");
        if (stat.exists()) {
            List<String> lines = Files.readAllLines(stat.toPath());
            int ncores = 0;
            for (String l : lines) if (l.startsWith("cpu") && Character.isDigit(l.charAt(3))) ncores++;
            if (lastCores == null || lastCores.length != ncores) lastCores = new long[ncores][];
            float[] per = new float[ncores];
            int ci = 0;
            for (String l : lines) {
                if (!l.startsWith("cpu")) continue;
                long[] v = cpuTimes(l);
                if (l.startsWith("cpu ")) { cpu = busy(lastCpu, v); lastCpu = v; }
                else { per[ci] = busy(lastCores[ci], v); lastCores[ci] = v; ci++; }
            }
            cores = per;
            String up = read(new File("/proc/uptime"));
            if (up != null) uptimeSec = (long) Double.parseDouble(up.split(" ")[0]);
        } else {
            try {
                com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                cpu = (float) (os.getSystemCpuLoad() * 100);
                ramTotal = os.getTotalPhysicalMemorySize();
                ramAvail = os.getFreePhysicalMemorySize();
            } catch (Throwable ignored) { }
        }
        double khz = 0; int n = 0;
        for (int i = 0; i < cores.length; i++) {
            String f = read(new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq"));
            if (f == null) break;
            khz += Double.parseDouble(f); n++;
        }
        cpuGhz = n == 0 ? -1 : (float) (khz / n / 1e6);
        if (cpuHwmon != null) cpuTemp = milli(new File(cpuHwmon, "temp1_input"));
        // ---- memory
        File mi = new File("/proc/meminfo");
        if (mi.exists()) for (String l : Files.readAllLines(mi.toPath())) {
            if (l.startsWith("MemTotal:")) ramTotal = kb(l);
            else if (l.startsWith("MemAvailable:")) ramAvail = kb(l);
            else if (l.startsWith("SwapTotal:")) swapTotal = kb(l);
            else if (l.startsWith("SwapFree:")) swapFree = kb(l);
            else if (l.startsWith("Cached:")) cached = kb(l);
        }
        File self = new File("/proc/self/status");
        if (self.exists()) for (String l : Files.readAllLines(self.toPath())) {
            if (l.startsWith("VmRSS:")) gameRss = kb(l);
            else if (l.startsWith("VmSwap:")) gameSwap = kb(l);
            else if (l.startsWith("Threads:")) threads = Integer.parseInt(l.replaceAll("\\D+", ""));
        }
        else threads = Thread.activeCount();
        // ---- GPU (AMD sysfs)
        if (gpuDir != null) {
            gpu = num(new File(gpuDir, "gpu_busy_percent"));
            vramUsed = lng(new File(gpuDir, "mem_info_vram_used"));
            vramTotal = lng(new File(gpuDir, "mem_info_vram_total"));
            gttUsed = lng(new File(gpuDir, "mem_info_gtt_used"));
            gttTotal = lng(new File(gpuDir, "mem_info_gtt_total"));
            if (gpuHwmon != null) {
                gpuTemp = milli(new File(gpuHwmon, "temp1_input"));
                gpuHotspot = milli(new File(gpuHwmon, "temp2_input"));
                long hz = lng(new File(gpuHwmon, "freq1_input"));
                gpuMhz = hz > 0 ? hz / 1e6F : -1;
                long uw = lng(new File(gpuHwmon, "power1_average"));
                if (uw <= 0) uw = lng(new File(gpuHwmon, "power1_input"));
                gpuWatts = uw > 0 ? uw / 1e6F : -1;
                gpuFan = num(new File(gpuHwmon, "fan1_input"));
            }
        }
        // ---- disk + network speeds
        long dr = 0, dw = 0;
        File ds = new File("/proc/diskstats");
        if (ds.exists()) for (String l : Files.readAllLines(ds.toPath())) {
            String[] p = l.trim().split("\\s+");
            if (p.length < 10) continue;
            String name = p[2];
            if (!(name.matches("nvme\\d+n\\d+") || name.matches("sd[a-z]+") || name.matches("vd[a-z]+"))) continue;
            dr += Long.parseLong(p[5]) * 512; dw += Long.parseLong(p[9]) * 512;
        }
        long nr = 0, nt = 0;
        File nd = new File("/proc/net/dev");
        if (nd.exists()) for (String l : Files.readAllLines(nd.toPath())) {
            int c = l.indexOf(':');
            if (c < 0) continue;
            String name = l.substring(0, c).trim();
            if (name.equals("lo") || name.startsWith("veth") || name.startsWith("br-") || name.startsWith("docker") || name.startsWith("virbr") || name.startsWith("vnet") || name.startsWith("tun") || name.startsWith("wt")) continue;
            String[] p = l.substring(c + 1).trim().split("\\s+");
            nr += Long.parseLong(p[0]); nt += Long.parseLong(p[8]);
        }
        if (dt > 0) {
            diskRead = (dr - lastDiskR) / dt; diskWrite = (dw - lastDiskW) / dt;
            netDown = (nr - lastNetR) / dt; netUp = (nt - lastNetT) / dt;
        }
        lastDiskR = dr; lastDiskW = dw; lastNetR = nr; lastNetT = nt;
        history();
    }

    static void history() {
        int h = (head + 1) % HIST;
        hCpu[h] = Math.max(0, cpu);
        hGpu[h] = Math.max(0, gpu);
        hRam[h] = ramTotal > 0 ? 100F * (ramTotal - ramAvail) / ramTotal : 0;
        hVram[h] = vramTotal > 0 ? 100F * vramUsed / vramTotal : 0;
        hFps[h] = net.minecraft.client.Minecraft.getDebugFPS();
        hDisk[h] = (float) Math.max(0, (diskRead + diskWrite) / 1048576.0);
        hNet[h] = (float) Math.max(0, (netDown + netUp) / 1048576.0);
        head = h;
    }

    static void javaFallback() {
        try {
            com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (cpu < 0 || !WINDOWS) cpu = (float) Math.max(0, os.getSystemCpuLoad() * 100);
            ramTotal = os.getTotalPhysicalMemorySize();
            ramAvail = os.getFreePhysicalMemorySize();
            swapTotal = os.getTotalSwapSpaceSize();
            swapFree = os.getFreeSwapSpaceSize();
            java.lang.management.MemoryMXBean m = java.lang.management.ManagementFactory.getMemoryMXBean();
            gameRss = m.getHeapMemoryUsage().getCommitted() + m.getNonHeapMemoryUsage().getCommitted();
            threads = Thread.activeCount();
            uptimeSec = uptimeSec < 0 ? -1 : uptimeSec;
        } catch (Throwable ignored) { }
    }

    private static long[] cpuTimes(String l) {
        String[] p = l.trim().split("\\s+");
        long[] v = new long[p.length - 1];
        for (int i = 1; i < p.length; i++) v[i - 1] = Long.parseLong(p[i]);
        return v;
    }

    /** % busy between two /proc/stat samples (idle = idle + iowait) */
    private static float busy(long[] a, long[] b) {
        if (a == null || a.length != b.length) return 0;
        long ta = 0, tb = 0;
        for (long x : a) ta += x;
        for (long x : b) tb += x;
        long ia = a[3] + (a.length > 4 ? a[4] : 0), ib = b[3] + (b.length > 4 ? b[4] : 0);
        long dt = tb - ta;
        return dt <= 0 ? 0 : Math.max(0, Math.min(100, 100F * (dt - (ib - ia)) / dt));
    }

    private static long kb(String l) { return Long.parseLong(l.replaceAll("\\D+", "")) * 1024; }
    private static String read(File f) { try { return f.exists() ? new String(Files.readAllBytes(f.toPath())).trim() : null; } catch (Throwable t) { return null; } }
    private static long lng(File f) { String s = read(f); try { return s == null ? -1 : Long.parseLong(s); } catch (Throwable t) { return -1; } }
    private static float num(File f) { long v = lng(f); return v < 0 ? -1 : v; }
    private static float milli(File f) { long v = lng(f); return v < 0 ? -1 : v / 1000F; }

    // ------------------------------------------------------------------ formatting

    public static String bytes(double b) {
        if (b < 0) return "n/a";
        if (b >= 1L << 40) return String.format(java.util.Locale.ROOT, "%.2f TB", b / (1L << 40));
        if (b >= 1L << 30) return String.format(java.util.Locale.ROOT, "%.1f GB", b / (1L << 30));
        if (b >= 1L << 20) return String.format(java.util.Locale.ROOT, "%.0f MB", b / (1L << 20));
        return String.format(java.util.Locale.ROOT, "%.0f KB", b / 1024);
    }

    public static String speed(double bps) { return bps < 0 ? "n/a" : bytes(bps) + "/s"; }
}
