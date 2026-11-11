# Next steps (what remains to be built)

Done and tested: core, and the whole settings screen logic including benchmark and search (`core/ui`; 60 tests in total).
Written but never compiled: the Fabric 1.21.11 adapter in `fabric/`.

Everything below needs an environment with network access, Gradle, and the ability to launch Minecraft.

1. **Build the Fabric draft.** Follow `fabric/README.md`: confirm versions, `gradle build`, fix compile errors, `gradle runClient`.
   Verify the keybind, the screen, Apply writing the config, and that dashboard FPS matches the F3 counter.
   Replace the offscreen previews in `docs/preview/` with real screenshots.
2. **Wire the LOADER settings one at a time**, each verified in-game, then add its key to the `activeLoaderKeys` set passed to
   `ViewEnvironment` (the GUI enables the control automatically): `fps.menuLimit`, `fps.backgroundLimit`, `fps.dynamic`,
   `rendering.skipOffscreenBlockEntities`, `particles.reduction`, `entities.decorativeDistance`, `monitor.graphVisible`.
   Mixins are version-specific and a wrong target crashes the game, so each needs a launch test.
3. **Measure.** Before claiming any optimization, use the built-in Benchmark (Performance page) with and without it on a fixed scene, repeat each a few times, and record the numbers. Do the same with Sodium to see its real effect on your machine.
4. **Forge, NeoForge, then Quilt on 26.x**, then the other 1.21.x versions and the 26.x lines (each needs its own adapter
   for input and drawing APIs; the core and `core/ui` are shared). Test every build independently.
5. **Choose and add a LICENSE.**
