package com.dogpound.canvas;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Captures recent log lines into small ring buffers so the Pride loading screens can show "logs as it loads",
 * filter them by level / Forge, search them (roadmap #4) and spot problems (#5). Attaches to the root logger at
 * startup. Runs on whatever thread logs, so it does as little as possible. Everything is wrapped so a log4j version
 * mismatch can never break the game.
 */
public final class DPLogBuffer extends AbstractAppender {
    /** one captured line. lvl: 0 debug/trace, 1 info, 2 warn, 3 error/fatal */
    public static final class Line {
        public final String msg, logger, thrown;
        public String[] stack;                          // first frames of the exception (warnings/errors only)
        public final int lvl;
        public final long time;
        public final boolean forge;
        Line(String msg, String logger, int lvl, String thrown) {
            this.msg = msg; this.logger = logger; this.lvl = lvl; this.thrown = thrown;
            this.time = System.currentTimeMillis();
            String l = logger == null ? "" : logger;
            this.forge = l.startsWith("FML") || l.startsWith("net.minecraftforge") || l.equalsIgnoreCase("forge") || l.startsWith("LaunchWrapper");
        }
    }

    private static final Deque<Line> ALL = new ConcurrentLinkedDeque<>();     // every level
    private static final Deque<Line> WARN = new ConcurrentLinkedDeque<>();    // warnings + errors only (survive debug floods)
    private static final int MAX = 400, MAX_WARN = 200;
    private static final java.util.concurrent.atomic.AtomicInteger allSize = new java.util.concurrent.atomic.AtomicInteger(), warnSize = new java.util.concurrent.atomic.AtomicInteger();
    /** bumped on every new line, so readers can skip work when nothing changed */
    public static volatile int version;
    private static boolean installed = false;

    private DPLogBuffer() {
        // 4-arg ctor is present across log4j 2.8.x .. 2.20.x (Forge 1.12.2 + Cleanroom).
        super("DogPoundLog", null, null, true);
    }

    /** Attach to the root logger. Safe no-op if anything goes wrong. */
    public static void install() {
        if (installed) return;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            DPLogBuffer app = new DPLogBuffer();
            app.start();
            ctx.getConfiguration().getRootLogger().addAppender(app, null, null);
            ctx.updateLoggers();
            installed = true;
        } catch (Throwable t) {
            // Older/newer log4j without this ctor: skip; the box shows a fallback line.
        }
    }

    @Override
    public void append(LogEvent event) {
        try {
            Level lv = event.getLevel();
            int lvl = lv == null ? 1 : lv.isMoreSpecificThan(Level.ERROR) ? 3 : lv.isMoreSpecificThan(Level.WARN) ? 2 : lv.isMoreSpecificThan(Level.INFO) ? 1 : 0;
            String msg = event.getMessage().getFormattedMessage();
            if (msg == null) return;
            int nl = msg.indexOf('\n');
            if (nl >= 0) msg = msg.substring(0, nl);
            msg = msg.trim();
            if (msg.isEmpty()) return;
            if (msg.length() > 160) msg = msg.substring(0, 160);
            String thrown = null;
            Throwable t = event.getThrown();
            if (t != null) thrown = t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            Line line = new Line(msg, event.getLoggerName(), lvl, thrown);
            if (t != null && lvl >= 2) {                // dev mode (#36) shows these; only paid when an exception is logged
                StackTraceElement[] st = t.getStackTrace();
                line.stack = new String[Math.min(8, st.length)];
                for (int i = 0; i < line.stack.length; i++) line.stack[i] = st[i].toString();
            }
            ALL.addLast(line);
            if (allSize.incrementAndGet() > MAX && ALL.pollFirst() != null) allSize.decrementAndGet();
            if (lvl >= 2) {
                WARN.addLast(line);
                if (warnSize.incrementAndGet() > MAX_WARN && WARN.pollFirst() != null) warnSize.decrementAndGet();
            }
            version++;
        } catch (Throwable t) {
            // never let logging crash
        }
    }

    /** The last n captured lines (oldest -> newest). */
    public static List<String> last(int n) {
        List<String> all = new ArrayList<String>(ALL.size());
        for (Line l : ALL) all.add(l.msg);
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }

    /** a snapshot of every captured line, or only warnings + errors (oldest -> newest) */
    public static List<Line> snapshot(boolean warnOnly) { return new ArrayList<Line>(warnOnly ? WARN : ALL); }
}
