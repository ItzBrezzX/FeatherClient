package dev.kingbrezz.featherfps.core;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/** Config format migrations. Step N upgrades the raw key/value map from version N to N+1. */
public final class Migrations {
    public static final int CURRENT_VERSION = 1;

    private final TreeMap<Integer, UnaryOperator<Map<String, String>>> steps = new TreeMap<>();

    public void register(int fromVersion, UnaryOperator<Map<String, String>> step) { steps.put(fromVersion, step); }

    /** Upgrades {@code raw} from {@code from} to {@code to}. A version with no registered step is treated as unchanged. */
    public Map<String, String> migrate(Map<String, String> raw, int from, int to, List<String> warnings) {
        Map<String, String> cur = new TreeMap<>(raw);
        for (int v = from; v < to; v++) {
            UnaryOperator<Map<String, String>> step = steps.get(v);
            if (step == null) {
                warnings.add("No migration registered for config version " + v + "; keeping values as they are.");
                continue;
            }
            cur = new TreeMap<>(step.apply(cur));
        }
        return cur;
    }
}
