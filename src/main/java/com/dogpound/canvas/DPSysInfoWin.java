package com.dogpound.canvas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System Monitor on Windows (requested feature) and Mac.
 * Windows: ONE long-lived `typeperf -si 1` (built into every Windows) streams the counters as CSV: every core, CPU speed,
 * disk and network speeds, GPU engine use and GPU memory (Windows 10/11 have GPU counters for AMD, NVIDIA and Intel).
 * RAM/swap come from Java. Temperatures need admin on Windows, so they're read from LibreHardwareMonitor's web page
 * (http://localhost:8085/data.json) if the player runs it; otherwise "n/a".
 * Mac: CPU/RAM/swap from Java, CPU name from sysctl.
 * NOT TESTED ON A WINDOWS PC YET (built on Linux) — ask a Discord player to try.
 */
final class DPSysInfoWin {
    private DPSysInfoWin() {}

    private static final String[] COUNTERS = {
            "\\Processor(*)\\% Processor Time",
            "\\Processor Information(_Total)\\% Processor Performance",
            "\\Processor Information(_Total)\\Processor Frequency",
            "\\PhysicalDisk(_Total)\\Disk Read Bytes/sec",
            "\\PhysicalDisk(_Total)\\Disk Write Bytes/sec",
            "\\Network Interface(*)\\Bytes Received/sec",
            "\\Network Interface(*)\\Bytes Sent/sec",
            "\\GPU Engine(*)\\Utilization Percentage",
            "\\GPU Adapter Memory(*)\\Dedicated Usage",
            "\\GPU Adapter Memory(*)\\Shared Usage",
            "\\System\\System Up Time",
    };
    private static volatile List<String> header;
    private static volatile String[] lastRow;
    private static Process typeperf;
    private static long lastLhm;

    static void find() {
        String name = regValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString");
        if (name != null) DPSysInfo.cpuName = name.trim().replaceAll("\\s+", " ");
        // total VRAM: the display driver's registry entry (64-bit qwMemorySize), else nothing
        String v = runFirst("reg", "query", "HKLM\\SYSTEM\\ControlSet001\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}", "/s", "/v", "HardwareInformation.qwMemorySize");
        if (v != null) {
            Matcher m = Pattern.compile("0x([0-9a-fA-F]+)").matcher(v);
            long best = -1;
            while (m.find()) best = Math.max(best, Long.parseLong(m.group(1), 16));
            if (best > 0) DPSysInfo.vramTotal = best;
        }
        startTypeperf();
    }

    static void findMac() {
        String n = runFirst("sysctl", "-n", "machdep.cpu.brand_string");
        if (n != null) DPSysInfo.cpuName = n.trim();
    }

    private static void startTypeperf() {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("typeperf");
            for (String c : COUNTERS) cmd.add(c);
            cmd.add("-si");
            cmd.add("1");
            typeperf = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(typeperf.getInputStream()))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        if (!line.startsWith("\"")) continue;
                        String[] cols = csv(line);
                        if (header == null && cols.length > 1 && cols[0].contains("PDH-CSV")) { header = java.util.Arrays.asList(cols); continue; }
                        if (header != null && cols.length == header.size()) lastRow = cols;
                    }
                } catch (Throwable ignored) { }
            }, "Pride-SysMonitor-typeperf");
            t.setDaemon(true);
            t.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (typeperf != null) typeperf.destroy(); }));
        } catch (Throwable ignored) { }
    }

    static void sample(double dt) {
        DPSysInfo.javaFallback();
        String[] row = lastRow;
        List<String> h = header;
        if (DPSysInfo.WINDOWS && row != null && h != null) {
            List<Float> per = new ArrayList<>();
            double netR = 0, netT = 0, gpu3d = 0, ded = 0, shared = 0, freq = -1, perf = -1;
            for (int i = 1; i < row.length; i++) {
                String c = h.get(i).toLowerCase(Locale.ROOT);
                double v;
                try { v = Double.parseDouble(row[i].trim()); } catch (Throwable t) { continue; }
                if (c.contains("\\processor(") && c.endsWith("% processor time")) {
                    if (c.contains("(_total)")) DPSysInfo.cpu = (float) v; else per.add((float) v);
                } else if (c.contains("% processor performance")) perf = v;
                else if (c.contains("processor frequency")) freq = v;
                else if (c.contains("disk read bytes")) DPSysInfo.diskRead = v;
                else if (c.contains("disk write bytes")) DPSysInfo.diskWrite = v;
                else if (c.contains("bytes received/sec")) netR += v;
                else if (c.contains("bytes sent/sec")) netT += v;
                else if (c.contains("gpu engine(") && c.contains("engtype_3d")) gpu3d += v;
                else if (c.contains("dedicated usage")) ded = Math.max(ded, v);
                else if (c.contains("shared usage")) shared = Math.max(shared, v);
                else if (c.contains("system up time")) DPSysInfo.uptimeSec = (long) v;
            }
            DPSysInfo.cores = toArray(per);
            DPSysInfo.netDown = netR;
            DPSysInfo.netUp = netT;
            DPSysInfo.gpu = (float) Math.min(100, gpu3d);
            DPSysInfo.vramUsed = (long) ded;
            DPSysInfo.gttUsed = (long) shared;                      // Windows calls it "shared GPU memory"
            if (freq > 0) DPSysInfo.cpuGhz = (float) (freq * (perf > 0 ? perf / 100.0 : 1) / 1000.0);
            if (freq > 0 && DPSysInfo.cpuMaxGhz < 0) DPSysInfo.cpuMaxGhz = (float) (freq / 1000.0);
        }
        if (System.currentTimeMillis() - lastLhm > 2000) { lastLhm = System.currentTimeMillis(); libreHardwareMonitor(); }
    }

    /** temperatures, clock, power, fan from LibreHardwareMonitor's web server, if the player runs it */
    private static void libreHardwareMonitor() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8085/data.json").openConnection();
            c.setConnectTimeout(150);
            c.setReadTimeout(400);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) { for (String l; (l = r.readLine()) != null; ) sb.append(l); }
            String j = sb.toString();
            DPSysInfo.cpuTemp = sensor(j, "(CPU Package|Core \\(Tctl/Tdie\\)|CPU Total)", "°C");
            DPSysInfo.gpuTemp = sensor(j, "GPU Core", "°C");
            DPSysInfo.gpuHotspot = sensor(j, "GPU Hot Spot", "°C");
            float w = sensor(j, "GPU (Package|Power|Core)", "W");
            if (w > 0) DPSysInfo.gpuWatts = w;
            float mhz = sensor(j, "GPU Core", "MHz");
            if (mhz > 0) DPSysInfo.gpuMhz = mhz;
            float fan = sensor(j, "GPU( Fan)?", "RPM");
            if (fan > 0) DPSysInfo.gpuFan = fan;
        } catch (Throwable ignored) { }
    }

    /** first sensor whose Text matches and whose Value ends with the unit */
    private static float sensor(String json, String text, String unit) {
        Matcher m = Pattern.compile("\"Text\"\\s*:\\s*\"" + text + "\"[^}]*?\"Value\"\\s*:\\s*\"([0-9.,]+)\\s*" + Pattern.quote(unit) + "\"").matcher(json);
        if (!m.find()) return -1;
        try { return Float.parseFloat(m.group(m.groupCount()).replace(',', '.')); } catch (Throwable t) { return -1; }
    }

    private static float[] toArray(List<Float> l) { float[] a = new float[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }

    private static String[] csv(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(line);
        while (m.find()) out.add(m.group(1));
        return out.toArray(new String[0]);
    }

    private static String regValue(String key, String value) {
        String o = runFirst("reg", "query", key, "/v", value);
        if (o == null) return null;
        for (String l : o.split("\\r?\\n")) if (l.trim().startsWith(value)) { String[] p = l.trim().split("\\s{2,}"); return p.length >= 3 ? p[2] : null; }
        return null;
    }

    private static String runFirst(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { for (String l; (l = r.readLine()) != null; ) sb.append(l).append('\n'); }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return sb.toString();
        } catch (Throwable t) { return null; }
    }
}
