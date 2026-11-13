# Changelog

Newest first. Each Minecraft line has its own jar — install the one that matches your game version.

Jars look like `gsr-2.1.0+26.3-Minecraft26.3.jar`. GitHub tags look like `v2.1.0-mc26.3`.

## Minecraft version notes

GSR 2.1.0 ships on four Minecraft lines. Features are meant to match; the code underneath had to change because Mojang renamed APIs and rewrote menus.

**For players / pack makers:**

- **1.21.11** — original GSR 2.0 line. Yarn mappings, Java 21, Fabric Loader 0.18.4, Fabric API `0.141.2+1.21.11`, Cloth Config `21.11.153`.
- **26.1.2** — first official-mappings port. Java 25, Fabric Loader 0.19.2, Fabric API `0.149.0+26.1.2`, Cloth Config `26.1.154`.
- **26.2** — Java 25, Fabric Loader 0.19.3, Fabric API `0.157.0+26.2`, Cloth Config `26.2.155`. Pause/title icon rows are a vanilla 26.2 layout, which is why the square **GSR** button exists here.
- **26.3** — this branch. Java 25, Fabric Loader 0.19.5, Fabric API `0.163.0+26.3`, Cloth Config `26.3.159`, Mod Menu `21.0.0`.

Pick the jar whose Minecraft number matches the instance. A 26.3 jar will not load on 26.2, 26.1.2, or 1.21.11.

**For developers (code / API):**

- **1.21.11 (Yarn):** `DrawContext`, `MinecraftClient.setScreen`, `WorldCreator` on Create World, Yarn mixin / class names.
- **26.1.2 (official Mojang names):** `GuiGraphicsExtractor`, `Minecraft.setScreen`, Create World uses a shadowed `worldCreator` (`WorldCreationUiState`). Many Yarn identifiers were remapped (`templateIdString` → `templateName`, NBT getters, death / locate / screen helpers).
- **26.2:** screens open with `client.gui.setScreen` (not `client.setScreen`). Drawing still uses `GuiGraphicsExtractor`. Create World seed filter reads `uiState`. Title-screen item icons need `Holder.direct` + `DataComponents.ITEM_MODEL` because registry item components are unbound until a world exists (`GSRItemStacks`; `isUsable` = non-air).

---

## Mod versions

### 2.1.0 (Minecraft 26.3)

jar `gsr-2.1.0+26.3-Minecraft26.3.jar`

Same 2.1.0 features as 26.2, retargeted to Minecraft 26.3 (Fabric API `0.163.0+26.3`, Cloth Config `26.3.159`, Mod Menu `21.0.0`).

### 2.1.0 (Minecraft 26.2)

GitHub: [v2.1.0-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.1.0-mc26.2) · jar `gsr-2.1.0+26.2-Minecraft26.2.jar`

**For players:**

- Config item icons (maps, locator items, and similar) show on the title screen instead of crashing or drawing as air.
- The square **GSR** button and the title-screen GSR button fade with the rest of the vanilla menu.
- **Seed Filter** is a full-width Config row: map icon, **ON** by default / **OFF**, green check / red X.
- The square **GSR** button opens **Controls**, not Config. Opening waits one tick so the pause menu cannot steal the click.

**For developers:**

- Unbound registries: build a `Holder.direct` stack with `ITEM_MODEL` (and skip air names) so Preferences can open before a world exists.
- Menu widgets apply vanilla menu alpha.
- Square GSR → Controls; `setScreen` deferred to the next client tick. Config sync packets must not recreate Controls / Preferences.

### 2.0.6 (Minecraft 26.2)

GitHub: [v2.0.6-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.6-mc26.2)

**For players:**

- Config actually opens from the square button / pause row (a vanilla icon could steal the click).
- Run reset no longer hangs on **Saving World**.
- Long confirmation text wraps so buttons stay on screen.

**For developers:**

- 26.2 `getChildAt` returns the first overlapping widget; GSR hit-tests the square button first.
- `disconnectWithSavingScreen` from the play-packet handler deadlocked the integrated server; reset now disconnects like vanilla Save and Quit on the next tick, then restores after `session.lock` is released (`releaseTemporarilyAndRun` fallback on the server).

### 2.0.5 (Minecraft 26.2)

GitHub: [v2.0.5-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.5-mc26.2)

**For players:**

- Config toggles (including Seed Filter) register clicks again after the 26.2 GUI rewrite.
- Closing Config / Controls unpauses and re-grabs the mouse.
- Reset restores the original world backup (regions, entities, `level.dat`), not only spawn + empty inventory. Reset aborts with a chat error if there is no backup.

**For developers:**

- `mouseClicked`’s `captured` flag is a **double-click** marker on 26.2, not “already handled”. Hover-stability no longer swallows clicks when hover tracking lags `extractRenderState`.
- Close path uses `onClose` → `gui.setScreen(null)`. Snapshot is taken while the run is still primed, skipping `session.lock`.

### 2.0.4 (Minecraft 26.2)

GitHub: [v2.0.4-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.4-mc26.2)

**For players:**

- Random-seed Create World retries until Overworld + Nether look usable (village near spawn, ruined portal or lava, nearby bastion, nearby fortress). Typed seeds are never filtered.
- Seed Filter defaults **ON**, saved in `seed_filter.json` so the title-screen choice works before a world exists; the host setting syncs to clients.

**For developers:**

- Placement checks only (no full world gen). Filter gated on blank/random seed. Create World mixin uses `uiState` on 26.2.

### 2.0.3 (Minecraft 26.2)

GitHub: [v2.0.3-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.3-mc26.2)

**For players:**

- Completed run times survive world reload and jar updates.
- Square **GSR** letters no longer fill the whole 20×20 face (inner padding).

**For developers:**

- 26.x NBT getters are type-strict (JSON 0/1 flags and int-sized split times). World config is written atomically on split complete, run complete, pause, periodic save, and leave. Priming must not overwrite a completed HUD file.

### 2.0.2 (Minecraft 26.2)

GitHub: [v2.0.2-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.2-mc26.2)

**For players:**

- End ship locator finds a ship that is already on screen.
- Advancement-style toast when a locator finds nothing.
- Pause / title control is a square **GSR** button on the vanilla icon row.

**For developers:**

- Scan loaded End structure pieces first (`end_city/ship` / `templateName` `ship`). Ignore vanilla `INVALID_START` so standing outside a city does not abort the search.

### 2.0.1 (Minecraft 26.2)

GitHub: [v2.0.1-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.1-mc26.2)

**For players:**

- Fixes a crash on startup leftover from the 26.1.2 → 26.2 name remap.

**For developers:**

- Yarn mixin leftovers remapped; `TemplateStructurePiece.templateIdString` is official `templateName`.

### 2.0.0 (Minecraft 26.2)

GitHub: [v2.0.0-mc26.2](https://github.com/marcosdherrero/GroupSpeedRun/releases/tag/v2.0.0-mc26.2)

**For players:**

- First 26.2 build of GSR 2.0 (shared timer, splits, locators, run history). Same idea as 1.21.11 / 26.1.2; this jar is for Minecraft 26.2 only.

**For developers:**

- Retarget: Fabric Loader 0.19.3, Fabric API `0.157.0+26.2`. Screens, dyes, HUD, and toasts adapted to 26.2 Gui APIs (`GuiGraphicsExtractor`, `client.gui.setScreen`).
