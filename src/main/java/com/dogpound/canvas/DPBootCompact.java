package com.dogpound.canvas;

import java.util.Collection;

/**
 * The loading screen for SMALL windows (< 1600 px wide - her real window is about 1300 x 720 on an 8K desktop).
 * Drawn at the GUI scale so text is readable, laid out in fixed rectangles that never overlap:
 *
 *   [ MangoHud corner kept clear ]   [ logo ]                 [ Music: ON ]
 *   [ one card, 5 big lines: phase + mods, step, % + time + ETA, usual time / stuck, problems + screen cost ]
 *   [ box: MEMORY bar, LOAD bar, log chips + search, log lines ]
 *   [ Music | System | Slowest | Issues | Play | Settings ]   <- each opens a panel over card + box (clipped)
 */
final class DPBootCompact {
    private DPBootCompact() {}

    static final String[] TABS = {"Music", "System", "Slowest", "Issues", "Play", "Settings"};
    static int tab = -1;
    private static final String[] TAB_S = new String[TABS.length];
    private static int tabProblems = -1;

    // keep this top-left corner (real pixels) free: her MangoHud overlay lives there
    private static final int MANGO_W = 280, MANGO_H = 260;

    static void draw(int W, int H, int bs, int progress, int memPct, Collection<String> log, int logCount) {
        DPBoot.tick(progress);
        DPBoot.scanProblems();
        int mangoW = MANGO_W / bs + 4, mangoH = MANGO_H / bs + 3;
        int logoBottom = 10 + (int) (Math.min(Math.min(W * 0.42f, 360f), W - 20) * 300f / 939f);
        int cardY = logoBottom + 4, cardH = 5 * DPBootUI.LINE + 8;
        int cardX = W - mangoW - 6 >= 240 && cardY < mangoH ? mangoW : 6;
        int barY = H - 15, boxBottom = barY - 4;
        int boxTop = Math.min(Math.max(cardY + cardH + 5, (int) (H * 0.5f)), boxBottom - 40);

        // ---- bottom button bar
        int n = TABS.length, gap = 3, bw = (W - 12 - (n - 1) * gap) / n;
        int np = DPBoot.PROBLEMS.size();
        if (np != tabProblems) { tabProblems = np; for (int i = 0; i < n; i++) TAB_S[i] = i == 3 && np > 0 ? "Issues " + np : TABS[i]; }
        for (int i = 0; i < n; i++) {
            if (DPBootUI.button(6 + i * (bw + gap), barY, bw, 11, TAB_S[i] == null ? TABS[i] : TAB_S[i], tab == i)) {
                tab = tab == i ? -1 : i;
                DPBootSettings.open = tab == 5;
                if (tab != 4) { DPBootGames.game = -1; DPBootViews.view = -1; }
            }
            if (i == 3 && np > 0 && tab != 3) DPBootUI.rect(6 + i * (bw + gap), barY + 10, 6 + i * (bw + gap) + bw, barY + 11, DPBoot.serious ? DPBootUI.BAD : DPBootUI.WARN);
        }

        if (tab >= 0) {                                       // a panel covers card + box, clipped to its rectangle
            int px = 6, py = Math.max(cardY, mangoH), pw = W - 12, ph = boxBottom - py;
            DPBootUI.clip(px, py, pw, ph);
            try { panel(px, py, pw, ph); } finally { DPBootUI.unclip(); }
            if (tab == 5 && !DPBootSettings.open) tab = -1;    // closed with its own x / Esc
            return;
        }

        // ---- the card: 5 big lines
        if (DPBootSettings.show("dash")) {
        DPBootUI.clip(cardX, cardY, W - cardX - 6, cardH);
        try {
            int cw = W - cardX - 6, y = DPBootUI.panel(cardX, cardY, cw, cardH, null);
            int[] col = {DPBootUI.PINK, DPBootUI.WHITE, DPBootUI.WHITE, DPBoot.stalled ? DPBootUI.WARN : DPBootUI.DIM,
                    DPBoot.PROBLEMS.isEmpty() ? DPBootUI.DIM : DPBoot.serious ? DPBootUI.BAD : DPBootUI.WARN};
            for (int i = 0; i < 5; i++) DPBootUI.text(DPBootUI.fit(470 + i, DPBoot.CARD[i], cw - 10), cardX + 5, y + i * DPBootUI.LINE, col[i]);
        } finally { DPBootUI.unclip(); }
        }

        // ---- the box: two bars + the log
        int bx = 6, bwid = W - 12;
        DPBootUI.rect(bx, boxTop, bx + bwid, boxBottom, 0x700A0614);
        DPBootUI.rect(bx - 1, boxTop - 1, bx + bwid + 1, boxTop, DPBootUI.PINK);
        DPBootUI.rect(bx - 1, boxBottom, bx + bwid + 1, boxBottom + 1, DPBootUI.PINK);
        DPBootUI.rect(bx - 1, boxTop, bx, boxBottom, DPBootUI.PINK);
        DPBootUI.rect(bx + bwid, boxTop, bx + bwid + 1, boxBottom, DPBootUI.PINK);
        DPBootUI.clip(bx, boxTop, bwid, boxBottom - boxTop);
        try {
            int y = boxTop + 2;
            if (DPBootSettings.show("membar")) {
                DPBootUI.bar(bx + 2, y, bwid - 4, 9, memPct / 100f, DPBootUI.BLUE);
                DPBootUI.text(memText(memPct), bx + 5, y + 1, DPBootUI.WHITE);
            }
            y += 10;
            DPBootUI.bar(bx + 2, y, bwid - 4, 9, progress / 100f, DPBootUI.PINK);
            DPBootUI.text(loadText(progress), bx + 5, y + 1, DPBootUI.WHITE);
            if (DPBoot.etaShort != null && DPBootSettings.show("eta")) DPBootUI.text(DPBoot.etaShort, bx + bwid - 5 - DPBootUI.width(DPBoot.etaShort), y + 1, DPBootUI.WHITE);
            if (DPBootSettings.show("log")) DPBootUI.logPanel(bx + 3, y + 12, bwid - 6, boxBottom - 1, log, logCount);
        } finally { DPBootUI.unclip(); }
    }

    private static int memKey = -1, loadKey = -1;
    private static String memS = "", loadS = "";
    private static String memText(int p) { if (p != memKey) { memKey = p; memS = "MEMORY " + p + "%"; } return memS; }
    private static String loadText(int p) { if (p != loadKey) { loadKey = p; loadS = "LOAD " + p + "%"; } return loadS; }

    private static void panel(int x, int y, int w, int h) {
        switch (tab) {
            case 0: DPBootUI.music(x, y, w); break;
            case 1: {                                          // System / JVM / Threads, two columns
                int tx = x, tw = Math.min(70, (w - 4) / 3);
                for (int i = 0; i < DPBootUI.RTABS.length; i++, tx += tw + 2)
                    if (DPBootUI.button(tx, y, tw, 11, DPBootUI.RTABS[i], DPBootUI.rtab == i)) DPBootUI.rtab = i;
                int cy = y + 14, half = (w - 4) / 2;
                DPBootJvm.wantThreads = DPBootUI.rtab == 2;
                DPBootJvm.sample();
                if (DPBootUI.rtab == 0) { DPBootUI.meters(x, cy, half); DPBootUI.assets(x + half + 4, DPBootUI.disk(x + half + 4, cy, half) + 4, half); }
                else if (DPBootUI.rtab == 1) { DPBootUI.jvm(x, cy, half); DPBootUI.heapGraph(x + half + 4, cy, half); }
                else DPBootUI.threads(x, cy, w);
                break;
            }
            case 2: {
                int half = (w - 4) / 2;
                DPBootUI.slowest(x, y, half);
                DPBootUI.whatsNew(x + half + 4, y, half, y + h);
                break;
            }
            case 3: {
                int top = DPBootUI.problems(x, w, y + h);
                DPBootUI.stall(x, w, top);
                if (DPBoot.PROBLEMS.isEmpty() && !DPBoot.stalled) {
                    DPBootUI.panel(x, y, w, 40, "Issues");
                    DPBootUI.text("No problems found so far.", x + 5, y + 22, DPBootUI.GOOD);
                }
                break;
            }
            case 4:
                if (!DPBootGames.draw(x, y, w, h) && !DPBootViews.draw(x, y, w, h)) DPBootUI.gamesPanel(x, y, w);
                break;
            default:
                DPBootSettings.draw(x, y, w, h);
                break;
        }
    }
}
