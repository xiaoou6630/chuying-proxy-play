package com.chuying.engine;

import com.chuying.Chuying;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * 把打包在 jar 里的原生引擎库与权重数据首次运行时解压到 {@code config/chuying/engines/}。
 * <p>
 * <b>3.0 起一个 jar 通吃所有系统</b>（不再按 windows/linux/macos 切分 jar）：
 * 引擎与权重全部打进同一个 jar，运行时只解压当前系统 + 架构需要的那一份。
 * 这与 Sable 的做法同源，但更简单——jar 本身就是 zip，条目天然是 deflate 压缩的，
 * 不需要再套一层 LZ4 压缩包。
 *
 * <h2>jar 内目录结构</h2>
 * <pre>
 * engines/
 * ├── shared/                       # 全平台共用（只打包一份，这是省体积的关键）
 * │   ├── pikafish.nnue             # 皮卡鱼权重（48 MB）
 * │   ├── stockfish/                # Stockfish 两份 NNUE
 * │   │   ├── nn-1c0000000000.nnue  #   大网 71 MB
 * │   │   └── nn-37f18f62d772.nnue  #   小网 3.4 MB
 * │   └── rapfi/                    # config.toml 模板 + 模型
 * ├── windows-x86_64/               # 各平台原生库（目录名 = os-arch）
 * ├── linux-x86_64/
 * └── macos-universal/              # 一个文件同时含 arm64 + x86_64（修 Intel Mac）
 * </pre>
 *
 * <h2>为什么要关掉 Stockfish 的权重内嵌</h2>
 * Stockfish 17.1 默认用 {@code INCBIN} 把 75 MB 的 NNUE 编进二进制。三平台各编一份，
 * 就是 225 MB 的重复。CI 现在用 {@code -DNNUE_EMBEDDING_OFF} 编译，权重改为运行时
 * 由 {@code setoption name EvalFile} 指向解压出的文件——单库从 76 MB 降到约 1 MB。
 */
public final class EngineExtractor {
    private static final String SUB_DIR = "config/chuying/engines";

    /** 当前平台标识（os-arch），用于定位 jar 内与磁盘上的平台目录 */
    private static final String PLATFORM = detectPlatform();
    /** 原生库后缀 */
    private static final String LIB_EXT = detectLibExt();

    private static Path enginesDir;

    private EngineExtractor() {
    }

    /**
     * 平台标识：{@code windows-x86_64} / {@code linux-x86_64} / {@code macos-universal}。
     * <p>
     * macOS 发的是 universal 二进制，所以不看架构；其它系统带上架构，
     * 以后加 windows-arm64 / linux-arm64 时目录名自动对得上。
     */
    public static String platform() {
        return PLATFORM;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows-" + arch();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos-universal";
        }
        return "linux-" + arch();
    }

    /** 归一化架构名：amd64/x86_64 → x86_64，aarch64/arm64 → arm64 */
    private static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) {
            return "arm64";
        }
        return "x86_64";
    }

    private static String detectLibExt() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return ".dylib";
        }
        return ".so";
    }

    public static synchronized Path enginesDir() {
        if (enginesDir == null) {
            Path base = Minecraft.getInstance().gameDirectory.toPath();
            Path dir = base.resolve(SUB_DIR);
            if (!isAscii(dir.toAbsolutePath().toString())) {
                // 游戏目录含中文（国内极常见）。三个引擎在 Windows 上都用 ANSI 窄字符
                // 文件 API 打开权重/模型，UTF-8 中文路径必然失败：
                //   * Stockfish: "ERROR: Network evaluation parameters ... must be available"
                //     然后永远不出 bestmove（表现为代打卡死）
                //   * Pikafish:  直接 std::exit，把整个 JVM 杀掉
                //   * Rapfi:     config.toml 里的模型路径同样打不开
                // 因此整棵引擎目录改放 ASCII 安全位置，不逐文件处理（config.toml 内含路径）。
                Path safe = asciiSafeDir();
                Chuying.LOGGER.warn("[chuying] 游戏目录含非 ASCII 字符，引擎改解压到 ASCII 安全位置: {}"
                        + "（原路径引擎无法读取权重）", safe);
                dir = safe;
            }
            extract(dir);
            enginesDir = dir;
        }
        return enginesDir;
    }

    /**
     * 返回原生引擎库解压后的绝对路径，未找到返回 null。
     * {@code name} 为库的基名（如 "chuying_pikafish"），平台后缀自动补全。
     */
    public static String nativeLibPath(String name) {
        Path target = enginesDir().resolve(PLATFORM).resolve(name + LIB_EXT);
        if (Files.exists(target)) {
            return target.toString();
        }
        Chuying.LOGGER.error("内置引擎库缺失：{}（当前平台 {}）——本 jar 可能不含该系统，"
                + "或系统架构不受支持（如 Windows/Linux ARM64）", target, PLATFORM);
        return null;
    }

    /**
     * 返回平台目录下数据文件（权重/模型）的绝对路径，未找到返回 null。
     * {@code rel} 为平台目录内相对路径（如 "pikafish.nnue"、"stockfish/nn-1c0000000000.nnue"）。
     * <p>
     * 游戏目录含非 ASCII 字符时，{@link #enginesDir()} 已经把整棵引擎目录改到
     * ASCII 安全位置，这里拿到的自然也是安全路径。
     */
    public static String dataFilePath(String rel) {
        Path target = enginesDir().resolve(PLATFORM).resolve(rel.replace('/', java.io.File.separatorChar));
        return Files.exists(target) ? target.toString() : null;
    }

    /** 纯 ASCII 判定（引擎的窄字符文件 API 只认本地代码页） */
    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * ASCII 安全的引擎目录：{@code <java.io.tmpdir>/chuying-engines/}。
     * <p>
     * 只在主路径含非 ASCII 时才用到。用临时目录而不是游戏目录，
     * 因为游戏目录很可能就装在中文路径下（本问题的来源）。
     */
    private static Path asciiSafeDir() {
        String tmp = System.getProperty("java.io.tmpdir", ".");
        if (!isAscii(tmp)) {
            // 系统临时目录也是中文：退到盘符根目录
            String drive = System.getProperty("user.home", "C:\\");
            int colon = drive.indexOf(':');
            tmp = colon > 0 ? drive.substring(0, colon + 2) : "C:\\";
        }
        return Path.of(tmp, "chuying-engines", PLATFORM);
    }

    /** Rapfi 的 config.toml 解压后路径（模型路径已改写为绝对路径），未就绪返回 null */
    public static String rapfiConfigPath() {
        Path target = enginesDir().resolve(PLATFORM).resolve("rapfi").resolve("config.toml");
        return Files.exists(target) ? target.toString() : null;
    }

    // ------------------------------------------------------------------
    // 解压
    // ------------------------------------------------------------------

    private static void extract(Path dir) {
        Path sharedDir = dir.resolve("shared");
        Path platDir = dir.resolve(PLATFORM);

        extractShared(sharedDir);
        extractPlatformLibs(platDir);
        copySharedToPlatform(sharedDir, platDir);
    }

    /**
     * 解压 {@code engines/shared/} 下的全部权重/模型。
     * 清单来自 {@link EngineRegistry}——加引擎时只改那里，这里不用动。
     */
    private static void extractShared(Path sharedDir) {
        for (EngineSpec spec : EngineRegistry.ALL) {
            for (String rel : spec.dataFiles()) {
                String res = "engines/shared/" + rel;
                Path target = sharedDir.resolve(rel.replace('/', java.io.File.separatorChar));
                extractOne(res, target);
            }
        }
    }

    /** 解压当前平台的四个原生库 */
    private static void extractPlatformLibs(Path platDir) {
        for (EngineSpec spec : EngineRegistry.ALL) {
            String res = "engines/" + PLATFORM + "/" + spec.libBase() + LIB_EXT;
            extractOne(res, platDir.resolve(spec.libBase() + LIB_EXT));
        }
    }

    /**
     * 解压单个资源。
     * <p>
     * 覆盖规则：目标文件与 jar 内同名资源<b>内容一致才跳过</b>（逐字节比对），不同就覆盖。
     * 早期版本是「存在即跳过」，结果引擎升级后旧权重留在原地（Pikafish 权重与引擎不匹配
     * 会直接 std::exit 杀掉 JVM）；再早的「只比大小」在两轮构建大小恰好相同时会漏更新。
     */
    private static void extractOne(String res, Path target) {
        if (sameContent(target, res)) {
            return;
        }
        try (InputStream in = EngineExtractor.class.getResourceAsStream("/" + res)) {
            if (in == null) {
                // 正常情况：本平台没有这个引擎（例如只有 windows 目录的本地开发构建）
                Chuying.LOGGER.warn("内置引擎资源缺失（跳过）: {}", res);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            Chuying.LOGGER.info("解压内置引擎资源: {}", target);
        } catch (IOException e) {
            // 典型场景：游戏仍在运行时旧 DLL 被加载锁定（AccessDenied）。
            // 此时盘上留的是旧库，加载后若符号不匹配会在引擎初始化时报错，便于定位。
            Chuying.LOGGER.error("解压引擎资源失败（若为文件被占用，请关闭所有游戏实例后重试）: {}", res, e);
        }
    }

    /**
     * 逐字节比对盘上文件与 jar 内资源是否完全一致。
     * <p>
     * 早期版本只比文件大小——两轮 CI 构建的 DLL 剥离符号后大小可能恰好相同而
     * 内容不同（本次真实发生：旧 DLL 永远无法被新 jar 覆盖，新代码加载旧库
     * UnsatisfiedLinkError），必须按内容判断。
     */
    private static boolean sameContent(Path diskFile, String resource) {
        if (!Files.exists(diskFile)) {
            return false;
        }
        try (InputStream in = EngineExtractor.class.getResourceAsStream("/" + resource)) {
            if (in == null) {
                return false;
            }
            try (InputStream disk = Files.newInputStream(diskFile)) {
                byte[] bufA = new byte[8192];
                byte[] bufB = new byte[8192];
                int n;
                while ((n = in.read(bufA)) != -1) {
                    int m = disk.readNBytes(bufB, 0, n);
                    if (m != n || !java.util.Arrays.equals(bufA, 0, n, bufB, 0, m)) {
                        return false;
                    }
                }
                return disk.read() == -1;
            }
        } catch (Throwable e) {
            // 兜住 Error：权重文件损坏/IO 异常时只当作"需要重新解压"，不能崩游戏
            return false;
        }
    }

    // ------------------------------------------------------------------
    // shared → 平台目录
    // ------------------------------------------------------------------

    /**
     * 把 shared 里的通用依赖复制到平台目录：
     * <ul>
     *   <li>pikafish.nnue → 平台目录根（EvalFile 指向）</li>
     *   <li>stockfish/*.nnue → 平台目录/stockfish/（EvalFile / EvalFileSmall 指向）</li>
     *   <li>rapfi/* → 平台目录/rapfi/（config.toml 重新生成，模型路径改写为绝对路径）</li>
     * </ul>
     * 这样引擎侧只需要认平台目录，不用关心 shared 的存在。
     */
    private static void copySharedToPlatform(Path sharedDir, Path platDir) {
        copyIfChanged(sharedDir.resolve("pikafish.nnue"), platDir.resolve("pikafish.nnue"));

        copyDirIfPresent(sharedDir.resolve("stockfish"), platDir.resolve("stockfish"));

        Path sharedRapfi = sharedDir.resolve("rapfi");
        if (!Files.isDirectory(sharedRapfi)) {
            return;
        }
        try (var files = Files.list(sharedRapfi)) {
            Path rapfiDir = platDir.resolve("rapfi");
            Files.createDirectories(rapfiDir);
            files.forEach(f -> {
                String name = f.getFileName().toString();
                if (name.equals("config.toml")) {
                    writeRapfiConfig(f, rapfiDir.resolve(name), rapfiDir);
                } else {
                    copyIfChanged(f, rapfiDir.resolve(name));
                }
            });
        } catch (IOException e) {
            Chuying.LOGGER.error("复制共享 Rapfi 资源失败", e);
        }
    }

    /** 整目录复制（目标不存在或大小不同才复制） */
    private static void copyDirIfPresent(Path srcDir, Path dstDir) {
        if (!Files.isDirectory(srcDir)) {
            return;
        }
        try (var files = Files.list(srcDir)) {
            Files.createDirectories(dstDir);
            files.forEach(f -> copyIfChanged(f, dstDir.resolve(f.getFileName().toString())));
        } catch (IOException e) {
            Chuying.LOGGER.error("复制共享资源目录失败: {}", srcDir, e);
        }
    }

    /**
     * 生成 Rapfi 的 config.toml：以 shared 模板为底，把模型文件的相对路径改写为本机绝对路径。
     * （Rapfi 只按 cwd 与 binaryDirectory 搜索模型，进程内模式下二者都不是引擎目录，故必须绝对路径。）
     * 每次解压都重新生成，避免游戏目录迁移后残留旧路径。
     */
    private static void writeRapfiConfig(Path template, Path target, Path rapfiDir) {
        try {
            String content = Files.readString(template, StandardCharsets.UTF_8);
            for (String model : EngineRegistry.GOMOKU.dataFiles()) {
                String base = model.substring(model.lastIndexOf('/') + 1);
                if (!base.equals("config.toml")) {
                    content = content.replace("\"" + base + "\"",
                            "\"" + rapfiDir.resolve(base).toString().replace('\\', '/') + "\"");
                }
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            Chuying.LOGGER.info("生成 Rapfi 配置: {}", target);
        } catch (IOException e) {
            Chuying.LOGGER.error("生成 Rapfi 配置失败: {}", target, e);
        }
    }

    /** shared → 平台目录的复制：目标不存在或大小不同才复制（大小相同视为同一份） */
    private static void copyIfChanged(Path src, Path dst) {
        if (!Files.exists(src)) {
            return;
        }
        try {
            if (Files.exists(dst) && Files.size(dst) == Files.size(src)) {
                return;
            }
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Chuying.LOGGER.error("复制引擎资源失败: {} -> {}", src, dst, e);
        }
    }

    /** 当前平台可用的引擎清单（用于日志/诊断） */
    public static List<EngineSpec> availableEngines() {
        return EngineRegistry.ALL;
    }
}
