# Chuying Proxy Play (褚嬴代打)

> "Old man toying with the child" — let strong engines play for you and crush the maids of Touhou Little Maid.

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-orange)
![Fabric](https://img.shields.io/badge/Fabric-Loader%200.15.11%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

An addon for [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) on **Fabric 1.20.1** that detects when it's your turn in the maid's board games and automatically makes a move with a bundled chess engine.

> **Pure client, no server mod needed** — engines calculate locally, then your right-click on the board is simulated through vanilla interaction, so the server does **NOT** need this mod installed (works on other people's servers, no network channel mismatch).

## Features

| Feature | Description |
|---|---|
| **Pure Client** | Server needs nothing extra; works in singleplayer & on any multiplayer server |
| **One-key Proxy Play** | Toggle on/off with **K**; keybind remappable in Controls |
| **4 Think Strength levels** | LOW / DEFAULT / HIGH / MAX — adjustable in-game, applied to the very next move |
| **Avoid Draw (Chess)** | Push Stockfish to actively seek the win instead of settling for a draw |
| **Auto-extract engines** | Bundled Windows / Linux / macOS engines extracted on first launch, no setup |
| **Multi-language** | Simplified Chinese, English, 日本語 |
| **Debug HELL** | Force the Gomoku maid to HELL difficulty (client-only, for testing) |

## Supported Games & Engines

| Game | Engine | Protocol |
|---|---|---|
| Gomoku | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain |
| Chinese Chess (Xiangqi) | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI |
| International Chess | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI |

## Platform Packages

One build produces three jars — pick the one for your OS:

| File | OS |
|---|---|
| `Chuying Proxy Play<ver>-Fabric-1.20.1-windows.jar` | Windows |
| `Chuying Proxy Play<ver>-Fabric-1.20.1-linux.jar` | Linux (x86-64, AVX2) |
| `Chuying Proxy Play<ver>-Fabric-1.20.1-macos.jar` | macOS (Apple Silicon) |

> Engines are extracted automatically to `config/chuying/engines/` on first run.

> Need the NeoForge 1.21.1 or Forge 1.20.1 build? Pick the matching branch / jar on the Releases page.

## Requirements

- Fabric Loader `0.15.11+` (Minecraft 1.20.1)
- Fabric API
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) (Orihime Fabric 1.20.1) — **client & server** (the boards come from it)
- ModMenu & Cloth Config are bundled, **no** separate install

## Installation

1. Install Fabric 1.20.1 (Loader + API) and Touhou Little Maid (Orihime Fabric)
2. Download the jar matching your OS from Releases
3. Put it into `.minecraft/mods/`
4. Launch the game

## Usage

- Press **K** to toggle proxy play (remappable in Options → Controls)
- Walk up to a board, **keep your main hand empty** (the board requires an empty hand), and moves are made for you
- Settings → Mods → Chuying Proxy Play → Config:
  - **Think Strength**: LOW (sandbag) → DEFAULT → HIGH → MAX (higher = steadier, fewer blunders)
  - **Avoid Draw** (Chess only): OFF / GENTLE / ACTIVE / MAX — avoid forced draws
  - Engine paths can be overridden with your own engines (empty = bundled)

## How It Works (Pure Client)

- The engine calculates the move locally, then the move is converted back into a 3D board position and sent as a **vanilla** `ServerboundUseItemOnPacket` (simulated right-click)
- The server just sees a player clicking the board normally and lets the installed TLM handle the move — **zero server-side changes or dependencies**
- Xiangqi/Chess use two-step clicks (select piece → move), Gomoku uses a single click

## Internationalization

UI and hints support Simplified Chinese, English and 日本語, switching automatically with the game language.

## For Developers

Engine binaries are not committed to git; the GitHub Actions `engines` workflow (manual trigger) assembles and uploads them.

```bash
# 1. Put engine binaries into src/main/resources/engines/{windows,linux,macos}/ and shared/
# 2. Build all three platform jars at once
./gradlew build
```

Outputs in `build/libs/`: `chuying-<version>.jar` (skeleton) + `-windows.jar` / `-linux.jar` / `-macos.jar`.

## License

**GPL-3.0-only** — bundled engines (Pikafish / Stockfish / Rapfi) are GPL-3.0 as well.
