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

## Phase D — game folder scanning (#28)

`GameScanner.kt` scans a resolved home folder for both game categories hypseus supports and returns a plain `List<Game>` (name, category, framefile path, ROM/script path) — no UI beyond a plain list yet (that's Phase E), and a folder with no valid games just produces an empty list, never an error.

**Confirmed folder layout** (owner-specified, differs from the upstream doc's example command which splits `-framefile`/`-zlua` across separate `singe/`/`roms/` locations): under whatever folder gets picked as the home dir —

- **Fan-made (Singe) games** each get their own folder directly at the top level, e.g. `<home>/<gamename>/<gamename>.txt` (framefile) plus either `<gamename>.zip` (zipped, `-zlua`) or `<gamename>.singe` (unzipped script, `-script`) alongside it.
- **Daphne-native games**: framefile at `<home>/vldp/<name>/<name>.txt`, ROM zip at `<home>/roms/<name>.zip`. `roms/` and `vldp/` are reserved top-level folder names, skipped when scanning for fan-made games.

Since hypseus's `-framefile`/`-zlua`/`-script` flags take full explicit paths (not relative lookups), the scanner doesn't need to match hypseus's own documented directory convention — it just needs to find matching files, which is what made this layout workable.

**A real naming mismatch was caught before it could cause a silent false-negative:** the owner's actual SD card folder was named `vdlp` (letters transposed), not `vldp` (hypseus's real term, its video/laserdisc-player subsystem). Caught by inspecting the real device folder via `adb shell ls` before writing the scanner, rather than assuming the plan's spelling was already right. Owner chose to rename the folder to `vldp` on the SD card (`adb shell mv`) rather than have the scanner special-case the typo, keeping the app's convention matching hypseus's own terminology throughout.

**Result, verified on the physical Retroid Pocket 5:** picking `sdcard/roms/singe` as the game folder (via the real SAF picker, navigated live: drawer → SD card → roms → singe → ALLOW) correctly detected the one real fan-made game present (`SINGE_ZIPPED`), displayed as a plain list beneath the folder path on the dashboard. The empty `roms/`/`vldp/` reserved folders (no Daphne-native game present yet) were correctly excluded with no error and no false entries. Daphne-native detection and the unzipped-script (`.singe`) Singe form are implemented per the confirmed layout above but not yet verified against a real example of either — no such test data exists on-device yet.

## Phase D — launch args + real game boot (#29)

Constructs the CLI-equivalent `argv` hypseus expects (`LaunchArgs.kt`) and passes it through an `Intent` extra to `HypseusActivity`, which `SDLActivity`'s `getArguments()` override reads and hands to `nativeRunMain()`. "Play" on a scanned game triggers this. Getting a real game to actually boot and play surfaced several genuine bugs, none of them hypothetical - every one below was diagnosed from a real crash on the physical Retroid Pocket 5 and confirmed fixed (or in one case, only partially fixed) by re-testing on the same device.

**Bug 1 - SDL hardcodes `argv[0]`.** `SDL_android.c`'s `nativeRunMain()` always sets `argv[0]` to `"app_process"` itself and appends the Java `String[]` starting at `argv[1]`. An initial version of `LaunchArgs.kt` included a `"hypseus"` placeholder as its first element, intending it as `argv[0]` - this actually landed at `argv[1]`, the slot hypseus's `parse_game_type()` reads as the game-type token, shifting every real argument by one. Result: `parse_game_type()` failed silently (no crash, no error - hypseus's "invalid game type" path is a designed non-crashing exit), matching Phase C's own documented "hypseus must never segfault" behavior. Fix: `buildLaunchArgs()` never includes a placeholder; the array starts directly with the game-type token (`"singe"` or a Daphne shortname).

**Bug 2 - `pics/`/`fonts/`/`sound/`/`midi/` must live in Android's internal app storage, never the SD card homedir.** `video.cpp`'s `load_one_bmp()`/`load_one_png()` pass bare relative strings (`"pics/led0.bmp"`) straight into `SDL_LoadBMP()`/`IMG_Load()`. SDL3's Android backend (`SDL_iostream.c`) special-cases every relative path: tries `<context.filesDir>/pics/led0.bmp` (real internal-storage `fopen()`) first, then falls back to the APK's bundled assets - it never consults the process's cwd (`-datadir`'s `chdir()` is irrelevant to this specific lookup) and never looks at `-homedir`. Confirmed by reading SDL's source and by watching identical `Could not load bitmap` failures persist even after the exact same files were verified present on the SD card. Fix: `HypseusAssets.kt`'s `ensureHypseusAssets(context)` copies these 4 folders (bundled under `android/app/src/main/assets/hypseus/{pics,fonts,sound,midi}/`, ~10MB, copied verbatim from `hypseus-singe/{pics,fonts,sound,midi}`) into `context.filesDir` on every folder pick (no-ops once already present). `hypinput.ini`/`hypinput_gamepad.ini` are deliberately **not** bundled anywhere - `input.cpp` resolves those via `homedir::find_file()` (a genuine absolute-path lookup, unaffected by SDL's relative-path special-casing) and has a fully graceful "use hardcoded defaults" fallback if missing, so skipping them keeps the SD card home folder free of anything that isn't real game content.

**Bug 3 - `SDL3_image` was built without PNG support.** Two of the 39 `pics/` files are `.png` (`annunon.png`/`annunoff.png`); both failed to load even after fixing Bug 2. The original Phase B `vcpkg install sdl3-image:arm64-android` never requested the optional `png` vcpkg feature (`SDLIMAGE_PNG FALSE` in the installed CMake config) - `libpng.a` existed already (pulled in transitively by something else) but was never linked into `sdl3-image`. Fixed via `vcpkg install sdl3-image[png]:arm64-android --recurse` (53s rebuild), then a normal Gradle build relinked `libmain.so` against it. This matters beyond these 2 files - bezels (`-bezeldir`) load through the same `load_one_png()` path, so PNG support is a real, ongoing requirement. **This vcpkg feature selection is not captured anywhere except this doc - if `vcpkg/installed` or the binary cache is ever wiped, re-run the `[png]` install before rebuilding.**

**Bug 4 - `-homedir`/`-datadir` must be the *parent* of the picked/scanned game folder, not the folder itself.** Every Singe/LaserForge game's own `.singe` script follows a standard convention (confirmed by reading a real unzipped fan-made game's script): `BASEDIR = "singe"`, `MYDIR = BASEDIR.."/"..gamename`, `dofile(BASEDIR.."/Framework/globals.singe")` - meaning the script itself expects a `singe/` subfolder under the *real* homedir, matching hypseus's own upstream doc example exactly. `LaunchArgs.kt` passes `File(homeDir).parent` (not `homeDir`) as `-homedir`/`-datadir` for this reason. Note: this fix is real and necessary, but on its own did **not** resolve the crash described below - `luaL_openlibs()` runs before the game script is ever read, so a failed `dofile()` downstream couldn't have caused it. Recorded here for correctness, not as the fix for Bug 5.

**Bug 5 (the big one, later fully resolved in #35) - a `_FORTIFY_SOURCE` false positive on Lua's classic "struct hack" string allocation, misdiagnosed at first as real corruption.** `FORTIFY: strchr: prevented read past end of buffer`, inside `traversetable()` (`lgc.c:167`, `strchr(svalue(mode), 'k')` on a table's `__mode` weak-table metafield) - confirmed via exact function-symbol boundaries from `llvm-nm -S` against the unstripped build artifact, not just DWARF line-table guessing, which was misleading for several inner frames. Reached via `luaC_step → singlestep → propagatemark → traversetable`, triggered mid-allocation during `lua_pushcclosure()` inside library/function registration. `-fno-strict-aliasing` was tried on the whole `singeproxy` CMake target (a well-known real Lua 5.1 build requirement, kept regardless) - didn't fix it.

**Initial workaround (superseded, see the real fix below):** holding Lua's GC off across the whole startup/script-load window (`lua_gc(LUA_GCSTOP)`/`LUA_GCRESTART)` in `sep_startup()`) got a real game booting, but the same crash reproduced later during real gameplay once GC resumed (confirmed live: it did, when opening the in-game service menu) - proof the workaround only masked the startup case, not the actual bug.

**Real root cause, found by direct memory inspection, not guessing:** added temporary `__android_log_print` diagnostics right at the `strchr()` call site in `traversetable()`, dumping the actual live string bytes at crash time. Result: the string was **completely valid** - `"kv\0"`, correctly null-terminated exactly where its own declared length said it would be. Since `strchr()` searching for `'k'` would find it at byte 0 immediately, it should never need to scan anywhere near a buffer boundary - yet FORTIFY still aborted. This is the signature of a `_FORTIFY_SOURCE` **false positive**, not real corruption: Lua's `TString` (`lstring.h`) uses the classic pre-C99 "struct hack" - one combined `malloc(sizeof(TString) + len + 1)`, then `getstr(ts) = (char*)(ts + 1)` to point past the struct header at the string data. The compiler's `__builtin_object_size()` (what `_FORTIFY_SOURCE` is built on) can't see through that pointer arithmetic to know the real allocation is bigger than `sizeof(TString)` alone, so it under-reports the buffer's remaining size and Bionic's fortified `strchr` aborts on reads that are actually completely safe. This is a known category of false positive for any C codebase using this allocation pattern (not Lua-specific), and as far as could be determined this is the first-ever arm64 Android build of hypseus-singe, so nobody had hit it before.

**The actual fix:** `target_compile_options( singeproxy PRIVATE -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0 )` on the `singeproxy` CMake target (`hypseus-singe/src/game/singe/CMakeLists.txt`). This is a real fix, not a workaround - confirmed by running with GC completely unrestricted (no stop/restart at all, full stress test) and watching `traversetable()` get hit repeatedly during normal gameplay with zero crashes, each time showing the same valid data. **The GC-stop/restart workaround in `singeproxy.cpp` was removed entirely once this landed** - it's no longer needed.

**Result: a real Singe game boots and plays on the physical Retroid Pocket 5, fully stable, not just past the old blocker.** Verified via hypseus's own log: clean video init, `SDL_gamepad_init` detecting "Retroid Pocket Controller", `.zip` ROM loading, config files copied out of the zip, correct viewport scaling, real full-motion video rendering with the `pics/`-based overlay ("FREE PLAY" text) drawn correctly on screen. Input verified end-to-end: a simulated `adb shell input keyevent` correctly produced `SINGE: "User requested a quit."` in hypseus's own log, and the owner directly confirmed on the physical controller that directions and START work, reaching real gameplay past the start screen. `hypinput_gamepad.ini` behavior confirmed via source read (`input.cpp`'s `defaultConfig()` only writes if the file doesn't already exist - manual edits are permanent) and used to bind the left-stick click to the in-game service/options menu - **the owner directly confirmed the service menu (the exact scenario that used to crash) now opens with no crash**, with the real FORTIFY fix in place and the GC-stop workaround fully removed.

**Bonus verification, unplanned:** while testing this fix, the owner's overnight Daphne game transfer was already on the device. The scanner correctly detected it as `DAPHNE_NATIVE` - verifying #28's previously-untested Daphne-native detection path against real data for the first time. It doesn't fully launch yet (a required ROM file inside its zip couldn't be found - an incomplete transfer, not an app bug), but the detection and launch-arg construction both worked correctly up to that point.

## Phase D - per-category `-homedir` fix for Daphne-native games (#38)

With a complete ROM transfer in place, the Daphne-native game still failed to launch - hypseus reported the ROM couldn't be found even though the zip was present at the expected `<picked-folder>/roms/<name>.zip`.

**Root cause:** Bug 4's "`-homedir` is the parent of the picked folder" rule (added for Singe games' `.singe`-script convention) was being applied unconditionally to *every* game category, including `DAPHNE_NATIVE`. But hypseus's own ROM lookup (`game.cpp`'s `load_roms()` -> `homedir::get_romfile()`) builds `<homedir>/roms/<name>.zip` - and #28's scanner already scans for exactly that path relative to the *picked* folder, not its parent. Applying the Singe rule here made hypseus look one level too high (the picked folder's parent's own `roms/` - a real, unrelated folder on the shared SD card), and `find_romfile()`'s fallback path (`homedir.cpp`) silently reported the ROM missing instead of crashing, which is why it looked like a data problem rather than an args problem at first.

**Fix:** `buildLaunchArgs()` in `LaunchArgs.kt` now branches `-homedir`/`-datadir` by category - the picked folder's parent for `SINGE_ZIPPED`/`SINGE_SCRIPT` (unchanged from Bug 4), the picked folder itself for `DAPHNE_NATIVE`.

**Result, verified on the physical Retroid Pocket 5:** the Daphne-native game's ROM loads, video parsing completes, and the game reaches its real attract-mode/scoreboard screen with full-motion video and the overlay scoreboard rendering correctly - confirmed via screenshot, not just log inspection.

## Phase D - persist picked game folder + dashboard redesign (#36)

Before this, the picked game folder lived only in Compose's in-memory `remember { mutableStateOf(...) }` state in `MainActivity` - any process restart (including the known #35 crash, before it was fixed) reset the dashboard to empty, forcing the SAF folder picker to be re-run from scratch every time.

**Persistence:** the picked folder's SAF tree `Uri` is now saved to a plain `SharedPreferences` entry (`hypdroid_prefs` / `game_folder_uri`) whenever a folder is picked. On every launch, a `LaunchedEffect` reads it back and re-runs the same `resolveRealPath()` + `scanGames()` pipeline the picker itself uses - no separate persistence-specific code path, no re-pick needed. The underlying SAF grant (`takePersistableUriPermission()`, from #27) already survives restarts on its own; this just re-applies it automatically instead of requiring the user to manually re-pick the same folder.

**Graceful fallback if the grant no longer holds:** `contentResolver.persistedUriPermissions` is checked as the actual source of truth before trusting the saved URI - it's possible for a `SharedPreferences` entry to outlive its real SAF grant (permission revoked in Android's own Settings, SD card removed/swapped). If the check fails, or if `resolveRealPath()` can't produce a real path for it, the saved URI is cleared and the app falls back to the ordinary empty "+" state - no crash, no stale/broken path shown.

**Dashboard redesign, per the owner's spec:** the raw folder-path text and "Change Game Folder" button are gone from `HomeScreen` entirely. In their place, a `Row` of two `IconButton`s sits in the top-right corner: a "+" (`Icons.Filled.Add`) to pick/change the game folder, and a gear (`Icons.Filled.Settings`) as a placeholder entry point for #30's future Settings screen (currently a no-op stub - #30's actual scope is unaffected). The game list itself stays on the main dashboard, unchanged in behavior.

**Result, verified on the physical Retroid Pocket 5:** picked the game folder once (all 3 previously-detected games listed, no path text visible anywhere, `+`/gear icons present top-right), then `am force-stop` + relaunched the app - the same 3 games reappeared immediately with no re-pick and no crash in `adb logcat`.

## Phase D - Settings screen: Game/Media folder pickers + Controller Configuration entry point (#30)

Adds a real Settings screen behind the gear icon that #36 stubbed out. Scope narrowed significantly during planning (captured in full in the private `CLAUDEMEMORY.md`, summarized here): the bezel folder/toggle originally planned for this screen moved entirely to #31 (per-game options), since a bezel image needs per-image `-scalefactor`/`-shiftx`/`-shifty` tuning to actually be visible - not something that makes sense as one global setting. What's left is deliberately small.

**Navigation.** No navigation library - this app is small enough that a plain `Screen` enum (`HOME`/`SETTINGS`) toggled in `HypdroidApp`'s Compose state is enough. Since that's not a real back-stack, `SettingsScreen` adds its own back arrow (top bar) and a `BackHandler` to intercept Android's system back button while on Settings - without it, the system back button would exit the app entirely rather than returning to the dashboard (confirmed live: it now correctly returns to Home instead).

**Game folder row.** Reuses the exact same SAF picker launcher instance as the dashboard's "+" (`onChooseGameFolder`, shared, not a duplicated copy) - picking from either place produces the identical result, since both call the same underlying `applyGameFolder()` logic and update the same persisted state from #36.

**Media folder row.** New, using the same SAF pattern and a new `SharedPreferences` key (`media_folder_uri`), following the exact same persistence/restore/graceful-fallback pattern #36 established for the game folder - just without the game-specific side effects (no asset copy, no scan). Nothing consumes this folder yet; it's prep for Phase E's gallery art and #31's per-game bezel lookup.

**Controller Configuration row.** Present in the layout, labeled "Coming soon," and its button is currently a no-op - matches the same stub precedent #36 set for the gear icon itself. Real functionality is #41 (a separate issue, since it's substantial enough on its own: parsing/editing/saving `hypinput_gamepad.ini`, which doesn't even exist until a game has been launched once).

**Result, verified on the physical Retroid Pocket 5:** opened Settings from the gear icon - Game folder correctly showed the real persisted path from #36, Media folder correctly showed "Not set." Picked a real folder for Media folder via the SAF picker; it resolved and displayed correctly. Verified the back arrow returns to the dashboard (game list intact), and separately that the system back button does the same rather than exiting the app. Force-stopped and relaunched the app, reopened Settings - both Game folder and Media folder paths were still present, confirming both persist correctly together.

## Phase F - Controller Configuration: live gamepad button mapping (#41)

Wires up the "Controller Configuration" row from #30 with a real screen for viewing and editing `hypinput_gamepad.ini`'s gamepad bindings, scoped through a real planning discussion (full reasoning in the private working notes).

**Two independently-tracked ini files, confirmed by pulling both off the real device.** Because Singe and Daphne-native games use different `-datadir` values (#38), hypseus generates/reads `hypinput_gamepad.ini` relative to whichever was active - `roms/hypinput_gamepad.ini` for Singe games, `roms/singe/hypinput_gamepad.ini` for Daphne-native. Diffing the two real files found they'd already drifted (a manual `KEY_SERVICE` edit from earlier testing only made it into the Singe one). Per the owner's call, every capture now writes to **both** files (`applyBindingToBothFiles()`, `GamepadIni.kt`) - one consistent physical layout regardless of which game category is running - while any *pre-existing* drift on rows the screen hasn't touched yet is deliberately left alone rather than mass-reconciled, so saves stay minimal and targeted.

**Real file format, confirmed from `doc/hypinput_gamepad.ini` (the authoritative template) and `input.cpp`'s actual tokenizer** (`find_word()`-based, case-sensitive `strcmp` for button/axis token values despite case-insensitive `strcasecmp` for the `KEY_*` names themselves - confirmed by reading `keycodes.cpp`'s `sdl3_controller_button()`/`sdl3_controller_axis()`). 22 `KEY_*` lines, `KEY_X = Key1 Key2 Pad0Button AxisPad0 Pad1Button AxisPad1`, with trailing tokens legitimately optional (a real line in the shipped template has only 5 of 6 tokens). `GamepadIni.kt`'s parser/writer mirrors this: reads/writes only the `Pad0Button`/`AxisPad0` columns, preserves every other line and column untouched, and pads any genuinely-missing trailing tokens to an explicit `"0"` on lines it edits - semantically identical to omitting them, confirmed by reading `input.cpp`'s optional `find_word()` calls defaulting those to 0.

**Column semantics, not what a naive read of the names would suggest.** The two analog triggers (`AXIS_TRIGGER_LEFT`/`RIGHT`) are parsed by `sdl3_controller_button()`, not `sdl3_controller_axis()` - they belong in the `Pad0Button` column despite the "AXIS_" name. Only the 8 stick-direction macros (`AXIS_LEFT_*`/`AXIS_RIGHT_*`) go through `sdl3_controller_axis()` into the `AxisPad0` column, and only the 4 directional rows (`KEY_UP`/`DOWN`/`LEFT`/`RIGHT`) meaningfully use it at all (a left-stick push as a redundant alternative to the d-pad button) - the UI only shows an Axis slot for those 4 rows, not all 22.

**Live capture, not text/dropdown entry.** `ControllerConfigScreen.kt` runs as plain Compose inside `MainActivity`, which never runs SDL at all (SDL only exists inside `HypseusActivity` during actual gameplay) - so capture goes through Android's own `KeyEvent`/`MotionEvent` APIs directly, with no dependency on hypseus's own input code. `MainActivity` exposes `gamepadCaptureListener`/`gamepadCaptureListeningForAxis`, set only while a slot is actively being listened to, and forwards matching real input via overridden `dispatchKeyEvent()`/`dispatchGenericMotionEvent()`. Most tokens are discrete `KeyEvent`s; the analog triggers and stick-as-dpad macros arrive as continuous `MotionEvent` axis values needing a threshold check (`AXIS_THRESHOLD = 0.5f`) instead of simple press detection - both paths implemented and confirmed working on the real controller (button capture and trigger capture both verified).

**Conflict warning, not silent duplicate binding.** Reassigning an input already used elsewhere shows which action currently owns it before committing - confirmed still allowed either way, matching the acceptance criteria ("allowed if confirmed, just not silent").

**Two real bugs found and fixed while testing this feature, both scoped beyond just #41:**

- **Touch scrolling didn't work anywhere in the Compose UI - only d-pad/focus-based scrolling did.** Root cause: `AndroidManifest.xml`'s `android:hardwareAccelerated="false"` was set at the `<application>` level, disabling hardware acceleration for the *entire app* - including `MainActivity`'s pure-Compose UI, which has nothing to do with the SDL/GL crash that flag was actually added for (see Phase C notes). Fixed by moving the flag onto `HypseusActivity`'s own `<activity>` element specifically, leaving the rest of the app (and Compose's gesture handling) hardware-accelerated as normal. Confirmed fixed: the owner tested real finger-swipe scrolling on-device after the fix and it worked correctly, both here and generally.
- **`LazyColumn` had no size modifier**, leaving its scroll gestures unreliably bound. Fixed with `Modifier.weight(1f)` inside the containing `Column`.

**Result, verified on the physical Retroid Pocket 5, by the owner directly:** all 22 `KEY_*` rows displayed with real parsed values (including `AXIS_TRIGGER_RIGHT` correctly showing in `KEY_BUTTON3`'s Button slot, confirming the trigger-in-button-column semantics). Captured a real binding via live input (assigned `KEY_QUIT` to `BUTTON_RIGHTSTICK`) - confirmed written identically to both ini files via `adb pull`+`diff`, with the pre-existing `KEY_SERVICE` drift on the *other*, untouched row left exactly as it was, matching the "only touched rows get rewritten" design. Force-closed and reopened the app; the new binding persisted. Started a real game with the new binding active and confirmed exiting correctly returns to the dashboard rather than closing the app outright.

## Phase E - carousel dashboard with box art, v1 (#44)

Replaces `HomeScreen`'s plain text list (from #28/#29) with a real visual carousel - Phase E started ahead of Phase D's remaining item (#31) after the owner pulled up **Eden** (a separate, already-installed emulator frontend on the same device, unrelated to hypseus - used purely as a visual reference) and asked for that same look: one game centered and highlighted, neighbors' art peeking in at the edges, swipe to page. Scoped deliberately basic for the first real APK rollout - no search box, no per-game media-type override yet (that's #31's future "Cover Art" field).

**`GameCarousel` uses `HorizontalPager`** (`androidx.compose.foundation.pager`, still `@ExperimentalFoundationApi` in this project's Compose BOM version, opted into explicitly) with a **fixed page width** (`PageSize.Fixed(420.dp)`) rather than the default full-width page - the default left the narrow portrait card pinned to one edge of a nearly-screen-wide page with a large empty gap before the next page, not the tight centered carousel the reference showed. `contentPadding` is computed from `LocalConfiguration`'s screen width so the very first/last cards still center correctly. Non-centered cards are scaled down (`lerp(0.82f, 1f, ...)` based on each page's offset from center) for the same "receding neighbors" depth effect Eden has.

**Box art only for this pass**, loaded via **Coil** (`io.coil-kt:coil-compose`, a new dependency - nothing before this loaded images from arbitrary file paths). Default representation is `box`, not the earlier-discussed `logo` default - revised once the owner had real content ready (box art for all 3 test games, chosen because it's simply bigger/reads better in a carousel card than a logo would). `boxArtFile()` looks up `<media folder>/box/<gamename>.png`, matching the subfolder convention from #30's planning; returns null (graceful fallback to a plain text card, not an error) if the media folder isn't set or that specific file doesn't exist.

**A real image-cropping bug found and fixed via a side-by-side comparison with the owner:** initially used `ContentScale.Crop`, which crops to fill the card's fixed aspect ratio - but the real box art PNGs are rendered with a transparent shadow/margin around the actual box, at a different aspect ratio than the card, so Crop was cutting into the top and bottom of the image (including part of the box itself, confirmed by comparing the in-app render directly against the source file). Fixed by switching to `ContentScale.Fit`, showing the whole image at all times.

**A real, deliberately-scoped-out gap fixed the same session it was noticed:** #44's issue explicitly deferred gamepad d-pad/button navigation of the carousel to "Phase F's broader gamepad-navigation pass if needed," touch-only for this first pass. Once built, the owner immediately tried the physical d-pad and it didn't work - unlike `LazyColumn` (Controller Configuration screen), which gets up/down d-pad scrolling for free from Compose's built-in focus-traversal system, `HorizontalPager` doesn't automatically respond to left/right d-pad presses as page changes. Given this is gamepad-first hardware, fixed immediately rather than deferred further: `GameCarousel` requests focus on first composition (`FocusRequester`/`LaunchedEffect`) and handles `Key.DirectionLeft`/`Key.DirectionRight` via `Modifier.onKeyEvent`, calling `pagerState.animateScrollToPage()` through a `rememberCoroutineScope()`. Confirmed working both via a simulated `adb shell input keyevent KEYCODE_DPAD_RIGHT` and by the owner directly on the physical controller.

**Result, verified on the physical Retroid Pocket 5 with real box art for all 3 existing test games:** carousel renders with real, uncropped box art, centered card highlighted at full scale with neighbors visibly receding on both sides. Swiping (touch) and the physical d-pad both page between games correctly. Tapping the centered card launches it via the same `buildLaunchArgs()`/`HypseusActivity` flow already in place.

## App identity: real name + launcher icon

`strings.xml`'s `app_name` was still the SDL Android project template's placeholder value (`"Game"`) - changed to `"Hypdroid"`. The launcher icon (`mipmap-{m,h,xh,xxh,xxx}dpi/ic_launcher.png`, plain flat PNGs, no adaptive-icon layers) was the stock SDL template icon - replaced at all 5 densities (48/72/96/144/192px) with the real app icon, resized from a 1024x1024 source (`asset/Hypdroid_Android_Icon_Laserdisc.png` - a laserdisc/play-button mark, matching the `HYPDROID` wordmark's own "O" design). The full wordmark logo (`asset/Hypdroid_Logo_YXBA.png`) is also kept in the repo for future branding use (e.g. an about screen or store listing) - not currently wired into the app itself, since a wide wordmark doesn't work as a small launcher icon. Verified on the physical Retroid Pocket 5: app shows as "Hypdroid" with the new icon in the app drawer.

## Phase D - per-game custom options: Cover Art, Bezel, Arguments (#31)

Adds a per-game options screen, opened by pressing down on the d-pad (or long-pressing, for touch) on the focused carousel card - the gamepad-first equivalent of a touch long-press, both wired to the same handler. Originally scoped around a large set of curated per-flag toggles (fastboot, cheat, aspect ratio, scale/shift, stretch, scanlines, PAL variants); dropped after discussion, since the free-text Arguments field already covers all of them generically and nobody was expected to reach for most of them via dedicated UI. Final scope is the three things that genuinely need real controls: **Cover Art**, **Bezel**, and **Arguments**.

**`GameOptions.kt`** (new, pure Kotlin - no Compose deps) holds the data model and `SharedPreferences`-backed persistence, keyed per game name, entirely separate from the app-wide `hypdroid_prefs` used for folder paths. `Screen` was refactored from a plain enum to a sealed class (`Screen.GameOptionsFor(val gameName: String)`) so the options screen can carry which specific game it's scoped to - the first screen in this app that needs per-instance state beyond a fixed set of destinations.

**Cover Art** - a 4-choice picker (CD/Logo/Box/Text), persisted per game, resolved via `resolveCoverArtFile()`: an explicit per-game override if set, falling back to the app default (`box`, from #44) if unset. Choosing "Text" is a first-class choice (always show the plain title), not just an automatic no-media fallback. Changes apply immediately to the carousel - confirmed live: picking "Logo" for a game updated its carousel card to show the real logo art without needing a restart, since `gameOptionsMap` is held in Compose state and reloaded/refreshed whenever the game list changes.

**Bezel** - a plain on/off `Switch`. When on, `bezelLaunchArgs()` looks for `<media folder>/bezel/<gamename>.png`; if found, both `-bezeldir` and `-bezel` are appended to that game's launch args (matching #31's earlier planning: `-bezeldir` must always accompany `-bezel`, since hypseus's own relative default can never resolve correctly on this Android port - see `video.cpp:130`). If off, or no matching file exists, both flags are skipped silently.

**Arguments** - a text field + "Add" button; entries appear in a list below with an individual "X" to remove each one. All active entries are split on whitespace and appended as individual `argv` tokens to that game's launch command at Play time (a single entry like `-latency 200` correctly becomes two separate array elements, not one malformed token).

**A real regression found live on-device, deliberately deferred rather than fixed immediately (owner's call):** the dashboard's "+"/gear icons stopped responding to touch after #44's carousel landed. Root cause identified but not yet fixed: `HomeScreen`'s icon `Row` is declared *before* the full-screen `GameCarousel`/`HorizontalPager` in the same `Box`, so the carousel (which claims pointer input across its entire `fillMaxSize()` bounds, including the corner where the icons visually sit) draws on top and intercepts those taps. The fix (reordering so the icon `Row` is declared last, giving it input priority) is known and written but was explicitly held back per the owner's request to finish #31 first - **tracked as a real, open bug, not forgotten.**

**Two real Compose UX pitfalls hit and fixed while testing on-device:**

- The system back button, while the on-screen keyboard was showing in the Arguments text field, dismissed the *entire options screen* (via `BackHandler`) rather than just the keyboard - losing an in-progress (not-yet-added) argument. Not a code bug to fix (an unconfirmed draft correctly not being saved is right), but a real workflow gotcha: dismiss the keyboard via its own "Done" key, not the system back button, when using this screen with a physical/on-screen keyboard.
- The keyboard covering the "Add" button after typing meant a screen-coordinate tap at the button's normal position actually hit the keyboard instead - same underlying cause as the above, resolved the same way (dismiss via "Done" first).

**Result, verified on the physical Retroid Pocket 5, testing all three fields against a real fan-made Singe game:** pressing down on the d-pad opened its options screen correctly. Changed Cover Art to Logo - the carousel immediately showed the real logo art for that game. Toggled Bezel on and off, confirmed correct on/off state displayed and persisted (left off for this game, which is already fullscreen and doesn't need bezel art - the owner noted a Daphne-native game is the one that will actually use it, not yet tested there). Added `-scanlines` as a real argument, confirmed it appeared in the list, removed it via its "X" to verify deletion works, then re-added it as a real, kept setting. Later verified the actual merge into a real launch: `hypseus.log`'s own "Command line" entry for a real on-device launch showed `-scanlines -scanline_shunt 4` present alongside the baked-in `-fullscreen -gamepad` defaults, confirming the merge mechanism genuinely works end-to-end, not just at the persistence layer.

## Fix: dashboard "+"/gear icons not responding to touch

The regression noted (but deliberately deferred) during #31's work - fixed as its own follow-up. Root cause: `HomeScreen`'s icon `Row` was declared *before* the full-screen `GameCarousel` in the same `Box`. Compose draws/hit-tests later-declared `Box` children on top of earlier ones, and `GameCarousel`'s `HorizontalPager` claims pointer input across its entire `fillMaxSize()` bounds - including the corner where the icons visually sit, even though no card is ever actually there. The carousel was intercepting taps in that region before they could reach the `IconButton`s underneath.

**Fix:** reordered `HomeScreen` so the icon `Row` is declared last, after the carousel `Box` - later declaration order gives it input priority in the overlapping region, with no visual change (the icons render in the same place either way, `Alignment.TopEnd` is unaffected by declaration order).

**Result, verified on the physical Retroid Pocket 5:** both the "+" icon (opens the SAF game-folder picker) and the gear icon (opens Settings) respond to touch correctly again.

## Dashboard wordmark logo

Added the `HYPDROID` wordmark (`asset/Hypdroid_Logo_YXBA.png`, already in the repo from the earlier icon/name work) to the dashboard's upper-left corner, balancing the "+"/gear icons on the right. Copied into `android/app/src/main/res/drawable/hypdroid_logo.png` (Android resource names must be lowercase/alphanumeric - the source filename doesn't qualify as-is) and rendered via a plain `Image`/`painterResource`, sized to `40.dp` height with its aspect ratio preserved. Declared after the carousel `Box` in `HomeScreen`, matching the same declaration-order convention the icon `Row` fix just established - not required for a non-interactive image, but keeps the pattern consistent. Verified on the physical Retroid Pocket 5.

## Fix: carousel resets to first game after backing out of per-game Options (#52)

`GameCarousel`'s `pagerState` was `remember`ed inside `GameCarousel` itself. Since `HomeScreen`/`GameCarousel` only exist in composition while `currentScreen == Screen.Home`, navigating to `Screen.GameOptionsFor` (#31) tore that whole subtree down - and recomposing back into `Screen.Home` created a brand new `pagerState` starting at its default page (0), discarding whatever game had been focused.

**Fix:** hoisted the current page to a `carouselPage` state in `HypdroidApp`, which survives screen navigation (it's never torn down, unlike `HomeScreen`). Passed down to `GameCarousel` as `initialPage`; a `LaunchedEffect` collecting `snapshotFlow { pagerState.currentPage }` syncs it back up to `HypdroidApp` on every page change (swipe, d-pad, or otherwise), so the next time `Screen.Home` recomposes, the new `pagerState` starts at the restored page instead of 0.

**Result, verified on the physical Retroid Pocket 5:** focused a non-first game in the carousel, pressed down to open its Options screen, backed out - the carousel was still showing that same game, not reset to the first one.
