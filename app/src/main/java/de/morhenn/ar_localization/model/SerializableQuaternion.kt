package de.morhenn.ar_localization.model

import dev.romainguy.kotlin.math.Quaternion
import kotlinx.serialization.Serializable

@Serializable
data class SerializableQuaternion(
    var x: Float,
    var y: Float,
    var z: Float,
    var w: Float,
) {
    constructor(q: Quaternion) : this(q.x, q.y, q.z, q.w)
    constructor() : this(1f, 0f, 0f, 0f)

    fun toQuaternion(): Quaternion = Quaternion(x, y, z, w)
}
