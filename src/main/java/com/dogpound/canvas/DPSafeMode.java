package com.dogpound.canvas;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loading screen #16: Safe Mode after repeated startup crashes. Runs from the coremod — BEFORE Forge looks at the mods
 * folder — so a choice made here still counts for this start. Plain Java + Swing only (no Minecraft classes exist yet).
 *
 * Every start writes config/pride-boot-unfinished.txt (+1); reaching the main menu deletes it. Two unfinished starts
 * in a row = ask: Start normally / Safe mode (turn off the mods added or updated since the last good start) /
 * Diagnostic (the loading screen opens on the Errors tab with developer details).
 */
public final class DPSafeMode {
    private DPSafeMode() {}

    static final File UNFINISHED = new File("config/pride-boot-unfinished.txt");
    static final File OFF_LIST = new File("config/pride-safe-mode.txt");    // jars Safe Mode turned off (original names)
    static final File SNAPSHOT = new File("config/pride-mods-snapshot.txt");
    static final String OFF_SUFFIX = ".pride-safe";

    /** coremod, first thing: count this start, maybe ask */
    public static void onCoremodStart() {
        try {
            int before = 0;
            try { before = Integer.parseInt(new String(Files.readAllBytes(UNFINISHED.toPath()), StandardCharsets.UTF_8).trim()); } catch (Throwable ignored) { }
            UNFINISHED.getParentFile().mkdirs();
            Files.write(UNFINISHED.toPath(), Integer.toString(before + 1).getBytes(StandardCharsets.UTF_8));
            if (before >= 2 && DPBootSettings.show("safemode")) ask(before);
        } catch (Throwable ignored) { }
    }

    /** main menu reached: this start finished fine */
    static void onFinished() { UNFINISHED.delete(); }

    private static void ask(int fails) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        // no dialog possible (the pack runs with -Djava.awt.headless=true; AWT before LWJGL can hang on macOS):
        // the loading screen asks instead, and Safe mode then counts from the next start
        if (os.startsWith("mac") || Boolean.getBoolean("java.awt.headless") || java.awt.GraphicsEnvironment.isHeadless()) {
            System.setProperty("pride.boot.ask", Integer.toString(fails));
            return;
        }
        List<String> changed = changedSinceGoodStart();
        String msg = "Minecraft didn't reach the main menu the last " + fails + " times it started.\n\n"
                + (changed.isEmpty() ? "No mods were added or updated since the last good start.\n"
                : changed.size() + " mod(s) were added or updated since the last good start:\n  " + String.join("\n  ", changed.subList(0, Math.min(8, changed.size())))
                + (changed.size() > 8 ? "\n  ... and " + (changed.size() - 8) + " more" : "") + "\n")
                + "\nStart normally, try Safe mode (turns those mods off - you can turn them back on from the\n"
                + "'Loaded in ...' report on the main menu), or Diagnostic (loading screen opens on the Errors tab)?";
        String[] opts = changed.isEmpty() ? new String[]{"Start normally", "Diagnostic"} : new String[]{"Start normally", "Safe mode", "Diagnostic"};
        final int[] pick = {0};
        try {
            javax.swing.JOptionPane pane = new javax.swing.JOptionPane(msg, javax.swing.JOptionPane.WARNING_MESSAGE, javax.swing.JOptionPane.DEFAULT_OPTION, null, opts, opts[0]);
            javax.swing.JDialog d = pane.createDialog("Pride - startup trouble");
            d.setAlwaysOnTop(true);
            javax.swing.Timer auto = new javax.swing.Timer(60000, e -> d.dispose());   // nobody there: start normally after a minute
            auto.setRepeats(false);
            auto.start();
            d.setVisible(true);                                                         // modal: waits for her
            auto.stop();
            Object v = pane.getValue();
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(v)) pick[0] = i;
            d.dispose();
        } catch (Throwable t) { return; }
        String choice = opts[pick[0]];
        System.out.println("[Pride UI] startup trouble (" + fails + " unfinished starts): " + choice);
        if (choice.equals("Safe mode")) turnOff(changed);
        if (choice.equals("Diagnostic")) System.setProperty("pride.boot.diag", "1");
    }

    /** jar names added or updated since the last start that reached the menu */
    static List<String> changedSinceGoodStart() {
        List<String> out = new ArrayList<String>();
        try {
            if (!SNAPSHOT.isFile()) return out;
            Map<String, String> old = new HashMap<String, String>();
            for (String j : Files.readAllLines(SNAPSHOT.toPath(), StandardCharsets.UTF_8)) if (!j.trim().isEmpty()) old.put(baseName(j), j.trim());
            for (String j : modJars()) { String o = old.get(baseName(j)); if (o == null || !o.equals(j)) out.add(j); }
        } catch (Throwable ignored) { }
        return out;
    }

    static void turnOff(List<String> jars) {
        List<String> done = new ArrayList<String>();
        for (String j : jars) {
            File f = new File("mods", j);
            if (f.renameTo(new File("mods", j + OFF_SUFFIX))) done.add(j);
        }
        try {
            List<String> all = offList();
            all.addAll(done);
            Files.write(OFF_LIST.toPath(), String.join("\n", all).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
        System.setProperty("pride.boot.safe", Integer.toString(done.size()));
        System.out.println("[Pride UI] Safe mode: turned off " + done);
    }

    /** turn one mod jar off for the next start (startup repair, loading screen #17); returns true if it worked */
    static boolean turnOffJar(String jar) {
        File f = new File("mods", jar);
        if (!f.isFile() || !f.renameTo(new File("mods", jar + OFF_SUFFIX))) return false;
        try {
            List<String> all = offList();
            all.add(jar);
            Files.write(OFF_LIST.toPath(), String.join("\n", all).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
        return true;
    }

    /** open a folder in the system file manager, off-thread */
    static void openFolder(File dir) {
        Thread t = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT), p = dir.getAbsolutePath();
                new ProcessBuilder(os.startsWith("win") ? new String[]{"explorer", p} : os.startsWith("mac") ? new String[]{"open", p} : new String[]{"xdg-open", p})
                        .redirectErrorStream(true).start();
            } catch (Throwable ignored) { }
        }, "Pride-OpenFolder");
        t.setDaemon(true);
        t.start();
    }

    static List<String> offList() {
        List<String> l = new ArrayList<String>();
        try { for (String s : Files.readAllLines(OFF_LIST.toPath(), StandardCharsets.UTF_8)) if (!s.trim().isEmpty()) l.add(s.trim()); } catch (Throwable ignored) { }
        return l;
    }

    /** put the Safe Mode jars back (takes effect next start); returns how many */
    static int turnBackOn() {
        int n = 0;
        for (String j : offList()) if (new File("mods", j + OFF_SUFFIX).renameTo(new File("mods", j))) n++;
        OFF_LIST.delete();
        return n;
    }

    static List<String> modJars() {
        List<String> l = new ArrayList<String>();
        File[] fs = new File("mods").listFiles();
        if (fs != null) for (File f : fs) if (f.isFile() && f.getName().endsWith(".jar")) l.add(f.getName());
        Collections.sort(l);
        return l;
    }

    /** "Botania-1.12.2-r1.10-363.jar" -> "botania", "[1.12.2] Foo-1.2.jar" -> "foo" (the name without its version) */
    static String baseName(String jar) {
        String n = jar.toLowerCase(Locale.ROOT).replace(".jar", "");
        n = n.replaceAll("^\\s*[\\[(][^\\])]*[\\])]\\s*[-_ ]*", "").replaceAll("^(mc)?1\\.12(\\.2)?[-_ ]+", "");
        n = n.replaceAll("[-_+ ]*(mc)?v?\\d.*$", "").replaceAll("[-_+ ]*(1\\.12(\\.2)?|forge|universal)$", "");
        return n.isEmpty() ? jar.toLowerCase(Locale.ROOT) : n;
    }
}
