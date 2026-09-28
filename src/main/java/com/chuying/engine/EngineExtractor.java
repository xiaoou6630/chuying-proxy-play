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
 * 2.0 起引擎为 JNI 原生库（零子进程），jar 内允许直接携带 .dll/.so/.dylib。
 * 目录结构（jar 内）：
 * <pre>
 * engines/
 * ├── shared/                          # 三平台通用权重（只打包一份）
 * │   ├── pikafish.nnue                # 皮卡鱼权重
 * │   ├── stockfish/                   # Stockfish 两份 NNUE（EvalFile / EvalFileSmall）
 * │   └── rapfi/                       # config.toml 模板 + 模型（config 内模型路径解压时改写为绝对路径）
 * └── windows|linux|macos/             # 当前平台的原生库（带平台后缀）
 *     ├── chuying_stockfish.dll|so|dylib
 *     ├── chuying_pikafish.dll|so|dylib
 *     └── chuying_rapfi.dll|so|dylib
 * </pre>
 * 解压规则：库与权重文件不存在才解压（已存在视为用户可能自行替换过，尊重用户文件）；
 * rapfi config.toml 因内含本机绝对路径，每次解压都重新生成。
 */
public final class EngineExtractor {
    private static final String SUB_DIR = "config/chuying/engines";
    /** 当前平台原生库后缀 */
    private static final String LIB_EXT = switch (platformRaw()) {
        case "windows" -> ".dll";
        case "macos" -> ".dylib";
        default -> ".so";
    };
    /** 三平台通用权重/模型（jar 内路径，带 engines/ 前缀） */
    private static final List<String> SHARED_RESOURCES = List.of(
            "engines/shared/pikafish.nnue",
            "engines/shared/stockfish/nn-1c0000000000.nnue",
            "engines/shared/stockfish/nn-37f18f62d772.nnue",
            "engines/shared/rapfi/config.toml",
            "engines/shared/rapfi/model210901.bin",
            "engines/shared/rapfi/mix9svqfreestyle_bsmix.bin.lz4"
    );
    /** 三个原生引擎库名（不含平台后缀） */
    private static final List<String> NATIVE_LIBS = List.of(
            "chuying_stockfish",
            "chuying_pikafish",
            "chuying_rapfi"
    );
    /** Rapfi config.toml 中需要改写为绝对路径的模型文件 */
    private static final List<String> RAPFI_MODEL_FILES = List.of(
            "model210901.bin",
            "mix9svqfreestyle_bsmix.bin.lz4"
    );

    private static Path enginesDir;

    private EngineExtractor() {
    }

    private static String platformRaw() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        return "linux";
    }

    /** 当前运行平台：windows / linux / macos */
    public static String platform() {
        return platformRaw();
    }

    public static synchronized Path enginesDir() {
        if (enginesDir == null) {
            Path base = Minecraft.getInstance().gameDirectory.toPath();
            Path dir = base.resolve(SUB_DIR);
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
        Path target = enginesDir().resolve(platform()).resolve(name + LIB_EXT);
        return Files.exists(target) ? target.toString() : null;
    }

    /**
     * 返回平台目录下数据文件（权重/模型）的绝对路径，未找到返回 null。
     * {@code rel} 为平台目录内相对路径（如 "pikafish.nnue"、"stockfish/nn-1c0000000000.nnue"）。
     */
    public static String dataFilePath(String rel) {
        Path target = enginesDir().resolve(platform()).resolve(rel.replace('/', java.io.File.separatorChar));
        return Files.exists(target) ? target.toString() : null;
    }

    /** Rapfi 的 config.toml 解压后路径（模型路径已改写为绝对路径），未就绪返回 null */
    public static String rapfiConfigPath() {
        Path target = enginesDir().resolve(platform()).resolve("rapfi").resolve("config.toml");
        return Files.exists(target) ? target.toString() : null;
    }

    private static void extract(Path dir) {
        Path sharedDir = dir.resolve("shared");
        Path platDir = dir.resolve(platform());

        extractResources(SHARED_RESOURCES, sharedDir, "engines/shared/", false);
        extractResources(nativeLibResources(), platDir, "engines/" + platform() + "/", false);
        copySharedToPlatform(sharedDir, platDir);
    }

    /** 当前平台原生库资源清单（jar 内直接带平台后缀） */
    private static List<String> nativeLibResources() {
        return NATIVE_LIBS.stream()
                .map(name -> "engines/" + platform() + "/" + name + LIB_EXT)
                .toList();
    }

    /**
     * 解压资源到目标目录。
     * {@code prefix} 是 jar 内资源前缀（含 engines/ 与平台/shared 段），
     * 剥离后剩余的相对路径再 resolve 到 {@code targetRoot}。
     */
    private static void extractResources(List<String> resources, Path targetRoot, String prefix, boolean replace) {
        for (String res : resources) {
            String rel = res.substring(prefix.length());
            Path target = targetRoot.resolve(rel.replace('/', java.io.File.separatorChar));
            if (!replace && Files.exists(target)) {
                continue;
            }
            try (InputStream in = EngineExtractor.class.getResourceAsStream("/" + res)) {
                if (in == null) {
                    Chuying.LOGGER.warn("内置引擎资源缺失: {}", res);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                Chuying.LOGGER.info("解压内置引擎资源: {}", target);
            } catch (IOException e) {
                Chuying.LOGGER.error("解压引擎资源失败: {}", res, e);
            }
        }
    }

    /**
     * 把 shared 里的通用依赖复制到平台目录：
     * - pikafish.nnue → 平台目录根（EvalFile 指向）
     * - stockfish/*.nnue → 平台目录/stockfish/
     * - rapfi/* → 平台目录/rapfi/（config.toml 重新生成，模型路径改写为绝对路径）
     */
    private static void copySharedToPlatform(Path sharedDir, Path platDir) {
        copyIfAbsent(sharedDir.resolve("pikafish.nnue"), platDir.resolve("pikafish.nnue"));
        Path sharedSf = sharedDir.resolve("stockfish");
        if (Files.isDirectory(sharedSf)) {
            try (var files = Files.list(sharedSf)) {
                Path sfDir = platDir.resolve("stockfish");
                Files.createDirectories(sfDir);
                files.forEach(f -> copyIfAbsent(f, sfDir.resolve(f.getFileName().toString())));
            } catch (IOException e) {
                Chuying.LOGGER.error("复制 Stockfish 权重失败", e);
            }
        }
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
                    copyIfAbsent(f, rapfiDir.resolve(name));
                }
            });
        } catch (IOException e) {
            Chuying.LOGGER.error("复制共享 Rapfi 资源失败", e);
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
            for (String model : RAPFI_MODEL_FILES) {
                content = content.replace("\"" + model + "\"",
                        "\"" + rapfiDir.resolve(model).toString().replace('\\', '/') + "\"");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8, StandardCopyOption.REPLACE_EXISTING);
            Chuying.LOGGER.info("生成 Rapfi 配置: {}", target);
        } catch (IOException e) {
            Chuying.LOGGER.error("生成 Rapfi 配置失败: {}", target, e);
        }
    }

    private static void copyIfAbsent(Path src, Path dst) {
        if (!Files.exists(src) || Files.exists(dst)) {
            return;
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Chuying.LOGGER.error("复制引擎资源失败: {} -> {}", src, dst, e);
        }
    }
}
