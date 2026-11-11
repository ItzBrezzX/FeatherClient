package dev.kingbrezz.featherfps.core;

import java.util.List;

/** Result of loading or importing a config. {@code warnings} is shown to the user, never silently dropped. */
public record LoadResult(FeatherConfig config, List<String> warnings, boolean recoveredFromCorruption, boolean createdDefaults) {}
