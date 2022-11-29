package de.morhenn.ar_localization.floorPlan

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
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

    private var map: GoogleMap? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFloorPlanListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.floorPlanList
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        listAdapter = FloorPlanListAdapter(viewModelFloorPlan.floorPlanList, viewModelFloorPlan)
        binding.floorPlanList.adapter = listAdapter

        requestLocationPermission()

        binding.fabFloorPlanList.setOnClickListener {
            showDialogToCreate()
        }
    }

    private fun showFloorPlanOnMap(floorPlan: FloorPlan) {
        map?.let { map ->
            val mainAnchorLatLng = LatLng(floorPlan.mainAnchor.lat, floorPlan.mainAnchor.lng)
            map.clear()
            val latLngBounds = LatLngBounds.Builder()
            latLngBounds.include(mainAnchorLatLng)

            map.addMarker(MarkerOptions().position(mainAnchorLatLng).title("X"))
            floorPlan.cloudAnchorList.forEach {
                map.addMarker(MarkerOptions().position(LatLng(it.lat, it.lng)))
                latLngBounds.include(LatLng(it.lat, it.lng))
            }
            var lastPos = mainAnchorLatLng
            floorPlan.mappingPointList.forEach {
                val pointLatLng = GeoUtils.getLatLngByLocalCoordinateOffset(mainAnchorLatLng.latitude, mainAnchorLatLng.longitude, floorPlan.mainAnchor.compassHeading, it.x, it.z)
                map.addPolyline(PolylineOptions()
                    .clickable(false)
                    .add(lastPos)
                    .add(pointLatLng))
                lastPos = pointLatLng
            }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds.build(), 250))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isMyLocationButtonEnabled = false
            isIndoorEnabled = true
            mapType = GoogleMap.MAP_TYPE_NORMAL
        }

        listAdapter.selectedFloorPlanChanged.observe(viewLifecycleOwner) {
            it.hasBeenHandled
            val pos = listAdapter.expandedPosition
            if (pos < 0) {
                binding.floorPlanMap.visibility = View.GONE
            } else {
                binding.floorPlanMap.visibility = View.VISIBLE
                layoutManager.scrollToPositionWithOffset(pos, 150)
                lifecycleScope.launch {
                    delay(1) //wait for the map to be ready
                    showFloorPlanOnMap(viewModelFloorPlan.floorPlanList[pos])
                }
            }
        }
    }

    private fun showDialogToCreate() {
        val dialogBinding = DialogNewFloorPlanBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.show()
        dialogBinding.dialogNewFloorPlanButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputName.text.toString().isNotEmpty()) {
                viewModelFloorPlan.nameForNewFloorPlan = dialogBinding.dialogNewAnchorInputName.text.toString()
                viewModelFloorPlan.infoForNewFloorPlan = dialogBinding.dialogNewAnchorInputInfo.text.toString()
                dialog.dismiss()
                lifecycleScope.launch {
                    delay(1) //This is necessary, due to a bug causing the map to lag after navigating while a dialog is still open
                    findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToAugmentedRealityFragment())
                }
            } else {
                dialogBinding.dialogNewFloorPlanInputNameLayout.error = getString(R.string.dialog_new_floor_plan_error)
            }
        }
        dialogBinding.dialogNewFloorPlanButtonCancel.setOnClickListener {
            dialog.cancel()
        }
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