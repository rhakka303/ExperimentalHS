# Development environment

How to reproduce the native Android toolchain this project builds against. Windows-focused, since that's the current dev machine; adjust paths for other platforms.

## Requirements

Installed via Android Studio's SDK Manager (**Settings → Languages & Frameworks → Android SDK → SDK Tools** tab):

- **NDK (Side by side)** — installed version: `30.0.15729638`
- **CMake** — installed version: `4.1.2`
- **Android SDK Command-line Tools (latest)** — provides `sdkmanager` on the command line

These install under the Android SDK root shown at the top of that same Settings page (`Android SDK Location`), typically `%LOCALAPPDATA%\Android\Sdk` on Windows.

## Environment variables

Set as **User**-scope environment variables (not machine-wide, no admin required):

| Variable | Value |
|---|---|
| `JAVA_HOME` | Android Studio's bundled JDK, e.g. `C:\Program Files\Android\Android Studio\jbr` — `sdkmanager` needs a JDK and none is on `PATH` by default. |
| `ANDROID_HOME` | The Android SDK root, e.g. `%LOCALAPPDATA%\Android\Sdk` |
| `ANDROID_NDK_HOME` | The specific NDK version's folder, e.g. `%LOCALAPPDATA%\Android\Sdk\ndk\30.0.15729638` |

Verify after setting (new terminal session required — these don't propagate to already-open shells):

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --version
& "$env:ANDROID_NDK_HOME\toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe" --version
```

The second command should report an NDK-based clang build and list `aarch64-linux-android*-clang` wrapper binaries alongside it in the same `bin` directory — those are the actual `arm64-v8a` cross-compilers this project targets.

## Notes

- `sdkmanager` reports itself as deprecated in favor of a newer `android` CLI tool as of this NDK/SDK version. Still functional; noted here in case a future session hits removal.

## vcpkg (`arm64-android` triplet)

vcpkg is vendored as a git submodule at `vcpkg/` (pinned to a specific commit, not tracking a branch, for reproducible dependency versions). Its generated output (`buildtrees/`, `packages/`, `downloads/`, `vcpkg_installed/`) is gitignored — only the submodule pointer itself is tracked.

Setup, after cloning this repo:

```powershell
git submodule update --init
cd vcpkg
./bootstrap-vcpkg.bat
```

vcpkg ships a first-party `triplets/arm64-android.cmake` triplet (not a community/unsupported one) — targets `arm64-v8a`, Android API level 28. It picks up the NDK automatically via the `ANDROID_NDK_HOME` env var set above; no extra vcpkg-side configuration needed.

Verified working via a smoke-test install:

```powershell
cd vcpkg
./vcpkg.exe install zlib:arm64-android
```

This built successfully using the NDK's `clang++`, producing genuine `elf64-littleaarch64` object files (confirmed with `llvm-readelf -h`) — the full vcpkg → NDK → cross-compiled arm64 static lib pipeline works end to end.

### Phase B — real dependency list

hypseus's actual dependencies (`find_package(...)` calls in `src/CMakeLists.txt`), cross-compiled and verified for `arm64-android`:

```powershell
cd vcpkg
./vcpkg.exe install sdl3-image:arm64-android sdl3-ttf:arm64-android sdl3-mixer:arm64-android
```

All three (`sdl3-image@3.4.4`, `sdl3-ttf@3.2.2`, `sdl3-mixer@3.2.4`, plus transitive deps like `libpng`, `bzip2`) built successfully — total ~19 min. Output verified as genuine AArch64 via `llvm-readelf -h` on each library, not just "the command exited 0":

```text
libSDL3_image.a  Machine: AArch64
libSDL3_ttf.a    Machine: AArch64
libSDL3_mixer.a  Machine: AArch64
```

Vorbis/Ogg and libzip:

```powershell
cd vcpkg
./vcpkg.exe install libvorbis:arm64-android "libzip[core]:arm64-android"
```

Hit a real port issue here: `libzip`'s default features include `default-aes`, which pulls in **OpenSSL** as a crypto backend on non-Windows/non-macOS platforms (Android counts). OpenSSL's `Configure` script (Perl + MSYS/autotools) failed to build specifically because this project's path (`D:/Claude Code/Hypdroid`) contains a space — a known class of problem for that toolchain on Windows, unrelated to the NDK/vcpkg pipeline itself. Since hypseus only needs plain zip read/write (its ROM sets and zlua packages aren't AES-encrypted), the fix was disabling default features entirely: `libzip[core]:arm64-android` — builds cleanly, no OpenSSL involved. `libvorbis` (and its `libogg` dependency) built without any issue.

All verified as genuine AArch64 via `llvm-readelf -h`:

```text
libogg.a         Machine: AArch64
libvorbis.a      Machine: AArch64
libvorbisenc.a   Machine: AArch64
libvorbisfile.a  Machine: AArch64
libzip.a         Machine: AArch64
```

## libmpeg2 (built from hypseus's vendored source, not vcpkg)

hypseus's `.m2v` laserdisc video decode uses its own vendored `libmpeg2` (`hypseus-singe/src/3rdparty/libmpeg2/libmpeg2-master.tgz`), built via autotools (`configure`/`make`), not CMake and not a vcpkg port. `hypseus-singe/` is vendored here as plain tracked files (not a submodule — Phase C modifies its `CMakeLists.txt` directly, and a submodule can't hold local-only commits reproducibly since it fetches from upstream's own URL), pinned to `v3.0.1`.

**Real obstacle hit:** the same class of problem as the OpenSSL/libzip issue above, but worse — this whole toolchain (`libtoolize`, `autoreconf`, `configure`, `make`) is shell-script-driven, and vcpkg's own downloaded MSYS2 environment lives under this project's path (`D:/Claude Code/Hypdroid/vcpkg/downloads/tools/msys2/...`), which contains a space. `configure` broke immediately trying to find `ld` via `$PATH`, silently word-splitting the space and producing a truncated, nonexistent path. This isn't a vcpkg port bug — it's a structural conflict between this project's path and any shell-script-based (autotools) build.

**Fix:** copied the existing MSYS2 install (already downloaded by vcpkg for `openssl`'s build) to a space-free path — `C:\Users\rhakk\msys2-buildtools\` — and ran the bootstrap/configure/make sequence from there instead. No re-download needed, ~230MB copy. This same space-free MSYS2 copy should be reused for any other autotools-based cross-compile this project needs later, rather than re-diagnosing the same path issue.

```powershell
# One-time setup: copy vcpkg's downloaded MSYS2 to a space-free path
# (only needed for autotools-based builds — vcpkg's own CMake-based ports don't hit this)
Copy-Item -Recurse "vcpkg\downloads\tools\msys2\<hash>" "C:\Users\rhakk\msys2-buildtools"
```

Build sequence (from the space-free MSYS2's `bash.exe`, NDK's `clang`/`llvm-ar`/`llvm-ranlib` as the toolchain):

```text
cd hypseus-singe/src/3rdparty/libmpeg2 && tar -xzf libmpeg2-master.tgz
cd libmpeg2-master
libtoolize --copy --force && autoreconf -f -i
CC=<ndk>/aarch64-linux-android28-clang AR=<ndk>/llvm-ar.exe RANLIB=<ndk>/llvm-ranlib.exe \
  ./configure --host=aarch64-linux-android --prefix=<install-dir> \
  --disable-shared --enable-static --disable-sdl
make CFLAGS="-std=gnu89 -Wno-deprecated-non-prototype"
make install
```

(`CFLAGS`/`configure` flags match hypseus's own `BuildLibMPEG2.cmake` module exactly, targeting Android API 28 for consistency with the vcpkg triplet.)

Result: clean install layout (`include/mpeg2dec/*.h`, `lib/libmpeg2.a`, `lib/libmpeg2convert.a`) matching exactly what `BuildLibMPEG2.cmake` expects (`MPEG2_INCLUDE_DIRS`, `MPEG2_LIBRARIES`). Verified genuine AArch64 via `llvm-readelf -h` on the installed `libmpeg2.a`.

All of Phase B's dependencies are now cross-compiled and verified for `arm64-android`.

**Final destination for Phase C:** copy the installed `include/` and `lib/` directories into `prebuilt/libmpeg2/arm64-v8a/` at the repo root (gitignored, reproducible via the recipe above) — that's the path hypseus's Android CMake target (see below) expects.

## Phase C — CMake configure for the Android target

With Phase B's dependencies in place, hypseus's own `CMakeLists.txt` configures successfully for Android. Two changes were needed beyond the dependency setup:

1. **`src/CMakeLists.txt`**: added an `if(ANDROID)` branch producing `add_library(main SHARED hypseus.cpp globals.h)` instead of `add_executable(hypseus ...)` (SDL3's Android JNI shell loads the app via `System.loadLibrary("main")`, not as a standalone process). Also replaced the unconditional `build_libmpeg2()` call (which triggers an autotools `ExternalProject_Add` mid-configure — fragile to cross-compile automatically) with a direct reference to the pre-built `prebuilt/libmpeg2/arm64-v8a/` artifact on Android, keeping the original autotools path for non-Android builds.
2. **`src/ldp-out/CMakeLists.txt`**: its `PKG_SEARCH_MODULE(VORBISFILE REQUIRED vorbisfile)` call broke on Android — pkg-config successfully found `vorbisfile.pc`, but CMake's `FindPkgConfig` module choked parsing pkg-config's Windows-style backslash+space output (this project's path contains a space) as an invalid escape sequence. Fixed by skipping `PKG_SEARCH_MODULE` on Android entirely and using the `Vorbis::vorbisfile` imported target that vcpkg's own `FindVorbis.cmake` module already provides (via plain `find_library`/`find_path`, unaffected by the pkg-config parsing issue) from the `find_package(Vorbis REQUIRED)` call directly below it.

Configure invocation:

```powershell
cmake -S hypseus-singe/src -B build-android `
  -DCMAKE_TOOLCHAIN_FILE="vcpkg/scripts/buildsystems/vcpkg.cmake" `
  -DVCPKG_TARGET_TRIPLET=arm64-android `
  -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE="$env:ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a `
  -DANDROID_PLATFORM=android-28 `
  -DCMAKE_MAKE_PROGRAM="$env:ANDROID_HOME/cmake/4.1.2/bin/ninja.exe" `
  -DPKG_CONFIG_EXECUTABLE="C:\Users\rhakk\msys2-buildtools\usr\bin\pkg-config.exe" `
  -G Ninja
```

Result: **configure succeeds cleanly**, no errors. Confirmed via the generated `build.ninja` that a `libmain.so` shared-library target exists with the full dependency graph correctly wired — every vcpkg-installed library (SDL3 family, Vorbis/vorbisfile, libzip), the pre-built `libmpeg2.a`, and all of hypseus's own module static libraries (`plog`, `io`, `timer`, `sound`, `video`, `cpu`, `game`, `ldp-out`, `scoreboard`, `manymouse`, `ldp-in`, `vldp`, `singeproxy`).

Actually compiling the full source tree (likely to surface real portability issues — hypseus is developed against Linux/Windows/macOS/Raspberry Pi, not Android/Bionic) is separate, tracked work — see the project's issue tracker.
