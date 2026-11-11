package dev.kingbrezz.featherfps.core.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-frame immediate-mode helpers: hit regions, clipping, easing and text utilities. */
final class UiKit {
    interface Action { void run(double mx, double my, int button); }

    interface Drag { void drag(double mx, double my); }

    static final class Hit {
        final String id;
        final Rect rect;
        final boolean enabled;
        final List<String> tooltip;
        final Action click;
        final Drag drag;

        Hit(String id, Rect rect, boolean enabled, List<String> tooltip, Action click, Drag drag) {
            this.id = id; this.rect = rect; this.enabled = enabled; this.tooltip = tooltip; this.click = click; this.drag = drag;
        }
    }

    UiRenderer r;
    int mx, my;
    long now;
    float dt = 0.016f;
    final List<Hit> hits = new ArrayList<>();
    private final ArrayDeque<Rect> clips = new ArrayDeque<>();
    private final Map<String, Float> anim = new HashMap<>();
    private long lastNow = -1;

    void beginFrame(UiRenderer renderer, int mouseX, int mouseY, long nowNs) {
        r = renderer; mx = mouseX; my = mouseY;
        dt = lastNow < 0 ? 0.016f : Math.min(0.1f, (nowNs - lastNow) / 1e9f);
        lastNow = nowNs; now = nowNs;
        hits.clear();
        clips.clear();
    }

    void pushClip(Rect rc) {
        Rect c = clips.peek();
        Rect n = c == null ? rc : c.intersect(rc);
        clips.push(n);
        r.pushClip(n.x(), n.y(), n.w(), n.h());
    }

    void popClip() {
        clips.pop();
        r.popClip();
    }

    boolean hover(Rect rc) {
        Rect c = clips.peek();
        return rc.contains(mx, my) && (c == null || c.contains(mx, my));
    }

    Hit addHit(String id, Rect rc, boolean enabled, List<String> tip, Action click, Drag drag) {
        Rect c = clips.peek();
        Rect vis = c == null ? rc : rc.intersect(c);
        if (vis.isEmpty()) return null;
        Hit h = new Hit(id, vis, enabled, tip, click, drag);
        hits.add(h);
        return h;
    }

    /** Eases a value toward its target; a value seen for the first time appears at its target without animating. */
    float smooth(String key, float target) {
        Float cur = anim.get(key);
        if (cur == null) { anim.put(key, target); return target; }
        float v = cur + (target - cur) * (1f - (float) Math.exp(-dt * 16f));
        if (Math.abs(target - v) < 0.002f) v = target;
        anim.put(key, v);
        return v;
    }

    // ---- text ----

    void text(String s, int x, int y, int color) { r.drawText(s, x, y, color); }

    void textRight(String s, int rightX, int y, int color) { r.drawText(s, rightX - r.textWidth(s), y, color); }

    void textCentered(String s, Rect rc, int color) {
        r.drawText(s, rc.x() + (rc.w() - r.textWidth(s)) / 2, rc.y() + (rc.h() - r.lineHeight()) / 2 + 1, color);
    }

    String ellipsize(String s, int maxW) {
        if (r.textWidth(s) <= maxW) return s;
        int dots = r.textWidth("...");
        for (int i = 0; i < s.length(); i++) {
            if (r.textWidth(s.substring(0, i + 1)) + dots > maxW) return s.substring(0, i) + "...";
        }
        return s;
    }

    List<String> wrap(String s, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && r.textWidth(trial) > maxW) { lines.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(trial);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ---- widgets (flat, square, one-pixel borders) ----

    void rect(Rect rc, int fill, int border) {
        r.fillRect(rc.x(), rc.y(), rc.w(), rc.h(), border);
        r.fillRect(rc.x() + 1, rc.y() + 1, rc.w() - 2, rc.h() - 2, fill);
    }

    void box(Rect rc) { rect(rc, Theme.FIELD, Theme.DIVIDER); }

    void divider(int x, int y, int w) { r.fillRect(x, y, w, 1, Theme.DIVIDER); }

    /** Small label with a rule running to the right edge. Returns the height used. */
    int groupHeader(String title, int x, int y, int w, int reserveRight) {
        r.drawText(title, x, y, Theme.TEXT_DIM);
        int tw = r.textWidth(title), rw = w - tw - 8 - reserveRight;
        if (rw > 0) r.fillRect(x + tw + 8, y + r.lineHeight() / 2, rw, 1, Theme.DIVIDER);
        return r.lineHeight() + 7;
    }

    void button(String id, Rect rc, String label, boolean enabled, boolean primary, Action click, List<String> tip) {
        float h = smooth("hov:" + id, enabled && hover(rc) ? 1f : 0f);
        int fill, border, textColor;
        if (!enabled) { fill = 0xFF16171A; border = Theme.DIVIDER; textColor = Theme.TEXT_DISABLED; }
        else if (primary) { fill = Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT_HOVER, h); border = Theme.ACCENT; textColor = 0xFFFFFFFF; }
        else { fill = Theme.lerp(0xFF1C1E22, 0xFF272A2F, h); border = Theme.lerp(Theme.BORDER, Theme.ACCENT, h * 0.8f); textColor = Theme.TEXT; }
        rect(rc, fill, border);
        textCentered(ellipsize(label, rc.w() - 2), rc, textColor);
        addHit(id, rc, enabled, tip, click, null);
    }

    /** Text tab with an underline when selected. */
    void tab(String id, Rect rc, String label, boolean selected, Action click, List<String> tip) {
        float h = smooth("hov:" + id, hover(rc) ? 1f : 0f);
        textCentered(label, rc, selected ? Theme.TEXT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, h));
        if (selected) r.fillRect(rc.x() + 2, rc.bottom() - 1, rc.w() - 4, 1, Theme.ACCENT);
        addHit(id, rc, true, tip, click, null);
    }

    void toggle(String id, Rect rc, boolean on, boolean enabled, Action click, List<String> tip) {
        float k1 = smooth("tg:" + id, on ? 1f : 0f);
        int fill = Theme.lerp(0xFF22252A, Theme.ACCENT_DIM, k1);
        int border = Theme.lerp(Theme.BORDER, Theme.ACCENT, k1);
        if (!enabled) { fill = Theme.lerp(0xFF1A1C1F, 0xFF223A42, k1); border = Theme.lerp(Theme.DIVIDER, 0xFF3C6672, k1); }
        rect(rc, fill, border);
        int kn = rc.h() - 6;
        int kx = rc.x() + 3 + Math.round(k1 * (rc.w() - kn - 6));
        int knob = enabled ? Theme.lerp(0xFF8C919B, 0xFFFFFFFF, k1) : 0xFF6A6F78;
        r.fillRect(kx, rc.y() + 3, kn, kn, knob);
        addHit(id, rc, enabled, tip, click, null);
    }
}
