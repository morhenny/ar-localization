package de.morhenn.ar_localization.floorPlan

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.transition.Slide
import android.transition.TransitionManager
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.ar.AugmentedRealityViewModel
import de.morhenn.ar_localization.databinding.DialogNewFloorPlanBinding
import de.morhenn.ar_localization.databinding.FragmentFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.utils.DataExport
import de.morhenn.ar_localization.utils.Utils
import de.morhenn.ar_localization.utils.Utils.showFloorPlanOnMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloorPlanListFragment : Fragment(), OnMapReadyCallback {

    //viewBinding
    private var _binding: FragmentFloorPlanListBinding? = null
    private val binding get() = _binding!!

    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)
    private val viewModelAr: AugmentedRealityViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private lateinit var listAdapter: FloorPlanListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager

    private var currentFloorPlans = listOf<FloorPlan>()

    private var map: GoogleMap? = null

    private var waitingOnInitialMapLoad = true
    private var sortByLocation = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var requestingLocationUpdates = false
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_REQUEST_INTERVAL).build()
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { newLocation ->
                listAdapter.currentLocation?.let { lastLocation ->
                    if (lastLocation.distanceTo(newLocation) > 1f) {
                        if (sortByLocation) {
                            viewModelFloorPlan.sortListByNewLocation(newLocation)
                        }
                        listAdapter.updateCurrentLocation(newLocation)
                    }
                } ?: run {
                    if (sortByLocation) viewModelFloorPlan.sortListByNewLocation(newLocation)
                    listAdapter.updateCurrentLocation(newLocation)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFloorPlanListBinding.inflate(inflater, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.floorPlanList
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        listAdapter = FloorPlanListAdapter(currentFloorPlans, ::showDialogToDelete, ::showDialogToUpdate, ::localizeSelectedFloorPlan, ::onSelectedFloorPlanChanged)

        viewModelFloorPlan.floorPlans.observe(viewLifecycleOwner) {
            currentFloorPlans = it
            listAdapter.updateFloorPlans(it)
        }
        recyclerView.adapter = listAdapter
        recyclerView.itemAnimator?.changeDuration = 0

        requestLocationPermission()

        binding.fabFloorPlanList.setOnClickListener {
            showDialogToCreate()
        }

        initializeMenu()
    }

    private fun initializeMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.floor_plan_list_menu, menu)
                val searchView = menu.findItem(R.id.floor_plan_list_search).actionView as SearchView
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        viewModelFloorPlan.filterList(query)
                        Utils.hideKeyboard(requireActivity())
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        viewModelFloorPlan.filterList(newText)
                        return true
                    }
                })
                menu.findItem(R.id.setting_enable_logging).isChecked = true
                menu.findItem(R.id.sort_floor_plans_by_name).isChecked = true
                sortByLocation = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.sort_floor_plans_by_name -> {
                        menuItem.isChecked = true
                        viewModelFloorPlan.lastLocation = null
                        viewModelFloorPlan.refreshFloorPlanList()
                        listAdapter.resetExpanded()
                        sortByLocation = false
                    }
                    R.id.sort_floor_plans_by_distance -> {
                        menuItem.isChecked = true
                        listAdapter.currentLocation?.let { viewModelFloorPlan.sortListByNewLocation(it) }
                        listAdapter.resetExpanded()
                        sortByLocation = true
                    }
                    R.id.setting_enable_logging -> {
                        menuItem.isChecked = !menuItem.isChecked
                        DataExport.loggingEnabled = menuItem.isChecked
                    }
                    R.id.setting_start_anchor_tracking_test -> {
                        findNavController().navigate(R.id.action_floorPlanListFragment_to_anchorTrackingTestFragment)
                    }
                    else -> {}
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    @SuppressLint("MissingPermission") //due to android studio lint bug
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            isMyLocationEnabled = true
            isIndoorEnabled = true
            mapType = GoogleMap.MAP_TYPE_NORMAL
            setOnMapLoadedCallback {
                if (waitingOnInitialMapLoad) {
                    if (listAdapter.expandedPosition != -1)
                        showFloorPlanOnMap(currentFloorPlans[listAdapter.expandedPosition], this, requireContext(), 200)
                    waitingOnInitialMapLoad = false
                }
            }
            setOnMarkerClickListener {
                true //consume all click events on markers and ignore them
            }
        }
    }

    private fun onSelectedFloorPlanChanged() {
        Utils.hideKeyboard(requireActivity())
        map?.let { map ->
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
                        showFloorPlanOnMap(currentFloorPlans[pos], map, requireContext(), 200)
                    }
                } else {
                    showFloorPlanOnMap(currentFloorPlans[pos], map, requireContext(), 200)
                }
            }
        }
    }

    private fun showDialogToDelete(floorPlan: FloorPlan) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.dialog_delete_floor_plan_title))
        builder.setMessage(getString(R.string.dialog_delete_floor_plan_message))
        builder.setPositiveButton(R.string.dialog_button_confirm) { _, _ ->
            viewModelFloorPlan.removeFloorPlan(floorPlan)
        }
        builder.setNeutralButton(R.string.dialog_button_cancel) { _, _ ->
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun localizeSelectedFloorPlan(floorPlan: FloorPlan) {
        viewModelAr.floorPlan = floorPlan
        findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToArLocalizingFragment())
    }

    private fun showDialogToUpdate(editFloorPlan: FloorPlan) {
        val dialogBinding = DialogNewFloorPlanBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()

        with(dialogBinding) {
            dialogNewFloorPlanTitle.text = getText(R.string.update_floor_plan_dialog_title)
            dialogNewFloorPlanHint.text = getText(R.string.update_floor_plan_dialog_hint)
            dialogNewAnchorInputName.setText(editFloorPlan.name)
            dialogNewAnchorInputInfo.setText(editFloorPlan.info)
            dialogNewAnchorInputName.requestFocus()
        }

        dialogBinding.dialogNewFloorPlanButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputName.text.toString().isNotEmpty()) {
                editFloorPlan.name = dialogBinding.dialogNewAnchorInputName.text.toString()
                editFloorPlan.info = dialogBinding.dialogNewAnchorInputInfo.text.toString()

                viewModelFloorPlan.updateFloorPlan(editFloorPlan)
                listAdapter.notifyItemChanged(listAdapter.expandedPosition)
                dialog.dismiss()
            } else {
                dialogBinding.dialogNewFloorPlanInputNameLayout.error = getString(R.string.dialog_new_floor_plan_error)
            }
        }
        dialogBinding.dialogNewFloorPlanButtonCancel.setOnClickListener {
            dialog.cancel()
        }
    }

    private fun showDialogToCreate() {
        val user = Firebase.auth.currentUser

        val dialogBinding = DialogNewFloorPlanBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()

        dialogBinding.dialogNewAnchorInputName.requestFocus()

        var navigateFromDialog = false
        dialogBinding.dialogNewFloorPlanButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputName.text.toString().isNotEmpty()) {
                viewModelFloorPlan.nameForNewFloorPlan = dialogBinding.dialogNewAnchorInputName.text.toString()
                viewModelFloorPlan.infoForNewFloorPlan = dialogBinding.dialogNewAnchorInputInfo.text.toString()
                viewModelFloorPlan.ownerUID = user?.uid ?: ""
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
                    findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToArMappingFragment())
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                (childFragmentManager.findFragmentById(R.id.floor_plan_map) as SupportMapFragment).getMapAsync(this)
                startLocationUpdates()
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
                startLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        requestingLocationUpdates = true
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        map?.isIndoorEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) {
            startLocationUpdates()
        }
        map?.isIndoorEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map?.clear()
        map = null
        _binding = null
    }

    companion object {
        private const val TAG = "FloorPlanListFragment"
        private const val LOCATION_REQUEST_INTERVAL = 1000L
    }
}