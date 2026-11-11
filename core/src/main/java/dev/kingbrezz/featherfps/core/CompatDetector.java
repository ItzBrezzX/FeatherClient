package dev.kingbrezz.featherfps.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Detects well-known optimization/graphics mods by loader mod id and reports which FEATHER FPS settings
 * overlap with them, so only those settings are deferred. The loader module supplies the loaded mod ids.
 * Ids are the commonly published ones; confirm against each mod's metadata when wiring a loader.
 */
public final class CompatDetector {
    public record KnownMod(String id, String displayName, String role, Set<String> overlappingSettings) {}

    public record Report(List<KnownMod> detected, Set<String> deferredSettings) {
        public boolean isDeferred(String settingKey) { return deferredSettings.contains(settingKey); }
    }

    public static final List<KnownMod> KNOWN = List.of(
            new KnownMod("sodium", "Sodium", "Replaces the chunk renderer", Set.of()),
            new KnownMod("embeddium", "Embeddium", "Sodium-derived renderer for Forge-family loaders", Set.of()),
            new KnownMod("iris", "Iris Shaders", "Shader pack loader", Set.of()),
            new KnownMod("oculus", "Oculus", "Shader pack loader for Forge-family loaders", Set.of()),
            new KnownMod("indium", "Indium", "Fabric Rendering API support for Sodium", Set.of()),
            new KnownMod("lithium", "Lithium", "Game logic optimizations", Set.of()),
            new KnownMod("ferritecore", "FerriteCore", "Memory usage reduction", Set.of()),
            new KnownMod("modernfix", "ModernFix", "Startup, memory and performance fixes", Set.of()),
            new KnownMod("immediatelyfast", "ImmediatelyFast", "Optimizes immediate-mode rendering", Set.of()),
            new KnownMod("entityculling", "Entity Culling",
                    "Skips rendering of entities and block entities that are not visible",
                    Set.of("rendering.skipOffscreenBlockEntities")));

    private CompatDetector() {}

    public static Report detect(Collection<String> loadedModIds) {
        List<KnownMod> found = new ArrayList<>();
        Set<String> deferred = new TreeSet<>();
        if (loadedModIds != null) {
            Set<String> ids = new TreeSet<>();
            for (String id : loadedModIds) if (id != null) ids.add(id.toLowerCase(Locale.ROOT));
            for (KnownMod m : KNOWN) {
                if (ids.contains(m.id())) {
                    found.add(m);
                    deferred.addAll(m.overlappingSettings());
                }
            }
        }
        return new Report(List.copyOf(found), Set.copyOf(deferred));
    }
}
