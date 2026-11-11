package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.CompatDetector;
import dev.kingbrezz.featherfps.core.ConfigStore;
import dev.kingbrezz.featherfps.core.FeatherConfig;
import dev.kingbrezz.featherfps.core.PerformanceMonitor;
import java.nio.file.Path;
import java.util.Set;

/** Everything the settings screen needs from the loader module. */
public final class ViewEnvironment {
    public final String minecraftVersion;
    public final String loaderName;
    /** Settings the loader module has actually implemented. CORE settings are always active. */
    public final Set<String> activeLoaderKeys;
    public final CompatDetector.Report compat;
    public final PerformanceMonitor monitor;
    public final ConfigStore store;
    public final FeatherConfig config;
    /** Where FPS samples come from, shown when no frame data exists yet. */
    public final String frameSourceNote;
    public final Path exportPath;
    public final Runnable closeAction;

    public ViewEnvironment(String minecraftVersion, String loaderName, Set<String> activeLoaderKeys,
                           CompatDetector.Report compat, PerformanceMonitor monitor, ConfigStore store,
                           FeatherConfig config, String frameSourceNote, Path exportPath, Runnable closeAction) {
        this.minecraftVersion = minecraftVersion;
        this.loaderName = loaderName;
        this.activeLoaderKeys = Set.copyOf(activeLoaderKeys);
        this.compat = compat;
        this.monitor = monitor;
        this.store = store;
        this.config = config;
        this.frameSourceNote = frameSourceNote;
        this.exportPath = exportPath;
        this.closeAction = closeAction;
    }
}
