# 褚嬴代打 (Chuying Proxy Play)

> 老叟戲頑童（ろうそうぎがんどう）：強い外部エンジンに代わりに打ってもらい、東方小紅魔郷のメイドを打ち負かそう。

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-orange)
![Fabric](https://img.shields.io/badge/Fabric-Loader%200.15.11%2B-green)
[![CurseForge](https://img.shields.io/badge/CurseForge-ダウンロード-red)](https://www.curseforge.com/minecraft/mc-mods/chuying-proxy-play)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

**Fabric 1.20.1** 向け [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) のアドオン。メイドの盤上対局で自分の手番になると、内蔵エンジンが自動的に打ってくれます。

> **純クライアント、サーバー側に不要** —— エンジンはローカルで計算し、バニラの操作を模して右クリックを送信するだけ。サーバー側にこの MOD を**入れる必要はありません**（他人のサーバーでも動作、ネットワークチャンネルの不一致も起きません）。

## 特徴

| 特徴 | 説明 |
|---|---|
| **純クライアント** | サーバー側は無変更。シングル・マルチプレイ両対応 |
| **ワンキー代打** | **K** キーでON/OFF。キー設定はゲーム内で変更可能 |
| **思考強度4段階** | 低 / 標準 / 高 / 極限 —— ゲーム内でリアルタイム調整、次の一手から即反映 |
| **引き分け回避（チェス）** | Stockfish に勝利を狙わせ、強制ドローを回避 |
| **エンジン自動展開** | Windows / Linux / macOS 用エンジンを同梱、初回起動時に自動展開 |
| **3言語対応** | 簡体字中国語、English、日本語 |
| **デバッグ HELL** | 五目並べのメイドを最高難易度 HELL に強制（クライアントのみ、テスト用） |

## 対応棋種とエンジン

| 棋種 | エンジン | プロトコル |
|---|---|---|
| 五目並べ | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain |
| 中国将棋（シャンチー） | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI |
| チェス | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI |

## 3プラットフォーム配布

1回のビルドで3種類の jar を生成。お使いのOSに合ったものを選んでください（**間違えないように**）：

| ファイル | 対応OS |
|---|---|
| `Chuying Proxy Play<バージョン>-Fabric-1.20.1-windows.jar` | Windows |
| `Chuying Proxy Play<バージョン>-Fabric-1.20.1-linux.jar` | Linux（x86-64, AVX2） |
| `Chuying Proxy Play<バージョン>-Fabric-1.20.1-macos.jar` | macOS（Apple Silicon） |

> エンジンは初回起動時に `config/chuying/engines/` へ自動展開されます。手動設定は不要です。

> NeoForge 1.21.1 / Forge 1.20.1 版が必要な場合は Releases で対応するブランチ・jar を選んでください。

## 前提条件

- Fabric Loader `0.15.11+`（Minecraft 1.20.1）
- Fabric API
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid)（Orihime Fabric 1.20.1）—— クライアント・サーバー両方に必要（盤面はこれ由来）
- ModMenu・Cloth Config は同梱済み。**別途導入不要**

## 導入方法

1. Fabric 1.20.1（Loader + API）と Touhou Little Maid（Orihime Fabric）を導入
2. [CurseForge](https://www.curseforge.com/minecraft/mc-mods/chuying-proxy-play) / Releases から OS に合う jar をダウンロード
3. `.minecraft/mods/` に配置
4. ゲームを起動

## 使い方

- **K** キーで代打のON/OFF（設定 → 操作 → キー設定 で変更可）
- 盤面のそばへ行き、**手を空けて**（盤面は素手が必要）自動で打ってもらう
- 設定 → MOD → 褚嬴代打 → Config：
  - **思考強度**：低（手加減）→ 標準 → 高 → 極限（高いほど安定・失着が少ない）
  - **引き分け回避**（チェスのみ）：オフ / 穏やか / 積極的 / 極限 —— 強制ドローを回避
  - エンジンパスは独自エンジンで上書き可能（空欄 = 内蔵）

## 純クライアントの仕組み

- エンジンはローカルで計算し、指し手を盤面の3D座標に逆変換して**バニラ**の `ServerboundUseItemOnPacket`（右クリックの模擬）として送信
- サーバーは単にプレイヤーが盤面をクリックしたと見なすだけで、導入済みの TLM が指し手を処理 —— **サーバー側の変更・依存はゼロ**
- 将棋・チェスは「駒選択 → 移動」の2段クリック、五目並べは1回のクリック

## 国際化

UI・メッセージは簡体字中国語・English・日本語に対応し、ゲーム言語に応じて自動切替します。

## 開発者向け：ローカルビルド

エンジン本体は git 管理外。GitHub Actions `engines` ワークフロー（手動実行）で組み立て・アップロードします。

```bash
# 1. 3プラットフォームのエンジンを src/main/resources/engines/{windows,linux,macos}/ と shared/ に配置
# 2. 3種類の jar を一括ビルド
./gradlew build
```

生成物は `build/libs/`：`chuying-<version>.jar`（骨格）+ `-windows.jar` / `-linux.jar` / `-macos.jar`。

## ライセンス

**GPL-3.0-only** —— 内蔵エンジン（Pikafish / Stockfish / Rapfi）も GPL-3.0 です。
