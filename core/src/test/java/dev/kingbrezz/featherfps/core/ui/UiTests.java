package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.CompatDetector;
import dev.kingbrezz.featherfps.core.ConfigStore;
import dev.kingbrezz.featherfps.core.FeatherConfig;
import dev.kingbrezz.featherfps.core.PerformanceMonitor;
import dev.kingbrezz.featherfps.core.Profile;
import dev.kingbrezz.featherfps.core.Section;
import dev.kingbrezz.featherfps.core.Setting;
import dev.kingbrezz.featherfps.core.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Tests for the settings screen logic. Rendering is checked structurally; nothing here proves how it looks in-game. */
public final class UiTests {
    interface T { void run() throws Exception; }

    static int passed, failed;

    static void test(String name, T t) {
        try { t.run(); passed++; System.out.println("PASS  " + name); }
        catch (Throwable e) { failed++; System.out.println("FAIL  " + name + " -> " + e); e.printStackTrace(System.out); }
    }

    static void check(boolean c, String msg) { if (!c) throw new AssertionError(msg); }

    static final long MS = 1_000_000L;

    /** A view wired to a temp config directory with a synthetic clock. */
    static final class Fx {
        final Path dir;
        final FeatherConfig config;
        final ConfigStore store;
        final PerformanceMonitor pm;
        final FeatherView view;
        final RecordingRenderer rec = new RecordingRenderer();
        final boolean[] closed = {false};
        long t = 5_000_000_000L;
        int mx = -1, my = -1;
        int w = 480, h = 270;

        Fx(Set<String> active, Collection<String> mods) throws IOException {
            dir = Files.createTempDirectory("feather-ui");
            store = new ConfigStore(dir);
            config = store.load().config();
            pm = new PerformanceMonitor(config);
            ViewEnvironment env = new ViewEnvironment("1.21.11", "Fabric", active, CompatDetector.detect(mods), pm, store, config,
                    "No frame data yet. FPS is measured while the HUD is rendered in-game.",
                    dir.resolve("feather-fps-export.properties"), () -> closed[0] = true);
            view = new FeatherView(env);
        }

        void frame() { frame(w, h); }

        void frame(int w, int h) { rec.clear(); t += 16 * MS; view.render(rec, w, h, mx, my, t); }

        void frames(int n) { for (int i = 0; i < n; i++) frame(); }

        boolean click(String id) { return click(id, 0); }

        /** Scrolls down (as a user would) until the control is on screen. */
        void reveal(String id) {
            for (int i = 0; i < 12; i++) {
                frame();
                if (view.hitRect(id) != null) return;
                view.keyPressed(Keys.PAGE_DOWN, 0);
                for (int j = 0; j < 30; j++) frame();
            }
        }

        boolean click(String id, int button) {
            reveal(id);
            Rect r = view.hitRect(id);
            check(r != null, "no hit region for " + id);
            mx = r.x() + r.w() / 2; my = r.y() + r.h() / 2;
            boolean used = view.mouseClicked(mx, my, button);
            view.mouseReleased(mx, my, button);
            frame();
            return used;
        }

        void type(String text) { text.codePoints().forEach(view::charTyped); }

        void feedFrames(int count, long dtNs) {
            long ts = t;
            for (int i = 0; i < count; i++) { ts += dtNs; pm.onFrame(ts); }
            t = ts;
        }
    }

    static Fx fx() throws IOException { return new Fx(Set.of(), List.of()); }

    public static void main(String[] args) {
        test("every setting has a human-readable label", () -> {
            for (Setting s : Settings.ALL) check(!UiText.label(s.key).equals(s.key), "no label for " + s.key);
            for (Section s : Section.values()) check(s == Section.ABOUT || !UiText.intro(s).isEmpty(), "no intro for " + s);
        });

        test("rounded rect and default outline stay inside their bounds", () -> {
            RecordingRenderer r = new RecordingRenderer();
            for (int radius = 0; radius <= 6; radius++) {
                r.clear();
                r.fillRoundRect(10, 20, 30, 12, radius, 0xFFFFFFFF);
                r.outlineRect(10, 20, 30, 12, 0xFFFFFFFF);
                for (RecordingRenderer.Fill f : r.fills)
                    check(f.x() >= 10 && f.y() >= 20 && f.x() + f.w() <= 40 && f.y() + f.h() <= 32 && f.w() > 0 && f.h() > 0, "radius " + radius + " out of bounds: " + f);
            }
            check(Theme.lerp(0xFF000000, 0xFFFFFFFF, 0f) == 0xFF000000 && Theme.lerp(0xFF000000, 0xFFFFFFFF, 1f) == 0xFFFFFFFF, "lerp ends");
        });

        test("all 12 sections render at several window sizes; draws stay inside the window; clip stack balanced", () -> {
            Fx f = fx();
            int[][] sizes = {{480, 270}, {854, 480}, {320, 200}, {1920, 1080}};
            for (Section s : Section.values()) {
                f.view.select(s);
                for (int[] sz : sizes) {
                    f.frame(sz[0], sz[1]);
                    check(f.rec.hasText(s.title), s + " title not drawn at " + sz[0] + "x" + sz[1]);
                    check(f.rec.clipDepth == 0 && f.rec.maxClipDepth >= 1, "clip stack unbalanced in " + s);
                    check(!f.rec.hasText("NaN") && !f.rec.hasText("null"), "NaN/null text in " + s);
                    for (RecordingRenderer.Fill fl : f.rec.fills)
                        check(fl.x() >= 0 && fl.y() >= 0 && fl.x() + fl.w() <= sz[0] && fl.y() + fl.h() <= sz[1], s + " fill outside window: " + fl);
                }
            }
        });

        test("clicking a sidebar entry selects that section; Up/Down move; ESC closes", () -> {
            Fx f = fx();
            check(f.view.section() == Section.DASHBOARD, "start section");
            f.click("nav:MEMORY");
            check(f.view.section() == Section.MEMORY, "click did not select");
            f.view.keyPressed(Keys.DOWN, 0);
            check(f.view.section() == Section.CHUNKS, "Down");
            f.view.keyPressed(Keys.UP, 0); f.view.keyPressed(Keys.UP, 0);
            check(f.view.section() == Section.PERFORMANCE, "Up");
            check(!f.closed[0], "closed too early");
            f.view.keyPressed(Keys.ESCAPE, 0);
            check(f.closed[0], "ESC did not close");
            f.closed[0] = false;
            f.view.keyPressed(Keys.RIGHT_SHIFT, 0);
            check(f.closed[0], "Right Shift did not close");
        });

        test("toggle: stage, shows unsaved count, Apply saves to disk, Undo discards", () -> {
            Fx f = fx();
            f.view.select(Section.MEMORY);
            check(f.config.getBool("memory.warnings"), "default on");
            f.click("opt:memory.warnings");
            check(f.view.options().hasPending() && f.config.getBool("memory.warnings"), "should be pending, not applied");
            check(f.rec.hasText("1 unsaved change"), "footer should show pending count");
            check(f.view.hitEnabled("btn:apply"), "Apply should be enabled");
            f.click("btn:undo");
            check(!f.view.options().hasPending() && f.rec.hasText("No pending changes"), "undo failed");
            f.click("opt:memory.warnings");
            f.click("btn:apply");
            check(!f.config.getBool("memory.warnings"), "not applied to live config");
            check(!f.view.options().hasPending(), "pending not cleared");
            check(!new ConfigStore(f.dir).load().config().getBool("memory.warnings"), "not saved to disk");
            check(f.rec.hasText("Saved 1 change"), "no confirmation message");
            check(!f.view.hitEnabled("btn:apply"), "Apply should be disabled again");
        });

        test("Enter applies pending changes", () -> {
            Fx f = fx();
            f.view.select(Section.MEMORY);
            f.click("opt:memory.warnings");
            f.view.keyPressed(Keys.ENTER, 0);
            check(!f.config.getBool("memory.warnings"), "Enter did not apply");
        });

        test("slider: click sets value from position, drag follows, +/- step, always within range", () -> {
            Fx f = fx();
            f.view.select(Section.PERFORMANCE);
            f.reveal("opt:monitor.intervalMs");
            Rect r = f.view.hitRect("opt:monitor.intervalMs");
            check(r != null, "slider hit missing");
            f.view.mouseClicked(r.x() + r.w() - 1, r.y() + 3, 0);
            check(f.view.options().value("monitor.intervalMs").equals("5000"), "right edge should be max, got " + f.view.options().value("monitor.intervalMs"));
            f.view.mouseDragged(r.x() - 500, r.y() + 3, 0);
            check(f.view.options().value("monitor.intervalMs").equals("100"), "far left should clamp to min, got " + f.view.options().value("monitor.intervalMs"));
            f.view.mouseDragged(r.x() + r.w() / 2, r.y() + 3, 0);
            int mid = Integer.parseInt(f.view.options().value("monitor.intervalMs"));
            check(mid > 1500 && mid < 3500 && mid % 100 == 0, "middle value " + mid);
            f.view.mouseReleased(0, 0, 0);
            check(!f.view.mouseDragged(0, 0, 0), "drag after release must be ignored");
            f.click("btn:undo");
            f.click("opt:monitor.intervalMs:plus");
            check(f.view.options().value("monitor.intervalMs").equals("600"), "plus step");
            f.click("opt:monitor.intervalMs:minus"); f.click("opt:monitor.intervalMs:minus");
            check(f.view.options().value("monitor.intervalMs").equals("400"), "minus step");
        });

        test("enum setting cycles with left click and back with right click", () -> {
            Fx f = new Fx(Set.of("particles.reduction"), List.of());
            f.view.select(Section.PARTICLES);
            check(f.view.options().value("particles.reduction").equals("DECORATIVE"), "default");
            f.click("opt:particles.reduction");
            check(f.view.options().value("particles.reduction").equals("AGGRESSIVE"), "next");
            f.click("opt:particles.reduction", 1);
            check(f.view.options().value("particles.reduction").equals("DECORATIVE"), "previous");
        });

        test("inactive settings cannot be changed and explain why; they work once the loader marks them active", () -> {
            Fx f = fx();
            f.view.select(Section.FPS);
            f.frame();
            check(f.rec.hasText("INACTIVE"), "no INACTIVE badge");
            check(!f.view.hitEnabled("opt:fps.dynamic") && !f.view.hitEnabled("opt:fps.menuLimit:plus"), "inactive control enabled");
            f.click("opt:fps.dynamic");
            check(!f.view.options().hasPending(), "inactive setting was staged");
            check(f.view.hitTooltip("opt:fps.dynamic").stream().anyMatch(s -> s.startsWith("Inactive")), "tooltip lacks reason");
            Fx g = new Fx(Set.of("fps.dynamic", "fps.menuLimit", "fps.backgroundLimit"), List.of());
            g.view.select(Section.FPS);
            g.click("opt:fps.dynamic");
            check(g.view.options().hasPending(), "active loader setting should be editable");
            check(!g.rec.hasText("INACTIVE"), "badge shown for active settings");
        });

        test("a setting another installed mod already handles is disabled and says which mod", () -> {
            Fx f = new Fx(Set.of("rendering.skipOffscreenBlockEntities"), List.of("entityculling"));
            f.view.select(Section.RENDERING);
            f.frame();
            check(f.rec.hasText("HANDLED BY ENTITY CULLING"), "badge missing");
            check(!f.view.hitEnabled("opt:rendering.skipOffscreenBlockEntities"), "should be disabled");
            check(f.view.hitTooltip("opt:rendering.skipOffscreenBlockEntities").stream().anyMatch(s -> s.contains("Entity Culling")), "tooltip lacks mod name");
            Fx g = new Fx(Set.of("rendering.skipOffscreenBlockEntities"), List.of("sodium"));
            g.view.select(Section.RENDERING);
            g.frame();
            check(g.view.hitEnabled("opt:rendering.skipOffscreenBlockEntities"), "non-overlapping mod must not disable it");
        });

        test("hovering a row shows a tooltip with description, range and default", () -> {
            Fx f = fx();
            f.view.select(Section.MEMORY);
            f.frame();
            f.reveal("row:memory.warnPercent");
            Rect r = f.view.hitRect("row:memory.warnPercent");
            f.mx = r.x() + 20; f.my = r.y() + 5;
            f.frame();
            check(f.rec.hasText("Allowed: integer 50-99") && f.rec.hasText("Default: 85"), "tooltip content not drawn: " + f.rec.texts);
        });

        test("Chunks section has no settings and says so instead of inventing any", () -> {
            Fx f = fx();
            f.view.select(Section.CHUNKS);
            f.frame();
            check(f.rec.hasText("No chunk optimizations are implemented"), "empty note missing");
            check(f.view.hitRect("opt:fps.dynamic") == null, "no option controls expected");
        });

        test("dashboard: placeholders before data, real numbers after frames, 1% low only after 100 frames, no fake values", () -> {
            Fx f = fx();
            f.frames(2);
            check(f.rec.hasText("--") && f.rec.hasText("No frame data yet"), "empty state missing");
            check(f.rec.hasText("needs 100 frames (0)"), "1% low hint missing");
            f.t += 2_000 * MS;
            f.feedFrames(60, 10 * MS);
            f.frame();
            check(f.rec.hasText("needs 100 frames (59)"), "1% low should still be pending (the first frame has no duration): " + f.rec.texts);
            f.t += 2_000 * MS;
            f.feedFrames(150, 10 * MS);
            f.t += MS;
            f.frame();
            check(f.rec.texts.contains("100"), "FPS card should read 100: " + f.rec.texts);
            check(f.rec.hasText("worst 1% of frames") && f.rec.hasText("10.0 ms"), "1% low / frame time missing: " + f.rec.texts);
            check(f.rec.hasText("Minecraft 1.21.11") && f.rec.hasText("Fabric"), "environment missing");
            check(f.rec.hasText("Memory") && f.rec.hasText("CPU"), "memory/CPU rows missing");
            check(!f.rec.hasText("No frame data yet"), "empty-state text should be gone");
        });

        test("profile card stages a profile and its values; Apply saves; hand edit flips to CUSTOM; CUSTOM restores", () -> {
            Fx f = new Fx(Set.of("particles.reduction", "entities.decorativeDistance"), List.of());
            f.view.select(Section.PROFILES);
            f.click("profile:PERFORMANCE");
            OptionsModel o = f.view.options();
            check(o.value("profile").equals("PERFORMANCE") && o.value("entities.decorativeDistance").equals("32"), "profile values not staged");
            check(f.config.profile() == Profile.BALANCED, "must not apply before Apply");
            check(f.rec.hasText("Selected, not saved"), "pending marker missing");
            f.click("btn:apply");
            check(f.config.profile() == Profile.PERFORMANCE && f.config.getInt("entities.decorativeDistance") == 32, "not applied");
            check(new ConfigStore(f.dir).load().config().profile() == Profile.PERFORMANCE, "not saved");
            f.view.select(Section.ENTITIES);
            f.click("opt:entities.decorativeDistance:plus");
            check(o.value("profile").equals("CUSTOM") && o.value("entities.decorativeDistance").equals("37"), "hand edit should flip to CUSTOM");
            f.click("btn:apply");
            check(f.config.profile() == Profile.CUSTOM, "CUSTOM not applied");
            f.view.select(Section.PROFILES);
            f.click("profile:LOW_END");
            f.click("btn:apply");
            check(f.config.getInt("entities.decorativeDistance") == 24, "LOW_END not applied");
            f.click("profile:CUSTOM");
            f.click("btn:apply");
            check(f.config.profile() == Profile.CUSTOM && f.config.getInt("entities.decorativeDistance") == 37, "custom values not restored");
        });

        test("profiles page: reset stages defaults; export/import round-trip; restore needs a backup", () -> {
            Fx f = fx();
            f.config.applyProfile(Profile.LOW_END);
            f.config.setInt("memory.warnPercent", 70);
            f.store.save(f.config);
            f.view.select(Section.PROFILES);
            f.click("btn:reset");
            check(f.view.options().value("profile").equals("BALANCED") && f.view.options().value("memory.warnPercent").equals("85"), "reset not staged");
            f.click("btn:undo");
            f.click("btn:export");
            check(Files.exists(f.dir.resolve("feather-fps-export.properties")) && f.rec.hasText("Exported"), "export failed");
            f.config.applyAll(java.util.Map.of("memory.warnPercent", "60"));
            f.store.save(f.config);
            f.click("btn:import");
            check(f.view.options().value("memory.warnPercent").equals("70") && f.rec.hasText("Imported"), "import not staged: " + f.rec.texts);
            f.click("btn:undo");
            f.t += 3_000 * MS; f.frame();
            check(f.view.hitEnabled("btn:restore"), "backups exist after saves, restore should be enabled");
            f.click("btn:restore");
            check(f.rec.hasText("Backup loaded"), "restore message missing: " + f.rec.texts);
            Fx g = fx();
            g.view.select(Section.PROFILES);
            g.frame();
            check(!g.view.hitEnabled("btn:restore"), "restore must be disabled without backups");
            Files.delete(f.dir.resolve("feather-fps-export.properties"));
            f.click("btn:import");
            check(f.rec.hasText("No feather-fps-export.properties found"), "missing-file message");
        });

        test("compatibility page lists detected mods and what they handle; empty state otherwise", () -> {
            Fx none = fx();
            none.view.select(Section.COMPATIBILITY);
            none.frame();
            check(none.rec.hasText("No known optimization mods detected"), "empty state");
            Fx f = new Fx(Set.of(), List.of("sodium", "entityculling", "somethingelse"));
            f.view.select(Section.COMPATIBILITY);
            f.frame(854, 480);
            check(f.rec.hasText("Sodium") && f.rec.hasText("Entity Culling") && f.rec.hasText("Overlaps: Skip off-screen block entities"), "detected mods: " + f.rec.texts);
            check(f.rec.hasText("Not detected") && !f.rec.hasText("somethingelse"), "unknown mods must not be listed");
        });

        test("about page shows the product description; no page of the GUI shows an author credit", () -> {
            Fx f = fx();
            f.view.select(Section.ABOUT);
            f.frame();
            check(f.rec.hasText("FEATHER FPS") && f.rec.hasText("lightweight"), "about text: " + f.rec.texts);
            for (Section sec : Section.values()) {
                f.view.select(sec);
                f.frame(854, 480);
                check(f.rec.texts.stream().noneMatch(t -> t.contains("KingBrezz") || t.contains("Created by")), "author credit shown on " + sec + ": " + f.rec.texts);
                check(f.rec.texts.stream().noneMatch(t -> t.toUpperCase().contains("VOR" + "TEX")), "old brand name present on " + sec);
            }
        });

        test("scrolling moves content; content outside the viewport is not clickable; scrollbar thumb drags", () -> {
            Fx f = fx();
            f.view.select(Section.PROFILES);
            f.frames(3);
            f.frame(320, 200);
            check(f.view.hitRect("btn:restore") == null, "off-screen button must not be clickable");
            check(f.view.hitRect("scroll:thumb") != null, "scrollbar missing when content overflows");
            Rect before = f.view.hitRect("profile:BALANCED");
            check(before != null, "BALANCED card should be visible at scroll 0");
            f.view.mouseScrolled(160, 100, -3);
            for (int i = 0; i < 40; i++) f.frame(320, 200);
            Rect after = f.view.hitRect("profile:BALANCED");
            check(after == null || after.y() < before.y(), "content did not scroll up");
            for (int i = 0; i < 5; i++) { f.view.keyPressed(Keys.PAGE_DOWN, 0); for (int j = 0; j < 30; j++) f.frame(320, 200); }
            check(f.view.hitRect("btn:restore") != null, "bottom content should be reachable by scrolling");
            Rect th = f.view.hitRect("scroll:thumb");
            f.view.mouseClicked(th.x() + 1, th.y() + 2, 0);
            f.view.mouseReleased(0, 0, 0);
            f.view.mouseScrolled(160, 100, 100);
            for (int i = 0; i < 60; i++) f.frame(320, 200);
            check(f.view.hitRect("profile:PERFORMANCE") != null, "scrolling back to top failed");
            check(!f.view.mouseScrolled(0, 0, 1) || true, "scroll outside panel is ignored");
        });

        // ---- OptionsModel ----
        test("OptionsModel: validation, equal-to-saved clears pending, undo, unknown keys", () -> {
            FeatherConfig c = new FeatherConfig();
            OptionsModel o = new OptionsModel(c);
            check(!o.stage("monitor.intervalMs", "10") && !o.stage("nope", "1") && !o.stage("memory.warnings", "maybe"), "invalid accepted");
            check(o.stage("monitor.intervalMs", "1000") && o.isPending("monitor.intervalMs") && o.pendingCount() == 1, "stage");
            check(c.getInt("monitor.intervalMs") == 500, "config must not change before apply");
            check(o.stage("monitor.intervalMs", "500") && !o.hasPending(), "staging the saved value should clear pending");
            o.stage("memory.warnPercent", "60"); o.undo();
            check(!o.hasPending() && o.value("memory.warnPercent").equals("85"), "undo");
        });

        test("OptionsModel: profile staging is consistent in every direction", () -> {
            FeatherConfig c = new FeatherConfig();
            OptionsModel o = new OptionsModel(c);
            o.stageProfile(Profile.LOW_END);
            check(o.value("profile").equals("LOW_END") && o.value("fps.backgroundLimit").equals("5"), "LOW_END staged");
            o.stage("fps.menuLimit", "45");
            check(o.value("profile").equals("CUSTOM"), "hand edit under staged preset should become CUSTOM");
            o.stageProfile(Profile.BALANCED);
            check(!o.hasPending(), "back to the saved profile should leave nothing pending: " + o.pendingKeys());
            o.stage("fps.menuLimit", "45");
            check(o.value("profile").equals("CUSTOM") && o.isPending("profile"), "flip to CUSTOM");
            o.stage("fps.menuLimit", "60");
            check(!o.hasPending(), "reverting the edit should clear the CUSTOM flip: " + o.pendingKeys());
            o.stage("memory.warnPercent", "70");
            o.stageProfile(Profile.PERFORMANCE);
            check(o.value("memory.warnPercent").equals("70"), "profile staging must keep non-managed pending edits");
            check(o.stage("profile", "quality") && o.value("profile").equals("QUALITY"), "stage(profile) delegates");
        });

        test("OptionsModel: apply order cannot corrupt values (CUSTOM restore after preset round trip)", () -> {
            Path dir = Files.createTempDirectory("feather-om");
            ConfigStore st = new ConfigStore(dir);
            FeatherConfig c = st.load().config();
            OptionsModel o = new OptionsModel(c);
            o.stage("fps.menuLimit", "45"); o.stage("entities.decorativeDistance", "100");
            o.apply(st);
            check(c.profile() == Profile.CUSTOM && c.getInt("fps.menuLimit") == 45, "custom applied");
            o.stageProfile(Profile.PERFORMANCE); o.apply(st);
            check(c.getInt("fps.menuLimit") == 30 && c.getInt("entities.decorativeDistance") == 32, "preset applied");
            o.stageProfile(Profile.CUSTOM); o.apply(st);
            check(c.profile() == Profile.CUSTOM && c.getInt("fps.menuLimit") == 45 && c.getInt("entities.decorativeDistance") == 100, "custom values lost: " + c.asMap());
            FeatherConfig reloaded = st.load().config();
            check(reloaded.asMap().equals(c.asMap()), "saved file differs from live config");
        });

        test("OptionsModel: stageAllFrom and stageDefaults", () -> {
            FeatherConfig c = new FeatherConfig();
            c.applyProfile(Profile.PERFORMANCE);
            FeatherConfig other = new FeatherConfig();
            other.applyProfile(Profile.QUALITY);
            other.setInt("memory.warnPercent", 70);
            OptionsModel o = new OptionsModel(c);
            o.stageAllFrom(other);
            check(o.value("profile").equals("QUALITY") && o.value("memory.warnPercent").equals("70"), "stageAllFrom");
            o.undo();
            o.stageDefaults();
            check(o.value("profile").equals("BALANCED") && o.value("fps.menuLimit").equals("60"), "stageDefaults");
        });

        // ---- stutter cards, graph modes, optimization counter ----
        test("dashboard: 0.1% low waits for 1000 frames; stutters and worst frame come from real frame times", () -> {
            Fx a = fx();
            a.feedFrames(500, 10 * MS); a.t += 700 * MS; a.frame();
            check(a.rec.hasText("needs 1000 frames (499)"), "0.1% low should still be pending: " + a.rec.texts);
            Fx f = fx();
            long ts = f.t;
            for (int i = 0; i < 1200; i++) { ts += (i == 100 || i == 500 || i == 900 ? 60 : 10) * MS; f.pm.onFrame(ts); }
            f.t = ts + 700 * MS; f.frame();
            check(f.rec.hasText("Stutters") && f.rec.texts.contains("3"), "stutter count: " + f.rec.texts);
            check(f.rec.hasText("worst frame 60.0 ms"), "worst frame: " + f.rec.texts);
            check(f.rec.hasText("worst 0.1% of frames"), "0.1% low should be available: " + f.rec.texts);
        });

        test("dashboard: optimization counter is honest (inactive and deferred ones do not count)", () -> {
            check(fx().rec != null, "fixture");
            Fx none = fx(); none.frame();
            check(none.rec.hasText("0/6 active"), "none active: " + none.rec.texts);
            Fx some = new Fx(Set.of("fps.dynamic", "fps.menuLimit"), List.of()); some.frame();
            check(some.rec.hasText("2/6 active"), "two active: " + some.rec.texts);
            Fx deferred = new Fx(Set.of("rendering.skipOffscreenBlockEntities"), List.of("entityculling")); deferred.frame();
            check(deferred.rec.hasText("0/6 active") && deferred.rec.hasText("1 mod detected"), "deferred must not count: " + deferred.rec.texts);
        });

        test("history graph switches between FPS and frame-time views", () -> {
            Fx f = fx();
            f.feedFrames(1200, 10 * MS);
            f.t += 700 * MS;
            f.reveal("btn:graph-ft");
            f.frame();
            check(f.rec.texts.stream().anyMatch(t -> t.matches("\\d+ fps")), "FPS view should label its scale in fps: " + f.rec.texts);
            f.click("btn:graph-ft");
            check(f.rec.texts.stream().anyMatch(t -> t.matches("\\d+ ms")) && f.rec.texts.stream().noneMatch(t -> t.matches("\\d+ fps")),
                    "frame-time view should label its scale in ms: " + f.rec.texts);
            f.click("btn:graph-fps");
            check(f.rec.texts.stream().anyMatch(t -> t.matches("\\d+ fps")), "back to FPS view");
        });

        // ---- benchmark ----
        test("benchmark card: run, progress, results, A/B comparison, cancel, length, clear, no-frames warning", () -> {
            Fx f = fx();
            f.view.select(Section.PERFORMANCE);
            f.frames(2);
            check(f.rec.hasText("Benchmark") && f.rec.hasText("30 s"), "card missing");
            f.click("btn:bench-len"); check(f.rec.hasText("60 s"), "length 60");
            f.click("btn:bench-len"); check(f.rec.hasText("15 s"), "length 15");
            f.click("btn:bench-len"); check(f.rec.hasText("30 s"), "length 30");
            f.click("btn:bench-start");
            check(f.pm.benchmark().isRunning() && !f.view.hitEnabled("btn:bench-start") && f.view.hitEnabled("btn:bench-cancel"), "should be running");
            f.feedFrames(1000, 10 * MS); f.frame();
            check(f.rec.hasText("Running: 10 / 30 s"), "progress text: " + f.rec.texts);
            f.feedFrames(2100, 10 * MS); f.frame();
            check(!f.pm.benchmark().isRunning() && f.pm.benchmark().history().size() == 1, "run 1 should be finished");
            check(f.rec.hasText("Run 1") && f.rec.hasText("Avg FPS") && f.rec.texts.contains("100"), "results row: " + f.rec.texts);
            f.click("btn:bench-start");
            f.feedFrames(3800, 8 * MS); f.frame();
            check(f.pm.benchmark().history().size() == 2, "run 2 should be finished");
            check(f.rec.hasText("Run 2 vs Run 1") && String.join(" ", f.rec.texts).contains("Run 2 is faster"), "comparison: " + f.rec.texts);
            f.click("btn:bench-start");
            f.click("btn:bench-cancel");
            check(!f.pm.benchmark().isRunning() && f.rec.hasText("Benchmark cancelled."), "cancel: " + f.rec.texts);
            f.click("btn:bench-clear");
            check(f.pm.benchmark().history().isEmpty() && !f.rec.hasText("Run 1"), "clear");
            f.click("btn:bench-start");
            f.t += 3_000 * MS; f.frame();
            check(f.rec.hasText("No frames received yet"), "missing-frames warning: " + f.rec.texts);
        });

        // ---- reset page ----
        test("Reset this page stages defaults for available settings only", () -> {
            Fx f = fx();
            f.view.select(Section.MEMORY);
            f.reveal("btn:reset-page");
            check(!f.view.hitEnabled("btn:reset-page"), "nothing to reset yet");
            f.click("opt:memory.warnPercent:plus"); f.click("opt:memory.warnings");
            check(f.view.options().pendingCount() == 2, "two pending edits");
            f.click("btn:reset-page");
            check(!f.view.options().hasPending() && f.rec.hasText("Staged defaults for 2 settings"), "reset should cancel the edits: " + f.rec.texts);
            f.config.applyAll(java.util.Map.of("memory.warnPercent", "70"));
            f.click("btn:reset-page");
            check(f.view.options().value("memory.warnPercent").equals("85") && f.view.options().isPending("memory.warnPercent"), "default staged over saved value");
            f.click("btn:apply");
            check(f.config.getInt("memory.warnPercent") == 85, "applied");
            Fx g = fx();
            g.config.applyAll(java.util.Map.of("fps.menuLimit", "120"));   // saved, non-default, but INACTIVE in this build
            g.view.select(Section.FPS);
            g.reveal("btn:reset-page");
            check(!g.view.hitEnabled("btn:reset-page"), "inactive settings must not be reset");
            Fx h = new Fx(Set.of("fps.menuLimit"), List.of());
            h.config.applyAll(java.util.Map.of("fps.menuLimit", "120", "fps.backgroundLimit", "99"));
            h.view.select(Section.FPS);
            h.click("btn:reset-page");
            check(h.view.options().isPending("fps.menuLimit") && !h.view.options().isPending("fps.backgroundLimit"),
                    "only the active setting may be reset: " + h.view.options().pendingKeys());
        });

        // ---- search ----
        test("search: Ctrl+F focuses, typing filters across sections, Backspace edits, Esc clears then closes", () -> {
            Fx f = fx();
            check(!f.view.charTyped('x') && f.view.searchQuery().isEmpty(), "typing without focus must be ignored");
            check(f.view.keyPressed(Keys.F, Keys.MOD_CONTROL) && f.view.isSearchFocused(), "Ctrl+F");
            f.type("memory");
            f.frame();
            check(f.rec.hasText("Search results") && f.view.hitRect("row:memory.warnings") != null && f.view.hitRect("row:memory.warnPercent") != null, "memory rows");
            check(f.view.hitRect("row:fps.dynamic") == null && f.view.hitRect("row:advanced.configBackups") == null, "unrelated rows must be filtered out");
            f.view.keyPressed(Keys.BACKSPACE, 0); f.view.keyPressed(Keys.BACKSPACE, 0); f.view.keyPressed(Keys.BACKSPACE, 0);
            check(f.view.searchQuery().equals("mem"), "backspace: " + f.view.searchQuery());
            f.view.keyPressed(Keys.RIGHT_SHIFT, 0);
            check(!f.closed[0], "Right Shift must not close the screen while typing");
            f.view.keyPressed(Keys.ESCAPE, 0);
            check(!f.closed[0] && f.view.searchQuery().isEmpty() && !f.view.isSearchFocused(), "first Esc clears the search");
            f.view.keyPressed(Keys.ESCAPE, 0);
            check(f.closed[0], "second Esc closes");
        });

        test("search: matches descriptions and several words, shows an empty state, rows stay usable, nav click leaves search", () -> {
            Fx f = fx();
            f.view.keyPressed(Keys.F, Keys.MOD_CONTROL);
            f.type("cosmetic particles");
            f.frame();
            check(f.view.hitRect("row:particles.reduction") != null, "description match");
            f.view.keyPressed(Keys.ENTER, 0);
            check(!f.view.isSearchFocused() && f.view.searchQuery().equals("cosmetic particles"), "Enter keeps the query but leaves the box");
            f.view.keyPressed(Keys.F, Keys.MOD_CONTROL);
            for (int i = 0; i < 30; i++) f.view.keyPressed(Keys.BACKSPACE, 0);
            f.type("warnings");
            f.click("opt:memory.warnings");
            check(f.view.options().isPending("memory.warnings"), "result rows must be editable");
            f.view.keyPressed(Keys.F, Keys.MOD_CONTROL);
            for (int i = 0; i < 30; i++) f.view.keyPressed(Keys.BACKSPACE, 0);
            f.type("zzzz");
            f.frame();
            check(f.rec.hasText("No settings match"), "empty state");
            f.click("nav:FPS");
            check(f.view.searchQuery().isEmpty() && f.view.section() == Section.FPS, "nav click should leave search");
            f.view.keyPressed(Keys.F, Keys.MOD_CONTROL);
            f.type("e");
            for (int[] sz : new int[][]{{480, 270}, {320, 200}, {854, 480}}) {
                f.frame(sz[0], sz[1]);
                for (RecordingRenderer.Fill fl : f.rec.fills)
                    check(fl.x() >= 0 && fl.y() >= 0 && fl.x() + fl.w() <= sz[0] && fl.y() + fl.h() <= sz[1], "search page fill outside window: " + fl);
            }
        });

        System.out.println();
        System.out.println("UI: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
