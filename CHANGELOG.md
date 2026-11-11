# Changelog

## 0.2.0-dev (unreleased)
- Complete visual redesign of the settings screen: flat square panel, neutral graphite palette with one soft cyan accent (violet only for
  unsaved changes), sidebar grouped with hairlines, settings as plain rows separated by hairlines, dashboard as a two-column metric list
  (single column when narrow) instead of a grid of cards, flat buttons/switches/sliders with aligned columns, text tabs for the graph.
- Removed the "by KingBrezz" line from the GUI (the credit stays in the mod metadata); a test now fails if any page shows an author credit.
- Sidebar adapts to very small windows instead of overflowing.
- Stutter detection (frames at least 2x the average and at least 20 ms), worst frame, 0.1% low, and a slowest-frame-per-slice series for the frame-time graph.
- Built-in A/B Benchmark (15/30/60 s, last 5 runs, noise-aware comparison) so every performance claim can be measured, including Sodium on/off.
- Dashboard: stutter and 0.1% low cards, FPS / frame-time graph toggle, honest "N/6 optimizations active" counter.
- Settings: search across all settings (Ctrl+F), "Reset this page", sidebar no longer implies a section while searching.
- Tests: 31 core + 29 UI, all executed and passing; new logic mutation-checked (one test gap found and closed).
- Settings screen logic in `core/ui`: 12 sections, sidebar navigation, dashboard with stat cards, memory bar and FPS history graph,
  setting rows (toggle, slider with +/- buttons, cycle button), tooltips, scrolling with draggable scrollbar, pending changes with
  Apply / Undo, profile cards, reset, export, import, restore latest backup, compatibility page, about page.
- Settings with no implementation show INACTIVE and are disabled; settings another mod already handles are disabled.
- 22 UI tests (all executed, passing); mutation-checked.
- Offscreen layout previews in `docs/preview/` (synthetic data, labelled).
- Fabric 1.21.11 adapter draft in `fabric/` (never compiled).

## 0.1.0-core (unreleased)
- Loader-independent core: validated config with generated defaults, corruption recovery, atomic saves,
  rotating backups, export/import and a migration mechanism.
- Profiles PERFORMANCE, BALANCED, QUALITY, LOW_END, CUSTOM that change real configuration values.
- Real frame-time statistics (FPS, average, 1% low, frame time, FPS history series), JVM memory and
  process CPU readings, memory-pressure classification.
- Compatibility detection for 10 known optimization/graphics mods.
- GUI navigation model (12 sections).
- 24 core tests, all executed and passing on JDK 21.
- No mod JAR exists yet. No Minecraft integration, GUI rendering or loader entrypoints exist yet.
