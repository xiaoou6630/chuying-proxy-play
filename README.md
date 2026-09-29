# 褚嬴代打 (Chuying Proxy Play) — Forge 1.20.1

> 老叟戏顽童：让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-orange)
![Forge](https://img.shields.io/badge/Forge-47%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

一个基于 **Forge 1.20.1** 的 [Touhou Little Maid（车万女仆）](https://modrinth.com/mod/touhou-little-maid) 附属模组：检测到女仆棋局轮到你走子时，自动调用内置引擎替你落子。

> **纯客户端，服务器免装** —— 引擎在本地算招，再通过原版交互模拟你右键棋盘，服务器**不需要**安装本模组。
>
> **零子进程（2.0）** —— 引擎以 JNI 原生库（`.dll` / `.so` / `.dylib`）**在游戏进程内运行**，不启动任何外部进程。

## 特性

| 特性 | 说明 |
|---|---|
| **纯客户端** | 服务器零改动，单机/联机都能用 |
| **零子进程** | 引擎编译成 JNI 原生库，进程内跑 |
| **一键代打** | **K** 键随时启停，可在 控制 里改键 |
| **思考强度四档** | 低 / 默认 / 高 / 极致 —— 游戏内实时调整 |
| **避和强度（国象）** | 让 Stockfish 主动求胜、拒绝被拖平 |
| **引擎自动解压** | 内置 Windows / Linux / macOS 原生库，首次运行解压到 `config/chuying/engines/` |
| **棋圣 PVP 代打** | 装了棋圣 [ChessPVP](https://modrinth.com/mod/tlmcp-chesspvp)（1.20.1 Forge 版）时支持 PVP 代打：只替自己对局一方落子 |
| **三语界面** | 简体中文、English、日本語 |

## 支持的棋种与引擎

| 棋种 | 引擎 | 协议 | 备注 |
|---|---|---|---|
| 五子棋 | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | 内置，随 jar 分发 |
| 中国象棋 | [皮卡鱼 Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | 内置，随 jar 分发 |
| 国际象棋 | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | 内置，随 jar 分发 |

> 将棋联动需要 `tlm_shogi` 扩展，该扩展仅发布 NeoForge 1.21.1 版，Forge 版暂不支持。

## 三平台分发包

| 文件 | 适用系统 |
|---|---|
| `Chuying.Proxy.Play<版本>-Forge-1.20.1-windows.jar` | Windows |
| `Chuying.Proxy.Play<版本>-Forge-1.20.1-linux.jar` | Linux（x86-64） |
| `Chuying.Proxy.Play<版本>-Forge-1.20.1-macos.jar` | macOS（Apple Silicon） |

> 引擎首次运行时自动解压到 `config/chuying/engines/`，无需手动配置。

## 前置

- Forge `47+`（Minecraft 1.20.1）
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) 1.20.1 —— 客户端与服务端都需要（棋盘来自它）

## 安装

1. 安装 Forge 1.20.1 与 Touhou Little Maid
2. 从 Releases 下载与**系统**匹配的 jar
3. 放入 `.minecraft/mods/`
4. 启动游戏

## 使用

- 按 **K** 开启/关闭代打（可在 设置 → 控制 → 按键绑定 修改）
- 走到棋盘旁，**保持空手**（棋盘本身要求空手操作），代打会自动落子
- 装了棋圣 `ChessPVP` 时同样适用：只在双方都加入、且轮到你所属那一方时代打
- 设置 → 模组 → 褚嬴代打：**思考强度**（低 → 极致）、**避和强度**（仅国象）

## 纯客户端原理

- 引擎在客户端本地算招，把走法逆推为棋盘交叉点的 3D 命中坐标，用**原版** `ServerboundUseItemOnPacket`（模拟右键）发送
- 服务器只当玩家在正常点击棋盘，由它已装的 TLM 完成落子 —— **服务器零改动、零依赖**
- 中国象棋/国际象棋为“选子→落子”两步模拟点击，五子棋按棋盘分块偏移换算后点击
- 引擎侧：三家引擎的 `main()` 在构建期被重命名并链接进 JNI 原生库，`std::cin`/`std::cout` 被重定向到内存队列，因此**在游戏进程内跑完整 UCI/pbrain 循环，全程不创建进程**

## 国际化

界面与提示支持简体中文、English、日本語，随游戏语言自动切换。

## 引擎内存说明

引擎采用**懒加载**：首次走子才加载，未触发代打前零引擎内存占用；关闭代打后引擎常驻到游戏退出（退出游戏自动全部释放）。

> 注：JNI 原生库一旦加载便无法在进程内卸载，因此关闭代打后库代码段与已内嵌的 NNUE 权重仍驻留内存，直到退出游戏。

## 开发者：本地构建

引擎二进制不进 git 仓库：由 GitHub Actions `native-build` 工作流在 CI 上拉取引擎源码、编译成三平台 JNI 原生库并上传 artifact；下载后放进 `src/main/resources/engines/<平台>/`，权重等数据文件放 `engines/shared/`。

```bash
# 1. 把 CI 产物 chuying_*.dll|so|dylib 放进 src/main/resources/engines/{windows,linux,macos}/
# 2. 一次构建出三版 jar
./gradlew build
```

产物在 `build/libs/`：`chuying-<版本>.jar`（骨架）+ `Chuying.Proxy.Play<版本>-Forge-1.20.1-{windows,linux,macos}.jar`。

## 许可证

**GPL-3.0-only** —— 内置引擎（Pikafish / Stockfish / Rapfi）同为 GPL-3.0；引擎版本、commit 与构建期补丁见各 jar 内 `engines/<平台>/BUILD_INFO.txt`。
