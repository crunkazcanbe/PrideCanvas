package com.dogpound.canvas;

import java.lang.reflect.Method;

/**
 * Thin reflection bridge to MCEF (net.montoyo.mcef) so we can render a real Chromium
 * browser INSIDE the menu without hard-linking the mod — if MCEF isn't installed (or its
 * Chromium natives never downloaded) every call no-ops and available() stays false, so the
 * menu falls back to the floating browser and never crashes. Draw/input all go through
 * IBrowser: draw(x1,y1,x2,y2) [y1=bottom, y2=top], getTextureID, resize, inject*, loadURL.
 */
public final class DPMcef {
    private DPMcef() {}

    private static Object api;          // net.montoyo.mcef.api.API
    private static Object browser;      // net.montoyo.mcef.api.IBrowser
    private static boolean triedApi = false;
    private static int bw, bh;
    // cached IBrowser methods
    private static Method mDraw, mResize, mClose, mLoad, mBack, mFwd,
            mMove, mButton, mWheel, mKeyTyped, mKeyPressed, mKeyReleased;

    private static Object api() {
        if (triedApi) return api;
        triedApi = true;
        try {
            Class<?> mcefApi = Class.forName("net.montoyo.mcef.api.MCEFApi");
            boolean loaded = (Boolean) mcefApi.getMethod("isMCEFLoaded").invoke(null);
            if (loaded) api = mcefApi.getMethod("getAPI").invoke(null);
        } catch (Throwable ignored) { api = null; }
        return api;
    }

    /** MCEF present AND its natives loaded. */
    public static boolean available() {
        return api() != null;
    }

    public static boolean ready() {
        return browser != null;
    }

    /** Create (or reload) the single browser at the given pixel size. */
    public static void open(String url, int w, int h) {
        if (api() == null) return;
        try {
            if (browser == null) {
                Method create = api.getClass().getMethod("createBrowser", String.class, boolean.class);
                create.setAccessible(true);
                browser = create.invoke(api, url, false);
                Class<?> ib = browser.getClass();
                mDraw = find(ib, "draw", double.class, double.class, double.class, double.class);
                mResize = find(ib, "resize", int.class, int.class);
                mClose = find(ib, "close");
                mLoad = find(ib, "loadURL", String.class);
                mBack = find(ib, "goBack");
                mFwd = find(ib, "goForward");
                mMove = find(ib, "injectMouseMove", int.class, int.class, int.class, boolean.class);
                mButton = find(ib, "injectMouseButton", int.class, int.class, int.class, int.class, boolean.class, int.class);
                mWheel = find(ib, "injectMouseWheel", int.class, int.class, int.class, int.class, int.class);
                mKeyTyped = find(ib, "injectKeyTyped", char.class, int.class);
                mKeyPressed = find(ib, "injectKeyPressedByKeyCode", int.class, char.class, int.class);
                mKeyReleased = find(ib, "injectKeyReleasedByKeyCode", int.class, char.class, int.class);
            } else if (url != null) {
                loadURL(url);
            }
            resize(w, h);
        } catch (Throwable ignored) { browser = null; }
    }

    private static Method find(Class<?> c, String name, Class<?>... args) {
        try { Method m = c.getMethod(name, args); m.setAccessible(true); return m; }
        catch (Throwable t) { return null; }
    }

    private static void call(Method m, Object... a) {
        if (m == null || browser == null) return;
        try { m.invoke(browser, a); } catch (Throwable ignored) {}
    }

    public static void resize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        bw = w; bh = h; call(mResize, w, h);
    }

    /** Draw into the GUI rect (left,top)-(right,bottom); MCEF wants y1=bottom, y2=top. */
    public static void draw(double left, double top, double right, double bottom) {
        call(mDraw, left, bottom, right, top);
    }

    public static void loadURL(String url) { call(mLoad, url); }
    public static void goBack() { call(mBack); }
    public static void goForward() { call(mFwd); }

    public static void mouseMove(int x, int y) { call(mMove, x, y, 0, false); }
    public static void mouseButton(int x, int y, int button, boolean pressed) {
        call(mButton, x, y, 0, button, pressed, 1);
    }
    public static void mouseWheel(int x, int y, int rot) { call(mWheel, x, y, 0, 0, rot); }
    public static void keyTyped(char c) { call(mKeyTyped, c, 0); }
    public static void keyPressed(int key, char c) { call(mKeyPressed, key, c, 0); }
    public static void keyReleased(int key, char c) { call(mKeyReleased, key, c, 0); }

    public static void close() {
        call(mClose);
        browser = null;
    }
}
