package de.morhenn.ar_localization.utils

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.ar.core.GeospatialPose
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.GeoPose
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DataExport {

    private const val TAG = "DataExport"
    private const val FILE_NAME_MAPPING_PREFIX = "Mapping_Export_"
    private const val FILE_NAME_LOCALIZING_PREFIX = "Localizing_Export_"
    private var fileNameMapping = ""
    private var fileNameLocalizing = ""

    private var hasOpenMappingFile = false
    private var hasOpenLocalizingFile = false

    private lateinit var path: String
    private lateinit var fileWriterMapping: FileWriter
    private lateinit var fileWriterLocalizing: FileWriter

    private lateinit var xmlMap: XmlSerializer
    private lateinit var xmlLocalize: XmlSerializer

    private var mapAnchors = mutableListOf<CloudAnchor>()

    private var vpsList = mutableListOf<GeospatialPose>()
    private var cpsList = mutableListOf<GeoPose>()
    private var gpsList = mutableListOf<Location>()

    fun init(context: Context) {
        path = context.filesDir.absolutePath
    }

    private fun currentTimestampString(): String {
        return DateTimeFormatter
            .ofPattern("dd-MM-yy_HH-mm-ss")
            .withZone(ZoneOffset.from(ZonedDateTime.now()))
            .format(Instant.now())
    }

    fun startNewMappingFile(name: String? = null) {
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
            startTag(null, "name")
            text("Mapping Data Export")
            endTag(null, "name")
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

    fun finishMappingFile() {
        if (hasOpenMappingFile) {
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
        if (!hasOpenMappingFile) startNewMappingFile()
        xmlMap.text("${mappingPointPose.longitude},${mappingPointPose.latitude},${mappingPointPose.altitude} ")
    }

    fun addMappingAnchor(cloudAnchor: CloudAnchor) {
        mapAnchors.add(cloudAnchor)
    }

    private fun appendMappingAnchors() {
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

    fun startNewLocalizingFile() {
        if (hasOpenLocalizingFile) {
            finishLocalizingFile()
        }
        hasOpenLocalizingFile = true
        fileNameLocalizing = "$path/$FILE_NAME_LOCALIZING_PREFIX" + currentTimestampString() + ".kml"
        Log.d(TAG, "Creating new Localizing file at: $fileNameLocalizing")

        fileWriterLocalizing = FileWriter(fileNameLocalizing, true)

        xmlLocalize = XmlPullParserFactory.newInstance().newSerializer()
        with(xmlLocalize) {
            startDocument(null, null)
            setOutput(fileWriterLocalizing)
            setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
            startTag(null, "kml")
            startTag(null, "Document")
            startTag(null, "name")
            text("Localizing Data Export")
            endTag(null, "name")
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

    fun finishLocalizingFile() {
        if (hasOpenLocalizingFile) {
            appendLocalizingCPSPoints()
            appendLocalizingVPSPoints()
            appendLocalizingGPSPoints()
            xmlLocalize.endTag(null, "Document")
            xmlLocalize.endTag(null, "kml")
            xmlLocalize.endDocument()
            xmlLocalize.flush()
            fileWriterLocalizing.close()
            hasOpenLocalizingFile = false
            Log.d(TAG, "Finished Localizing file at: $fileNameLocalizing")
        }
    }

    fun addLocalizingData(vpsPose: GeospatialPose, cpsPose: GeoPose, gpsPose: Location) {
        if (!hasOpenLocalizingFile) startNewLocalizingFile()
        vpsList.add(vpsPose)
        cpsList.add(cpsPose)
        gpsList.add(gpsPose)
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
}