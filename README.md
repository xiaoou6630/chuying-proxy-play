# 褚嬴代打 (Chuying Proxy Play)

> 老叟戏顽童：让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-orange)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

一个基于 **NeoForge 1.21.1** 的 [Touhou Little Maid（车万女仆）](https://modrinth.com/mod/touhou-little-maid) 附属模组：检测到女仆棋局轮到你走子时，自动调用内置引擎替你落子。

> **纯客户端，服务器免装** —— 引擎在本地算招，再通过原版交互模拟你右键棋盘，因此服务器**不需要**安装本模组（连别人的服务器也能用，不会报网络通道不匹配）。
>
> **零子进程（2.0）** —— 三大引擎以 JNI 原生库（`.dll` / `.so` / `.dylib`）**在游戏进程内运行**，不再启动任何外部进程，也不再有"外置引擎路径"配置。

## 特性

| 特性 | 说明 |
|---|---|
| **纯客户端** | 服务器零改动，单机/联机都能用 |
| **零子进程** | 引擎编译成 JNI 原生库，进程内跑，不 spawn 任何 exe |
| **一键代打** | **K** 键随时启停，可在 控制 里改键 |
| **思考强度四档** | 低 / 默认 / 高 / 极致 —— 游戏内实时调整，下一步立即生效 |
| **避和强度（国象）** | 让 Stockfish 主动求胜、拒绝被拖平 |
| **引擎自动解压** | **一个 jar 通吃 Windows / Linux / macOS（含 Intel Mac）**，首次运行只解压当前系统需要的那份 |
| **将棋联动** | 装了将棋扩展 [tlm_shogi](https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-shogi) 也照样代打（走它自带的 Sunfish，不额外占体积） |
| **棋圣 PVP 代打** | 装了棋圣 [ChessPVP](https://modrinth.com/mod/tlmcp-chesspvp) 时支持 PVP 代打：只替自己在对局中的一方落子（需该扩展支持的潜行点击交互） |
| **围棋代打** | 装了 [TouhouGO](https://github.com/moyinsky/TouhouGO)（车万女仆·围棋棋盘）时支持围棋：15 路、数子规则，GNU Go 引擎**进程内直调** |
| **三语界面** | 简体中文、English、日本語 |
| **调试 HELL** | 强制五子棋女仆最高难度 HELL（纯客户端，测试用） |

## 支持的棋种与引擎

| 棋种 | 引擎 | 协议 | 备注 |
|---|---|---|---|
| 五子棋 | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | 内置，随 jar 分发 |
| 中国象棋 | [皮卡鱼 Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | 内置，随 jar 分发 |
| 国际象棋 | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | 内置，随 jar 分发 |
| 将棋 | `tlm_shogi` 自带的 Sunfish | 反射调用 | 不随本模组分发，装了扩展才启用 |
| 围棋 | [GNU Go](https://www.gnu.org/software/gnugo/) 3.8 | 引擎 C API（进程内直调） | 内置，随 jar 分发；棋盘来自 TouhouGO |

## 一个 jar 通吃所有系统

**3.0 起不再按系统分版本**——只有一个 jar，下载即用：

| 文件 | 适用系统 |
|---|---|
| `Chuying.Proxy.Play<版本>-NeoForge-1.21.1-universal.jar` | Windows x64 / Linux x64 / macOS（Apple Silicon **和** Intel Mac） |

> 3.0 之前是三个 jar 各带一份引擎（共 1219 MB）。现在合并成一个，
> 且 Stockfish 权重不再编进二进制而是三平台共用一份，总体积降到约 129 MB。

> 引擎首次运行时自动解压到 `config/chuying/engines/`，无需手动配置。

> 需要 Forge 1.20.1 或 Fabric 1.20.1 版本？请到 Releases 选择对应分支 / 发行页的 jar。

## 前置

- NeoForge `21.1.0+`（Minecraft 1.21.1）
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) ≥ `1.3.0` —— 客户端与服务端都需要（棋盘来自它）
- **[TouhouGO](https://github.com/moyinsky/TouhouGO)（可选，只有围棋代打需要）** —— 车万女仆的围棋棋盘扩展，客户端与服务端都要装

## 安装

1. 安装 NeoForge 1.21.1 与 Touhou Little Maid
2. 从 Releases 下载**加载器 + 系统**匹配的 jar
3. 放入 `.minecraft/mods/`
4. 启动游戏

## 使用

- 按 **K** 开启/关闭代打（可在 设置 → 控制 → 按键绑定 修改）
- 走到棋盘旁，**保持空手**（棋盘本身要求空手操作），代打会自动落子
- 将棋棋盘同样适用（需安装 `tlm_shogi` 将棋扩展）；升变选择会由代打自动应答
- 装了棋圣 `ChessPVP` 时同样适用：只在双方都加入、且轮到你所属那一方（红/白 或 黑）时代打，旁观者不介入
- 装了 `TouhouGO` 时围棋同样适用：**落子时别按着潜行键**（潜行 + 空手点击是"停一手"，代打会等你松手）；引擎认为该停一手时，代打会自动帮你停一手
- 代打自己会**见好就收**：围棋引擎认为"无棋可下"、而按模组数子我方已领先时，代打会判双方停一手直接终局判胜（金色大字 `收工判胜！`），不再无意义地填满棋盘
- **围棋「举报一手」**（整活）：对着围棋棋盘、**空手**长按 **J** 1.5 秒（屏幕上有进度条与倒计时，提前松手即取消）→ 自动完成「点棋子盒重置 → 天元落子 → 女仆这一手判停一手 → 我方停一手」→ 双方连续停手触发数子 → **直接判我方获胜**（红色大字 `举报一手！`）。代价是当前这盘棋会被重置，但胜负照算你赢
- 设置 → 模组 → 褚嬴代打 → Config：
  - **思考强度**：低（放水）→ 默认 → 高 → 极致（越高越稳、越少失子）；将棋同样按此档位（默认 10 秒/深度 20，碾压女仆默认档）
  - **避和强度**（仅国象）：关闭 / 温和 / 激进 / 极致 —— 避免强制和棋
  - **将棋代打**：装了 `tlm_shogi` 时可用，关掉即不介入将棋
  - **围棋代打**：装了 `TouhouGO` 时可用，关掉即不介入围棋；思考强度档位映射到 GNU Go 的 level 8 / 9 / 10（低 / 默认 / 高与极致）

## 纯客户端原理

- 引擎在客户端本地算招，把走法逆推为棋盘交叉点的 3D 命中坐标，用**原版** `ServerboundUseItemOnPacket`（模拟右键）发送
- 服务器只当玩家在正常点击棋盘，由它已装的 TLM 完成落子 —— **服务器零改动、零依赖**
- 中国象棋/国际象棋为"选子→落子"两步模拟点击，五子棋/将棋按各自棋盘的分块偏移换算后点击
- 围棋（TouhouGO）与五子棋的九宫格几何**逐字节相同**，直接复用同一套交叉点换算（已穷举验证 225 点 × 4 朝向）
- 引擎侧：前三家引擎的 `main()` 在构建期被重命名并链接进 JNI 原生库，`std::cin/std::cout` 被重定向到内存队列，因此**在游戏进程内跑完整 UCI/pbrain 循环，全程不创建进程**
- 围棋引擎是 C 程序，走的是 `FILE*` 流（Windows/MinGW 没有 `fopencookie`/`funopen`，无法在进程内重定向），因此改为直接调用它的引擎 C API：`init_gnugo()` → `add_stone()` 摆当前棋面 + 注入劫点 → `genmove()`（见 `native/src/gnugo_bridge.cpp`）—— 同样**零子进程、零管道、零文本协议**
- **不与 TouhouGO 的自定义协议冲突**：代打落子只发原版 `ServerboundUseItemOnPacket`，女仆的应手仍由该模组自己的客户端 AI 流程计算，本模组不注册、不拦截它的 `go_to_client` / `go_to_server` 通道。唯一的例外是「举报一手」：它**以"女仆的应手"为名义**发一个负坐标（等价于该模组自己的"女仆停一手"，服务端只校验回合与合法性），属于玩家主动触发的整活功能，不改动、也不新增任何协议字段

## 国际化

界面与提示支持简体中文、English、日本語，随游戏语言自动切换。

## 开发者：本地构建

引擎二进制不进 git 仓库：由 GitHub Actions `native-build` 工作流在**临时分支**上拉取引擎源码、编译成三平台 JNI 原生库，编译完自动删除该临时分支，产物 `engines-unified` 可下载。**CI 只编原生库，jar 在本地打**。

```powershell
# 1. 下载并解压 CI 的 engines-unified 产物到某个目录
# 2. 一键装引擎 + 打包（会校验 4 引擎 × 3 平台是否齐全）
.\package-local.ps1 -UnifiedDir ..\..\_release_tmp\unified
```

脚本会把产物按 `os-arch` 铺进 `src/main/resources/engines/`：

```
engines/
├── windows-x86_64/     chuying_*.dll
├── linux-x86_64/       chuying_*.so
├── macos-universal/    chuying_*.dylib   (arm64 + x86_64 合一)
└── shared/             pikafish.nnue、stockfish/*.nnue、rapfi/*
```

产物在 `build/libs/`：`Chuying.Proxy.Play<版本>-NeoForge-1.21.1-universal.jar`（发布用）+ `chuying-<版本>.jar`（骨架）。

### 加新引擎

引擎层是登记表驱动的，**加引擎只改一个文件** `EngineRegistry.java`：

1. `native/CMakeLists.txt` 加一个 `chuying_bridge(chuying_xxx ...)`，`jni_exports.cpp` 加对应 `CHUYING_ENGINE_XXX` 分支；
2. `EngineRegistry` 加一条 `EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue")`；
3. 若协议族是已有的 `UCI` / `GOMOKU`，`EngineManager` 加一行 getter 即可；全新协议族才需要新写包装类。

`EngineExtractor`（解压哪些文件）和 `EngineManager`（怎么创建）都从登记表推导，不需要同步修改。

## 许可证

**GPL-3.0-only** —— 内置引擎（Pikafish / Stockfish / Rapfi / GNU Go）同为 GNU GPL（GNU Go 为 GPL-3.0-or-later，按 GPLv3 的 "v3" 选项链接进整体，许可兼容）；本模组把它们链接进同一进程，整体仍以 GPL-3.0 分发。引擎版本与精确 commit / tar 包 sha256、以及构建期所打的全部补丁（含 GNU Go 3.8 的三处老 C 缺陷修复）见 `THIRD_PARTY_LICENSES.txt` 与构建产物内的 `BUILD_INFO.txt`。

**对应源码（GPLv3 Corresponding Source）**：引擎源码取自上游（GNU Go = 官方 tar，sha256 固定为 `da68d7a6…6a72`），本模组的修改（JNI 桥接层 `native/src/*.cpp` 与 `native/CMakeLists.txt`）与构建期补丁脚本 `.github/scripts/build_native.sh` 都在本仓库内，从任意发布 tag（如 `2.1`）即可完整取得，据此可复现出与发布包完全一致的引擎二进制。

**可选依赖的许可（仅运行时互操作，不分发其内容）**：围棋棋盘 [TouhouGO](https://github.com/moyinsky/TouhouGO) 上游声明不一致 —— 仓库 `LICENSE` 是 **CC0 1.0**（公共领域），README 首行却写 **CC BY-NC-SA 4.0**。本模组按**更严格的 README 口径**处理：**不打包、不复制、不修改它的任何代码与素材**，只在运行时用反射读取棋盘、并以它自己的网络包类型发送数据。因此 ① 不触发 CC 的署名与「相同方式共享」义务，② 其「非商业(NC)」限制不会传染到本模组的 GPL-3.0 分发 —— **两种口径下都不存在许可冲突**。使用围棋功能请自行安装 TouhouGO 并遵守其条款（含非商业限制），**不要**把它的文件打进本模组的发布包；详细说明见 `THIRD_PARTY_LICENSES.txt`。
