package de.morhenn.ar_localization.model

import kotlinx.serialization.Serializable

@Serializable
data class FloorPlan(
    var name: String,
    var info: String,
    //TODO author / owner / permissions
    var mainAnchor: CloudAnchor,
    var mappingPointList: MutableList<MappingPoint>,
    var cloudAnchorList: MutableList<CloudAnchor>,
) {
    constructor(mainAnchor: CloudAnchor) : this("", "", mainAnchor, mutableListOf(), mutableListOf())
}
