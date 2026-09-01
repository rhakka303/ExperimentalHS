import java.io.File
import kotlinx.serialization.Serializable

/**
 * #27 - pure file-resolution logic for cover art and background art,
 * ported from Android's GameOptions.kt (coverArtFile/resolveCoverArtFile/
 * backgroundArtFile) - the specification, not something copied from,
 * since this logic is already platform-free (plain java.io.File) and
 * survives the port unchanged. Signatures take a resolved File for the
 * media folder rather than Android's nullable String path, since #26's
 * resolveMediaFolder() is unconditional here - there's no SAF permission
 * gate on Windows for it to be null while waiting on.
 *
 * @Serializable now, ahead of #30/#31 actually persisting it, since it's
 * a plain data enum with no reason to wait.
 */
@Serializable
enum class CoverArtType { CD, LOGO, BOX, TEXT }

/**
 * <media>/{box,cd,logo}/<gameName>.png, or null.
 *
 * TEXT resolves to null unconditionally - no file lookup at all,
 * confirmed against the real Android source. A missing file for
 * CD/LOGO/BOX also resolves to null - the two cases are deliberately
 * indistinguishable to the caller: never a silent swap to a different
 * art type than what was actually requested.
 */
fun coverArtFile(mediaFolder: File, gameName: String, type: CoverArtType): File? {
    val subfolder = when (type) {
        CoverArtType.CD -> "cd"
        CoverArtType.LOGO -> "logo"
        CoverArtType.BOX -> "box"
        CoverArtType.TEXT -> return null
    }
    val file = File(mediaFolder, "$subfolder/$gameName.png")
    return if (file.isFile) file else null
}

/**
 * The effective type for a game is its own override if set, else BOX
 * (the app default) - confirmed on a real device: a game with no
 * override still showed BOX on its options screen, not a blank state.
 */
fun resolveCoverArtFile(mediaFolder: File, gameName: String, override: CoverArtType?): File? {
    val effectiveType = override ?: CoverArtType.BOX
    return coverArtFile(mediaFolder, gameName, effectiveType)
}

/**
 * <media>/bg/<gameName>.png or <media>/bg/default.png, gated by two
 * separate toggles. This is NOT a fallback mechanic - confirmed against
 * the real backgroundArtFile() source, and corrected here after an
 * earlier wrong assumption while scoping this story:
 *
 * - backgroundArtEnabled false -> always null (plain background),
 *   regardless of defaultArtEnabled
 * - backgroundArtEnabled true + defaultArtEnabled true -> EVERY game
 *   uses bg/default.png, unconditionally - an override, not a
 *   missing-file fallback
 * - backgroundArtEnabled true + defaultArtEnabled false -> each game
 *   looks up its own bg/<gameName>.png; if missing, null (plain) - no
 *   fallback to default.png in this case
 */
fun backgroundArtFile(
    mediaFolder: File,
    gameName: String,
    backgroundArtEnabled: Boolean,
    defaultArtEnabled: Boolean,
): File? {
    if (!backgroundArtEnabled) return null
    val fileName = if (defaultArtEnabled) "default" else gameName
    val file = File(mediaFolder, "bg/$fileName.png")
    return if (file.isFile) file else null
}
