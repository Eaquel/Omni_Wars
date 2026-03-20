cmake_minimum_required(VERSION 3.22.1)
project(warcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(warcore SHARED
    WarCore.cpp
    WarCheat.cpp
    WarSettings.cpp
)

find_library(log-lib       log)
find_library(android-lib   android)
find_library(gles3-lib     GLESv3)
find_library(egl-lib       EGL)
find_library(aaudio-lib    aaudio)
find_library(opensl-lib    OpenSLES)

target_link_libraries(warcore
    ${log-lib}
    ${android-lib}
    ${gles3-lib}
    ${egl-lib}
    ${aaudio-lib}
    ${opensl-lib}
)

target_compile_options(warcore PRIVATE
    -O3
    -ffast-math
    -fvisibility=hidden
    -Wall
    -Wno-unused-variable
)
