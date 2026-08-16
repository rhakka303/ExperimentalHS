cmake_minimum_required(VERSION 3.6)

project(GAME)

# SDL is vendored as a git submodule at the repo root (../../../sdl relative
# to this file), not duplicated under android/ — single source of truth.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../sdl ${CMAKE_CURRENT_BINARY_DIR}/SDL)

# Compilation of companion libraries
#add_subdirectory(SDL_image)
#add_subdirectory(SDL_mixer)
#add_subdirectory(SDL_ttf)

# Your game and its CMakeLists.txt are in a subfolder named "src"
add_subdirectory(src)

