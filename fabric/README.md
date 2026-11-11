# FEATHER FPS - Fabric module (1.21.11) - UNCOMPILED DRAFT

**Status: source only. This module has never been compiled, run, or launched.** It was written from the documented
1.21.11 and Fabric API names (sources are listed in `../docs/SUPPORT_MATRIX.md`) in an environment with no network
and no Minecraft. Treat every line as unverified until `gradle build` and a real launch succeed.

## What it contains
- `FeatherFpsClient` - entrypoint: config, mod detection, Right Shift keybind, HUD frame sampler.
- `FeatherScreen` - thin `Screen` that forwards rendering and input to the tested core `FeatherView`.
- `GuiGraphicsRenderer` - maps the core's 6-method `UiRenderer` onto `GuiGraphics`.
- No mixins. No optimization is applied, so every loader-dependent setting shows as INACTIVE in the GUI.

## First-build checklist (do in this order)
1. Confirm versions at https://fabricmc.net/develop and edit `gradle.properties` (all values are unverified).
2. Install a Gradle version that your Loom release supports, JDK 21, then run `gradle build`.
3. Fix compile errors. The most likely trouble spots, because they changed around 1.21.9 to 1.21.11:
   - `KeyMapping.Category.register(Identifier)` and the `KeyMapping` constructor
   - `KeyBindingHelper` package/name
   - `MouseButtonEvent` accessors (`x()`, `y()`, `button()`), `KeyEvent` accessors (`key()`, `modifiers()`) and `CharacterEvent.codepoint()` (search box typing)
   - `GuiGraphics.enableScissor` / `disableScissor` / `drawString` signatures
   - `HudElementRegistry` / `VanillaHudElements` import paths
4. `gradle runClient`: check the game starts, the keybind appears under "FEATHER FPS" in Controls,
   Right Shift opens the screen, Esc and Right Shift close it, Apply writes `config/feather-fps.properties`.
5. In a world, check the dashboard FPS tracks the F3 FPS counter, that nothing is sampled in menus, that typing in the search box works (including capital letters with Right Shift held), and that a Benchmark run produces a result.
6. Only then produce `build/libs/feather-fps-fabric-1.21.11-<version>.jar` and record the result.

## Known limitations of this draft
- Frames are sampled once per HUD draw: no data in the main menu or with the HUD hidden.
- The 26.x lines need their own adapter (rendering and input APIs changed again in 26.1).
