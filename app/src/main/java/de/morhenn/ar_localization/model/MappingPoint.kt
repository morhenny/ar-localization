package de.morhenn.ar_localization.model

import io.github.sceneview.math.Position

data class MappingPoint(
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var z: Float = 0.0f,
) {
    fun getPosition(): Position {
        return Position(x, y, z)
    }
}
