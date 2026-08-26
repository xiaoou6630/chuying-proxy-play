# 褚嬴代打 (Chuying Proxy Play)

> 老叟戏顽童：让外部强引擎替你下棋，虐翻 Touhou Little Maid 的女仆。

一个 [NeoForge 1.21.1](https://projects.neoforged.net/neoforged/neoforge) 客户端模组，在 Touhou Little Maid（车万女仆）的棋类小游戏里，检测到轮到你走子时自动调用外部引擎落子。

**纯客户端实现**：引擎在本地算招后，模拟"玩家右键棋盘交叉点"走 Minecraft 原版交互，**服务器无需安装本 mod**（连别人的服务器也能用，不会报网络通道不匹配）。

## 支持的棋种与引擎

| 棋种 | 引擎 | 协议 |
|---|---|---|
| 中国象棋 | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI |
| 国际象棋 | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI |
| 五子棋 | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain |

## 三平台分发包

一次构建产出三版 jar，按你的系统选择对应版本，**别下错了**：

| 文件 | 适用系统 | 说明 |
|---|---|---|
| `chuying-<版本>-windows.jar` | Windows | 内置 Windows 引擎 |
| `chuying-<版本>-linux.jar` | Linux | 内置 Linux 引擎（x86-64, AVX2） |
| `chuying-<版本>-macos.jar` | macOS | 内置 Apple Silicon 引擎 |

> 引擎在首次运行时自动解压到 `config/chuying/engines/`，无需手动配置。

## 依赖

- NeoForge `21.1.0+`（1.21.1）
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid)（1.21.1 版本，放到 `mods/`）

## 安装

1. 安装 NeoForge 1.21.1 与 Touhou Little Maid
2. 从 Releases / CI 产物下载与你系统匹配的 jar
3. 把 jar 放入 `.minecraft/mods/`
4. 启动游戏

## 使用

- 默认按 **K** 键开启/关闭代打（可在 设置 → 控制 → 按键绑定 修改）
- 进入女仆棋局，轮到你的回合会自动落子；**请保持空手**（棋盘本身要求空手操作，主手有物品时会在屏幕提示）
- 思考强度：设置 → 模组 → 褚嬴代打 → Config，四档实时生效，无需重启
  - **低**（放水）→ **默认** → **高** → **极致**（越高越稳、越少失子）
- 避和强度（仅国际象棋）：让 Stockfish 主动求胜、避免强制和棋，同样四档实时生效
  - **关闭** → **温和** → **激进** → **极致**
- 调试：可强制五子棋女仆用最高难度 HELL（纯客户端，不改服务端数据）
- 引擎路径可在 Config 里覆盖为自定义引擎（留空则使用内置引擎）

## 纯客户端原理

- 引擎在客户端本地算招，走法逆推为棋盘交叉点的 3D 命中坐标，用原版 `ServerboundUseItemOnPacket` 模拟玩家右键
- 服务端（内置或远程）只当玩家在正常点击棋盘，由它已装的 TLM 完成落子——**因此服务器零改动、零依赖**
- 中国象棋/国际象棋为"选子→落子"两步模拟点击，五子棋为单次点击

## 国际化

界面与提示支持简体中文、English、日本語，随游戏语言自动切换。

## 开发者：本地构建

引擎二进制不进 git 仓库（见 `.gitignore`），由 GitHub Actions `engines` 工作流（手动触发）组装并上传 artifact。

```bash
# 1. 准备引擎：把三平台引擎放入 src/main/resources/engines/{windows,linux,macos}/
#    和 shared/（目录缺失的版本会自动跳过，打包为空 jar）
# 2. 一次构建出三版 jar
./gradlew build
```

产物在 `build/libs/`：

- `chuying-<版本>.jar` — 纯代码骨架（不含引擎，约 0.2MB）
- `chuying-<版本>-windows.jar` / `-linux.jar` / `-macos.jar` — 分平台完整版

## 许可证

GPL-3.0-only

内置引擎（Pikafish / Stockfish / Rapfi）均为 GPL-3.0 协议，故本模组一并采用 GPL-3.0。
