package com.dogpound.canvas;

import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loading screen #9: a little gallery of the textures being loaded right now. While a texture bar runs, the newest
 * texture name is picked ~3x a second, decoded on a low-priority background thread (never the loading thread), and
 * uploaded on the splash thread at most once per frame. Keeps the last 16; old GL textures are freed.
 */
final class DPBootAssets {
    private DPBootAssets() {}

    static final int MAX = 16;
    static final int[] TEX = new int[MAX];
    static final String[] NAME = new String[MAX];
    static int count, head;                          // ring: newest at head-1

    private static volatile String want;             // texture name waiting to be decoded
    private static volatile ByteBuffer ready;        // decoded RGBA waiting for upload
    private static volatile int readyW, readyH;
    private static volatile String readyName;
    private static volatile Thread worker;
    private static long lastPick;
    private static String lastName = "";

    /** splash thread, every frame: maybe queue the current texture, maybe upload a decoded one */
    static void frame() {
        try {
            String bar = DPBoot.curBarTitle.toLowerCase(java.util.Locale.ROOT), msg = DPBoot.curBarMsg;
            long now = System.currentTimeMillis();
            if (bar.contains("textur") && msg.indexOf(':') > 0 && !msg.equals(lastName) && now - lastPick > 300) {
                lastPick = now; lastName = msg;
                want = msg;
                if (worker == null) {
                    worker = new Thread(DPBootAssets::loop, "Pride-BootAssets");
                    worker.setDaemon(true);
                    worker.setPriority(Thread.MIN_PRIORITY);
                    worker.start();
                }
            }
            ByteBuffer b = ready;
            if (b != null) {
                ready = null;
                int id = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, readyW, readyH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
                if (TEX[head] > 0) GL11.glDeleteTextures(TEX[head]);
                TEX[head] = id;
                NAME[head] = readyName;
                head = (head + 1) % MAX;
                if (count < MAX) count++;
            }
        } catch (Throwable ignored) { }
    }

    private static void loop() {
        long lastWork = System.currentTimeMillis();
        while (System.currentTimeMillis() - lastWork < 20000) {     // nothing to do for 20 s: the thread ends
            try {
                String n = want;
                if (n == null || ready != null) { Thread.sleep(100); continue; }
                want = null;
                lastWork = System.currentTimeMillis();
                decode(n);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable ignored) { }
        }
        worker = null;
    }

    /** "ns:blocks/stone" -> /assets/ns/textures/blocks/stone.png, first animation frame, max 32x32 */
    private static void decode(String name) throws Exception {
        int c = name.indexOf(':');
        String ns = name.substring(0, c), path = name.substring(c + 1);
        if (path.startsWith("textures/")) path = path.substring(9);
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        InputStream in = DPBootAssets.class.getResourceAsStream("/assets/" + ns + "/textures/" + path + ".png");
        if (in == null) return;
        BufferedImage img;
        try (InputStream is = in) { img = ImageIO.read(is); }
        if (img == null) return;
        int w = img.getWidth(), h = Math.min(img.getHeight(), w);   // animated strips: top frame only
        int step = Math.max(1, w / 32), ow = w / step, oh = h / step;
        ByteBuffer buf = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) {
            int p = img.getRGB(x * step, y * step);
            buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        buf.flip();
        readyW = ow; readyH = oh; readyName = ns + ":" + path;
        ready = buf;
    }

    static void draw(int x, int y, int sz, int max) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        for (int i = 0; i < count && i < max; i++) {
            int id = TEX[(head - 1 - i + MAX) % MAX];
            if (id <= 0) continue;
            int px = x + i * (sz + 3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(px, y);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(px, y + sz);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(px + sz, y + sz);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(px + sz, y);
            GL11.glEnd();
        }
    }

    static String newest() { return count == 0 ? null : NAME[(head - 1 + MAX) % MAX]; }
}
