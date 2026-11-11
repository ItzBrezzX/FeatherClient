package dev.kingbrezz.featherfps.core;

import dev.kingbrezz.featherfps.core.Setting.Wiring;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of every setting FEATHER FPS knows about. */
public final class Settings {
    public static final List<Setting> ALL;
    private static final Map<String, Setting> BY_KEY = new LinkedHashMap<>();

    static {
        List<String> profiles = new ArrayList<>();
        for (Profile p : Profile.values()) profiles.add(p.name());
        List<Setting> l = new ArrayList<>();
        l.add(Setting.choice("profile", Section.PROFILES, "BALANCED", profiles, Wiring.CORE, false,
                "Active optimization profile. Changing a profile-managed setting by hand switches this to CUSTOM."));
        l.add(Setting.bool("fps.dynamic", Section.FPS, true, Wiring.LOADER, true,
                "Lower the frame rate cap while the window is unfocused or a menu is open."));
        l.add(Setting.integer("fps.menuLimit", Section.FPS, 60, 10, 260, Wiring.LOADER, true,
                "Frame rate cap while a menu or the pause screen is open."));
        l.add(Setting.integer("fps.backgroundLimit", Section.FPS, 30, 1, 260, Wiring.LOADER, true,
                "Frame rate cap while the game window is not focused."));
        l.add(Setting.bool("rendering.skipOffscreenBlockEntities", Section.RENDERING, true, Wiring.LOADER, true,
                "Skip rendering block entities (chests, signs, banners) that are outside the camera view."));
        l.add(Setting.choice("particles.reduction", Section.PARTICLES, "DECORATIVE",
                List.of("OFF", "DECORATIVE", "AGGRESSIVE"), Wiring.LOADER, true,
                "How many purely cosmetic particles are skipped. Gameplay-relevant particles are never skipped."));
        l.add(Setting.integer("entities.decorativeDistance", Section.ENTITIES, 64, 16, 128, Wiring.LOADER, true,
                "Maximum render distance in blocks for decorative entities (armor stands, item frames, paintings). Never applies to players, mobs or projectiles."));
        l.add(Setting.integer("monitor.intervalMs", Section.PERFORMANCE, 500, 100, 5000, Wiring.CORE, false,
                "How often the dashboard readings (FPS, memory, CPU) are refreshed, in milliseconds."));
        l.add(Setting.integer("monitor.historySeconds", Section.PERFORMANCE, 60, 10, 300, Wiring.CORE, false,
                "Length of the FPS history window used for averages, 1% lows and the graph, in seconds."));
        l.add(Setting.bool("monitor.graphVisible", Section.PERFORMANCE, true, Wiring.LOADER, false,
                "Show the FPS history graph on the dashboard."));
        l.add(Setting.bool("memory.warnings", Section.MEMORY, true, Wiring.CORE, false,
                "Warn when heap usage stays above the threshold. Informational only; nothing is freed or changed."));
        l.add(Setting.integer("memory.warnPercent", Section.MEMORY, 85, 50, 99, Wiring.CORE, false,
                "Heap usage, as a percentage of the maximum heap, that counts as high memory pressure."));
        l.add(Setting.integer("advanced.configBackups", Section.ADVANCED, 5, 0, 20, Wiring.CORE, false,
                "How many automatic backups of the config file to keep (0 disables backups)."));
        ALL = List.copyOf(l);
        for (Setting s : ALL) BY_KEY.put(s.key, s);
    }

    private Settings() {}

    public static Setting find(String key) { return BY_KEY.get(key); }

    public static List<Setting> forSection(Section section) {
        return ALL.stream().filter(s -> s.section == section).toList();
    }

    public static List<Setting> profileManaged() {
        return ALL.stream().filter(s -> s.profileManaged).toList();
    }

}
