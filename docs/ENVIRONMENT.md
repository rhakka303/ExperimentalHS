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
make CFLAGS="-std=gnu89 -Wno-deprecated-non-prototype -fPIC"
make install
```

(`CFLAGS`/`configure` flags match hypseus's own `BuildLibMPEG2.cmake` module, targeting Android API 28 for consistency with the vcpkg triplet, **plus `-fPIC`** — required because this static lib ends up linked into a `SHARED` library (`libmain.so`) on Android, not a standalone executable like `BuildLibMPEG2.cmake`'s desktop target. Without it, the final link fails with `relocation R_AARCH64_ADR_PREL_PG_HI21 cannot be used against symbol '...'; recompile with -fPIC` — found and fixed while building #20.)

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

## Phase C — full source compile + link (`libmain.so`)

`ninja` (same build directory as above) compiled 179/182 objects cleanly on the first pass. Three real portability issues surfaced, all in `src/io/`, all fixed:

1. **`serialib.cpp`** — used `FNDELAY`, a BSD/glibc alias for `O_NONBLOCK` not defined on Android's Bionic libc. Fixed: use `O_NONBLOCK` directly (POSIX-standard, identical behavior, portable everywhere this code already targets).
2. **`network.cpp`** — a genuine upstream inconsistency: `fnv1a()`/`read_id()` (used to build a machine-fingerprint ID) are *defined* under `#ifdef LINUX` (hypseus's own build-system macro, set only when `CMAKE_SYSTEM_NAME` is literally `Linux`) but *used* under `#elif defined(__linux__)` (the compiler-predefined macro, true on **any** Linux-kernel target — including Android, which is Linux-kernel-based but never sets hypseus's custom `LINUX` macro since its `CMAKE_SYSTEM_NAME` is `Android`). On desktop Linux both macros are simultaneously true so this "works" there; on Android only `__linux__` is true, so the usage site compiled but the functions were never defined. Also: `read_id()` reads `/etc/machine-id`, a systemd/desktop-Linux file that doesn't exist on Android — even a compiling version would silently produce a meaningless ID. The function already has a graceful `"Unknown"` fallback for platforms without a specific implementation, so the correct fix wasn't making Android compile that code — it was excluding Android from the `__linux__` branch (`#elif defined(__linux__) && !defined(__ANDROID__)`) so it falls through to the existing fallback instead.
3. **`mpo_fileio.h`** — `MPO_FOPEN`/`MPO_FSEEK`/`MPO_FTELL` (core file I/O, not optional) had branches for `LINUX` (`fopen64`/`fseeko64`/`ftello64`, glibc-specific) and `MAC_OSX` (`fopen`/`fseeko`/`ftello`) but no Android branch at all. Fixed by adding an `__ANDROID__` branch using the same plain-named functions as the macOS branch — Bionic has no glibc-style `*64` variants, and `off_t` is already 64-bit by default for LP64 targets like `arm64-v8a`.

After those three fixes, all 182 objects compiled, but the final **link** failed:

```text
ld.lld: error: relocation R_AARCH64_ADR_PREL_PG_HI21 cannot be used against symbol 'mpeg2_idct_copy'; recompile with -fPIC
```

Root cause: the pre-built `libmpeg2.a` from Phase B was compiled as a plain static library for eventual linking into an executable, not a `SHARED` object — never built with `-fPIC`. Every other Phase B dependency (vcpkg-built) already had `-fPIC` handled automatically by vcpkg's toolchain; `libmpeg2` was the one manually-driven autotools build, and this requirement was missed until the actual link step surfaced it. Fixed by rebuilding `libmpeg2` with `-fPIC` added to `CFLAGS` (the recipe above is already updated) and re-copying the result to `prebuilt/libmpeg2/arm64-v8a/`.

**Result: `libmain.so` links successfully** — 35MB, verified genuine `DYN`/`AArch64` via `llvm-readelf -h`. Checked `NEEDED` dynamic entries: only standard Android system libraries (`libc`, `libm`, `libdl`, `liblog`, `libandroid`, `libOpenSLES`, `libGLESv1_CM`, `libGLESv2`) — everything else (SDL3 family, Vorbis, libzip, libmpeg2, all of hypseus's own module code) is statically linked into the one `.so`, simplifying APK packaging (no extra native libs to bundle separately).

Build invocation: same `cmake ... -G Ninja` configure command as Phase C's configure section above, then `ninja` in the build directory.

## Phase C — Gradle wiring + real-device verification

Wired the verified-working direct-CMake build (above) into the actual `android/` Gradle project, replacing Phase A's hello-world stub.

**`app/build.gradle`** — `externalNativeBuild.cmake.path` now points directly at `../../hypseus-singe/src/CMakeLists.txt` rather than a `jni/CMakeLists.txt` wrapper using `add_subdirectory()`. Tried the wrapper approach first; it broke because hypseus's own `CMakeLists.txt` uses `${CMAKE_SOURCE_DIR}` (the true top-level project directory) for its `cmake/modules` lookup (`include(TargetArch)`), and `add_subdirectory()` doesn't change what `CMAKE_SOURCE_DIR` means — it kept resolving to the wrapper's directory, not `hypseus-singe/src`, so `TargetArch` couldn't be found. Pointing Gradle directly at hypseus's own `CMakeLists.txt` keeps it as the actual top-level project, exactly matching the direct-`cmake` invocation that was already verified working — the `android/app/jni/` directory (Phase A's SDL-template scaffold) was removed entirely, no longer needed.

`cmake.arguments` carries the same vcpkg toolchain flags verified in the direct-CMake testing (`CMAKE_TOOLCHAIN_FILE`, `VCPKG_TARGET_TRIPLET=arm64-android`, `VCPKG_CHAINLOAD_TOOLCHAIN_FILE`, `PKG_CONFIG_EXECUTABLE`) — AGP passes its own `-DCMAKE_TOOLCHAIN_FILE` (the NDK's toolchain file) first, and these override it since `vcpkg.cmake` chain-loads the NDK toolchain internally anyway. `abiFilters` is `arm64-v8a` only now, not `arm64-v8a, x86_64` — Phase B's vcpkg dependencies were only ever built for the `arm64-android` triplet (matching the real target hardware), never `x86_64-android`.

**ARM64 emulation is not possible on this Windows/x86_64 host at all** — confirmed by trying: Google's QEMU2-based Android Emulator refuses arm64 system images outright on an x86_64 host (`FATAL: Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host`), not a configuration problem, no workaround. Real device testing (Retroid Pocket 5, connected via USB/ADB) is the only on-device verification path on this machine going forward — which is also more representative anyway, matching the actual target hardware.

Building via Gradle surfaced two more real runtime issues beyond what direct-CMake testing could catch (JNI/Activity-layer behavior, not visible from a bare `ninja` build):

1. **`dlopen failed: library "libSDL3.so" not found`, app never progressed past this.** `SDLActivity`'s default `getLibraries()` returns `["SDL3", "main"]` and `loadLibraries()` loads each in sequence, throwing `UnsatisfiedLinkError` on failure — but hypseus links SDL3 *statically* into `libmain.so` (Phase C's CMake target), so there's no separate `libSDL3.so` to find, and the loop broke before ever reaching `"main"`. Fixed the documented way (`getLibraries()` is explicitly marked as the override point for exactly this): added `HypseusActivity extends SDLActivity` overriding `getLibraries()` to return just `["main"]`, updated `AndroidManifest.xml`'s launcher activity to reference it instead of the base `SDLActivity`.
2. **`nativeRunMain(): Couldn't find function SDL_main in library ...libmain.so`.** `hypseus.cpp` included `<SDL3/SDL.h>` but not `<SDL3/SDL_main.h>` — the latter's macro magic is what renames `int main(...)` to the `SDL_main` symbol Android's JNI glue looks up by name (desktop builds call `main()` directly via the OS and don't need this). Fixed by adding the include, guarded `#ifdef ANDROID` since desktop doesn't need it.
3. **`Fatal signal 6 (SIGABRT)` in `RenderThread`, crash entirely inside Android's own system UI compositor** (`libhwui.so`, `libGLESv2_adreno.so`, Qualcomm's proprietary shader compiler `libllvm-qgl.so`) — not in `libmain.so`/hypseus/SDL code at all. This is a known class of conflict between Android's hardware-accelerated view compositor and an app managing its own direct OpenGL context (as SDL does). Fixed by setting `android:hardwareAccelerated="false"` on the `<application>` element in `AndroidManifest.xml` — SDL doesn't need Android's own hardware-accelerated view rendering layered on top of its own GL context.

**Result, verified on the physical Retroid Pocket 5 (not just the emulator):** APK installs, launches, `libmain.so` loads, `SDL_main` is found and runs. No crash. The process exits shortly after (confirmed via `adb logcat` — no crash signal, no tombstone, a clean process exit) because it was launched with no game arguments (`SDLActivity`'s default `getArguments()` returns an empty array) — traced directly to hypseus's own `main()`: `parse_cmd_line(argc, argv)` returning false on missing/invalid arguments is an explicitly documented, intentional non-crashing path in `hypseus.cpp` ("hypseus must NEVER segfault!"). This is expected, correct behavior for this checkpoint — actually loading and displaying a real game needs valid launch arguments, which requires an actual game the user has and is Phase D's job (the GUI launcher that constructs those arguments), not this one.

## Phase D — Kotlin/Compose launcher shell + game folder picker (#27)

Added a real Kotlin/Compose UI layer on top of the existing native SDL stack. `android/build.gradle` picked up the `org.jetbrains.kotlin.android` plugin (`1.9.24`); `app/build.gradle` added `compose true` under `buildFeatures`, a `compose-bom` (`2024.06.00`) platform dependency plus `core-ktx`, `activity-compose`, `compose.ui`, `material3`, `lifecycle-runtime-ktx`; `gradle.properties` gained `android.useAndroidX=true` (required the moment any AndroidX/Compose dependency is pulled in — build fails without it). `minSdkVersion` bumped 21 → 30 (reasonable floor for this project's actual target hardware regardless of the storage-permission approach below). `compileOptions`/`kotlinOptions` pinned to JVM target 1.8.

**`MainActivity` (new, Kotlin) is now the app's launcher** (`MAIN`/`LAUNCHER` intent-filter moved to it in `AndroidManifest.xml`), not `HypseusActivity` — the native SDL game view is launched explicitly from `MainActivity` once a game is picked (constructing CLI-equivalent args; not yet implemented, that's #29). `HypseusActivity` keeps only its `USB_DEVICE_ATTACHED` intent-filter. A new `LauncherTheme` (`android:Theme.Material.Light.NoActionBar`) was added in `styles.xml` for `MainActivity` — a normal app screen, unlike `HypseusActivity`'s fullscreen-immersive `AppTheme`.

**Storage access — deliberately scoped, not broad.** The first implementation attempt requested `MANAGE_EXTERNAL_STORAGE` (All Files Access) plus a custom native filesystem-browser screen (`FolderBrowser.kt`), and verified the permission screen rendering correctly on-device. The repo owner explicitly rejected this on privacy grounds: *"it should only have access to it's system folder, and whatever folder i pick for game folder, media, and bezel, it shouldnt have access to modify or delete all files on my device"* — confirmed again after discussing the SAF tradeoff: *"no one will install, you dont want it to have access to everything. Only access to the folders i choose and its own system files when it installs."* This is a binding constraint for the rest of the project: **no broad/All-Files-Access storage permission, ever** — only app-private files plus whatever specific folders the user explicitly picks via Storage Access Framework.

Rearchitected around SAF instead: `MainActivity.kt` uses `ActivityResultContracts.OpenDocumentTree()` to launch the system folder picker, then `contentResolver.takePersistableUriPermission()` to persist access to just that one tree across app restarts — scoped to exactly the folder picked, nothing else. `MANAGE_EXTERNAL_STORAGE` was removed from `AndroidManifest.xml` (replaced with a comment explaining why), and `FolderBrowser.kt` was deleted outright — no longer needed since SAF supplies its own picker UI.

**Real-path resolution.** SAF hands back a `content://` tree URI, not a filesystem path, but hypseus's native I/O is plain `fopen()` and can't consume a URI. `resolveRealPath()` in `MainActivity.kt` parses the real path out of `DocumentsContract.getTreeDocumentId(treeUri)`, whose document ID is formatted `"<volume>:<relative-path>"` — `"primary"` maps to `Environment.getExternalStorageDirectory()`, any other value is the SD card's actual volume ID, mapped to `/storage/<volume-id>/<relative-path>`. This mapping isn't official/guaranteed-stable API, but is widely relied on in practice; it was verified working end-to-end on the physical Retroid Pocket 5 for **both** internal storage and a real SD card, resolving to correct, existing, readable absolute paths in both cases.

**A real OS-level SAF restriction was hit and worked around during testing:** Android's folder picker refuses certain special top-level folders (e.g. `Download`, `DCIM`, and volume roots themselves) with "Can't use this folder — To protect your privacy, choose another folder." Not a bug in this app — subfolders within those restricted folders remain selectable. Confirmed live: picking `Download` directly was blocked, picking `Download/Keys-22.1.0` worked and resolved correctly.

**Result, verified on the physical Retroid Pocket 5:** launching the app shows the empty-dashboard "+" state; tapping it opens the real Android SAF folder picker; picking `sdcard/roms/singe` (the owner's real test-game location, for #28/#29) resolves via `resolveRealPath()` to the correct absolute path and persists across the picker's `ALLOW` confirmation. Same flow verified against internal storage as well.
