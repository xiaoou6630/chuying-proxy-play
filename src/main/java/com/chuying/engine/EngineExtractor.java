package com.chuying.engine;

import com.chuying.Chuying;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 把打包在 jar 里的内置引擎（Windows / Linux / macOS 三平台）首次运行时解压到
 * {@code config/chuying/engines/}。
 * <p>
 * 目录结构（jar 内）：
 * <pre>
 * engines/
 * ├── shared/                     # 三平台通用资源（只打包一份，运行时复制到平台目录）
 * │   ├── pikafish.nnue
 * │   └── rapfi/                  # config + 模型（Rapfi 要求与 exe 同目录，解压时复制过去）
 * └── windows|linux|macos/        # 当前平台的可执行文件（统一无扩展名！）
 *     ├── pikafish                # CurseForge 禁止 jar 内含 .exe/.sh/.bat，
 *     ├── stockfish               # 故 jar 内一律不带后缀，
 *     └── rapfi/pbrain-rapfi      # Windows 下解压时再补回 .exe
 * </pre>
 * <p>
 * 解压规则：文件不存在才解压（已存在说明用户可能自行替换过，尊重用户文件）。
 */
public final class EngineExtractor {
    private static final String SUB_DIR = "config/chuying/engines";
    /** 三平台通用资源（jar 内路径，带 engines/ 前缀） */
    private static final List<String> SHARED_RESOURCES = List.of(
            "engines/shared/pikafish.nnue",
            "engines/shared/rapfi/config.toml",
            "engines/shared/rapfi/model210901.bin",
            "engines/shared/rapfi/mix9svqfreestyle_bsmix.bin.lz4"
    );
    /** 各平台可执行文件（jar 内相对路径，不含后缀，enginePath() 会补 .exe） */
    private static final List<String> EXECUTABLES = List.of(
            "pikafish",
            "stockfish",
            "rapfi/pbrain-rapfi"
    );

    private static Path enginesDir;

    private EngineExtractor() {
    }

    /** 当前运行平台：windows / linux / macos */
    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        return "linux";
    }

    private static boolean isWindows() {
        return "windows".equals(platform());
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
     * 返回 jar 内某个引擎可执行文件解压后的绝对路径，未解压成功返回 null。
     * {@code rel} 为平台目录内的相对路径（如 "pikafish" / "stockfish" / "rapfi/pbrain-rapfi"），
     * Windows 下自动补 .exe 后缀。
     */
    public static String enginePath(String rel) {
        String file = rel + (isWindows() ? ".exe" : "");
        Path target = enginesDir().resolve(platform()).resolve(file.replace('/', java.io.File.separatorChar));
        return Files.exists(target) ? target.toString() : null;
    }

    private static void extract(Path dir) {
        Path sharedDir = dir.resolve("shared");
        Path platDir = dir.resolve(platform());

        // 传入各自目录前缀，去掉后才 resolve 到对应目录，避免 engines/shared/shared 双重目录
        extractResources(SHARED_RESOURCES, sharedDir, "engines/shared/", false);
        extractResources(platformResources(), platDir, "engines/" + platform() + "/", true);
        copySharedToPlatform(sharedDir, platDir);
        makeExecutable(platDir);
    }

    /** 当前平台的资源清单（jar 内统一无 .exe 后缀，Windows 下解压时补回） */
    private static List<String> platformResources() {
        String plat = platform();
        return List.of(
                "engines/" + plat + "/pikafish",
                "engines/" + plat + "/stockfish",
                "engines/" + plat + "/rapfi/pbrain-rapfi"
        );
    }

    /**
     * 解压资源到目标目录。
     * {@code prefix} 是 jar 内资源的前缀（含 engines/ 与平台/shared 段），
     * 剥离后剩余的相对路径再 resolve 到 {@code targetRoot}。
     * {@code appendExeOnWindows} 为 true 时，Windows 下解压文件名补回 .exe 后缀
     * （jar 内禁止含可执行文件，见 build.gradle 的 rename）。
     */
    private static void extractResources(List<String> resources, Path targetRoot, String prefix, boolean appendExeOnWindows) {
        for (String res : resources) {
            String rel = res.substring(prefix.length());
            Path target = targetRoot.resolve(rel.replace('/', java.io.File.separatorChar));
            if (appendExeOnWindows && isWindows()) {
                target = target.resolveSibling(target.getFileName() + ".exe");
            }
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in = EngineExtractor.class.getResourceAsStream("/" + res)) {
                if (in == null) {
                    Chuying.LOGGER.warn("内置引擎资源缺失: {}", res);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                Chuying.LOGGER.info("解压内置引擎: {}", target);
            } catch (IOException e) {
                Chuying.LOGGER.error("解压引擎失败: {}", res, e);
            }
        }
    }

    /**
     * 把 shared 里的通用依赖复制到平台目录：
     * - pikafish.nnue → 平台目录（Pikafish 按 exe 旁同名 .nnue 自动加载）
     * - rapfi/* → 平台目录/rapfi/（Rapfi 自动检测 config.toml 与模型）
     */
    private static void copySharedToPlatform(Path sharedDir, Path platDir) {
        copyIfAbsent(sharedDir.resolve("pikafish.nnue"), platDir.resolve("pikafish.nnue"));
        Path sharedRapfi = sharedDir.resolve("rapfi");
        Path platRapfi = platDir.resolve("rapfi");
        if (!Files.isDirectory(sharedRapfi)) {
            return;
        }
        try (Stream<Path> files = Files.list(sharedRapfi)) {
            files.forEach(f -> copyIfAbsent(f, platRapfi.resolve(f.getFileName().toString())));
        } catch (IOException e) {
            Chuying.LOGGER.error("复制共享 Rapfi 资源失败", e);
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

    /** Linux/macOS 需要可执行权限；Windows 无此概念 */
    private static void makeExecutable(Path platDir) {
        if (isWindows()) {
            return;
        }
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
        );
        for (String name : EXECUTABLES) {
            try {
                Files.setPosixFilePermissions(platDir.resolve(name), perms);
            } catch (IOException | UnsupportedOperationException e) {
                Chuying.LOGGER.warn("设置引擎执行权限失败: {}", name);
            }
        }
    }
}
