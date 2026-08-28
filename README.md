# 褚嬴代打 (Chuying Proxy Play)

> 老叟戏顽童：让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-orange)
![Fabric](https://img.shields.io/badge/Fabric-Loader%200.15.11%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

一个基于 **Fabric 1.20.1** 的 [Touhou Little Maid（车万女仆）](https://modrinth.com/mod/touhou-little-maid) 附属模组：检测到女仆棋局轮到你走子时，自动调用内置引擎替你落子。

> **纯客户端，服务器免装** —— 引擎在本地算招，再通过原版交互模拟你右键棋盘，因此服务器**不需要**安装本模组（连别人的服务器也能用，不会报网络通道不匹配）。

## 特性

| 特性 | 说明 |
|---|---|
| **纯客户端** | 服务器零改动，单机/联机都能用 |
| **一键代打** | **K** 键随时启停，可在 控制 里改键 |
| **思考强度四档** | 低 / 默认 / 高 / 极致 —— 游戏内实时调整，下一步立即生效 |
| **避和强度（国象）** | 让 Stockfish 主动求胜、拒绝被拖平 |
| **引擎自动解压** | 内置 Windows / Linux / macOS 引擎，开箱即用 |
| **三语界面** | 简体中文、English、日本語 |
| **调试 HELL** | 强制五子棋女仆最高难度 HELL（纯客户端，测试用） |

## 支持的棋种与引擎

| 棋种 | 引擎 | 协议 |
|---|---|---|
| 五子棋 | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain |
| 中国象棋 | [皮卡鱼 Pikafish](https://github.com/official-pikafish/Pikafish) | UCI |
| 国际象棋 | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI |

## 三平台分发包

一次构建产出三版 jar，按你的系统选择对应版本，**别下错了**：

| 文件 | 适用系统 |
|---|---|
| `Chuying Proxy Play<版本>-Fabric-1.20.1-windows.jar` | Windows |
| `Chuying Proxy Play<版本>-Fabric-1.20.1-linux.jar` | Linux（x86-64, AVX2） |
| `Chuying Proxy Play<版本>-Fabric-1.20.1-macos.jar` | macOS（Apple Silicon） |

> 引擎首次运行时自动解压到 `config/chuying/engines/`，无需手动配置。

> 需要 NeoForge 1.21.1 或 Forge 1.20.1 版本？请到 Releases 选择对应分支 / 发行页的 jar。

## 依赖

- Fabric Loader `0.15.11+`（Minecraft 1.20.1）
- Fabric API
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid)（Orihime Fabric 1.20.1）—— 客户端与服务端都需要（棋盘来自它）
- ModMenu 与 Cloth Config 已内嵌，**无需**另行安装

## 安装

1. 安装 Fabric 1.20.1（Fabric Loader + Fabric API）与 Touhou Little Maid（Orihime Fabric）
2. 从 Releases 下载与系统匹配的 jar
3. 放入 `.minecraft/mods/`
4. 启动游戏

## 使用

- 按 **K** 开启/关闭代打（可在 设置 → 控制 → 按键绑定 修改）
- 走到棋盘旁，**保持空手**（棋盘本身要求空手操作），代打会自动落子
- 设置 → 模组 → 褚嬴代打 → Config：
  - **思考强度**：低（放水）→ 默认 → 高 → 极致（越高越稳、越少失子）
  - **避和强度**（仅国象）：关闭 / 温和 / 激进 / 极致 —— 避免强制和棋
  - 引擎路径可覆盖为自定义引擎（留空 = 内置）

## 纯客户端原理

- 引擎在客户端本地算招，把走法逆推为棋盘交叉点的 3D 命中坐标，用**原版** `ServerboundUseItemOnPacket`（模拟右键）发送
- 服务器只当玩家在正常点击棋盘，由它已装的 TLM 完成落子 —— **服务器零改动、零依赖**
- 中国象棋/国际象棋为"选子→落子"两步模拟点击，五子棋为单次点击

## 国际化

界面与提示支持简体中文、English、日本語，随游戏语言自动切换。

## 开发者：本地构建

引擎二进制不进 git 仓库，由 GitHub Actions `engines` 工作流（手动触发）组装并上传 artifact。

```bash
# 1. 把三平台引擎放入 src/main/resources/engines/{windows,linux,macos}/ 与 shared/
# 2. 一次构建出三版 jar
./gradlew build
```

产物在 `build/libs/`：`chuying-<版本>.jar`（骨架）+ `-windows.jar` / `-linux.jar` / `-macos.jar`。

## 许可证

**GPL-3.0-only** —— 内置引擎（Pikafish / Stockfish / Rapfi）同为 GPL-3.0。
