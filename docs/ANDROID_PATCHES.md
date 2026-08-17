# Android patches to vendored hypseus-singe

`hypseus-singe/` is vendored as plain files (not a git submodule - the owner
confirmed no ongoing connection to DirtBagXon's GitHub is wanted). That means
when a new upstream hypseus-singe release comes out, updating the vendored
copy is a manual replace, not a `git pull` - and every patch listed here has
to be manually reapplied to the new copy afterward, or Android support breaks
again in the exact same ways it was already fixed.

**When updating to a new hypseus-singe version:** replace the vendored files,
then go through every entry below in order and confirm/reapply it against the
new source. Some may already be fixed upstream (check first, don't blindly
reapply) - note that here if so.

Full commit history for these patches is in Hypdroid's own git log
(`git log --oneline --stat -- hypseus-singe/`), which is the authoritative
source if this doc and the code ever disagree.

---

## `src/CMakeLists.txt`

**What:** On Android, builds `libmain.so` (a `SHARED` library named `main`)
instead of the desktop `hypseus` executable - SDL3's Android JNI shell loads
the app via `System.loadLibrary("main")`. Also points `libmpeg2` at a
pre-built `prebuilt/libmpeg2/arm64-v8a/` artifact instead of triggering
upstream's autotools `ExternalProject_Add` cross-compile step, which doesn't
thread cleanly through an Android CMake configure.

**Why:** Android has no concept of a standalone executable entry point the
way desktop does; SDL's Android backend requires a specific shared-library
target name. The autotools libmpeg2 build is fragile to cross-compile inline
during CMake configure on Android specifically.

**Origin:** #19 (`217cd42`)

**Reapply:** wrap the `build_libmpeg2()` call and the final
`add_executable(hypseus ...)` / `target_link_libraries` block in
`if(ANDROID) ... else() ... endif()`, matching the pattern in the current
file. If upstream's own CMakeLists.txt structure has changed significantly,
re-derive the split rather than blindly diffing.

---

## `src/ldp-out/CMakeLists.txt`

**What:** On Android, skips `PKG_SEARCH_MODULE(VORBISFILE REQUIRED
vorbisfile)` and links `Vorbis::vorbisfile` (vcpkg's own imported target)
directly instead.

**Why:** `pkg-config`'s Windows-style backslash+space output (this project's
path contains a space) breaks CMake's `FindPkgConfig` string parsing. vcpkg's
own `FindVorbis.cmake` resolves the same library via plain
`find_library`/`find_path`, unaffected by the same bug.

**Origin:** #19 (`217cd42`)

**Reapply:** check whether upstream has moved off `PKG_SEARCH_MODULE` for
this lookup - if so this patch may no longer be needed. Otherwise, gate it
behind `if(ANDROID)` the same way.

---

## `src/io/mpo_fileio.h`

**What:** Adds an `#ifdef __ANDROID__` branch defining `MPO_FOPEN`/
`MPO_FSEEK`/`MPO_FTELL` as plain `fopen`/`fseeko`/`ftello` (same as the
existing macOS branch).

**Why:** Android's Bionic libc has no glibc-style `fopen64`/`fseeko64`/
`ftello64` variants that the Linux branch expects. `off_t` is already 64-bit
by default on arm64-v8a (LP64), so the plain-named functions are correct.

**Origin:** #20 (`e50733f`)

**Reapply:** almost certainly still needed - this is a Bionic libc gap, not
an upstream hypseus quirk. Check upstream hasn't already added an Android
branch here before reapplying.

---

## `src/io/network.cpp`

**What:** `getUid()`'s `#elif defined(__linux__)` branch (which reads
`/etc/machine-id` via `read_id()`) now excludes Android:
`#elif defined(__linux__) && !defined(__ANDROID__)`.

**Why:** `read_id()`/`fnv1a()` were originally written under hypseus's own
`#ifdef LINUX` build macro (never set for Android builds) but *used* under
`#elif defined(__linux__)` (a compiler-predefined macro, true on Android too
since it's Linux-kernel-based) - a real upstream inconsistency. Even if made
to compile, `/etc/machine-id` doesn't exist on Android, so the code would
just fail at runtime instead - excluding Android here falls through to the
existing "Unknown" fallback instead, which is correct.

**Origin:** #20 (`e50733f`)

**Reapply:** check whether upstream's own `#ifdef LINUX`/`#elif
defined(__linux__)` split has changed. If upstream ever properly guards this
under their own `LINUX` macro consistently, this patch becomes unnecessary.

---

## `src/io/serialib.cpp`

**What:** `openDevice()`'s `fcntl(fd, F_SETFL, FNDELAY)` changed to
`fcntl(fd, F_SETFL, O_NONBLOCK)`.

**Why:** `FNDELAY` is a BSD/glibc alias for `O_NONBLOCK`, not defined on
Android's Bionic libc. `O_NONBLOCK` is the POSIX-standard name and behaves
identically everywhere this branch already targets.

**Origin:** #20 (`e50733f`)

**Reapply:** straightforward find-and-replace if upstream still uses
`FNDELAY` here. Safe to reapply even if not strictly needed - `O_NONBLOCK` is
correct on every platform this code targets.

---

## `src/hypseus.cpp`

**What:** Adds `#include <SDL3/SDL_main.h>` under `#ifdef ANDROID`, right
after the existing `#include <SDL3/SDL.h>`.

**Why:** SDL3's Android JNI glue looks up the app's entry point by the symbol
name `SDL_main`, not `main`. `SDL_main.h`'s macro magic renames `main()` to
that symbol at compile time. Desktop builds call `main()` directly via the OS
and don't need this include.

**Origin:** #21 (`a61d394`)

**Reapply:** almost certainly still needed for any SDL3-based Android build.
Check the exact macro/include hasn't changed name in a newer SDL3 version
before reapplying verbatim.

---

## `src/game/singe/CMakeLists.txt`

**What:** Two `target_compile_options` added to the `singeproxy` target,
Android/ARM-scoped:

1. `-fno-strict-aliasing` (gated `if(CMAKE_SYSTEM_PROCESSOR MATCHES
   "arm*|aarch64")`) - a real, independently-justified Lua 5.1 build
   requirement (Lua 5.1's `TValue`/`GCObject` representation relies on
   pointer type-punning that strict-aliasing optimizations can silently
   break), documented upstream but never applied in this vendored copy.
2. `-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0` (unconditional on this target) -
   works around a confirmed `_FORTIFY_SOURCE` **false positive** in Bionic's
   fortified `strchr()`, triggered by Lua's `TString` "struct hack" allocation
   pattern (`lstring.h`: one `malloc(sizeof(TString)+len+1)`, then pointer
   arithmetic past the header) which `__builtin_object_size()` can't see
   through, causing Bionic to under-report the buffer size and abort on
   completely safe reads.

**Why:** Real crash symptom - `SIGABRT` / `FORTIFY: strchr: prevented read
past end of buffer` inside Lua's `traversetable()` (`lgc.c`), during weak-table
GC traversal. Root-caused via direct memory inspection (dumping the live
string buffer at the crash site, confirming the data was always valid and
correctly null-terminated) - this is a known false-positive category for any
C codebase using this allocation pattern, not a Lua-specific bug and not real
memory corruption. Full investigation trail: #29 (initial GC-stop workaround,
since reverted) and #35 (the actual root-cause fix).

**Reapply:** both flags should still apply to any Lua 5.1-based build on
Bionic/arm64 - this isn't specific to any one hypseus version, it's a
compiler/libc/Lua-internals interaction. Re-verify against the new version's
actual crash behavior rather than assuming blindly (build with GC fully
unrestricted, watch the crash site under real gameplay, confirm zero
failures - same verification method used in #35).

---

## Not currently patched: `src/game/singe/singeproxy.cpp`

Issue #29 added temporary diagnostic `lua_gc(..., LUA_GCSTOP/GCRESTART, ...)` calls to `sep_startup()` while investigating the FORTIFY crash above. Issue #35 fully root-caused the real issue and removed both diagnostic lines - between that fix and the asset-path patch below, the file went through a period of being fully unpatched (matching upstream exactly) before picking up the new patch.

---

## `src/game/singe/singeproxy.cpp` - asset path resolution for unzipped Singe-script games

**What:** `sep_sound_load()`, `sep_music_load()`, `sep_font_load()`,
`sep_sprite_load()`, and `sep_sprite_loadframes()` all share the same
idiom: use an in-memory zip-extraction path when the game is zipped
(`SEP_HAS(SEP_ROM_ZIP)`), otherwise pass a **bare relative path straight from
the Lua script** to an SDL/TTF/MIX/IMG loader (`SDL_LoadWAV`, `TTF_OpenFont`,
`MIX_LoadAudio`, `IMG_LoadAnimation`, `IMG_Load`).

**Why this breaks on Android:** SDL3's Android backend resolves any bare
relative path by checking internal app storage first, then the APK's own
bundled assets - it never resolves against `-homedir`/`-datadir`/cwd (same
root cause already documented in `HypseusAssets.kt` for why `pics`/`fonts`/
`midi`/`sound` have to be copied into internal storage in the first place).
For unzipped Singe-script games (games shipped as a bare `.singe` file, not
a `roms/<name>.zip`), every non-video asset load through these five functions
hits this exact wall - confirmed via real on-device reproduction (a real
Singe-script game, `SDL_LoadWAV` failing with `Couldn't open asset '...'`
even though the file genuinely existed at the correct real path on the SD
card).

**Fix:** resolve the relative path against the real `-homedir` explicitly
(via `homedir::find_file()`, already used elsewhere in this same file) before
calling the loader, Android-only.

**Origin:** this session, filed as issue #56 (see that issue for the full
investigation trail and on-device repro).

**Reapply:** the underlying SDL3-Android relative-path routing behavior is a
platform characteristic, not a hypseus version quirk - should still apply to
any future hypseus-singe version using the same `SEP_HAS(SEP_ROM_ZIP)` /
loader idiom. Re-check the five call sites still use the exact same pattern
before assuming a blind reapply is correct.
