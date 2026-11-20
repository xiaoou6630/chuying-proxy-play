# 车万女仆：褚嬴代打 3.0 —— 大一统

## 一个 jar 通吃所有系统

从 3.0 起**不再按系统分版本**。原来 9 个 jar（3 加载器 × 3 系统）各带一份引擎，合计 **1219 MB**；现在三个加载器各一个 jar，**合计 397 MB，减少 67%**。

| 加载器 | 文件 | 体积 |
|---|---|---|
| **NeoForge 1.21.1** | `Chuying.Proxy.Play3.0-NeoForge-1.21.1.jar` | 131.3 MB |
| **Forge 1.20.1** | `Chuying.Proxy.Play3.0-Forge-1.20.1.jar` | 127.7 MB |
| **Fabric 1.20.1** | `Chuying.Proxy.Play3.0-Fabric-1.20.1.jar` | 127.8 MB |

每个 jar 支持：**Windows x64 / Linux x64 / macOS（Apple Silicon 和 Intel Mac）**。
首次运行只解压当前系统需要的那份引擎到 `config/chuying/engines/`。

## 体积怎么降下来的

**Stockfish 权重不再编进二进制。**

Stockfish 17.1 默认用 `INCBIN` 把 75 MB 的 NNUE 权重编译进库文件，三个平台各编一份就是 225 MB 的纯重复。3.0 编译时定义 `NNUE_EMBEDDING_OFF`，权重改为独立文件、三平台共用一份，运行时由 Java 侧通过 `setoption name EvalFile / EvalFileSmall` 加载。

| | 2.1 | 3.0 |
|---|---|---|
| 单平台 Stockfish 库 | 76 MB | **0.75 ~ 1.61 MB** |
| 权重存放 | 每个库各一份 | **三平台共用一份** |

## 修了 Intel Mac 加载不了引擎

2.1 的 macOS jar 里，引擎库只有 **arm64** 一个架构（CI 的 `macos-latest` 是 Apple Silicon 机器）。Intel Mac 上 `System.load()` 直接 `UnsatisfiedLinkError`，代打完全不可用。

3.0 的 macOS 引擎改为 **universal 二进制**（arm64 + x86_64 合在一个文件里），CI 构建后会用 `lipo -archs` 校验，缺架构直接让构建失败。

## 修了中文路径下引擎读不到权重

这是 **2.0/2.1 就存在的老 bug**：游戏装在中文路径下时（如 `E:\游戏\我的世界\`），引擎用 ANSI 窄字符 API 打开权重会失败。三个引擎表现不同：

| 引擎 | 症状 |
|---|---|
| Stockfish | `ERROR: Network evaluation parameters ... must be available`，然后**永远不出 bestmove**（代打卡死） |
| Pikafish | 直接 `std::exit`，**把整个 JVM 杀掉** |
| Rapfi | `config.toml` 里的模型路径打不开 |

3.0 的修法：`EngineExtractor` 启动时检测引擎目录路径，含非 ASCII 字符就整棵目录改到 `%TEMP%\chuying-engines\`。

## 修了围棋下到 200 多手必崩

**实测崩溃**（第 224 手、棋盘只剩 6 个空点）：

```
***assertion failure:
gnugo/engine/board.c:3042 - ON_BOARD1(pos) near PASS***
gnugo 3.8: You stepped on a bug.
```

Windows 事件日志：`java.exe` / `ucrtbase.dll` / `0xc0000409`（BEX64）。即 GNU Go 3.8 把 `PASS` 哨兵当坐标，断言失败后 `abort()` **直接杀掉 JVM**，没有 Java 异常、没有 crash-report。

这个 bug 在 2.1 里也存在（源码与补丁逐行相同），只是没下到那么深。

3.0 的修法（三层）：

1. 把 GNU Go 里**所有**参与编译的 `abort()` 换成 `longjmp` 出口（`printutils.c` 的 abortgo、`reading.c` 的 3 处 `default:` 分支、`oracle.c`）
2. `gnugo_bridge.cpp` 在 `nativeInit` / `nativeSetPosition` / `nativeGenmove` 三处加 `setjmp` 保护区
3. Java 侧对返回码 `-3` 做兜底：用模组自带 GoAI 选一手，并校验合法性

断言失败从此退化为「这一手算不出来」，不再崩游戏。

## 引擎层模块化

以前加引擎要在 `EngineExtractor`、`EngineManager`、`CMakeLists`、`jni_exports` 四处同步改。现在引擎是**登记表驱动**的，加引擎只改 `EngineRegistry.java` 一个文件：

```java
public static final EngineSpec SHOGI_ENGINE =
        EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue");
```

`EngineExtractor`（解压哪些文件）和 `EngineManager`（怎么创建实例）都从登记表推导。协议族目前有 `UCI` / `GOMOKU` / `GO` 三种。

## 更抗崩

- **棋种分派层加了总兜底**：以前 `onClientTick` 里调用各棋种适配层的外层没有 try/catch，而我们大量用反射调第三方模组。反射失败抛的 `NoClassDefFoundError` / `NoSuchMethodError` 都是 `Error` 而不是 `Exception`，一旦漏出来会直接崩客户端。现在外层 `catch (Throwable)`，异常只让当次代打跳过，日志限流 10 秒一条。
- **唯一的 mixin 加了 `require = 0`**：TLM 改了方法签名时，调试开关静默失效，而不是启动阶段报 `MixinApplyError` 打不开游戏。

## 补齐全部第三方许可证

以前只有棋引擎的许可说明，且 Forge/Fabric 版内容陈旧。3.0 补齐：

**内置组件**
- Pikafish / Stockfish / Rapfi（GPL-3.0）+ 各自权重
- GNU Go 3.8（GPL-3.0-or-later）+ 全部构建期补丁说明
- Cloth Config（Forge 内嵌，LGPL-3.0）

**前置/可选模组**
- **Touhou Little Maid —— MIT / CC BY-NC-SA 4.0 双许可**（此前完全缺失）
- tlm_shogi（MIT）、Sunfish4（MIT，Copyright © 2015 Ryosuke Kubo）
- TouhouGO（CC0 1.0 vs CC BY-NC-SA 4.0，上游矛盾，按严格口径说明）
- **ChessPVP / 棋圣 —— All Rights Reserved**（已标注不得打包）
- ForgeConfigAPIPort（MPL-2.0）、ModMenu（MIT）、MixinExtras（MIT）

完整内容见 jar 内 `THIRD_PARTY_LICENSES.txt`。

## 各版本联动对照

| 联动项 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|---|
| 五子棋 / 中国象棋 / 国际象棋 | ✅ | ✅ | ✅ |
| 围棋（TouhouGO） | ✅ | ❌ | ❌ |
| 将棋（tlm_shogi） | ⚠️ 见下 | ❌ | ❌ |
| 棋圣 PVP（ChessPVP） | ✅ | ✅ | ❌ |
| 调试强制 HELL | ✅ | ✅ | ✅ |

## ⚠️ 已知问题

**1. 将棋：女仆不会来坐（`tlm_shogi` 自身的 bug）**

`tlm_shogi` 的 `MaidPoiManagerMixin` 没有生效，导致 `jchess` 方块没被加入女仆的「娱乐方块」POI 集合，女仆永远找不到将棋棋盘。

诊断实测（3.0 内置诊断输出）：

```
[chuying][诊断] 女仆娱乐方块 POI 共 156 个状态，含 jchess = false
```

根因：`tlm_shogi.mixins.json` 声明 `"compatibilityLevel": "JAVA_17"`，但 `build.gradle` 用 Java 21 工具链编译，mixin class 是 version 65，超过 Mixin 按 JAVA_17 支持的上限 61，注入被跳过。

**这不是本模组的问题**，本模组的 mixin 声明是 `JAVA_21`，且只注入五子棋难度。需要 `tlm_shogi` 作者把 `compatibilityLevel` 改成 `JAVA_21`。

**2. 1.20.1 两个版本不含围棋和将棋**

- 围棋棋盘模组 TouhouGO 只支持 NeoForge 1.21.1
- 将棋附属 tlm_shogi 只有 NeoForge 版
- ChessPVP 没有 Fabric 版

## 兼容性

- **NeoForge 1.21.1**：NeoForge 21.1.0+、TLM 1.3.0+
- **Forge 1.20.1**：Forge 47+、TLM 1.3.0+（Cloth Config 已内嵌）
- **Fabric 1.20.1**：Fabric Loader 0.15+、TLM 0.8.2+、ForgeConfigAPIPort 8.0+

纯客户端，服务器零改动。

## 许可证

本模组：**GPL-3.0-only**。内置引擎同为 GNU GPL（GNU Go 为 GPL-3.0-or-later），整体以 GPL-3.0 分发。引擎版本、精确 commit / tar sha256、以及构建期所打的全部补丁见 `THIRD_PARTY_LICENSES.txt` 与 jar 内 `BUILD_INFO.txt`。

---

# Chuying Proxy Play 3.0 (English)

## One jar for every OS

Starting with 3.0 there are no per-OS builds. Previously 9 jars (3 loaders × 3 systems) each carried their own copy of the engines, totalling **1219 MB**; now each loader ships one jar, **397 MB in total — a 67% reduction**.

| Loader | File | Size |
|---|---|---|
| **NeoForge 1.21.1** | `Chuying.Proxy.Play3.0-NeoForge-1.21.1.jar` | 131.3 MB |
| **Forge 1.20.1** | `Chuying.Proxy.Play3.0-Forge-1.20.1.jar` | 127.7 MB |
| **Fabric 1.20.1** | `Chuying.Proxy.Play3.0-Fabric-1.20.1.jar` | 127.8 MB |

Each jar supports **Windows x64 / Linux x64 / macOS (both Apple Silicon and Intel Macs)**. On first run only the engine copy for the current OS is extracted.

## Why it got smaller

**Stockfish nets are no longer embedded.** Stockfish 17.1 embeds its 75 MB NNUE net into the binary via `INCBIN` — three platforms meant 225 MB of pure duplication. 3.0 compiles with `NNUE_EMBEDDING_OFF`; the nets ship as separate files shared by all platforms and are loaded via `setoption name EvalFile / EvalFileSmall`.

| | 2.1 | 3.0 |
|---|---|---|
| Stockfish library (per platform) | 76 MB | **0.75 – 1.61 MB** |
| Net storage | one copy per binary | **one copy shared by all** |

## Fixed: engines failed to load on Intel Macs

In 2.1 the macOS jar contained arm64-only libraries (CI's `macos-latest` runner is Apple Silicon), so Intel Macs got `UnsatisfiedLinkError`. 3.0 ships **universal binaries** (arm64 + x86_64), verified with `lipo -archs` during the build.

## Fixed: engines could not load weights from a non-ASCII path

An old bug present since 2.0. With the game installed under a path containing non-ASCII characters, the engines fail to open their weight files: Stockfish never emits `bestmove`, Pikafish calls `std::exit` and kills the JVM, Rapfi cannot open its model paths. 3.0 detects a non-ASCII engine directory and relocates the whole tree to `%TEMP%\chuying-engines\`.

## Fixed: Go crashed after ~224 moves

GNU Go 3.8 passes the `PASS` sentinel as a board coordinate near the end of a game, hits an assertion in `board.c`, and calls `abort()` — which kills the entire JVM (no Java exception, no crash report). This bug exists in 2.1 too; it just requires a long game to trigger.

3.0 fixes it in three layers: every `abort()` reachable in the compiled engine is replaced with a `longjmp` exit, `gnugo_bridge.cpp` wraps `nativeInit` / `nativeSetPosition` / `nativeGenmove` in `setjmp` guards, and the Java side falls back to the mod's own GoAI when the engine returns `-3`.

## Modular engine layer

Adding an engine now touches only `EngineRegistry.java`:

```java
public static final EngineSpec SHOGI_ENGINE =
        EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue");
```

## Harder to crash

- **Top-level guard around game dispatch** — reflection failures throw `Error`s (`NoClassDefFoundError`, `NoSuchMethodError`), which would previously crash the client. Now caught as `Throwable`; a failure skips one proxy action and logs at most once per 10 seconds.
- **The single mixin uses `require = 0`** — if TLM changes the method signature, the debug toggle silently does nothing instead of failing startup.

## Complete third-party licensing

Previously only the engines were documented, and the Forge/Fabric versions were out of date. 3.0 now covers everything:

**Bundled:** Pikafish / Stockfish / Rapfi (GPL-3.0) with their nets, GNU Go 3.8 (GPL-3.0-or-later) with the full patch list, Cloth Config (Forge, LGPL-3.0).

**Dependencies:** **Touhou Little Maid — MIT / CC BY-NC-SA 4.0 dual license** (previously undocumented), tlm_shogi (MIT), Sunfish4 (MIT, © 2015 Ryosuke Kubo), TouhouGO (CC0 vs CC BY-NC-SA, upstream inconsistency documented), **ChessPVP — All Rights Reserved** (marked as not redistributable), ForgeConfigAPIPort (MPL-2.0), ModMenu (MIT), MixinExtras (MIT).

See `THIRD_PARTY_LICENSES.txt` inside the jar.

## Compatibility matrix

| Feature | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|---|
| Gomoku / Xiangqi / Chess | ✅ | ✅ | ✅ |
| Go (TouhouGO) | ✅ | ❌ | ❌ |
| Shogi (tlm_shogi) | ⚠️ see below | ❌ | ❌ |
| ChessPVP PvP | ✅ | ✅ | ❌ |

## ⚠️ Known issue

**Shogi: the maid will not come to the board** — this is a bug in `tlm_shogi`, not in this mod. Its `MaidPoiManagerMixin` is skipped because `tlm_shogi.mixins.json` declares `"compatibilityLevel": "JAVA_17"` while the mod is compiled with Java 21 (class version 65 > 61), so `jchess` never enters the maid's POI set. Our built-in diagnostic reports `含 jchess = false` (156 states checked). The fix belongs upstream: change `compatibilityLevel` to `JAVA_21`.

## License

**GPL-3.0-only**. The bundled engines are GNU GPL as well; the combined work is distributed under GPL-3.0. Engine versions, exact commits / tarball sha256, and every build-time patch are documented in `THIRD_PARTY_LICENSES.txt` and `BUILD_INFO.txt`.
