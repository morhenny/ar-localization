package de.morhenn.ar_localization.model

data class FloorPlan(
    var name: String = "",
    var info: String = "",
    //TODO author / owner / permissions
    var mainAnchor: CloudAnchor = CloudAnchor(),
    var mappingPointList: MutableList<MappingPoint> = mutableListOf(),
    var cloudAnchorList: MutableList<CloudAnchor> = mutableListOf(),
) {
    constructor(mainAnchor: CloudAnchor) : this("", "", mainAnchor, mutableListOf(), mutableListOf())
}
