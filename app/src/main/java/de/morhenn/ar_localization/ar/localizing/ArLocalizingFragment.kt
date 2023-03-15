package de.morhenn.ar_localization.ar.localizing

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnIndoorStateChangeListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
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
import de.morhenn.ar_localization.ar.ArResolveModes
import de.morhenn.ar_localization.ar.ArResolveModes.*
import de.morhenn.ar_localization.ar.AugmentedRealityViewModel
import de.morhenn.ar_localization.ar.ModelName
import de.morhenn.ar_localization.ar.ModelName.*
import de.morhenn.ar_localization.databinding.DialogSelectFloorBinding
import de.morhenn.ar_localization.databinding.FragmentArLocalizingBinding
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.GeoPose
import de.morhenn.ar_localization.model.MappingPoint
import de.morhenn.ar_localization.utils.DataExport
import de.morhenn.ar_localization.utils.GeoUtils
import de.morhenn.ar_localization.utils.Utils
import de.morhenn.ar_localization.utils.Utils.getBitmapFromVectorDrawable
import de.morhenn.ar_localization.utils.Utils.showFloorPlanOnMap
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3
import io.github.sceneview.model.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ArLocalizingFragment : Fragment(), OnMapReadyCallback {

    //viewBinding
    private var _binding: FragmentArLocalizingBinding? = null
    private val binding get() = _binding!!

    private lateinit var sceneView: ArSceneView

    private var map: GoogleMap? = null
    private var earth: Earth? = null
    private var geospatialEnabled = true
        set(value) {
            if (!value && arState == RESOLVING && resolveMode == GEOSPATIAL) {
                removeCloudAnchorPreviews()
                resolvingArNode?.cancelCloudAnchorResolveTask()
                binding.arLocalizingProgressBar.visibility = View.INVISIBLE
            }
            binding.arLocalizingGeospatialAccView.collapsed = !value
            binding.arLocalizingBottomSheetGeospatialToggleButton.isSelected = value
            field = value
        }

    private val viewModelAr: AugmentedRealityViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private var modelMap: EnumMap<ModelName, Renderable> = EnumMap(ModelName::class.java)

    private lateinit var floorPlan: FloorPlan
    private var filteredCloudAnchorList = mutableListOf<CloudAnchor>()
    private var currentFilter = ""

    private lateinit var anchorRecyclerView: RecyclerView
    private lateinit var anchorListAdapter: CloudAnchorListAdapter

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    private var arState: ArLocalizingStates = NOT_INITIALIZED
    private var resolveMode: ArResolveModes = NONE

    private val earthNodeList = mutableListOf<ArNode>()

    private var currentCloudAnchorNode: ArNode? = null
    private var currentCloudAnchor: CloudAnchor? = null
    private var lastCloudAnchorNode: ArNode? = null
    private var lastCloudAnchor: CloudAnchor? = null

    private var resolvingArNode: ArModelNode? = null

    private var userPose: GeoPose? = null
    private var currentRenderedMappingPoints = mutableListOf<ArNode>()
    private var currentRenderedAnchorPreviews = mutableListOf<ArNode>()
    private val previewAnchorMap = mutableMapOf<CloudAnchor, ArNode>()

    private var navTarget: CloudAnchor? = null
    private var navTargetNode: ArNode? = null
    private val navMappingPoints = mutableListOf<MappingPoint>()
    private val navMappingNodes = mutableListOf<ArNode>()

    private var userPositionMarker: Marker? = null
    private var geospatialPositionMarker: Marker? = null
    private var currentResolvedMapMarker: Marker? = null

    private var lastPositionUpdate = 0L
    private var lastResolveUpdate = 0L
    private var lastUserPoseCalculation = 0L
    private var delayNextPositionUpdate = 500L

    private var maxResolvingAmountWhileTracking = 5
    private var maxResolvingAmountOnSelected = MAX_SIMULTANEOUS_ANCHORS
    private var trackingResolveInterval = INTERVAL_RESOLVE_UPDATE

    private var lastLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var userIcon: BitmapDescriptor
    private lateinit var geospatialIcon: BitmapDescriptor

    private var requestingLocationUpdates = false
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_POSITION_UPDATE).build()
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { newLocation ->
                lastLocation = newLocation
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArLocalizingBinding.inflate(inflater, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                findNavController().popBackStack()
            }
        }

        viewModelAr.floorPlan?.let {
            floorPlan = it
            filteredCloudAnchorList.apply {
                clear()
                add(floorPlan.mainAnchor)
                addAll(floorPlan.cloudAnchorList)
            }
            DataExport.startNewLocalizingFile(floorPlan.name)
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
        sceneView.lightEstimationMode = LightEstimationMode.DISABLED
        sceneView.planeRenderer.isEnabled = false

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
        if (arState == TRACKING || arState == NAVIGATING) {

            val currentMillis = System.currentTimeMillis()
            if (currentMillis - lastResolveUpdate > trackingResolveInterval) {
                lastResolveUpdate = currentMillis
                findNextCloudAnchorAndResolve()
            }
            if (currentMillis - lastUserPoseCalculation > INTERVAL_USER_POSE_CALCULATION) {
                lastUserPoseCalculation = currentMillis
                lifecycleScope.launch {
                    calculateUserPose()
                }
            }
            val timeSinceLastPositionUpdate = currentMillis - lastPositionUpdate
            if (timeSinceLastPositionUpdate > INTERVAL_POSITION_UPDATE && timeSinceLastPositionUpdate > delayNextPositionUpdate) {
                delayNextPositionUpdate = 0L
                userPose?.let { CameraUpdateFactory.newLatLng(it.getLatLng()) }?.let { map?.moveCamera(it) }

                lastPositionUpdate = currentMillis
                lastCloudAnchorNode?.let {
                    lastCloudAnchorNode = null
                    lastCloudAnchor = null
                    it.parent = null
                    it.detachAnchor()
                    it.destroy()
                }
                currentCloudAnchor?.let { cloudAnchor ->
                    userPose?.let {
                        updateRenderedMappingPointsList(it)
                    } ?: run {
                        updateRenderedMappingPointsList(cloudAnchor.getGeoPose())
                    }
                }
            }
        }
    }

    private fun onArFrameWithEarthTracking(earth: Earth) {
        val cameraGeoPose = earth.cameraGeospatialPose
        binding.arLocalizingGeospatialAccView.updateView(cameraGeoPose)

        if (cameraGeoPose.horizontalAccuracy < MIN_HORIZONTAL_ACCURACY) {
            if (arState == NOT_INITIALIZED && geospatialEnabled) {
                showCloudAnchorsAsGeospatial()

                updateState(RESOLVING)
                val geoPose = GeoPose(cameraGeoPose.latitude, cameraGeoPose.longitude, cameraGeoPose.altitude, cameraGeoPose.heading)
                resolveAnyOfClosestCloudAnchors(geoPose)
            }
        }
    }

    private fun calculateUserPose() {
        val calculatingAnchorNode = lastCloudAnchorNode ?: currentCloudAnchorNode
        val calculatingAnchor = lastCloudAnchor ?: currentCloudAnchor

        calculatingAnchorNode?.let { calculatingAnchorNode ->
            calculatingAnchor?.let { calculatingAnchor ->
                val cameraPositionRelativeToCurrentAnchor = calculatingAnchorNode.worldToLocalPosition(sceneView.camera.worldPosition.toVector3()).toFloat3()
                val cameraGeoPoseFromAnchor = GeoUtils.getGeoPoseByLocalCoordinateOffset(calculatingAnchor.getGeoPose(), cameraPositionRelativeToCurrentAnchor)

                val cameraHeadingRelativeToAnchorHeading = (sceneView.camera.worldRotation.y.mod(360.0) - calculatingAnchorNode.worldRotation.y.mod(360.0)).mod(360.0)

                cameraGeoPoseFromAnchor.heading = (cameraGeoPoseFromAnchor.heading.mod(360.0) + cameraHeadingRelativeToAnchorHeading.mod(360.0)).mod(360.0)

                map?.let { map ->
                    userPositionMarker?.let {
                        it.position = cameraGeoPoseFromAnchor.getLatLng()
                        it.rotation = cameraGeoPoseFromAnchor.heading.toFloat()
                    } ?: run {
                        userPositionMarker = map.addMarker(MarkerOptions().position(cameraGeoPoseFromAnchor.getLatLng()).icon(userIcon).anchor(0.5f, 0.5f).rotation(cameraGeoPoseFromAnchor.heading.toFloat()))
                    }

                    earth?.let {
                        val earthLatLng = LatLng(it.cameraGeospatialPose.latitude, it.cameraGeospatialPose.longitude)
                        geospatialPositionMarker?.let { marker ->
                            marker.position = earthLatLng
                            marker.rotation = it.cameraGeospatialPose.heading.toFloat()
                        } ?: run {
                            geospatialPositionMarker = map.addMarker(MarkerOptions().position(earthLatLng).icon(geospatialIcon).anchor(0.5f, 0.5f).rotation(it.cameraGeospatialPose.heading.toFloat()))
                        }
                    }
                }
                userPose = cameraGeoPoseFromAnchor

                earth?.let {
                    lastLocation?.let { lastLocation ->
                        DataExport.addLocalizingData(it.cameraGeospatialPose, cameraGeoPoseFromAnchor, lastLocation)
                    }
                }
            }
        }
    }

    private fun updateRenderedMappingPointsList(geoPose: GeoPose) {
        val newRenderedMappingPoints = mutableListOf<ArNode>()

        val relativePositionOfPose = GeoUtils.getLocalCoordinateOffsetFromTwoGeoPoses(floorPlan.mainAnchor.getGeoPose(), geoPose)
        val relativePositionOfCurrentAnchor = currentCloudAnchor!!.let {
            Position(it.xToMain, it.yToMain, it.zToMain)
        }
        floorPlan.mappingPointList.forEach {
            val distance = GeoUtils.distanceBetweenTwo3dCoordinates(Position(it.x, it.y, it.z), relativePositionOfPose)
            if (distance < MAPPING_POINT_RENDER_DISTANCE) {
                val relativePositionOfMappingPointToCurrentAnchor = Position(it.x, it.y, it.z) - relativePositionOfCurrentAnchor
                val potentiallyAlreadyCurrentNode = currentRenderedMappingPoints.find { arNode -> arNode.position == relativePositionOfMappingPointToCurrentAnchor }
                potentiallyAlreadyCurrentNode?.let {
                    newRenderedMappingPoints.add(it)
                } ?: run {
                    val node = ArNode()
                    lifecycleScope.launch(Dispatchers.Main) {
                        node.apply {
                            this.parent = currentCloudAnchorNode
                            this.position = relativePositionOfMappingPointToCurrentAnchor
                            setModel(modelMap[BALL])
                        }
                    }
                    newRenderedMappingPoints.add(node)
                }
            }
        }

        val mappingPointsToRemove = mutableListOf<ArNode>()
        currentRenderedMappingPoints.forEach {
            if (newRenderedMappingPoints.contains(it)) {
                newRenderedMappingPoints.remove(it)
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    it.detachAnchor()
                    it.parent = null
                    it.destroy()
                }
                mappingPointsToRemove.add(it)
            }
        }
        currentRenderedMappingPoints.removeAll(mappingPointsToRemove)
        currentRenderedMappingPoints.addAll(newRenderedMappingPoints)

        val newAnchorPreviews = mutableListOf<ArNode>()
        val cloudAnchorListIncludingInitial = mutableListOf<CloudAnchor>()
        cloudAnchorListIncludingInitial.addAll(floorPlan.cloudAnchorList)
        cloudAnchorListIncludingInitial.add(floorPlan.mainAnchor)
        cloudAnchorListIncludingInitial.remove(currentCloudAnchor)

        previewAnchorMap.clear()
        cloudAnchorListIncludingInitial.forEach {
            val distance = GeoUtils.distanceBetweenTwo3dCoordinates(Position(it.xToMain, it.yToMain, it.zToMain), relativePositionOfPose)
            if (distance < ANCHOR_PREVIEW_RENDER_DISTANCE) {
                val relativePositionToCurrentTrackingAnchor = Position(it.xToMain, it.yToMain, it.zToMain) - relativePositionOfCurrentAnchor
                val potentiallyAlreadyRenderedAnchor = currentRenderedAnchorPreviews.find { arNode -> arNode.position == relativePositionToCurrentTrackingAnchor }
                potentiallyAlreadyRenderedAnchor?.let { arNode ->
                    previewAnchorMap[it] = arNode
                    newAnchorPreviews.add(arNode)
                } ?: run {
                    val node = ArNode()
                    lifecycleScope.launch(Dispatchers.Main) {
                        node.apply {
                            previewAnchorMap[it] = this
                            this.parent = currentCloudAnchorNode
                            this.position = relativePositionToCurrentTrackingAnchor
                            setModel(modelMap[ANCHOR_TRACKING_PREVIEW])
                        }
                    }
                    newAnchorPreviews.add(node)
                }
            }
        }
        val anchorPreviewsToRemove = mutableListOf<ArNode>()
        currentRenderedAnchorPreviews.forEach {
            if (newAnchorPreviews.contains(it)) {
                newAnchorPreviews.remove(it)
            } else {
                it.detachAnchor()
                it.parent = null
                it.destroy()
                anchorPreviewsToRemove.add(it)
            }
        }
        currentRenderedAnchorPreviews.removeAll(anchorPreviewsToRemove)
        currentRenderedAnchorPreviews.addAll(newAnchorPreviews)

        navTarget?.let {
            findMappingPointsBetweenPositionAndTarget(it)?.let { list ->
                updateRouteHighlight(list)
            }
        }
    }

    private fun initializeUIElements() {
        userIcon = BitmapDescriptorFactory.fromBitmap(getBitmapFromVectorDrawable(R.drawable.ic_baseline_navigation_24_green, requireContext()))
        geospatialIcon = BitmapDescriptorFactory.fromBitmap(getBitmapFromVectorDrawable(R.drawable.ic_baseline_navigation_24_red, requireContext()))

        bottomSheetBehavior = BottomSheetBehavior.from(binding.arLocalizingBottomSheet)
        requireActivity().window.setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_PAN)

        val collapsedHeight = binding.arLocalizingBottomSheetMap.layoutParams.height
        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        (requireActivity() as AppCompatActivity).supportActionBar?.let {
                            it.setDisplayHomeAsUpEnabled(false)
                            it.setHomeAsUpIndicator(R.drawable.ic_baseline_close_24)
                        }
                        with(binding) {
                            arLocalizingBottomSheetEditLayoutWhileTracking.visibility = View.GONE
                            arLocalizingBottomSheetEditLayoutOnSelection.visibility = View.GONE
                            arLocalizingBottomSheetEditLayoutTrackingInterval.visibility = View.GONE
                            arLocalizingBottomSheetMap.layoutParams.height = collapsedHeight
                        }
                        map?.uiSettings?.isIndoorLevelPickerEnabled = false
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        (requireActivity() as AppCompatActivity).supportActionBar?.let {
                            it.setDisplayHomeAsUpEnabled(true)
                            it.setHomeAsUpIndicator(R.drawable.ic_baseline_close_24)
                        }
                        with(binding) {
                            arLocalizingBottomSheetEditLayoutWhileTracking.visibility = View.VISIBLE
                            arLocalizingBottomSheetEditLayoutOnSelection.visibility = View.VISIBLE
                            arLocalizingBottomSheetEditLayoutTrackingInterval.visibility = View.VISIBLE
                            arLocalizingBottomSheetMap.layoutParams.height = (bottomSheet.height / 2) - 25
                        }
                        map?.uiSettings?.isIndoorLevelPickerEnabled = true
                    }
                    else -> {} //NO-OP
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                //NO-OP
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

        with(binding) {
            arLocalizingBottomSheetAutoModeButton.setOnClickListener {
                if (arState == NOT_INITIALIZED || arState == RESOLVING) {
                    if (floorPlan.cloudAnchorList.size < maxResolvingAmountOnSelected) {
                        Log.d(TAG, "Auto mode button clicked, starting to resolve all anchors simulataneously")

                        updateResolveButtons(AUTO)
                        updateState(RESOLVING)

                        resolveAnyOfClosestCloudAnchors()
                    } else {
                        Log.d(TAG, "Auto mode button clicked, but too many anchors to be resolved")
                        Toast.makeText(requireContext(), "Too many anchors to automatically resolve all, please select one from the list", Toast.LENGTH_SHORT).show()
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        val anim = AlphaAnimation(0.2f, 1.0f).apply {
                            duration = 400
                            startOffset = 50
                            repeatMode = Animation.REVERSE
                            repeatCount = 2
                        }
                        binding.arLocalizingBottomSheetListDescription.startAnimation(anim)
                    }
                }
            }

            arLocalizingBottomSheetSearchView.setOnQueryTextListener(
                object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
            arLocalizingBottomSheetSearchViewSortButton.setOnClickListener { view ->
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

            arLocalizingBottomSheetSelectAnchorButton.setOnClickListener {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                val anim = AlphaAnimation(0.2f, 1.0f).apply {
                    duration = 400
                    startOffset = 50
                    repeatMode = Animation.REVERSE
                    repeatCount = 2
                }
                binding.arLocalizingBottomSheetListDescription.startAnimation(anim)
            }
            arLocalizingBottomSheetSelectFloorButton.setOnClickListener {
                showFloorSelectionDialog()
            }
            arLocalizingBottomSheetGeospatialToggleButton.apply {
                isSelected = true
                setOnClickListener {
                    geospatialEnabled = !geospatialEnabled
                }
            }
            arLocalizingBottomSheetEditResolveAroundSelected.apply {
                setText(maxResolvingAmountOnSelected.toString())
                doOnTextChanged { text, _, _, _ ->
                    if (text.toString().isNotEmpty()) {
                        maxResolvingAmountOnSelected = text.toString().toInt()
                    }
                }
            }
            arLocalizingBottomSheetEditResolveTracking.apply {
                setText(maxResolvingAmountWhileTracking.toString())
                doOnTextChanged { text, _, _, _ ->
                    if (text.toString().isNotEmpty()) {
                        maxResolvingAmountWhileTracking = text.toString().toInt()
                    }
                }
            }
            arLocalizingBottomSheetEditTrackingInterval.apply {
                setText(trackingResolveInterval.toString())
                doOnTextChanged { text, _, _, _ ->
                    if (text.toString().isNotEmpty()) {
                        trackingResolveInterval = text.toString().toLong()
                    }
                }
            }
            arLocalizingBottomSheetCancelResolveButton.setOnClickListener {
                if (arState == RESOLVING) {
                    Log.d(TAG, "Cancel resolve button clicked, cancelling all resolves")
                    resolvingArNode?.cancelCloudAnchorResolveTask()
                    updateResolveButtons(NONE)
                    updateState(NOT_INITIALIZED)
                    anchorListAdapter.indicateResolving(mutableListOf())
                }
            }
            arLocalizingHintText.setOnClickListener {
                if (arState == NAVIGATING) {
                    Log.d(TAG, "Cancelling navigation clicked")
                    clearNavigationIndication()
                    navTarget = null
                    navMappingPoints.clear()
                    updateState(TRACKING)
                }
            }
        }
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
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = false
            }
            setOnIndoorStateChangeListener(object : OnIndoorStateChangeListener {
                override fun onIndoorBuildingFocused() {
                    //NO-OP
                }

                override fun onIndoorLevelActivated(building: IndoorBuilding) {
                    val currentIndoorLevel = building.activeLevelIndex
                    val currentFloor = building.levels[currentIndoorLevel].name
                    val floorAsInt = currentFloor.toIntOrNull()
                    if (floorAsInt != null) {
                        resolveAnyOfClosestCloudAnchors(floor = floorAsInt)
                    } else {
                        when (currentFloor) {
                            "EG", "G" -> resolveAnyOfClosestCloudAnchors(floor = 0)
                            "UG", "1UG" -> resolveAnyOfClosestCloudAnchors(floor = -1)
                            "OG", "1OG" -> resolveAnyOfClosestCloudAnchors(floor = 1)
                            //TODO potentially more exceptions depending on country and city of use, since the floor names are not standardized
                        }
                    }
                }
            })

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
        startLocationUpdates()
    }

    private fun filterCloudAnchorList(query: String?) {
        currentFilter = query ?: ""
        filteredCloudAnchorList.clear()
        if (currentFilter.isNotEmpty()) {
            filteredCloudAnchorList.addAll(floorPlan.cloudAnchorList.filter {
                it.text.contains(currentFilter, true)
            })
            if (floorPlan.mainAnchor.text.contains(currentFilter, true)) {
                filteredCloudAnchorList.add(floorPlan.mainAnchor)
            }
        } else {
            filteredCloudAnchorList.addAll(floorPlan.cloudAnchorList)
            filteredCloudAnchorList.add(floorPlan.mainAnchor)
        }
        anchorListAdapter.submitList(filteredCloudAnchorList)
        anchorListAdapter.notifyDataSetChanged()
    }

    private fun findNextCloudAnchorAndResolve() {
        userPose?.let {
            Log.d(TAG, "Cancelling resolve tasks")
            if (arState != NAVIGATING) updateState(TRACKING)
            resolveAnyOfClosestCloudAnchors(it)
        }
    }

    private fun showCloudAnchorsAsGeospatial() {
        if (earthNodeList.isEmpty()) {
            val mainEarthAnchor = earth!!.createAnchor(floorPlan.mainAnchor.lat, floorPlan.mainAnchor.lng, floorPlan.mainAnchor.alt, 0f, 0f, 0f, 1f)
            val mainEarthNode = ArNode().apply {
                anchor = mainEarthAnchor
                parent = sceneView
                setModel(modelMap[GEO_ANCHOR])
            }
            earthNodeList.add(mainEarthNode)

            getClosestCloudAnchorIfTooMany().forEach {
                val cloudAnchor = earth!!.createAnchor(it.lat, it.lng, it.alt, 0f, 0f, 0f, 1f)
                val cloudAnchorNode = ArNode().apply {
                    anchor = cloudAnchor
                    parent = sceneView
                    setModel(modelMap[GEO_ANCHOR])
                }
                earthNodeList.add(cloudAnchorNode)
            }
        }
    }

    private fun removeCloudAnchorPreviews(resolvedPreviewNode: ArNode? = null, lastAnchor: ArNode? = null, resolvedAnchor: ArNode? = null, posOfResolvedPreview: Position? = null) {
        if (earthNodeList.isNotEmpty()) {
            Log.d(TAG, "Removing all earth nodes")
            earthNodeList.forEach {
                it.detachAnchor()
                it.parent = null
                it.destroy()
            }
            earthNodeList.clear()
        }
        resolvedPreviewNode?.let {
            Log.d(TAG, "Removing resolve preview anchor node")
            val previewPos = posOfResolvedPreview!!

            lastAnchor?.let { lastAnchor ->
                resolvedAnchor?.let { resolvedAnchor ->
                    val posOffsetFromPreviewToCloudAnchor = resolvedAnchor.worldPosition.minus(previewPos) //maybe useful for other test cases

                    val distanceFromPreviewToCloudAnchor = GeoUtils.distanceBetweenTwo3dCoordinates(previewPos, resolvedAnchor.worldPosition)
                    val distanceToLast = GeoUtils.distanceBetweenTwo3dCoordinates(resolvedAnchor.worldPosition, lastAnchor.worldPosition)
                    DataExport.addAnchorErrorSet(distanceToLast, distanceFromPreviewToCloudAnchor)
                    DataExport.addAnchorErrorOffset(distanceToLast, posOffsetFromPreviewToCloudAnchor)
                }
            }

            it.detachAnchor()
            it.parent = null
            it.destroy()
            currentRenderedAnchorPreviews.remove(it)
        }
    }

    private fun resolveAnyOfClosestCloudAnchors(geoPose: GeoPose? = null, floor: Int? = null) {
        Log.d(TAG, "Trying to resolve any of closest cloud anchors")
        val listOfAnchors = if (arState == TRACKING || arState == NAVIGATING) {
            getAnchorsToResolveWhileTracking()
        } else if (floor != null) {
            getAnchorsOnSpecificFloor(floor)
        } else {
            getClosestCloudAnchorIfTooMany(geoPose)
        }
        val listOfAnchorIds = mutableListOf<String>()
        listOfAnchors.forEach { listOfAnchorIds.add(it.cloudAnchorId) }
        currentCloudAnchor?.let {
            listOfAnchorIds.remove(it.cloudAnchorId)
        }

        if (listOfAnchorIds.size < 1) {
            Log.d(TAG, "No anchors to resolve for selected floor $floor or geoPose $geoPose")
            return
        }

        resolvingArNode?.cancelCloudAnchorResolveTask()
        anchorListAdapter.indicateResolving(listOfAnchorIds)
        resolvingArNode = ArModelNode().apply {
            parent = sceneView
            resolveCloudAnchorFromIdList(listOfAnchorIds) { anchor: Anchor, success: Boolean ->
                binding.arLocalizingProgressBar.visibility = View.INVISIBLE
                if (success) {
                    updateResolveButtons(NONE)

                    isVisible = false
                    this.anchor = anchor
                    setModel(modelMap[ANCHOR_RESOLVED])

                    delayNextPositionUpdate = DELAY_POSITION_UPDATE
                    lastCloudAnchorNode = currentCloudAnchorNode
                    currentCloudAnchorNode = this
                    lastCloudAnchor = currentCloudAnchor
                    findCloudAnchorFromId(anchor.cloudAnchorId)?.let {
                        currentCloudAnchor = it
                        filteredCloudAnchorList.remove(it)
                        filteredCloudAnchorList.add(0, it)
                        anchorListAdapter.indicateResolved(it)

                        userPose?.let { pose ->
                            DataExport.addResolvePoint(pose, it.text)
                        }

                        val previewAnchor = previewAnchorMap[it]
                        val previewPos = previewAnchor?.worldPosition
                        val lastAnchor = lastCloudAnchorNode
                        lifecycleScope.launch {
                            delay(DELAY_POSITION_UPDATE) //wait for the node to show at the correct position
                            isVisible = true
                            removeCloudAnchorPreviews(previewAnchor, lastAnchor, this@apply, previewPos)

                            navTarget?.let { target ->
                                findMappingPointsBetweenPositionAndTarget(target)?.let { list ->
                                    navMappingPoints.clear()
                                    navMappingPoints.addAll(list)
                                    highlightRouteAndTarget(true)
                                }
                            }
                        }
                    }
                    binding.arLocalizingBottomSheetCurrentlyResolved.text = getString(R.string.ar_localizing_currently_resolved, currentCloudAnchor?.text)
                    updateResolvedMapMarker()

                    if (arState != NAVIGATING) updateState(TRACKING)
                    Log.d(TAG, "Successfully resolved cloud anchor with id: ${anchor.cloudAnchorId} and name: ${currentCloudAnchor?.text}")
                } else {
                    if (arState != TRACKING) {
                        updateState(NOT_INITIALIZED)
                    }
                }
            }
        }
    }

    private fun updateResolvedMapMarker() {
        currentResolvedMapMarker?.remove()
        val icon = BitmapDescriptorFactory.fromBitmap(getBitmapFromVectorDrawable(R.drawable.ic_outline_flag_24_highlighted, requireContext()))
        currentResolvedMapMarker = map?.addMarker(MarkerOptions().position(LatLng(currentCloudAnchor!!.lat, currentCloudAnchor!!.lng)).title(currentCloudAnchor!!.cloudAnchorId).icon(icon))
    }

    private fun findCloudAnchorFromId(id: String): CloudAnchor? {
        return if (floorPlan.mainAnchor.cloudAnchorId == id) {
            floorPlan.mainAnchor
        } else {
            floorPlan.cloudAnchorList.find { it.cloudAnchorId == id }
        }
    }

    private fun getAnchorsOnSpecificFloor(floor: Int): List<CloudAnchor> {
        val listOfAnchors = mutableListOf<CloudAnchor>()
        floorPlan.cloudAnchorList.forEach {
            if (it.floor == floor && listOfAnchors.size < maxResolvingAmountOnSelected) {
                listOfAnchors.add(it)
            }
        }
        return listOfAnchors
    }

    private fun getClosestCloudAnchorIfTooMany(geoPose: GeoPose? = null): List<CloudAnchor> {
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

        val tempCloudAnchorList = mutableListOf<CloudAnchor>().apply {
            addAll(floorPlan.cloudAnchorList)
            add(floorPlan.mainAnchor)
        }

        while (tempCloudAnchorList.size > maxResolvingAmountOnSelected) {
            tempCloudAnchorList.remove(tempCloudAnchorList.maxBy {
                GeoUtils.distanceBetweenTwoWorldCoordinates(lat, lng, alt, it.lat, it.lng, it.alt)
            })
        }
        return tempCloudAnchorList
    }

    private fun getAnchorsToResolveWhileTracking(): List<CloudAnchor> {
        val tempCloudAnchorList = mutableListOf<CloudAnchor>()
        tempCloudAnchorList.add(floorPlan.mainAnchor)
        tempCloudAnchorList.addAll(floorPlan.cloudAnchorList)
        tempCloudAnchorList.remove(currentCloudAnchor)

        userPose?.let { user ->
            while (tempCloudAnchorList.size > maxResolvingAmountWhileTracking) {
                tempCloudAnchorList.remove(tempCloudAnchorList.maxBy {
                    GeoUtils.distanceBetweenTwoWorldCoordinates(user.latitude, user.longitude, user.altitude, it.lat, it.lng, it.alt)
                })
            }
        }
        binding.arLocalizingProgressBar.visibility = View.VISIBLE
        return tempCloudAnchorList
    }

    private fun onClickCloudAnchorItem(cloudAnchor: CloudAnchor) {
        Log.d(TAG, "Clicked on cloud anchor: $cloudAnchor")
        if (arState == NOT_INITIALIZED || arState == RESOLVING) {
            updateResolveButtons(ANCHOR)
            updateState(RESOLVING)
            resolveAnyOfClosestCloudAnchors(GeoPose(cloudAnchor.lat, cloudAnchor.lng, cloudAnchor.alt, 0.0))
        } else if (arState == TRACKING) {
            //Start navigation indication to selected cloud anchor

            findMappingPointsBetweenPositionAndTarget(cloudAnchor)?.let { mappingPointsBetweenCurrentAndTarget ->
                if (navTarget == null) {
                    navTarget = cloudAnchor
                    navMappingPoints.addAll(mappingPointsBetweenCurrentAndTarget)
                    highlightRouteAndTarget(true)
                } else {
                    if (navTarget == cloudAnchor) {
                        Toast.makeText(requireContext(), "Already navigating to this anchor", Toast.LENGTH_SHORT).show()
                    } else {
                        navTarget = cloudAnchor
                        navMappingPoints.clear()
                        navMappingPoints.addAll(mappingPointsBetweenCurrentAndTarget)
                        highlightRouteAndTarget(false)
                    }
                }
                updateState(NAVIGATING)
            }
        }
    }

    private fun highlightRouteAndTarget(initial: Boolean) {
        if (!initial) {
            clearNavigationIndication()
        }
        navMappingPoints.forEach {
            val relativePositionOfMappingPointToCurrentAnchor = Position(it.x, it.y, it.z) - Position(currentCloudAnchor!!.xToMain, currentCloudAnchor!!.yToMain, currentCloudAnchor!!.zToMain)

            val node = ArNode()
            lifecycleScope.launch(Dispatchers.Main) {
                node.apply {
                    this.parent = currentCloudAnchorNode
                    this.position = relativePositionOfMappingPointToCurrentAnchor
                    setModel(modelMap[NAV_BALL])
                }
            }
            navMappingNodes.add(node)
        }

        val relativePositionOfTargetNodeToCurrentAnchor = Position(navTarget!!.xToMain, navTarget!!.yToMain, navTarget!!.zToMain) - Position(currentCloudAnchor!!.xToMain, currentCloudAnchor!!.yToMain, currentCloudAnchor!!.zToMain)
        val targetNode = ArNode()
        lifecycleScope.launch(Dispatchers.Main) {
            targetNode.apply {
                this.parent = currentCloudAnchorNode
                this.position = relativePositionOfTargetNodeToCurrentAnchor
                setModel(modelMap[NAV_TARGET])
            }
        }
        navTargetNode = targetNode
    }

    private fun updateRouteHighlight(updatedMappingPointList: List<MappingPoint>) {
        val mappingPointsToRemove = navMappingPoints.minus(updatedMappingPointList.toSet())
        mappingPointsToRemove.forEach {
            navMappingNodes.find { node -> node.position == Position(it.x, it.y, it.z) }?.let { node ->
                node.detachAnchor()
                node.parent = null
                node.destroy()
                navMappingNodes.remove(node)
            }
        }
    }

    private fun findMappingPointsBetweenPositionAndTarget(cloudAnchor: CloudAnchor): MutableList<MappingPoint>? {
        Log.d(TAG, "Finding MappingPoints for navigation")

        val closestMappingPointToTarget = findClosestMappingPoint(cloudAnchor)
        val closestMappingPointToCurrent = findClosestMappingPoint()

        val indexOfCurrent = floorPlan.mappingPointList.indexOf(closestMappingPointToCurrent)
        val indexOfTarget = floorPlan.mappingPointList.indexOf(closestMappingPointToTarget)

        return if (indexOfTarget == -1 || indexOfCurrent == -1) {
            null
        } else if (indexOfTarget < indexOfCurrent) {
            floorPlan.mappingPointList.subList(indexOfTarget, indexOfCurrent)
        } else {
            floorPlan.mappingPointList.subList(indexOfCurrent, indexOfTarget)
        }
    }

    private fun findClosestMappingPoint(cloudAnchor: CloudAnchor): MappingPoint {
        return floorPlan.mappingPointList.minBy {
            GeoUtils.distanceBetweenTwo3dCoordinates(Position(cloudAnchor.xToMain, cloudAnchor.yToMain, cloudAnchor.zToMain), Position(it.x, it.y, it.z))
        }
    }

    private fun findClosestMappingPoint(): MappingPoint? {
        val cloudAnchorRelativePos = Position(currentCloudAnchor!!.xToMain, currentCloudAnchor!!.yToMain, currentCloudAnchor!!.zToMain)
        val closestMappingPointNode = currentRenderedMappingPoints.minBy {
            val cameraWorldPos = Position(sceneView.camera.worldPosition.x, sceneView.camera.worldPosition.y, sceneView.camera.worldPosition.z)
            GeoUtils.distanceBetweenTwo3dCoordinates(cameraWorldPos, Position(it.worldPosition.x, it.worldPosition.y, it.worldPosition.z))
        }
        val relativePosOfClosestMappingPoint = cloudAnchorRelativePos + closestMappingPointNode.position

        return floorPlan.mappingPointList.find { it.x == relativePosOfClosestMappingPoint.x && it.y == relativePosOfClosestMappingPoint.y && it.z == relativePosOfClosestMappingPoint.z }
    }

    private fun clearNavigationIndication() {
        navMappingNodes.forEach {
            it.detachAnchor()
            it.parent = null
            it.destroy()
        }
        navMappingNodes.clear()
        navTargetNode?.let {
            it.detachAnchor()
            it.parent = null
            it.destroy()
        }
        navTargetNode = null
    }

    private fun showFloorSelectionDialog() {
        val floorList = mutableListOf<Int>()
        floorPlan.cloudAnchorList.forEach {
            if (!floorList.contains(it.floor)) {
                floorList.add(it.floor)
            }
        }
        floorList.sort()
        Log.d(TAG, "Floor list: $floorList with size: ${floorList.size}")

        val dialogBinding = DialogSelectFloorBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.show()

        val layoutManager = LinearLayoutManager(requireContext())
        val recyclerView = dialogBinding.dialogSelectFloorList
        val adapter = FloorListAdapter(::onClickFloorItem, dialog)
        recyclerView.layoutManager = layoutManager
        adapter.submitList(floorList)
        recyclerView.adapter = adapter
    }

    private fun onClickFloorItem(floor: Int, dialog: Dialog) {
        Log.d(TAG, "Clicked on floor: $floor")
        dialog.dismiss()
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
        if (arState != TRACKING) {
            updateResolveButtons(FLOOR)
            updateState(RESOLVING)
            resolveAnyOfClosestCloudAnchors(floor = floor)
        }
    }

    private fun updateResolveButtons(resolveMode: ArResolveModes) {
        this.resolveMode = resolveMode
        binding.arLocalizingBottomSheetSelectAnchorButton.isSelected = false
        binding.arLocalizingBottomSheetSelectFloorButton.isSelected = false
        binding.arLocalizingBottomSheetAutoModeButton.isSelected = false
        geospatialEnabled = false
        when (resolveMode) {
            AUTO -> {
                binding.arLocalizingBottomSheetAutoModeButton.isSelected = true
            }
            GEOSPATIAL -> {
                geospatialEnabled = true
            }
            FLOOR -> {
                binding.arLocalizingBottomSheetSelectFloorButton.isSelected = true
            }
            ANCHOR -> {
                binding.arLocalizingBottomSheetSelectAnchorButton.isSelected = true
            }
            NONE -> {}//NO-OP
        }
    }

    private fun updateState(newState: ArLocalizingStates) {
        arState = newState
        binding.arLocalizingProgressBar.visibility = arState.progressBarVisibility
        binding.arLocalizingBottomSheetCancelResolveButton.visibility = arState.cancelButtonVisibility
        binding.arLocalizingHintText.text = when (newState) {
            NOT_INITIALIZED -> getString(R.string.ar_localizing_hint_not_initialized)
            RESOLVING -> getString(R.string.ar_localizing_hint_resolving, maxResolvingAmountOnSelected.toString())
            TRACKING -> getString(R.string.ar_localizing_hint_tracking)
            NAVIGATING -> getString(R.string.ar_localizing_hint_navigating, navTarget?.text)
        }
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
            .setSource(context, Uri.parse("models/anchorGeospatialPreview.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[ANCHOR_RESOLVED] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/anchorResolved.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[ANCHOR_TRACKING_PREVIEW] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/anchorTrackingPreview.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[NAV_BALL] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/icoSphereGreen.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[NAV_TARGET] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/anchorNavTarget.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
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
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) {
            startLocationUpdates()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map?.let {
            it.isIndoorEnabled = false
            it.clear()
        }
        map = null
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        DataExport.writeAnchorErrorToFile(floorPlan.name)
        DataExport.finishLocalizingFile()
    }

    override fun onDestroy() {
        //TODO this is only needed in 0.6.0, since it is in sceneView for newer versions
        ResourceManager.getInstance().destroyAllResources()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArLocalizingFragment"

        private const val MIN_HORIZONTAL_ACCURACY = 2.0
        private const val MAX_SIMULTANEOUS_ANCHORS = 40
        private const val MAPPING_POINT_RENDER_DISTANCE = 8.0
        private const val ANCHOR_PREVIEW_RENDER_DISTANCE = 15.0
        private const val INTERVAL_POSITION_UPDATE = 750L
        private const val INTERVAL_USER_POSE_CALCULATION = 100L
        private const val INTERVAL_RESOLVE_UPDATE = 5000L
        private const val DELAY_POSITION_UPDATE = 1000L
    }
}