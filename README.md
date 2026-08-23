# Hypdroid

A standalone Android port of [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe), the laserdisc arcade emulator for fan-made Singe/Lua games, paired with an original, from-scratch native Android launcher: a visual game gallery, built-in gamepad navigation, and full touch controls.

**Two flavors, two targets:**

- **Hypdroid Handheld**: for Android gaming handhelds with built-in physical controls (D-pad, buttons, sticks). Storage access via Storage Access Framework only, no broad file permissions requested.
- **Hypdroid Touch**: for stock/OEM Android tablets and phones without built-in game controls. Adds an on-screen touch control overlay alongside gamepad support, and requests All Files Access, needed to reliably read game files from external SD cards on some devices (see Troubleshooting).

**Status:** actively in development, running real games on real hardware. No public APK releases yet.

## What this is

- A from-scratch native build of hypseus-singe for `arm64-v8a` Android, using SDL3's official Android support.
- A visual game gallery/launcher (box art, logo overlay, background art) instead of a bare file picker, pointing at a folder you already have populated with your own game/media files rather than bundling or scraping anything.
- Per-game custom launch options via long-press, on top of hypseus's existing `.ini`-driven input config.
- Touch controls (Touch flavor) and physical gamepad support (both flavors), sharing the same underlying input-binding system.

This repo does **not** contain, bundle, or distribute any ROMs, laserdisc video dumps, or artwork. You provide your own game files; the app points at wherever you keep them.

This software is intended for educational purposes only.

## Folder Structure

Game folder (recommended folder name: hypseus)
Media folder (recommended folder name: media)

**Folders you create:**

```text
hypseus/
├── roms/     ← Daphne-native ROM(s)
├── vldp/     ← Daphne-native framefile folder(s)
└── singe/    ← fan-made games

media/
├── box/      ← 2D or 3D box art
├── cd/       ← CD/laserdisc art
├── logo/     ← Game logo art
└── bg/       ← background art, must match your device's own resolution
```

**Folders Hypseus creates at first launch:**

```text
hypseus/
├── bezels/     ← your game bezels here
├── fonts/
├── logs/       ← hypseus.log (game logs)
├── midi/
├── ram/
└── screenshots/
```

### Media Instructions

This artwork is what represents each game in the carousel. `box`, `cd`, and `logo` are three different ways to represent the same game: pick whichever one you have art for, per-game or globally in Settings. `bg` is a separate, optional full-screen background shown behind the carousel itself.

- Filename must exactly match the game's identifier, the same name used for its `roms/` or `singe/` folder.
  - For example: `box/dragonslair.png`, `cd/dragonslair.png`, `logo/dragonslair.png`, `bg/dragonslair.png`.
- PNG format only, for every art type.
- Background art (`bg/`) must be sized to match your device's own screen resolution, since it's shown full-bleed, not scaled or cropped to fit.

See the Folder Structure section above for how to set up the `media/` folder itself.

### Troubleshooting

**Games don't show on the dashboard?**

If you picked a Game folder on external/removable SD card storage and no games appear, this is usually an All Files Access limitation, Handheld's Storage Access Framework permission doesn't always cover raw file reads on external SD cards depending on your device and Android build. If this happens, install Hypdroid Touch instead; it requests All Files Access and can read SD card folders that Handheld cannot.

Also double-check your device's own Settings: go to Settings > Apps > All files access, find Hypdroid Touch, and make sure the flag is turned to Allow. The in-app onboarding screen's "Access granted" checkmark isn't always reliable, some devices show it as granted in-app while the real system setting is still blocked, so turn it on manually there if needed.

## Status / roadmap

Core emulation, game scanning/launching, the visual gallery, Settings, and controls (gamepad + touch) are all working and tested on real hardware across both flavors. Ongoing work is polish, UX gaps, and further real-device testing.

## License

GPL-3.0, matching upstream [hypseus-singe](https://github.com/DirtBagXon/hypseus-singe), since this project builds directly against and incorporates that GPL-licensed source.

This program is free software: you can redistribute it and/or modify
it under the terms of the [GNU General Public License](LICENSE) as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[GNU General Public License](LICENSE) for more details.

## Credits

Built on [Hypseus Singe](https://github.com/DirtBagXon/hypseus-singe) by DirtBagXon, itself a fork of [Hypseus](https://github.com/h0tw1r3/hypseus) by Jeffrey Clark and [Daphne](http://www.daphne-emu.com) by Matt Ownby, with Singe LUA support originally by Scott Duensing.

Touch controls (Hypdroid Touch flavor) built with [RadialGamePad](https://github.com/Swordfish90/RadialGamePad) by Swordfish90.
