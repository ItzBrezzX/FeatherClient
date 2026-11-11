package dev.kingbrezz.featherfps.core.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Headless renderer for tests: fixed 6x9 font; records the visible (clipped) part of every fill and every text. */
final class RecordingRenderer implements UiRenderer {
    record Fill(int x, int y, int w, int h, int argb) {}

    final List<Fill> fills = new ArrayList<>();
    final List<String> texts = new ArrayList<>();
    private final ArrayDeque<Rect> clips = new ArrayDeque<>();
    int clipDepth;
    int maxClipDepth;

    void clear() { fills.clear(); texts.clear(); clips.clear(); clipDepth = 0; maxClipDepth = 0; }

    boolean hasText(String s) { return texts.stream().anyMatch(t -> t.contains(s)); }

    @Override public void fillRect(int x, int y, int w, int h, int argb) {
        Rect r = new Rect(x, y, w, h);
        Rect c = clips.peek();
        if (c != null) r = r.intersect(c);
        if (!r.isEmpty()) fills.add(new Fill(r.x(), r.y(), r.w(), r.h(), argb));
    }

    @Override public void drawText(String text, int x, int y, int argb) { texts.add(text); }
    @Override public int textWidth(String text) { return text.length() * 6; }
    @Override public int lineHeight() { return 9; }

    @Override public void pushClip(int x, int y, int w, int h) {
        Rect n = new Rect(x, y, w, h);
        Rect c = clips.peek();
        clips.push(c == null ? n : c.intersect(n));
        clipDepth++;
        maxClipDepth = Math.max(maxClipDepth, clipDepth);
    }

    @Override public void popClip() { clips.pop(); clipDepth--; }
}
