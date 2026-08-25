# Cutting a release

How to build, verify, and publish a Hypdroid release APK. Written after [#129](https://github.com/rhakka303/Hypdroid/issues/129), where the v2.0 release APKs were accidentally built via the `debug` build type and shipped with the wrong package name (`org.libsdl.app.debug` instead of `org.libsdl.app`) - installing them would have created a second, separate app instead of updating the existing install. Follow this doc instead of relying on memory of "how we did it last time."

## The rule

**Always build releases with the `release` build type. Never `debug`.**

`debug` carries `applicationIdSuffix ".debug"` (see `app/build.gradle`), specifically so test installs don't overwrite the live app. Building a "release" via `debug` silently produces an app with the wrong package name that won't update anyone's existing install.

`release` has its own signing config (added in #129, reusing the same debug keystore every build has ever been signed with - there's no separate release keystore). This means `assembleRelease` alone now produces a correctly-named, already-signed, installable APK. No manual `apksigner` step, no ambiguity.

## Build

```powershell
cd android
./gradlew assembleHandheldRelease assembleFullRelease
```

Output:

```text
app/build/outputs/apk/handheld/release/app-handheld-release.apk
app/build/outputs/apk/full/release/app-full-release.apk
```

If either file has an `-unsigned` suffix, the signing config isn't being applied - stop and fix that before going any further.

## Verify before installing anywhere

Check the package name actually matches what's expected (`org.libsdl.app` for Handheld, `org.libsdl.app.full` for Touch) - this is the exact check that would have caught #129 immediately:

```powershell
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt.exe" dump badging app\build\outputs\apk\handheld\release\app-handheld-release.apk | Select-Object -First 1
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt.exe" dump badging app\build\outputs\apk\full\release\app-full-release.apk | Select-Object -First 1
```

Check the signature is valid:

```powershell
& "$env:ANDROID_HOME\build-tools\36.1.0\apksigner.bat" verify app\build\outputs\apk\handheld\release\app-handheld-release.apk
& "$env:ANDROID_HOME\build-tools\36.1.0\apksigner.bat" verify app\build\outputs\apk\full\release\app-full-release.apk
```

## Test on real devices before publishing

Install over the existing live app on both real test devices and confirm it updates in place (not a fresh separate install):

```powershell
adb -s <handheld-device-serial> install -r app\build\outputs\apk\handheld\release\app-handheld-release.apk
adb -s <handheld-device-serial> shell dumpsys package org.libsdl.app | Select-String "versionCode|versionName|lastUpdateTime"

adb -s <touch-device-serial> install -r app\build\outputs\apk\full\release\app-full-release.apk
adb -s <touch-device-serial> shell dumpsys package org.libsdl.app.full | Select-String "versionCode|versionName|lastUpdateTime"
```

`lastUpdateTime` should move forward to just now. If it doesn't move, the install silently went somewhere else (wrong package name) - don't publish, go back and check.

Currently tested on: Retroid Pocket 5 (Handheld flavor), Samsung Galaxy Tab S7+ (Touch flavor).

## Publish

Tag and create the GitHub Release (see prior releases for tag/notes format), then attach both verified APKs:

```powershell
gh release upload <tag> app\build\outputs\apk\handheld\release\app-handheld-release.apk#Hypdroid-Handheld-<tag>-arm64-v8a.apk app\build\outputs\apk\full\release\app-full-release.apk#Hypdroid-Touch-<tag>-arm64-v8a.apk
```

After uploading, download the live asset back and re-check its package name - this is the step that was skipped for v2.0's first publish attempt:

```powershell
gh release download <tag> -p "Hypdroid-Handheld-<tag>-arm64-v8a.apk" -D verify-dl --clobber
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt.exe" dump badging verify-dl\Hypdroid-Handheld-<tag>-arm64-v8a.apk | Select-Object -First 1
```
