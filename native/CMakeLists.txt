# chuying native engines: JNI bridge + Stockfish/Pikafish/Rapfi linked in-process.
# CI clones engine sources into native/engines/<name> and patches their
# main() -> engine_main() before configuring this project (see
# .github/scripts/build_native.sh). gen_targets.cmake is generated there too.
cmake_minimum_required(VERSION 3.16)
project(chuying_native CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded") # static CRT: keep iostream state per-DLL
    add_compile_options(/utf-8 /EHsc /Zc:__cplusplus)
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

include(${CMAKE_CURRENT_LIST_DIR}/gen_targets.cmake)

macro(chuying_bridge name engine_target)
    add_library(${name} SHARED
        ${CMAKE_CURRENT_LIST_DIR}/src/uci_bridge.cpp
        ${CMAKE_CURRENT_LIST_DIR}/src/jni_exports.cpp)
    target_link_libraries(${name} PRIVATE ${engine_target})
    set_target_properties(${name} PROPERTIES PREFIX "")
endmacro()

# Stockfish (chess) - CMakeLists lives in src/
add_subdirectory(engines/stockfish/src  sf_build)
chuying_bridge(chuying_stockfish ${SF_TARGET})

# Pikafish (xiangqi) - same layout as Stockfish
add_subdirectory(engines/pikafish/src  pf_build)
chuying_bridge(chuying_pikafish ${PF_TARGET})

# Rapfi (gomoku, pbrain protocol) - CMakeLists at repo root
add_subdirectory(engines/rapfi  rf_build)
chuying_bridge(chuying_rapfi ${RF_TARGET})
