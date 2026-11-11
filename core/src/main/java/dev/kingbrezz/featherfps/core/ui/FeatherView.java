package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.Info;
import dev.kingbrezz.featherfps.core.NavigationModel;
import dev.kingbrezz.featherfps.core.Section;
import dev.kingbrezz.featherfps.core.Setting;
import dev.kingbrezz.featherfps.core.Settings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole settings screen, independent of Minecraft. A loader module forwards frames and input to it and
 * supplies a {@link UiRenderer}. Changes stay pending until Apply; Escape discards them.
 */
public final class FeatherView {
    private static final int SIDEBAR_GAP = 9;

    private final ViewEnvironment env;
    private final OptionsModel options;
    private final NavigationModel nav = new NavigationModel();
    private final UiKit k = new UiKit();
    private final Pages pages;
    private final float[] scrollTarget = new float[Section.values().length + 1];
    private final float[] scrollCur = new float[Section.values().length + 1];
    private int maxScroll;
    private int viewportH;
    private String status = "";
    private long statusUntil;
    private long lastNow;
    private Rect panel = new Rect(0, 0, 0, 0);
    private UiKit.Hit dragging;
    private String query = "";
    private boolean searchFocused;

    public FeatherView(ViewEnvironment env) {
        this.env = env;
        this.options = new OptionsModel(env.config);
        this.pages = new Pages(k, env, options, this::setStatus);
    }

    // ---------------------------------------------------------------- state accessors (also used by tests)

    public OptionsModel options() { return options; }

    public Section section() { return nav.selected(); }

    public void select(Section s) { nav.select(s); dragging = null; query = ""; searchFocused = false; }

    public String searchQuery() { return query; }

    public boolean isSearchFocused() { return searchFocused; }

    public String status() { return status; }

    private int slot() { return query.isEmpty() ? nav.selected().ordinal() : Section.values().length; }

    private String tail(String text, int maxW) {
        String t = text;
        while (!t.isEmpty() && k.r.textWidth(t) > maxW) t = t.substring(1);
        return t;
    }

    public Rect hitRect(String id) {
        for (int i = k.hits.size() - 1; i >= 0; i--) if (k.hits.get(i).id.equals(id)) return k.hits.get(i).rect;
        return null;
    }

    public List<String> hitTooltip(String id) {
        for (int i = k.hits.size() - 1; i >= 0; i--) if (k.hits.get(i).id.equals(id)) return k.hits.get(i).tooltip;
        return null;
    }

    public boolean hitEnabled(String id) {
        for (int i = k.hits.size() - 1; i >= 0; i--) if (k.hits.get(i).id.equals(id)) return k.hits.get(i).enabled;
        return false;
    }

    // ---------------------------------------------------------------- rendering

    /** The sidebar groups sections with a hairline before FPS, Profiles and Advanced. */
    private static boolean startsGroup(Section s) {
        return s == Section.FPS || s == Section.PROFILES || s == Section.ADVANCED;
    }

    public void render(UiRenderer r, int sw, int sh, int mx, int my, long now) {
        k.beginFrame(r, mx, my, now);
        lastNow = now;
        int lh = r.lineHeight();
        r.fillRect(0, 0, sw, sh, Theme.DIM);

        int pw = Math.max(240, Math.min(sw - 20, 580)), ph = Math.max(160, Math.min(sh - 20, 340));
        panel = new Rect((sw - pw) / 2, (sh - ph) / 2, pw, ph);
        k.rect(panel, Theme.PANEL, Theme.BORDER);

        // ---- sidebar
        int navW = r.textWidth(Info.NAME);
        for (Section s : Section.values()) navW = Math.max(navW, r.textWidth(s.title));
        int sbw = Math.max(100, Math.min(140, navW + 32));
        int sx = panel.x() + 1, sy = panel.y() + 1, sh2 = panel.h() - 2;
        r.fillRect(sx, sy, sbw, sh2, Theme.SIDEBAR);
        r.fillRect(sx + sbw, sy, 1, sh2, Theme.DIVIDER);
        boolean tight = sh2 < 200;
        int topPad = tight ? 7 : 11, gap = tight ? 5 : SIDEBAR_GAP;
        r.drawText(Info.NAME, sx + 12, sy + topPad, Theme.TEXT);
        r.drawText(k.ellipsize("Minecraft " + env.minecraftVersion, sbw - 20), sx + 12, sy + topPad + lh + 3, Theme.TEXT_NOTE);
        int y0 = sy + topPad + 2 * lh + 3 + (tight ? 9 : 14);
        r.fillRect(sx + 12, y0 - (tight ? 4 : 7), sbw - 24, 1, Theme.DIVIDER);
        int groups = 3;
        int itemH = Math.max(lh + (tight ? 1 : 3), Math.min(lh + 9, (sy + sh2 - y0 - 8 - groups * gap) / Section.values().length));
        List<Section> dirty = new ArrayList<>();
        for (String key : options.pendingKeys()) {
            Setting st = Settings.find(key);
            if (st != null) dirty.add(st.section);
        }
        int ny = y0;
        for (Section s : Section.values()) {
            if (startsGroup(s)) {
                r.fillRect(sx + 12, ny + gap / 2, sbw - 24, 1, Theme.DIVIDER);
                ny += gap;
            }
            Rect rc = new Rect(sx + 1, ny, sbw - 1, itemH);
            boolean selected = nav.selected() == s && query.isEmpty();
            float h = k.smooth("nav:" + s.name(), k.hover(rc) ? 1f : 0f);
            if (selected) {
                r.fillRect(rc.x(), rc.y(), rc.w(), rc.h(), Theme.SELECTED);
                r.fillRect(rc.x(), rc.y(), 2, rc.h(), Theme.ACCENT);
            } else if (h > 0.01f) {
                r.fillRect(rc.x(), rc.y(), rc.w(), rc.h(), Theme.withAlpha(Theme.ROW_HOVER, Math.round(255 * h)));
            }
            r.drawText(s.title, rc.x() + 11, rc.y() + (rc.h() - lh) / 2 + 1, selected ? Theme.TEXT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, h));
            if (dirty.contains(s)) r.fillRect(rc.right() - 9, rc.y() + rc.h() / 2 - 1, 3, 3, Theme.VIOLET);
            k.addHit("nav:" + s.name(), rc, true, null, (x, y, b) -> select(s), null);
            ny += itemH;
        }

        // ---- header
        int mx0 = sx + sbw + 1, mw = panel.right() - 1 - mx0;
        int hh = lh + 17;
        Rect header = new Rect(mx0, panel.y() + 1, mw, hh);
        String title = query.isEmpty() ? nav.selected().title : "Search results";
        r.drawText(title, header.x() + Pages.PAD, header.y() + (hh - lh) / 2 + 1, Theme.TEXT);
        int sx0 = header.x() + Pages.PAD + r.textWidth(title) + 14;
        int boxW = Math.min(150, header.right() - Pages.PAD - sx0);
        if (boxW >= 70) {
            Rect box = new Rect(header.right() - Pages.PAD - boxW, header.y() + (hh - (lh + 9)) / 2, boxW, lh + 9);
            float fh = k.smooth("search", searchFocused ? 1f : 0f);
            k.rect(box, Theme.FIELD, Theme.lerp(Theme.BORDER, Theme.ACCENT, fh));
            int innerW = box.w() - 14;
            int ty = box.y() + (box.h() - lh) / 2 + 1;
            if (query.isEmpty() && !searchFocused) {
                r.drawText(k.ellipsize("Search (Ctrl+F)", innerW), box.x() + 7, ty, Theme.TEXT_DISABLED);
            } else {
                String shown = tail(query, innerW - 4);
                r.drawText(shown, box.x() + 7, ty, Theme.TEXT);
                if (searchFocused && (now / 500_000_000L) % 2 == 0) r.fillRect(box.x() + 7 + r.textWidth(shown) + 1, ty - 1, 1, lh + 1, Theme.ACCENT);
            }
            k.addHit("search:box", box, true, List.of("Search settings", "Type to find a setting by name or description. Esc clears the search."),
                    (x, y, b) -> searchFocused = true, null);
        }
        r.fillRect(mx0, header.bottom(), mw, 1, Theme.DIVIDER);

        // ---- footer
        int fh = lh + 16;
        boolean showStatus = now < statusUntil && !status.isEmpty();
        List<String> statusLines = showStatus ? k.wrap(status, mw - 2 * Pages.PAD) : List.of();
        int extra = showStatus ? statusLines.size() * (lh + 2) + 8 : 0;
        Rect footer = new Rect(mx0, panel.bottom() - 1 - fh - extra, mw, fh + extra);
        r.fillRect(footer.x(), footer.y(), footer.w(), 1, Theme.DIVIDER);
        int sty = footer.y() + 6;
        for (String l : statusLines) { r.drawText(l, footer.x() + Pages.PAD, sty, Theme.ACCENT); sty += lh + 2; }

        // ---- content
        Rect vp = new Rect(mx0, header.bottom() + 1, mw, footer.y() - header.bottom() - 1);
        viewportH = vp.h();
        int idx = slot();
        scrollCur[idx] += (scrollTarget[idx] - scrollCur[idx]) * (1f - (float) Math.exp(-k.dt * 20f));
        if (Math.abs(scrollTarget[idx] - scrollCur[idx]) < 0.5f) scrollCur[idx] = scrollTarget[idx];
        k.pushClip(vp);
        int contentH = query.isEmpty() ? pages.render(nav.selected(), vp, Math.round(scrollCur[idx])) : pages.search(query, vp, Math.round(scrollCur[idx]));
        k.popClip();
        maxScroll = Math.max(0, contentH - vp.h());
        scrollTarget[idx] = Math.max(0, Math.min(maxScroll, scrollTarget[idx]));
        scrollCur[idx] = Math.max(0, Math.min(maxScroll, scrollCur[idx]));
        if (maxScroll > 0) {
            Rect track = new Rect(vp.right() - 5, vp.y() + 3, 2, vp.h() - 6);
            int thumbH = Math.max(14, track.h() * vp.h() / contentH);
            int thumbY = track.y() + Math.round((track.h() - thumbH) * (scrollCur[idx] / maxScroll));
            boolean hov = k.hover(new Rect(track.x() - 4, track.y(), 10, track.h()));
            r.fillRect(track.x(), thumbY, track.w(), thumbH, hov ? 0xFF7A808C : 0xFF454952);
            final int th = thumbH;
            k.addHit("scroll:thumb", new Rect(track.x() - 4, track.y(), 10, track.h()), true, null, null, (x, y) -> {
                double f = (y - track.y() - th / 2.0) / Math.max(1, track.h() - th);
                scrollTarget[idx] = (float) (Math.max(0, Math.min(1, f)) * maxScroll);
                scrollCur[idx] = scrollTarget[idx];
            });
        }

        // ---- footer controls
        int bh = lh + 9, by = footer.y() + extra + (fh - bh) / 2 + 1, bx = footer.right() - Pages.PAD;
        String[] labels = {"Done", "Apply", "Undo"};
        String[] ids = {"btn:close", "btn:apply", "btn:undo"};
        for (int i = 0; i < 3; i++) {
            int bw = Math.max(48, r.textWidth(labels[i]) + 20);
            bx -= bw;
            boolean en = i == 0 || options.hasPending();
            Rect rc = new Rect(bx, by, bw, bh);
            switch (i) {
                case 0: k.button(ids[i], rc, labels[i], true, false, (x, y, b) -> env.closeAction.run(),
                        List.of("Done", "Closes the screen. Unsaved changes are discarded.")); break;
                case 1: k.button(ids[i], rc, labels[i], en, true, (x, y, b) -> apply(),
                        List.of("Apply", "Saves all pending changes to the config file.")); break;
                default: k.button(ids[i], rc, labels[i], en, false, (x, y, b) -> options.undo(),
                        List.of("Undo", "Discards all pending changes.")); break;
            }
            bx -= 4;
        }
        int leftW = bx - footer.x() - Pages.PAD;
        String msg;
        int mc;
        if (options.hasPending()) {
            int n = options.pendingCount();
            msg = n + " unsaved change" + (n == 1 ? "" : "s");
            mc = Theme.VIOLET;
        } else { msg = "No pending changes"; mc = Theme.TEXT_NOTE; }
        r.drawText(k.ellipsize(msg, leftW), footer.x() + Pages.PAD, by + (bh - lh) / 2 + 1, mc);

        drawTooltip(sw, sh);
    }

    private void drawTooltip(int sw, int sh) {
        UiRenderer r = k.r;
        List<String> tip = null;
        for (int i = k.hits.size() - 1; i >= 0; i--) {
            UiKit.Hit h = k.hits.get(i);
            if (h.rect.contains(k.mx, k.my)) { tip = h.tooltip; if (tip != null) break; }
        }
        if (tip == null || dragging != null) return;
        int lh = r.lineHeight(), maxW = Math.min(210, sw / 2);
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < tip.size(); i++) {
            for (String l : k.wrap(tip.get(i), maxW)) { lines.add(l); colors.add(i == 0 ? Theme.TEXT : i == 1 ? Theme.TEXT_DIM : Theme.TEXT_NOTE); }
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, r.textWidth(l));
        w += 16;
        int h = lines.size() * (lh + 2) + 10;
        int x = k.mx + 12, y = k.my + 12;
        if (x + w > sw - 4) x = Math.max(4, k.mx - w - 8);
        if (y + h > sh - 4) y = Math.max(4, sh - 4 - h);
        k.rect(new Rect(x, y, w, h), 0xF8090A0C, Theme.BORDER);
        int ty = y + 5;
        for (int i = 0; i < lines.size(); i++) { r.drawText(lines.get(i), x + 8, ty, colors.get(i)); ty += lh + 2; }
    }

    // ---------------------------------------------------------------- actions and input

    private void setStatus(String s) {
        status = s;
        statusUntil = lastNow + 5_000_000_000L;
    }

    private void apply() {
        if (!options.hasPending()) return;
        int n = options.pendingCount();
        try {
            options.apply(env.store);
            setStatus("Saved " + n + " change" + (n == 1 ? "" : "s") + ".");
        } catch (IOException e) {
            setStatus("Could not save: " + e.getMessage());
        }
        pages.invalidateBackups();
    }

    public boolean mouseClicked(double x, double y, int button) {
        searchFocused = false;
        for (int i = k.hits.size() - 1; i >= 0; i--) {
            UiKit.Hit h = k.hits.get(i);
            if (!h.rect.contains(x, y)) continue;
            if (!h.enabled) return true;
            if (h.drag != null) { dragging = h; h.drag.drag(x, y); return true; }
            if (h.click != null) { h.click.run(x, y, button); return true; }
            return true;
        }
        return panel.contains(x, y);
    }

    public boolean mouseReleased(double x, double y, int button) {
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (dragging == null) return false;
        dragging.drag.drag(x, y);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (!panel.contains(x, y)) return false;
        scrollBy((float) (-amount * 18));
        return true;
    }

    private void scrollBy(float dy) {
        int idx = slot();
        scrollTarget[idx] = Math.max(0, Math.min(maxScroll, scrollTarget[idx] + dy));
    }

    private void resetSearchScroll() {
        int i = Section.values().length;
        scrollTarget[i] = 0;
        scrollCur[i] = 0;
    }

    /** Text input for the search box (GLFW char callback / CharacterEvent code point). */
    public boolean charTyped(int codePoint) {
        if (!searchFocused) return false;
        if (codePoint >= 32 && codePoint != 127 && query.length() < 40) {
            query += new String(Character.toChars(codePoint));
            resetSearchScroll();
        }
        return true;
    }

    public boolean keyPressed(int key, int modifiers) {
        if ((modifiers & (Keys.MOD_CONTROL | Keys.MOD_SUPER)) != 0 && key == Keys.F) { searchFocused = true; return true; }
        switch (key) {
            case Keys.ESCAPE:
                if (searchFocused || !query.isEmpty()) { query = ""; searchFocused = false; return true; }
                env.closeAction.run();
                return true;
            case Keys.RIGHT_SHIFT:
                if (searchFocused) return true; // Shift is held while typing capital letters
                env.closeAction.run();
                return true;
            case Keys.BACKSPACE:
                if (searchFocused && !query.isEmpty()) {
                    query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
                    resetSearchScroll();
                }
                return searchFocused;
            case Keys.UP:
            case Keys.DOWN:
                if (searchFocused) return true;
                query = "";
                dragging = null;
                if (key == Keys.UP) nav.previous(); else nav.next();
                return true;
            case Keys.PAGE_UP: scrollBy(-viewportH * 0.9f); return true;
            case Keys.PAGE_DOWN: scrollBy(viewportH * 0.9f); return true;
            case Keys.ENTER:
                if (searchFocused) { searchFocused = false; return true; }
                apply();
                return true;
            default: return false;
        }
    }
}
