cmake_minimum_required(VERSION 3.22.1)
project("hyperdex_native" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED HYPERDEX_BUILD_PEPPER)
    set(HYPERDEX_BUILD_PEPPER "UNOFFICIAL_LOCAL_OR_FORK_BUILD")
endif()

# 1. Pure C11 Kernel, APK V2/DEX Integrity, Anti-Hook & Checksum Library (libhyper_sys_c.so)
add_library(hyper_sys_c SHARED
    hyper_sys.c
)
target_compile_options(hyper_sys_c PRIVATE
    -O3
    -fvisibility=hidden
    -Wall
    -Wextra
    -Wno-unused-parameter
)
target_link_libraries(hyper_sys_c
    log
)

# 2. Modern C++20 OpenGL ES 3.0 Shader Engine, Acrylic Blur, PE64 Dissector & Quorum (libhyper_gfx_cc.so)
add_library(hyper_gfx_cc SHARED
    hyper_gfx.cc
)
target_compile_definitions(hyper_gfx_cc PRIVATE
    HYPERDEX_BUILD_PEPPER="${HYPERDEX_BUILD_PEPPER}"
)
target_compile_options(hyper_gfx_cc PRIVATE
    -O3
    -fvisibility=hidden
    -Wall
    -Wextra
    -Wno-unused-parameter
)
target_link_libraries(hyper_gfx_cc
    GLESv3
    EGL
    jnigraphics
    log
)
