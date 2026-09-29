# Chuying Proxy Play (褚嬴代打)

> "Old man toying with the child" — let bundled strong engines play for you and crush the maids of Touhou Little Maid.

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-orange)
![Forge](https://img.shields.io/badge/Forge-47%2B-green)
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-red)](https://www.curseforge.com/minecraft/mc-mods/chuying-proxy-play)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

An addon for [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) on **Forge 1.20.1** that detects when it's your turn in the maid's board games and automatically makes a move with a bundled chess engine.

> **Pure client, no server mod needed** — engines calculate locally, then your right-click on the board is simulated through vanilla interaction, so the server does **NOT** need this mod installed (works on other people's servers, no network channel mismatch).
>
> **No subprocesses (2.0)** — all three engines are JNI native libraries (`.dll` / `.so` / `.dylib`) running **inside the game process**. Nothing is spawned, and the "custom engine path" option is gone.

## Features

| Feature | Description |
|---|---|
| **Pure Client** | Server needs nothing extra; works in singleplayer & on any multiplayer server |
| **No subprocess** | Engines are JNI native libraries running in-process; no exe is ever spawned |
| **One-key Proxy Play** | Toggle on/off with **K**; keybind remappable in Controls |
| **4 Think Strength levels** | LOW / DEFAULT / HIGH / MAX — adjustable in-game, applied to the very next move |
| **Avoid Draw (Chess)** | Push Stockfish to actively seek the win instead of settling for a draw |
| **Auto-extract engines** | Bundled Windows / Linux / macOS native libraries, extracted to `config/chuying/engines/` on first run |
| **ChessPVP support** | With the `ChessPVP` addon installed, PVP games are played for you too: only your own side is played |
| **Multi-language** | Simplified Chinese, English, 日本語 |
| **Debug HELL** | Force the Gomoku maid to HELL difficulty (client-only, for testing) |

## Supported Games & Engines

| Game | Engine | Protocol | Notes |
|---|---|---|---|
| Gomoku | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | bundled in the jar |
| Chinese Chess (Xiangqi) | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | bundled in the jar |
| International Chess | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | bundled in the jar |

## Platform Packages

One build produces three jars — pick the one for your OS (don't mix them up):

| File | OS |
|---|---|
| `Chuying Proxy Play<ver>-Forge-1.20.1-windows.jar` | Windows |
| `Chuying Proxy Play<ver>-Forge-1.20.1-linux.jar` | Linux (x86-64, AVX2) |
| `Chuying Proxy Play<ver>-Forge-1.20.1-macos.jar` | macOS (Apple Silicon) |

> Engines are extracted automatically to `config/chuying/engines/` on first run.

## Requirements

- Forge `47+` (Minecraft 1.20.1)
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) — needed on both client & server (the boards come from it)

## Installation

1. Install Forge 1.20.1 and Touhou Little Maid
2. Download the jar matching your **OS** from Releases
3. Put it into `.minecraft/mods/`
4. Launch the game

## Usage

- Press **K** to toggle proxy play (remappable in Options → Controls)
- Walk up to a board, **keep your main hand empty** (the board requires an empty hand), and moves are made for you
- The same works with the `ChessPVP` addon: it only plays once both players joined and it is your side's turn (red/white or black); spectators stay out
- Settings → Mods → Chuying Proxy Play → Config:
  - **Think Strength**: LOW (sandbag) → DEFAULT → HIGH → MAX (higher = steadier, fewer blunders)
  - **Avoid Draw** (Chess only): OFF / GENTLE / ACTIVE / MAX — avoid forced draws

## How It Works (Pure Client)

- The engine calculates the move locally, then the move is converted back into a 3D board position and sent as a **vanilla** `ServerboundUseItemOnPacket` (simulated right-click)
- The server just sees a player clicking the board normally and lets the installed TLM handle the move — **zero server-side changes or dependencies**
- Xiangqi/Chess use two-step clicks (select piece → move); Gomoku is resolved through its own multi-part board offsets
- Engine side: each engine's `main()` is renamed at build time and linked into a JNI library, with `std::cin`/`std::cout` redirected to in-memory queues, so the full UCI/pbrain loop runs **inside the game process — no process is ever created**

## Internationalization

UI and hints support Simplified Chinese, English and 日本語, switching automatically with the game language.

## For Developers

Engine binaries are not committed to git; the GitHub Actions `native-build` workflow clones the engine sources on CI, compiles them into per-platform JNI libraries and uploads them as artifacts. Download those into the mod's resource folder.

```bash
# 1. Put the CI outputs (chuying_*.dll|so|dylib) into src/main/resources/engines/{windows,linux,macos}/
#    weights live in engines/shared/
# 2. Build all three platform jars at once
./gradlew build
```

Outputs in `build/libs/`: `chuying-<version>.jar` (skeleton) + `Chuying Proxy Play<version>-Forge-1.20.1-{windows,linux,macos}.jar`.

## License

**GPL-3.0-only** — the bundled engines (Pikafish / Stockfish / Rapfi) are GPL-3.0 as well; because they are now linked into the same process, the combined work is distributed under GPL-3.0. Exact engine versions/commits and every build-time patch are listed in `THIRD_PARTY_LICENSES.txt` and in the `BUILD_INFO.txt` shipped next to the native libraries.
