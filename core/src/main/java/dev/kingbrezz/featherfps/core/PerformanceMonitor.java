package dev.kingbrezz.featherfps.core;

import java.util.OptionalDouble;

/**
 * Ties FrameStats and SystemMonitor to the config. Readings are cached and refreshed at most every
 * monitor.intervalMs, so opening the dashboard costs nothing per frame beyond one recordFrame call.
 */
public final class PerformanceMonitor {
    public record Reading(FrameStats.Snapshot frames, SystemMonitor.MemorySnapshot memory,
                          SystemMonitor.Pressure pressure, boolean memoryWarning, OptionalDouble cpuLoad) {}

    private final FeatherConfig config;
    private final FrameStats frames = new FrameStats();
    private final SystemMonitor system = new SystemMonitor();
    private final Benchmark benchmark = new Benchmark();
    private long lastRefreshNs;
    private Reading cached;

    public PerformanceMonitor(FeatherConfig config) { this.config = config; }

    public FrameStats frames() { return frames; }

    public Benchmark benchmark() { return benchmark; }

    /** The loader module calls this once per rendered frame. */
    public void onFrame(long nowNs) {
        frames.recordFrame(nowNs);
        benchmark.onFrame(nowNs);
    }

    public Reading read(long nowNs) {
        long interval = config.getInt("monitor.intervalMs") * 1_000_000L;
        if (cached == null || nowNs - lastRefreshNs >= interval) {
            frames.setWindowSeconds(config.getInt("monitor.historySeconds"));
            SystemMonitor.Pressure p = system.samplePressure(config.getInt("memory.warnPercent"));
            boolean warn = config.getBool("memory.warnings") && system.sustainedHighPressure();
            cached = new Reading(frames.snapshot(nowNs), system.memory(), p, warn, system.processCpuLoad());
            lastRefreshNs = nowNs;
        }
        return cached;
    }
}
