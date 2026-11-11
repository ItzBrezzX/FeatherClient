package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.Section;
import java.util.Map;

/** Human-readable names and section descriptions for the settings screen. */
public final class UiText {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("profile", "Optimization profile"),
            Map.entry("fps.dynamic", "Dynamic FPS"),
            Map.entry("fps.menuLimit", "Menu FPS limit"),
            Map.entry("fps.backgroundLimit", "Background FPS limit"),
            Map.entry("rendering.skipOffscreenBlockEntities", "Skip off-screen block entities"),
            Map.entry("particles.reduction", "Particle reduction"),
            Map.entry("entities.decorativeDistance", "Decorative entity distance"),
            Map.entry("monitor.intervalMs", "Monitor refresh (ms)"),
            Map.entry("monitor.historySeconds", "FPS history length (s)"),
            Map.entry("monitor.graphVisible", "Show FPS graph"),
            Map.entry("memory.warnings", "Memory pressure warnings"),
            Map.entry("memory.warnPercent", "Warning threshold (%)"),
            Map.entry("advanced.configBackups", "Config backups to keep"));

    private UiText() {}

    public static String label(String key) { return LABELS.getOrDefault(key, key); }

    public static String intro(Section s) {
        switch (s) {
            case DASHBOARD: return "Live readings from the running game.";
            case FPS: return "Frame rate caps. They do not replace Minecraft's own Max Framerate and VSync options.";
            case RENDERING: return "Skip rendering work for things the camera cannot see.";
            case PERFORMANCE: return "Monitoring and frame-time statistics.";
            case MEMORY: return "Memory readings and warnings. FEATHER FPS never changes JVM arguments or forces garbage collection.";
            case CHUNKS: return "Chunk loading and chunk rendering.";
            case PARTICLES: return "Handling of cosmetic particles.";
            case ENTITIES: return "Render distance for decorative entities only.";
            case PROFILES: return "Presets that change several settings at once, plus config tools.";
            case COMPATIBILITY: return "Other optimization mods installed next to FEATHER FPS.";
            case ADVANCED: return "Config file and backups.";
            default: return "";
        }
    }

    public static String emptyNote(Section s) {
        if (s == Section.CHUNKS) {
            return "No chunk optimizations are implemented in FEATHER FPS. Use a dedicated chunk renderer such as Sodium "
                    + "(Embeddium on Forge-family loaders); FEATHER FPS works alongside them.";
        }
        return "This section has no settings.";
    }
}
