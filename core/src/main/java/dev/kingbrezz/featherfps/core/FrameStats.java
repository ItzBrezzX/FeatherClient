package dev.kingbrezz.featherfps.core;

import java.util.Arrays;
import java.util.OptionalDouble;

/**
 * Real frame-time statistics over a sliding time window, fed by one recordFrame call per rendered frame.
 * Memory is bounded: a fixed ring buffer, no allocation per frame.
 * Intervals longer than {@link #MAX_GAP_NS} (window minimized, loading screen, debugger pause) are not counted as frames.
 * Single render thread by contract; methods are synchronized only as a safety net.
 */
public final class FrameStats {
    public static final int CAPACITY = 16384;
    public static final long MAX_GAP_NS = 10_000_000_000L;
    public static final int MIN_SAMPLES_FOR_LOW = 100;
    public static final int MIN_SAMPLES_FOR_POINT_ONE_LOW = 1000;
    /** A stutter is a frame at least this many times the window average, and at least {@link #HITCH_MIN_NS}. */
    public static final double HITCH_FACTOR = 2.0;
    public static final long HITCH_MIN_NS = 20_000_000L;

    private final long[] stamps = new long[CAPACITY];
    private final long[] durations = new long[CAPACITY];
    private final long[] scratch = new long[CAPACITY];
    private int head;
    private int size;
    private long lastStamp = -1;
    private long firstStamp = -1;
    private long windowNs = 60_000_000_000L;

    /**
     * One immutable reading. The 1% and 0.1% lows are the FPS equivalent of the average of the slowest 1% / 0.1% of
     * frames; they are empty until 100 / 1000 frames are in the window.
     */
    public record Snapshot(int samples, double fps, double avgFps, double lastFrameMs, double avgFrameMs,
                           OptionalDouble onePercentLowFps, OptionalDouble pointOnePercentLowFps,
                           int hitches, double worstFrameMs) {}

    public synchronized void setWindowSeconds(int seconds) { this.windowNs = seconds * 1_000_000_000L; }

    public synchronized void reset() {
        head = 0; size = 0; lastStamp = -1; firstStamp = -1;
    }

    /** Call once per rendered frame with a monotonic clock reading (System.nanoTime()). */
    public synchronized void recordFrame(long nowNs) {
        if (firstStamp < 0) firstStamp = nowNs;
        if (lastStamp >= 0) {
            long dt = nowNs - lastStamp;
            if (dt > 0 && dt <= MAX_GAP_NS) push(nowNs, dt);
        }
        lastStamp = nowNs;
    }

    private void push(long stamp, long duration) {
        if (size == CAPACITY) { head = (head + 1) % CAPACITY; size--; }
        int idx = (head + size) % CAPACITY;
        stamps[idx] = stamp;
        durations[idx] = duration;
        size++;
    }

    private void evict(long nowNs) {
        long cutoff = nowNs - windowNs;
        while (size > 0 && stamps[head] < cutoff) { head = (head + 1) % CAPACITY; size--; }
    }

    private static OptionalDouble low(long[] sortedAsc, int n, int per) {
        int k = (n + per - 1) / per;
        long worst = 0;
        for (int i = n - k; i < n; i++) worst += sortedAsc[i];
        return OptionalDouble.of(k * 1e9 / worst);
    }

    public synchronized Snapshot snapshot(long nowNs) {
        evict(nowNs);
        if (size == 0) return new Snapshot(0, 0, 0, 0, 0, OptionalDouble.empty(), OptionalDouble.empty(), 0, 0);

        long sumAll = 0, worst = 0;
        for (int i = 0; i < size; i++) {
            long d = durations[(head + i) % CAPACITY];
            sumAll += d;
            if (d > worst) worst = d;
        }
        long hitchThreshold = Math.max((long) (HITCH_FACTOR * sumAll / size), HITCH_MIN_NS);
        int hitches = 0;
        for (int i = 0; i < size; i++) if (durations[(head + i) % CAPACITY] >= hitchThreshold) hitches++;

        int lastSecondCount = 0;
        long lastSecondSum = 0;
        for (int i = size - 1; i >= 0; i--) {
            int idx = (head + i) % CAPACITY;
            if (stamps[idx] < nowNs - 1_000_000_000L) break;
            lastSecondCount++;
            lastSecondSum += durations[idx];
        }
        double fps = lastSecondSum == 0 ? 0 : lastSecondCount * 1e9 / lastSecondSum;
        double avgFps = size * 1e9 / sumAll;
        double lastMs = durations[(head + size - 1) % CAPACITY] / 1e6;
        double avgMs = sumAll / (double) size / 1e6;

        OptionalDouble low1 = OptionalDouble.empty(), low01 = OptionalDouble.empty();
        if (size >= MIN_SAMPLES_FOR_LOW) {
            for (int i = 0; i < size; i++) scratch[i] = durations[(head + i) % CAPACITY];
            Arrays.sort(scratch, 0, size);
            low1 = low(scratch, size, 100);
            if (size >= MIN_SAMPLES_FOR_POINT_ONE_LOW) low01 = low(scratch, size, 1000);
        }
        return new Snapshot(size, fps, avgFps, lastMs, avgMs, low1, low01, hitches, worst / 1e6);
    }

    /**
     * Fills {@code out} with the FPS of each equal slice of the window, oldest first, for the history graph.
     * Slices that end before the first recorded frame are NaN (no data), not zero.
     */
    public synchronized void fillFpsSeries(double[] out, long nowNs) {
        evict(nowNs);
        bucketFill(out, nowNs, false);
    }

    /** Like {@link #fillFpsSeries} but each slice holds its slowest frame in milliseconds, which makes stutters visible. */
    public synchronized void fillFrameTimeSeries(double[] out, long nowNs) {
        evict(nowNs);
        bucketFill(out, nowNs, true);
    }

    private void bucketFill(double[] out, long nowNs, boolean maxMs) {
        int n = out.length;
        if (n == 0) return;
        long start = nowNs - windowNs;
        long bucketNs = Math.max(1, windowNs / n);
        Arrays.fill(out, 0.0);
        for (int i = 0; i < size; i++) {
            int idx = Math.min(n - 1, (int) ((stamps[(head + i) % CAPACITY] - start) / bucketNs));
            if (idx < 0) continue;
            if (maxMs) out[idx] = Math.max(out[idx], durations[(head + i) % CAPACITY] / 1e6);
            else out[idx] += 1.0;
        }
        for (int b = 0; b < n; b++) {
            long bucketEnd = start + (b + 1) * bucketNs;
            if (firstStamp < 0 || bucketEnd <= firstStamp) out[b] = Double.NaN;
            else if (!maxMs) out[b] = out[b] * 1e9 / bucketNs;
        }
    }
}
