import java.io.File

/**
 * #41 - resolves `media/` inside `HypdroidDesktop/` itself: fully
 * self-contained alongside `options.json`/`app_settings.json`, owned by
 * the launcher like everything else it writes (#6's ownership rule).
 * Originally #26 defaulted this to `<installRoot>/media/`, a sibling of
 * `HypdroidDesktop/` and the one deliberate exception to that rule -
 * revised here after phase 3 shipped and real device testing was done.
 *
 * Fixed location, not user-configurable: no folder picker exists or is
 * planned (owner's explicit confirmation, 2026-09-01) - unlike Android,
 * which has no natural default at all under its SAF storage-permission
 * model and genuinely needs one.
 *
 * Auto-creates `box/`, `cd/`, `logo/` and `bg/` underneath (empty) if
 * they don't already exist, so a real install always has the
 * discoverable structure in place with zero manual setup - the same
 * "auto-creates on every launch regardless of whether it's ever used"
 * pattern hypseus's own `homedir::set_homedir()` already establishes for
 * `bezels/` (confirmed real in `video.cpp` during phase 3's bezel work).
 * Existing art in an already-populated subfolder is left untouched -
 * `mkdirs()` is a no-op when the folder already exists.
 *
 * Real, deliberate consequence: deleting `HypdroidDesktop/` now deletes
 * any cover art placed inside it too. Anyone with art at the old
 * `<installRoot>/media/` location needs to move that folder in by hand -
 * no automatic migration is attempted.
 */
fun resolveMediaFolder(launcherFolder: File): File {
    val mediaFolder = File(launcherFolder, "media")
    for (subfolder in listOf("box", "cd", "logo", "bg")) {
        File(mediaFolder, subfolder).mkdirs()
    }
    return mediaFolder
}
