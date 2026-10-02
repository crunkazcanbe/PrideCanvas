package com.dogpound.canvas;

/**
 * Hides the game window during the pre-DogPound splash phase and shows it the moment
 * the DogPound loading screen starts drawing — so you never see the white Mojang frame
 * or a black screen; the window simply appears already on the DogPound screen.
 *
 * Everything is reflection + guarded so it can never crash the splash:
 *  - the GLFW window handle (Display.getWindow()) is 0 until the window is ready, so we
 *    just skip and try again on the next frame;
 *  - glfwHideWindow/glfwShowWindow are idempotent, so calling them every frame is fine
 *    (no flags needed beyond a "shown wins" latch so we never re-hide after showing).
 *
 * Built against Forge 1.12.2 (LWJGL2) but the runtime is Cleanroom LWJGL3, so we reach
 * org.lwjglx.opengl.Display + org.lwjgl.glfw.GLFW by reflection (no compile-time dep).
 */
public final class DPWindow {
    private DPWindow() {}

    private static volatile boolean shown = false;
    private static boolean loggedHide = false, loggedShow = false, loggedDiag = false;

    private static long handle() {
        // 1) The window whose GL context is current on THIS (splash) thread — this is the
        //    window we're actually rendering to, and it works when Display.getWindow()==0.
        try {
            Object cur = Class.forName("org.lwjgl.glfw.GLFW")
                    .getMethod("glfwGetCurrentContext").invoke(null);
            if (cur instanceof Long && (Long) cur != 0L) {
                if (!loggedDiag) { loggedDiag = true; System.out.println("[DogPound] DPWindow diag: glfwGetCurrentContext=" + cur); }
                return (Long) cur;
            }
        } catch (Throwable t) {
            if (!loggedDiag) { loggedDiag = true; System.out.println("[DogPound] DPWindow diag: glfwGetCurrentContext FAILED: " + t); }
        }
        // 2) fall back to the LWJGLX Display window field
        try {
            Object h = Class.forName("org.lwjglx.opengl.Display")
                    .getMethod("getWindow").invoke(null);
            return (h instanceof Long) ? (Long) h : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static void glfw(String method, long h) {
        try {
            Class.forName("org.lwjgl.glfw.GLFW")
                    .getMethod(method, long.class).invoke(null, h);
        } catch (Throwable t) { /* never break the splash */ }
    }

    /** Called every frame of the pre-DogPound phase. Hides the window once it exists. */
    public static void hide() {
        if (shown) return;            // once DogPound has shown the window, never hide again
        long h = handle();
        if (h == 0L) return;          // window not ready yet — retry next frame
        glfw("glfwHideWindow", h);
        if (!loggedHide) { loggedHide = true; System.out.println("[DogPound] splash window hidden (handle=" + h + ")"); }
    }

    /** Called when the DogPound loading screen draws. Shows the window. */
    public static void show() {
        long h = handle();
        if (h == 0L) return;
        glfw("glfwShowWindow", h);
        glfw("glfwFocusWindow", h);
        shown = true;
        if (!loggedShow) { loggedShow = true; System.out.println("[DogPound] window shown for DogPound screen (handle=" + h + ")"); }
    }
}
