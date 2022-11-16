package de.morhenn.ar_localization.model

import kotlinx.serialization.Serializable

@Serializable
data class MappingPoint(
    var x: Float,
    var y: Float,
    var z: Float,
)
