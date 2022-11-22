package de.morhenn.ar_localization.ar

import android.view.View

enum class ArState(
    val progressBarVisibility: Int = View.INVISIBLE,
    val fabEnabled: Boolean = false,
    val anchorCircleEnabled: Boolean = false,
) {
    NOT_INITIALIZED,
    PLACE_ANCHOR(fabEnabled = true, anchorCircleEnabled = true),
    SCAN_ANCHOR_CIRCLE(anchorCircleEnabled = true),
    HOSTING(progressBarVisibility = View.VISIBLE, anchorCircleEnabled = true),
    MAPPING,
}

enum class ArMode {
    CREATE_FLOOR_PLAN,
    LOCALIZE
}

enum class ModelName {
    DEBUG_CUBE,
    AXIS,
    BALL,
}