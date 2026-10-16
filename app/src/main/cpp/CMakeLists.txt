cmake_minimum_required(VERSION 3.22.1)
project(simmer_whisper CXX C)

set(CMAKE_CXX_STANDARD 17)

# Native code ships as a Release build in every variant: an unoptimised whisper.cpp took 44 s for a 10 s clip
# (spike #86), an optimised one 2.6 s. Flags are forced here because the Gradle debug variant would pass -O0.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

include(FetchContent)
# whisper.cpp v1.9.5 (MIT), pinned to its commit.
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG d1be6fde11ac6e0407606b4e42fe72d34add8037
)
FetchContent_MakeAvailable(whisper)

add_library(simmer_whisper SHARED whisper_jni.cpp)
target_link_libraries(simmer_whisper whisper log android)
# 16 KB page size alignment (Android 15+ devices).
target_link_options(simmer_whisper PRIVATE -Wl,-z,max-page-size=16384)
