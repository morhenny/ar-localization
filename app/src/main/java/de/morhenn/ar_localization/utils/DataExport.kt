package de.morhenn.ar_localization.utils

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.ar.core.GeospatialPose
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.GeoPose
import io.github.sceneview.math.Position
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DataExport {

    var loggingEnabled = true

    private const val TAG = "DataExport"
    private const val FILE_NAME_MAPPING_PREFIX = "Map__"
    private const val FILE_NAME_LOCALIZING_PREFIX = "Localize__"
    private const val FILE_NAME_ANCHOR_ERROR_PREFIX = "AnchorError__"
    private const val FILE_NAME_ANCHOR_TRACKING_TEST_PREFIX = "TrackingTest__"

    private var fileNameMapping = ""
    private var fileNameLocalizing = ""
    private var fileNameAnchorError = ""
    private var fileNameTrackingTest = ""

    private var hasOpenMappingFile = false
    private var hasOpenLocalizingFile = false

    private lateinit var path: String
    private lateinit var fileWriterMapping: FileWriter
    private lateinit var fileWriterLocalizing: FileWriter

    private lateinit var xmlMap: XmlSerializer
    private lateinit var xmlLocalize: XmlSerializer
    private lateinit var xmlAnchorTest: XmlSerializer

    private var anchorErrorDistanceMap = mutableListOf<Pair<Float, Float>>() //error per distance
    private var anchorErrorOffsetMap = mutableListOf<Pair<Float, Position>>() //error offset per distance

    private var mapAnchors = mutableListOf<CloudAnchor>()

    private var resolvePointList = mutableListOf<Pair<GeoPose, String>>()

    private var vpsList = mutableListOf<GeospatialPose>()
    private var cpsList = mutableListOf<GeoPose>()
    private var gpsList = mutableListOf<Location>()

    private var vpsAnchorTestList = mutableListOf<GeospatialPose>()
    private var cpsAnchorTestList = mutableListOf<GeoPose>()

    fun init(context: Context) {
        path = context.filesDir.absolutePath
    }

    private fun currentTimestampString(): String {
        return DateTimeFormatter
            .ofPattern("dd-MM-yy_HH-mm-ss")
            .withZone(ZoneOffset.from(ZonedDateTime.now()))
            .format(Instant.now())
    }

    fun writeAnchorTrackingDataToFile(distanceBetweenAnchors: Float, loggingInterval: Long) {
        if (loggingEnabled) {
            Log.d(TAG, "Writing anchor error map to file")
            fileNameTrackingTest = FILE_NAME_ANCHOR_TRACKING_TEST_PREFIX + currentTimestampString() + ".kml"
            xmlAnchorTest = XmlPullParserFactory.newInstance().newSerializer()
            with(xmlAnchorTest) {
                startDocument(null, null)
                setOutput(FileWriter("$path/$fileNameTrackingTest", true))
                setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
                startTag(null, "kml")
                startTag(null, "Document")
                startTag(null, "Style")
                attribute(null, "id", "cpsStyle")
                startTag(null, "LineStyle")
                startTag(null, "color")
                text("00FF00")
                endTag(null, "color")
                endTag(null, "LineStyle")
                endTag(null, "Style")
                startTag(null, "Style")
                attribute(null, "id", "vpsStyle")
                startTag(null, "LineStyle")
                startTag(null, "color")
                text("FF0000")
                endTag(null, "color")
                endTag(null, "LineStyle")
                endTag(null, "Style")

                appendAnchorTrackingTestCPSPoints(distanceBetweenAnchors, loggingInterval)
                appendAnchorTrackingTestVPSPoints()

                endTag(null, "Document")
                endTag(null, "kml")
                endDocument()
                flush()
            }
        }
    }

    fun addAnchorTrackingData(cpsPose: GeoPose, vpsPose: GeospatialPose) {
        cpsAnchorTestList.add(cpsPose)
        vpsAnchorTestList.add(vpsPose)
    }

    fun isAnchorTrackingDataEmpty(): Boolean {
        return cpsAnchorTestList.isEmpty()
    }

    fun writeAnchorErrorToFile(name: String = "") {
        if (loggingEnabled) {
            Log.d(TAG, "Writing anchor error distance to file")
            fileNameAnchorError = FILE_NAME_ANCHOR_ERROR_PREFIX + currentTimestampString() + "_$name.csv"
            FileWriter("$path/$fileNameAnchorError").use {
                it.write("Distance, ErrorDistance, ErrorX, ErrorY, ErrorZ\n")
                for (i in 0 until anchorErrorOffsetMap.size) {
                    it.write("${anchorErrorDistanceMap[i].first}, ${anchorErrorDistanceMap[i].second}, ${anchorErrorOffsetMap[i].second.x}, ${anchorErrorOffsetMap[i].second.y}, ${anchorErrorOffsetMap[i].second.z}\n")
                }

                it.close()
                anchorErrorDistanceMap.clear()
                anchorErrorOffsetMap.clear()
            }
        }
    }

    fun addAnchorErrorSet(distance: Float, error: Float) {
        if (loggingEnabled) {
            Log.d(TAG, "addAnchorErrorSet: $distance, $error")
            anchorErrorDistanceMap.add(Pair(distance, error))
        }
    }

    fun addAnchorErrorOffset(distanceToLast: Float, posOffsetFromPreviewToCloudAnchor: Position) {
        if (loggingEnabled) {
            Log.d(TAG, "addAnchorErrorOffset: $distanceToLast, $posOffsetFromPreviewToCloudAnchor")
            anchorErrorOffsetMap.add(Pair(distanceToLast, posOffsetFromPreviewToCloudAnchor))
        }
    }

    fun startNewMappingFile(name: String? = null) {
        if (loggingEnabled) {
            if (hasOpenMappingFile) {
                finishMappingFile()
            }
            hasOpenMappingFile = true
            name?.let {
                fileNameMapping = "$path/$FILE_NAME_MAPPING_PREFIX" + currentTimestampString() + "_$name.kml"
            } ?: run {
                fileNameMapping = "$path/$FILE_NAME_MAPPING_PREFIX" + currentTimestampString() + ".kml"
            }
            Log.d(TAG, "Creating new Mapping file at: $fileNameMapping")

            fileWriterMapping = FileWriter(fileNameMapping, true)
            xmlMap = XmlPullParserFactory.newInstance().newSerializer()
            with(xmlMap) {
                startDocument(null, null)
                setOutput(fileWriterMapping)
                setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
                startTag(null, "kml")
                startTag(null, "Document")
                startTag(null, "Placemark")
                startTag(null, "name")
                text("Mapped Floor Plan")
                endTag(null, "name")
                startTag(null, "LineString")
                startTag(null, "altitudeMode")
                text("absolute")
                endTag(null, "altitudeMode")
                startTag(null, "coordinates")
            }
        }
    }

    fun finishMappingFile() {
        if (loggingEnabled && hasOpenMappingFile) {
            with(xmlMap) {
                endTag(null, "coordinates")
                endTag(null, "LineString")
                endTag(null, "Placemark")
            }

            appendMappingAnchors()

            with(xmlMap) {
                endTag(null, "Document")
                endTag(null, "kml")
                endDocument()
                flush()
            }
            fileWriterMapping.close()

            hasOpenMappingFile = false
            Log.d(TAG, "Finished Mapping file at: $fileNameMapping")
        }
    }

    fun appendMappingPointData(mappingPointPose: GeoPose) {
        if (loggingEnabled) {
            if (!hasOpenMappingFile) startNewMappingFile()
            xmlMap.text("${mappingPointPose.longitude},${mappingPointPose.latitude},${mappingPointPose.altitude} ")
        }
    }

    fun addMappingAnchor(cloudAnchor: CloudAnchor) {
        if (loggingEnabled) mapAnchors.add(cloudAnchor)
    }

    private fun appendMappingAnchors() {
        if (loggingEnabled) {
            mapAnchors.forEach {
                with(xmlMap) {
                    startTag(null, "Placemark")
                    startTag(null, "name")
                    text(it.text)
                    endTag(null, "name")
                    startTag(null, "Point")
                    startTag(null, "altitudeMode")
                    text("absolute")
                    endTag(null, "altitudeMode")
                    startTag(null, "coordinates")
                    text("${it.lng},${it.lat},${it.alt}")
                    endTag(null, "coordinates")
                    endTag(null, "Point")
                    endTag(null, "Placemark")
                }
            }
            mapAnchors.clear()
        }
    }

    fun startNewLocalizingFile(name: String? = null) {
        anchorErrorOffsetMap.clear()
        anchorErrorDistanceMap.clear()
        resolvePointList.clear()
        if (loggingEnabled) {
            if (hasOpenLocalizingFile) {
                finishLocalizingFile()
            }
            hasOpenLocalizingFile = true
            name?.let {
                fileNameLocalizing = "$path/$FILE_NAME_LOCALIZING_PREFIX" + currentTimestampString() + "_$name.kml"
            } ?: run {
                fileNameLocalizing = "$path/$FILE_NAME_LOCALIZING_PREFIX" + currentTimestampString() + ".kml"
            }
            Log.d(TAG, "Creating new Localizing file at: $fileNameLocalizing")

            fileWriterLocalizing = FileWriter(fileNameLocalizing, true)

            xmlLocalize = XmlPullParserFactory.newInstance().newSerializer()
            with(xmlLocalize) {
                startDocument(null, null)
                setOutput(fileWriterLocalizing)
                setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
                startTag(null, "kml")
                startTag(null, "Document")
                startTag(null, "Style")
                attribute(null, "id", "cpsStyle")
                startTag(null, "LineStyle")
                startTag(null, "color")
                text("00FF00")
                endTag(null, "color")
                endTag(null, "LineStyle")
                startTag(null, "IconStyle")
                startTag(null, "color")
                text("FF00FF00")
                endTag(null, "color")
                startTag(null, "scale")
                text("1")
                endTag(null, "scale")
                startTag(null, "Icon")
                startTag(null, "href")
                text("https://www.gstatic.com/mapspro/images/stock/503-wht-blank_maps.png")
                endTag(null, "href")
                endTag(null, "Icon")
                startTag(null, "hotSpot")
                attribute(null, "x", "32")
                attribute(null, "xunits", "pixels")
                attribute(null, "y", "64")
                attribute(null, "yunits", "insetPixels")
                endTag(null, "hotSpot")
                endTag(null, "IconStyle")
                endTag(null, "Style")
                startTag(null, "Style")
                attribute(null, "id", "vpsStyle")
                startTag(null, "LineStyle")
                startTag(null, "color")
                text("FF0000")
                endTag(null, "color")
                endTag(null, "LineStyle")
                endTag(null, "Style")
                startTag(null, "Style")
                attribute(null, "id", "gpsStyle")
                startTag(null, "LineStyle")
                startTag(null, "color")
                text("0000FF")
                endTag(null, "color")
                endTag(null, "LineStyle")
                endTag(null, "Style")
            }
        }
    }

    fun finishLocalizingFile() {
        if (loggingEnabled) {
            if (hasOpenLocalizingFile) {
                appendLocalizingCPSPoints()
                appendLocalizingVPSPoints()
                appendLocalizingGPSPoints()
                appendResolvePoints()
                with(xmlLocalize) {
                    endTag(null, "Document")
                    endTag(null, "kml")
                    endDocument()
                    flush()
                }
                fileWriterLocalizing.close()
                hasOpenLocalizingFile = false
                Log.d(TAG, "Finished Localizing file at: $fileNameLocalizing")
            }
        }
    }

    fun addLocalizingData(vpsPose: GeospatialPose, cpsPose: GeoPose, gpsPose: Location) {
        if (loggingEnabled) {
            if (!hasOpenLocalizingFile) startNewLocalizingFile()
            vpsList.add(vpsPose)
            cpsList.add(cpsPose)
            gpsList.add(gpsPose)
        }
    }

    fun addResolvePoint(geoPose: GeoPose, anchorName: String) {
        if (loggingEnabled) {
            resolvePointList.add(Pair(geoPose, anchorName))
        }
    }

    private fun appendResolvePoints() {
        with(xmlLocalize) {
            resolvePointList.forEach {
                startTag(null, "Placemark")
                startTag(null, "name")
                text("Resolved: ${it.second}")
                endTag(null, "name")
                startTag(null, "styleUrl")
                text("#cpsStyle")
                endTag(null, "styleUrl")
                startTag(null, "Point")
                startTag(null, "altitudeMode")
                text("absolute")
                endTag(null, "altitudeMode")
                startTag(null, "coordinates")
                text("${it.first.longitude},${it.first.latitude},${it.first.altitude}")
                endTag(null, "coordinates")
                endTag(null, "Point")
                endTag(null, "Placemark")
            }
        }
        resolvePointList.clear()
    }

    private fun appendLocalizingCPSPoints() {
        with(xmlLocalize) {
            startTag(null, "Placemark")
            startTag(null, "name")
            text("CPS")
            endTag(null, "name")
            startTag(null, "styleUrl")
            text("#cpsStyle")
            endTag(null, "styleUrl")
            startTag(null, "LineString")
            startTag(null, "altitudeMode")
            text("absolute")
            endTag(null, "altitudeMode")
            startTag(null, "coordinates")
        }
        cpsList.forEach {
            xmlLocalize.text("${it.longitude},${it.latitude},${it.altitude}\n")
        }
        with(xmlLocalize) {
            endTag(null, "coordinates")
            endTag(null, "LineString")
            endTag(null, "Placemark")
        }

        cpsList.clear()
    }

    private fun appendLocalizingVPSPoints() {
        with(xmlLocalize) {
            startTag(null, "Placemark")
            startTag(null, "name")
            text("VPS")
            endTag(null, "name")
            startTag(null, "styleUrl")
            text("#vpsStyle")
            endTag(null, "styleUrl")
            startTag(null, "LineString")
            startTag(null, "altitudeMode")
            text("absolute")
            endTag(null, "altitudeMode")
            startTag(null, "coordinates")
        }
        vpsList.forEach {
            xmlLocalize.text("${it.longitude},${it.latitude},${it.altitude}\n")
        }
        with(xmlLocalize) {
            endTag(null, "coordinates")
            endTag(null, "LineString")
            endTag(null, "Placemark")
        }

        vpsList.clear()
    }

    private fun appendLocalizingGPSPoints() {
        with(xmlLocalize) {
            startTag(null, "Placemark")
            startTag(null, "name")
            text("GPS")
            endTag(null, "name")
            startTag(null, "styleUrl")
            text("#gpsStyle")
            endTag(null, "styleUrl")
            startTag(null, "LineString")
            startTag(null, "altitudeMode")
            text("absolute")
            endTag(null, "altitudeMode")
            startTag(null, "coordinates")
        }
        gpsList.forEach {
            xmlLocalize.text("${it.longitude},${it.latitude},${it.altitude}\n")
        }
        with(xmlLocalize) {
            endTag(null, "coordinates")
            endTag(null, "LineString")
            endTag(null, "Placemark")
        }

        gpsList.clear()
    }

    private fun appendAnchorTrackingTestCPSPoints(distanceBetweenAnchors: Float, loggingInterval: Long) {
        with(xmlAnchorTest) {
            startTag(null, "Placemark")
            startTag(null, "name")
            text("Anchored SLAM Data")
            endTag(null, "name")
            startTag(null, "description")
            text("Anchor Distance: $distanceBetweenAnchors; Interval: $loggingInterval")
            endTag(null, "description")
            startTag(null, "styleUrl")
            text("#cpsStyle")
            endTag(null, "styleUrl")
            startTag(null, "LineString")
            startTag(null, "altitudeMode")
            text("absolute")
            endTag(null, "altitudeMode")
            startTag(null, "coordinates")
        }
        cpsAnchorTestList.forEach {
            xmlAnchorTest.text("${it.longitude},${it.latitude},${it.altitude}\n")
        }
        with(xmlAnchorTest) {
            endTag(null, "coordinates")
            endTag(null, "LineString")
            endTag(null, "Placemark")
        }

        cpsAnchorTestList.clear()
    }

    private fun appendAnchorTrackingTestVPSPoints() {
        with(xmlAnchorTest) {
            startTag(null, "Placemark")
            startTag(null, "name")
            text("VPS")
            endTag(null, "name")
            startTag(null, "styleUrl")
            text("#vpsStyle")
            endTag(null, "styleUrl")
            startTag(null, "LineString")
            startTag(null, "altitudeMode")
            text("absolute")
            endTag(null, "altitudeMode")
            startTag(null, "coordinates")
        }
        vpsAnchorTestList.forEach {
            xmlAnchorTest.text("${it.longitude},${it.latitude},${it.altitude}\n")
        }
        with(xmlAnchorTest) {
            endTag(null, "coordinates")
            endTag(null, "LineString")
            endTag(null, "Placemark")
        }

        vpsAnchorTestList.clear()
    }
}