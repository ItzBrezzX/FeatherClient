package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.Benchmark;
import dev.kingbrezz.featherfps.core.CompatDetector;
import dev.kingbrezz.featherfps.core.FrameStats;
import dev.kingbrezz.featherfps.core.Info;
import dev.kingbrezz.featherfps.core.LoadResult;
import dev.kingbrezz.featherfps.core.PerformanceMonitor;
import dev.kingbrezz.featherfps.core.Profile;
import dev.kingbrezz.featherfps.core.Section;
import dev.kingbrezz.featherfps.core.Setting;
import dev.kingbrezz.featherfps.core.Settings;
import dev.kingbrezz.featherfps.core.SystemMonitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Content of each screen section. Pages are plain lists: group headings, rows separated by hairlines, flat controls.
 * Every page returns its total content height so the view can scroll it.
 */
final class Pages {
    static final int PAD = 12;

    private final UiKit k;
    private final ViewEnvironment env;
    private final OptionsModel opt;
    private final Consumer<String> status;
    private double[] series = new double[0];
    private PerformanceMonitor.Reading seriesFor;
    private GraphMode graphMode = GraphMode.FPS;
    private GraphMode seriesMode;
    private int benchSeconds = 30;
    private long backupsCheckedAt = Long.MIN_VALUE;
    private int backupCount;

    private enum GraphMode { FPS, FRAME_TIME }

    Pages(UiKit k, ViewEnvironment env, OptionsModel opt, Consumer<String> status) {
        this.k = k; this.env = env; this.opt = opt; this.status = status;
    }

    int render(Section s, Rect vp, int scroll) {
        switch (s) {
            case DASHBOARD: return dashboard(vp, scroll);
            case PROFILES: return profiles(vp, scroll);
            case COMPATIBILITY: return compatibility(vp, scroll);
            case ABOUT: return about(vp, scroll);
            default: return settings(s, vp, scroll);
        }
    }

    // ---------------------------------------------------------------- helpers

    private int lh() { return k.r.lineHeight(); }

    private int contentW(Rect vp) { return vp.w() - PAD * 2 - 4; }

    private static String f0(double v) { return String.format(Locale.ROOT, "%.0f", v); }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    static String bytes(long b) {
        if (b >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", b / (double) (1L << 30));
        return String.format(Locale.ROOT, "%d MB", b >> 20);
    }

    private String pick(int width, String longText, String shortText) {
        return k.r.textWidth(longText) <= width ? longText : shortText;
    }

    /** Wrapped dim paragraph; returns the y below it. */
    private int paragraph(String text, int x, int y, int w, int color) {
        for (String l : k.wrap(text, w)) { k.text(l, x, y, color); y += lh() + 2; }
        return y;
    }

    /** One label / value / note line with a hairline below. Returns its height. */
    private int metric(int x, int y, int w, int labelW, String label, String value, int valueColor, String noteLong, String noteShort) {
        int h = lh() + 8;
        k.text(label, x, y + 4, Theme.TEXT_DIM);
        int vx = x + labelW;
        k.text(value, vx, y + 4, valueColor);
        int nx = vx + Math.max(46, k.r.textWidth(value) + 12), avail = x + w - nx;
        if (noteLong != null && !noteLong.isEmpty() && avail > 24) {
            String n = k.r.textWidth(noteLong) <= avail ? noteLong : k.ellipsize(noteShort != null ? noteShort : noteLong, avail);
            k.text(n, nx, y + 4, Theme.TEXT_NOTE);
        }
        k.divider(x, y + h - 1, w);
        return h;
    }

    private int labelWidth(String... labels) {
        int w = 0;
        for (String l : labels) w = Math.max(w, k.r.textWidth(l));
        return w + 14;
    }

    private String deferredBy(String key) {
        for (CompatDetector.KnownMod m : env.compat.detected()) if (m.overlappingSettings().contains(key)) return m.displayName();
        return null;
    }

    private boolean settingEnabled(Setting s) {
        return (s.wiring == Setting.Wiring.CORE || env.activeLoaderKeys.contains(s.key)) && deferredBy(s.key) == null;
    }

    /** {active, total} for the optimization settings that need game integration. */
    private int[] optimizationCounts() {
        int total = 0, active = 0;
        for (Setting s : Settings.ALL) {
            if (s.wiring != Setting.Wiring.LOADER || s.section == Section.PERFORMANCE) continue;
            total++;
            if (env.activeLoaderKeys.contains(s.key) && deferredBy(s.key) == null) active++;
        }
        return new int[]{active, total};
    }

    // ---------------------------------------------------------------- dashboard

    private int memoryBlock(int x, int y, int w, PerformanceMonitor.Reading rd) {
        SystemMonitor.MemorySnapshot m = rd.memory();
        boolean unlimited = m.maxBytes() <= 0 || m.maxBytes() == Long.MAX_VALUE;
        double pct = unlimited ? 0 : m.usedPercentOfMax();
        int color = rd.pressure() == SystemMonitor.Pressure.HIGH ? Theme.BAD
                : rd.pressure() == SystemMonitor.Pressure.ELEVATED ? Theme.WARN : Theme.ACCENT;
        int lh = lh(), y0 = y;
        k.text("Memory", x, y + 4, Theme.TEXT_DIM);
        k.textRight(bytes(m.usedBytes()) + " / " + (unlimited ? "no limit" : bytes(m.maxBytes())), x + w, y + 4, Theme.TEXT);
        y += lh + 9;
        k.r.fillRect(x, y, w, 3, Theme.TRACK);
        k.r.fillRect(x, y, (int) Math.round(w * Math.min(1.0, pct / 100.0)), 3, color);
        y += 3 + 5;
        k.text(k.ellipsize("allocated " + bytes(m.allocatedBytes()) + "  -  " + f0(pct) + "% of max", w), x, y, Theme.TEXT_NOTE);
        y += lh + 5;
        k.divider(x, y - 1, w);
        return y - y0;
    }

    private int dashboard(Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll;
        PerformanceMonitor.Reading rd = env.monitor.read(k.now);
        FrameStats.Snapshot fs = rd.frames();
        boolean has = fs.samples() > 0;
        boolean two = cw >= 400;
        int gap = 18, colW = two ? (cw - gap) / 2 : cw, rx = two ? x + colW + gap : x;
        int secs = env.config.getInt("monitor.historySeconds");

        // left: frame rate
        int yl = top + k.groupHeader("Frame rate", x, top, colW, 0);
        int lw = labelWidth("FPS", "Average", "1% low", "0.1% low", "Frame time", "Stutters"), nw = colW - lw - 46;
        yl += metric(x, yl, colW, lw, "FPS", has ? f0(fs.fps()) : "--", has ? Theme.fpsColor(fs.fps()) : Theme.TEXT_DIM, "last second", null);
        yl += metric(x, yl, colW, lw, "Average", has ? f0(fs.avgFps()) : "--", has ? Theme.fpsColor(fs.avgFps()) : Theme.TEXT_DIM, "last " + secs + " s", null);
        OptionalDouble low = fs.onePercentLowFps();
        yl += metric(x, yl, colW, lw, "1% low", low.isPresent() ? f0(low.getAsDouble()) : "--",
                low.isPresent() ? Theme.fpsColor(low.getAsDouble()) : Theme.TEXT_DIM,
                low.isPresent() ? pick(nw, "worst 1% of frames", "worst 1%") : pick(nw, "needs 100 frames (" + fs.samples() + ")", "need 100 frames"), null);
        OptionalDouble low01 = fs.pointOnePercentLowFps();
        yl += metric(x, yl, colW, lw, "0.1% low", low01.isPresent() ? f0(low01.getAsDouble()) : "--",
                low01.isPresent() ? Theme.fpsColor(low01.getAsDouble()) : Theme.TEXT_DIM,
                low01.isPresent() ? pick(nw, "worst 0.1% of frames", "worst 0.1%") : pick(nw, "needs 1000 frames (" + fs.samples() + ")", "need 1000 frames"), null);
        yl += metric(x, yl, colW, lw, "Frame time", has ? f1(fs.lastFrameMs()) + " ms" : "--", Theme.TEXT,
                has ? "avg " + f1(fs.avgFrameMs()) + " ms" : "no data yet", null);
        yl += metric(x, yl, colW, lw, "Stutters", has ? Integer.toString(fs.hitches()) : "--",
                !has ? Theme.TEXT_DIM : fs.hitches() == 0 ? Theme.GOOD : fs.hitches() < 5 ? Theme.WARN : Theme.BAD,
                has ? pick(nw, "worst frame " + f1(fs.worstFrameMs()) + " ms", f0(fs.worstFrameMs()) + " ms worst") : "no data yet", null);

        // right (or below): system
        int yr = two ? top : yl + 10;
        yr += k.groupHeader("System", rx, yr, colW, 0);
        yr += memoryBlock(rx, yr, colW, rd);
        int lw2 = labelWidth("CPU", "Profile", "Game", "Optimizations"), nw2 = colW - lw2 - 46;
        OptionalDouble cpu = rd.cpuLoad();
        yr += metric(rx, yr, colW, lw2, "CPU", cpu.isPresent() ? f0(cpu.getAsDouble() * 100) + "%" : "N/A",
                cpu.isPresent() ? Theme.TEXT : Theme.TEXT_DIM, cpu.isPresent() ? pick(nw2, "this game, all cores", "this game") : "not available", null);
        yr += metric(rx, yr, colW, lw2, "Profile", opt.value("profile"), Theme.VIOLET, opt.isPending("profile") ? "unsaved change" : "active", null);
        yr += metric(rx, yr, colW, lw2, "Game", "Minecraft " + env.minecraftVersion + "  -  " + env.loaderName, Theme.TEXT, null, null);
        int known = env.compat.detected().size();
        int[] oc = optimizationCounts();
        yr += metric(rx, yr, colW, lw2, "Optimizations", oc[0] + "/" + oc[1] + " active", oc[0] == 0 ? Theme.TEXT_DIM : Theme.GOOD,
                known + " mod" + (known == 1 ? "" : "s") + " detected", known + " mod" + (known == 1 ? "" : "s"));

        int y = Math.max(yl, yr) + 12;
        if (rd.memoryWarning()) {
            String msg = "Heap usage has stayed above " + env.config.getInt("memory.warnPercent")
                    + "% of the maximum. This is informational only; FEATHER FPS does not change memory settings.";
            List<String> lines = k.wrap(msg, cw - 18);
            int bh = lines.size() * (lh + 2) + 8;
            k.r.fillRect(x, y, cw, bh, 0xFF1F1B10);
            k.r.fillRect(x, y, 2, bh, Theme.WARN);
            int ty = y + 4;
            for (String l : lines) { k.text(l, x + 10, ty, Theme.WARN); ty += lh + 2; }
            y += bh + 12;
        }

        // history
        String[] tabLabels = {"FPS", "Frame time"};
        String[] tabIds = {"btn:graph-fps", "btn:graph-ft"};
        GraphMode[] tabModes = {GraphMode.FPS, GraphMode.FRAME_TIME};
        int tabsW = 4;
        for (String t : tabLabels) tabsW += k.r.textWidth(t) + 16;
        int hy = y;
        y += k.groupHeader("History (last " + secs + " s)", x, y, cw, tabsW + 6);
        int tx = x + cw - tabsW;
        for (int i = 0; i < 2; i++) {
            int tw = k.r.textWidth(tabLabels[i]) + 16;
            final GraphMode m = tabModes[i];
            k.tab(tabIds[i], new Rect(tx, hy - 3, tw, lh + 6), tabLabels[i], graphMode == m, (mx, my, bt) -> graphMode = m,
                    List.of(i == 0 ? "FPS graph" : "Frame-time graph",
                            i == 0 ? "Frames per second for each slice of the history window."
                                    : "The slowest frame in each slice, in milliseconds. Spikes here are stutters even when average FPS looks fine."));
            tx += tw + 4;
        }
        Rect gr = new Rect(x, y, cw, Math.max(84, lh * 9));
        k.box(gr);
        Rect plot = new Rect(gr.x() + 8, gr.y() + 8, gr.w() - 16, gr.h() - 16);
        int points = Math.max(10, Math.min(120, plot.w() / 2));
        if (series.length != points) { series = new double[points]; seriesFor = null; }
        if (seriesFor != rd || seriesMode != graphMode) {
            if (graphMode == GraphMode.FPS) env.monitor.frames().fillFpsSeries(series, k.now);
            else env.monitor.frames().fillFrameTimeSeries(series, k.now);
            seriesFor = rd;
            seriesMode = graphMode;
        }
        double max = 0;
        boolean any = false;
        for (double v : series) if (!Double.isNaN(v)) { any = true; max = Math.max(max, v); }
        if (!has || !any) {
            List<String> lines = k.wrap(env.frameSourceNote, plot.w() - 12);
            int ty = plot.y() + Math.max(0, (plot.h() - lines.size() * (lh + 2)) / 2);
            for (String l : lines) { k.textCentered(l, new Rect(plot.x(), ty, plot.w(), lh), Theme.TEXT_NOTE); ty += lh + 2; }
        } else {
            boolean fpsMode = graphMode == GraphMode.FPS;
            double scale = fpsMode ? Math.max(60, max * 1.25) : Math.max(33.4, max * 1.25);
            if (!fpsMode && scale >= 20) k.r.fillRect(plot.x(), plot.bottom() - (int) Math.round(plot.h() * 16.7 / scale), plot.w(), 1, Theme.TRACK);
            for (int i = 0; i < points; i++) {
                double v = series[i];
                if (Double.isNaN(v) || (!fpsMode && v <= 0)) continue;
                int bh = Math.max(1, (int) Math.round(plot.h() * Math.min(1.0, v / scale)));
                int bx = plot.x() + i * plot.w() / points, bw = Math.max(1, plot.w() / points - 1);
                int color = fpsMode ? Theme.lerp(Theme.ACCENT_DIM, Theme.ACCENT, (float) (v / scale))
                        : v <= 16.7 ? Theme.ACCENT : v <= 33.4 ? Theme.WARN : Theme.BAD;
                k.r.fillRect(bx, plot.bottom() - bh, bw, bh, color);
            }
            k.r.fillRect(plot.x(), plot.bottom(), plot.w(), 1, Theme.TRACK);
            k.textRight(f0(scale) + (fpsMode ? " fps" : " ms"), plot.right(), plot.y(), Theme.TEXT_NOTE);
        }
        return gr.bottom() + PAD - top + PAD;
    }

    // ---------------------------------------------------------------- benchmark

    private int benchmarkBlock(int x, int y, int cw) {
        Benchmark b = env.monitor.benchmark();
        b.poll(k.now);
        int lh = lh(), bh = lh + 9;
        boolean running = b.isRunning();
        List<Benchmark.Result> hist = b.history();
        y += k.groupHeader("Benchmark", x, y, cw, 0);
        y = paragraph("Records frame times for a fixed time so you can compare settings, other mods or Sodium with real numbers. "
                + "Stay in the same scene for every run. Frames are sampled while the in-game HUD is drawn. The run continues if you close this screen.",
                x, y, cw, Theme.TEXT_DIM) + 4;

        String[] labels = {"Start run", benchSeconds + " s", "Cancel", "Clear results"};
        String[] ids = {"btn:bench-start", "btn:bench-len", "btn:bench-cancel", "btn:bench-clear"};
        boolean[] en = {!running, !running, running, !running && !hist.isEmpty()};
        UiKit.Action[] actions = {
            (mx, my, bt) -> { if (b.start(k.now, benchSeconds)) status.accept("Benchmark started (" + benchSeconds + " s). Stay in the same scene."); },
            (mx, my, bt) -> benchSeconds = benchSeconds == 15 ? 30 : benchSeconds == 30 ? 60 : 15,
            (mx, my, bt) -> b.cancel(),
            (mx, my, bt) -> b.clearHistory()
        };
        List<List<String>> tips = List.of(
                List.of("Start run", "Records frame times for " + benchSeconds + " seconds. Nothing is changed while it runs."),
                List.of("Run length", "Click to switch between 15, 30 and 60 seconds. Longer runs are more reliable."),
                List.of("Cancel", "Stops the current run without saving a result."),
                List.of("Clear results", "Forgets all recorded runs."));
        int bx = 0;
        for (int i = 0; i < 4; i++) {
            int bw = Math.max(40, k.r.textWidth(labels[i]) + 18);
            if (bx > 0 && bx + bw > cw) { bx = 0; y += bh + 4; }
            k.button(ids[i], new Rect(x + bx, y, bw, bh), labels[i], en[i], i == 0, actions[i], tips.get(i));
            bx += bw + 4;
        }
        y += bh + 8;

        if (running) {
            k.text("Running: " + f0(b.elapsedSeconds(k.now)) + " / " + f0(b.totalSeconds()) + " s, " + b.framesRecorded() + " frames", x, y, Theme.ACCENT);
            y += lh + 4;
            k.r.fillRect(x, y, cw, 3, Theme.TRACK);
            k.r.fillRect(x, y, (int) Math.round(cw * b.progress(k.now)), 3, Theme.ACCENT);
            y += 3 + 8;
        }
        int shown = Math.min(3, hist.size());
        if (shown > 0) {
            String[] cols = {"Run", "Avg FPS", "1% low", "Stutters", "Worst"};
            int colW = cw / 5;
            for (int c = 0; c < 5; c++) k.text(cols[c], x + c * colW, y + 2, Theme.TEXT_NOTE);
            y += lh + 5;
            k.divider(x, y - 1, cw);
            for (int i = 0; i < shown; i++) {
                Benchmark.Result r = hist.get(hist.size() - 1 - i);
                int color = i == 0 ? Theme.ACCENT : Theme.TEXT;
                String[] vals = {r.label(), f0(r.avgFps()), Double.isNaN(r.onePercentLowFps()) ? "--" : f0(r.onePercentLowFps()),
                        Integer.toString(r.hitches()), f1(r.worstMs()) + " ms"};
                for (int c = 0; c < 5; c++) k.text(vals[c], x + c * colW, y + 4, color);
                y += lh + 8;
                k.divider(x, y - 1, cw);
            }
            y += 6;
        }
        if (hist.size() >= 2) {
            Benchmark.Comparison cmp = Benchmark.compare(hist.get(hist.size() - 2), hist.get(hist.size() - 1));
            int color = cmp.verdict() == Benchmark.Verdict.B_FASTER ? Theme.GOOD : cmp.verdict() == Benchmark.Verdict.B_SLOWER ? Theme.BAD
                    : cmp.verdict() == Benchmark.Verdict.MIXED ? Theme.WARN : Theme.TEXT_DIM;
            y = paragraph(cmp.summary(), x, y, cw, color) + 2;
        }
        String msg = running ? (b.noFramesWarning(k.now) ? "No frames received yet. The benchmark needs the in-game HUD frame sampler." : "") : b.message();
        if (!msg.isEmpty()) y = paragraph(msg, x, y, cw, Theme.WARN);
        return y + 4;
    }

    // ---------------------------------------------------------------- settings sections

    private int settings(Section sec, Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll;
        int y = paragraph(UiText.intro(sec), x, top, cw, Theme.TEXT_DIM) + 8;
        if (sec == Section.MEMORY) {
            y += k.groupHeader("Java heap", x, y, cw, 0);
            y += memoryBlock(x, y, cw, env.monitor.read(k.now)) + 12;
            y += k.groupHeader("Warnings", x, y, cw, 0);
        }
        if (sec == Section.PERFORMANCE) {
            y = benchmarkBlock(x, y, cw) + 6;
            y += k.groupHeader("Monitoring", x, y, cw, 0);
        }
        List<Setting> list = Settings.forSection(sec);
        if (list.isEmpty()) {
            List<String> lines = k.wrap(UiText.emptyNote(sec), cw - 14);
            int bh = lines.size() * (lh + 2) + 6;
            k.r.fillRect(x, y, 2, bh, Theme.ACCENT_DIM);
            int ty = y + 3;
            for (String l : lines) { k.text(l, x + 10, ty, Theme.TEXT_DIM); ty += lh + 2; }
            y += bh + 8;
        }
        for (Setting s : list) y = row(s, x, y, cw);
        if (!list.isEmpty()) {
            y += 10;
            int diff = 0;
            for (Setting s : list) if (settingEnabled(s) && !opt.value(s.key).equals(s.defaultValue)) diff++;
            String label = "Reset this page";
            k.button("btn:reset-page", new Rect(x, y, k.r.textWidth(label) + 18, lh + 9), label, diff > 0, false, (mx, my, bt) -> {
                int c = 0;
                for (Setting s : list) {
                    if (settingEnabled(s) && !opt.value(s.key).equals(s.defaultValue)) { opt.stage(s.key, s.defaultValue); c++; }
                }
                status.accept("Staged defaults for " + c + " setting" + (c == 1 ? "" : "s") + ". Press Apply to save.");
            }, List.of("Reset this page", "Stages the default value for every available setting on this page. Nothing changes until you press Apply."));
            y += lh + 9 + 8;
        }
        if (sec == Section.ADVANCED) {
            k.text(k.ellipsize("Config file: " + env.store.file(), cw), x, y, Theme.TEXT_NOTE);
            y += lh + 6;
        }
        return y - top + PAD * 2;
    }

    static int step(Setting s) {
        int range = s.max - s.min;
        return range <= 50 ? 1 : range <= 300 ? 5 : range <= 1000 ? 10 : 100;
    }

    private void stageInt(Setting s, int v) { opt.stage(s.key, Integer.toString(Math.max(s.min, Math.min(s.max, v)))); }

    private int row(Setting s, int x, int y, int cw) {
        int lh = lh(), rh = lh * 2 + 10;
        Rect rc = new Rect(x, y, cw, rh);
        boolean active = s.wiring == Setting.Wiring.CORE || env.activeLoaderKeys.contains(s.key);
        String by = deferredBy(s.key);
        boolean enabled = active && by == null, pending = opt.isPending(s.key);
        float h = k.smooth("row:" + s.key, k.hover(rc) ? 1f : 0f);
        if (h > 0.01f) k.r.fillRect(rc.x() - 4, rc.y(), rc.w() + 4, rc.h() - 1, Theme.withAlpha(Theme.ROW_HOVER, Math.round(255 * h)));
        k.divider(rc.x(), rc.bottom() - 1, rc.w());
        if (pending) k.r.fillRect(rc.x() - 7, rc.y() + 4, 2, rh - 9, Theme.VIOLET);

        List<String> tip = new ArrayList<>();
        tip.add(UiText.label(s.key));
        tip.add(s.description);
        tip.add("Allowed: " + s.describeConstraints());
        tip.add("Default: " + s.defaultValue);
        if (!active) tip.add("Inactive: this build has no code that applies this setting yet, so changing it would do nothing.");
        if (by != null) tip.add(by + " is installed and already handles this, so FEATHER FPS leaves it alone to avoid duplicate work.");
        if (pending) tip.add("Unsaved change (saved value: " + opt.committed(s.key) + "). Press Apply to keep it.");

        int right = rc.right() - 2, cy = rc.y() + (rh - 1) / 2, controlW;
        String val = opt.value(s.key);
        int valColor = !enabled ? Theme.TEXT_DISABLED : pending ? Theme.VIOLET : Theme.TEXT;
        k.addHit("row:" + s.key, rc, false, tip, null, null);
        switch (s.type) {
            case BOOL: {
                controlW = 24;
                boolean on = val.equals("true");
                k.toggle("opt:" + s.key, new Rect(right - 24, cy - 6, 24, 12), on, enabled, (mx, my, b) -> opt.stage(s.key, Boolean.toString(!on)), tip);
                break;
            }
            case INT: {
                int valW = k.r.textWidth("0000") + 4, trackW = 64, bw = 11;   // one fixed value column keeps every slider aligned
                int vx = right - valW, plusX = vx - 5 - bw, trackX = plusX - 5 - trackW, minusX = trackX - 5 - bw;
                controlW = right - minusX;
                k.textRight(val, right, cy - lh / 2 + 1, valColor);
                int cur = Integer.parseInt(val), st = step(s);
                k.button("opt:" + s.key + ":minus", new Rect(minusX, cy - 5, bw, 11), "-", enabled && cur > s.min, false, (mx, my, b) -> stageInt(s, cur - st), tip);
                k.button("opt:" + s.key + ":plus", new Rect(plusX, cy - 5, bw, 11), "+", enabled && cur < s.max, false, (mx, my, b) -> stageInt(s, cur + st), tip);
                Rect track = new Rect(trackX, cy, trackW, 2);
                double frac = (cur - s.min) / (double) Math.max(1, s.max - s.min);
                k.r.fillRect(track.x(), track.y(), track.w(), 2, Theme.TRACK);
                k.r.fillRect(track.x(), track.y(), (int) Math.round(track.w() * frac), 2, enabled ? Theme.ACCENT : 0xFF2F4F58);
                int kx = track.x() + (int) Math.round((track.w() - 4) * frac);
                boolean hov = enabled && k.hover(new Rect(trackX - 2, rc.y(), trackW + 4, rh));
                k.r.fillRect(kx, cy - 4, 4, 10, enabled ? (hov ? 0xFFFFFFFF : Theme.TEXT) : 0xFF6A6F78);
                k.addHit("opt:" + s.key, new Rect(trackX - 2, rc.y(), trackW + 4, rh), enabled, tip, null, (mx, my) -> {
                    double fr = Math.max(0, Math.min(1, (mx - track.x()) / (double) track.w()));
                    int raw = s.min + (int) Math.round(fr * (s.max - s.min));
                    stageInt(s, (int) Math.round(raw / (double) st) * st);
                });
                break;
            }
            default: {
                int bw = 0;
                for (String o : s.options) bw = Math.max(bw, k.r.textWidth(o));
                bw += 18;
                controlW = bw;
                int idx = s.options.indexOf(val), n = s.options.size();
                k.button("opt:" + s.key, new Rect(right - bw, cy - 6, bw, 13), val, enabled, false,
                        (mx, my, b) -> opt.stage(s.key, s.options.get((idx + (b == 1 ? n - 1 : 1)) % n)), tip);
            }
        }
        int textW = cw - controlW - 28;
        String label = UiText.label(s.key);
        k.text(label, rc.x(), rc.y() + 4, enabled ? Theme.TEXT : Theme.TEXT_DISABLED);
        String badge = !active ? "INACTIVE" : by != null ? "HANDLED BY " + by.toUpperCase(Locale.ROOT) : null;
        if (badge != null) k.text(badge, rc.x() + k.r.textWidth(label) + 8, rc.y() + 4, Theme.WARN);
        k.text(k.ellipsize(s.description, textW), rc.x(), rc.y() + 4 + lh + 2, enabled ? Theme.TEXT_NOTE : Theme.TEXT_DISABLED);
        return rc.bottom();
    }

    // ---------------------------------------------------------------- search

    private static boolean matches(Setting s, String[] terms) {
        String hay = (UiText.label(s.key) + " " + s.description + " " + s.key + " " + s.section.title).toLowerCase(Locale.ROOT);
        for (String t : terms) if (!hay.contains(t)) return false;
        return true;
    }

    int search(String query, Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll, y = top;
        String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        int found = 0;
        for (Section sec : Section.values()) {
            List<Setting> hits = new ArrayList<>();
            for (Setting s : Settings.forSection(sec)) if (matches(s, terms)) hits.add(s);
            if (hits.isEmpty()) continue;
            y += k.groupHeader(sec.title, x, y, cw, 0);
            for (Setting s : hits) { y = row(s, x, y, cw); found++; }
            y += 10;
        }
        if (found == 0) y = paragraph("No settings match \"" + query + "\".", x, y, cw, Theme.TEXT_DIM);
        return y - top + PAD * 2;
    }

    // ---------------------------------------------------------------- profiles

    private List<String> profileTip(Profile p) {
        List<String> tip = new ArrayList<>();
        tip.add(p.name());
        tip.add(p.description);
        if (p == Profile.CUSTOM) tip.add("Restores the custom values you last saved. Editing any profile-managed setting by hand switches to CUSTOM.");
        else if (p.overrides().isEmpty()) tip.add("Uses every default value.");
        else {
            StringBuilder sb = new StringBuilder("Sets: ");
            boolean first = true;
            for (Setting s : Settings.profileManaged()) {
                if (!p.overrides().containsKey(s.key)) continue;
                sb.append(first ? "" : ", ").append(UiText.label(s.key)).append(" = ").append(p.overrides().get(s.key));
                first = false;
            }
            tip.add(sb.toString());
        }
        return tip;
    }

    private int profiles(Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll;
        int y = paragraph(UiText.intro(Section.PROFILES), x, top, cw, Theme.TEXT_DIM) + 8;
        y += k.groupHeader("Profile", x, y, cw, 0);
        String current = opt.value("profile");
        for (Profile p : Profile.values()) {
            Rect rc = new Rect(x, y, cw, lh * 2 + 10);
            boolean active = current.equals(p.name());
            float h = k.smooth("prof:" + p.name(), k.hover(rc) ? 1f : 0f);
            if (h > 0.01f) k.r.fillRect(rc.x() - 4, rc.y(), rc.w() + 4, rc.h() - 1, Theme.withAlpha(Theme.ROW_HOVER, Math.round(255 * h)));
            k.divider(rc.x(), rc.bottom() - 1, rc.w());
            int cy = rc.y() + (rc.h() - 1) / 2;
            k.rect(new Rect(rc.x(), cy - 4, 9, 9), Theme.FIELD, active ? Theme.ACCENT : Theme.BORDER);
            if (active) k.r.fillRect(rc.x() + 3, cy - 1, 3, 3, Theme.ACCENT);
            k.text(p.name().replace('_', '-'), rc.x() + 18, rc.y() + 4, active ? Theme.TEXT : Theme.TEXT_DIM);
            if (active) k.textRight(opt.isPending("profile") ? "Selected, not saved" : "Active", rc.right() - 2, rc.y() + 4,
                    opt.isPending("profile") ? Theme.VIOLET : Theme.ACCENT);
            k.text(k.ellipsize(p.description, cw - 22), rc.x() + 18, rc.y() + 4 + lh + 2, Theme.TEXT_NOTE);
            k.addHit("profile:" + p.name(), rc, true, profileTip(p), (mx, my, b) -> opt.stageProfile(p), null);
            y = rc.bottom();
        }
        y += 14;
        y += k.groupHeader("Config", x, y, cw, 0);
        refreshBackups();
        int bx = 0, bh = lh + 9;
        String[] ids = {"reset", "export", "import", "restore"};
        String[] labels = {"Reset to defaults", "Export", "Import", "Restore latest backup"};
        List<List<String>> tips = List.of(
                List.of("Reset to defaults", "Stages every setting at its default value. Nothing changes until you press Apply."),
                List.of("Export", "Writes your saved settings to " + env.exportPath.getFileName() + " in the config folder."),
                List.of("Import", "Reads " + env.exportPath.getFileName() + " from the config folder and stages its values. Invalid values are ignored."),
                List.of("Restore latest backup", "Stages the values from the newest automatic backup. " + backupCount + " backup(s) available."));
        for (int i = 0; i < ids.length; i++) {
            int bw = k.r.textWidth(labels[i]) + 18;
            if (bx > 0 && bx + bw > cw) { bx = 0; y += bh + 4; }
            boolean en = !ids[i].equals("restore") || backupCount > 0;
            final String id = ids[i];
            k.button("btn:" + id, new Rect(x + bx, y, bw, bh), labels[i], en, false, (mx, my, b) -> profileAction(id), tips.get(i));
            bx += bw + 4;
        }
        y += bh + 8;
        y = paragraph("Export/import file: " + env.exportPath.getFileName() + " (in the config folder).", x, y, cw, Theme.TEXT_NOTE);
        return y - top + PAD * 2;
    }

    private void refreshBackups() {
        if (backupsCheckedAt != Long.MIN_VALUE && k.now - backupsCheckedAt < 2_000_000_000L) return;
        backupsCheckedAt = k.now;
        try { backupCount = env.store.listBackups().size(); } catch (IOException e) { backupCount = 0; }
    }

    /** Forces the backup count to be re-read on the next frame (called after saving). */
    void invalidateBackups() { backupsCheckedAt = Long.MIN_VALUE; }

    private void profileAction(String id) {
        try {
            switch (id) {
                case "reset":
                    opt.stageDefaults();
                    status.accept("Defaults staged. Press Apply to save them.");
                    break;
                case "export":
                    env.store.exportTo(env.config, env.exportPath);
                    status.accept("Exported saved settings to " + env.exportPath.getFileName() + ".");
                    break;
                case "import": {
                    if (!Files.exists(env.exportPath)) { status.accept("No " + env.exportPath.getFileName() + " found in the config folder."); break; }
                    stageFrom(env.store.importFrom(env.exportPath), "Imported");
                    break;
                }
                default: {
                    List<Path> b = env.store.listBackups();
                    if (b.isEmpty()) { status.accept("No backups yet."); break; }
                    stageFrom(env.store.importFrom(b.get(b.size() - 1)), "Backup loaded");
                }
            }
        } catch (IOException e) {
            status.accept("Failed: " + e.getMessage());
        }
    }

    private void stageFrom(LoadResult r, String verb) {
        opt.stageAllFrom(r.config());
        int n = opt.pendingCount();
        status.accept(verb + ": " + n + " change" + (n == 1 ? "" : "s") + " staged"
                + (r.warnings().isEmpty() ? "" : " (" + r.warnings().size() + " invalid value(s) ignored)") + ". Press Apply to save.");
    }

    // ---------------------------------------------------------------- compatibility

    private int compatibility(Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll;
        int y = paragraph(UiText.intro(Section.COMPATIBILITY), x, top, cw, Theme.TEXT_DIM) + 8;
        y += k.groupHeader("Detected", x, y, cw, 0);
        List<CompatDetector.KnownMod> found = env.compat.detected();
        if (found.isEmpty()) y = paragraph("No known optimization mods detected.", x, y, cw, Theme.TEXT_DIM) + 4;
        for (CompatDetector.KnownMod m : found) {
            List<String> extra = new ArrayList<>();
            if (!m.overlappingSettings().isEmpty()) {
                StringBuilder sb = new StringBuilder("Overlaps: ");
                boolean first = true;
                for (String key : m.overlappingSettings()) { sb.append(first ? "" : ", ").append(UiText.label(key)); first = false; }
                extra = k.wrap(sb.toString(), cw);
            }
            int rh = lh * 2 + 10 + extra.size() * (lh + 2);
            k.text(m.displayName(), x, y + 4, Theme.TEXT);
            k.textRight(extra.isEmpty() ? "no overlap" : "overlap", x + cw - 2, y + 4, extra.isEmpty() ? Theme.TEXT_NOTE : Theme.WARN);
            k.text(k.ellipsize(m.role(), cw), x, y + 4 + lh + 2, Theme.TEXT_NOTE);
            int ty = y + 4 + 2 * (lh + 2);
            for (String l : extra) { k.text(l, x, ty, Theme.WARN); ty += lh + 2; }
            y += rh;
            k.divider(x, y - 1, cw);
        }
        y += 12;
        List<String> missing = new ArrayList<>();
        for (CompatDetector.KnownMod m : CompatDetector.KNOWN) if (!found.contains(m)) missing.add(m.displayName());
        y += k.groupHeader("Not detected", x, y, cw, 0);
        y = paragraph(String.join(", ", missing) + ".", x, y, cw, Theme.TEXT_NOTE) + 6;
        y = paragraph("Detection is by mod id only. FEATHER FPS never modifies other mods; where one of them already does the same job, "
                + "the matching FEATHER FPS setting is switched off for you.", x, y, cw, Theme.TEXT_NOTE);
        return y - top + PAD * 2;
    }

    // ---------------------------------------------------------------- about

    private int about(Rect vp, int scroll) {
        int lh = lh(), x = vp.x() + PAD, cw = contentW(vp), top = vp.y() + PAD - scroll, y = top;
        k.text(Info.NAME, x, y, Theme.TEXT);
        y += lh + 5;
        y = paragraph(Info.DESCRIPTION, x, y, cw, Theme.TEXT_DIM) + 10;
        y += k.groupHeader("Details", x, y, cw, 0);
        int lw = labelWidth("Minecraft", "Loader", "Config");
        y += metric(x, y, cw, lw, "Minecraft", env.minecraftVersion, Theme.TEXT, null, null);
        y += metric(x, y, cw, lw, "Loader", env.loaderName, Theme.TEXT, null, null);
        k.text("Config", x, y + 4, Theme.TEXT_DIM);
        k.text(k.ellipsize(env.store.file().toString(), cw - lw), x + lw, y + 4, Theme.TEXT);
        y += lh + 8;
        k.divider(x, y - 1, cw);
        y += 10;
        y = paragraph("Optimizations marked INACTIVE are not applied by this build.", x, y, cw, Theme.TEXT_NOTE);
        return y - top + PAD * 2;
    }
}
