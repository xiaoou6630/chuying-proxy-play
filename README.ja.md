# 褚嬴代打 (Chuying Proxy Play)

> 老叟戲頑童（ろうそうぎがんどう）：強い外部エンジンに代わりに打ってもらい、東方小紅魔郷のメイドを打ち負かそう。

**[简体中文](README.md) | [English](README.en.md) | [日本語](README.ja.md)**

![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-orange)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-green)
![Engines](https://img.shields.io/badge/Engines-Pikafish%20%7C%20Stockfish%20%7C%20Rapfi-brightgreen)

**NeoForge 1.21.1** 向け [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) のアドオン。メイドの盤上対局で自分の手番になると、内蔵エンジンが自動的に打ってくれます。

> **純クライアント、サーバー側に不要** —— エンジンはローカルで計算し、バニラの操作を模して右クリックを送信するだけ。サーバー側にこの MOD を**入れる必要はありません**（他人のサーバーでも動作、ネットワークチャンネルの不一致も起きません）。
>
> **サブプロセスなし（2.0）** —— 3つのエンジンは JNI ネイティブライブラリ（`.dll` / `.so` / `.dylib`）として**ゲームプロセス内で動作**します。外部プロセスは一切起動せず、「独自エンジンパス」設定も廃止しました。

## 特徴

| 特徴 | 説明 |
|---|---|
| **純クライアント** | サーバー側は無変更。シングル・マルチプレイ両対応 |
| **サブプロセスなし** | エンジンは JNI ネイティブライブラリとしてプロセス内で動作。exe は起動しません |
| **ワンキー代打** | **K** キーでON/OFF。キー設定はゲーム内で変更可能 |
| **思考強度4段階** | 低 / 標準 / 高 / 極限 —— ゲーム内でリアルタイム調整、次の一手から即反映 |
| **引き分け回避（チェス）** | Stockfish に勝利を狙わせ、強制ドローを回避 |
| **エンジン自動展開** | Windows / Linux / macOS 用ネイティブライブラリを同梱、初回起動時に `config/chuying/engines/` へ展開 |
| **将棋（しょうぎ）対応** | [tlm_shogi](https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-shogi) アドオン導入時は将棋も代打（同梱の Sunfish を使用、サイズ増加なし） |
| **棋聖 PVP 対応** | [ChessPVP](https://modrinth.com/mod/tlmcp-chesspvp) アドオン導入時は PVP も代打：自分の担当側のみを打つ（同アドオンのスニーククリック操作が必要） |
| **囲碁対応** | [TouhouGO](https://github.com/moyinsky/TouhouGO) 導入時は 15路・数え子の囲碁も代打。[GNU Go](https://www.gnu.org/software/gnugo/) が**プロセス内**で動作 |
| **「一手を通報」（J）** | 碁盤に向かって素手で **J** を1.5秒長押し → 盤面リセット → 一手着手 → メイドの応手をパス扱い → 自分もパス → 双方パスで数え子、**あなたの勝ち** |
| **3言語対応** | 簡体字中国語、English、日本語 |
| **デバッグ HELL** | 五目並べのメイドを最高難易度 HELL に強制（クライアントのみ、テスト用） |

## 対応棋種とエンジン

| 棋種 | エンジン | プロトコル | 備考 |
|---|---|---|---|
| 五目並べ | [Rapfi](https://github.com/dhbloo/rapfi) | Pbrain | jar に同梱 |
| 中国将棋（シャンチー） | [Pikafish](https://github.com/official-pikafish/Pikafish) | UCI | jar に同梱 |
| チェス | [Stockfish](https://github.com/official-stockfish/Stockfish) | UCI | jar に同梱 |
| 将棋 | `tlm_shogi` 同梱の Sunfish | リフレクション | 本 MOD には非同梱、アドオン導入時のみ有効 |
| 囲碁 | [GNU Go](https://www.gnu.org/software/gnugo/) 3.8 | エンジン C API を直接呼び出し（プロセス内） | jar に同梱。盤面は TouhouGO 由来 |

## 3プラットフォーム配布

1回のビルドで3種類の jar を生成。お使いのOSに合ったものを選んでください（**間違えないように**）：

| ファイル | 対応OS |
|---|---|
| `Chuying.Proxy.Play<バージョン>-NeoForge-1.21.1-windows.jar` | Windows |
| `Chuying.Proxy.Play<バージョン>-NeoForge-1.21.1-linux.jar` | Linux（x86-64, AVX2） |
| `Chuying.Proxy.Play<バージョン>-NeoForge-1.21.1-macos.jar` | macOS（Apple Silicon） |

> エンジンは初回起動時に `config/chuying/engines/` へ自動展開されます。手動設定は不要です。

> Forge 1.20.1 / Fabric 1.20.1 版が必要な場合は Releases で対応するブランチ・jar を選んでください。

## 前提条件

- NeoForge `21.1.0+`（Minecraft 1.21.1）
- [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) ≥ `1.3.0` —— クライアント・サーバー両方に必要（盤面はこれ由来）
- **[TouhouGO](https://github.com/moyinsky/TouhouGO)（任意・囲碁のみ）** —— TLM の碁盤アドオン。クライアントとサーバーの両方に導入してください

## 導入方法

1. NeoForge 1.21.1 と Touhou Little Maid を導入
2. Releases から**ローダー + OS** に合う jar をダウンロード
3. `.minecraft/mods/` に配置
4. ゲームを起動

## 使い方

- **K** キーで代打のON/OFF（設定 → 操作 → キー設定 で変更可）
- 盤面のそばへ行き、**手を空けて**（盤面は素手が必要）自動で打ってもらう
- 将棋盤でも同様に動作します（`tlm_shogi` アドオンが必要）。成りの選択は自動で応答します
- `ChessPVP` アドオン導入時も同様：両者が参加し、自分の担当側（紅/白 または 黒）の手番のときのみ代打します。観戦者は介入しません
- `TouhouGO` 導入時は囲碁も代打：**スニークを押しながら打たないでください**（スニーク + 素手クリックは「パス」扱い。代打はキーを離すまで待ちます）。エンジンがパスを選んだときは代打がパスします
- 代打は**終局も自動で締めます**：GNU Go が「打つ手なし」と判断し、MOD の数え子であなたがリードしているとき、メイドの応手もパス扱いにして双方パス → 数え子で**あなたの勝ち**（画面に金色 `收工判胜!`）
- **囲碁「一手を通報」**（ネタ機能）：碁盤に向かって素手で **J** を1.5秒長押し（画面上にプログレスバーとカウントダウン、途中で離すとキャンセル）→ 石入れ（棋盒）をクリックして盤面リセット → 一手着手 → メイドの応手をパス扱い → 自分もパス → 双方パス → 数え子で**あなたの勝ち**（赤い `举报一手!`）。現在の対局はリセットされますが、勝敗はあなたの勝ちとして記録されます
- 設定 → MOD → 褚嬴代打 → Config：
  - **思考強度**：低（手加減）→ 標準 → 高 → 極限（高いほど安定・失着が少ない）。将棋も同じ段階（標準 = 10秒 / 深さ20）。囲碁は GNU Go の level 8 / 9 / 10 に対応
  - **引き分け回避**（チェスのみ）：オフ / 穏やか / 積極的 / 極限 —— 強制ドローを回避
  - **将棋代打**：`tlm_shogi` 導入時のみ有効。オフにすると将棋には介入しません
  - **囲碁代打**：`TouhouGO` 導入時のみ有効。オフにすると囲碁には介入しません

## 純クライアントの仕組み

- エンジンはローカルで計算し、指し手を盤面の3D座標に逆変換して**バニラ**の `ServerboundUseItemOnPacket`（右クリックの模擬）として送信
- サーバーは単にプレイヤーが盤面をクリックしたと見なすだけで、導入済みの TLM が指し手を処理 —— **サーバー側の変更・依存はゼロ**
- シャンチー・チェスは「駒選択 → 移動」の2段クリック。五目並べ・将棋は各盤面の分割オフセットで座標換算してクリック
- エンジン側：各エンジンの `main()` をビルド時に改名して JNI ライブラリへリンクし、`std::cin`/`std::cout` をメモリ上のキューへリダイレクト。これにより UCI/pbrain ループが**ゲームプロセス内で完結し、プロセスを一切生成しません**
- GNU Go は `FILE*` ストリームで動く C プログラムです（Windows/MinGW には `fopencookie`/`funopen` が無く、プロセス内で標準入出力を差し替えられません）。そのためブリッジはエンジンの C API を直接呼びます：`init_gnugo()` → 現局面の全石とコウ点を `add_stone()` で投入 → `genmove()`（`native/src/gnugo_bridge.cpp`）—— こちらも**サブプロセス無し・パイプ無し・テキストプロトコル無し**
- **TouhouGO 独自プロトコルと競合しません**：着手はバニラの `ServerboundUseItemOnPacket` のみ、メイドの応手は同 MOD 自身のクライアント AI が計算し、本 MOD は `go_to_client` / `go_to_server` チャンネルを登録も傍受もしません。唯一の例外が「一手を通報」で、これは**メイドの応手という建前で**負の座標を送るもの（同 MOD の「メイドのパス」と等価。サーバーは手番と合法手のみ検証）で、プレイヤーが明示的に押したときだけ動くネタ機能です。プロトコルの項目を変更・追加しません

## 国際化

UI・メッセージは簡体字中国語・English・日本語に対応し、ゲーム言語に応じて自動切替します。

## 開発者向け：ローカルビルド

エンジン本体は git 管理外です。GitHub Actions `native-build` ワークフローが CI 上でエンジンソースを取得して3プラットフォームの JNI ライブラリをビルドし、artifact としてアップロードします。

JNI ブリッジと CMake スクリプト（`native/CMakeLists.txt`、`native/src/*.cpp`）は**本リポジトリで管理**しています（2.1 以降はリリースタグ内にも含まれます）。ビルド時に当てるパッチはすべて `.github/scripts/build_native.sh` にあります。

```bash
# 1. CI の成果物 chuying_*.dll|so|dylib を src/main/resources/engines/{windows,linux,macos}/ に配置
#    重みなどのデータは engines/shared/ に
# 2. 3種類の jar を一括ビルド
./gradlew build
```

生成物は `build/libs/`：`chuying-<version>.jar`（骨格）+ `Chuying.Proxy.Play<バージョン>-NeoForge-1.21.1-{windows,linux,macos}.jar`。

## ライセンス

**GPL-3.0-only** —— 内蔵エンジン（Pikafish / Stockfish / Rapfi / GNU Go）も GPL（GNU Go は GPL-3.0-or-later で、GPLv3 の "v3" オプションにより全体へリンク可能＝互換）。同一プロセスへリンクしているため、全体を GPL-3.0 として配布します。エンジンのバージョン・正確な commit・tar の sha256・ビルド時の全パッチは `THIRD_PARTY_LICENSES.txt` と、ネイティブライブラリに同梱される `BUILD_INFO.txt` に記載しています。

**対応ソース（GPLv3 Corresponding Source）**：エンジンのソースは上流から取得（GNU Go = 公式 tar、sha256 `da68d7a6…6a72` 固定）。本 MOD の改変（`native/src/*.cpp` の JNI ブリッジと `native/CMakeLists.txt`）およびパッチスクリプト `.github/scripts/build_native.sh` は本リポジトリにあり、任意のリリースタグ（例：`2.1`）から完全に取得できます。
