# FEATHER FPS - support matrix

**Build status of every row below: NOT BUILT, NOT TESTED.** Nothing here is a support claim.
A version moves to "supported" only when its own build compiles and has been launched and tested.

## Verified facts from research (web search, Nov 2026)

| Fact | Source |
|---|---|
| Minecraft 26.1 is the first release without obfuscation; mods built for 1.21.11 or older do not work on it, even as compile-only dependencies. | Fabric docs "Porting to 26.1"; Fabric blog "Fabric for Minecraft 26.1" |
| 26.1 requires Java 25 (also for the Gradle JVM). 1.21.x uses Java 21. | Fabric blog; NeoForged blog; a third-party mod's install page lists Java 21 for 1.21.11 and 25 for 26.x |
| Fabric on 26.1+ uses the non-remapping Loom plugin `net.fabricmc.fabric-loom` (`implementation` instead of `modImplementation`, `jar` instead of `remapJar`). Yarn is no longer officially supported. | Fabric blog |
| NeoForge versions now have four components (e.g. `26.1.0.10-beta`): the first three are the Minecraft version. | NeoForged blog |
| Architectury's setup docs for 26.x use `dev.architectury.loom-no-remap` and list Fabric Loader 0.19.3+ and Fabric API 0.152.0+ as minimums. | Architectury docs |
| Rendering changed heavily in 26.1, so render-related code and mixin targets must be written per version line. | Fabric blog ("many changes, especially to the rendering engine") |

## Reported but not confirmed against official metadata

- 26.2 and 26.3 exist, with Fabric/NeoForge/Forge builds and Quilt on 26.x only. This comes from one third-party mod's install page (edited Oct 21, 2026), not from Mojang or the loader projects.
- NeoForge for 26.1: that page lists NeoForge starting at 26.1.2, while the NeoForged blog mentions NeoForge builds for 26.1. Check the NeoForge version list before choosing a target.
- Forge on 26.1-26.3 is reported by the same page; it was absent from one release of BlueMap's 26.1 line, so Forge availability varies.
- 1.21.0 through 1.21.10: loader availability, mappings and exact dependency versions were **not researched** per version.

## Target versions

| Minecraft | Java | Mappings / toolchain (expected) | Fabric | Forge | NeoForge | Quilt | Build status |
|---|---|---|---|---|---|---|---|
| 1.21 - 1.21.10 | 21 | Mojang mappings, remapping toolchain | not started | not started | not started | not planned | NOT BUILT |
| 1.21.11 | 21 | Mojang mappings, remapping toolchain | **source draft in `fabric/`, never compiled** | not started | not started | not planned | NOT BUILT |
| 26.1 | 25 | unobfuscated, no remapping | not built | not built | not built | not built | NOT BUILT |
| 26.2 | 25 | unobfuscated, no remapping | not built | not built | not built | not built | NOT BUILT |
| 26.3 | 25 | unobfuscated, no remapping | not built | not built | not built | not built | NOT BUILT |

The `core` module is plain Java compiled for Java 21 bytecode, so it can be shared by both the Java 21 and Java 25 lines.

## API facts used by the 1.21.11 Fabric draft (from docs and other mods' ports, not from compiling)

- 1.21.9 and later: `Screen.mouseClicked(MouseButtonEvent, boolean)`, `keyPressed(KeyEvent)`; `KeyEvent.key()` is an int and `InputConstants.KEY_ESCAPE` is 256 (a PR that checked these with javap against the 1.21.11 jar).
- 1.21.11: `Identifier` replaces `ResourceLocation`; HUD drawing uses `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id, (GuiGraphics, DeltaTracker) -> ...)` (Fabric docs for 1.21.11). `HudRenderCallback` is deprecated since Fabric API 0.116.
- 26.2: `Minecraft.setScreen` became `setScreenAndShow` and the `screen` field moved (one mod's release notes), so the 26.x adapter will differ.

## Known incompatibilities
None documented yet; none have been tested.
