# Chuying Proxy Play (褚嬴代打)

> "Old man toying with the child" — let strong engines play for you and crush the maids of Touhou Little Maid.

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-orange)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

An addon for [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) on **NeoForge 1.21.1** that detects when it's your turn in the maid's board games and automatically makes a move with a bundled chess engine.

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
| **Shogi support** | With the [tlm_shogi](https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-shogi) addon installed, shogi is played for you too (using its bundled Sunfish, zero extra size) |
| **ChessPVP support** | With the [ChessPVP](https://modrinth.com/mod/tlmcp-chesspvp) addon installed, PVP games are played for you too: only your own side is played (requires that addon's sneak-click interaction) |
| **Go support** | With [TouhouGO](https://github.com/moyinsky/TouhouGO) installed: 15×15 Go with area scoring, played by [GNU Go](https://www.gnu.org/software/gnugo/) running **in-process** |
| **"Report a move" (J)** | At a Go board with an empty hand, hold **J** for 1.5 s → the board is reset, one stone is played, the maid's reply is judged a pass and you pass too → double pass → area scoring declares **you** the winner |
| **Multi-language** | Simplified Chinese, English, 日本語 |
| **Debug HELL** | Force the Gomoku maid to HELL difficulty (client-only, for testing) |

## Supported Games & Engines

| Game | Engine | Protocol | Notes |
|---|---|---|---|
| Gomoku | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | bundled in the jar |
| Chinese Chess (Xiangqi) | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | bundled in the jar |
| International Chess | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | bundled in the jar |
| Shogi | Sunfish bundled with `tlm_shogi` | reflection | not shipped here; needs the addon installed |
| Go | [GNU Go](https://www.gnu.org/software/gnugo/) 3.8 | engine C API (called directly, in-process) | bundled in the jar; the board comes from TouhouGO |

## Platform Packages

One build produces three jars — pick the one for your OS (don't mix them up):

| File | OS |
|---|---|
| `Chuying.Proxy.Play<ver>-NeoForge-1.21.1-windows.jar` | Windows |
| `Chuying.Proxy.Play<ver>-NeoForge-1.21.1-linux.jar` | Linux (x86-64, AVX2) |
| `Chuying.Proxy.Play<ver>-NeoForge-1.21.1-macos.jar` | macOS (Apple Silicon) |

> Engines are extracted automatically to `config/chuying/engines/` on first run.

> Need the Forge 1.20.1 or Fabric 1.20.1 build? Pick the matching branch / jar on the Releases page.

## Requirements

- NeoForge `21.1.0+` (Minecraft 1.21.1)
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) ≥ `1.3.0` — needed on both client & server (the boards come from it)
- **[TouhouGO](https://github.com/moyinsky/TouhouGO) (optional, only for Go)** — the TLM Go-board addon; install it on both client and server

## Installation

1. Install NeoForge 1.21.1 and Touhou Little Maid
2. Download the jar matching your **loader + OS** from Releases
3. Put it into `.minecraft/mods/`
4. Launch the game

## Usage

- Press **K** to toggle proxy play (remappable in Options → Controls)
- Walk up to a board, **keep your main hand empty** (the board requires an empty hand), and moves are made for you
- The same works for shogi boards (needs the `tlm_shogi` addon); promotion choices are answered automatically
- The same works with the `ChessPVP` addon: it only plays once both players joined and it is your side's turn (red/white or black); spectators stay out
- With `TouhouGO` installed, Go works too: **do not hold sneak while it plays** (sneak + empty-hand click means "pass"; proxy play waits for you to release it), and when the engine wants to pass it passes for you
- Proxy play also **closes the game out by itself**: when GNU Go sees nothing left worth playing and you are ahead on the mod's area score, it passes and forces the maid's reply to be a pass too → the game ends by scoring in your favour (gold `收工判胜!` on screen)
- **Go "Report a move"** (for fun): at a Go board with an empty hand, hold **J** for 1.5 s (progress bar + countdown on screen; releasing early cancels) → click the stone bowl to reset, play one stone, have the maid's reply judged a pass, then pass yourself → double pass → area scoring declares **you** the winner (red `举报一手!` on screen). The current game is reset, but the win still counts
- Settings → Mods → Chuying Proxy Play → Config:
  - **Think Strength**: LOW (sandbag) → DEFAULT → HIGH → MAX (higher = steadier, fewer blunders); shogi uses the same tiers (DEFAULT = 10s / depth 20, far above the maid's own level); Go maps these to GNU Go level 8 / 9 / 10
  - **Avoid Draw** (Chess only): OFF / GENTLE / ACTIVE / MAX — avoid forced draws
  - **Shogi proxy play**: available when `tlm_shogi` is installed; turn it off to leave shogi alone
  - **Go proxy play**: available when `TouhouGO` is installed; turn it off to leave Go alone

## How It Works (Pure Client)

- The engine calculates the move locally, then the move is converted back into a 3D board position and sent as a **vanilla** `ServerboundUseItemOnPacket` (simulated right-click)
- The server just sees a player clicking the board normally and lets the installed TLM handle the move — **zero server-side changes or dependencies**
- Xiangqi/Chess use two-step clicks (select piece → move); Gomoku/Shogi are resolved through their own multi-part board offsets
- Engine side: each engine's `main()` is renamed at build time and linked into a JNI library, with `std::cin`/`std::cout` redirected to in-memory queues, so the full UCI/pbrain loop runs **inside the game process — no process is ever created**
- GNU Go is a C program that works on `FILE*` streams (Windows/MinGW has no `fopencookie`/`funopen`, so its stdio cannot be redirected in-process). The bridge therefore calls its engine C API directly: `init_gnugo()` → `add_stone()` for every stone of the current position plus the ko point → `genmove()` (see `native/src/gnugo_bridge.cpp`) — still **no subprocess, no pipe, no text protocol**
- **No conflict with TouhouGO's own protocol**: moves are sent as vanilla `ServerboundUseItemOnPacket`, the maid's replies are still computed by that mod's own client-side AI, and this mod neither registers nor intercepts its `go_to_client` / `go_to_server` channels. The one exception is "Report a move", which deliberately sends a negative-coordinate reply *as if it were the maid's own move* (equivalent to that mod's maid-pass; the server only validates turn and legality) — a player-triggered joke feature that neither changes nor extends any protocol field

## Internationalization

UI and hints support Simplified Chinese, English and 日本語, switching automatically with the game language.

## For Developers

Engine binaries are not committed to git; the GitHub Actions `native-build` workflow clones the engine sources on CI, compiles them into per-platform JNI libraries and uploads them as artifacts. Download those into the mod's resource folder.

The JNI bridge and CMake scripts (`native/CMakeLists.txt`, `native/src/*.cpp`) **are tracked in this repository** (and, since 2.1, inside the release tag); every patch applied at build time lives in `.github/scripts/build_native.sh`.

```bash
# 1. Put the CI outputs (chuying_*.dll|so|dylib) into src/main/resources/engines/{windows,linux,macos}/
#    weights live in engines/shared/
# 2. Build all three platform jars at once
./gradlew build
```

Outputs in `build/libs/`: `chuying-<version>.jar` (skeleton) + `Chuying.Proxy.Play<version>-NeoForge-1.21.1-{windows,linux,macos}.jar`.

## License

**GPL-3.0-only** — the bundled engines (Pikafish / Stockfish / Rapfi / GNU Go) are GPL as well (GNU Go is GPL-3.0-or-later, linked into the whole under GPLv3's "v3" option, which is compatible); because they are linked into the same process, the combined work is distributed under GPL-3.0. Exact engine versions/commits, tar sha256 and every build-time patch are listed in `THIRD_PARTY_LICENSES.txt` and in the `BUILD_INFO.txt` shipped next to the native libraries.

**Corresponding Source (GPLv3)**: engine sources come from upstream (GNU Go = the official tar, sha256 `da68d7a6…6a72`), while this mod's modifications (the JNI bridges in `native/src/*.cpp` and `native/CMakeLists.txt`) and the patch script `.github/scripts/build_native.sh` are all in this repository — available in full from any release tag (e.g. `2.1`), which is enough to rebuild byte-identical engine libraries.
