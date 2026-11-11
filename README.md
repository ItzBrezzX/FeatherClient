# FEATHER FPS

A lightweight, advanced Minecraft Java Edition performance optimization mod with a modern client-style interface.

Created by KingBrezz.

## Project status (read this first)

**FEATHER FPS is not a playable mod yet. No mod JAR exists.**

| Part | State |
|---|---|
| `core` (config, profiles, monitoring, benchmark, compatibility detection) | Compiled and tested (31 tests, all executed and passing on OpenJDK 21) |
| `core/ui` (the whole settings screen: layout, widgets, scrolling, tooltips, search, Apply/Undo, benchmark, profiles, import/export) | Compiled and tested (29 tests passing). Drawing is verified structurally and through offscreen previews, **not inside Minecraft** |
| `fabric/` adapter for Minecraft 1.21.11 (entrypoint, Right Shift keybind, Screen, HUD frame sampler) | **Source draft, never compiled or launched** |
| Forge, NeoForge, Quilt, other Minecraft versions, 26.x | Not started |
| Any actual optimization (FPS limits, culling, particle or entity reduction) | **Not implemented.** Those settings appear in the GUI marked INACTIVE and cannot be changed |

No performance improvement is claimed or measured. See [docs/SUPPORT_MATRIX.md](docs/SUPPORT_MATRIX.md) and [docs/NEXT_STEPS.md](docs/NEXT_STEPS.md).

## The settings screen

Opened with Right Shift (rebindable under Controls). Sections: Dashboard, FPS, Rendering, Performance, Memory, Chunks,
Particles, Entities, Profiles, Compatibility, Advanced, About.
Flat, neutral dark design: square panel, hairline separators instead of boxed cards, one soft cyan accent for selection and "on",
violet only for unsaved changes. The author credit lives in the mod metadata and this README, not on screen.

- **Dashboard:** FPS, average FPS, 1% low (after 100 frames), 0.1% low (after 1000 frames), frame time, stutter count and worst frame,
  Java heap used/allocated/max, process CPU (only when the JVM reports it, otherwise N/A), Minecraft version, loader, profile,
  an "N/6 optimizations active" counter, and a history graph that switches between FPS and slowest-frame-per-slice (stutters show as spikes).
  A stutter is a frame at least 2x the window average and at least 20 ms.
- **Benchmark (Performance page):** records 15, 30 or 60 seconds of frame times, keeps the last 5 runs, and compares the two newest
  (average FPS, 1% low, stutters, worst frame). Differences under 3% average FPS / 5% 1% low are reported as "no clear difference" because
  single runs are noisy. It only measures; use it to check any optimization, other mod, or Sodium with and without. The run continues if you close the screen.
- **Search:** Ctrl+F (Cmd+F on macOS) or click the box; filters every setting by name or description. Esc clears it, a second Esc closes the screen.
- **Settings pages:** toggles, sliders with +/- buttons, cycle buttons, "Reset this page", tooltips (description, allowed range, default, why a setting is disabled).
  Changes stay pending (purple marker) until **Apply**; **Undo** discards them; Esc closes and discards.
- **Honest states:** a setting nothing implements shows `INACTIVE` and is disabled; a setting another installed mod already
  handles (currently Entity Culling for off-screen block entities) shows `HANDLED BY <MOD>` and is disabled.
- **Profiles:** PERFORMANCE, BALANCED, QUALITY, LOW-END, CUSTOM; reset to defaults; export/import; restore latest automatic backup.
- **Chunks** has no settings because no chunk optimization exists.

### Screenshots

[docs/preview/](docs/preview/) holds ten offscreen renders of the real layout code. They are **not screenshots from Minecraft**:
frame data is synthetic (each image says so), memory is the preview process's own, and the font is a stand-in for Minecraft's.
Real in-game screenshots will be added after the first successful launch.

## What works today (core)

| Feature | State |
|---|---|
| Config file with generated defaults, per-key validation, comments, safe atomic saves | Implemented, tested |
| Corrupt-config recovery (file moved aside, defaults restored) | Implemented, tested |
| Rotating automatic backups, export/import, version migration mechanism | Implemented, tested (no real migrations needed yet: only config version 1 exists) |
| Profiles changing real values, custom profile save/restore, reset | Implemented, tested |
| Frame statistics: FPS, average, 1% low, frame time, history series | Implemented, tested with synthetic frame times; not yet fed by a real game |
| JVM memory and process CPU readings, sustained memory-pressure warning | Implemented, tested |
| Detection of Sodium, Embeddium, Iris, Oculus, Indium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling | Implemented, tested; mod ids not yet confirmed against each mod's metadata |

## Supported versions and loaders

None yet. Targets: Minecraft 1.21 to 1.21.11 (Java 21), then 26.1, 26.2, 26.3 (Java 25).
Fabric, Forge, NeoForge, and Quilt where compatible. Details and sources: [docs/SUPPORT_MATRIX.md](docs/SUPPORT_MATRIX.md).

## Installation

Not available: there is no mod JAR. Separate Fabric, Forge and NeoForge instructions will be added with the first verified build of each.

## Configuration

The core writes `feather-fps.properties` to the config directory the first time it runs. It is a commented
`key = value` file you can edit by hand (or use the settings screen). Invalid values are replaced by defaults (with a warning), a file that cannot be
read at all is renamed `feather-fps.properties.corrupt-<time>` and defaults are restored, and unknown keys are preserved.
A changed file is backed up to `feather-fps-backups/` before it is overwritten (`advanced.configBackups`, default 5).
Hand-editing a profile-managed value under a named profile switches the profile to CUSTOM on the next load.

## Build and test

Core and GUI logic (requires JDK 21 or newer, no network):

    ./build-core.sh      # compiles, runs 31 core tests and 29 UI tests
    ./preview.sh         # renders docs/preview/*.png (needs a JDK with java.desktop)

Fabric module: see [fabric/README.md](fabric/README.md). It has never been built. A Gradle file for `core` exists but has not been run.

## Compatibility

Detection only, no integration. Currently the only known overlap is Entity Culling with
`rendering.skipOffscreenBlockEntities`, which is disabled in the GUI when Entity Culling is installed. The other listed mods are detected
and listed but nothing is disabled for them. No mod conflicts have been tested in a real game.

## Troubleshooting

- *My settings reset:* look for a `feather-fps.properties.corrupt-*` file next to the config; the old contents are there.
- *A setting is greyed out:* it says why: INACTIVE (no code applies it yet) or HANDLED BY another mod.

## FAQ

**Is this like Sodium?** No. Sodium replaces Minecraft's chunk renderer, which is a very large piece of engineering that FEATHER FPS does not attempt.
FEATHER FPS is meant to run alongside Sodium (Embeddium on Forge-family loaders), not replace it. Today it contains no optimizations at all.
**How much FPS will this give me?** Unknown. No optimization has been implemented or measured, and none will be promised.
**Will it change my JVM arguments, allocate more RAM, or force garbage collection?** No, and it never will; the memory tools only read values.

## Credits

Created by KingBrezz. Not affiliated with Mojang, Microsoft, or any Minecraft client or mod mentioned in this document.

## License

Not chosen yet. A license must be added before the project is published.
