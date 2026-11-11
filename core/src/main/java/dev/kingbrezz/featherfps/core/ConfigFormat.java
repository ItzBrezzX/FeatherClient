package dev.kingbrezz.featherfps.core;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

/** Reads and writes the commented "key = value" config file. */
final class ConfigFormat {
    private ConfigFormat() {}

    static String render(FeatherConfig c) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("# ").append(Info.NAME).append(" configuration - created by ").append(Info.AUTHOR).append('\n');
        sb.append("# Lines starting with '#' are comments. Edit a value and restart Minecraft to apply it.\n");
        sb.append("# Invalid values are replaced with their defaults when the file is loaded.\n");
        sb.append("# Settings marked 'inactive' are stored but not applied by this build.\n\n");
        sb.append("config_version = ").append(Migrations.CURRENT_VERSION).append("\n\n");
        for (Section sec : Section.values()) {
            List<Setting> list = Settings.forSection(sec);
            if (list.isEmpty()) continue;
            sb.append("# ---- ").append(sec.title).append(" ----\n\n");
            for (Setting s : list) {
                sb.append("# ").append(s.description).append('\n');
                sb.append("# Allowed: ").append(s.describeConstraints()).append(" (default ").append(s.defaultValue).append(")\n");
                if (s.wiring == Setting.Wiring.LOADER) {
                    sb.append("# Status: inactive - needs the in-game integration layer, which this build does not include.\n");
                }
                sb.append(s.key).append(" = ").append(escape(c.get(s.key))).append("\n\n");
            }
        }
        if (!c.customSnapshot().isEmpty()) {
            sb.append("# ---- Saved CUSTOM profile (restored when the CUSTOM profile is selected) ----\n\n");
            for (Map.Entry<String, String> e : c.customSnapshot().entrySet()) {
                sb.append("custom.").append(e.getKey()).append(" = ").append(escape(e.getValue())).append('\n');
            }
            sb.append('\n');
        }
        if (!c.unrecognizedKeys().isEmpty()) {
            sb.append("# ---- Unrecognized keys (kept as-is; they may belong to a newer version) ----\n\n");
            for (Map.Entry<String, String> e : c.unrecognizedKeys().entrySet()) {
                sb.append(escapeKey(e.getKey())).append(" = ").append(escape(e.getValue())).append('\n');
            }
        }
        return sb.toString();
    }

    static LoadResult parse(Reader reader, Migrations migrations) throws IOException {
        Properties p = new Properties();
        try {
            p.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed escape sequence in config file", e);
        }
        Map<String, String> raw = new TreeMap<>();
        for (String k : p.stringPropertyNames()) raw.put(k, p.getProperty(k));

        List<String> warnings = new ArrayList<>();
        int version = Migrations.CURRENT_VERSION;
        String vs = raw.remove("config_version");
        if (vs == null) {
            warnings.add("config_version is missing; assuming " + version + ".");
        } else {
            try {
                version = Integer.parseInt(vs.trim());
            } catch (NumberFormatException e) {
                warnings.add("config_version '" + vs + "' is not a number; assuming " + version + ".");
            }
        }
        if (version > Migrations.CURRENT_VERSION) {
            warnings.add("Config was written by a newer version (" + version + " > " + Migrations.CURRENT_VERSION
                    + "). Known settings were loaded; unknown ones are preserved. The old file is backed up before it is overwritten.");
        } else if (version < Migrations.CURRENT_VERSION) {
            raw = migrations.migrate(raw, version, Migrations.CURRENT_VERSION, warnings);
        }

        FeatherConfig cfg = new FeatherConfig();
        for (Setting s : Settings.ALL) {
            String v = raw.remove(s.key);
            if (v == null) continue;
            Optional<String> n = s.normalize(v);
            if (n.isPresent()) {
                cfg.loadValue(s.key, n.get());
            } else {
                warnings.add("Invalid value '" + v + "' for " + s.key + " (" + s.describeConstraints()
                        + "); using default " + s.defaultValue + ".");
            }
        }
        for (Iterator<Map.Entry<String, String>> it = raw.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (!e.getKey().startsWith("custom.")) continue;
            String key = e.getKey().substring("custom.".length());
            Setting s = Settings.find(key);
            Optional<String> n = (s != null && s.profileManaged) ? s.normalize(e.getValue()) : Optional.empty();
            if (n.isPresent()) cfg.loadCustom(key, n.get());
            else warnings.add("Ignoring invalid saved custom-profile entry '" + e.getKey() + "'.");
            it.remove();
        }
        for (Map.Entry<String, String> e : raw.entrySet()) cfg.putExtra(e.getKey(), e.getValue());

        String before = cfg.profile().name();
        if (cfg.reconcileProfile()) {
            warnings.add("Settings no longer match profile " + before + "; profile switched to CUSTOM.");
        }
        return new LoadResult(cfg, List.copyOf(warnings), false, false);
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String escapeKey(String k) {
        return escape(k).replace(" ", "\\ ").replace("=", "\\=").replace(":", "\\:").replace("#", "\\#").replace("!", "\\!");
    }
}
