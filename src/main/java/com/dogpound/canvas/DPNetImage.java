package com.dogpound.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures from the web for Pride menus (project icons, gallery shots): fetched + decoded on a worker thread, cached on disk
 * (config/pridecanvas/cache/img), uploaded as textures on the render thread. Formats Java can't read (WebP) stay null and the
 * caller draws a placeholder. At most 160 textures live at once; the oldest are freed.
 */
public final class DPNetImage {
    private DPNetImage() {}

    public static final class Img { public final ResourceLocation loc; public final int w, h; Img(ResourceLocation l, int w, int h) { loc = l; this.w = w; this.h = h; } }

    private static final File CACHE = new File("config/pridecanvas/cache/img");
    private static final int MAX = 160, MAX_SIDE = 512;
    private static final Map<String, Img> READY = Collections.synchronizedMap(new LinkedHashMap<String, Img>(64, 0.75F, true));
    private static final Map<String, Boolean> ASKED = Collections.synchronizedMap(new HashMap<String, Boolean>());
    private static final ConcurrentLinkedQueue<Object[]> DECODED = new ConcurrentLinkedQueue<Object[]>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "PrideImage"); t.setDaemon(true); return t; });
    private static int seq;

    /** the picture if it's ready, else null (and it starts loading) */
    public static Img get(String url) {
        if (url == null || url.isEmpty()) return null;
        upload();
        Img i = READY.get(url);
        if (i != null || ASKED.containsKey(url)) return i;
        ASKED.put(url, Boolean.TRUE);
        POOL.submit(() -> {
            try {
                File f = new File(CACHE, Integer.toHexString(url.hashCode()) + "-" + url.length());
                byte[] bytes;
                if (f.isFile()) bytes = Files.readAllBytes(f.toPath());
                else {
                    try (InputStream in = DPContentApi.open(url).getInputStream()) { bytes = readAll(in); }
                    CACHE.mkdirs();
                    Files.write(f.toPath(), bytes);
                }
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (img == null) return;                                         // WebP & friends: placeholder
                if (img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE) img = shrink(img);
                DECODED.add(new Object[]{ url, img });
            } catch (Throwable ignored) { }
        });
        return null;
    }

    private static void upload() {
        for (Object[] d; (d = DECODED.poll()) != null; ) {
            BufferedImage img = (BufferedImage) d[1];
            ResourceLocation loc = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("pridecanvas_net_" + (seq++), new DynamicTexture(img));
            READY.put((String) d[0], new Img(loc, img.getWidth(), img.getHeight()));
            synchronized (READY) {
                while (READY.size() > MAX) {
                    String oldest = READY.keySet().iterator().next();
                    Img o = READY.remove(oldest);
                    ASKED.remove(oldest);
                    if (o != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(o.loc);
                }
            }
        }
    }

    private static BufferedImage shrink(BufferedImage src) {
        double k = MAX_SIDE / (double) Math.max(src.getWidth(), src.getHeight());
        int w = Math.max(1, (int) (src.getWidth() * k)), h = Math.max(1, (int) (src.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) { o.write(b, 0, n); if (o.size() > 8 << 20) break; }
        return o.toByteArray();
    }
}
