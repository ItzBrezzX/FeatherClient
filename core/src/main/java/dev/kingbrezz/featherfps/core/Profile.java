package dev.kingbrezz.featherfps.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Optimization profiles. A profile lists only its differences from the defaults; BALANCED is the defaults.
 * CUSTOM has no preset: it restores the user's own saved values.
 */
public enum Profile {
    PERFORMANCE("Prioritize rendering efficiency.",
            "fps.menuLimit", "30", "fps.backgroundLimit", "10",
            "particles.reduction", "AGGRESSIVE", "entities.decorativeDistance", "32"),
    BALANCED("Balance visual quality and performance."),
    QUALITY("Keep visual effects; apply only safe optimizations.",
            "particles.reduction", "OFF", "entities.decorativeDistance", "128"),
    LOW_END("Reduce visual overhead for weaker hardware.",
            "fps.menuLimit", "30", "fps.backgroundLimit", "5",
            "particles.reduction", "AGGRESSIVE", "entities.decorativeDistance", "24"),
    CUSTOM("Your own values; every managed setting can be changed individually.");

    public final String description;
    private final Map<String, String> overrides;

    Profile(String description, String... pairs) {
        this.description = description;
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        this.overrides = Map.copyOf(m);
    }

    public Map<String, String> overrides() { return overrides; }

    /** The value this profile prescribes for a managed setting. */
    public String expectedValue(Setting s) { return overrides.getOrDefault(s.key, s.defaultValue); }
}
