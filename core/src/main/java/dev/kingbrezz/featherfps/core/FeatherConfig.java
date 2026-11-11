package dev.kingbrezz.featherfps.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory configuration. All values are validated; invalid values are rejected, never stored. */
public final class FeatherConfig {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> customSnapshot = new LinkedHashMap<>();
    private final Map<String, String> extras = new TreeMap<>();

    public FeatherConfig() { resetToDefaults(); }

    /** Restores every setting to its default and forgets the saved custom profile. Unrecognized keys are kept. */
    public void resetToDefaults() {
        values.clear();
        customSnapshot.clear();
        for (Setting s : Settings.ALL) values.put(s.key, s.defaultValue);
    }

    public String get(String key) {
        String v = values.get(key);
        if (v == null) throw new IllegalArgumentException("Unknown setting: " + key);
        return v;
    }

    public boolean getBool(String key) { return Boolean.parseBoolean(get(key)); }

    public int getInt(String key) { return Integer.parseInt(get(key)); }

    public Profile profile() { return Profile.valueOf(get("profile")); }

    /** Sets a value. Returns false (and changes nothing) if the key is unknown or the value is invalid. */
    public boolean set(String key, String value) {
        Setting s = Settings.find(key);
        if (s == null) return false;
        Optional<String> n = s.normalize(value);
        if (n.isEmpty()) return false;
        if (key.equals("profile")) {
            applyProfile(Profile.valueOf(n.get()));
            return true;
        }
        String old = values.get(key);
        values.put(key, n.get());
        if (s.profileManaged && !old.equals(n.get()) && profile() != Profile.CUSTOM) values.put("profile", Profile.CUSTOM.name());
        return true;
    }

    public boolean setBool(String key, boolean v) { return set(key, Boolean.toString(v)); }

    public boolean setInt(String key, int v) { return set(key, Integer.toString(v)); }

    /** Applies a profile. Leaving CUSTOM remembers the custom values; applying CUSTOM restores them. */
    public void applyProfile(Profile p) {
        if (profile() == Profile.CUSTOM) snapshotCustom();
        if (p == Profile.CUSTOM) {
            values.putAll(customSnapshot);
        } else {
            for (Setting s : Settings.profileManaged()) values.put(s.key, p.expectedValue(s));
        }
        values.put("profile", p.name());
    }


    /** Independent copy (values, saved custom profile and unrecognized keys). */
    public FeatherConfig copy() {
        FeatherConfig c = new FeatherConfig();
        c.values.clear();
        c.values.putAll(values);
        c.customSnapshot.putAll(customSnapshot);
        c.extras.putAll(extras);
        return c;
    }

    /**
     * Applies a complete set of desired values at once (used by the settings screen's Apply button).
     * Invalid or unknown entries are ignored. Leaving CUSTOM remembers the custom values first, and if the managed
     * values do not match the named profile the profile becomes CUSTOM.
     */
    public void applyAll(Map<String, String> desired) {
        Map<String, String> next = new LinkedHashMap<>(values);
        for (Map.Entry<String, String> e : desired.entrySet()) {
            Setting s = Settings.find(e.getKey());
            if (s == null) continue;
            Optional<String> n = s.normalize(e.getValue());
            if (n.isPresent()) next.put(s.key, n.get());
        }
        if (profile() == Profile.CUSTOM && !Profile.CUSTOM.name().equals(next.get("profile"))) snapshotCustom();
        values.clear();
        values.putAll(next);
        reconcileProfile();
    }

    public Map<String, String> asMap() { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }

    public Map<String, String> customSnapshot() { return Collections.unmodifiableMap(customSnapshot); }

    public Map<String, String> unrecognizedKeys() { return Collections.unmodifiableMap(extras); }

    // ---- package-private plumbing for ConfigFormat / ConfigStore ----

    void snapshotCustom() {
        for (Setting s : Settings.profileManaged()) customSnapshot.put(s.key, values.get(s.key));
    }

    void prepareForSave() { if (profile() == Profile.CUSTOM) snapshotCustom(); }

    void loadValue(String key, String normalized) { values.put(key, normalized); }

    void loadCustom(String key, String normalized) { customSnapshot.put(key, normalized); }

    void putExtra(String key, String value) { extras.put(key, value); }

    /** If a hand-edited file no longer matches its named profile, the profile becomes CUSTOM. */
    boolean reconcileProfile() {
        Profile p = profile();
        if (p == Profile.CUSTOM) return false;
        for (Setting s : Settings.profileManaged()) {
            if (!values.get(s.key).equals(p.expectedValue(s))) {
                values.put("profile", Profile.CUSTOM.name());
                return true;
            }
        }
        return false;
    }
}
