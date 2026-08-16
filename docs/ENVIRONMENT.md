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

```
libSDL3_image.a  Machine: AArch64
libSDL3_ttf.a    Machine: AArch64
libSDL3_mixer.a  Machine: AArch64
```

Remaining Phase B dependencies (Vorbis/Ogg, libzip, libmpeg2) are tracked separately — see the project's issue tracker.
