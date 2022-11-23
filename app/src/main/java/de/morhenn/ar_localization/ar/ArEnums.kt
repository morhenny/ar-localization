package de.morhenn.ar_localization.ar

import android.view.View

enum class ArState(
    val progressBarVisibility: Int = View.INVISIBLE,
    val fabEnabled: Boolean = false,
    val fabConfirmVisibility: Int = View.INVISIBLE,
    val anchorCircleEnabled: Boolean = false,
    val fabState: ArFabState = ArFabState.PLACE,
) {
    NOT_INITIALIZED,
    PLACE_ANCHOR(fabEnabled = true, anchorCircleEnabled = true, fabState = ArFabState.PLACE),
    SCAN_ANCHOR_CIRCLE(anchorCircleEnabled = true),
    HOSTING(progressBarVisibility = View.VISIBLE, anchorCircleEnabled = true, fabState = ArFabState.HOST),
    MAPPING(fabEnabled = true, fabState = ArFabState.NEW_ANCHOR, fabConfirmVisibility = View.VISIBLE),
}

enum class ArFabState {
    PLACE,
    RESOLVE,
    HOST,
    NEW_ANCHOR,
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