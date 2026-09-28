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
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded") # static CRT: keep iostream state per-DLL
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

# JNI headers (JAVA_HOME provided by actions/setup-java)
if(WIN32)
    set(JNI_PLATFORM_DIR "win32")
elseif(APPLE)
    set(JNI_PLATFORM_DIR "darwin")
else()
    set(JNI_PLATFORM_DIR "linux")
endif()
include_directories("$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/${JNI_PLATFORM_DIR}")

# ---------------------------------------------------------------------------
# Engine sources (main.cpp already patched to engine_main by CI script)
# ---------------------------------------------------------------------------
file(GLOB_RECURSE SF_SRCS CONFIGURE_DEPENDS engines/stockfish/src/*.cpp)
file(GLOB_RECURSE PF_SRCS CONFIGURE_DEPENDS engines/pikafish/src/*.cpp)
list(FILTER PF_SRCS EXCLUDE REGEX "pikafish/src/universal/")

# Rapfi: use its own CMakeLists (bundled externals: cpptoml/cxxopts/lz4/simde/zip...)
# CI patches add_executable(rapfi -> add_library(rapfi STATIC, and main.cpp -> engine_main.
# SIMD locked to SSE baseline for portability (see build_native.sh CMAKE_ARGS).
add_subdirectory(engines/rapfi/Rapfi rf_build)

function(chuying_bridge name)
    cmake_parse_arguments(ARG "" "" "SOURCES;INCLUDES" ${ARGN})
    add_library(${name} SHARED
        ${CMAKE_CURRENT_LIST_DIR}/src/uci_bridge.cpp
        ${CMAKE_CURRENT_LIST_DIR}/src/jni_exports.cpp
        ${ARG_SOURCES})
    target_include_directories(${name} PRIVATE ${ARG_INCLUDES})
    set_target_properties(${name} PROPERTIES PREFIX "")
endfunction()

chuying_bridge(chuying_stockfish
    SOURCES ${SF_SRCS}
    INCLUDES ${CMAKE_CURRENT_SOURCE_DIR}/engines/stockfish/src)

chuying_bridge(chuying_pikafish
    SOURCES ${PF_SRCS}
    INCLUDES ${CMAKE_CURRENT_SOURCE_DIR}/engines/pikafish/src)

chuying_bridge(chuying_rapfi
    SOURCES rapfi)
