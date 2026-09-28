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
| **引擎自动解压** | 内置 Windows / Linux / macOS 原生库，首次运行解压到 `config/chuying/engines/` |
| **将棋联动** | 装了将棋扩展 `tlm_shogi` 也照样代打（走它自带的 Sunfish，不额外占体积） |
| **三语界面** | 简体中文、English、日本語 |
| **调试 HELL** | 强制五子棋女仆最高难度 HELL（纯客户端，测试用） |

## 支持的棋种与引擎

| 棋种 | 引擎 | 协议 | 备注 |
|---|---|---|---|
| 五子棋 | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | 内置，随 jar 分发 |
| 中国象棋 | [皮卡鱼 Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | 内置，随 jar 分发 |
| 国际象棋 | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | 内置，随 jar 分发 |
| 将棋 | `tlm_shogi` 自带的 Sunfish | 反射调用 | 不随本模组分发，装了扩展才启用 |

## 三平台分发包

一次构建产出三版 jar，按你的系统选择对应版本，**别下错了**：

| 文件 | 适用系统 |
|---|---|
| `Chuying Proxy Play<版本>-NeoForge-1.21.1-windows.jar` | Windows |
| `Chuying Proxy Play<版本>-NeoForge-1.21.1-linux.jar` | Linux（x86-64, AVX2） |
| `Chuying Proxy Play<版本>-NeoForge-1.21.1-macos.jar` | macOS（Apple Silicon） |

> 引擎首次运行时自动解压到 `config/chuying/engines/`，无需手动配置。

> 需要 Forge 1.20.1 或 Fabric 1.20.1 版本？请到 Releases 选择对应分支 / 发行页的 jar。

## 前置

- NeoForge `21.1.0+`（Minecraft 1.21.1）
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) ≥ `1.3.0` —— 客户端与服务端都需要（棋盘来自它）

## 安装

1. 安装 NeoForge 1.21.1 与 Touhou Little Maid
2. 从 Releases 下载**加载器 + 系统**匹配的 jar
3. 放入 `.minecraft/mods/`
4. 启动游戏

## 使用

- 按 **K** 开启/关闭代打（可在 设置 → 控制 → 按键绑定 修改）
- 走到棋盘旁，**保持空手**（棋盘本身要求空手操作），代打会自动落子
- 将棋棋盘同样适用（需安装 `tlm_shogi` 将棋扩展）；升变选择会由代打自动应答
- 设置 → 模组 → 褚嬴代打 → Config：
  - **思考强度**：低（放水）→ 默认 → 高 → 极致（越高越稳、越少失子）；将棋同样按此档位（默认 10 秒/深度 20，碾压女仆默认档）
  - **避和强度**（仅国象）：关闭 / 温和 / 激进 / 极致 —— 避免强制和棋
  - **将棋代打**：装了 `tlm_shogi` 时可用，关掉即不介入将棋

## 纯客户端原理

- 引擎在客户端本地算招，把走法逆推为棋盘交叉点的 3D 命中坐标，用**原版** `ServerboundUseItemOnPacket`（模拟右键）发送
- 服务器只当玩家在正常点击棋盘，由它已装的 TLM 完成落子 —— **服务器零改动、零依赖**
- 中国象棋/国际象棋为"选子→落子"两步模拟点击，五子棋/将棋按各自棋盘的分块偏移换算后点击
- 引擎侧：三家引擎的 `main()` 在构建期被重命名并链接进 JNI 原生库，`std::cin/std::cout` 被重定向到内存队列，因此**在游戏进程内跑完整 UCI/pbrain 循环，全程不创建进程**

## 国际化

界面与提示支持简体中文、English、日本語，随游戏语言自动切换。

## 开发者：本地构建

引擎二进制（以及将棋用的扩展 jar）不进 git 仓库：由 GitHub Actions `native-build` 工作流在 CI 上拉取引擎源码、编译成三平台 JNI 原生库并上传 artifact；下载后放进 `src/main/resources/engines/<平台>/`，权重等数据文件放 `engines/shared/`。

```bash
# 1. 把 CI 产物 chuying_*.dll|so|dylib 放进 src/main/resources/engines/{windows,linux,macos}/
# 2. 一次构建出三版 jar
./gradlew build
```

产物在 `build/libs/`：`chuying-<版本>.jar`（骨架）+ `Chuying Proxy Play<版本>-NeoForge-1.21.1-{windows,linux,macos}.jar`。

## 许可证

**GPL-3.0-only** —— 内置引擎（Pikafish / Stockfish / Rapfi）同为 GPL-3.0；本模组把它们链接进同一进程，整体仍以 GPL-3.0 分发。引擎版本与精确 commit、以及构建期所打的全部补丁见 `THIRD_PARTY_LICENSES.txt` 与构建产物内的 `BUILD_INFO.txt`。
