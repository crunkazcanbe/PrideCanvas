package com.dogpound.canvas;

import org.lwjgl.input.Keyboard;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

/**
 * Loading screen #11: mini games while the pack loads — Snake, Tetris, 2048, Minesweeper, Pong, Memory, Clicker.
 * Drawn in the middle of the splash with plain rects (cheap), stepped on timers (not per frame), keyboard + mouse from
 * DPBootUI's per-frame input. Best scores live in config/pride-boot-games.txt.
 */
final class DPBootGames {
    private DPBootGames() {}

    static final String[] NAMES = {"Snake", "Tetris", "2048", "Minesweeper", "Pong", "Memory", "Clicker", "Runner"};
    static int game = -1;                              // -1 = closed
    private static final Random RND = new Random();
    private static final int[] RAINBOW = {0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF5BCEFA, 0xFF732982, 0xFFF5A9B8, 0xFF24408E};
    private static final long[] BEST = new long[NAMES.length];
    private static boolean bestLoaded;
    private static String msg = "";                    // status line under the game

    static void open(int g) { game = g; reset(g); DPBootSettings.open = false; }

    // ------------------------------------------------------------------ best scores
    private static File bestFile() { return new File("config/pride-boot-games.txt"); }

    private static void loadBest() {
        if (bestLoaded) return;
        bestLoaded = true;
        try {
            for (String l : Files.readAllLines(bestFile().toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("=");
                for (int i = 0; i < NAMES.length; i++) if (p.length == 2 && p[0].trim().equals(NAMES[i])) BEST[i] = Long.parseLong(p[1].trim());
            }
        } catch (Throwable ignored) { }
    }

    /** higher is better, except Memory (fewer moves) */
    private static boolean dirty;
    private static long savedAt;

    private static void score(int g, long v) {
        loadBest();
        boolean better = g == 5 ? (BEST[g] == 0 || v < BEST[g]) : v > BEST[g];
        if (!better) return;
        BEST[g] = v;
        dirty = true;
        if (System.currentTimeMillis() - savedAt > 10000) save();
    }

    /** written at most every 10 s while playing, and when the game is closed */
    static void save() {
        if (!dirty) return;
        dirty = false;
        savedAt = System.currentTimeMillis();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) b.append(NAMES[i]).append('=').append(BEST[i]).append('\n');
        try { bestFile().getParentFile().mkdirs(); Files.write(bestFile().toPath(), b.toString().getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ frame
    /** draw the open game in this box; returns false when closed */
    static boolean draw(int x, int y, int w, int h) {
        if (game < 0) return false;
        loadBest();
        int cy = DPBootUI.panel(x, y, w, h, "While you wait: " + NAMES[game]);
        if (DPBootUI.button(x + w - 16, y + 3, 13, 11, "x", false)) { game = -1; save(); return false; }
        // tabs
        int tx = x + 5;
        for (int i = 0; i < NAMES.length; i++) {
            int tw = DPBootUI.width(NAMES[i]) + 8;
            if (DPBootUI.button(tx, cy, tw, 11, NAMES[i], i == game)) open(i);
            tx += tw + 2;
        }
        String best = BEST[game] == 0 ? "" : "best " + BEST[game] + (game == 5 ? " moves" : "");
        DPBootUI.text(best, x + w - 6 - DPBootUI.width(best), cy + 2, DPBootUI.PINK);
        int gx = x + 6, gy = cy + 15, gw = w - 12, gh = y + h - gy - 14;
        // keys (the search box gets them first when it's focused)
        if (!DPBootUI.searchFocus) for (int i = 0; i < DPBootUI.nKeys; i++) {
            int k = DPBootUI.KEYS[i];
            if (k == Keyboard.KEY_ESCAPE) { game = -1; save(); return false; }
            key(k);
        }
        long now = System.currentTimeMillis();
        switch (game) {
            case 0: snake(now, gx, gy, gw, gh); break;
            case 1: tetris(now, gx, gy, gw, gh); break;
            case 2: g2048(gx, gy, gw, gh); break;
            case 3: mines(gx, gy, gw, gh); break;
            case 4: pong(now, gx, gy, gw, gh); break;
            case 5: memory(now, gx, gy, gw, gh); break;
            case 6: clicker(now, gx, gy, gw, gh); break;
            case 7: runner(now, gx, gy, gw, gh); break;
            default: break;
        }
        DPBootUI.text(msg, x + 6, y + h - 11, DPBootUI.DIM);
        return true;
    }

    private static void reset(int g) {
        switch (g) {
            case 0: snakeReset(); break;
            case 1: tetrisReset(); break;
            case 2: b2048Reset(); break;
            case 3: minesReset(); break;
            case 4: pongReset(); break;
            case 5: memoryReset(); break;
            case 7: runnerReset(); break;
            default: msg = "click the heart! buy rainbows for hearts per second"; break;
        }
    }

    private static void key(int k) {
        int dx = 0, dy = 0;
        if (k == Keyboard.KEY_LEFT || k == Keyboard.KEY_A) dx = -1;
        if (k == Keyboard.KEY_RIGHT || k == Keyboard.KEY_D) dx = 1;
        if (k == Keyboard.KEY_UP || k == Keyboard.KEY_W) dy = -1;
        if (k == Keyboard.KEY_DOWN || k == Keyboard.KEY_S) dy = 1;
        boolean go = k == Keyboard.KEY_SPACE || k == Keyboard.KEY_RETURN;
        switch (game) {
            case 0:
                if (snakeDead) { if (go) snakeReset(); break; }
                if ((dx != 0 && sdx == 0) || (dy != 0 && sdy == 0)) { ndx = dx; ndy = dy; }
                break;
            case 1:
                if (tOver) { if (go) tetrisReset(); break; }
                if (dx != 0) tMove(dx, 0, tRot);
                if (dy == 1) tMove(0, 1, tRot);
                if (dy == -1 || k == Keyboard.KEY_X) tRotate();
                if (k == Keyboard.KEY_SPACE) { while (tMove(0, 1, tRot)) { tScore += 2; } tLock(); }
                break;
            case 2:
                if (bOver && go) b2048Reset();
                else if (dx != 0 || dy != 0) b2048Move(dx, dy);
                break;
            case 3: case 5:
                if (go) reset(game);
                break;
            case 7:
                if (rDead) { if (go) runnerReset(); break; }
                if (go || dy == -1) rJump = true;
                break;
            case 4:
                if (go && pongWin != 0) pongReset();
                if (dy != 0) pPlayer += dy * 18;
                break;
            default: break;
        }
    }

    private static void cell(int x, int y, int s, int color) { DPBootUI.rect(x, y, x + s - 1, y + s - 1, color); }

    private static void frame(int x, int y, int w, int h) {
        DPBootUI.rect(x - 1, y - 1, x + w + 1, y + h + 1, 0x60F5A9B8);
        DPBootUI.rect(x, y, x + w, y + h, 0xF0080510);
    }

    private static void center(String s, int x, int w, int y, int c) { DPBootUI.text(s, x + (w - DPBootUI.width(s)) / 2, y, c); }

    // ================================================================== Snake
    private static final int SW = 24, SH = 16;
    private static final int[] SX = new int[SW * SH], SY = new int[SW * SH];
    private static int sLen, sdx, sdy, ndx, ndy, fx, fy;
    private static long sStep;
    private static boolean snakeDead;

    private static void snakeReset() {
        sLen = 3; sdx = 1; sdy = 0; ndx = 1; ndy = 0; snakeDead = false;
        for (int i = 0; i < 3; i++) { SX[i] = 6 - i; SY[i] = SH / 2; }
        food();
        msg = "arrows / WASD to steer, eat the hearts";
    }

    private static void food() {
        for (int t = 0; t < 500; t++) {
            fx = RND.nextInt(SW); fy = RND.nextInt(SH);
            boolean hit = false;
            for (int i = 0; i < sLen; i++) if (SX[i] == fx && SY[i] == fy) { hit = true; break; }
            if (!hit) return;
        }
    }

    private static void snake(long now, int x, int y, int w, int h) {
        int s = Math.max(4, Math.min(w / SW, h / SH)), bw = s * SW, bh = s * SH, ox = x + (w - bw) / 2, oy = y + (h - bh) / 2;
        if (!snakeDead && now - sStep > Math.max(55, 120 - sLen * 2)) {
            sStep = now;
            sdx = ndx; sdy = ndy;
            int nx = SX[0] + sdx, ny = SY[0] + sdy;
            boolean dead = nx < 0 || ny < 0 || nx >= SW || ny >= SH;
            for (int i = 0; i < sLen - 1 && !dead; i++) if (SX[i] == nx && SY[i] == ny) dead = true;
            if (dead) { snakeDead = true; score(0, sLen - 3); msg = "ouch! score " + (sLen - 3) + " - space to play again"; }
            else {
                boolean eat = nx == fx && ny == fy;
                if (eat && sLen < SX.length) sLen++;
                for (int i = sLen - 1; i > 0; i--) { SX[i] = SX[i - 1]; SY[i] = SY[i - 1]; }
                SX[0] = nx; SY[0] = ny;
                if (eat) { food(); msg = "score " + (sLen - 3); }
            }
        }
        frame(ox, oy, bw, bh);
        cell(ox + fx * s, oy + fy * s, s, DPBootUI.PINK);
        for (int i = 0; i < sLen; i++) cell(ox + SX[i] * s, oy + SY[i] * s, s, i == 0 ? DPBootUI.WHITE : RAINBOW[i % RAINBOW.length]);
        if (snakeDead) center("GAME OVER - space", ox, bw, oy + bh / 2 - 4, DPBootUI.WHITE);
    }

    // ================================================================== Tetris
    private static final int TW = 10, TH = 20;
    private static final int[][] BOARD = new int[TH][TW];
    // pieces as cells inside an n x n box; rotation turns the box
    private static final int[][] PIECE = {
            {0, 1, 1, 1, 2, 1, 3, 1}, {1, 1, 2, 1, 1, 2, 2, 2}, {1, 0, 0, 1, 1, 1, 2, 1}, {1, 0, 2, 0, 0, 1, 1, 1},
            {0, 0, 1, 0, 1, 1, 2, 1}, {0, 0, 0, 1, 1, 1, 2, 1}, {2, 0, 0, 1, 1, 1, 2, 1}};
    private static final int[] PSIZE = {4, 4, 3, 3, 3, 3, 3};
    private static int tPiece, tNext, tX, tY, tRot, tLines, tScore;
    private static long tFall;
    private static boolean tOver;

    private static void tetrisReset() {
        for (int[] r : BOARD) java.util.Arrays.fill(r, 0);
        tLines = 0; tScore = 0; tOver = false;
        tNext = RND.nextInt(7);
        tSpawn();
        msg = "left/right move, up rotate, down drop, space slam";
    }

    private static void tSpawn() {
        tPiece = tNext; tNext = RND.nextInt(7); tRot = 0; tX = 3; tY = 0;
        if (!tFits(tX, tY, tRot)) { tOver = true; score(1, tScore); msg = "game over - " + tScore + " points, " + tLines + " lines - space"; }
    }

    private static int cx(int p, int i, int rot) {
        int x = PIECE[p][i * 2], y = PIECE[p][i * 2 + 1], n = PSIZE[p];
        for (int r = 0; r < rot; r++) { int t = x; x = n - 1 - y; y = t; }
        return x;
    }

    private static int cy(int p, int i, int rot) {
        int x = PIECE[p][i * 2], y = PIECE[p][i * 2 + 1], n = PSIZE[p];
        for (int r = 0; r < rot; r++) { int t = x; x = n - 1 - y; y = t; }
        return y;
    }

    private static boolean tFits(int px, int py, int rot) {
        for (int i = 0; i < 4; i++) {
            int x = px + cx(tPiece, i, rot), y = py + cy(tPiece, i, rot);
            if (x < 0 || x >= TW || y >= TH || (y >= 0 && BOARD[y][x] != 0)) return false;
        }
        return true;
    }

    private static boolean tMove(int dx, int dy, int rot) {
        if (!tFits(tX + dx, tY + dy, rot)) return false;
        tX += dx; tY += dy; tRot = rot;
        return true;
    }

    private static void tRotate() {
        int r = (tRot + 1) % 4;
        for (int kick : new int[]{0, -1, 1, -2, 2}) if (tMove(kick, 0, r)) return;
    }

    private static void tLock() {
        for (int i = 0; i < 4; i++) {
            int x = tX + cx(tPiece, i, tRot), y = tY + cy(tPiece, i, tRot);
            if (y >= 0) BOARD[y][x] = tPiece + 1;
        }
        int cleared = 0;
        for (int y = TH - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < TW; x++) if (BOARD[y][x] == 0) { full = false; break; }
            if (!full) continue;
            for (int yy = y; yy > 0; yy--) System.arraycopy(BOARD[yy - 1], 0, BOARD[yy], 0, TW);
            java.util.Arrays.fill(BOARD[0], 0);
            cleared++; y++;
        }
        if (cleared > 0) { tLines += cleared; tScore += new int[]{0, 100, 300, 500, 800}[cleared] * (1 + tLines / 10); msg = tScore + " points, " + tLines + " lines"; }
        tSpawn();
    }

    private static void tetris(long now, int x, int y, int w, int h) {
        int s = Math.max(4, Math.min(h / TH, (w - 80) / TW)), bw = s * TW, bh = s * TH, ox = x + (w - bw) / 2, oy = y + (h - bh) / 2;
        if (!tOver && now - tFall > Math.max(90, 600 - tLines * 25)) { tFall = now; if (!tMove(0, 1, tRot)) tLock(); }
        frame(ox, oy, bw, bh);
        for (int yy = 0; yy < TH; yy++) for (int xx = 0; xx < TW; xx++) if (BOARD[yy][xx] != 0) cell(ox + xx * s, oy + yy * s, s, RAINBOW[BOARD[yy][xx] - 1]);
        if (!tOver) {
            int gyy = tY;                                       // ghost: where it would land
            while (tFits(tX, gyy + 1, tRot)) gyy++;
            for (int i = 0; i < 4; i++) {
                int px = tX + cx(tPiece, i, tRot);
                cell(ox + px * s, oy + (gyy + cy(tPiece, i, tRot)) * s, s, 0x30FFFFFF);
                int py = tY + cy(tPiece, i, tRot);
                if (py >= 0) cell(ox + px * s, oy + py * s, s, RAINBOW[tPiece]);
            }
        }
        int nx = ox + bw + 10;
        DPBootUI.text("next", nx, oy, DPBootUI.DIM);
        for (int i = 0; i < 4; i++) cell(nx + PIECE[tNext][i * 2] * 6, oy + 12 + PIECE[tNext][i * 2 + 1] * 6, 6, RAINBOW[tNext]);
        DPBootUI.text("score", nx, oy + 44, DPBootUI.DIM);
        DPBootUI.text(Integer.toString(tScore), nx, oy + 54, DPBootUI.WHITE);
        DPBootUI.text("lines", nx, oy + 68, DPBootUI.DIM);
        DPBootUI.text(Integer.toString(tLines), nx, oy + 78, DPBootUI.WHITE);
        if (tOver) center("GAME OVER - space", ox, bw, oy + bh / 2 - 4, DPBootUI.WHITE);
    }

    // ================================================================== 2048
    private static final int[][] B = new int[4][4];
    private static int bScore;
    private static boolean bOver;

    private static void b2048Reset() {
        for (int[] r : B) java.util.Arrays.fill(r, 0);
        bScore = 0; bOver = false;
        bAdd(); bAdd();
        msg = "arrows slide the tiles - join them to 2048";
    }

    private static void bAdd() {
        int free = 0;
        for (int[] r : B) for (int v : r) if (v == 0) free++;
        if (free == 0) return;
        int k = RND.nextInt(free);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (B[y][x] == 0 && k-- == 0) B[y][x] = RND.nextInt(10) == 0 ? 4 : 2;
    }

    private static void b2048Move(int dx, int dy) {
        boolean moved = false;
        for (int line = 0; line < 4; line++) {
            int[] v = new int[4];
            for (int i = 0; i < 4; i++) v[i] = get(line, i, dx, dy);
            int[] o = new int[4];
            int n = 0, last = 0;
            for (int i = 0; i < 4; i++) {
                if (v[i] == 0) continue;
                if (last == v[i]) { o[n - 1] = last * 2; bScore += last * 2; last = 0; }
                else { o[n++] = v[i]; last = v[i]; }
            }
            for (int i = 0; i < 4; i++) { if (get(line, i, dx, dy) != o[i]) moved = true; set(line, i, dx, dy, o[i]); }
        }
        if (moved) bAdd();
        boolean can = false;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            if (B[y][x] == 0 || (x < 3 && B[y][x] == B[y][x + 1]) || (y < 3 && B[y][x] == B[y + 1][x])) can = true;
        score(2, bScore);
        if (!can) { bOver = true; msg = "no moves left - " + bScore + " points - space"; } else msg = bScore + " points";
    }

    /** cell `i` of `line`, counted from the side the tiles slide toward */
    private static int get(int line, int i, int dx, int dy) {
        if (dx != 0) return B[line][dx < 0 ? i : 3 - i];
        return B[dy < 0 ? i : 3 - i][line];
    }

    private static void set(int line, int i, int dx, int dy, int v) {
        if (dx != 0) B[line][dx < 0 ? i : 3 - i] = v; else B[dy < 0 ? i : 3 - i][line] = v;
    }

    private static void g2048(int x, int y, int w, int h) {
        int s = Math.max(16, Math.min(w, h) / 4), ox = x + (w - 4 * s) / 2, oy = y + (h - 4 * s) / 2;
        frame(ox, oy, 4 * s, 4 * s);
        for (int yy = 0; yy < 4; yy++) for (int xx = 0; xx < 4; xx++) {
            int v = B[yy][xx], px = ox + xx * s + 2, py = oy + yy * s + 2;
            int lg = v == 0 ? 0 : Integer.numberOfTrailingZeros(v);
            DPBootUI.rect(px, py, px + s - 4, py + s - 4, v == 0 ? 0xFF1C1530 : RAINBOW[(lg - 1) % RAINBOW.length]);
            if (v > 0) center(Integer.toString(v), px, s - 4, py + (s - 4) / 2 - 4, lg == 3 || lg == 5 ? 0xFF000000 : DPBootUI.WHITE);
        }
        if (bOver) center("NO MOVES - space", ox, 4 * s, oy - 12, DPBootUI.WHITE);
    }

    // ================================================================== Minesweeper
    private static final int MW = 16, MH = 10, MINES = 22;
    private static final boolean[][] MINE = new boolean[MH][MW], OPEN = new boolean[MH][MW], FLAG = new boolean[MH][MW];
    private static boolean mPlaced, mOver, mWon;
    private static long mStart;

    private static void minesReset() {
        for (int y = 0; y < MH; y++) for (int x = 0; x < MW; x++) { MINE[y][x] = false; OPEN[y][x] = false; FLAG[y][x] = false; }
        mPlaced = false; mOver = false; mWon = false;
        msg = "click to dig, right-click to flag - first click is always safe";
    }

    private static int around(int x, int y) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int ax = x + dx, ay = y + dy;
            if (ax >= 0 && ay >= 0 && ax < MW && ay < MH && MINE[ay][ax]) n++;
        }
        return n;
    }

    private static void dig(int x, int y) {
        if (x < 0 || y < 0 || x >= MW || y >= MH || OPEN[y][x] || FLAG[y][x]) return;
        OPEN[y][x] = true;
        if (MINE[y][x]) { mOver = true; msg = "boom! space for a new board"; return; }
        if (around(x, y) == 0) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) dig(x + dx, y + dy);
    }

    private static void mines(int x, int y, int w, int h) {
        int s = Math.max(6, Math.min(w / MW, h / MH)), bw = s * MW, bh = s * MH, ox = x + (w - bw) / 2, oy = y + (h - bh) / 2;
        if (!mOver && !mWon) {
            int mx = DPBootUI.clickX, my = DPBootUI.clickY;
            if (DPBootUI.clicked(ox, oy, bw, bh)) {
                int cxx = (mx - ox) / s, cyy = (my - oy) / s;
                if (!mPlaced) {                                 // place the mines now, never on/next to the first click
                    mPlaced = true; mStart = System.currentTimeMillis();
                    for (int k = 0; k < MINES; ) {
                        int rx = RND.nextInt(MW), ry = RND.nextInt(MH);
                        if (MINE[ry][rx] || (Math.abs(rx - cxx) <= 1 && Math.abs(ry - cyy) <= 1)) continue;
                        MINE[ry][rx] = true; k++;
                    }
                }
                dig(cxx, cyy);
            }
            if (DPBootUI.rclickX >= 0 && DPBootUI.in(DPBootUI.rclickX, DPBootUI.rclickY, ox, oy, bw, bh)) {
                int cxx = (DPBootUI.rclickX - ox) / s, cyy = (DPBootUI.rclickY - oy) / s;
                if (!OPEN[cyy][cxx]) FLAG[cyy][cxx] = !FLAG[cyy][cxx];
                DPBootUI.rclickX = -1;
            }
            int closed = 0;
            for (int yy = 0; yy < MH; yy++) for (int xx = 0; xx < MW; xx++) if (!OPEN[yy][xx]) closed++;
            if (mPlaced && closed == MINES && !mOver) {
                mWon = true;
                long secs = (System.currentTimeMillis() - mStart) / 1000;
                score(3, Math.max(1, 1000 - secs));
                msg = "cleared in " + secs + " s! space for another";
            }
        }
        frame(ox, oy, bw, bh);
        for (int yy = 0; yy < MH; yy++) for (int xx = 0; xx < MW; xx++) {
            int px = ox + xx * s, py = oy + yy * s;
            boolean open = OPEN[yy][xx] || (mOver && MINE[yy][xx]);
            cell(px, py, s, open ? (MINE[yy][xx] ? 0xFFFF5A64 : 0xFF2A2140) : 0xFF4A3570);
            if (!open && FLAG[yy][xx]) DPBootUI.text("F", px + (s - 5) / 2, py + (s - 8) / 2, DPBootUI.PINK);
            if (open && !MINE[yy][xx]) {
                int n = around(xx, yy);
                if (n > 0) DPBootUI.text(Integer.toString(n), px + (s - 5) / 2, py + (s - 8) / 2, RAINBOW[(n + 3) % RAINBOW.length]);
            }
        }
    }

    // ================================================================== Pong
    private static float bx, by, bvx, bvy, pPlayer, pAi;
    private static int sPlayer, sAi, pongWin;
    private static long pLast;

    private static void pongReset() {
        sPlayer = 0; sAi = 0; pongWin = 0; pPlayer = 0.5f * 100; pAi = 50;
        serve(1);
        msg = "mouse or up/down moves your paddle - first to 7";
    }

    private static void serve(int dir) { bx = 50; by = 50; bvx = 28 * dir; bvy = (RND.nextFloat() - 0.5f) * 30; }

    private static void pong(long now, int x, int y, int w, int h) {
        // world is 100 x 100 units stretched over the box; paddles are 16 tall
        float dt = Math.min(0.05f, (now - pLast) / 1000f);
        pLast = now;
        if (DPBootUI.hover(x, y, w, h)) pPlayer = (DPBootUI.my - y) * 100f / h;
        pPlayer = Math.max(8, Math.min(92, pPlayer));
        if (pongWin == 0) {
            pAi += Math.max(-55 * dt, Math.min(55 * dt, by - pAi));
            pAi = Math.max(8, Math.min(92, pAi));
            bx += bvx * dt; by += bvy * dt;
            if (by < 1 || by > 99) { bvy = -bvy; by = Math.max(1, Math.min(99, by)); }
            if (bx < 4 && Math.abs(by - pPlayer) < 9 && bvx < 0) { bvx = -bvx * 1.06f; bvy += (by - pPlayer) * 4; }
            if (bx > 96 && Math.abs(by - pAi) < 9 && bvx > 0) { bvx = -bvx * 1.06f; bvy += (by - pAi) * 4; }
            if (bx < 0) { sAi++; serve(1); }
            if (bx > 100) { sPlayer++; serve(-1); }
            if (sPlayer >= 7 || sAi >= 7) {
                pongWin = sPlayer >= 7 ? 1 : -1;
                if (pongWin == 1) score(4, BEST[4] + 1);
                msg = (pongWin == 1 ? "you win!" : "the computer wins") + " - space for a rematch (wins: " + BEST[4] + ")";
            }
        }
        frame(x, y, w, h);
        for (int i = 0; i < 10; i++) DPBootUI.rect(x + w / 2, y + i * h / 10 + 2, x + w / 2 + 1, y + i * h / 10 + h / 20, 0x40FFFFFF);
        int ph = h * 16 / 100;
        DPBootUI.rect(x + w * 2 / 100, y + (int) (pPlayer * h / 100) - ph / 2, x + w * 2 / 100 + 4, y + (int) (pPlayer * h / 100) + ph / 2, DPBootUI.PINK);
        DPBootUI.rect(x + w * 98 / 100 - 4, y + (int) (pAi * h / 100) - ph / 2, x + w * 98 / 100, y + (int) (pAi * h / 100) + ph / 2, DPBootUI.BLUE);
        int bxx = x + (int) (bx * w / 100), byy = y + (int) (by * h / 100);
        DPBootUI.rect(bxx - 2, byy - 2, bxx + 2, byy + 2, DPBootUI.WHITE);
        center(sPlayer + "   " + sAi, x, w, y + 4, DPBootUI.WHITE);
    }

    // ================================================================== Memory
    private static final int[] CARD = new int[16];
    private static final boolean[] SHOWN = new boolean[16];
    private static int first = -1, second = -1, moves, pairs;
    private static long hideAt;

    private static void memoryReset() {
        for (int i = 0; i < 16; i++) { CARD[i] = i / 2; SHOWN[i] = false; }
        for (int i = 15; i > 0; i--) { int j = RND.nextInt(i + 1), t = CARD[i]; CARD[i] = CARD[j]; CARD[j] = t; }
        first = second = -1; moves = 0; pairs = 0;
        msg = "find the 8 pairs in as few moves as you can";
    }

    private static void memory(long now, int x, int y, int w, int h) {
        int s = Math.max(14, Math.min(w, h) / 4), ox = x + (w - 4 * s) / 2, oy = y + (h - 4 * s) / 2;
        if (second >= 0 && now > hideAt) {
            if (CARD[first] != CARD[second]) { SHOWN[first] = false; SHOWN[second] = false; }
            first = second = -1;
        }
        for (int i = 0; i < 16; i++) {
            int px = ox + (i % 4) * s + 2, py = oy + (i / 4) * s + 2;
            boolean up = SHOWN[i];
            DPBootUI.rect(px, py, px + s - 4, py + s - 4, up ? RAINBOW[CARD[i]] : DPBootUI.hover(px, py, s - 4, s - 4) ? 0xFF6A3FA0 : 0xFF4A3570);
            if (up) center(Character.toString((char) ('A' + CARD[i])), px, s - 4, py + (s - 4) / 2 - 4, 0xFF000000);
            if (!up && second < 0 && DPBootUI.clicked(px, py, s - 4, s - 4)) {
                SHOWN[i] = true;
                if (first < 0) first = i;
                else {
                    second = i; moves++; hideAt = now + 700;
                    if (CARD[first] == CARD[second]) { pairs++; first = second = -1; }
                    msg = moves + " moves, " + pairs + " / 8 pairs";
                    if (pairs == 8) { score(5, moves); msg = "all pairs in " + moves + " moves! space to shuffle"; }
                }
            }
        }
    }

    // ================================================================== Clicker
    private static double hearts;
    private static int perClick = 1, rainbows;
    private static long cLast;

    private static void clicker(long now, int x, int y, int w, int h) {
        if (cLast > 0) hearts += rainbows * (now - cLast) / 1000.0;
        cLast = now;
        int s = Math.min(w / 2, h - 30), hx = x + w / 4 - s / 2, hy = y + (h - s) / 2;
        boolean over = DPBootUI.hover(hx, hy, s, s);
        int pad = over ? 0 : 3;
        // a pixel heart
        String[] art = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};
        int px = Math.max(2, (s - 2 * pad) / 7);
        for (int r = 0; r < art.length; r++) for (int c = 0; c < 7; c++)
            if (art[r].charAt(c) == 'X') cell(hx + pad + c * px, hy + pad + r * px, px, RAINBOW[r % RAINBOW.length]);
        if (DPBootUI.clicked(hx, hy, s, s)) hearts += perClick;
        int rx = x + w / 2 + 10, ry = y + 10;
        DPBootUI.text((long) hearts + " hearts", rx, ry, DPBootUI.PINK);
        DPBootUI.text(rainbows + " per second, " + perClick + " per click", rx, ry + 12, DPBootUI.DIM);
        long costR = (long) (15 * Math.pow(1.15, rainbows)), costC = (long) (25 * Math.pow(1.4, perClick - 1));
        if (DPBootUI.button(rx, ry + 30, 150, 12, "Rainbow +1/s  (" + costR + ")", hearts >= costR) && hearts >= costR) { hearts -= costR; rainbows++; }
        if (DPBootUI.button(rx, ry + 46, 150, 12, "Bigger heart +1  (" + costC + ")", hearts >= costC) && hearts >= costC) { hearts -= costC; perClick++; }
        score(6, (long) hearts);
    }

    // ================================================================== Runner (#12 + "tiny parkour")
    // A Minecraft-y side runner: jump over TNT, creepers and lava gaps, grab diamonds and food. World units are
    // pixels of the game box; the ground scrolls left faster and faster.
    private static final int RN = 24;
    private static final int[] OT = new int[RN];         // 0 empty, 1 diamond, 2 food, 3 TNT, 4 creeper, 5 lava gap, 6 floating platform
    private static final float[] OX = new float[RN], OY = new float[RN], OW = new float[RN];
    private static float rY, rVy, rSpeed, rDist, rNext;
    private static int rScore, rJumps;
    private static boolean rDead, rJump;
    private static long rLast;

    private static void runnerReset() {
        java.util.Arrays.fill(OT, 0);
        rY = 0; rVy = 0; rSpeed = 90; rDist = 0; rNext = 160; rScore = 0; rJumps = 0; rDead = false; rJump = false;
        msg = "space / up to jump (twice for a double jump) - avoid TNT, creepers and lava";
    }

    private static void spawn(int type, float x, float y, float w) {
        for (int i = 0; i < RN; i++) if (OT[i] == 0) { OT[i] = type; OX[i] = x; OY[i] = y; OW[i] = w; return; }
    }

    private static void runner(long now, int x, int y, int w, int h) {
        float dt = rLast == 0 ? 0 : Math.min(0.05f, (now - rLast) / 1000f);
        rLast = now;
        int ground = h - 18, ps = 12, px = 40;                 // rY = height above the ground (up = positive)
        if (!rDead) {
            rSpeed = Math.min(330, 90 + rDist / 40);
            float dx = rSpeed * dt;
            rDist += dx;
            for (int i = 0; i < RN; i++) if (OT[i] != 0) { OX[i] -= dx; if (OX[i] + OW[i] < -20) OT[i] = 0; }
            rNext -= dx;
            if (rNext <= 0) {                                  // next obstacle / pickup, further apart as it speeds up
                float sx = w + 10;
                int r = RND.nextInt(10);
                if (r < 2) spawn(3, sx, 0, 12);
                else if (r < 4) spawn(4, sx, 0, 12);
                else if (r < 6) spawn(5, sx, 0, 26 + RND.nextInt(20));
                else if (r < 8) { spawn(6, sx, 34 + RND.nextInt(16), 40); spawn(1, sx + 14, 56 + RND.nextInt(10), 10); }
                else spawn(RND.nextBoolean() ? 1 : 2, sx, 6 + RND.nextInt(30), 10);
                rNext = 70 + RND.nextInt(90) + rSpeed / 3;
            }
            if (rJump) { rJump = false; if (rY <= 0.01f || rJumps < 2) { rVy = rJumps == 0 ? 230 : 200; rJumps++; } }
            rVy -= 620 * dt;
            float oldY = rY;
            rY += rVy * dt;
            // what's under us: ground (unless over a lava gap) or a floating platform
            float floor = 0;
            boolean overLava = false;
            for (int i = 0; i < RN; i++) {
                if (OT[i] == 0 || OX[i] > px + ps - 3 || OX[i] + OW[i] < px + 3) continue;
                if (OT[i] == 5) overLava = true;
                if (OT[i] == 6 && oldY >= OY[i] - 1 && rY <= OY[i]) floor = Math.max(floor, OY[i]);
            }
            if (floor > 0 && rY <= floor) { rY = floor; rVy = 0; rJumps = 0; }
            else if (!overLava && rY <= 0) { rY = 0; rVy = 0; rJumps = 0; }
            if (overLava && rY < -ps) { rDead = true; msg = "fell in the lava!"; }
            for (int i = 0; i < RN && !rDead; i++) {
                int t = OT[i];
                if (t == 0 || t >= 5) continue;
                float oy = t >= 3 ? 0 : OY[i], oh = t == 4 ? 18 : t == 3 ? 12 : 10;
                boolean hit = OX[i] < px + ps - 2 && OX[i] + OW[i] > px + 2 && oy < rY + ps - 2 && oy + oh > rY + 2;
                if (!hit) continue;
                if (t <= 2) { rScore += t == 1 ? 10 : 5; OT[i] = 0; }
                else { rDead = true; msg = t == 3 ? "boom - TNT!" : "a creeper got you!"; }
            }
            if (rDead) {
                long sc = rScore + (long) (rDist / 10);
                score(7, sc);
                msg += "  score " + sc + " - space to run again";
            }
        }
        frame(x, y, w, h);
        DPBootUI.rect(x, y, x + w, y + ground - 30, 0xFF1A1440);                       // night sky
        DPBootUI.rect(x, y + ground, x + w, y + h, 0xFF5A3A22);                         // dirt
        DPBootUI.rect(x, y + ground, x + w, y + ground + 3, 0xFF4CAF3A);                // grass
        for (int i = 0; i < RN; i++) {
            int t = OT[i];
            if (t == 0) continue;
            int ox = x + (int) OX[i], ow = (int) OW[i];
            if (ox > x + w || ox + ow < x) continue;
            int end = Math.min(x + w, ox + ow);
            ox = Math.max(x, ox); ow = end - ox;
            switch (t) {
                case 1: DPBootUI.rect(ox + 2, y + ground - (int) OY[i] - 9, ox + 8, y + ground - (int) OY[i] - 1, 0xFF5BE7E0); break;
                case 2: DPBootUI.rect(ox + 1, y + ground - (int) OY[i] - 9, ox + 9, y + ground - (int) OY[i] - 1, 0xFFE0302A); break;
                case 3:
                    DPBootUI.rect(ox, y + ground - 12, ox + ow, y + ground, 0xFFD02020);
                    DPBootUI.rect(ox, y + ground - 8, ox + ow, y + ground - 5, 0xFFEEEEEE);
                    break;
                case 4:
                    DPBootUI.rect(ox, y + ground - 18, ox + ow, y + ground, 0xFF3FA03A);
                    DPBootUI.rect(ox + 2, y + ground - 15, ox + 5, y + ground - 12, 0xFF000000);
                    DPBootUI.rect(ox + 7, y + ground - 15, ox + 10, y + ground - 12, 0xFF000000);
                    break;
                case 5:
                    DPBootUI.rect(ox, y + ground, ox + ow, y + h, 0xFFFF6A00);
                    DPBootUI.rect(ox, y + ground + 4, ox + ow, y + h, 0xFFFFA020);
                    break;
                default:
                    DPBootUI.rect(ox, y + ground - (int) OY[i], ox + ow, y + ground - (int) OY[i] + 5, 0xFF8A6A40);
                    break;
            }
        }
        int py = y + ground - (int) rY - ps;
        if (py < y + h) {
            DPBootUI.rect(x + px, py + 4, x + px + ps, py + ps, 0xFF2E9BD6);             // shirt
            DPBootUI.rect(x + px + 2, py, x + px + ps - 2, py + 5, 0xFFE8B38A);           // head
        }
        String sc = "score " + (rScore + (long) (rDist / 10));
        DPBootUI.text(sc, x + w - 6 - DPBootUI.width(sc), y + 4, DPBootUI.WHITE);
        if (rDead) center("GAME OVER - space", x, w, y + h / 2 - 20, DPBootUI.WHITE);
    }
}
