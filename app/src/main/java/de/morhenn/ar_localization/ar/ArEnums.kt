package de.morhenn.ar_localization.ar

import android.view.View.INVISIBLE
import android.view.View.VISIBLE

enum class ArState(
    val progressBarVisibility: Int = INVISIBLE,
    val fabEnabled: Boolean = false,
    val fabConfirmVisibility: Int = INVISIBLE,
    val anchorCircleEnabled: Boolean = false,
    val fabState: ArFabState = ArFabState.PLACE,
    val undoVisibility: Int = INVISIBLE,
) {
    NOT_INITIALIZED,
    PLACE_ANCHOR(fabEnabled = true, anchorCircleEnabled = true, undoVisibility = VISIBLE, fabState = ArFabState.PLACE),
    SCAN_ANCHOR_CIRCLE(anchorCircleEnabled = true, undoVisibility = VISIBLE),
    HOSTING(progressBarVisibility = VISIBLE, anchorCircleEnabled = true, undoVisibility = VISIBLE, fabState = ArFabState.HOST),
    MAPPING(fabEnabled = true, undoVisibility = VISIBLE, fabState = ArFabState.NEW_ANCHOR, fabConfirmVisibility = VISIBLE),
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