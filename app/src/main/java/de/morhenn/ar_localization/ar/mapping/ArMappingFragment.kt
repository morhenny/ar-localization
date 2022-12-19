package de.morhenn.ar_localization.ar.mapping

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.ar.core.*
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ResourceManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.ar.ArMappingFabState
import de.morhenn.ar_localization.ar.ArMappingStates
import de.morhenn.ar_localization.ar.ArMappingStates.*
import de.morhenn.ar_localization.ar.ModelName
import de.morhenn.ar_localization.ar.ModelName.*
import de.morhenn.ar_localization.ar.MyArInstructions
import de.morhenn.ar_localization.databinding.DialogNewAnchorBinding
import de.morhenn.ar_localization.databinding.FragmentArMappingBinding
import de.morhenn.ar_localization.floorPlan.FloorPlanViewModel
import de.morhenn.ar_localization.model.*
import de.morhenn.ar_localization.utils.DataExport
import de.morhenn.ar_localization.utils.GeoUtils
import io.github.sceneview.Filament
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.*
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.ar.node.infos.TapArPlaneInfoNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3
import io.github.sceneview.model.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ArMappingFragment : Fragment() {

    private var arState: ArMappingStates = NOT_INITIALIZED

    //state flags
    private var isInitialAnchorPlaced = false

    //viewBinding
    private var _binding: FragmentArMappingBinding? = null
    private val binding get() = _binding!!

    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private lateinit var sceneView: ArSceneView
    private lateinit var myArInstructions: MyArInstructions

    private var earth: Earth? = null

    private var modelMap: EnumMap<ModelName, Renderable> = EnumMap(ModelName::class.java)
    private lateinit var anchorHostingCircle: AnchorHostingPoint
    private var placementNode: ArModelNode? = null

    //the initial cloud anchor that the whole floor plan is anchored to
    private var initialAnchorNode: ArModelNode? = null

    //the currently used calibration anchor
    private var trackingAnchorNode: ArModelNode? = null

    //list of all anchors that are part of the floor plan, besides the initial anchor
    private var listOfAnchorNodes: MutableList<ArModelNode> = mutableListOf()

    //list of all virtual mapping points
    private var listOfMappingPoints: MutableList<ArModelNode> = mutableListOf()

    private var floorPlan: FloorPlan? = null

    private var lastMappingPosition = Position(0f, 0f, 0f)
    private var initialGeoPose: GeoPose? = null
    private var newAnchorText: String = ""
    private var newAnchorFloor: Int = 0


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArMappingBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenCreated {
            //Loads all used models into the modelMap
            loadModels()
        }

        sceneView = binding.sceneView
        sceneView.lightEstimationMode = LightEstimationMode.AMBIENT_INTENSITY

        sceneView.onArSessionCreated = {
            sceneView.configureSession { _, config ->
                config.planeFindingEnabled = true
                config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                config.geospatialMode = Config.GeospatialMode.ENABLED
            }
        }
        initializeUIElements()
        myArInstructions = MyArInstructions(sceneView.lifecycle)
        myArInstructions.infoNode = TapArPlaneInfoNode(sceneView.lifecycle.context, sceneView.lifecycle)
        myArInstructions.enabled = false

        DataExport.startNewMappingFile(viewModelFloorPlan.nameForNewFloorPlan)
        Log.d(TAG, "Called start new mapping file with name: ${viewModelFloorPlan.nameForNewFloorPlan}")

        sceneView.onArFrame = { frame ->
            onArFrame(frame)
        }
    }

    private fun onArFrame(frame: ArFrame) {
        if (frame.isTrackingPlane) {
            myArInstructions.enabled = true
            myArInstructions.text = when (arState) {
                PLACE_ANCHOR -> {
                    earth?.let {
                        if (initialAnchorNode == null && it.cameraGeospatialPose.horizontalAccuracy > MIN_HORIZONTAL_GEOSPATIAL_ACCURACY) {
                            getString(R.string.ar_mapping_instructions_geospatial)
                        } else {
                            getString(R.string.ar_mapping_instructions_place_anchor)
                        }
                    } ?: getString(R.string.ar_mapping_instructions_place_anchor)
                }
                SCAN_ANCHOR_CIRCLE -> getString(R.string.ar_mapping_instructions_scan_anchor)
                MAPPING -> getString(R.string.ar_mapping_instructions_mapping)
                else -> null
            }
        }
        earth?.let {
            if (it.trackingState == TrackingState.TRACKING) {
                onArFrameWithEarthTracking(it)
            }
        } ?: run {
            earth = sceneView.arSession?.earth
            Log.d(TAG, "Geospatial API initialized and earth object assigned")
        }
        when (arState) {
            NOT_INITIALIZED -> {
                if (frame.isTrackingPlane) {
                    initializeAR()
                }
            }
            PLACE_ANCHOR -> {
                placementNode?.let {
                    it.pose?.let { pose ->
                        anchorHostingCircle.setPosition(pose)
                    }
                }
            }
            SCAN_ANCHOR_CIRCLE -> {
                if (anchorHostingCircle.isInFrame(frame.camera)) {
                    anchorHostingCircle.highlightSegment(frame.camera.pose)
                }
                if (anchorHostingCircle.allSegmentsHighlighted) {
                    hostCloudAnchor()
                }
            }
            MAPPING -> {
                if (GeoUtils.distanceBetweenTwo3dCoordinates(lastMappingPosition, frame.camera.pose.position) > MAPPING_DISTANCE_THRESHOLD) {
                    lastMappingPosition = frame.camera.pose.position
                    lifecycleScope.launch(Dispatchers.Main) {
                        placeMappingPoint()
                    }
                }
            }
            else -> {} //NOOP
        }
    }

    private fun onArFrameWithEarthTracking(earth: Earth) {
        binding.arGeospatialAccuracyView.updateView(earth.cameraGeospatialPose)
    }

    private fun initializeUIElements() {
        binding.arExtendedFab.setOnClickListener {
            when (arState.fabState) {
                ArMappingFabState.PLACE -> onPlaceClicked()
                ArMappingFabState.NEW_ANCHOR -> onNewAnchorClicked()
                else -> {} //NO-OP
            }
        }
        binding.arFabConfirm.setOnClickListener {
            onConfirmClicked()
        }
        binding.arFabUndo.setOnClickListener {
            onUndoClicked()
        }
    }

    private fun initializeAR() {
        anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
        updateState(PLACE_ANCHOR)
        placementNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
            parent = sceneView
            isVisible = false
        }
    }

    private fun onPlaceClicked() {
        placementNode?.let { placementNode ->
            updateState(SCAN_ANCHOR_CIRCLE)
            if (!isInitialAnchorPlaced) {
                initialAnchorNode = ArModelNode(PlacementMode.DISABLED).apply {
                    parent = sceneView
                    isVisible = false
                    setModel(modelMap[AXIS])
                    anchor = placementNode.createAnchor() //TODO monitor how often his causes ARCoreError: Can't attach anchor
                    anchor?.pose?.let { pose ->
                        anchorHostingCircle.setPosition(pose)
                    }
                    calculateGeoPoseOfPlacementNode()
                    resetPlacementNode()
                }
            } else {
                initialAnchorNode?.let { initialAnchorNode ->
                    trackingAnchorNode = ArModelNode(PlacementMode.DISABLED).apply {
                        parent = sceneView
                        isVisible = false
                        setModel(modelMap[AXIS])
                        placementNode.pose?.let {
                            val anchorPose = Pose(placementNode.pose?.translation, initialAnchorNode.quaternion.toFloatArray())
                            placementNode.lastHitResult?.let {
                                anchor = it.trackable.createAnchor(anchorPose)
                                anchor?.pose?.let { pose ->
                                    anchorHostingCircle.setPosition(pose)
                                }
                                resetPlacementNode()
                            } ?: run {
                                Log.e(TAG, "lastHitResult is null, no anchor created for trackingAnchorNode")
                                updateState(PLACE_ANCHOR)
                            }
                        } ?: run {
                            Log.e(TAG, "Pose is null, cannot place trackingNode")
                            updateState(PLACE_ANCHOR)
                        }
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "onPlaceClicked: placementNode is null")
        }
    }

    private fun onConfirmClicked() {
        binding.arProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            floorPlan?.let {
                it.name = viewModelFloorPlan.nameForNewFloorPlan
                it.info = viewModelFloorPlan.infoForNewFloorPlan
                it.ownerUID = viewModelFloorPlan.ownerUID
                addRemainingMappingPointsToFloorPlan()
                DataExport.finishMappingFile()
                viewModelFloorPlan.addFloorPlan(it)
                withContext(Dispatchers.Main) {
                    findNavController().popBackStack()
                }
            } ?: run {
                Toast.makeText(requireContext(), "No floor plan created yet, cannot confirm", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onConfirmClicked: floorPlan is null")
            }
        }
    }

    private fun onUndoClicked() {
        if (arState == SCAN_ANCHOR_CIRCLE || arState == HOSTING) {
            if (isInitialAnchorPlaced) {
                trackingAnchorNode?.let {
                    it.detachAnchor()
                    it.parent = null
                    it.destroy()
                }
                trackingAnchorNode = null
            } else {
                initialAnchorNode?.let {
                    it.detachAnchor()
                    it.parent = null
                    it.destroy()
                }
                initialAnchorNode = null
                isInitialAnchorPlaced = false
            }
            anchorHostingCircle.destroy()
            anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
            updateState(PLACE_ANCHOR)
        } else if (arState == MAPPING) {
            if (listOfAnchorNodes.isNotEmpty()) {
                with(listOfAnchorNodes.last()) {
                    anchor?.detach()
                    parent = null
                    destroy()
                }
                listOfAnchorNodes.removeLast()
            } else {
                initialAnchorNode?.let {
                    it.detachAnchor()
                    it.parent = null
                    it.destroy()
                }
                initialAnchorNode = null
                isInitialAnchorPlaced = false
                binding.arGeospatialAccuracyView.collapsed = false
            }
            anchorHostingCircle.destroy()
            anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
            updateState(PLACE_ANCHOR)
        } else if (arState == PLACE_ANCHOR) {
            if (isInitialAnchorPlaced) {
                updateState(MAPPING)
            }
        }
    }

    private fun onNewAnchorClicked() {
        val dialogBinding = DialogNewAnchorBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        dialogBinding.dialogNewAnchorInputText.requestFocus()
        dialogBinding.dialogNewAnchorButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputText.text.toString().isNotEmpty()) {
                resetAnchorHostingCircle()
                resetPlacementNode()
                updateState(PLACE_ANCHOR)
                newAnchorText = dialogBinding.dialogNewAnchorInputText.text.toString()
                if (dialogBinding.dialogNewAnchorInputFloor.text.toString().isNotEmpty()) {
                    newAnchorFloor = dialogBinding.dialogNewAnchorInputFloor.text.toString().toInt()
                }
                dialog.dismiss()
            } else {
                dialogBinding.dialogNewAnchorInputLayout.error = getString(R.string.dialog_new_anchor_text_error)
            }
        }
        dialogBinding.dialogNewAnchorButtonCancel.setOnClickListener {
            dialog.cancel()
        }
    }

    private fun hostCloudAnchor() {
        updateState(HOSTING)
        if (!isInitialAnchorPlaced) {
            initialAnchorNode?.let { anchorNode ->
                anchorNode.hostCloudAnchor(365) { anchor: Anchor, success: Boolean ->
                    if (success) {
                        binding.arGeospatialAccuracyView.collapsed = true
                        updateState(MAPPING)
                        anchorNode.isVisible = true

                        initialGeoPose?.let {
                            floorPlan = FloorPlan(CloudAnchor("initial", 0, anchor.cloudAnchorId, it, SerializableQuaternion(anchorNode.quaternion)))
                        } ?: run {
                            Log.e(TAG, "hostCloudAnchor: initialGeoPose is null")
                            Toast.makeText(requireContext(), "Could not create floor plan with geoPose, since initialGeoPose is null", Toast.LENGTH_SHORT).show()
                            floorPlan = FloorPlan(CloudAnchor("initial", 0, anchor.cloudAnchorId, 0.0, 0.0, 0.0, 0.0, SerializableQuaternion(anchorNode.quaternion)))
                        }
                        //TODO possibly find out what floor the initial anchor is on
                        isInitialAnchorPlaced = true
                        Log.d(TAG, "Cloud anchor hosted successfully")
                    } else {
                        onHostingFailed()
                    }
                }
            }
        } else {
            trackingAnchorNode?.let { anchorNode ->
                anchorNode.hostCloudAnchor(365) { anchor: Anchor, success: Boolean ->
                    if (success) {
                        updateState(MAPPING)
                        anchorNode.isVisible = true
                        addAnchorAndMappingPointsToFloorPlan(anchorNode, anchor.cloudAnchorId)
                        listOfAnchorNodes.add(anchorNode)
                        Log.d(TAG, "Cloud anchor hosted successfully")
                    } else {
                        onHostingFailed()
                    }
                }
            }
        }
    }

    private fun calculateGeoPoseOfPlacementNode() {
        earth?.cameraGeospatialPose?.let { geospatialPose ->
            placementNode?.let { pNode ->
                pNode.pose?.let { pose ->
                    val vectorUp = pose.yDirection

                    val cameraTransform = sceneView.camera.transform
                    val cameraPosition = cameraTransform.position
                    val cameraOnPlaneOfPlacementNode = cameraPosition.minus(vectorUp.times((cameraPosition.minus(pNode.position)).times(vectorUp)))
                    val distanceOfCameraToGround = (cameraPosition.minus(cameraOnPlaneOfPlacementNode)).toVector3().length()

                    val distanceToPlacementNode = ((pNode.position.minus(cameraOnPlaneOfPlacementNode)).toVector3().length()) / 1000 // divided by 1000, because in km

                    val latLng = GeoUtils.getLatLngByDistanceAndBearing(geospatialPose.latitude, geospatialPose.longitude, geospatialPose.heading, distanceToPlacementNode.toDouble())

                    initialGeoPose = GeoPose(latLng.latitude, latLng.longitude, geospatialPose.altitude - distanceOfCameraToGround, geospatialPose.heading)
                }
            }
        }
    }

    private fun onHostingFailed() {
        Toast.makeText(requireContext(), "Hosting failed", Toast.LENGTH_SHORT).show()
        resetAnchorHostingCircle()
        updateState(PLACE_ANCHOR)
        Log.d(TAG, "Cloud anchor hosting failed")
    }

    private fun placeMappingPoint() {
        val mappingAnchor = if (listOfAnchorNodes.isEmpty()) {
            initialAnchorNode!!
        } else {
            listOfAnchorNodes.last()
        }
        val newMappingPoint = ArModelNode(PlacementMode.DISABLED).apply {
            parent = mappingAnchor
            worldPosition = sceneView.camera.position.apply { --y }
            setModel(modelMap[BALL])
        }
        listOfMappingPoints.add(newMappingPoint)
    }

    private fun resetAnchorHostingCircle() {
        anchorHostingCircle.destroy()
        anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
    }

    private fun resetPlacementNode() {
        placementNode?.destroy()
        placementNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
            parent = sceneView
            isVisible = false
        }
    }

    private fun addAnchorAndMappingPointsToFloorPlan(anchorNode: ArModelNode, cloudAnchorId: String) {
        floorPlan?.let { floorPlan ->
            //Use offset to last placed anchor to calculate latLng and relative x,y,z to the initial Anchor, using the lastAnchors values
            val lastAnchor = if (listOfAnchorNodes.isEmpty()) {
                DataExport.addMappingAnchor(floorPlan.mainAnchor)
                Pair(floorPlan.mainAnchor, initialAnchorNode!!)
            } else {
                Pair(floorPlan.cloudAnchorList.last(), listOfAnchorNodes.last())
            }
            val posOffsetToLastAnchor = lastAnchor.second.worldToLocalPosition(anchorNode.worldPosition.toVector3()).toFloat3()
            val xOffset = posOffsetToLastAnchor.x + lastAnchor.first.xToMain
            val yOffset = posOffsetToLastAnchor.y + lastAnchor.first.yToMain
            val zOffset = posOffsetToLastAnchor.z + lastAnchor.first.zToMain

            val newLatLng = GeoUtils.getLatLngByLocalCoordinateOffset(lastAnchor.first.lat, lastAnchor.first.lng, lastAnchor.first.compassHeading, posOffsetToLastAnchor.x, posOffsetToLastAnchor.z)
            val newAlt = lastAnchor.first.alt + posOffsetToLastAnchor.y

            val newAnchor = CloudAnchor(newAnchorText, newAnchorFloor, cloudAnchorId, newLatLng.latitude, newLatLng.longitude, newAlt, lastAnchor.first.compassHeading,
                xOffset, yOffset, zOffset, lastAnchor.first.relativeQuaternion)

            addMappingPointsAndClearList(lastAnchor.first)
            floorPlan.cloudAnchorList.add(newAnchor)

            DataExport.addMappingAnchor(newAnchor)
        }
    }

    private fun addRemainingMappingPointsToFloorPlan() {
        floorPlan?.let { floorPlan ->
            val lastAnchor = if (listOfAnchorNodes.isEmpty()) {
                floorPlan.mainAnchor
            } else {
                floorPlan.cloudAnchorList.last()
            }
            addMappingPointsAndClearList(lastAnchor)
        }
    }

    private fun addMappingPointsAndClearList(lastAnchor: CloudAnchor) {
        listOfMappingPoints.forEach {
            val x = it.position.x + lastAnchor.xToMain
            val y = it.position.y + lastAnchor.yToMain
            val z = it.position.z + lastAnchor.zToMain
            val point = MappingPoint(x, y, z)
            floorPlan!!.mappingPointList.add(point)
            it.parent = null //Remove the mappingPoint from the rendered scene
            DataExport.appendMappingPointData(GeoUtils.getGeoPoseByLocalCoordinateOffset(initialGeoPose!!, Position(x, y, z)))
        }
        listOfMappingPoints.clear()
    }

    private fun updateState(state: ArMappingStates) {
        arState = state
        anchorHostingCircle.enabled = arState.anchorCircleEnabled
        binding.arFabConfirm.visibility = arState.fabConfirmVisibility
        binding.arProgressBar.visibility = arState.progressBarVisibility
        binding.arExtendedFab.isEnabled = arState.fabEnabled
        if (arState == PLACE_ANCHOR && initialAnchorNode == null) {
            binding.arFabUndo.visibility = View.INVISIBLE
        } else {
            binding.arFabUndo.visibility = arState.undoVisibility
        }
        when (arState.fabState) {
            ArMappingFabState.PLACE -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_place)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_place_item_24)
            }
            ArMappingFabState.HOST -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_hosting)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_cloud_upload_24)
            }
            ArMappingFabState.NEW_ANCHOR -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_new_anchor)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_add_24)
            }
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
    }

    override fun onDestroy() {
        //TODO this is only needed in 0.6.0, since it is in sceneView for newer versions
        ResourceManager.getInstance().destroyAllResources()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArMappingFragment"
        private const val MAPPING_DISTANCE_THRESHOLD = 1 //distance in meters between mapping points
        private const val MIN_HORIZONTAL_GEOSPATIAL_ACCURACY = 1.5
    }
}