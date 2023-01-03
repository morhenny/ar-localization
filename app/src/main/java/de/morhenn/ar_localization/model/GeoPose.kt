package de.morhenn.ar_localization.model

import com.google.android.gms.maps.model.LatLng

data class GeoPose(
    var latitude: Double,
    var longitude: Double,
    var altitude: Double,
    var heading: Double,
) {
    fun getLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }

    override fun toString(): String {
        return buildString {
            append("GeoPose ")
            append(String.format("Lat: %.6f , ", latitude))
            append(String.format("Long: %.6f , ", longitude))
            append(String.format("Alt: %.3f , ", altitude))
            append(String.format("Heading: %.2f°", heading))
        }
    }
}
