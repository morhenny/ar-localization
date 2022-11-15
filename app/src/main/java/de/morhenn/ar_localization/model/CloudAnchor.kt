package de.morhenn.ar_localization.model

import io.github.sceneview.math.Rotation
import kotlinx.serialization.Serializable

@Serializable
data class CloudAnchor(
    var text: String, //description to search by
    var cloudAnchorId: String,

    //geo position of the anchor
    var lat: Double,
    var lng: Double,
    var alt: Double,
    var compassHeading: Double,

    //relative position of the anchor to current main anchor
    var relativeX: Float,
    var relativeY: Float,
    var relativeZ: Float,
    var relativeQuaternion: SerializableQuaternion, //TODO use quaternion or euler angles, or constrain to always the main anchors rotation?
) {
    constructor(text: String, cloudAnchorId: String, lat: Double, lng: Double, alt: Double, compassHeading: Double) :
            this(text, cloudAnchorId, lat, lng, alt, compassHeading, 0.0f, 0.0f, 0.0f, SerializableQuaternion())
}
