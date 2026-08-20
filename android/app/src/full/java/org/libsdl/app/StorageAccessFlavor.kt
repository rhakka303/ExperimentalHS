package org.libsdl.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * "full"/Touch flavor only. Requests All Files Access
 * (MANAGE_EXTERNAL_STORAGE) - confirmed on-device (before a later revert
 * of unrelated work) to fix raw file access for hypseus's own code and
 * third-party libraries it bundles (Singe's zip loader, plog's logger)
 * that bypass the app's own file-access code entirely.
 *
 * Unlike a normal runtime permission, Android has no simple grant/deny
 * popup for this one - API 30+ requires sending the user to a dedicated
 * Settings screen where they flip the toggle themselves.
 */
fun requestAllFilesAccessIfNeeded(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

// #88 - this flavor uses the permission concept at all, regardless of
// current grant state. Onboarding/recovery UI uses this to decide whether
// to show the row in the first place.
fun isAllFilesAccessSupported(): Boolean = true

fun isAllFilesAccessGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
