package dev.kingbrezz.featherfps.core;

import java.util.List;
import java.util.Optional;

/** Definition of one configurable setting: type, range, default and documentation. */
public final class Setting {
    public enum Type { BOOL, INT, ENUM }

    /**
     * CORE: consumed by code in the core module (works today).
     * LOADER: needs a hook into the game that only a loader module can provide; inactive until one exists.
     */
    public enum Wiring { CORE, LOADER }

    public final String key;
    public final Section section;
    public final Type type;
    public final String defaultValue;
    public final int min;
    public final int max;
    public final List<String> options;
    public final Wiring wiring;
    public final boolean profileManaged;
    public final String description;

    private Setting(String key, Section section, Type type, String defaultValue, int min, int max,
                    List<String> options, Wiring wiring, boolean profileManaged, String description) {
        this.key = key; this.section = section; this.type = type; this.defaultValue = defaultValue;
        this.min = min; this.max = max; this.options = options; this.wiring = wiring;
        this.profileManaged = profileManaged; this.description = description;
    }

    static Setting bool(String key, Section s, boolean def, Wiring w, boolean managed, String desc) {
        return new Setting(key, s, Type.BOOL, Boolean.toString(def), 0, 0, List.of(), w, managed, desc);
    }

    static Setting integer(String key, Section s, int def, int min, int max, Wiring w, boolean managed, String desc) {
        return new Setting(key, s, Type.INT, Integer.toString(def), min, max, List.of(), w, managed, desc);
    }

    static Setting choice(String key, Section s, String def, List<String> options, Wiring w, boolean managed, String desc) {
        return new Setting(key, s, Type.ENUM, def, 0, 0, List.copyOf(options), w, managed, desc);
    }

    /** Returns the canonical form of {@code raw}, or empty if it is not a valid value. */
    public Optional<String> normalize(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim();
        switch (type) {
            case BOOL:
                if (v.equalsIgnoreCase("true")) return Optional.of("true");
                if (v.equalsIgnoreCase("false")) return Optional.of("false");
                return Optional.empty();
            case INT:
                try {
                    int n = Integer.parseInt(v);
                    return (n < min || n > max) ? Optional.empty() : Optional.of(Integer.toString(n));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            case ENUM:
                for (String o : options) if (o.equalsIgnoreCase(v)) return Optional.of(o);
                return Optional.empty();
            default:
                return Optional.empty();
        }
    }

    public String describeConstraints() {
        switch (type) {
            case BOOL: return "true or false";
            case INT: return "integer " + min + "-" + max;
            default: return String.join(" | ", options);
        }
    }
}
