package de.morhenn.ar_localization.utils

import com.google.android.gms.maps.model.LatLng
import io.github.sceneview.math.Position
import kotlin.math.*

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

    //calculate distance in meters between 2 positions in the ar world
    fun distanceBetweenTwo3dCoordinates(worldPos1: Position, worldPos2: Position): Float {
        val x = worldPos1.x - worldPos2.x
        val y = worldPos1.y - worldPos2.y
        val z = worldPos1.z - worldPos2.z
        return sqrt(x * x + y * y + z * z)
    }

    fun distanceBetweenTwoWorldCoordinates(lat1: Double, lng1: Double, alt1: Double, lat2: Double, lng2: Double, alt2: Double): Double {
        val earthRadius = 6378.1

        val latR1 = Math.toRadians(lat1)
        val lngR1 = Math.toRadians(lng1)
        val latR2 = Math.toRadians(lat2)
        val lngR2 = Math.toRadians(lng2)

        val dLat = latR2 - latR1
        val dLng = lngR2 - lngR1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(latR1) * cos(latR2) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distance = earthRadius * c * 1000

        return sqrt(distance * distance + (alt2 - alt1) * (alt2 - alt1))
    }
}