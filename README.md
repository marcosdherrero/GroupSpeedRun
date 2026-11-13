![GSR Icon](images/icon/gsr_icon.png)

# Group Speed Run (GSR)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62b47a?logo=minecraft)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Loader-62b47a)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> A co-op speedrun utility mod for Minecraft that adds a shared run timer, split tracking, run controls, locator HUD tooling, and end-of-run history syncing.

---

## Table of Contents

- [Features](#features)
- [Changelog](#changelog)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Menus & Screens](#menus--screens)
- [Commands & Keybinds](#commands--keybinds)
- [Data & Persistence](#data--persistence)
- [System Architecture](#system-architecture)
- [Database Schema](#database-schema)
- [Networking](#networking)
- [Config Sync Flow](#config-sync-flow)
- [Run History Sync Flow](#run-history-sync-flow)
- [Config Options](#config-options)
- [Build from Source](#build-from-source)
- [License](#license)

---

## Features

- **Shared run timer** — Split milestones for Nether, Bastion, Fortress, End, and Dragon
- **Optional team rules** — Group death and shared health pool
- **In-game controls** — Press `G` for run actions and management
- **Locator HUD** — Configurable icons, colors, scales, and anti-cheat derank control
- **Run Manager** — Participant grouping and exclusion rules
- **Run History** — From title menu with:
  - Run detail view
  - "Stats vs Avg" comparison chart
  - Player multi-select filter (○ / ✔ style)
  - Player skins in charts (fetched from Mojang when displaying)
  - Personal + shared run merge
- **Client sync** — Run history sync between players
- **Quick world flow** — "New GSR World" after victory or failure

---

## Changelog

What changed in each GSR version (player notes plus a short developer/API subsection), including the Minecraft 1.21.11 → 26.1.2 → 26.2 → 26.3 ports, is in [CHANGELOG.md](CHANGELOG.md).

---

## Screenshots

### Main Menu & Controls

| Main Menu | GSR Controls |
|-----------|--------------|
| ![Main Menu](images/menu/gsr_main_menu.png) | ![GSR Controls](images/menu/gsr_controls_menu.png) |

### Timer HUD

| Pre-start | Run Started | Nether Split | Bastion Split | End Split |
|-----------|--------------|---------------|---------------|-----------|
| ![Pre-start](images/timer/gsr_prestart.png) | ![Run Started](images/timer/gsr_run_started.png) | ![Nether Split](images/timer/gsr_nether_split.png) | ![Bastion Split](images/timer/gsr_bastion_split.png) | ![End Split](images/timer/gsr_end_split.png) |

### Victory & Fail

| Victory Broadcast 1 | Victory Broadcast 2 | Fail Broadcast |
|--------------------|---------------------|----------------|
| ![Victory 1](images/victory_fail/gsr_victory_broadcast_1.png) | ![Victory 2](images/victory_fail/gsr_victory_broadcast_2.png) | ![Fail Broadcast](images/victory_fail/gsr_fail_broadcast.png) |

### Locators

| Bastion Locator | Stronghold Locator |
|----------------|---------------------|
| ![Bastion Locator](images/locators/gsr_locator_example_bastion.png) | ![Stronghold Locator](images/locators/gsr_locator_example_stronghold.png) |

### Run History

| Run Info | Run Graph | Player Graph | Player Graph 2 | Player Graph Run Selection |
|----------|-----------|---------------|----------------|----------------------------|
| ![Run Info](images/run_history/gsr_run_info_screen.png) | ![Run Graph](images/run_history/gsr_run_graph_screen.png) | ![Player Graph](images/run_history/gsr_player_graph_screen.png) | ![Player Graph 2](images/run_history/gsr_player_graph_screen_2.png) | ![Player Graph Selection](images/run_history/gsr_player_graph_run_selection.png) |

### Config & Keybinds

| Config 1 | Config 2 | Config 3 | Config 4 | Keybinds |
|----------|----------|----------|----------|----------|
| ![Config 1](images/config/gsr_config_1.png) | ![Config 2](images/config/gsr_config_2.png) | ![Config 3](images/config/gsr_config_3.png) | ![Config 4](images/config/gsr_config_4.png) | ![Keybinds](images/config/gsr_keybinds.png) |

---

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 26.3 |
| Java | 25+ |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.163.0+26.3 |
| Cloth Config | 26.3.159 |

**Optional:** Mod Menu (`com.terraformersmc:modmenu:17.0.0-beta.1`) for in-game mod configuration.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Place these jars in your `mods` folder:
   - GSR mod jar
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Cloth Config](https://modrinth.com/mod/cloth-config)
3. Launch the game or server.

> **Multiplayer:** All players should install the mod for full run-history sync and UI parity.

---

## Quick Start

1. **Start a run** — Create or join a world; use the GSR Controls screen (`G`) to manage runs.
2. **Track splits** — Timer and milestones update as you progress through Nether, Bastion, Fortress, End, and Dragon.
3. **View history** — From the main menu, click **GSR Run History** to browse completed runs and stats.
4. **Configure** — Press `G` then `C`, or run `/gsr config`, to adjust HUD, locators, and preferences.

---

## Menus & Screens

### Main Menu Integration

On the title screen, GSR inserts and reflows buttons:

| Order | Buttons |
|-------|---------|
| 1 | Singleplayer (full width) |
| 2 | Multiplayer (full width) |
| 3 | GSR Run History \| Minecraft Realms (half-width each) |
| 4 | Mods (full width, if Mod Menu is present) |
| 5 | Options \| Quit Game (half-width each) |

### Screen Reference

| Screen | Access | Purpose |
|--------|--------|---------|
| **GSR Run History** | Main menu → GSR Run History | Browse runs, inspect details, compare stats, filter by player; player skins shown in charts |
| **GSR Controls** | `G` or `/gsr controls` | Main hub: run actions, status, run manager, locators, preferences |
| **GSR Preferences** | `/gsr config`, `G` + `C`, or Mod Menu | HUD visibility/mode/scale, locator visuals, styling |
| **Run Manager** | GSR Controls → Run Manager | Manage participant pools (group-death, shared-health, excluded) |
| **Locators** | GSR Controls → GSR Locators | Toggle/clear locator types, review run state |
| **Status** | GSR Controls → View Run Status | Current run summary and server state |
| **New World Confirm** | `N` after victory/failure | Quick creation of next GSR world with suggested naming |

### Navigation Map

```
Main Menu → GSR Run History
In-World → G → GSR Controls
GSR Controls → View Run Status → Status Screen
GSR Controls → Run Manager → Participant Management
GSR Controls → GSR Locators → Locators Screen
GSR Controls → GSR Preferences → Config
In-World (after victory/failure) → N → New World Confirm
Anywhere → /gsr controls | /gsr config
```

---

## Commands & Keybinds

### Commands

| Command | Description |
|---------|-------------|
| `/gsr config` | Opens GSR preferences |
| `/gsr controls` | Opens in-game GSR controls |

### Keybinds

| Action | Default |
|--------|---------|
| New GSR World | `N` |
| Open GSR Controls | `G` |
| Open GSR Config (hold G) | `C` |
| Show GSR HUD (toggle) | `V` |
| Show GSR HUD (hold) | `Tab` |

All keybinds are configurable in **Controls** → **GSR**.

---

## Data & Persistence

### World-scoped

| Path | Purpose |
|------|---------|
| `world/data/gsr/groupspeedrun_world.json` | World config |
| `world/data/gsr/players.json` | Per-player HUD config |
| `world/data/gsr/stats.json` | Stats |
| `world/data/gsr/db/runs.json` | Run datastore |
| `world/data/gsr/db/run_participants.json` | Participant data |
| `world/data/gsr/db/run_player_snapshots.json` | Player snapshots |
| `world/data/gsr/db/runs/<runId>.json` | Per-run file (O(1) lookup) |

### Global (client / game dir)

| Path | Purpose |
|------|---------|
| `gsr_folder/personal_runs/` | Personal run history |
| `gsr_folder/shared_runs/` | Shared run history |
| `gsr_folder/run_count.json` | Quick-world run count |
| `gsr_folder/snapshots/<worldName>/` | World snapshot for restore |

### Runtime Behavior

- World config loads on server start and autosaves every 5 seconds (100 ticks)
- Run timer freezes on server stop if a run is active
- Config and player profile sync on join and periodic server sync
- Locator usage can mark run as deranked when anti-cheat is enabled
- Locator gate timing: 30-minute unlock windows for non-admin usage where applicable

---

## System Architecture

```mermaid
flowchart TB
    subgraph Server["SERVER (GSRMain, GSRNetworking)"]
        CONFIG[GSRMain.CONFIG<br/>GSRConfigWorld]
        PROFILE[GSRProfileManager<br/>Per-player config by UUID]
        DATASTORE[GSRDataStore<br/>Run storage]
        RUNSYNC[GSRRunSyncManager<br/>Run ID exchange & relay]
        CONFIGSYNC[GSRConfigSync<br/>Config broadcast]
    end

    subgraph Client["CLIENT (GSRClient)"]
        clientConfig[clientWorldConfig<br/>Synced copy of world run state]
        playerConfig[PLAYER_CONFIG<br/>Synced per-player HUD config]
        HUD[GSR HUD<br/>Timer, Splits, Locators]
        runLoader[GSRSharedRunLoader<br/>Personal + shared runs]
    end

    subgraph Network["Fabric Play Networking"]
        S2C[S2C Payloads]
        C2S[C2S Payloads]
    end

    CONFIG --> CONFIGSYNC
    PROFILE --> CONFIGSYNC
    CONFIGSYNC --> S2C
    S2C --> clientConfig
    S2C --> playerConfig

    C2S --> CONFIG
    C2S --> PROFILE
    C2S --> DATASTORE
    C2S --> RUNSYNC

    clientConfig --> HUD
    playerConfig --> HUD

    runLoader --> DATASTORE
    runLoader --> RUNSYNC
```

---

## Database Schema

### Entity Relationship Diagram

```mermaid
erDiagram
    GSRRunSaveState ||--|| GSRRunRecord : "contains"
    GSRRunSaveState ||--o{ GSRRunParticipant : "contains"
    GSRRunSaveState ||--o{ GSRRunPlayerSnapshot : "contains"
    GSRRunRecord ||--o{ GSRRunParticipant : "runId"
    GSRRunRecord ||--o{ GSRRunPlayerSnapshot : "runId"

    GSRRunRecord {
        string runId PK
        string worldName
        long startMs
        long endMs
        string status
        int participantCount
        long timeNether
        long timeBastion
        long timeFortress
        long timeEnd
        long timeDragon
        boolean deranked
    }

    GSRRunParticipant {
        string runId FK
        string playerUuid
        string playerName
    }

    GSRRunPlayerSnapshot {
        string runId FK
        string playerUuid
        string playerName
        float damageDealt
        float damageTaken
        float dragonDamage
        int enderPearls
        int blazeRods
        int blocksPlaced
        int blocksBroken
        float distanceMoved
    }

    GSRRunSaveState {
        GSRRunRecord run
        list participants
        list snapshots
    }
```

### Transactional Structure

```
GSRRunSaveState (atomic per run)
├── run (GSRRunRecord)
│   ├── runId, worldName, startMs, endMs
│   ├── status (VICTORY | FAIL | IN_PROGRESS)
│   ├── timeNether, timeBastion, timeFortress, timeEnd, timeDragon
│   └── participantCount, deranked, runDifficulty
├── participants (List<GSRRunParticipant>)
│   └── runId, playerUuid, playerName
└── snapshots (List<GSRRunPlayerSnapshot>)
    └── runId, playerUuid, playerName
        damageDealt, damageTaken, dragonDamage
        enderPearls, blazeRods, blocksPlaced, blocksBroken
        distanceMoved, entityKills, ...
```

### Storage Layout

```mermaid
flowchart TB
    subgraph World["world/data/gsr/ (world-colocated)"]
        worldConfig[groupspeedrun_world.json]
        players[players.json]
        stats[stats.json]
        db[db/]
    end

    subgraph DB["db/"]
        runsJson[runs.json]
        participantsJson[run_participants.json]
        snapshotsJson[run_player_snapshots.json]
        runsDir[runs/]
    end

    subgraph RunsDir["runs/"]
        run1["run_<runId>.json"]
    end

    subgraph GSRFolder["gsr_folder/ (game dir)"]
        personal[personal_runs/]
        shared[shared_runs/]
        runCount[run_count.json]
        snapshots[snapshots/]
    end

    worldConfig --> World
    players --> World
    stats --> World
    db --> World
    runsJson --> DB
    participantsJson --> DB
    snapshotsJson --> DB
    runsDir --> DB
    run1 --> RunsDir
```

---

## Networking

### Network Payload Tree

```mermaid
flowchart TB
    subgraph S2C["Server → Client (S2C)"]
        configSync[GSRConfigPayload]
        openScreen[GSROpenScreenPayload]
        splitAchieved[GSRSplitAchievedPayload]
        victory[GSRVictoryCelebrationPayload]
        runComplete[GSRRunCompletePayload]
        runIdsReq[GSRRunIdsRequestPayload]
        runIds[GSRRunIdsPayload]
        runReqBroadcast[GSRRunRequestBroadcastPayload]
        runData[GSRRunDataPayload]
        playerList[GSRPlayerListPayload]
    end

    subgraph C2S["Client → Server (C2S)"]
        configSyncC[GSRConfigPayload]
        worldConfig[GSRWorldConfigPayload]
        runAction[GSRRunActionPayload]
        locatorAction[GSRLocatorActionPayload]
        runManagerReq[GSRRunManagerRequestPayload]
        runManagerUpdate[GSRRunManagerUpdatePayload]
        runIdsC[GSRRunIdsPayload]
        runRequest[GSRRunRequestPayload]
        runDataC[GSRRunDataPayload]
    end

    Fabric[Fabric Play Networking]
    S2C --> Fabric
    C2S --> Fabric
```

### Payload Reference

| Payload | ID | Direction | Purpose |
|---------|-----|-----------|---------|
| `GSRConfigPayload` | `gsr:config_sync` | S2C, C2S | World run state + per-player HUD config |
| `GSRWorldConfigPayload` | `gsr:world_config` | C2S | Host-only: anti-cheat, auto-start, locator mode |
| `GSRRunActionPayload` | `gsr:run_action` | C2S | Start, pause, resume, reset |
| `GSRLocatorActionPayload` | `gsr:locator_action` | C2S | Toggle fortress/bastion/stronghold/ship locators |
| `GSROpenScreenPayload` | `gsr:open_screen` | S2C | Server requests client to open Controls or Config |
| `GSRSplitAchievedPayload` | `gsr:split_achieved` | S2C | Notify client a split was achieved |
| `GSRVictoryCelebrationPayload` | `gsr:victory_celebration` | S2C | Notify client of victory |
| `GSRRunCompletePayload` | `gsr:run_complete` | S2C | Broadcast full run save state to all clients |
| `GSRRunManagerRequestPayload` | `gsr:run_manager_request` | C2S | Client requests participant lists |
| `GSRRunManagerUpdatePayload` | `gsr:run_manager_update` | C2S | Client sends updated group-death/shared-health/excluded |
| `GSRPlayerListPayload` | `gsr:player_list` | S2C | Server sends online player list |
| `GSRRunIdsRequestPayload` | `gsr:run_ids_request` | S2C | Server asks all clients for their run IDs |
| `GSRRunIdsPayload` | `gsr:run_ids` | C2S, S2C | Exchange run IDs for sync |
| `GSRRunRequestPayload` | `gsr:run_request` | C2S | Client requests full run data for missing IDs |
| `GSRRunRequestBroadcastPayload` | `gsr:run_request_broadcast` | S2C | Server asks other clients to provide run data |
| `GSRRunDataPayload` | `gsr:run_data` | C2S, S2C | Run NBT; client sends, server forwards to requester |

---

## Config Sync Flow

```mermaid
sequenceDiagram
    participant Player
    participant Server
    participant ConfigSync
    participant Client

    Player->>Server: Join
    Server->>ConfigSync: syncConfigWithPlayer(player)
    ConfigSync->>Client: GSRConfigPayload (world + player config)
    Client->>Client: Update clientWorldConfig, PLAYER_CONFIG

    loop Periodic
        Server->>ConfigSync: syncConfigWithAll()
        ConfigSync->>Client: GSRConfigPayload
    end

    Player->>Client: Save config (Mod Menu)
    Client->>Server: GSRConfigPayload (C2S)
    Server->>Server: Persist via GSRProfileManager
```

---

## Run History Sync Flow

```mermaid
sequenceDiagram
    participant Joiner
    participant Server
    participant OtherClients

    Joiner->>Server: Join
    Server->>OtherClients: GSRRunIdsRequestPayload
    OtherClients->>Server: GSRRunIdsPayload (their run IDs)
    Server->>Server: Union of IDs + server runs
    Server->>Joiner: GSRRunIdsPayload (all known IDs)

    Joiner->>Joiner: Find missing IDs
    Joiner->>Server: GSRRunRequestPayload (missing IDs)
    Server->>OtherClients: GSRRunRequestBroadcastPayload
    OtherClients->>Server: GSRRunDataPayload (for each run they have)
    Server->>Joiner: GSRRunDataPayload (forwarded runs)
    Joiner->>Joiner: Save to shared via GSRSharedRunLoader
```

---

## Config Options

### Per-Player Config (`GSRConfigPlayer`)

| Option | Type | Default | Notes |
|--------|------|---------|-------|
| hudOverallScale | float | 1.0 | 0.5–2.5 |
| timerScale | float | 1.0 | 0.5–2.5 |
| hudMode | int | 0 | 0 = Full, 1 = Condensed |
| hudVisibility | int | 1 | 0 = Toggle, 1 = Hold to show |
| timerHudOnRight | bool | true | Timer on right vs left |
| locateHudOnTop | bool | true | Locator bar top vs bottom |
| locateScale | float | 1.0 | 0.5–2.5 |
| barWidth | int | 200 | 80–400 |
| barHeight | int | 16 | 8–48 |
| fortressItem | string | etc. | Registry IDs |
| fortressColor | int | Theme | 0xRRGGBB |
| **Where to change** | | | GSR Preferences (G+C or Mod Menu) |

### Per-World Config (`GSRConfigWorld`)

| Option | Type | Purpose |
|--------|------|---------|
| startTime / frozenTime | long | Timer state |
| isTimerFrozen | bool | Pause state |
| groupDeathEnabled | bool | Group death rule |
| sharedHealthEnabled | bool | Shared health pool |
| groupDeathParticipants | Set&lt;UUID&gt; | Participant sets |
| timeNether … timeDragon | long | Split times (ms) |
| antiCheatEnabled | bool | Locator use deranks runs |
| locatorNonAdminMode | int | 0 = Never, 1 = Always, 2 = 30 min post-split |
| **Where to change** | | Run Manager, Locators, GSR Preferences |

---

## Build from Source

```bash
./gradlew build
```

**Windows (PowerShell):**

```powershell
.\gradlew build
```

Output jars are in `build/libs/`.

---

## License

MIT — see the packaged `LICENSE` file for details.
