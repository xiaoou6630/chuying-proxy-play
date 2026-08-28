# Chuying Proxy Play 1.0.0 (褚嬴代打)

> 老叟戏顽童：让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。

首次发布，为 **NeoForge 1.21.1** 的 [Touhou Little Maid（车万女仆）](https://modrinth.com/mod/touhou-little-maid) 棋类游戏接入外部强引擎自动落子。

## 功能

- **纯客户端，服务器免装** —— 引擎本地算招，通过原版交互模拟点击落子，服务器不需要装本 mod
- **一键代打**：K 键启停，可在 控制 里改键
- **思考强度四档**：低 / 默认 / 高 / 极致，游戏内实时调整
- **避和强度（国际象棋）**：让 Stockfish 主动求胜、拒绝被拖平
- **引擎自动解压**：内置 Windows / Linux / macOS 引擎，开箱即用
- **三语界面**：简体中文 / English / 日本語

## 支持的棋种

| 棋种 | 引擎 |
|---|---|
| 五子棋 | Rapfi |
| 中国象棋 | Pikafish（皮卡鱼） |
| 国际象棋 | Stockfish |

## 下载（按系统选择）

- **Windows**：`Chuying Proxy Play1.0.0-windows.jar`
- **Linux**（x86-64, AVX2）：`Chuying Proxy Play1.0.0-linux.jar`
- **macOS**（Apple Silicon）：`Chuying Proxy Play1.0.0-macos.jar`

## 安装

1. 安装 NeoForge 1.21.1 + Touhou Little Maid
2. 下载对应系统的 jar，放入 `.minecraft/mods/`
3. 进入游戏，走到棋盘旁，**空手**、按 **K** 开启代打，准星对准棋盘即可自动落子

## 依赖

- NeoForge `21.1.0+`
- Touhou Little Maid（1.21.1）—— 客户端与服务端都需要（棋盘来自它）

## 许可证

GPL-3.0-only —— 内置引擎（Pikafish / Stockfish / Rapfi）同为 GPL-3.0。
