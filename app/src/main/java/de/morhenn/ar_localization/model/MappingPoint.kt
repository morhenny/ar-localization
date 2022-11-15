package de.morhenn.ar_localization.model

import kotlinx.serialization.Serializable

@Serializable
data class MappingPoint(
    var relativeX: Float,
    var relativeY: Float,
    var relativeZ: Float,
    var relativeQuaternion: SerializableQuaternion,
)
