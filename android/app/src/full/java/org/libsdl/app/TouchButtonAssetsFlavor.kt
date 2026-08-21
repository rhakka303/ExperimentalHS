package org.libsdl.app

/**
 * "full"/Touch flavor only - the real #98 button art. Handheld's copy of
 * this file (same package, same names) returns all nulls, since it has no
 * such resources and never shows the touch overlay. Keeping this split
 * across per-flavor source sets (rather than a runtime resource lookup)
 * means TouchControls.kt gets a real compile-time R reference and Handheld
 * still builds clean with zero knowledge of these assets.
 */
val touchButtonBumperLeft: Int? = R.drawable.hypdroid_touch_bumper_left_b
val touchButtonBumperRight: Int? = R.drawable.hypdroid_touch_bumper_right_b
val touchButtonTriggerLeft: Int? = R.drawable.hypdroid_touch_trigger_left_b
val touchButtonTriggerRight: Int? = R.drawable.hypdroid_touch_trigger_right_b
val touchButtonPill: Int? = R.drawable.hypdroid_touch_pill_b
val touchButtonStickCap: Int? = R.drawable.hypdroid_touch_stick_cap_b
