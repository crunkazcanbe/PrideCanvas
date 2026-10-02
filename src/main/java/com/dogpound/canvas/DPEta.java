package com.dogpound.canvas;

import java.lang.reflect.Method;

/** Asks Zoomies (if installed) for the loading ETA — "about 3m 20s left". No Zoomies = null, nothing shown. */
public final class DPEta {
    private static Method m;
    private static boolean tried;

    private DPEta() {}

    public static String text() {
        try {
            if (!tried) { tried = true; m = Class.forName("com.dogpound.zoomies.Eta").getMethod("text"); }
            return m == null ? null : (String) m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
