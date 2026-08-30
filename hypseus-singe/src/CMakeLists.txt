cmake_minimum_required(VERSION 3.16)
cmake_policy(SET CMP0074 NEW)

set(PKG_VERSION "v3.0.2")

project(hypseus)

option(BENCHMARK        "Benchmark"             OFF)
option(DEBUG            "Debug"                 OFF)
option(VLDP_DEBUG       "VLDP Debug"            OFF)
option(CPU_DEBUG        "CPU Debug"             OFF)
option(BUILD_SINGE      "Singe"                 ON)
option(BUILDBOT         "Buildbot"              OFF)
option(ASAN             "Address Sanitizer"     OFF)

# Abstract out -espath ES extension: Use .hypseus NOT .daphne
option(ABSTRACT_SINGE   "Redefine Singe LUA Rewrites"     ON)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE "Release")
endif()

if (UNIX)
  if (CMAKE_SIZEOF_VOID_P EQUAL 4)
      message(STATUS "Compiling with 64-bit file/time support")
      add_compile_definitions(
          _FILE_OFFSET_BITS=64
          _TIME_BITS=64
      )
  endif()
endif()

set(DOWNLOAD_ARGS)
if (CMAKE_VERSION VERSION_GREATER_EQUAL "3.24.0")
    list(APPEND DOWNLOAD_ARGS DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
endif()

add_compile_options(-Wall)
add_compile_definitions(SDL_DISABLE_OLD_NAMES)

if (ASAN)
    add_compile_options(-fsanitize=address -fno-omit-frame-pointer -g)
endif()

list(APPEND CMAKE_MODULE_PATH "${CMAKE_SOURCE_DIR}/cmake/modules")

include(TargetArch)
target_architecture(CMAKE_TARGET_ARCHITECTURES)

# Config.h defines
string(TOUPPER ${CMAKE_SYSTEM_NAME} TARGET_SYSTEM)
string(CONCAT TARGET_SYSTEM TARGET_ ${TARGET_SYSTEM})
string(TOUPPER ${CMAKE_TARGET_ARCHITECTURES} TARGET_ARCH)
string(CONCAT TARGET_ARCH TARGET_ ${TARGET_ARCH})

include(GNUInstallDirs)
include(GetGitRevisionDescription)
include(InstallRequiredSystemLibraries)
include(FindPkgConfig)
include(ExternalProject)
include(BuildLibMPEG2)

set (CMAKE_CXX_STANDARD 11)

if( ANDROID )
    # The upstream build_libmpeg2() macro cross-compiles via an autotools
    # ExternalProject_Add step (libtoolize/autoreconf/configure/make),
    # triggered mid-CMake-configure. That's fragile to wire through a
    # cross-compiling CMake configure on Android (needs a POSIX shell +
    # autotools + the NDK toolchain all correctly threaded through
    # ExternalProject's subprocess environment). Since Phase B already
    # cross-compiled and verified libmpeg2 for arm64-v8a by hand (see
    # docs/ENVIRONMENT.md), point directly at that pre-built artifact
    # instead of re-triggering the autotools build here.
    set( HYPDROID_PREBUILT_DIR "${CMAKE_SOURCE_DIR}/../../prebuilt" )
    set( MPEG2_INCLUDE_DIRS "${HYPDROID_PREBUILT_DIR}/libmpeg2/arm64-v8a/include/mpeg2dec" )
    set( MPEG2_LIBRARIES "${HYPDROID_PREBUILT_DIR}/libmpeg2/arm64-v8a/lib/libmpeg2.a" )
    if( NOT EXISTS "${MPEG2_LIBRARIES}" )
        message( FATAL_ERROR "Pre-built libmpeg2 not found at ${MPEG2_LIBRARIES} - see docs/ENVIRONMENT.md for the build recipe." )
    endif()
else()
    build_libmpeg2( )
endif()

message(STATUS "Target: ${CMAKE_SYSTEM_NAME} ${CMAKE_TARGET_ARCHITECTURES}")

git_describe(VER_TAG --tags --abbrev=0)
if("${VER_TAG}" MATCHES "-NOTFOUND")
    set(VER_TAG  ${PKG_VERSION})
    set(VER_GIT_COUNT "0")
else()
    git_revlist(VER_GIT_COUNT --count ${VER_TAG}..HEAD)
endif()
string(REGEX REPLACE "^v" "" RESULT ${VER_TAG})
string(REGEX REPLACE "[-\\.]" ";" RESULT ${RESULT})
# hypseus-singe is vendored as plain files in the same git repo as Hypdroid
# (not a submodule), so git describe --tags --abbrev=0 also picks up
# Hypdroid's own release tags - deliberately 2-part (v1.0, v2.0), not the
# 3-part MAJOR.MINOR.PATCH this code otherwise assumes. Unguarded list(GET)
# crashed (list index: 2 out of range) the moment such a tag existed (#127).
# Defensive: any tag with fewer than 3 parts gets "0" for the missing ones.
list(LENGTH RESULT VER_PARTS_COUNT)
if(VER_PARTS_COUNT GREATER 0)
    list(GET RESULT 0 VER_MAJOR)
else()
    set(VER_MAJOR "0")
endif()
if(VER_PARTS_COUNT GREATER 1)
    list(GET RESULT 1 VER_MINOR)
else()
    set(VER_MINOR "0")
endif()
if(VER_PARTS_COUNT GREATER 2)
    list(GET RESULT 2 VER_PATCH)
else()
    set(VER_PATCH "0")
endif()
if(NOT "${VER_GIT_COUNT}" EQUAL "0")
    set(VER_TAG "${VER_TAG}-${VER_GIT_COUNT}")
endif()
git_describe(VER_GIT_DIRTY --always --dirty)
if("${VER_GIT_DIRTY}" MATCHES "-dirty")
    set(VER_TAG "${VER_TAG}-dirty")
endif()
message(STATUS "Version: ${VER_TAG}")

include_directories(
    ${PROJECT_BINARY_DIR} # config.h
)

if( "${CMAKE_TARGET_ARCHITECTURES}" MATCHES "i386" )
    enable_language( ASM )
    set(can_use_assembler TRUE)
endif()

configure_file(
    "${PROJECT_SOURCE_DIR}/config.h.in"
    "${PROJECT_BINARY_DIR}/config.h"
)

find_package(SDL3 CONFIG REQUIRED)

if( TARGET SDL3::SDL3-shared AND NOT TARGET SDL3::SDL3 )
    add_library( SDL3::SDL3 ALIAS SDL3::SDL3-shared )
endif()

find_package(SDL3_ttf CONFIG REQUIRED)
find_package(SDL3_image CONFIG REQUIRED)
find_package(SDL3_mixer CONFIG REQUIRED)
find_package(Vorbis REQUIRED)

message(STATUS "SDL3 version: ${SDL3_VERSION}")
message(STATUS "SDL3_ttf version: ${SDL3_ttf_VERSION}")
message(STATUS "SDL3_image version: ${SDL3_image_VERSION}")
message(STATUS "SDL3_mixer version: ${SDL3_mixer_VERSION}")
message(STATUS "Vorbis version: ${Vorbis_VERSION}")

add_library(sdl3_deps INTERFACE)
target_link_libraries(sdl3_deps INTERFACE
    SDL3::SDL3
    SDL3::Headers
    SDL3_ttf::SDL3_ttf
    SDL3_image::SDL3_image
    SDL3_mixer::SDL3_mixer
)

add_subdirectory(3rdparty)

add_subdirectory(cpu)
add_subdirectory(game)
add_subdirectory(manymouse)
add_subdirectory(io)
add_subdirectory(ldp-in)
add_subdirectory(ldp-out)
add_subdirectory(scoreboard)
add_subdirectory(sound)
add_subdirectory(timer)
add_subdirectory(video)
add_subdirectory(vldp)

if( NOT ANDROID )
    # On Android, libmpeg2 is a pre-built artifact (see above), not a
    # target this CMake invocation builds - no ordering dependency needed.
    add_dependencies( vldp libmpeg2 )
endif()
add_dependencies( ldp-out vldp )
add_dependencies( game vldp )
add_dependencies( sound vldp )

if( ANDROID )
    # SDL3's Android JNI shell loads the app as a shared library named
    # "main" (System.loadLibrary("main")), not a standalone executable.
    add_library( main SHARED hypseus.cpp globals.h )
    target_link_libraries( main plog io timer sound video cpu game ${Vorbis_Enc_LIBRARY} Vorbis::vorbis ${OGG_LIBRARIES} ${VORBISFILE_LIBRARIES} ${Vorbis_File_LIBRARY} SDL3::SDL3 SDL3::Headers SDL3_ttf::SDL3_ttf SDL3_image::SDL3_image SDL3_mixer::SDL3_mixer ${LIBZIP_LIBRARY} )
else()
    add_executable( hypseus hypseus.cpp globals.h )
    target_link_libraries( hypseus plog io timer sound video cpu game ${Vorbis_Enc_LIBRARY} Vorbis::vorbis ${OGG_LIBRARIES} ${VORBISFILE_LIBRARIES} ${Vorbis_File_LIBRARY} SDL3::SDL3 SDL3::Headers SDL3_ttf::SDL3_ttf SDL3_image::SDL3_image SDL3_mixer::SDL3_mixer ${LIBZIP_LIBRARY} )
endif()

if(ASAN)
    target_link_options(hypseus PRIVATE -fsanitize=address -fno-omit-frame-pointer -g)
endif()

#[[
set_source_files_properties( testvldp.cpp PROPERTIES COMPILE_FLAGS -DSHOW_FRAMES)
add_executable( testvldp testvldp.cpp )
target_link_libraries( testvldp PRIVATE video vldp sdl3_deps )
]]

if(BUILDBOT)
    add_custom_command( TARGET hypseus
        POST_BUILD
	COMMAND ${CMAKE_STRIP} $<TARGET_FILE:hypseus>
        COMMAND ${CMAKE_COMMAND} -E remove -f hypseus.zip
        COMMAND 7za a -mpass=4 -mfb=255 -y -tzip hypseus.zip $<TARGET_FILE:hypseus>
        COMMAND ${CMAKE_COMMAND} -E md5sum hypseus.zip > hypseus.md5
        COMMENT "Create zip and md5 for buildbot")
endif()

set(CPACK_PACKAGE_NAME "Hypseus Singe")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Hypseus Singe - Arcade Laserdisc Emulator")
set(CPACK_PACKAGE_VENDOR "DirtBagXon")
set(CPACK_PACKAGE_CONTACT "hypseus@users.noreply.github.com")
set(CPACK_PACKAGE_VERSION "${VER_TAG}")
set(CPACK_PACKAGE_VERSION_MAJOR "${VER_MAJOR}")
set(CPACK_PACKAGE_VERSION_MINOR "${VER_MINOR}")
set(CPACK_PACKAGE_VERSION_PATCH "${VER_PATCH}")

include(CPack)
