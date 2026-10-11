cmake_minimum_required(VERSION 3.10)

project("aimbot")

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

add_library(aimbot SHARED
    aimbot.cpp
)

target_compile_options(aimbot PRIVATE
    -std=c++17
    -frtti
    -fexceptions
    -fvisibility=default
)

target_link_libraries(aimbot
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)