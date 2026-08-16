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

Remaining Phase B dependencies (Vorbis/Ogg, libzip) are tracked separately — see the project's issue tracker.

## libmpeg2 (built from hypseus's vendored source, not vcpkg)

hypseus's `.m2v` laserdisc video decode uses its own vendored `libmpeg2` (`hypseus-singe/src/3rdparty/libmpeg2/libmpeg2-master.tgz`), built via autotools (`configure`/`make`), not CMake and not a vcpkg port. `hypseus-singe/` is vendored here as a git submodule too, pinned to `v3.0.1`.

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
