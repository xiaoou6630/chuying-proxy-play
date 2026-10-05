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
    # Mirrors Pikafish/Stockfish Makefile minimum arch flags
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "(x86_64)|(AMD64)")
        add_compile_definitions(USE_SSE2 USE_POPCNT IS_64BIT)
        add_compile_options(-msse3 -mpopcnt)
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "(aarch64)|(arm64)|(ARM64)")
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
if(WIN32)
    set(CHUYING_PLATFORM windows)
elseif(APPLE)
    set(CHUYING_PLATFORM macos)
else()
    set(CHUYING_PLATFORM linux)
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
    set_target_properties(chuying_gnugo PROPERTIES PREFIX "")
endif()

