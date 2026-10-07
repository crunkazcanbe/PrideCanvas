package com.dogpound.canvas;

/**
 * Loads + initializes, ON THE MAIN THREAD, every class the loading screen's splash thread and its helper threads will
 * ever use - before the splash thread starts (the coremod calls this at the top of SplashProgress.start()).
 *
 * Why: Mixin is not thread-safe. A class loaded for the first time on the splash thread goes through Mixin's
 * transformer while the main thread is changing Mixin's config list -> ConcurrentModificationException in
 * MixinProcessor.selectConfigs -> crash at "Initializing game" (the pack, 2026-10-05). After this runs, the splash
 * only ever finds already-loaded classes.
 *
 * RULE for future loading-screen code: anything the splash / helper threads touch must be listed here, and they
 * must never reach into Forge registries, mod objects or other mods' classes (those are copied on the main thread
 * by DPBootMain instead).
 */
public final class DPBootPreload {
    private DPBootPreload() {}

    private static final String[] OURS = {
            "DPConfig", "DPSplashHook", "DPLogBuffer", "DPLogBuffer$Line", "DPMusic", "DPMusicPause", "DPSysInfo", "DPSysInfoWin",
            "DPBoot", "DPBoot$Problem", "DPBootUI", "DPBootGames", "DPBootViews", "DPBootAssets", "DPBootBackdrop", "DPBootSettings",
            "DPBootTheme", "DPBootTerminal", "DPBootJvm", "DPSafeMode", "DPBootCompact", "DPBootFont"};

    private static boolean done;

    /** "Pride" in the window title from the very start (Cleanroom names it "Cleanroom"); main thread only */
    public static void title() {
        try { if (!"Pride".equals(org.lwjgl.opengl.Display.getTitle())) org.lwjgl.opengl.Display.setTitle("Pride"); } catch (Throwable ignored) { }
    }

    public static void run() {
        if (done) return;
        done = true;
        long t0 = System.nanoTime();
        title();
        ClassLoader cl = DPBootPreload.class.getClassLoader();
        int n = 0;
        for (String c : OURS) {
            try { Class.forName("com.dogpound.canvas." + c, true, cl); n++; }
            catch (Throwable t) { System.out.println("[Pride UI] loading-screen preload: " + c + " -> " + t); }
        }
        DPBootFont.prepare();   // the splash's own text renderer: decode ascii.png here, on the main thread
        // the Forge class the splash reads every frame (its mixins - e.g. Zoomies' - are early ones, already registered)
        for (String c : new String[]{"net.minecraftforge.fml.common.ProgressManager", "net.minecraftforge.fml.common.ProgressManager$ProgressBar"})
            try { Class.forName(c, true, cl); } catch (Throwable t) { System.out.println("[Pride UI] loading-screen preload: " + c + " -> " + t); }
        // the music decoder (jorbis): when music starts from the loading screen's Music switch, the music thread must not
        // be the first to load these either
        try {
            java.net.URL jar = com.jcraft.jorbis.Info.class.getProtectionDomain().getCodeSource().getLocation();
            java.io.File f = new java.io.File(jar.toURI());
            if (f.isFile()) try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(f)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    String e = en.nextElement().getName();
                    if ((e.startsWith("com/jcraft/jorbis/") || e.startsWith("com/jcraft/jogg/")) && e.endsWith(".class"))
                        try { Class.forName(e.substring(0, e.length() - 6).replace('/', '.'), true, cl); } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable t) { System.out.println("[Pride UI] loading-screen preload: jorbis -> " + t); }
        try { javax.sound.sampled.AudioSystem.getSourceDataLine(new javax.sound.sampled.AudioFormat(44100, 16, 2, true, false)); }
        catch (Throwable ignored) { }
        // JDK pieces the splash / helper threads use (image decoding, JVM stats, formatting), warmed up here once
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (String fmt : new String[]{"png", "jpg"}) {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, fmt, o);
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(o.toByteArray()));
            }
        } catch (Throwable t) { System.out.println("[Pride UI] loading-screen preload: ImageIO -> " + t); }
        try {
            java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
            java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            java.lang.management.ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            for (java.lang.management.GarbageCollectorMXBean g : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) g.getCollectionCount();
            for (java.lang.management.MemoryPoolMXBean p : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) p.getUsage();
            java.lang.management.ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
            java.lang.management.ThreadMXBean tb = java.lang.management.ManagementFactory.getThreadMXBean();
            tb.getThreadInfo(tb.getAllThreadIds());
            if (tb.isThreadCpuTimeSupported()) tb.getCurrentThreadCpuTime();
            try { java.lang.management.ManagementFactory.getCompilationMXBean().getTotalCompilationTime(); } catch (Throwable ignored) { }
            java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) ((com.sun.management.OperatingSystemMXBean) os).getSystemCpuLoad();
        } catch (Throwable t) { System.out.println("[Pride UI] loading-screen preload: management -> " + t); }
        try {
            String.format(java.util.Locale.ROOT, "%,d %.1f %02d %s", 1234, 1.5f, 3, "x");
            java.util.regex.Pattern.compile("(?i)a").matcher("A").find();
            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR);
            new java.util.concurrent.ConcurrentLinkedDeque<String>().add("x");
            new java.util.Random().nextInt(2);
            java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder());
            java.nio.file.Files.exists(new java.io.File("config").toPath());
            new ProcessBuilder("true");
        } catch (Throwable ignored) { }
        System.out.println(String.format(java.util.Locale.ROOT, "[Pride UI] loading-screen classes preloaded on the main thread (%d of %d, %.0f ms)",
                n, OURS.length, (System.nanoTime() - t0) / 1e6));
    }
}
