package de.morhenn.ar_localization.model

import dev.romainguy.kotlin.math.Quaternion

data class SerializableQuaternion(
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var z: Float = 0.0f,
    var w: Float = 0.0f,
) {
    constructor(q: Quaternion) : this(q.x, q.y, q.z, q.w)
    constructor() : this(1f, 0f, 0f, 0f)

    fun toQuaternion(): Quaternion = Quaternion(x, y, z, w)
}
