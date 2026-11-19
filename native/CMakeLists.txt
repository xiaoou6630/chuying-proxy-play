# chuying native engines: JNI bridge + Stockfish/Pikafish/Rapfi linked in-process.
# CI clones engine sources into native/engines/<name> and patches their
# main() -> engine_main() before configuring this project (see
# .github/scripts/build_native.sh).
#
# We do NOT use the engines' own CMake targets (Stockfish ships no
# add_executable, Rapfi's target names vary). Engine sources are globbed
# directly: correctness first (scalar/SSE2 baseline), arch tuning later.
cmake_minimum_required(VERSION 3.16)
project(chuying_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    add_compile_options(/utf-8 /EHsc /Zc:lambda)    # /Zc:lambda: fix C3493 on Stockfish lambdas
    add_compile_definitions(USE_SSE2) # x64 MSVC has SSE2 baseline (POPcnt left off: safe fallback)
else()
    find_package(Threads REQUIRED)
    link_libraries(Threads::Threads)
    # Mirrors Pikafish/Stockfish Makefile minimum arch flags.
    #
    # ⚠️ macOS 交叉编译：CMAKE_SYSTEM_PROCESSOR 反映的是**宿主机**架构，
    # 不是 CMAKE_OSX_ARCHITECTURES 指定的目标架构。CI 的 macos runner 是
    # arm64 机器，即使 -DCMAKE_OSX_ARCHITECTURES=x86_64，这里仍会走 arm64 分支、
    # 定义 USE_NEON=8，于是 x86_64 分片也在编译 NEON 代码：
    #   error: unknown type name 'int32x4_t' / 'float32x4_t'
    #   error: use of undeclared identifier 'vaddvq_s32'
    # 因此 macOS 上必须看 CMAKE_OSX_ARCHITECTURES。
    set(CHUYING_TARGET_ARCH "${CMAKE_SYSTEM_PROCESSOR}")
    if(APPLE AND CMAKE_OSX_ARCHITECTURES)
        if(CMAKE_OSX_ARCHITECTURES MATCHES ";")
            message(FATAL_ERROR
                "chuying: 不支持一次编译多个 macOS 架构（SIMD 宏只能展开一次）。"
                "请分架构各编一遍再用 lipo 合并，见 .github/scripts/build_native.sh")
        endif()
        set(CHUYING_TARGET_ARCH "${CMAKE_OSX_ARCHITECTURES}")
    endif()
    message(STATUS "chuying: target arch = ${CHUYING_TARGET_ARCH}")

    if(CHUYING_TARGET_ARCH MATCHES "(x86_64)|(AMD64)|(amd64)")
        add_compile_definitions(USE_SSE2 USE_POPCNT IS_64BIT)
        add_compile_options(-msse3 -mpopcnt)
    elseif(CHUYING_TARGET_ARCH MATCHES "(aarch64)|(arm64)|(ARM64)")
        add_compile_definitions(USE_NEON=8 IS_64BIT)
    endif()
endif()

# Windows MinGW: link the whole C++ runtime statically. The shipped DLL must not
# import libwinpthread-1.dll / libgcc_s_seh-1.dll / libstdc++-6.dll: those are
# absent on end-user machines and on the CI runners, and a missing dependency
# makes System.load() fail (or hang) inside the JVM. Plain -static is the only
# reliable switch here: -Wl,-Bstatic/-Bdynamic around -lwinpthread does not work
# because gcc appends its own implicit -lwinpthread after our flags.
if(WIN32 AND NOT MSVC)
    set(CHUYING_WIN_STATIC_LINK -static -static-libgcc -static-libstdc++)
else()
    set(CHUYING_WIN_STATIC_LINK "")
endif()

# JNI headers (JAVA_HOME provided by actions/setup-java)
if(WIN32)
    set(JNI_PLATFORM_DIR "win32")
elseif(APPLE)
    set(JNI_PLATFORM_DIR "darwin")
else()
    set(JNI_PLATFORM_DIR "linux")
endif()
include_directories("$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/${JNI_PLATFORM_DIR}")

# Platform folder name, must match EngineExtractor.platform() in the mod.
# 3.0 起一个 jar 通吃所有系统，目录名带架构（macOS 发 universal 二进制，故不带架构）。
if(WIN32)
    set(CHUYING_PLATFORM "windows-x86_64")
elseif(APPLE)
    set(CHUYING_PLATFORM "macos-universal")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "(aarch64)|(arm64)|(ARM64)")
    set(CHUYING_PLATFORM "linux-arm64")
else()
    set(CHUYING_PLATFORM "linux-x86_64")
endif()

# ---------------------------------------------------------------------------
# Engine sources (main.cpp already patched to engine_main by CI script)
# ---------------------------------------------------------------------------
file(GLOB_RECURSE SF_SRCS CONFIGURE_DEPENDS engines/stockfish/src/*.cpp)
file(GLOB_RECURSE PF_SRCS CONFIGURE_DEPENDS engines/pikafish/src/*.cpp)
list(FILTER PF_SRCS EXCLUDE REGEX "pikafish/src/universal/")

# Rapfi: use its own CMakeLists (bundled externals: cpptoml/cxxopts/lz4/simde/zip...)
# CI patches add_executable(rapfi -> add_library(rapfi STATIC, and main.cpp -> engine_main.
# SIMD locked to SSE baseline for portability (see build_native.sh CMAKE_ARGS).
#
# NO_MULTI_THREADING is mandatory here: Rapfi initialises globals at load time and
# Search::TT's constructor clears the hash table, which spawns worker threads and
# joins them. Inside LoadLibrary the loader lock is held, a thread created there
# cannot start, and the join() deadlocks the whole JVM on Windows (reproduced:
# loading chuying_rapfi.dll never returns). Without MULTI_THREADING nothing is
# spawned during static init, so the library loads and searches single-threaded.
set(NO_MULTI_THREADING ON CACHE BOOL "chuying: no threads during static init" FORCE)
add_subdirectory(engines/rapfi/Rapfi rf_build)

function(chuying_bridge name)
    cmake_parse_arguments(ARG "" "JNI_DEFINE" "SOURCES;INCLUDES;LIBS" ${ARGN})
    add_library(${name} SHARED
        ${CMAKE_CURRENT_LIST_DIR}/src/uci_bridge.cpp
        ${CMAKE_CURRENT_LIST_DIR}/src/jni_exports.cpp
        ${ARG_SOURCES})
    target_include_directories(${name} PRIVATE ${ARG_INCLUDES})
    # One exported JNI class per engine. The three libraries must NOT export the
    # same JNI symbols: the JVM binds a native method name to the FIRST loaded
    # library that exports it, so shared names make the 2nd/3rd engine's start()
    # land on the 1st engine's singleton and fail at once.
    # Each lib compiles jni_exports.cpp with exactly one CHUYING_ENGINE_* define
    # and only exports its own five symbols (the #error in jni_exports.cpp guards
    # a missing define at build time, not at runtime).
    target_compile_definitions(${name} PRIVATE ${ARG_JNI_DEFINE})
    if(ARG_LIBS)
        target_link_libraries(${name} PRIVATE ${ARG_LIBS})
    endif()
    if(CHUYING_WIN_STATIC_LINK)
        target_link_options(${name} PRIVATE ${CHUYING_WIN_STATIC_LINK})
    endif()
    set_target_properties(${name} PROPERTIES PREFIX "")
endfunction()

chuying_bridge(chuying_stockfish
    JNI_DEFINE CHUYING_ENGINE_WCHESS
    SOURCES ${SF_SRCS}
    INCLUDES ${CMAKE_CURRENT_SOURCE_DIR}/engines/stockfish/src)
# Stockfish 17.1 默认用 INCBIN 把 75 MB NNUE 编进二进制。三平台各编一份就是 225 MB 纯重复，
# 而且 MSVC 不认 INCBIN 会静默失效（见 build_native.sh 注释）。
# 关掉内嵌后，权重由 Java 侧 setoption name EvalFile / EvalFileSmall 指向解压出的文件，
# 单库从 76 MB 降到约 1 MB，三平台共用同一份权重。
target_compile_definitions(chuying_stockfish PRIVATE NNUE_EMBEDDING_OFF)
# 让 Stockfish 在找不到 EvalFile 时还能退到游戏目录下的解压位置
target_compile_definitions(chuying_stockfish PRIVATE
    DEFAULT_NNUE_DIRECTORY="config/chuying/engines/${CHUYING_PLATFORM}/stockfish")

chuying_bridge(chuying_pikafish
    JNI_DEFINE CHUYING_ENGINE_CCHESS
    SOURCES ${PF_SRCS}
    INCLUDES ${CMAKE_CURRENT_SOURCE_DIR}/engines/pikafish/src)
# Pikafish bundles zstd whose ASM fast-loop is an .S file not in our glob; disable it.
target_compile_definitions(chuying_pikafish PRIVATE ZSTD_DISABLE_ASM)
# Pikafish loads its net at runtime (no INCBIN) and refuses to initialise without
# it. network.cpp searches CWD, the binary dir, then DEFAULT_NNUE_DIRECTORY; the
# mod extracts pikafish.nnue to config/chuying/engines/<platform>/ relative to the
# game directory (the process CWD), so point the engine straight at it.
target_compile_definitions(chuying_pikafish PRIVATE
    DEFAULT_NNUE_DIRECTORY="config/chuying/engines/${CHUYING_PLATFORM}")

chuying_bridge(chuying_rapfi
    JNI_DEFINE CHUYING_ENGINE_GOMOKU
    LIBS rapfi)

# ---------------------------------------------------------------------------
# GNU Go（围棋）：C 引擎，走它自己的引擎 C API，不用文本流桥。
#   * CI 已把它 interface/CMakeLists.txt 的 ADD_EXECUTABLE(gnugo 改成 STATIC 库
#     （见 .github/scripts/build_native.sh）；
#   * 桥本身：native/src/gnugo_bridge.cpp（零子进程、零管道、零文本协议）；
#   * 因此这里不用 chuying_bridge()：那个函数会一并编译 uci_bridge.cpp /
#     jni_exports.cpp（文本协议桥），对 GNU Go 没有意义。
# ---------------------------------------------------------------------------
set(CHUYING_GNUG0_DIR ${CMAKE_CURRENT_SOURCE_DIR}/engines/gnugo)
if(EXISTS ${CHUYING_GNUG0_DIR}/CMakeLists.txt)
    # GNU Go 3.8 是 2009 年的 C89 代码：
    #   * -fcommon：GCC 10 / Clang 11 起默认 -fno-common，它的暂定定义
    #     （meaningless_white_moves 等）会变成"重复定义"直接链接失败；
    #   * 隐式函数声明 / 隐式整数转换在 GCC 14、Clang 16 起是硬错误，统一降级；
    #   * 语言标准固定为 gnu90（它的代码就是这个年代的）。
    # 这些开关只作用于 C（避免 C++ 桥收到无意义的编译告警）。
    set(CMAKE_C_STANDARD 90)
    set(CMAKE_C_STANDARD_REQUIRED OFF)
    if(NOT MSVC)
        # -Wimplicit-function-declaration 故意**打开**（配 -Wno-error=… 不让它变成错误）：
        # 缺原型的调用在 64 位下会把指针/64 位返回值截断成 int，是这类老代码最典型的
        # "读到垃圾位置值"来源；保留告警便于在 CI 日志里核对。
        add_compile_options(
            "$<$<COMPILE_LANGUAGE:C>:-fcommon>"
            "$<$<COMPILE_LANGUAGE:C>:-Wimplicit-function-declaration>"
            "$<$<COMPILE_LANGUAGE:C>:-Wno-error=implicit-function-declaration>"
            "$<$<COMPILE_LANGUAGE:C>:-Wno-int-conversion>"
            "$<$<COMPILE_LANGUAGE:C>:-Wno-implicit-int>"
            "$<$<COMPILE_LANGUAGE:C>:-Wno-builtin-declaration-mismatch>"
            "$<$<COMPILE_LANGUAGE:C>:-Wno-return-type>")
        if(NOT WIN32)
            add_compile_options("$<$<COMPILE_LANGUAGE:C>:-D_GNU_SOURCE>")
        endif()
    endif()

    add_subdirectory(${CHUYING_GNUG0_DIR} gnugo_build)

    # ------------------------------------------------------------------
    # GNU Go 构建期小工具会链接 libboard.a / libengine.a，而 printutils.c 里
    # 我们把 abort() 换成了 chuying_gnugo_abortgo_exit()，于是它们链接时要求
    # 该符号存在：
    #   Undefined symbols for architecture arm64:
    #     "_chuying_gnugo_abortgo_exit", referenced from:
    #       _abortgo in libengine.a[27](printutils.c.o)
    #
    # Linux 的 ld / Windows 的 lld 能容忍 __attribute__((weak)) 未定义，
    # **macOS 的 ld64 不行**（实测）。因此给这些工具链接一个桩实现
    # （src/gnugo_abort_stub.c，直接 abort —— 构建期工具失败就该暴露）。
    # 运行时用的是 chuying_gnugo 共享库，那里是 gnugo_bridge.cpp 的 longjmp 实现。
    #
    # 注意：必须用**显式目标名**。CMake 的 get_property 没有 APPEND 参数
    # （那是 set_property 的），实测 `get_property(... APPEND DIRECTORY ...)`
    # 直接报 "get_property given invalid scope APPEND"。
    # 这些目标名来自 gnugo 3.8 的 patterns/CMakeLists.txt 与 sgf/CMakeLists.txt。
    # ------------------------------------------------------------------
    set(CHUYING_ABORT_STUB ${CMAKE_CURRENT_LIST_DIR}/src/gnugo_abort_stub.c)
    set(CHUYING_GNUG0_TOOLS
        mkpat mkeyes mkmcpat joseki uncompress_fuseki extract_fuseki compress_fuseki sgfgen)
    foreach(_t ${CHUYING_GNUG0_TOOLS})
        if(TARGET ${_t})
            target_sources(${_t} PRIVATE ${CHUYING_ABORT_STUB})
            message(STATUS "chuying: abort stub -> ${_t}")
        endif()
    endforeach()

    add_library(chuying_gnugo SHARED ${CMAKE_CURRENT_LIST_DIR}/src/gnugo_bridge.cpp)
    # gnugo.h 会连带包含 engine/board.h -> sgf/sgftree.h、utils/winsocket.h 等，
    # 因此四个源码目录都要在包含路径上；再加 CMake 生成 config.h 的目录。
    target_include_directories(chuying_gnugo PRIVATE
        ${CHUYING_GNUG0_DIR}/engine
        ${CHUYING_GNUG0_DIR}/sgf
        ${CHUYING_GNUG0_DIR}/utils
        ${CHUYING_GNUG0_DIR}/patterns
        ${CHUYING_GNUG0_DIR}
        ${CMAKE_CURRENT_BINARY_DIR}/gnugo_build
        "$ENV{JAVA_HOME}/include"
        "$ENV{JAVA_HOME}/include/${JNI_PLATFORM_DIR}")
    # GNU Go 的头文件按 C 声明，桥是 C++：HAVE_CONFIG_H 让它们读到 CMake 生成的 config.h
    target_compile_definitions(chuying_gnugo PRIVATE HAVE_CONFIG_H)
    # GNU Go 的静态库之间是循环依赖（engine <-> patterns，engine -> sgf），而它们的
    # CMakeLists 没有声明依赖，单遍扫描的 ld（Windows/MinGW 尤其明显）会报未定义符号
    # （实测：libengine.a(board.c.obj) undefined reference to sgftreeAddComment）。
    # 显式按序重复列一遍即可满足两轮解析，三平台通用。
    target_link_libraries(chuying_gnugo PRIVATE
        gnugo
        engine patterns sgf utils
        engine patterns sgf utils)
    if(CHUYING_WIN_STATIC_LINK)
        target_link_options(chuying_gnugo PRIVATE ${CHUYING_WIN_STATIC_LINK})
    endif()
    # macOS: SHARED 库允许未定义符号（否则 weak 钩子在 ld64 下仍会报错）
    if(APPLE)
        target_link_options(chuying_gnugo PRIVATE "-undefined" "dynamic_lookup")
    endif()
    set_target_properties(chuying_gnugo PROPERTIES PREFIX "")
endif()

