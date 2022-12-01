package de.morhenn.ar_localization.floorPlan

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

    private val _floorPlans = MutableLiveData<List<FloorPlan>>()
    val floorPlans: LiveData<List<FloorPlan>>
        get() = _floorPlans

    init {
        viewModelScope.launch {
            FirebaseFloorPlanService.registerForFloorPlanUpdates().collect {
                _floorPlans.value = it
            }
        }
    }

    //TODO might not even be needed, due to updateListener
    fun refreshFloorPlanList() {
        viewModelScope.launch {
            _floorPlans.value = FirebaseFloorPlanService.getFloorPlanList()
        }
    }

    fun addFloorPlan(floorPlan: FloorPlan) {
        FirebaseFloorPlanService.addFloorPlan(floorPlan)
    }

    fun removeFloorPlan(floorPlan: FloorPlan) {
        FirebaseFloorPlanService.deleteFloorPlan(floorPlan)
    }

    private fun loadDebugFloorPlan() {
        val mainCloudAnchor = CloudAnchor("main", "ua-fc9d89819f61fa990a619445d3248def", 52.5123785, 13.3262760, 70.318, 157.51)
        val floorPlan = FloorPlan(
            "Debug Floor Plan",
            "TU Seiteneingang zu Raum 0112",
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
                MappingPoint(24.010f, 0f, -16.544f)),
            mutableListOf(CloudAnchor("first", "noRealID", 52.5120557, 13.325830, 70.318, 157.51))
        )
        addFloorPlan(floorPlan)
    }
}