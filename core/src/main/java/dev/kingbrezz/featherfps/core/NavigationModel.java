package dev.kingbrezz.featherfps.core;

import java.util.List;

/** GUI navigation state, independent of any rendering code so it can be tested without Minecraft. */
public final class NavigationModel {
    private Section selected = Section.DASHBOARD;

    public Section selected() { return selected; }

    public void select(Section s) { selected = s; }

    public void next() { selected = Section.values()[(selected.ordinal() + 1) % Section.values().length]; }

    public void previous() {
        int n = Section.values().length;
        selected = Section.values()[(selected.ordinal() + n - 1) % n];
    }

    /** Settings to show in a section. Inactive (loader-dependent) settings are hidden unless requested. */
    public static List<Setting> visibleSettings(Section s, boolean includeInactive) {
        return Settings.forSection(s).stream()
                .filter(x -> includeInactive || x.wiring == Setting.Wiring.CORE).toList();
    }
}
