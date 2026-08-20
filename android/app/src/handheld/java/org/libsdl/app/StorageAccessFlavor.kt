package org.libsdl.app

import android.content.Context

/**
 * Handheld flavor: does nothing at all. No All Files Access request, no
 * behavior change from before this function existed. See the "full"
 * flavor's own copy of this file for the real implementation.
 */
fun requestAllFilesAccessIfNeeded(context: Context) {
    // Intentionally empty.
}

// #88 - Handheld never uses this permission concept, regardless of Android
// version - onboarding/recovery UI uses this to skip the row entirely
// rather than show it in an always-granted state.
fun isAllFilesAccessSupported(): Boolean = false

fun isAllFilesAccessGranted(context: Context): Boolean = true
