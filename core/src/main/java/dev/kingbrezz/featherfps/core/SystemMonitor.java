package dev.kingbrezz.featherfps.core;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.OptionalDouble;

/** Real JVM memory and process CPU readings. Nothing here changes JVM settings or triggers garbage collection. */
public final class SystemMonitor {
    public static final int SUSTAIN_SAMPLES = 3;

    public enum Pressure { NORMAL, ELEVATED, HIGH }

    public record MemorySnapshot(long usedBytes, long allocatedBytes, long maxBytes) {
        public double usedPercentOfMax() { return maxBytes <= 0 ? 0 : usedBytes * 100.0 / maxBytes; }
    }

    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    private int highStreak;
    private boolean cpuPrimed;

    /** NORMAL below (warn-10)%, ELEVATED from there up to warn%, HIGH at or above warn% of the maximum heap. */
    public static Pressure classify(long usedBytes, long maxBytes, int warnPercent) {
        if (maxBytes <= 0 || maxBytes == Long.MAX_VALUE) return Pressure.NORMAL;
        double pct = usedBytes * 100.0 / maxBytes;
        if (pct >= warnPercent) return Pressure.HIGH;
        if (pct >= warnPercent - 10) return Pressure.ELEVATED;
        return Pressure.NORMAL;
    }

    public MemorySnapshot memory() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        return new MemorySnapshot(total - rt.freeMemory(), total, rt.maxMemory());
    }

    /** Records one pressure observation. Used heap includes garbage not yet collected, so one HIGH sample is not a warning. */
    public Pressure observe(long usedBytes, long maxBytes, int warnPercent) {
        Pressure p = classify(usedBytes, maxBytes, warnPercent);
        highStreak = (p == Pressure.HIGH) ? highStreak + 1 : 0;
        return p;
    }

    public Pressure samplePressure(int warnPercent) {
        MemorySnapshot m = memory();
        return observe(m.usedBytes(), m.maxBytes(), warnPercent);
    }

    /** True once HIGH pressure has been observed {@link #SUSTAIN_SAMPLES} times in a row. */
    public boolean sustainedHighPressure() { return highStreak >= SUSTAIN_SAMPLES; }

    /**
     * Whole-process CPU load as a 0..1 fraction of total machine capacity, or empty if the JVM cannot report it.
     * The first call only primes the counter and returns empty, because the first reading is not meaningful.
     */
    public OptionalDouble processCpuLoad() {
        if (!(os instanceof com.sun.management.OperatingSystemMXBean ext)) return OptionalDouble.empty();
        double v = ext.getProcessCpuLoad();
        if (!cpuPrimed) { cpuPrimed = true; return OptionalDouble.empty(); }
        if (Double.isNaN(v) || v < 0 || v > 1) return OptionalDouble.empty();
        return OptionalDouble.of(v);
    }
}
