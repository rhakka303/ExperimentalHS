set( LIB_SOURCES
    ldp.cpp
    framemod.cpp
    ldp-vldp.cpp
    ldp-vldp-audio.cpp
)


set( LIB_HEADERS
    framemod.h
    ldp.h
    ldp-vldp.h
)

IF(ANDROID)
    # PKG_SEARCH_MODULE below breaks here: pkg-config's Windows-style
    # backslash+space output (this project's path contains a space)
    # trips CMake's FindPkgConfig string parsing - same root cause as
    # other path-with-space issues documented in docs/ENVIRONMENT.md.
    # vcpkg's own FindVorbis.cmake module (invoked by find_package(Vorbis)
    # below) resolves vorbisfile via plain find_library/find_path, not
    # pkg-config directly for the lookup itself, so it's unaffected.
ELSEIF(NOT APPLE)
    PKG_SEARCH_MODULE(VORBISFILE REQUIRED vorbisfile)
ENDIF()

find_package(Vorbis REQUIRED)
find_package(Ogg REQUIRED)

include_directories( ${Vorbis_File_INCLUDE_DIR} ${OGG_INCLUDE_DIRS})

add_library( ldp-out ${LIB_SOURCES} ${LIB_HEADERS} )
if(ANDROID)
    target_link_libraries( ldp-out PRIVATE vldp game plog Vorbis::vorbisfile sdl3_deps )
else()
    target_link_libraries( ldp-out PRIVATE vldp game plog ${VORBISFILE_LIBRARIES} sdl3_deps )
endif()
