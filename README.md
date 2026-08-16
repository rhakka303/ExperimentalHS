# Hypdroid

A standalone Android port of [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe), the laserdisc arcade emulator for fan-made Singe/Lua games, built directly from upstream source with a native gamepad-first game launcher UI.

**Target hardware:** Android gaming handhelds (Retroid Pocket 5) and Android-based SBCs — physical gamepad input is the primary control path, not a touch-overlay afterthought.

**Status:** early development. No APK releases yet.

## What this is

- A from-scratch native build of hypseus-singe for `arm64-v8a` Android, using SDL3's official Android support.
- A visual game gallery/launcher (box art, logo overlay, later gameplay previews) instead of a bare file picker — points at a folder you already have populated with your own game/media files rather than bundling or scraping anything.
- Per-game custom launch options via long-press, on top of hypseus's existing `.ini`-driven input config.

This repo does **not** contain, bundle, or distribute any ROMs, laserdisc video dumps, or artwork. You provide your own game files; the app points at wherever you keep them.

## Status / roadmap

See the phased plan this project follows — environment bring-up, native dependency cross-compilation, wiring hypseus into the SDL3 Android shell, the GUI launcher, the visual gallery, and gamepad input mapping.

## License

GPL-3.0, matching upstream [hypseus-singe](https://github.com/DirtBagXon/hypseus-singe), since this project builds directly against and incorporates that GPL-licensed source.

## Credits

Built on [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe) by DirtBagXon, itself a fork of [Hypseus](https://github.com/h0tw1r3/hypseus) by Jeffrey Clark and [Daphne](http://www.daphne-emu.com) by Matt Ownby, with Singe LUA support originally by Scott Duensing.
