# 车万女仆：褚嬴代打 2.0

让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。纯客户端，服务器免装。

**2.0 引擎全面进程内化**：Stockfish / Pikafish / Rapfi 三大引擎以 JNI 原生库（`.dll` / `.so` / `.dylib`）直接在游戏进程内运行——不再启动任何外部进程，没有任何 `.exe`，零配置开箱即用。

## 本 release 包含三个加载器版本 × 三平台（共 9 个 jar）

| 加载器 | Windows | Linux | macOS |
|---|---|---|---|
| NeoForge 1.21.1 | `Chuying.Proxy.Play2.0-NeoForge-1.21.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |
| Forge 1.20.1 | `Chuying.Proxy.Play2.0-Forge-1.20.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |
| Fabric 1.20.1 | `Chuying.Proxy.Play2.0-Fabric-1.20.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |

## 2.0 新特性

- **零子进程**：引擎编译为 JNI 原生库随 jar 分发，`std::cin/std::cout` 重定向到内存队列，游戏进程内跑完整 UCI/pbrain 循环，全程不创建进程
- **懒加载**：首次走子才加载引擎——未触发时代打零引擎内存占用；退出游戏自动全部释放
- **自动解压 + 内容校验**：引擎与 NNUE 权重按平台自动解压到 `config/chuying/engines/`，每次启动逐字节比对，引擎升级后旧文件一定会被替换
- **将棋代打**（仅 NeoForge 1.21.1）：装了 [tlm_shogi](https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-shogi) 即可代打将棋（走它自带的 Sunfish）
- **棋圣 PVP 代打**：装了 ChessPVP（棋圣）时支持 PVP 代打，只在自己回合动手
- 一引擎一桥接：三个引擎各自独立 JNI 符号，常驻互不串扰

## 功能（延续 1.1）

- 五子棋（Rapfi）/ 中国象棋（Pikafish）/ 国际象棋（Stockfish）AI 代打，均为 GPL-3.0 开源引擎
- 四档思考强度（低 / 默认 / 高 / 极致），游戏内实时调整，下一步立即生效
- 避和强度（仅国际象棋，让 Stockfish 主动求胜避免强制和棋）
- 中 / 英 / 日三语言

## 各平台前置

| 加载器 | 必须安装的前置 |
|---|---|
| NeoForge 1.21.1 | NeoForge 21.1+、Touhou Little Maid (TLM) 1.21.1 |
| Forge 1.20.1 | Forge 47+、Touhou Little Maid 1.20.1 |
| Fabric 1.20.1 | Fabric Loader 0.15.11+、Fabric API、Touhou Little Maid (Orihime Fabric) 1.20.1 |

## 使用

进游戏按 K 键开启 / 关闭代打，准星对准棋盘即可自动落子。设置 → 模组 → 褚嬴代打 调整思考时间、强度与避和。

## 关于引擎分发

本 mod 分发的是 JNI **共享库**（`.dll` / `.so` / `.dylib`），**不含也不运行任何 `.exe` 可执行文件**。三家引擎均为 GPL-3.0，编译期补丁与精确 commit 见各 jar 内 `engines/<平台>/BUILD_INFO.txt`。

---

# Chuying Proxy Play 2.0 (English)

Let strong chess engines play the Touhou Little Maid board games for you. Pure client-side — no server mod needed.

**2.0 moves the engines in-process.** Stockfish / Pikafish / Rapfi now run as JNI shared libraries (`.dll` / `.so` / `.dylib`) inside the game JVM — no subprocesses, no `.exe`, zero setup.

## This release ships 3 loaders × 3 platforms (9 jars)

| Loader | Windows | Linux | macOS |
|---|---|---|---|
| NeoForge 1.21.1 | `Chuying.Proxy.Play2.0-NeoForge-1.21.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |
| Forge 1.20.1 | `Chuying.Proxy.Play2.0-Forge-1.20.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |
| Fabric 1.20.1 | `Chuying.Proxy.Play2.0-Fabric-1.20.1-windows.jar` | `...-linux.jar` | `...-macos.jar` |

## New in 2.0

- **Zero subprocesses**: engines are bundled as JNI native libraries; their stdio is redirected to in-memory queues, so the full UCI/pbrain loop runs inside the game process
- **Lazy startup**: nothing is loaded until your first move — no engine memory before first use; exiting the game releases everything
- **Auto-extraction with byte-exact verification**: engines and NNUE weights extract to `config/chuying/engines/` on first launch; stale files are always replaced after engine updates
- **Shogi proxy play** (NeoForge 1.21.1 only) via tlm_shogi; **ChessPVP-aware PVP proxy play**
- One JNI bridge class per engine — the three resident engines never crosstalk

## Features (carried over from 1.1)

- Gomoku (Rapfi) / Chinese Chess (Pikafish) / International Chess (Stockfish), all GPL-3.0
- Four think-strength levels, adjustable in-game, applied from the very next move
- Avoid-Draw strength (Stockfish only)
- Chinese / English / Japanese localization

## Engine distribution note

This mod ships JNI **shared libraries** (`.dll` / `.so` / `.dylib`) — it does **not** contain or execute any `.exe`. All engines are GPL-3.0; build-time patches and exact commits are documented in `engines/<platform>/BUILD_INFO.txt` inside each jar.
