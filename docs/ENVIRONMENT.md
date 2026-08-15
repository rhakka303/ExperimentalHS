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
- vcpkg's `arm64-android` triplet setup (for cross-compiling SDL3 and the other native dependencies) is tracked separately — see the project's issue tracker.
