# 车万女仆：褚嬴代打 2.1

让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。纯客户端，服务器免装。

**2.1 新增围棋代打**：装了围棋棋盘扩展 [TouhouGO](https://github.com/moyinsky/TouhouGO) 后，15 路围棋也能代打 —— 引擎为 [GNU Go](https://www.gnu.org/software/gnugo/) 3.8，同样以 JNI 原生库**在游戏进程内直调其引擎 C API**：零子进程、零管道、零文本协议。

## 本 release 包含（仅 NeoForge 1.21.1 × 三平台，共 3 个 jar）

| 平台 | 文件 |
|---|---|
| Windows | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-windows.jar` |
| Linux (x86-64) | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-linux.jar` |
| macOS (Apple Silicon) | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-macos.jar` |

> Forge 1.20.1 / Fabric 1.20.1 分支本轮无改动（围棋棋盘只存在于 1.21.1 的 TouhouGO），继续使用 2.0 版本即可。

## 2.1 新特性

- **围棋代打**：装了 TouhouGO 后，检测到 15 路围棋棋盘便自动替你落子；数子规则、贴目 6.5、劫点与禁着点判定与模组 `GoRules` 完全一致
- **引擎进程内直调**：GNU Go 用的是 C 的 `FILE*` 流（Windows/MinGW 没有 `fopencookie`/`funopen`，无法在进程内重定向），因此桥接层直接调用它的引擎 C API（`init_gnugo()` → 逐子 `add_stone()` 摆当前棋面与劫点 → `genmove()`），依旧**不 spawn 任何进程**
- **「举报一手」**（整活，默认键 **J**）：对着围棋棋盘、**空手**长按 1.5 秒（屏幕上有进度条与倒计时，提前松手取消）→ 自动完成「点棋子盒重置 → 天元落子 → 女仆这一手判停一手 → 我方停一手」→ 双方连续停手触发数子 → **直接判我方获胜**。代价是当前这盘会被重置，但胜负照算你赢
- **自动收工**：GNU Go 认为"无棋可下"、而按数子我方已领先时，代打会主动判双方停手直接终局判胜（金色大字 `收工判胜！`），不再无意义地把棋盘填满（早期版本实测一路下到 398 手）
- **修掉 GNU Go 3.8 自身的三处缺陷**（Linux 只是靠未初始化内存恰好为 0 侥幸通过，macOS/arm64 必现中止；用 `execinfo` 调用栈定位到崩点）：
  1. `engine/combination.c`：`aa_init_moves()` 只设 `attacks[0].move` 哨兵，`target[]` 其余槽位是未初始化栈内存，会被后续读取当作目标位置 → 改为整数组初始化；
  2. `utils/gg_utils.c`：`gg_sort()` 缺 `nel < 2` 保护，`nel == 0` 时 `end = base + width * (nel - 1)` 发生 `size_t` 下溢；
  3. `engine/board.c`：`countlib` / `countstones` / `findstones` 收到非法位置时改为返回 0 —— 进程内引擎一旦 `abort()` 会连坐杀掉整个 Minecraft JVM
- **体积友好**：围棋原生库仅 6.5–7.8 MB（macOS 6.49 / Windows 7.58 / Linux 7.79），**无神经网络权重**

## 升级与使用注意

- 围棋需要 **TouhouGO**（客户端与服务端都要装）；不装时本模组行为与 2.0 完全一致，其余棋种不受影响
- 围棋落子时**别按着潜行键**（潜行 + 空手点击是模组的"停一手"；代打会等你松手再落子）
- 思考强度四档对围棋映射到 GNU Go 的 level **8 / 9 / 10**（低 / 默认 / 高与极致）；"每步思考时间"配置对围棋无效（GNU Go 按自己的 level 定时）
- 三语言（简中 / English / 日本語）已补齐围棋相关界面与提示

## 各平台前置

| 组件 | 要求 |
|---|---|
| NeoForge | 21.1.0+（Minecraft 1.21.1） |
| Touhou Little Maid | ≥ 1.3.0，客户端与服务端都要装 |
| TouhouGO（仅围棋） | 客户端与服务端都要装；不装则跳过围棋 |

## 关于引擎分发与许可

本 mod 分发的是 JNI **共享库**（`.dll` / `.so` / `.dylib`），**不含也不运行任何 `.exe`**。内置引擎（Pikafish / Stockfish / Rapfi / GNU Go）均为 GNU GPL；GNU Go 为 **GPL-3.0-or-later**，按 GPLv3 的 "v3" 选项链接进整体，与本 mod 的 GPL-3.0-only 兼容，整体仍以 GPL-3.0 分发。

精确 commit / tar 包 sha256（GNU Go 3.8 tar = `da68d7a65f44dcf6ce6e4e630b6f6dd9897249d34425920bfdd4e07ff1866a72`）与全部构建期补丁见 `THIRD_PARTY_LICENSES.txt` 及 jar 内 `engines/<平台>/BUILD_INFO.txt`；**对应源码**（上游源码 + 本仓库的 JNI 桥接层 `native/src/*.cpp` 与补丁脚本 `.github/scripts/build_native.sh`）随本仓库任意发布 tag 完整可得。

**可选依赖 TouhouGO 的许可（是否违规）**：围棋棋盘模组 [TouhouGO](https://github.com/moyinsky/TouhouGO) 上游声明不一致 —— 其仓库 `LICENSE` 是 **CC0 1.0**（公共领域奉献），而 README 首行写的是 **CC BY-NC-SA 4.0**。本 mod 按**更严格的 README 口径**设计：**不打包、不复制、不修改它的任何代码与素材**（jar 内没有一个它的字节），只在运行时用反射读取棋盘方块实体，并以它自己的网络包类型发送数据来完成代打。由此：

- ① 未复制、未改编其作品 → **不触发** CC BY-NC-SA 的署名与「相同方式共享」义务；
- ② 未链接其代码、未分发其内容 → 其**非商业（NC）限制不会传染**到本 mod 的 GPL-3.0 分发，也不构成 GPL 与 CC-NC 的混合作品冲突；
- ③ 即使按其 `LICENSE` 的 **CC0 1.0** 理解（无任何限制），更是毫无冲突。

**结论：两种口径下都不存在许可违规。** 玩家使用围棋功能需自行安装 TouhouGO，并遵守其自身条款（README 口径含非商业限制；其素材源自《车万女仆》本体）。也请勿把 TouhouGO 的文件打进本 mod 的发布包 —— 那才会同时踩到 CC 侧的 NC 与 GPL 的冲突。

---

# Chuying Proxy Play 2.1 (English)

Let strong engines play the Touhou Little Maid board games for you. Pure client-side — no server mod needed.

**2.1 adds Go.** With the [TouhouGO](https://github.com/moyinsky/TouhouGO) board addon installed, 15×15 Go is played for you too — by [GNU Go](https://www.gnu.org/software/gnugo/) 3.8 running as a JNI library that calls the engine's **C API directly inside the game process**: no subprocess, no pipe, no text protocol.

## This release ships NeoForge 1.21.1 only (3 jars)

| Platform | File |
|---|---|
| Windows | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-windows.jar` |
| Linux (x86-64) | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-linux.jar` |
| macOS (Apple Silicon) | `Chuying.Proxy.Play2.1-NeoForge-1.21.1-macos.jar` |

> The Forge 1.20.1 / Fabric 1.20.1 branches are unchanged this round (the Go board only exists on 1.21.1); keep using 2.0 there.

## New in 2.1

- **Go proxy play**: with TouhouGO installed, the mod plays your 15×15 Go games; area scoring, 6.5 komi, ko and suicide rules match the addon's own `GoRules`
- **Engine called in-process**: GNU Go works on C `FILE*` streams (Windows/MinGW has no `fopencookie`/`funopen`, so stdio cannot be redirected), so the bridge calls its C API directly (`init_gnugo()` → `add_stone()` for every stone plus the ko point → `genmove()`) — still **no OS process is ever created**
- **"Report a move"** (for fun, default key **J**): at a Go board with an empty hand, hold for 1.5 s (progress bar and countdown on screen; releasing early cancels) → the mod resets the board, plays one stone, has the maid's reply judged a pass and passes for you → double pass triggers area scoring → **you win**. The current game is reset, but the win still counts
- **Auto close-out**: when GNU Go has nothing left worth playing and you lead on the area score, the mod passes for both sides and ends the game in your favour (gold `收工判胜!` on screen) instead of pointlessly filling the board (earlier builds played on to move 398)
- **Three real GNU Go 3.8 defects fixed** (Linux only survived by luck; macOS/arm64 always aborted — located with an `execinfo` backtrace): uninitialised `attacks[].target[]` in `aa_init_moves()`; `size_t` underflow in `gg_sort()` when `nel == 0`; missing position guards in `countlib`/`countstones`/`findstones` (a native `abort()` would take the whole Minecraft JVM down)
- **Small footprint**: the Go library is only 6.5–7.8 MB (macOS 6.49 / Windows 7.58 / Linux 7.79) with **no neural-network weights**

## Upgrade notes

- Go requires **TouhouGO** on both client and server; without it the mod behaves exactly like 2.0 and other games are unaffected
- Do **not** hold sneak while Go moves are played (sneak + empty-hand click is the addon's "pass"; the mod waits for you to release it)
- The four think-strength tiers map to GNU Go level **8 / 9 / 10**; the "think time" setting has no effect on Go (GNU Go uses its own level-based timing)
- Chinese / English / Japanese localisation now covers the Go feature as well

## License and engine distribution

This mod ships JNI **shared libraries** (`.dll` / `.so` / `.dylib`) — it does **not** contain or execute any `.exe`. All bundled engines (Pikafish / Stockfish / Rapfi / GNU Go) are GNU GPL; GNU Go is **GPL-3.0-or-later**, linked into the combined work under GPLv3's "v3" option, which is compatible with this mod's GPL-3.0-only, so the whole is distributed under GPL-3.0.

Exact commits, tar sha256 (GNU Go 3.8 tar = `da68d7a65f44dcf6ce6e4e630b6f6dd9897249d34425920bfdd4e07ff1866a72`) and every build-time patch are listed in `THIRD_PARTY_LICENSES.txt` and in `engines/<platform>/BUILD_INFO.txt` inside each jar. The **corresponding source** (upstream sources plus this repository's JNI bridges in `native/src/*.cpp` and the patch script `.github/scripts/build_native.sh`) is available in full from any release tag of this repository.

**Optional dependency TouhouGO — is there any licence violation?** The upstream Go-board addon [TouhouGO](https://github.com/moyinsky/TouhouGO) contradicts itself: its repository `LICENSE` is **CC0 1.0** (public-domain dedication), while the README states **CC BY-NC-SA 4.0**. This mod is built for the **stricter README reading**: it bundles, copies and modifies **none** of its code or assets (not a single byte of it is inside the jar) — it only reads the board's block entity through reflection at runtime and sends data using that addon's own packet type.

- (1) No copying or adaptation of their work → **no** CC attribution or share-alike obligation is triggered;
- (2) Their code is not linked and their content is not distributed → the **non-commercial (NC) restriction does not propagate** to this mod's GPL-3.0 distribution, so there is no GPL / CC-NC combined-work conflict;
- (3) Read as their `LICENSE` file's **CC0 1.0** instead (no restrictions whatsoever), there is plainly no conflict either.

**Conclusion: no licence violation under either reading.** To use Go, install TouhouGO yourself and follow its own terms (the README reading includes non-commercial use); its assets come from Touhou Little Maid. Please also never bundle TouhouGO's files into this mod's releases — that is the only way to run into the CC-NC vs GPL conflict.
