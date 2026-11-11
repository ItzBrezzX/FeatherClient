package dev.kingbrezz.featherfps.core;

/** Branding and identity strings shared by every loader module and the GUI. */
public final class Info {
    public static final String NAME = "FEATHER FPS";
    public static final String MOD_ID = "featherfps";
    public static final String AUTHOR = "KingBrezz";
    public static final String DESCRIPTION =
            "A lightweight, advanced Minecraft Java Edition performance optimization mod with a modern client-style interface.";

    private Info() {}

    public static String aboutText() {
        return NAME + "\n" + DESCRIPTION + "\nCreated by " + AUTHOR + ".";
    }
}
