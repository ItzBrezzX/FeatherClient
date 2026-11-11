package dev.kingbrezz.featherfps.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A fixed-length frame-time recording used to compare settings, mods or profiles with real numbers.
 * It measures; it never changes anything. Single render thread by contract.
 * Frame times above {@link FrameStats#MAX_GAP_NS} are treated as pauses and ignored.
 */
public final class Benchmark {
    public static final int MAX_FRAMES = 200_000;
    public static final int MIN_FRAMES = 30;
    public static final int MIN_COMPARABLE_FRAMES = 100;
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 120;
    public static final int HISTORY = 5;
    /** Differences smaller than this are treated as normal run-to-run noise. */
    public static final double AVG_NOISE_PCT = 3.0;
    public static final double LOW_NOISE_PCT = 5.0;

    /** NaN means "not enough frames to compute this" (1% low needs 100 frames, 0.1% low needs 1000). */
    public record Result(String label, double seconds, int frames, double avgFps, double onePercentLowFps,
                         double pointOnePercentLowFps, double medianMs, double p99Ms, double worstMs,
                         double stdDevMs, int hitches, boolean truncated) {}

    public enum Verdict { B_FASTER, B_SLOWER, MIXED, NO_CLEAR_DIFFERENCE, NOT_COMPARABLE }

    public record Comparison(Result a, Result b, double avgFpsDeltaPct, double lowDeltaPct, Verdict verdict, String summary) {}

    private long[] durations;
    private int count;
    private long startNs;
    private long durationNs;
    private long lastStamp = -1;
    private boolean running;
    private boolean truncated;
    private int runCounter;
    private String message = "";
    private final List<Result> history = new ArrayList<>();

    public boolean isRunning() { return running; }

    public int framesRecorded() { return count; }

    public int runCounter() { return runCounter; }

    public String message() { return message; }

    public List<Result> history() { return List.copyOf(history); }

    public void clearHistory() { history.clear(); message = ""; }

    /** Starts a run. Returns false if one is already running. */
    public boolean start(long nowNs, int seconds) {
        if (running) return false;
        if (durations == null) durations = new long[MAX_FRAMES];
        count = 0;
        lastStamp = -1;
        truncated = false;
        startNs = nowNs;
        durationNs = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds)) * 1_000_000_000L;
        message = "";
        running = true;
        return true;
    }

    public void cancel() {
        if (!running) return;
        running = false;
        message = "Benchmark cancelled.";
    }

    /** 0..1 while running. */
    public double progress(long nowNs) {
        if (!running) return 0;
        return Math.max(0, Math.min(1, (nowNs - startNs) / (double) durationNs));
    }

    public double elapsedSeconds(long nowNs) { return running ? Math.max(0, (nowNs - startNs) / 1e9) : 0; }

    public double totalSeconds() { return durationNs / 1e9; }

    /** True when a run has been going for over 2 s without receiving a single frame. */
    public boolean noFramesWarning(long nowNs) { return running && count == 0 && nowNs - startNs > 2_000_000_000L; }

    public void onFrame(long nowNs) {
        if (!running) return;
        if (lastStamp >= 0) {
            long dt = nowNs - lastStamp;
            if (dt > 0 && dt <= FrameStats.MAX_GAP_NS) {
                if (count < MAX_FRAMES) durations[count++] = dt;
                else truncated = true;
            }
        }
        lastStamp = nowNs;
        if (nowNs - startNs >= durationNs) finish();
    }

    /** Lets a run end on time even if frames stop arriving. */
    public void poll(long nowNs) {
        if (running && nowNs - startNs >= durationNs) finish();
    }

    private void finish() {
        running = false;
        if (count < MIN_FRAMES) {
            message = "Not enough frames (" + count + ") to make a result. Frames are sampled while the in-game HUD is drawn.";
            return;
        }
        history.add(analyze("Run " + (++runCounter), durations, count, truncated));
        while (history.size() > HISTORY) history.remove(0);
        message = "";
    }

    static Result analyze(String label, long[] d, int n, boolean truncated) {
        long[] s = Arrays.copyOf(d, n);
        Arrays.sort(s);
        long sum = 0;
        for (long v : s) sum += v;
        double avgNs = sum / (double) n;
        double var = 0;
        for (long v : s) var += (v - avgNs) * (v - avgNs);
        double median = (n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2.0) / 1e6;
        double p99 = s[(int) Math.min(n - 1, Math.ceil(n * 0.99) - 1)] / 1e6;
        long hitchThreshold = Math.max((long) (FrameStats.HITCH_FACTOR * avgNs), FrameStats.HITCH_MIN_NS);
        int hitches = 0;
        for (long v : s) if (v >= hitchThreshold) hitches++;
        return new Result(label, sum / 1e9, n, 1e9 / avgNs,
                n >= 100 ? lowFps(s, n, 100) : Double.NaN,
                n >= 1000 ? lowFps(s, n, 1000) : Double.NaN,
                median, p99, s[n - 1] / 1e6, Math.sqrt(var / n) / 1e6, hitches, truncated);
    }

    private static double lowFps(long[] sortedAsc, int n, int per) {
        int k = (n + per - 1) / per;
        long worst = 0;
        for (int i = n - k; i < n; i++) worst += sortedAsc[i];
        return k * 1e9 / worst;
    }

    /** Compares run {@code b} against baseline {@code a}. Differences inside the noise margins are reported as such. */
    public static Comparison compare(Result a, Result b) {
        if (a.frames() < MIN_COMPARABLE_FRAMES || b.frames() < MIN_COMPARABLE_FRAMES) {
            return new Comparison(a, b, Double.NaN, Double.NaN, Verdict.NOT_COMPARABLE,
                    "Both runs need at least " + MIN_COMPARABLE_FRAMES + " frames to be compared.");
        }
        double avgD = (b.avgFps() - a.avgFps()) / a.avgFps() * 100;
        double lowD = Double.isNaN(a.onePercentLowFps()) || Double.isNaN(b.onePercentLowFps())
                ? Double.NaN : (b.onePercentLowFps() - a.onePercentLowFps()) / a.onePercentLowFps() * 100;
        boolean avgUp = avgD >= AVG_NOISE_PCT, avgDown = avgD <= -AVG_NOISE_PCT;
        boolean lowKnown = !Double.isNaN(lowD);
        boolean lowUp = lowKnown && lowD >= LOW_NOISE_PCT, lowDown = lowKnown && lowD <= -LOW_NOISE_PCT;
        Verdict v;
        String tail;
        if ((avgUp && lowDown) || (avgDown && lowUp)) { v = Verdict.MIXED; tail = "Mixed: average FPS and 1% lows moved in opposite directions."; }
        else if (avgUp || (lowUp && !avgDown)) { v = Verdict.B_FASTER; tail = b.label() + " is faster."; }
        else if (avgDown || (lowDown && !avgUp)) { v = Verdict.B_SLOWER; tail = b.label() + " is slower."; }
        else { v = Verdict.NO_CLEAR_DIFFERENCE; tail = "No clear difference (within normal run-to-run noise)."; }
        String summary = String.format(Locale.ROOT, "%s vs %s: average FPS %+.1f%%", b.label(), a.label(), avgD)
                + (lowKnown ? String.format(Locale.ROOT, ", 1%% low %+.1f%%", lowD) : "") + ". " + tail;
        return new Comparison(a, b, avgD, lowD, v, summary);
    }
}
