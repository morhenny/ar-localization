package de.morhenn.ar_localization.ar.localizing

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ResourceManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.ar.ArLocalizingStates
import de.morhenn.ar_localization.ar.ArLocalizingStates.*
import de.morhenn.ar_localization.ar.AugmentedRealityViewModel
import de.morhenn.ar_localization.ar.ModelName
import de.morhenn.ar_localization.ar.ModelName.*
import de.morhenn.ar_localization.databinding.FragmentArLocalizingBinding
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.GeoPose
import de.morhenn.ar_localization.utils.GeoUtils
import de.morhenn.ar_localization.utils.Utils
import de.morhenn.ar_localization.utils.Utils.showFloorPlanOnMap
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
import io.github.sceneview.model.await
import java.util.*

class ArLocalizingFragment : Fragment(), OnMapReadyCallback {

    //viewBinding
    private var _binding: FragmentArLocalizingBinding? = null
    private val binding get() = _binding!!

    private lateinit var sceneView: ArSceneView

    private var map: GoogleMap? = null
    private var earth: Earth? = null

    private val viewModelAr: AugmentedRealityViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private var modelMap: EnumMap<ModelName, Renderable> = EnumMap(ModelName::class.java)

    private lateinit var floorPlan: FloorPlan
    private var filteredCloudAnchorList = listOf<CloudAnchor>()
    private var currentFilter = ""

    private lateinit var anchorRecyclerView: RecyclerView
    private lateinit var anchorListAdapter: CloudAnchorListAdapter

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    private var arState: ArLocalizingStates = NOT_INITIALIZED

    private val earthNodeList = mutableListOf<ArNode>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArLocalizingBinding.inflate(inflater, container, false)

        viewModelAr.floorPlan?.let {
            floorPlan = it
            filteredCloudAnchorList = it.cloudAnchorList
        } ?: run {
            Log.e(TAG, "No floor plan found in arLocalizing, something went really wrong")
            findNavController().popBackStack()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenCreated {
            //Loads all used models into the modelMap
            loadModels()
        }

        sceneView = binding.sceneViewLocalizing
        sceneView.lightEstimationMode = LightEstimationMode.AMBIENT_INTENSITY

        sceneView.onArSessionCreated = {
            sceneView.configureSession { _, config ->
                config.planeFindingEnabled = false
                config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                config.geospatialMode = Config.GeospatialMode.ENABLED
            }
        }

        initializeUIElements()

        (childFragmentManager.findFragmentById(R.id.ar_localizing_bottom_sheet_map) as SupportMapFragment).getMapAsync(this)

        sceneView.onArFrame = { frame ->
            onArFrame(frame)
        }
    }

    private fun onArFrame(frame: ArFrame) {
        earth?.let {
            if (it.trackingState == TrackingState.TRACKING) {
                onArFrameWithEarthTracking(it)
            }
        } ?: run {
            earth = sceneView.arSession?.earth
            Log.d(TAG, "Geospatial API initialized and earth object assigned")
        }
    }

    private fun onArFrameWithEarthTracking(earth: Earth) {
        val cameraGeoPose = earth.cameraGeospatialPose
        binding.arLocalizingGeospatialAccVie.updateView(cameraGeoPose)

        if (cameraGeoPose.horizontalAccuracy < MIN_HORIZONTAL_ACCURACY) {
            showCloudAnchorsAsGeospatial(true)

            if (arState == NOT_INITIALIZED) {
                updateState(RESOLVING_FROM_GEOSPATIAL)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                resolveAnyOfClosestCloudAnchors()
            }
        }
    }

    private fun initializeUIElements() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.arLocalizingBottomSheet)
        requireActivity().window.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_PAN)

        val collapsedHeight = binding.arLocalizingBottomSheetMap.layoutParams.height
        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.arLocalizingBottomSheetTitle.text = when (arState) {
                            TRACKING -> getString(R.string.ar_localizing_bottom_sheet_title_tracking)
                            RESOLVING_FROM_SELECTED, RESOLVING_FROM_GEOSPATIAL -> getString(R.string.ar_localizing_bottom_sheet_title_resolving)
                            else -> getString(R.string.ar_localizing_bottom_sheet_title_collapsed)
                        }
                        binding.arLocalizingBottomSheetMap.layoutParams.height = collapsedHeight
                        binding.arLocalizingBottomSheetCloseButton.visibility = View.GONE
                        map?.uiSettings?.apply {
                            isZoomControlsEnabled = false
                            isIndoorLevelPickerEnabled = false
                        }
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.arLocalizingBottomSheetTitle.text = getString(R.string.ar_localizing_bottom_sheet_title_expanded)
                        binding.arLocalizingBottomSheetMap.layoutParams.height = (bottomSheet.height / 2) - 25
                        binding.arLocalizingBottomSheetCloseButton.visibility = View.VISIBLE
                        map?.uiSettings?.apply {
                            isZoomControlsEnabled = true
                            isIndoorLevelPickerEnabled = true
                        }
                    }
                    else -> {} //NO-OP
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                //TODO
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior.saveFlags = BottomSheetBehavior.SAVE_ALL

        anchorRecyclerView = binding.arLocalizingBottomSheetAnchorList
        anchorRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        anchorListAdapter = CloudAnchorListAdapter(::onClickCloudAnchorItem).apply {
            submitList(filteredCloudAnchorList)
        }
        anchorRecyclerView.adapter = anchorListAdapter

        binding.arLocalizingBottomSheetCloseButton.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding.arLocalizingBottomSheetSearchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterCloudAnchorList(query)
                Utils.hideKeyboard(requireActivity())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterCloudAnchorList(newText)
                return true
            }
        })
        binding.arLocalizingBottomSheetSearchViewSortButton.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.cloud_anchor_sort_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_by_floor -> {
                        anchorListAdapter.submitList(filteredCloudAnchorList.sortedBy { it.floor })
                        true
                    }
                    R.id.sort_by_name -> {
                        anchorListAdapter.submitList(filteredCloudAnchorList.sortedBy { it.text })
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun filterCloudAnchorList(query: String?) {
        currentFilter = query ?: ""
        filteredCloudAnchorList = if (currentFilter.isNotEmpty()) {
            floorPlan.cloudAnchorList.filter {
                it.text.contains(currentFilter, true)
            }
        } else {
            floorPlan.cloudAnchorList
        }
        anchorListAdapter.submitList(filteredCloudAnchorList)
    }

    @SuppressLint("MissingPermission") //due to android studio lint bug
    override fun onMapReady(map: GoogleMap) {
        this.map = map.apply {
            isMyLocationEnabled = true
            isIndoorEnabled = true
            mapType = GoogleMap.MAP_TYPE_NORMAL
            with(uiSettings) {
                isIndoorLevelPickerEnabled = false
                isMyLocationButtonEnabled = false
                isScrollGesturesEnabled = false
                isZoomControlsEnabled = false
                isZoomGesturesEnabled = false
            }

            setOnMapLoadedCallback {
                showFloorPlanOnMap(floorPlan, this, requireContext())
            }
            setOnMarkerClickListener {
                if (!it.title.isNullOrBlank()) {
                    var cloudAnchor = floorPlan.cloudAnchorList.find { cloudAnchor -> cloudAnchor.cloudAnchorId == it.title }
                    if (floorPlan.mainAnchor.cloudAnchorId == it.title) cloudAnchor = floorPlan.mainAnchor
                    cloudAnchor?.let { anchor ->
                        onClickCloudAnchorItem(anchor)
                    }
                }
                true
            }
        }
    }

    private fun showCloudAnchorsAsGeospatial(shouldShow: Boolean) {
        if (shouldShow && earthNodeList.isEmpty()) {
            val mainEarthAnchor = earth!!.createAnchor(floorPlan.mainAnchor.lat, floorPlan.mainAnchor.lng, floorPlan.mainAnchor.alt, 0f, 0f, 0f, 1f)
            val mainEarthNode = ArNode().apply {
                anchor = mainEarthAnchor
                parent = sceneView
                setModel(modelMap[GEO_ANCHOR])
            }
            val mainEarthNodeArrow = ArNode().apply {
                position = Position(0f, 2f, 0f)
                parent = mainEarthNode
                setModel(modelMap[GEO_ANCHOR_ARROW])
            }
            earthNodeList.add(mainEarthNode)
            earthNodeList.add(mainEarthNodeArrow)

            getClosestCloudAnchorIfTooMany().forEach {
                //TODO smaller indicators for these anchors
                val cloudAnchor = earth!!.createAnchor(it.lat, it.lng, it.alt, 0f, 0f, 0f, 1f)
                val cloudAnchorNode = ArNode().apply {
                    anchor = cloudAnchor
                    parent = sceneView
                    setModel(modelMap[GEO_ANCHOR])
                }
                val cloudAnchorNodeArrow = ArNode().apply {
                    position = Position(0f, 2f, 0f)
                    parent = cloudAnchorNode
                    setModel(modelMap[GEO_ANCHOR_ARROW])
                }
                earthNodeList.add(cloudAnchorNode)
                earthNodeList.add(cloudAnchorNodeArrow)
            }
        } else if (!shouldShow) {
            Log.d("O_O", "Removing earth nodes")
            earthNodeList.forEach {
                it.detachAnchor()
                it.parent = null
                it.destroy()
            }
            earthNodeList.clear()
        }
    }

    private fun resolveAnyOfClosestCloudAnchors(geoPose: GeoPose? = null, floor: Int? = null) {
        val listOfAnchors = if (floor != null) {
            getAnchorsOnFloor(floor)
        } else {
            getClosestCloudAnchorIfTooMany(geoPose)
        }
        val listOfAnchorIds = mutableListOf<String>()
        listOfAnchors.forEach { listOfAnchorIds.add(it.cloudAnchorId) }

        val cloudAnchorNode = ArModelNode().apply {
            position = Position(0f, 0f, 0f)
            parent = sceneView
            resolveCloudAnchorFromIdList(listOfAnchorIds) { anchor: Anchor, success: Boolean ->
                binding.arLocalizingProgressBar.visibility = View.INVISIBLE
                if (success) {
                    showCloudAnchorsAsGeospatial(false)
                    this.anchor = anchor
                    setModel(modelMap[AXIS])
                    updateState(TRACKING)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    if (arState != TRACKING) {
                        updateState(NOT_INITIALIZED)
                    }
                }
            }
        }
    }

    private fun getAnchorsOnFloor(floor: Int): List<CloudAnchor> {
        val listOfAnchors = mutableListOf<CloudAnchor>()
        floorPlan.cloudAnchorList.forEach {
            if (it.floor == floor) {
                listOfAnchors.add(it)
            }
        }
        return listOfAnchors

    }

    //Get list
    private fun getClosestCloudAnchorIfTooMany(geoPose: GeoPose? = null): List<CloudAnchor> {
        val cloudAnchorList = mutableListOf<CloudAnchor>()
        cloudAnchorList.add(floorPlan.mainAnchor)

        val lat: Double
        val lng: Double
        val alt: Double
        if (geoPose != null) {
            lat = geoPose.latitude
            lng = geoPose.longitude
            alt = geoPose.altitude
        } else {
            val cameraGeoPose = earth!!.cameraGeospatialPose
            lat = cameraGeoPose.latitude
            lng = cameraGeoPose.longitude
            alt = cameraGeoPose.altitude
        }

        val tempCloudAnchorList = floorPlan.cloudAnchorList

        while (tempCloudAnchorList.size > MAX_SIMULTANEOUS_ANCHORS - 1) {
            tempCloudAnchorList.remove(tempCloudAnchorList.maxBy {
                GeoUtils.distanceBetweenTwoWorldCoordinates(lat, lng, alt, it.lat, it.lng, it.alt)
            })
        }

        cloudAnchorList.addAll(tempCloudAnchorList)

        return cloudAnchorList
    }

    private fun onClickCloudAnchorItem(cloudAnchor: CloudAnchor) {
        Log.d(TAG, "Clicked on cloud anchor: $cloudAnchor")
        if (arState == NOT_INITIALIZED) {
            updateState(RESOLVING_FROM_SELECTED)
            resolveAnyOfClosestCloudAnchors(GeoPose(cloudAnchor.lat, cloudAnchor.lng, cloudAnchor.alt, 0.0))
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun onClickFloorItem(floor: Int) {
        Log.d(TAG, "Clicked on floor: $floor")
        map?.let { map ->
            map.focusedBuilding?.let { building ->
                building.levels.find { it.shortName == floor.toString() }?.activate() ?: run {
                    when (floor) {
                        0 -> building.levels.find { it.name == "EG" }?.activate()
                        1 -> building.levels.find { it.name == "OG" }?.activate()
                        -1 -> building.levels.find { it.name == "UG" }?.activate()
                        else -> {
                            Log.d(TAG, "No floor found for #$floor")
                        }
                    }
                }
            }
        }
        if (arState == NOT_INITIALIZED) {
            updateState(RESOLVING_FROM_SELECTED)
            resolveAnyOfClosestCloudAnchors(floor = floor)
        }
    }

    private fun updateState(newState: ArLocalizingStates) {
        arState = newState
        binding.arLocalizingProgressBar.visibility = arState.progressBarVisibility
    }

    private suspend fun loadModels() {
        modelMap[DEBUG_CUBE] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/cube.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[AXIS] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/axis.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[BALL] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/icoSphere.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[GEO_ANCHOR] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/geoAnchor.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[GEO_ANCHOR_ARROW] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/geoAnchorArrow.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map?.clear()
        map = null
        _binding = null
    }

    override fun onDestroy() {
        //TODO this is only needed in 0.6.0, since it is in sceneView for newer versions
        ResourceManager.getInstance().destroyAllResources()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArLocalizingFragment"

        private const val MIN_HORIZONTAL_ACCURACY = 2.0
        private const val MAX_SIMULTANEOUS_ANCHORS = 20
    }
}