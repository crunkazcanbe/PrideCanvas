package com.dogpound.canvas;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Captures recent log lines into a small ring buffer so the DogPound loading
 * screen can show "logs as it loads". Attaches to the root logger at startup.
 * Everything is wrapped so a log4j version mismatch can never break the game.
 */
public final class DPLogBuffer extends AbstractAppender {
    private static final Deque<String> LINES = new ConcurrentLinkedDeque<>();
    private static final int MAX = 80;
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
            String msg = event.getMessage().getFormattedMessage();
            if (msg == null) return;
            // one line, trimmed; skip blanks
            int nl = msg.indexOf('\n');
            if (nl >= 0) msg = msg.substring(0, nl);
            msg = msg.trim();
            if (msg.isEmpty()) return;
            if (msg.length() > 140) msg = msg.substring(0, 140);
            LINES.addLast(msg);
            while (LINES.size() > MAX) LINES.pollFirst();
        } catch (Throwable t) {
            // never let logging crash
        }
    }

    /** The last n captured lines (oldest -> newest). */
    public static List<String> last(int n) {
        List<String> all = new ArrayList<String>(LINES);
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }
}
