package de.morhenn.ar_localization.floorPlan

import androidx.lifecycle.ViewModel
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.MappingPoint

class FloorPlanViewModel : ViewModel() {

    val floorPlanList: ArrayList<FloorPlan> = ArrayList()

    var nameForNewFloorPlan = ""
    var infoForNewFloorPlan = ""

    init {
        loadDebugFloorPlan()
    }

    private fun loadDebugFloorPlan() {
        val mainCloudAnchor = CloudAnchor("main", "noRealID", 52.0, 13.0, 80.222, 0.0)
        val floorPlan = FloorPlan(
            "Debug Floor Plan",
            "This is a debug floor plan. It is used for testing purposes only.",
            mainCloudAnchor,
            mutableListOf(MappingPoint(100f, 100f, 100f), MappingPoint(200f, 100f, -200f), MappingPoint(300f, 100f, -300f)),
            mutableListOf(CloudAnchor("first", "noRealID", 52.01, 13.01, 80.0, 0.0))
        )
        floorPlanList.add(floorPlan)
    }
}