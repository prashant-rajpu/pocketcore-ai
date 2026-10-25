cmake_minimum_required(VERSION 3.22.1)

project("pocketcore" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Vulkan GPU acceleration flag
set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan GPU acceleration" FORCE)
add_definitions(-DGGML_VULKAN=ON)

# Target ARM64-v8a compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8.2-a+fp16+dotprod")

# Find Android system / NDK libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

# Define native library target
add_library(pocketcore SHARED
    native_engine.cpp
    jni_bridge.cpp
)

target_include_directories(pocketcore PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(pocketcore
    ${vulkan-lib}
    ${log-lib}
    ${android-lib}
)
