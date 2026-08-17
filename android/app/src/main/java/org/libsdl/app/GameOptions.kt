package org.libsdl.app

import android.content.Context
import java.io.File

enum class CoverArtType { CD, LOGO, BOX, TEXT }

data class GameOptions(
    val coverArt: CoverArtType?, // null = no override, resolves to the app default (box)
    val bezelEnabled: Boolean,
    val arguments: List<String>,
)

private const val GAME_OPTIONS_PREFS = "hypdroid_game_options"

private fun coverArtKey(gameName: String) = "coverart_$gameName"
private fun bezelKey(gameName: String) = "bezel_$gameName"
private fun argumentsKey(gameName: String) = "args_$gameName"

fun loadGameOptions(context: Context, gameName: String): GameOptions {
    val prefs = context.getSharedPreferences(GAME_OPTIONS_PREFS, Context.MODE_PRIVATE)
    val coverArt = prefs.getString(coverArtKey(gameName), null)?.let {
        try {
            CoverArtType.valueOf(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    val bezelEnabled = prefs.getBoolean(bezelKey(gameName), false)
    val arguments = (prefs.getString(argumentsKey(gameName), "") ?: "")
        .split("\n")
        .filter { it.isNotBlank() }
    return GameOptions(coverArt, bezelEnabled, arguments)
}

fun saveCoverArt(context: Context, gameName: String, coverArt: CoverArtType) {
    context.getSharedPreferences(GAME_OPTIONS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(coverArtKey(gameName), coverArt.name)
        .apply()
}

fun saveBezelEnabled(context: Context, gameName: String, enabled: Boolean) {
    context.getSharedPreferences(GAME_OPTIONS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(bezelKey(gameName), enabled)
        .apply()
}

fun saveArguments(context: Context, gameName: String, arguments: List<String>) {
    context.getSharedPreferences(GAME_OPTIONS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(argumentsKey(gameName), arguments.joinToString("\n"))
        .apply()
}

// <media>/<box|cd|logo>/<gamename>.png - matches the subfolder convention
// from #30's planning. TEXT never has a file (it's the literal-text choice).
fun coverArtFile(mediaFolderPath: String?, gameName: String, type: CoverArtType): File? {
    if (mediaFolderPath == null || type == CoverArtType.TEXT) return null
    val subfolder = when (type) {
        CoverArtType.CD -> "cd"
        CoverArtType.LOGO -> "logo"
        CoverArtType.BOX -> "box"
        CoverArtType.TEXT -> return null
    }
    val file = File(mediaFolderPath, "$subfolder/$gameName.png")
    return if (file.exists()) file else null
}

// Resolves what the carousel should actually display for a game: an
// explicit per-game override if set (falling back to plain text if that
// specific type's file is missing, never silently substituting a
// different type than what was chosen), otherwise the app default (box).
fun resolveCoverArtFile(mediaFolderPath: String?, gameName: String, override: CoverArtType?): File? {
    val effectiveType = override ?: CoverArtType.BOX
    return coverArtFile(mediaFolderPath, gameName, effectiveType)
}

// <media>/bezel/<gamename>.png, matching #31's planning. -bezeldir must
// always be passed alongside -bezel - confirmed in video.cpp:130, hypseus's
// own relative default can never resolve correctly on this Android port
// (same SDL relative-path routing issue as #29's Bug 2).
fun bezelArtFile(mediaFolderPath: String?, gameName: String): File? {
    if (mediaFolderPath == null) return null
    val file = File(mediaFolderPath, "bezel/$gameName.png")
    return if (file.exists()) file else null
}

fun bezelLaunchArgs(mediaFolderPath: String?, gameName: String): List<String> {
    bezelArtFile(mediaFolderPath, gameName) ?: return emptyList()
    val bezelDir = File(mediaFolderPath, "bezel").absolutePath
    return listOf("-bezeldir", bezelDir, "-bezel", "$gameName.png")
}

// #47 - Global Cover Art override. App-wide, not per-game, so it lives in
// the same PREFS_NAME file as the folder paths rather than GAME_OPTIONS_PREFS
// above (which is keyed per-game and cleared/rescanned with the game list).
private const val PREF_GLOBAL_COVER_ART_ENABLED = "global_cover_art_enabled"
private const val PREF_GLOBAL_COVER_ART_TYPE = "global_cover_art_type"

fun loadGlobalCoverArtEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_GLOBAL_COVER_ART_ENABLED, false)
}

fun saveGlobalCoverArtEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_GLOBAL_COVER_ART_ENABLED, enabled)
        .apply()
}

fun loadGlobalCoverArtType(context: Context): CoverArtType {
    val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_GLOBAL_COVER_ART_TYPE, null)
    val parsed = stored?.let {
        try {
            CoverArtType.valueOf(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    return parsed ?: CoverArtType.BOX
}

fun saveGlobalCoverArtType(context: Context, type: CoverArtType) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_GLOBAL_COVER_ART_TYPE, type.name)
        .apply()
}
