package dev.kingbrezz.featherfps.core.ui;

/**
 * The only drawing surface the settings screen needs. A loader module implements it on top of the game's
 * GuiGraphics; tests and the offscreen preview implement it with plain Java. Colors are ARGB.
 */
public interface UiRenderer {
    void fillRect(int x, int y, int w, int h, int argb);

    void drawText(String text, int x, int y, int argb);

    int textWidth(String text);

    int lineHeight();

    void pushClip(int x, int y, int w, int h);

    void popClip();

    /** Pixel-stepped rounded rectangle built from fillRect, so every renderer draws it identically. */
    default void fillRoundRect(int x, int y, int w, int h, int radius, int argb) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { fillRect(x, y, w, h, argb); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * (double) r - dy * dy)));
            if (w - 2 * inset <= 0) continue;
            fillRect(x + inset, y + i, w - 2 * inset, 1, argb);
            fillRect(x + inset, y + h - 1 - i, w - 2 * inset, 1, argb);
        }
        if (h - 2 * r > 0) fillRect(x, y + r, w, h - 2 * r, argb);
    }

    default void outlineRect(int x, int y, int w, int h, int argb) {
        fillRect(x, y, w, 1, argb);
        fillRect(x, y + h - 1, w, 1, argb);
        fillRect(x, y + 1, 1, h - 2, argb);
        fillRect(x + w - 1, y + 1, 1, h - 2, argb);
    }
}
