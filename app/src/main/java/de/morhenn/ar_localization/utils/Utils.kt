package de.morhenn.ar_localization.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.model.FloorPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

object Utils {

    fun getBitmapFromVectorDrawable(drawableId: Int, context: Context): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun showFloorPlanOnMap(floorPlan: FloorPlan, map: GoogleMap, context: Context, padding: Int = 100) {
        val mainAnchorLatLng = LatLng(floorPlan.mainAnchor.lat, floorPlan.mainAnchor.lng)
        map.clear()
        val latLngBounds = LatLngBounds.Builder()
        latLngBounds.include(mainAnchorLatLng)

        val mainAnchorIcon = getBitmapFromVectorDrawable(R.drawable.ic_outline_flag_circle_24, context)
        val trackingAnchorIcon = getBitmapFromVectorDrawable(R.drawable.ic_outline_flag_24, context)
        map.addMarker(MarkerOptions().position(mainAnchorLatLng).icon(BitmapDescriptorFactory.fromBitmap(mainAnchorIcon)).title(floorPlan.mainAnchor.cloudAnchorId))
        floorPlan.cloudAnchorList.forEach {
            map.addMarker(MarkerOptions().position(LatLng(it.lat, it.lng)).icon(BitmapDescriptorFactory.fromBitmap(trackingAnchorIcon)).title(it.cloudAnchorId))
            latLngBounds.include(LatLng(it.lat, it.lng))
        }

        val pointIcon = getBitmapFromVectorDrawable(R.drawable.ic_baseline_blue_dot_3, context)
        floorPlan.mappingPointList.forEach {
            val pointLatLng = GeoUtils.getLatLngByLocalCoordinateOffset(mainAnchorLatLng.latitude, mainAnchorLatLng.longitude, floorPlan.mainAnchor.compassHeading, it.x, it.z)
            map.addMarker(MarkerOptions().position(pointLatLng).icon(BitmapDescriptorFactory.fromBitmap(pointIcon)))
        }
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds.build(), padding))
        if (map.cameraPosition.zoom > 20) {
            map.moveCamera(CameraUpdateFactory.zoomTo(20f))
        }
    }

    fun hideKeyboard(activity: Activity) {
        CoroutineScope(Dispatchers.Main).run {
            val inputMethodManager = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(activity.currentFocus?.windowToken, 0)
        }
    }
}