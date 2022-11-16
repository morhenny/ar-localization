package de.morhenn.ar_localization.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object GeoUtils {

    fun getLatLngByDistanceAndBearing(lat: Double, lng: Double, bearing: Double, distanceKm: Double): LatLng {
        val earthRadius = 6378.1

        val bearingR = Math.toRadians(bearing)

        val latR = Math.toRadians(lat)
        val lngR = Math.toRadians(lng)

        val distanceToRadius = distanceKm / earthRadius

        val newLatR = asin(sin(latR) * cos(distanceToRadius) +
                cos(latR) * sin(distanceToRadius) * cos(bearingR))
        val newLonR = lngR + atan2(sin(bearingR) * sin(distanceToRadius) * cos(latR),
            cos(distanceToRadius) - sin(latR) * sin(newLatR))

        val latNew = Math.toDegrees(newLatR)
        val lngNew = Math.toDegrees(newLonR)

        return LatLng(latNew, lngNew)
    }


    //calculate new LatLng with offsetX and offsetZ
    //offsetX in meters towards startHeading
    //offsetZ in meters towards startHeading + 90°
    fun getLatLngByLocalCoordinateOffset(startLat: Double, startLng: Double, startHeading: Double, offsetX: Float, offsetZ: Float): LatLng {
        val latLngOnlyX = getLatLngByDistanceAndBearing(startLat, startLng, (startHeading + 90.0) % 360, offsetX / 1000.0)

        return getLatLngByDistanceAndBearing(latLngOnlyX.latitude, latLngOnlyX.longitude, startHeading, -offsetZ / 1000.0)
    }
}