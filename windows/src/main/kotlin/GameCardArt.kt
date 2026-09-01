import java.io.File

/**
 * #28 - the effective art file for a carousel card: Global Cover Art
 * (#31) wins over a game's own per-game override (#30) while it's on,
 * matching the real Android precedence exactly (confirmed against
 * MainActivity.kt's carousel rendering - an earlier draft of this story
 * had the precedence backwards before that check). resolveCoverArtFile's
 * fallback-to-BOX only kicks in for the null (no override at all) case,
 * so this always passes a concrete type through when Global Cover Art is
 * enabled, never leaving room for a per-game override to leak through.
 *
 * A standalone, non-composable function specifically so this precedence
 * rule is unit-testable without a Compose test harness.
 */
fun effectiveCoverArtFile(
    mediaFolder: File,
    gameName: String,
    appSettings: AppSettings,
    gameOptions: GameOptions?,
): File? {
    val override = if (appSettings.globalCoverArtEnabled) {
        appSettings.globalCoverArtType
    } else {
        gameOptions?.coverArtOverride
    }
    return resolveCoverArtFile(mediaFolder, gameName, override)
}
