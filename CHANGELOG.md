# Changelog

Newest first. Each Minecraft line has its own jar — install the one that matches your game version.

Jars look like `group_speed_run-2.1.2+1.21.11.jar` (see `gradle.properties` `mod_version=2.1.2+1.21.11`).

## Minecraft version notes

GSR 2.1.0 ships on three Minecraft lines. Features are meant to match; the code underneath had to change because Mojang renamed APIs and rewrote menus.

**For players / pack makers:**

- **1.21.11** — this branch. Original GSR 2.0 line. Yarn mappings, Java 21, Fabric Loader 0.18.4, Fabric API `0.141.2+1.21.11`, Cloth Config `21.11.153`.
- **26.1.2** — official-mappings port. Java 25, Fabric Loader 0.19.2, Fabric API `0.149.0+26.1.2`.
- **26.2** — later official-mappings line. Fabric Loader 0.19.3, Fabric API `0.157.0+26.2`. Pause/title icon rows are a vanilla 26.2 layout.

Pick the jar whose Minecraft number matches the instance. This 1.21.11 jar will not load on 26.1.2 or 26.2.

This branch goes **2.0.0 → 2.1.0**. Intermediate 26.2-only tags (2.0.1–2.0.6) are not separate 1.21.11 releases; the player-facing 2.1.0 UI (icons, fade, Seed Filter, Controls from the square/GSR button) is in 2.1.0 here.

**For developers (code / API):**

- **1.21.11 (Yarn):** `DrawContext` for rendering, `MinecraftClient.setScreen` to open screens, Create World uses Yarn `WorldCreator`. Mixins and class names stay Yarn.
- **26.1.2 (official Mojang names):** `GuiGraphicsExtractor`, `Minecraft.setScreen`, shadowed `worldCreator`.
- **26.2:** `client.gui.setScreen` / `GuiGraphicsExtractor`, Create World `uiState`. Title-screen icons use `Holder.direct` + `ITEM_MODEL` when the item registry is unbound.

---

## Mod versions

### 2.1.2 (Minecraft 1.21.11)

**For players:**

- The 20×20 **GSR** button now has the vanilla icon-button plate behind the letters, matching 26.x.

**For developers:**

- 1.21.11 `PressableWidget.renderWidget` only calls `drawIcon`. Vanilla `ButtonWidget.Text` draws `widget/button` via `drawButton`; GSR's square (and other `ButtonWidget.Text` subclasses) now call that before the label, same as 26.x `extractDefaultSprite`.

### 2.1.1 (Minecraft 1.21.11)

**For players:**

- The square **GSR** button no longer overlaps Options / Quit (title) or Save and Quit (pause). It stays 20x20 like 26.x and sits beside the existing button row.

**For developers:**

- 1.21.11 title language/accessibility icons are on opposite sides of Options/Quit, not a compact 26.x icon strip. Layout parks GSR next to that row instead of recentering the squares over vanilla buttons. Pause always uses the square control (no Save and Quit split).

### 2.1.0 (Minecraft 1.21.11)

**For players:**

- Config item icons show on the title screen instead of crashing or drawing as air.
- GSR title / square buttons fade with the rest of the vanilla menu.
- **Seed Filter** is a full-width Config row: map icon, **ON** by default / **OFF**, green check / red X. Random Create World seeds retry until Overworld + Nether pass; typed seeds are never filtered.
- The GSR square / pause control opens **Controls**, not Config. Opening waits one tick so the menu cannot steal the click. Config sync no longer recreates Controls / Preferences underneath you.

**For developers:**

- Same 2.1.0 UI as the 26.x lines, implemented with Yarn APIs: `DrawContext`, `MinecraftClient.setScreen` deferred one tick (`client.execute`), Create World mixin shadows Yarn `WorldCreator`.
- Item icons skip air / unbound names so Preferences can open on the title screen.

### 2.0.0 (Minecraft 1.21.11)

**For players:**

- Initial public GSR 2.0 for Fabric 1.21.11: shared run timer, Nether / Bastion / Fortress / End / Dragon splits, locators, Controls / Config, run history sync.

**For developers:**

- Yarn 1.21.11, Fabric Loader 0.18.4, Fabric API `0.141.2+1.21.11`. Later 26.x branches port this line to official Mojang names.
