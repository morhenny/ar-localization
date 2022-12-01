package de.morhenn.ar_localization.floorPlan

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.transition.Slide
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.DialogNewFloorPlanBinding
import de.morhenn.ar_localization.databinding.FragmentFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.utils.GeoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloorPlanListFragment : Fragment(), OnMapReadyCallback {

    //viewBinding
    private var _binding: FragmentFloorPlanListBinding? = null
    private val binding get() = _binding!!

    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)
    private lateinit var listAdapter: FloorPlanListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager

    private var currentFloorPlans = listOf<FloorPlan>()

    private var map: GoogleMap? = null

    private var waitingOnInitialMapLoad = true


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFloorPlanListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.floorPlanList
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        listAdapter = FloorPlanListAdapter()

        viewModelFloorPlan.floorPlans.observe(viewLifecycleOwner) {
            listAdapter.submitList(it)
            currentFloorPlans = it
        }
        recyclerView.adapter = listAdapter

        requestLocationPermission()

        binding.fabFloorPlanList.setOnClickListener {
            showDialogToCreate()
        }

        listAdapter.deleteSelectedFloorPlan.observe(viewLifecycleOwner) {
            viewModelFloorPlan.removeFloorPlan(currentFloorPlans[listAdapter.expandedPosition])
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isMyLocationButtonEnabled = false
            isIndoorEnabled = true
            mapType = GoogleMap.MAP_TYPE_NORMAL
            setOnMapLoadedCallback {
                if (waitingOnInitialMapLoad) {
                    showFloorPlanOnMap(currentFloorPlans[listAdapter.expandedPosition])
                    waitingOnInitialMapLoad = false
                }
            }
            setOnMarkerClickListener {
                true //consume all click events on markers and ignore them
            }
        }

        listAdapter.selectedFloorPlanChanged.observe(viewLifecycleOwner) {
            it.hasBeenHandled
            val pos = listAdapter.expandedPosition
            val transition = Slide()
            transition.duration = 350
            transition.addTarget(binding.floorPlanMap)
            if (pos < 0) {
                TransitionManager.beginDelayedTransition(binding.root, transition)
                binding.floorPlanMap.visibility = View.GONE
            } else {
                if (binding.floorPlanMap.visibility == View.GONE) {
                    TransitionManager.beginDelayedTransition(binding.root, transition)
                    binding.floorPlanMap.visibility = View.VISIBLE
                    layoutManager.scrollToPositionWithOffset(pos, 150)
                    if (!waitingOnInitialMapLoad) {
                        showFloorPlanOnMap(currentFloorPlans[listAdapter.expandedPosition])
                    }
                } else {
                    showFloorPlanOnMap(currentFloorPlans[listAdapter.expandedPosition])
                }
            }
        }
    }

    private fun showFloorPlanOnMap(floorPlan: FloorPlan) {
        map?.let { map ->
            val mainAnchorLatLng = LatLng(floorPlan.mainAnchor.lat, floorPlan.mainAnchor.lng)
            map.clear()
            val latLngBounds = LatLngBounds.Builder()
            latLngBounds.include(mainAnchorLatLng)

            val mainAnchorIcon = getBitmapFromVectorDrawable(R.drawable.ic_outline_flag_circle_24)
            val trackingAnchorIcon = getBitmapFromVectorDrawable(R.drawable.ic_outline_flag_24)
            map.addMarker(MarkerOptions().position(mainAnchorLatLng).icon(BitmapDescriptorFactory.fromBitmap(mainAnchorIcon)))
            floorPlan.cloudAnchorList.forEach {
                map.addMarker(MarkerOptions().position(LatLng(it.lat, it.lng)).icon(BitmapDescriptorFactory.fromBitmap(trackingAnchorIcon)))
                latLngBounds.include(LatLng(it.lat, it.lng))
            }
            var lastPos = mainAnchorLatLng

            val pointIcon = getBitmapFromVectorDrawable(R.drawable.ic_baseline_blue_dot_6)
            floorPlan.mappingPointList.forEach {
                val pointLatLng = GeoUtils.getLatLngByLocalCoordinateOffset(mainAnchorLatLng.latitude, mainAnchorLatLng.longitude, floorPlan.mainAnchor.compassHeading, it.x, it.z)
                map.addMarker(MarkerOptions().position(pointLatLng).icon(BitmapDescriptorFactory.fromBitmap(pointIcon)))
                lastPos = pointLatLng
            }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds.build(), 250))
        }
    }

    private fun showDialogToCreate() {
        val dialogBinding = DialogNewFloorPlanBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.show()

        var navigateFromDialog = false
        dialogBinding.dialogNewFloorPlanButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputName.text.toString().isNotEmpty()) {
                viewModelFloorPlan.nameForNewFloorPlan = dialogBinding.dialogNewAnchorInputName.text.toString()
                viewModelFloorPlan.infoForNewFloorPlan = dialogBinding.dialogNewAnchorInputInfo.text.toString()
                navigateFromDialog = true
                dialog.dismiss()
            } else {
                dialogBinding.dialogNewFloorPlanInputNameLayout.error = getString(R.string.dialog_new_floor_plan_error)
            }
        }
        dialogBinding.dialogNewFloorPlanButtonCancel.setOnClickListener {
            navigateFromDialog = false
            dialog.cancel()
        }
        dialog.setOnDismissListener {
            if (navigateFromDialog) {
                lifecycleScope.launch {
                    delay(40) //needed due to map lagging if navigated with open dialog
                    findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToAugmentedRealityFragment())
                }
            }
        }
    }

    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun requestLocationPermission() {
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                (childFragmentManager.findFragmentById(R.id.floor_plan_map) as SupportMapFragment).getMapAsync(this)
            } else {
                Toast.makeText(requireContext(), "The app requires location permission to function, please enable them", Toast.LENGTH_LONG).show()
            }
        }
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(requireContext(), "The app requires location permission to function, please enable them", Toast.LENGTH_LONG).show()
            }
            else -> {
                (childFragmentManager.findFragmentById(R.id.floor_plan_map) as SupportMapFragment).getMapAsync(this)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map?.clear()
        map = null
        _binding = null
    }
}