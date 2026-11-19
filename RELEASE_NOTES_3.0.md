# 车万女仆：褚嬴代打 3.0

## 一个 jar 通吃所有系统

从 3.0 起**不再按系统分版本**。原来 Windows / Linux / macOS 三个 jar 各带一份引擎（合计 1219 MB），
现在合并成**一个 jar（约 129 MB）**，下载即用：

```
Chuying.Proxy.Play3.0-NeoForge-1.21.1-universal.jar
```

支持：Windows x64 / Linux x64 / macOS（**Apple Silicon 和 Intel Mac 都支持**）。
首次运行只解压当前系统需要的那份引擎到 `config/chuying/engines/`。

## 修了 Intel Mac 加载不了引擎的问题

2.1 的 macOS jar 里，四个引擎库都只有 **arm64** 一个架构（CI 的 `macos-latest` 是 Apple Silicon 机器）。
Intel Mac 上 `System.load()` 会直接 `UnsatisfiedLinkError`，代打完全不可用。

3.0 的 macOS 引擎改为 **universal 二进制**（arm64 + x86_64 合在一个文件里），
CI 构建后会校验每个 `.dylib` 的 `lipo -archs` 确实含两个架构，不含就直接让构建失败。

## 修了中文路径下引擎加载不了权重的问题

这是排查 Stockfish 权重外置时发现的**老 bug**（2.0/2.1 就存在，只是被内嵌权重掩盖了）：
游戏装在中文路径下时（比如 `E:\游戏\我的世界\`），引擎打开权重文件会失败。
三个引擎在 Windows 上都用 ANSI 窄字符文件 API，不认 UTF-8 路径：

| 引擎 | 表现 |
|---|---|
| Stockfish | `ERROR: Network evaluation parameters ... must be available`，然后**永远不出 bestmove**（代打卡死） |
| Pikafish | 直接 `std::exit`，**把整个 JVM 杀掉** |
| Rapfi | `config.toml` 里的模型路径打不开，启动即失败 |

3.0 的修法：`EngineExtractor` 启动时检测引擎目录路径，含非 ASCII 字符就整棵目录改到
`%TEMP%\chuying-engines\`（纯 ASCII）。只处理路径不处理文件，因为 `config.toml` 内部也含路径。

## Stockfish 权重不再编进二进制

Stockfish 17.1 默认用 `INCBIN` 把 75 MB 的 NNUE 权重编译进库文件。
三个平台各编一份就是 **225 MB 的纯重复**——这是体积的大头。

3.0 编译时定义 `NNUE_EMBEDDING_OFF`，权重改为独立文件随 jar 分发，
运行时由 Java 侧通过 `setoption name EvalFile / EvalFileSmall` 指向解压后的路径：

| | 2.1 | 3.0 |
|---|---|---|
| 单平台 Stockfish 库 | 76 MB | **约 1 MB** |
| 权重存放 | 每个库各一份 | **三平台共用一份** |
| 发布总量 | 1219 MB（9 个 jar） | **约 129 MB（1 个 jar）** |

## 引擎层模块化

以前加引擎要在 `EngineExtractor`、`EngineManager`、`CMakeLists`、`jni_exports` 四处同步改。
现在引擎是**登记表驱动**的，加引擎只改 `EngineRegistry.java` 一个文件：

```java
public static final EngineSpec SHOGI_ENGINE =
        EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue");
```

`EngineExtractor`（解压哪些文件）和 `EngineManager`（怎么创建实例）都从登记表推导。
协议族目前有 `UCI` / `GOMOKU` / `GO` 三种，新引擎属于这三族就只加一行；全新协议族才需要新写包装类。

## 更抗崩

- **棋种分派层加了总兜底**：以前 `onClientTick` 里调用各棋种适配层的外层没有 try/catch，
  而我们大量用反射调第三方模组（TouhouGO / tlm_shogi / ChessPVP）。
  反射失败抛的 `NoClassDefFoundError` / `NoSuchMethodError` 都是 `Error` 而不是 `Exception`，
  一旦漏出来会直接崩客户端。现在外层 `catch (Throwable)`，异常只让当次代打跳过，日志限流 10 秒一条。
- **唯一的 mixin 加了 `require = 0`**：TLM 改了方法签名时，调试开关静默失效，而不是启动阶段报
  `MixinApplyError` 打不开游戏。

## 其他

- 版本号 2.1 → 3.0（jar 命名与分发方式变化，按大版本处理）
- CI 改为只在 `ci-tmp-*` 临时分支上编译，编译完自动删除该分支；且**只编原生库，jar 在本地打**
  （新增 `package-local.ps1` 一键装引擎 + 打包）
- 五棋种（五子棋 / 中国象棋 / 国际象棋 / 将棋 / 围棋）与「举报一手」功能与 2.1 一致

---

# Chuying Proxy Play 3.0 (English)

## One jar for every OS

Starting with 3.0 there are no per-OS builds. Previously three jars each carried their own copy
of the engines (1219 MB total); now it is **a single jar (~129 MB)**:

```
Chuying.Proxy.Play3.0-NeoForge-1.21.1-universal.jar
```

Supports Windows x64 / Linux x64 / macOS (**both Apple Silicon and Intel Macs**).
On first run only the engine copy for the current OS is extracted to `config/chuying/engines/`.

## Fixed: engines failed to load on Intel Macs

In 2.1 the macOS jar contained all four engine libraries as **arm64 only** (CI's `macos-latest`
runner is Apple Silicon). On an Intel Mac `System.load()` failed with `UnsatisfiedLinkError`
and proxy play was completely unusable.

3.0 ships the macOS engines as **universal binaries** (arm64 + x86_64 in one file). The CI build
verifies every `.dylib` with `lipo -archs` and fails the build if a slice is missing.

## Fixed: engines could not load their weights from a non-ASCII path

Found while moving the Stockfish nets out of the binary, but the bug is older (present in 2.0/2.1,
just masked by the embedded nets): when the game is installed under a path with non-ASCII characters
(e.g. `E:\游戏\我的世界\`), the engines fail to open their weight files. All three use the ANSI
narrow-char file API on Windows and cannot handle UTF-8 paths:

| Engine | Symptom |
|---|---|
| Stockfish | `ERROR: Network evaluation parameters ... must be available`, then **never emits `bestmove`** (proxy play hangs) |
| Pikafish | calls `std::exit` and **kills the whole JVM** |
| Rapfi | cannot open the model paths inside `config.toml`, fails at startup |

Fix: `EngineExtractor` checks the engine directory path at startup; if it contains non-ASCII
characters the whole tree moves to `%TEMP%\chuying-engines\` (pure ASCII). The directory is
relocated rather than individual files because `config.toml` also contains paths.

## Stockfish nets are no longer embedded

Stockfish 17.1 embeds its 75 MB NNUE net into the binary via `INCBIN`. Three platforms meant
**225 MB of pure duplication** — the bulk of the download.

3.0 compiles with `NNUE_EMBEDDING_OFF`; the nets ship as separate files shared by all platforms
and are loaded at runtime through `setoption name EvalFile / EvalFileSmall`:

| | 2.1 | 3.0 |
|---|---|---|
| Stockfish library (per platform) | 76 MB | **~1 MB** |
| Net storage | one copy per binary | **one copy shared by all** |
| Total release size | 1219 MB (9 jars) | **~129 MB (1 jar)** |

## Modular engine layer

Adding an engine used to require synchronized edits in four places. Engines are now
**registry-driven** — adding one touches only `EngineRegistry.java`:

```java
public static final EngineSpec SHOGI_ENGINE =
        EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue");
```

`EngineExtractor` (what to extract) and `EngineManager` (how to instantiate) both derive from the
registry. Protocol families are `UCI` / `GOMOKU` / `GO`; an engine in one of these families needs
just one line, and only a brand-new protocol needs a new wrapper class.

## Harder to crash

- **Top-level guard around game dispatch**: the outer call into each game adapter had no
  try/catch, and we reflect heavily into third-party mods (TouhouGO / tlm_shogi / ChessPVP).
  Reflection failures throw `NoClassDefFoundError` / `NoSuchMethodError` — `Error`s, not
  `Exception`s — which would crash the client. It is now caught as `Throwable`; a failure skips
  that one proxy action and logs at most once per 10 seconds.
- **The single mixin uses `require = 0`**: if TLM changes the method signature, the debug toggle
  silently does nothing instead of failing startup with `MixinApplyError`.

## Other

- Version 2.1 → 3.0 (jar naming and distribution model changed, treated as a major release)
- CI now builds on a temporary `ci-tmp-*` branch that is deleted afterwards, and **only builds the
  native libraries — the jar is built locally** (new `package-local.ps1` installs engines and builds)
- The five games (Gomoku / Xiangqi / Chess / Shogi / Go) and the "report a move" feature are unchanged

---

# 褚嬴代打 3.0（日本語）

## すべての OS で 1 つの jar

3.0 以降、OS 別の配布はありません。以前は 3 つの jar がそれぞれエンジンを同梱していました
（合計 1219 MB）。現在は**単一の jar（約 129 MB）**です：

```
Chuying.Proxy.Play3.0-NeoForge-1.21.1-universal.jar
```

対応：Windows x64 / Linux x64 / macOS（**Apple Silicon と Intel Mac の両方**）。
初回起動時、現在の OS 用のエンジンだけが `config/chuying/engines/` へ展開されます。

## 修正：Intel Mac でエンジンが読み込めなかった問題

2.1 の macOS 用 jar では、4 つのエンジンライブラリがすべて **arm64 のみ**でした
（CI の `macos-latest` は Apple Silicon マシン）。Intel Mac では `System.load()` が
`UnsatisfiedLinkError` で失敗し、代打がまったく使えませんでした。

3.0 では macOS 用エンジンを **universal バイナリ**（arm64 + x86_64 を 1 ファイルに格納）で
配布します。CI は各 `.dylib` を `lipo -archs` で検証し、アーキテクチャが欠けていればビルドを失敗させます。

## 修正：非 ASCII パスでエンジンが重みを読み込めなかった問題

Stockfish の重みをバイナリ外へ出す作業中に見つかった**古いバグ**です（2.0/2.1 から存在し、
重みが埋め込まれていたため表面化していませんでした）。ゲームが日本語・中国語などの
非 ASCII パス（例 `E:\游戏\我的世界\`）にインストールされていると、エンジンが重みファイルを
開けません。3 エンジンとも Windows では ANSI のナローファイル API を使っており、
UTF-8 パスを扱えません：

| エンジン | 症状 |
|---|---|
| Stockfish | `ERROR: Network evaluation parameters ... must be available` の後、**bestmove を永久に返さない**（代打が停止） |
| Pikafish | `std::exit` を呼び、**JVM ごと落ちる** |
| Rapfi | `config.toml` 内のモデルパスを開けず起動失敗 |

修正：`EngineExtractor` が起動時にエンジンディレクトリのパスを検査し、非 ASCII 文字を含む場合は
ツリー全体を `%TEMP%\chuying-engines\`（純 ASCII）へ移します。`config.toml` 自体にも
パスが含まれるため、個別ファイルではなくディレクトリごと移しています。

## Stockfish の重みをバイナリに埋め込まない

Stockfish 17.1 は既定で 75 MB の NNUE 重みを `INCBIN` でバイナリに埋め込みます。
3 プラットフォーム分で **225 MB の単純な重複**となり、サイズの大部分を占めていました。

3.0 は `NNUE_EMBEDDING_OFF` でコンパイルし、重みは全プラットフォームで共有する独立ファイル
として同梱、実行時に `setoption name EvalFile / EvalFileSmall` で読み込みます：

| | 2.1 | 3.0 |
|---|---|---|
| Stockfish ライブラリ（1 プラットフォーム） | 76 MB | **約 1 MB** |
| 重みの保存 | バイナリごとに 1 部 | **全プラットフォームで 1 部を共有** |
| リリース全体 | 1219 MB（9 jar） | **約 129 MB（1 jar）** |

## エンジン層のモジュール化

以前はエンジン追加時に 4 か所を同期して修正する必要がありました。現在は**レジストリ駆動**で、
`EngineRegistry.java` の 1 ファイルだけを変更します：

```java
public static final EngineSpec SHOGI_ENGINE =
        EngineSpec.of("xxx", "chuying_xxx", EngineKind.UCI, "xxx.nnue");
```

`EngineExtractor`（何を展開するか）と `EngineManager`（どう生成するか）はレジストリから導出されます。
プロトコル系統は `UCI` / `GOMOKU` / `GO` の 3 種類で、これらに属するエンジンは 1 行で追加できます。

## クラッシュ耐性の向上

- **棋種ディスパッチに最上位のガードを追加**：各棋種アダプタを呼ぶ外側に try/catch がなく、
  サードパーティ MOD（TouhouGO / tlm_shogi / ChessPVP）へリフレクションで頻繁にアクセスしていました。
  リフレクション失敗で投げられる `NoClassDefFoundError` / `NoSuchMethodError` は
  `Exception` ではなく `Error` であり、そのままクライアントが落ちます。現在は `Throwable` で捕捉し、
  その回の代打をスキップするだけ（ログは 10 秒に 1 回まで）にしました。
- **唯一の mixin に `require = 0` を付与**：TLM がメソッドシグネチャを変更しても、
  起動時に `MixinApplyError` でゲームが開けなくなる代わりに、デバッグスイッチが無効になるだけです。

## その他

- バージョン 2.1 → 3.0（jar 名と配布方式の変更に伴いメジャー扱い）
- CI は一時ブランチ `ci-tmp-*` でビルドし、完了後に自動削除。さらに**ネイティブライブラリのみを
  ビルドし、jar はローカルで作成**します（`package-local.ps1` を追加）
- 5 棋種（五目並べ / シャンチー / チェス / 将棋 / 囲碁）と「一手を通報」機能は 2.1 と同じ
