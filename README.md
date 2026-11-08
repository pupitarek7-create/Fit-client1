# Fit Client (Fabric, Minecraft 1.21.11)

Original code. Not based on Anubis or 67Client code or assets.

## Build
1. Generate a fresh Fabric project at https://fabricmc.net/develop/template for 1.21.11 (it gives you the correct
   `gradle.properties`, `build.gradle`, `settings.gradle` and Gradle wrapper with current Loom / Fabric API versions).
2. Copy the `src/` folder from here over the template's `src/` (replace `fabric.mod.json`, delete the template's example classes/mixins
   and any mixin entries it lists).
3. `./gradlew build` -> jar in `build/libs/`.

## Use
- Right Shift: open/close menu. F7: toggle Sus Chunks.
- Enable "Sus Chunks" (and "Sus HUD" for the on-screen list) in the Modules tab.
- Sus Chunks tab: live chunk map (you = white square) and ranked list with coordinates.

## If it doesn't compile
The most likely spots are Minecraft 1.21.9+ API changes:
- `ClickGui` bottom section: `Click`, `KeyInput`, `CharInput` classes and their accessors (`click.x()`, `input.key()`, `input.asString()`).
- `HudRenderCallback` (may be deprecated in favour of `HudElementRegistry`).
- Yarn names like `BlockEntityType.MOB_SPAWNER`, `ChunkSection.hasAny`, `chunk.getSectionArray()`.
Send the compiler errors and they can be fixed quickly.
