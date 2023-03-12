package de.morhenn.ar_localization.floorPlan

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.morhenn.ar_localization.firebase.FirebaseFloorPlanService
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.MappingPoint
import kotlinx.coroutines.launch

class FloorPlanViewModel : ViewModel() {

    var nameForNewFloorPlan = ""
    var infoForNewFloorPlan = ""
    var ownerUID = ""

    private var currentFilter = ""
    private var unfilteredList = listOf<FloorPlan>()

    var lastLocation: Location? = null

    private val _floorPlans = MutableLiveData<List<FloorPlan>>()
    val floorPlans: LiveData<List<FloorPlan>>
        get() = _floorPlans

    init {
        viewModelScope.launch {
            FirebaseFloorPlanService.registerForFloorPlanUpdates().collect {
                unfilteredList = it
                if (currentFilter.isNotEmpty()) {
                    filterList(currentFilter)
                }
                lastLocation?.let { location ->
                    sortListByNewLocation(location)
                } ?: run {
                    _floorPlans.value = it
                }
            }
        }
    }

    fun sortListByNewLocation(location: Location) {
        lastLocation = location
        val sortedList = _floorPlans.value?.sortedBy {
            val floorPlanLoc = Location("").apply {
                latitude = it.mainAnchor.lat
                longitude = it.mainAnchor.lng
            }
            floorPlanLoc.distanceTo(location)
        }
        sortedList?.let {
            _floorPlans.value = it
        }
    }

    fun filterList(query: String?) {
        currentFilter = query ?: ""
        if (currentFilter.isNotEmpty()) {
            val filteredList = unfilteredList.filter { floorPlan ->
                floorPlan.name.contains(currentFilter, true) || floorPlan.info.contains(currentFilter, true)
            }
            _floorPlans.value = filteredList
        } else {
            _floorPlans.value = unfilteredList
        }
    }

    fun refreshFloorPlanList() {
        viewModelScope.launch {
            _floorPlans.value = FirebaseFloorPlanService.getFloorPlanListByName()
        }
    }

    fun addFloorPlan(floorPlan: FloorPlan) {
        FirebaseFloorPlanService.addFloorPlan(floorPlan)
    }

    fun removeFloorPlan(floorPlan: FloorPlan) {
        FirebaseFloorPlanService.deleteFloorPlan(floorPlan)
    }

    fun updateFloorPlan(editFloorPlan: FloorPlan) {
        FirebaseFloorPlanService.updateFloorPlan(editFloorPlan)
    }

    private fun loadDebugFloorPlan() {
        val mainCloudAnchor = CloudAnchor("main", 0, "ua-fc9d89819f61fa990a619445d3248def", 52.5123785, 13.3262760, 70.318, 157.51)
        val floorPlan = FloorPlan(
            "Debug Floor Plan",
            "TU Seiteneingang zu Raum 0112",
            "",
            mainCloudAnchor,
            mutableListOf(
                MappingPoint(0.8f, 0f, -0.9f),
                MappingPoint(3.16f, 0f, -4.1f),
                MappingPoint(4.347f, 0f, -6.44f),
                MappingPoint(5.35f, 0f, -8.75f),
                MappingPoint(6.97f, 0f, -12.80f),
                MappingPoint(8.76f, 0f, -16.55f),
                MappingPoint(10.596f, 0f, -21.11f),
                MappingPoint(13.20f, 0f, -20.346f),
                MappingPoint(16.467f, 0f, -19.152f),
                MappingPoint(19.588f, 0f, -18.082f),
                MappingPoint(24.010f, 0f, -16.544f)
            ),
            mutableListOf(CloudAnchor("first", 0, "noRealID", 52.5120557, 13.325830, 70.318, 157.51))
        )
        addFloorPlan(floorPlan)
    }
}