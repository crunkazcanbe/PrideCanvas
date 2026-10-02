package com.dogpound.canvas;

import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The Pride window icon and title (her ask 2026-10-01: "the little icon at the top left of the window … change that to
 * something that matches the modpack"). The block-P icon in trans stripes is in assets/pridecanvas/icon/icon_<size>.png.
 */
public final class DPIcon {
    private DPIcon() {}

    private static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    public static void apply() {
        try {
            List<ByteBuffer> buffers = new ArrayList<>();
            for (int size : SIZES) {
                try (InputStream in = DPIcon.class.getResourceAsStream("/assets/pridecanvas/icon/icon_" + size + ".png")) {
                    if (in == null) continue;
                    buffers.add(rgba(ImageIO.read(in)));
                }
            }
            if (!buffers.isEmpty()) Display.setIcon(buffers.toArray(new ByteBuffer[0]));
        } catch (Throwable t) {
            System.out.println("[PrideCanvas] window icon not set: " + t);
        }
        try { Display.setTitle("Pride"); } catch (Throwable ignored) {}
    }

    private static ByteBuffer rgba(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer b = ByteBuffer.allocateDirect(w * h * 4);
        for (int p : px) {
            b.put((byte) ((p >> 16) & 0xFF)).put((byte) ((p >> 8) & 0xFF)).put((byte) (p & 0xFF)).put((byte) ((p >> 24) & 0xFF));
        }
        b.flip();
        return b;
    }
}
