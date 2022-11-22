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
}