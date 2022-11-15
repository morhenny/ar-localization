package de.morhenn.ar_localization.viewmodel

import androidx.lifecycle.ViewModel
import de.morhenn.ar_localization.adapter.FloorPlanListAdapter
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.MappingPoint
import de.morhenn.ar_localization.model.SerializableQuaternion

class FloorPlanViewModel : ViewModel() {

    val floorPlanList: ArrayList<FloorPlan> = ArrayList()
    val listAdapter = FloorPlanListAdapter(floorPlanList)

    init {
        loadDebugFloorPlan()
    }

    private fun loadDebugFloorPlan() {
        val mainCloudAnchor = CloudAnchor("main", "noRealID", 0.0, 0.0, 0.0, 0.0)
        val mappingPoint = MappingPoint(1f, 1f, 0f, SerializableQuaternion())
        val floorPlan = FloorPlan(
            "Debug Floor Plan",
            "This is a debug floor plan. It is used for testing purposes only.",
            mainCloudAnchor,
            listOf(mappingPoint),
            listOf(mainCloudAnchor)
        )
        floorPlanList.add(0, floorPlan)
    }
}