package de.morhenn.ar_localization.utils

import com.google.android.gms.maps.model.LatLng
import de.morhenn.ar_localization.model.GeoPose
import io.github.sceneview.math.Position
import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS = 6371.001 // average earth radius in km

    fun getLatLngByDistanceAndBearing(lat: Double, lng: Double, bearing: Double, distanceKm: Double): LatLng {
        val bearingR = Math.toRadians(bearing)

        val latR = Math.toRadians(lat)
        val lngR = Math.toRadians(lng)

        val distanceToRadius = distanceKm / EARTH_RADIUS

        val newLatR = asin(sin(latR) * cos(distanceToRadius) +
                cos(latR) * sin(distanceToRadius) * cos(bearingR))
        val newLonR = lngR + atan2(sin(bearingR) * sin(distanceToRadius) * cos(latR),
            cos(distanceToRadius) - sin(latR) * sin(newLatR))

        val latNew = Math.toDegrees(newLatR)
        val lngNew = Math.toDegrees(newLonR)

        return LatLng(latNew, lngNew)
    }

    //calculate new GeoPose by base on a relativePosition from another GeoPose
    fun getGeoPoseByLocalCoordinateOffset(startPose: GeoPose, offsetPosition: Position): GeoPose {
        val latLngOnlyX = getLatLngByDistanceAndBearing(startPose.latitude, startPose.longitude, (startPose.heading + 90.0).mod(360.0), offsetPosition.x / 1000.0)

        val latLng = getLatLngByDistanceAndBearing(latLngOnlyX.latitude, latLngOnlyX.longitude, startPose.heading, -offsetPosition.z / 1000.0)

        val altitude = startPose.altitude + offsetPosition.y

        return GeoPose(latLng.latitude, latLng.longitude, altitude, startPose.heading)
    }

    fun getGeoPoseByLocalCoordinateOffsetWithEastUpSouth(startPose: GeoPose, offsetPosition: Position): GeoPose {
        val latLngOnlyX = getLatLngByDistanceAndBearing(startPose.latitude, startPose.longitude, 90.0, offsetPosition.x / 1000.0)

        val latLng = getLatLngByDistanceAndBearing(latLngOnlyX.latitude, latLngOnlyX.longitude, 180.0, offsetPosition.z / 1000.0)

        val altitude = startPose.altitude + offsetPosition.y

        return GeoPose(latLng.latitude, latLng.longitude, altitude, startPose.heading)
    }

    //calculate new LatLng with offsetX and offsetZ
    //offsetX in meters towards startHeading
    //offsetZ in meters towards startHeading + 90°
    fun getLatLngByLocalCoordinateOffset(startLat: Double, startLng: Double, startHeading: Double, offsetX: Float, offsetZ: Float): LatLng {
        val latLngOnlyX = getLatLngByDistanceAndBearing(startLat, startLng, (startHeading + 90.0).mod(360.0), offsetX / 1000.0)

        return getLatLngByDistanceAndBearing(latLngOnlyX.latitude, latLngOnlyX.longitude, startHeading, -offsetZ / 1000.0)
    }

    //calculate relative position of one GeoPose to another
    fun getLocalCoordinateOffsetFromTwoGeoPoses(mainGeoPose: GeoPose, offsetGeoPose: GeoPose): Position {
        val mainLatLng = LatLng(mainGeoPose.latitude, mainGeoPose.longitude)
        val offsetLatLng = LatLng(offsetGeoPose.latitude, offsetGeoPose.longitude)

        val distance = distanceBetweenTwoLatLng(mainLatLng, offsetLatLng)
        val bearing = bearingBetweenTwoLatLng(mainLatLng, offsetLatLng)

        val offsetX = distance * sin(Math.toRadians(bearing - mainGeoPose.heading))
        val offsetZ = distance * cos(Math.toRadians(bearing - mainGeoPose.heading))

        val offsetY = offsetGeoPose.altitude - mainGeoPose.altitude

        return Position(offsetX.toFloat(), offsetY.toFloat(), -offsetZ.toFloat())
    }

    //calculate distance in meters between 2 positions in the ar world
    fun distanceBetweenTwo3dCoordinates(worldPos1: Position, worldPos2: Position): Float {
        val x = worldPos1.x - worldPos2.x
        val y = worldPos1.y - worldPos2.y
        val z = worldPos1.z - worldPos2.z
        return sqrt(x * x + y * y + z * z)
    }

    fun distanceBetweenTwoWorldCoordinates(lat1: Double, lng1: Double, alt1: Double, lat2: Double, lng2: Double, alt2: Double): Double {
        val distance = distanceBetweenTwoLatLng(LatLng(lat1, lng1), LatLng(lat2, lng2))

        return sqrt(distance * distance + (alt2 - alt1) * (alt2 - alt1))
    }

    //calculate distance in meters between 2 LatLng
    fun distanceBetweenTwoLatLng(start: LatLng, end: LatLng): Double {
        val latR1 = Math.toRadians(start.latitude)
        val lngR1 = Math.toRadians(start.longitude)
        val latR2 = Math.toRadians(end.latitude)
        val lngR2 = Math.toRadians(end.longitude)

        val dLat = latR2 - latR1
        val dLng = lngR2 - lngR1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(latR1) * cos(latR2) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS * c * 1000
    }

    //calculate bearing in degrees between 2 LatLng
    fun bearingBetweenTwoLatLng(start: LatLng, end: LatLng): Double {
        val latR1 = Math.toRadians(start.latitude)
        val lngR1 = Math.toRadians(start.longitude)
        val latR2 = Math.toRadians(end.latitude)
        val lngR2 = Math.toRadians(end.longitude)

        val dLng = lngR2 - lngR1

        val y = sin(dLng) * cos(latR2)
        val x = cos(latR1) * sin(latR2) -
                sin(latR1) * cos(latR2) * cos(dLng)
        val bearing = atan2(y, x)

        return (Math.toDegrees(bearing) + 360).mod(360.0)
    }
}