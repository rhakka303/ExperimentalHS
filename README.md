# Hypdroid

A standalone Android port of [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe), the laserdisc arcade emulator for fan-made Singe/Lua games, built directly from upstream source with a native game launcher UI.

**Two flavors, two targets:**
- **Hypdroid Handheld** — gamepad-first, for Android gaming handhelds and SBCs. Storage access via Storage Access Framework only, no broad file permissions.
- **Hypdroid Touch** — for stock/OEM Android tablets and phones, with an on-screen touch control overlay alongside gamepad support.

**Status:** actively in development, running real games on real hardware. No public APK releases yet.

## What this is

- A from-scratch native build of hypseus-singe for `arm64-v8a` Android, using SDL3's official Android support.
- A visual game gallery/launcher (box art, logo overlay, background art) instead of a bare file picker — points at a folder you already have populated with your own game/media files rather than bundling or scraping anything.
- Per-game custom launch options via long-press, on top of hypseus's existing `.ini`-driven input config.
- Touch controls (Touch flavor) and physical gamepad support (both flavors), sharing the same underlying input-binding system.

This repo does **not** contain, bundle, or distribute any ROMs, laserdisc video dumps, or artwork. You provide your own game files; the app points at wherever you keep them.

## Status / roadmap

Core emulation, game scanning/launching, the visual gallery, Settings, and controls (gamepad + touch) are all working and tested on real hardware across both flavors. Ongoing work is polish, UX gaps, and further real-device testing.

## License

GPL-3.0, matching upstream [hypseus-singe](https://github.com/DirtBagXon/hypseus-singe), since this project builds directly against and incorporates that GPL-licensed source.

## Credits

Built on [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe) by DirtBagXon, itself a fork of [Hypseus](https://github.com/h0tw1r3/hypseus) by Jeffrey Clark and [Daphne](http://www.daphne-emu.com) by Matt Ownby, with Singe LUA support originally by Scott Duensing.

Touch controls (Hypdroid Touch flavor) built with [RadialGamePad](https://github.com/Swordfish90/RadialGamePad) by Swordfish90.
