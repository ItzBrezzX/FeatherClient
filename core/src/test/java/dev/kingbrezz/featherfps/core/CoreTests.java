package dev.kingbrezz.featherfps.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/** Dependency-free test runner (no JUnit available offline). Exit code 1 if any test fails. */
public final class CoreTests {
    interface T { void run() throws Exception; }

    static int passed, failed;

    static void test(String name, T t) {
        try { t.run(); passed++; System.out.println("PASS  " + name); }
        catch (Throwable e) { failed++; System.out.println("FAIL  " + name + " -> " + e); e.printStackTrace(System.out); }
    }

    static void check(boolean c, String msg) { if (!c) throw new AssertionError(msg); }

    static void near(double expected, double actual, double tol, String msg) {
        if (Math.abs(expected - actual) > tol) throw new AssertionError(msg + ": expected " + expected + " but was " + actual);
    }

    static Path tmp() throws IOException { return Files.createTempDirectory("feather-test"); }

    static final long MS = 1_000_000L;

    public static void main(String[] args) {
        // ---- configuration ----
        test("every setting default is valid; every profile preset is valid", () -> {
            for (Setting s : Settings.ALL) check(s.normalize(s.defaultValue).isPresent(), "default invalid: " + s.key);
            for (Profile p : Profile.values()) for (Map.Entry<String, String> e : p.overrides().entrySet()) {
                Setting s = Settings.find(e.getKey());
                check(s != null && s.profileManaged, p + " references non-managed/unknown key " + e.getKey());
                check(s.normalize(e.getValue()).isPresent(), p + " has invalid value for " + e.getKey());
            }
        });

        test("first load generates a default config file; reload is identical", () -> {
            Path dir = tmp();
            LoadResult r = new ConfigStore(dir).load();
            check(r.createdDefaults() && Files.exists(dir.resolve(ConfigStore.FILE_NAME)), "file not generated");
            LoadResult r2 = new ConfigStore(dir).load();
            check(r2.warnings().isEmpty(), "unexpected warnings: " + r2.warnings());
            check(r.config().asMap().equals(r2.config().asMap()), "reload differs");
        });

        test("save then load round-trips values, custom snapshot and unrecognized keys", () -> {
            Path dir = tmp();
            ConfigStore st = new ConfigStore(dir);
            FeatherConfig c = st.load().config();
            check(c.setInt("fps.menuLimit", 144), "set failed");
            check(c.setInt("memory.warnPercent", 90), "set failed");
            Files.writeString(st.file(), Files.readString(st.file()) + "\nfuture.setting = a\\\\b\n");
            FeatherConfig withExtra = st.load().config();
            check("a\\b".equals(withExtra.unrecognizedKeys().get("future.setting")), "extra not parsed");
            withExtra.setInt("fps.menuLimit", 144);
            st.save(withExtra);
            LoadResult r = st.load();
            check(r.config().getInt("fps.menuLimit") == 144, "fps.menuLimit lost");
            check(r.config().profile() == Profile.CUSTOM, "profile should be CUSTOM");
            check(!r.config().customSnapshot().isEmpty(), "custom snapshot lost");
            check("a\\b".equals(r.config().unrecognizedKeys().get("future.setting")), "extra key not preserved on save");
        });

        test("invalid values fall back to defaults per key; valid keys survive", () -> {
            Path dir = tmp();
            Files.writeString(dir.resolve(ConfigStore.FILE_NAME),
                    "config_version = 1\nfps.menuLimit = 9999\nparticles.reduction = banana\nfps.dynamic = maybe\nmemory.warnPercent = 70\n");
            LoadResult r = new ConfigStore(dir).load();
            check(r.warnings().size() >= 3, "expected >=3 warnings, got " + r.warnings());
            check(r.config().getInt("fps.menuLimit") == 60, "menuLimit not defaulted");
            check(r.config().get("particles.reduction").equals("DECORATIVE"), "particles not defaulted");
            check(r.config().getBool("fps.dynamic"), "dynamic not defaulted");
            check(r.config().getInt("memory.warnPercent") == 70, "valid value lost");
            check(!r.recoveredFromCorruption(), "should not count as corruption");
        });

        test("corrupt file (invalid UTF-8) is moved aside and defaults restored", () -> {
            Path dir = tmp();
            Files.write(dir.resolve(ConfigStore.FILE_NAME), new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE, 'x'});
            LoadResult r = new ConfigStore(dir).load();
            check(r.recoveredFromCorruption(), "not flagged as recovered");
            check(r.config().asMap().equals(new FeatherConfig().asMap()), "not defaults");
            try (Stream<Path> s = Files.list(dir)) {
                check(s.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "corrupt file not preserved");
            }
            check(new ConfigStore(dir).load().warnings().isEmpty(), "regenerated file should load cleanly");
        });

        test("corrupt file (malformed escape) is recovered", () -> {
            Path dir = tmp();
            Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "fps.menuLimit = \\u12\n");
            check(new ConfigStore(dir).load().recoveredFromCorruption(), "not recovered");
        });

        test("hand-edited managed value under a named profile switches profile to CUSTOM", () -> {
            Path dir = tmp();
            Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "config_version = 1\nprofile = QUALITY\nfps.menuLimit = 99\n");
            LoadResult r = new ConfigStore(dir).load();
            check(r.config().profile() == Profile.CUSTOM, "should be CUSTOM");
            check(r.config().getInt("fps.menuLimit") == 99, "value lost");
        });

        test("older config versions run registered migrations; newer versions load with a warning", () -> {
            Migrations m = new Migrations();
            List<String> w = new ArrayList<>();
            m.register(0, in -> { Map<String, String> o = new java.util.HashMap<>(in); o.put("renamed", o.remove("old")); return o; });
            Map<String, String> out = m.migrate(Map.of("old", "v"), 0, 1, w);
            check("v".equals(out.get("renamed")) && !out.containsKey("old"), "migration not applied");
            Path dir = tmp();
            Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "config_version = 99\nfps.menuLimit = 120\nbrand.new = 1\n");
            LoadResult r = new ConfigStore(dir).load();
            check(r.config().getInt("fps.menuLimit") == 120, "known value lost");
            check(r.warnings().stream().anyMatch(s -> s.contains("newer version")), "no newer-version warning");
            check(r.config().unrecognizedKeys().containsKey("brand.new"), "unknown key dropped");
        });

        test("save is atomic: no temp file left; unchanged save makes no backup; changed save makes one", () -> {
            Path dir = tmp();
            ConfigStore st = new ConfigStore(dir);
            FeatherConfig c = st.load().config();
            st.save(c);
            check(st.listBackups().isEmpty(), "unchanged save created a backup");
            check(!Files.exists(dir.resolve(ConfigStore.FILE_NAME + ".tmp")), "temp file left behind");
            c.setInt("memory.warnPercent", 77);
            st.save(c);
            check(st.listBackups().size() == 1, "expected 1 backup, got " + st.listBackups().size());
            check(!Files.exists(dir.resolve(ConfigStore.FILE_NAME + ".tmp")), "temp file left behind");
        });

        test("backups are pruned to advanced.configBackups, oldest first", () -> {
            Path dir = tmp();
            ConfigStore st = new ConfigStore(dir);
            FeatherConfig c = st.load().config();
            c.setInt("advanced.configBackups", 2);
            st.save(c);
            for (int i = 0; i < 6; i++) { c.setInt("memory.warnPercent", 60 + i); st.save(c); }
            List<Path> b = st.listBackups();
            check(b.size() == 2, "expected 2 backups, got " + b.size());
            check(Files.readString(b.get(1)).contains("memory.warnPercent = 64"), "newest backup should hold the previous save");
        });

        test("export then import restores settings; importing garbage throws and changes nothing", () -> {
            Path dir = tmp();
            ConfigStore st = new ConfigStore(dir);
            FeatherConfig c = new FeatherConfig();
            c.applyProfile(Profile.LOW_END);
            Path out = dir.resolve("shared").resolve("mine.properties");
            st.exportTo(c, out);
            check(st.importFrom(out).config().asMap().equals(c.asMap()), "import differs from export");
            Path bad = dir.resolve("bad.properties");
            Files.write(bad, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xC3});
            boolean threw = false;
            try { st.importFrom(bad); } catch (IOException e) { threw = true; }
            check(threw, "garbage import did not throw");
        });

        // ---- profiles and toggles ----
        test("profile switching changes the real values", () -> {
            FeatherConfig c = new FeatherConfig();
            check(c.profile() == Profile.BALANCED, "default profile");
            c.applyProfile(Profile.PERFORMANCE);
            check(c.getInt("fps.menuLimit") == 30 && c.get("particles.reduction").equals("AGGRESSIVE")
                    && c.getInt("entities.decorativeDistance") == 32, "PERFORMANCE values");
            c.applyProfile(Profile.LOW_END);
            check(c.getInt("fps.backgroundLimit") == 5 && c.getInt("entities.decorativeDistance") == 24, "LOW_END values");
            c.applyProfile(Profile.QUALITY);
            check(c.get("particles.reduction").equals("OFF") && c.getInt("entities.decorativeDistance") == 128, "QUALITY values");
            c.applyProfile(Profile.BALANCED);
            check(c.asMap().equals(new FeatherConfig().asMap()), "BALANCED should equal defaults");
            check(c.set("profile", "performance") && c.getInt("fps.menuLimit") == 30, "set(profile) must apply it");
        });

        test("hand-editing a managed setting flips to CUSTOM; CUSTOM values survive other profiles", () -> {
            FeatherConfig c = new FeatherConfig();
            c.applyProfile(Profile.PERFORMANCE);
            c.setInt("fps.menuLimit", 75);
            check(c.profile() == Profile.CUSTOM, "should flip to CUSTOM");
            c.applyProfile(Profile.LOW_END);
            check(c.getInt("fps.menuLimit") == 30, "LOW_END should override");
            c.applyProfile(Profile.CUSTOM);
            check(c.getInt("fps.menuLimit") == 75, "custom value not restored");
            c.setInt("memory.warnPercent", 70);
            check(c.profile() == Profile.CUSTOM, "non-managed setting must not change profile");
        });

        test("reset to defaults restores everything", () -> {
            FeatherConfig c = new FeatherConfig();
            c.applyProfile(Profile.LOW_END); c.setInt("monitor.intervalMs", 1000); c.setInt("fps.menuLimit", 77);
            c.resetToDefaults();
            check(c.asMap().equals(new FeatherConfig().asMap()) && c.customSnapshot().isEmpty(), "not fully reset");
        });

        test("optimization toggles: typed access, validation, unknown keys", () -> {
            FeatherConfig c = new FeatherConfig();
            check(c.setBool("rendering.skipOffscreenBlockEntities", false) && !c.getBool("rendering.skipOffscreenBlockEntities"), "bool toggle");
            check(!c.set("fps.menuLimit", "9") && c.getInt("fps.menuLimit") == 60, "below range accepted");
            check(!c.set("fps.menuLimit", "abc"), "non-number accepted");
            check(!c.set("particles.reduction", "NOPE"), "bad enum accepted");
            check(c.set("particles.reduction", "off") && c.get("particles.reduction").equals("OFF"), "enum not canonicalized");
            check(!c.set("no.such.key", "1"), "unknown key accepted");
            boolean threw = false;
            try { c.get("no.such.key"); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "get on unknown key should throw");
        });

        // ---- performance monitoring ----
        test("FrameStats: steady 100 FPS gives fps/avg/frame time; 1% low needs 100 samples", () -> {
            FrameStats f = new FrameStats();
            long t = 1_000 * MS;
            f.recordFrame(t);
            for (int i = 0; i < 50; i++) { t += 10 * MS; f.recordFrame(t); }
            check(f.snapshot(t).onePercentLowFps().isEmpty(), "1% low must be empty with 50 samples");
            for (int i = 0; i < 70; i++) { t += 10 * MS; f.recordFrame(t); }
            FrameStats.Snapshot s = f.snapshot(t);
            near(100, s.fps(), 0.5, "fps"); near(100, s.avgFps(), 0.5, "avgFps");
            near(10, s.lastFrameMs(), 0.01, "lastFrameMs"); near(10, s.avgFrameMs(), 0.01, "avgFrameMs");
            check(s.onePercentLowFps().isPresent(), "1% low missing with 120 samples");
            near(100, s.onePercentLowFps().getAsDouble(), 0.5, "1% low (steady)");
        });

        test("FrameStats: 1% low is the average of the worst 1% of frames (100 ms + 50 ms of 200 -> 13.33 FPS)", () -> {
            FrameStats f = new FrameStats();
            long t = 0; f.recordFrame(t);
            for (int i = 0; i < 200; i++) { t += (i == 50 ? 100 : i == 150 ? 50 : 10) * MS; f.recordFrame(t); }
            FrameStats.Snapshot s = f.snapshot(t);
            check(s.samples() == 200, "samples=" + s.samples());
            near(1000.0 / 75.0, s.onePercentLowFps().getAsDouble(), 0.01, "1% low");
            check(s.avgFps() < 100 && s.avgFps() > 60, "avg should be dragged down but stay plausible: " + s.avgFps());
        });

        test("FrameStats: window eviction, pause gaps ignored, buffer bounded", () -> {
            FrameStats f = new FrameStats();
            f.setWindowSeconds(10);
            long t = 0; f.recordFrame(t);
            for (int i = 0; i < 100; i++) { t += 10 * MS; f.recordFrame(t); }
            check(f.snapshot(t + 30_000 * MS).samples() == 0, "old samples not evicted");
            FrameStats g = new FrameStats();
            g.recordFrame(0); g.recordFrame(11_000 * MS);
            check(g.snapshot(11_000 * MS).samples() == 0, "11 s gap counted as a frame");
            FrameStats h = new FrameStats();
            h.setWindowSeconds(300);
            long u = 0; h.recordFrame(u);
            for (int i = 0; i < FrameStats.CAPACITY + 5000; i++) { u += MS; h.recordFrame(u); }
            check(h.snapshot(u).samples() == FrameStats.CAPACITY, "buffer not capped at CAPACITY");
        });

        test("FrameStats: graph series is real data, NaN before first sample", () -> {
            FrameStats f = new FrameStats();
            f.setWindowSeconds(60);
            long t = 0; f.recordFrame(t);
            for (int i = 0; i < 1000; i++) { t += 10 * MS; f.recordFrame(t); } // 10 s of 100 FPS
            double[] out = new double[60];
            f.fillFpsSeries(out, t);
            check(Double.isNaN(out[0]) && Double.isNaN(out[40]), "pre-data buckets must be NaN");
            for (int i = 52; i < 59; i++) near(100, out[i], 2.0, "bucket " + i);
        });

        test("SystemMonitor: pressure thresholds, sustained warning, real memory/CPU sanity", () -> {
            long max = 1000;
            check(SystemMonitor.classify(500, max, 85) == SystemMonitor.Pressure.NORMAL, "normal");
            check(SystemMonitor.classify(760, max, 85) == SystemMonitor.Pressure.ELEVATED, "elevated");
            check(SystemMonitor.classify(850, max, 85) == SystemMonitor.Pressure.HIGH, "high");
            check(SystemMonitor.classify(1, Long.MAX_VALUE, 85) == SystemMonitor.Pressure.NORMAL, "unbounded heap");
            SystemMonitor m = new SystemMonitor();
            m.observe(900, max, 85); m.observe(900, max, 85);
            check(!m.sustainedHighPressure(), "2 samples must not warn");
            m.observe(900, max, 85);
            check(m.sustainedHighPressure(), "3 samples must warn");
            m.observe(100, max, 85);
            check(!m.sustainedHighPressure(), "streak must reset");
            SystemMonitor.MemorySnapshot s = m.memory();
            check(s.usedBytes() > 0 && s.usedBytes() <= s.allocatedBytes() && s.allocatedBytes() <= s.maxBytes(), "memory ordering: " + s);
            check(m.processCpuLoad().isEmpty(), "first CPU call must only prime");
            OptionalDouble cpu = m.processCpuLoad();
            check(cpu.isEmpty() || (cpu.getAsDouble() >= 0 && cpu.getAsDouble() <= 1), "cpu out of range");
        });

        test("PerformanceMonitor: readings cached per interval, honor config", () -> {
            FeatherConfig c = new FeatherConfig();
            c.setInt("monitor.intervalMs", 500);
            PerformanceMonitor pm = new PerformanceMonitor(c);
            long t = 0;
            for (int i = 0; i < 150; i++) { t += 10 * MS; pm.onFrame(t); }
            PerformanceMonitor.Reading a = pm.read(t);
            check(a.frames().samples() > 0 && a.memory().usedBytes() > 0, "reading empty");
            check(pm.read(t + 100 * MS) == a, "refreshed too early");
            check(pm.read(t + 600 * MS) != a, "did not refresh after interval");
            c.setBool("memory.warnings", false);
            check(!pm.read(t + 2000 * MS).memoryWarning(), "warning shown while disabled");
        });

        // ---- compatibility and navigation ----
        test("CompatDetector: detects known mods, defers only overlapping settings, tolerates absence/junk", () -> {
            CompatDetector.Report none = CompatDetector.detect(Set.of());
            check(none.detected().isEmpty() && none.deferredSettings().isEmpty(), "empty set");
            check(CompatDetector.detect(null).detected().isEmpty(), "null input");
            CompatDetector.Report r = CompatDetector.detect(List.of("minecraft", "SODIUM", "entityculling", "unknownmod"));
            check(r.detected().size() == 2, "detected=" + r.detected());
            check(r.isDeferred("rendering.skipOffscreenBlockEntities"), "overlap not deferred");
            check(!r.isDeferred("fps.menuLimit"), "unrelated setting deferred");
            check(!CompatDetector.detect(Set.of("sodium", "lithium", "ferritecore", "iris")).isDeferred("rendering.skipOffscreenBlockEntities"),
                    "non-overlapping mods must not defer anything");
            for (CompatDetector.KnownMod km : CompatDetector.KNOWN)
                for (String key : km.overlappingSettings()) check(Settings.find(key) != null, km.id() + " references unknown setting " + key);
        });

        test("NavigationModel: 12 sections in order, wraps both ways; only CORE settings visible by default", () -> {
            check(Section.values().length == 12, "section count");
            check(Section.values()[0].title.equals("Dashboard") && Section.values()[11].title.equals("About"), "order");
            NavigationModel n = new NavigationModel();
            check(n.selected() == Section.DASHBOARD, "start");
            n.previous(); check(n.selected() == Section.ABOUT, "wrap back");
            n.next(); check(n.selected() == Section.DASHBOARD, "wrap forward");
            n.select(Section.MEMORY); n.next(); check(n.selected() == Section.CHUNKS, "next");
            check(NavigationModel.visibleSettings(Section.CHUNKS, true).isEmpty(), "no chunk settings exist yet - none may be invented");
            for (Section s : Section.values()) for (Setting x : NavigationModel.visibleSettings(s, false)) check(x.wiring == Setting.Wiring.CORE, x.key);
            check(!NavigationModel.visibleSettings(Section.FPS, true).isEmpty() && NavigationModel.visibleSettings(Section.FPS, false).isEmpty(),
                    "FPS settings are inactive and must be hidden by default");
        });

        test("branding", () -> {
            check(Info.NAME.equals("FEATHER FPS") && Info.AUTHOR.equals("KingBrezz"), "identity");
            check(Info.aboutText().contains("Created by KingBrezz."), "about text");
        });

        // ---- stutter statistics and benchmark ----
        test("FrameStats: 0.1% low needs 1000 frames; stutters and worst frame are counted from real frame times", () -> {
            FrameStats f = new FrameStats();
            long t = 0; f.recordFrame(t);
            for (int i = 0; i < 999; i++) { t += 10 * MS; f.recordFrame(t); }
            check(f.snapshot(t).pointOnePercentLowFps().isEmpty(), "0.1% low must wait for 1000 frames");
            for (int i = 0; i < 1001; i++) { t += (i == 300 ? 80 : i == 900 ? 45 : i == 1500 ? 19 : 10) * MS; f.recordFrame(t); }
            FrameStats.Snapshot s = f.snapshot(t);
            check(s.samples() == 2000, "samples=" + s.samples());
            check(s.pointOnePercentLowFps().isPresent(), "0.1% low missing at 2000 frames");
            near(1000.0 / 62.5, s.pointOnePercentLowFps().getAsDouble(), 0.01, "0.1% low = avg of worst 2 (80 ms, 45 ms)");
            check(s.hitches() == 2, "19 ms frame is under the 20 ms floor, so only 80 and 45 count; got " + s.hitches());
            near(80.0, s.worstFrameMs(), 0.001, "worst frame");
            FrameStats calm = new FrameStats();
            long u = 0; calm.recordFrame(u);
            for (int i = 0; i < 300; i++) { u += 33 * MS; calm.recordFrame(u); }
            check(calm.snapshot(u).hitches() == 0, "steady 30 fps must not count as stutter");
        });

        test("FrameStats: frame-time series keeps the slowest frame per slice and is NaN before data", () -> {
            FrameStats f = new FrameStats();
            f.setWindowSeconds(60);
            long t = 0; f.recordFrame(t);
            for (int i = 0; i < 1000; i++) { t += (i == 500 ? 70 : 10) * MS; f.recordFrame(t); }
            double[] out = new double[60];
            f.fillFrameTimeSeries(out, t);
            check(Double.isNaN(out[0]), "pre-data slice must be NaN");
            double max = 0; for (double v : out) if (!Double.isNaN(v)) max = Math.max(max, v);
            near(70.0, max, 0.001, "hitch must show in the series");
            near(10.0, out[59], 0.5, "last slice");
        });

        test("Benchmark: records a fixed-length run, ignores pauses, produces real statistics", () -> {
            Benchmark b = new Benchmark();
            check(b.start(0, 20) && !b.start(0, 20), "second start must be refused while running");
            long t = 0;
            b.onFrame(t);
            for (int i = 0; i < 2000; i++) { t += (i == 700 ? 60 : 5) * MS; b.onFrame(t); if (!b.isRunning()) break; }
            check(b.isRunning() && b.progress(t) > 0.45 && b.progress(t) < 0.55, "should be about halfway through a 20 s run: " + b.progress(t));
            t += 11_000 * MS; b.onFrame(t);                  // an 11 s pause: ignored as a frame, but the 20 s clock has passed
            check(!b.isRunning() && b.history().size() == 1, "run should have finished");
            Benchmark.Result r = b.history().get(0);
            check(r.label().equals("Run 1") && r.frames() == 2000, "frames=" + r.frames());
            near(1000.0 / ((1999 * 5.0 + 60) / 2000), r.avgFps(), 0.5, "avg fps");
            check(r.hitches() == 1 && Math.abs(r.worstMs() - 60) < 0.001, "hitch/worst");
            check(!Double.isNaN(r.onePercentLowFps()) && !Double.isNaN(r.pointOnePercentLowFps()), "lows");
            near(5.0, r.medianMs(), 0.001, "median");
            check(r.stdDevMs() > 0 && r.p99Ms() >= r.medianMs(), "spread stats");
        });

        test("Benchmark: too few frames is an explained failure, polling ends a silent run, cancel and history work", () -> {
            Benchmark b = new Benchmark();
            b.start(0, 5);
            check(!b.noFramesWarning(1_000 * MS) && b.noFramesWarning(2_500 * MS), "no-frames warning timing");
            b.poll(6_000 * MS);
            check(!b.isRunning() && b.history().isEmpty() && b.message().contains("Not enough frames"), "silent run: " + b.message());
            b.start(10_000 * MS, 5); b.cancel();
            check(!b.isRunning() && b.message().contains("cancelled"), "cancel");
            for (int run = 0; run < 7; run++) {
                long t0 = 100_000L * MS * (run + 1);
                b.start(t0, 5);
                long t = t0; b.onFrame(t);
                for (int i = 0; i < 700; i++) { t += 8 * MS; b.onFrame(t); }
                b.poll(t0 + 6_000 * MS);
            }
            check(b.history().size() == Benchmark.HISTORY, "history capped at " + Benchmark.HISTORY + ", got " + b.history().size());
            check(b.history().get(Benchmark.HISTORY - 1).label().equals("Run 7"), "newest last");
            b.clearHistory();
            check(b.history().isEmpty(), "clear");
        });

        test("Benchmark: frame buffer is bounded", () -> {
            Benchmark b = new Benchmark();
            b.start(0, 120);
            long t = 0; b.onFrame(t);
            for (int i = 0; i < Benchmark.MAX_FRAMES + 500; i++) { t += 100_000; b.onFrame(t); }
            check(b.framesRecorded() == Benchmark.MAX_FRAMES, "recorded " + b.framesRecorded());
            b.poll(200_000 * MS);
            check(b.history().get(0).truncated() && b.history().get(0).frames() == Benchmark.MAX_FRAMES, "truncation flag");
        });

        test("Benchmark.compare: real differences are called, noise is not, odd cases are labelled honestly", () -> {
            java.util.function.BiFunction<String, Double, Benchmark.Result> run = (label, ms) -> {
                long[] d = new long[3000];
                for (int i = 0; i < d.length; i++) d[i] = (long) (ms * 1e6 * (1 + ((i * 7919) % 100 - 50) / 5000.0));  // +-1% jitter
                return Benchmark.analyze(label, d, d.length, false);
            };
            Benchmark.Result a = run.apply("Run 1", 10.0), faster = run.apply("Run 2", 8.0), same = run.apply("Run 3", 10.1), slower = run.apply("Run 4", 12.5);
            Benchmark.Comparison c1 = Benchmark.compare(a, faster);
            check(c1.verdict() == Benchmark.Verdict.B_FASTER && c1.avgFpsDeltaPct() > 20 && c1.summary().contains("Run 2 is faster"), c1.summary());
            check(Benchmark.compare(a, same).verdict() == Benchmark.Verdict.NO_CLEAR_DIFFERENCE, "1% shift is noise");
            check(Benchmark.compare(a, slower).verdict() == Benchmark.Verdict.B_SLOWER, "slower");
            long[] few = new long[50]; java.util.Arrays.fill(few, 10 * MS);
            check(Benchmark.compare(a, Benchmark.analyze("tiny", few, 50, false)).verdict() == Benchmark.Verdict.NOT_COMPARABLE, "too few frames");
            long[] spiky = new long[3000];
            for (int i = 0; i < spiky.length; i++) spiky[i] = (i % 40 == 0 ? 60 : 8) * MS;
            long[] smooth = new long[3000]; java.util.Arrays.fill(smooth, 9 * MS);
            Benchmark.Comparison mixed = Benchmark.compare(Benchmark.analyze("Smooth", smooth, 3000, false), Benchmark.analyze("Spiky", spiky, 3000, false));
            check(mixed.verdict() == Benchmark.Verdict.MIXED || mixed.verdict() == Benchmark.Verdict.B_SLOWER, "spiky run must not look like a win: " + mixed.summary());
            check(!mixed.summary().contains("Spiky is faster"), mixed.summary());
        });

        test("PerformanceMonitor feeds the benchmark and the frame statistics from the same frames", () -> {
            PerformanceMonitor pm = new PerformanceMonitor(new FeatherConfig());
            pm.benchmark().start(0, 5);
            long t = 0; pm.onFrame(t);
            for (int i = 0; i < 400; i++) { t += 10 * MS; pm.onFrame(t); }
            check(pm.benchmark().framesRecorded() == 400, "benchmark frames=" + pm.benchmark().framesRecorded());
            check(pm.frames().snapshot(t).samples() == 400, "stats frames");
        });

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
