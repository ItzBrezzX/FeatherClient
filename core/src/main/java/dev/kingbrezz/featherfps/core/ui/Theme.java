package dev.kingbrezz.featherfps.core.ui;

/**
 * Flat, neutral dark theme with one restrained accent. Colors are ARGB with explicit alpha.
 * ACCENT marks selection and "on"; VIOLET is used only for unsaved changes.
 */
public final class Theme {
    public static final int DIM = 0xA0000000;
    public static final int PANEL = 0xF2141518;
    public static final int SIDEBAR = 0xF20F1012;
    public static final int FIELD = 0xFF0E0F11;
    public static final int ROW_HOVER = 0xFF1B1D21;
    public static final int SELECTED = 0xFF1D2024;
    public static final int DIVIDER = 0xFF262930;
    public static final int BORDER = 0xFF343840;
    public static final int TRACK = 0xFF30333A;
    public static final int TEXT = 0xFFE4E6EA;
    public static final int TEXT_DIM = 0xFF959AA5;
    public static final int TEXT_NOTE = 0xFF6F7580;
    public static final int TEXT_DISABLED = 0xFF565B65;
    public static final int ACCENT = 0xFF56B6CE;
    public static final int ACCENT_DIM = 0xFF2A6577;
    public static final int ACCENT_HOVER = 0xFF317A90;
    public static final int VIOLET = 0xFFA391F0;
    public static final int GOOD = 0xFF72CC98;
    public static final int WARN = 0xFFE0B35A;
    public static final int BAD = 0xFFE27676;

    private Theme() {}

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    public static int withAlpha(int argb, int alpha) { return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }

    public static int fpsColor(double fps) { return fps >= 60 ? GOOD : fps >= 30 ? WARN : BAD; }
}
