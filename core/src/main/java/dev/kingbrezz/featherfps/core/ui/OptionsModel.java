package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.ConfigStore;
import dev.kingbrezz.featherfps.core.FeatherConfig;
import dev.kingbrezz.featherfps.core.Profile;
import dev.kingbrezz.featherfps.core.Setting;
import dev.kingbrezz.featherfps.core.Settings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Staged edits for the settings screen: changes stay pending until Apply, can be undone, and are saved together.
 * Mirrors the config's own rule that hand-editing a profile-managed value under a named profile switches to CUSTOM.
 */
public final class OptionsModel {
    private final FeatherConfig config;
    private final Map<String, String> pending = new LinkedHashMap<>();

    public OptionsModel(FeatherConfig config) { this.config = config; }

    /** The value to show: the pending one if there is one, otherwise the saved one. */
    public String value(String key) {
        String p = pending.get(key);
        return p != null ? p : config.get(key);
    }

    public String committed(String key) { return config.get(key); }

    public boolean isPending(String key) { return pending.containsKey(key); }

    public boolean hasPending() { return !pending.isEmpty(); }

    public int pendingCount() { return pending.size(); }

    public Set<String> pendingKeys() { return Set.copyOf(pending.keySet()); }

    /** Stages one value. Returns false (and changes nothing) if the key is unknown or the value is invalid. */
    public boolean stage(String key, String raw) {
        Setting s = Settings.find(key);
        if (s == null) return false;
        Optional<String> n = s.normalize(raw);
        if (n.isEmpty()) return false;
        if (key.equals("profile")) {
            stageProfile(Profile.valueOf(n.get()));
            return true;
        }
        put(key, n.get());
        if (s.profileManaged) reconcile();
        return true;
    }

    /** Stages a profile together with every managed value it prescribes. Hand-staged managed edits are replaced. */
    public void stageProfile(Profile p) {
        if (value("profile").equals(p.name())) return;
        for (Setting s : Settings.profileManaged()) pending.remove(s.key);
        pending.remove("profile");
        FeatherConfig scratch = config.copy();
        scratch.applyProfile(p);
        for (Setting s : Settings.profileManaged()) put(s.key, scratch.get(s.key));
        put("profile", scratch.get("profile"));
    }

    /** Stages every value from {@code other} (used by Import and Restore backup). */
    public void stageAllFrom(FeatherConfig other) {
        for (Setting s : Settings.ALL) put(s.key, other.get(s.key));
        reconcile();
    }

    /** Stages every default value (used by Reset to defaults). */
    public void stageDefaults() {
        for (Setting s : Settings.ALL) put(s.key, s.defaultValue);
        reconcile();
    }

    public void undo() { pending.clear(); }

    /** Applies all pending changes to the live config and saves it. Pending changes are cleared even if saving fails. */
    public void apply(ConfigStore store) throws IOException {
        Map<String, String> desired = new LinkedHashMap<>(config.asMap());
        desired.putAll(pending);
        pending.clear();
        config.applyAll(desired);
        store.save(config);
    }

    private void put(String key, String value) {
        if (value.equals(config.get(key))) pending.remove(key);
        else pending.put(key, value);
    }

    private void reconcile() {
        boolean sameAsSaved = true;
        for (Setting s : Settings.profileManaged()) {
            if (!value(s.key).equals(config.get(s.key))) { sameAsSaved = false; break; }
        }
        if (sameAsSaved) { pending.remove("profile"); return; }
        Profile eff = Profile.valueOf(value("profile"));
        if (eff == Profile.CUSTOM) return;
        for (Setting s : Settings.profileManaged()) {
            if (!value(s.key).equals(eff.expectedValue(s))) { put("profile", Profile.CUSTOM.name()); return; }
        }
    }
}
