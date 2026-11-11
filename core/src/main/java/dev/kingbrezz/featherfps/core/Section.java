package dev.kingbrezz.featherfps.core;

/** GUI sections, in display order. */
public enum Section {
    DASHBOARD("Dashboard"), FPS("FPS"), RENDERING("Rendering"), PERFORMANCE("Performance"),
    MEMORY("Memory"), CHUNKS("Chunks"), PARTICLES("Particles"), ENTITIES("Entities"),
    PROFILES("Profiles"), COMPATIBILITY("Compatibility"), ADVANCED("Advanced"), ABOUT("About");

    public final String title;

    Section(String title) { this.title = title; }
}
