import java.io.File

/**
 * #26 - resolves the default `media/` folder cover art and background art
 * live under: `<installRoot>/media/`.
 *
 * Android has no equivalent default - its storage-permission model (SAF)
 * forces a first-run folder picker with no natural default location at
 * all (see `PREF_MEDIA_FOLDER_URI` in `MainActivity.kt`). Windows has
 * direct filesystem access, so there's nothing forcing that same
 * first-run step here; the epic's own scope decision is to default
 * instead of ask, matching how #8's install root itself is resolved
 * rather than picked.
 *
 * Confirmed against two real Android devices (Powkiddy X55, Retroid
 * Pocket 5) via adb: both have a populated `media/{box,cd,logo,bg}/`
 * folder sitting directly alongside the games, matching this default
 * relationship.
 *
 * Deliberately returns the path unconditionally, with no existence
 * check - same resolution-vs-use split #8's `scanGames`/`resolveInstallRoot`
 * already establishes. Whether the folder (or any file inside it) exists
 * is #27's concern, not this function's.
 *
 * No override/configuration mechanism yet: the epic's non-goals require
 * media/ to be configurable eventually, but no real gap has appeared to
 * justify building a picker before one does.
 */
fun resolveMediaFolder(installRoot: File): File = File(installRoot, "media")
