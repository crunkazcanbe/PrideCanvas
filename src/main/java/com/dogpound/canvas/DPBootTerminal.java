package com.dogpound.canvas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Loading screen #33: terminal "boot sequence" mode (Settings > Layout). Black screen, every loading step as a
 * systemd-style line ([  OK  ] / [ WARN ] / [FAILED]), an ASCII progress bar at the bottom. New lines are appended
 * at most 4x a second; drawing reads the list as is.
 */
final class DPBootTerminal {
    private DPBootTerminal() {}

    private static final List<String> TEXT = new ArrayList<String>();
    private static final List<Integer> KIND = new ArrayList<Integer>();      // 0 ok, 1 warn, 2 fail
    private static int seenStatus;
    private static long seenWarn, lastAt;
    private static String header, barLine = "";

    private static void add(String t, int k) {
        TEXT.add(t); KIND.add(k);
        if (TEXT.size() > 400) { TEXT.remove(0); KIND.remove(0); }
    }

    private static void update(Collection<String> status, int statusCount, int progress) {
        long now = System.currentTimeMillis();
        if (now - lastAt < 250) return;
        lastAt = now;
        if (header == null) header = "Pride Boot Sequence  -  Minecraft 1.12.2, Forge, Java " + System.getProperty("java.version")
                + ", " + Runtime.getRuntime().availableProcessors() + " cpus, " + DPSysInfo.bytes(Runtime.getRuntime().maxMemory()) + " heap";
        int fresh = statusCount - seenStatus;
        if (fresh > 0) {
            List<String> all = new ArrayList<String>(status);
            for (int i = Math.max(0, all.size() - fresh); i < all.size(); i++) add(all.get(i), 0);
            seenStatus = statusCount;
        }
        for (DPLogBuffer.Line l : DPLogBuffer.snapshot(true)) {
            if (l.time <= seenWarn) continue;
            seenWarn = l.time;
            add((l.logger == null ? "" : l.logger + ": ") + l.msg, l.lvl >= 3 ? 2 : 1);
        }
        int cells = 40, filled = Math.max(0, Math.min(cells, progress * cells / 100));
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < cells; i++) b.append(i < filled ? '#' : '.');
        b.append("] ").append(progress).append("%   ").append(DPBoot.DASH_V[0]);
        if (DPBoot.etaShort != null) b.append("   ").append(DPBoot.etaShort);
        barLine = b.toString();
    }

    static void draw(int w, int h, int progress, Collection<String> status, int statusCount) {
        update(status, statusCount, progress);
        int green = 0xFF33FF66, gray = 0xFFC8C8C8;
        int y = 30, line = 10, bottom = h - 34;
        DPBootUI.text(DPBootUI.fit(260, header, w - 20), 10, y, green);
        y += 2 * line;
        int rows = Math.max(1, (bottom - y) / line), start = Math.max(0, TEXT.size() - rows);
        String spin = "|/-\\".substring((int) (System.currentTimeMillis() / 120 % 4), (int) (System.currentTimeMillis() / 120 % 4) + 1);
        for (int i = start; i < TEXT.size(); i++, y += line) {
            int k = KIND.get(i);
            boolean last = i == TEXT.size() - 1 && k == 0;
            String tag = last ? "[  " + spin + "   ]" : k == 0 ? "[  OK  ]" : k == 1 ? "[ WARN ]" : "[FAILED]";
            int tc = last ? 0xFF5BCEFA : k == 0 ? green : k == 1 ? 0xFFFFC040 : 0xFFFF5A64;
            DPBootUI.text(tag, 10, y, tc);
            DPBootUI.text(DPBootUI.fit(300 + Math.min(150, i - start), TEXT.get(i), w - 80), 64, y, k == 0 ? gray : tc);
        }
        DPBootUI.text(DPBootUI.fit(259, barLine, w - 20), 10, h - 24, green);
    }
}
