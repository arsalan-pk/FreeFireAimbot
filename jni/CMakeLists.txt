cmake_minimum_required(VERSION 3.18.1)
project(aimbot)

# ---------- Build settings ----------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Aggressive optimisation for low-latency scanning
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -O3 -ffast-math")

# ---------- Shared library ----------
add_library(aimbot SHARED
    aimbot.cpp
)

# ---------- Link libraries ----------
# android  → SurfaceFlinger / libandroid (window info)
# log      → __android_log_print
# jnigraphics → AndroidBitmap_lockPixels / unlockPixels
find_library(LOG_LIB      log)
find_library(ANDROID_LIB  android)
find_library(JNIGRAPHICS_LIB jnigraphics)

target_link_libraries(aimbot
    ${LOG_LIB}
    ${ANDROID_LIB}
    ${JNIGRAPHICS_LIB}
    jnigraphics
)

# ---------- Install ----------
install(TARGETS aimbot LIBRARY DESTINATION libs/${ANDROID_ABI})