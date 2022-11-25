package de.morhenn.ar_localization.ar

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ResourceManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.ar.ArState.*
import de.morhenn.ar_localization.ar.ModelName.*
import de.morhenn.ar_localization.databinding.DialogNewAnchorBinding
import de.morhenn.ar_localization.databinding.FragmentAugmentedRealityBinding
import de.morhenn.ar_localization.floorPlan.FloorPlanViewModel
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.MappingPoint
import de.morhenn.ar_localization.model.SerializableQuaternion
import de.morhenn.ar_localization.utils.GeoUtils
import io.github.sceneview.Filament
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3
import io.github.sceneview.model.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class AugmentedRealityFragment : Fragment() {

    private var arState: ArState = NOT_INITIALIZED
    private var arMode: ArMode = ArMode.CREATE_FLOOR_PLAN

    //state flags
    private var isInitialAnchorPlaced = false

    //viewBinding
    private var _binding: FragmentAugmentedRealityBinding? = null
    private val binding get() = _binding!!

    private val viewModelAR: AugmentedRealityViewModel by viewModels()
    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private lateinit var sceneView: ArSceneView

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
    private var newAnchorText: String = ""


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAugmentedRealityBinding.inflate(inflater, container, false)

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

        sceneView.onArFrame = { frame ->
            onArFrame(frame)
        }
    }

    private fun onArFrame(frame: ArFrame) {
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

    private fun initializeUIElements() {
        binding.arExtendedFab.setOnClickListener {
            when (arState.fabState) {
                ArFabState.PLACE -> onPlaceClicked()
                ArFabState.RESOLVE -> TODO()
                ArFabState.NEW_ANCHOR -> onNewAnchorClicked()
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
        if (arMode == ArMode.CREATE_FLOOR_PLAN) {
            anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
            updateState(PLACE_ANCHOR)
            placementNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
                parent = sceneView
                isVisible = false
            }
        } else {
            //TODO
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
                    anchor = placementNode.createAnchor()
                    resetPlacementNode()
                }
            } else {
                initialAnchorNode?.let { initialAnchorNode ->
                    trackingAnchorNode = ArModelNode(PlacementMode.DISABLED).apply {
                        parent = sceneView
                        isVisible = false
                        setModel(modelMap[AXIS])
                        val anchorPose = Pose(placementNode.pose?.translation, initialAnchorNode.quaternion.toFloatArray())
                        placementNode.lastHitResult?.let {
                            anchor = it.trackable.createAnchor(anchorPose)
                            resetPlacementNode()
                        } ?: run {
                            Log.e("O_O", "lastHitResult is null, no anchor created for trackingAnchorNode")
                            updateState(PLACE_ANCHOR)
                        }
                    }

                }
            }
        } ?: run {
            Log.e("O_O", "onPlaceClicked: placementNode is null")
        }
    }

    private fun onConfirmClicked() {
        floorPlan?.let {
            it.name = viewModelFloorPlan.nameForNewFloorPlan
            it.info = viewModelFloorPlan.infoForNewFloorPlan
            addRemainingMappingPointsToFloorPlan()
            viewModelFloorPlan.floorPlanList.add(it)
            findNavController().popBackStack()
        } ?: run {
            Toast.makeText(requireContext(), "No floor plan created yet, cannot confirm", Toast.LENGTH_SHORT).show()
            Log.e("O_O", "onConfirmClicked: floorPlan is null")
        }
    }

    private fun onUndoClicked() {
        TODO("Not yet implemented")
    }

    private fun onNewAnchorClicked() {
        val dialogBinding = DialogNewAnchorBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.show()
        dialogBinding.dialogNewAnchorButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputText.text.toString().isNotEmpty()) {
                resetAnchorHostingCircle()
                resetPlacementNode()
                updateState(PLACE_ANCHOR)
                newAnchorText = dialogBinding.dialogNewAnchorInputText.text.toString()
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
                        updateState(MAPPING)
                        anchorNode.isVisible = true
                        //TODO include geospatial localization
                        floorPlan = FloorPlan(CloudAnchor("initial", anchor.cloudAnchorId, 52.0, 13.0, 70.0, 0.0, SerializableQuaternion(anchorNode.quaternion)))
                        isInitialAnchorPlaced = true
                        Log.d("O_O", "Cloud anchor hosted successfully")
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
                        Log.d("O_O", "Cloud anchor hosted successfully")
                    } else {
                        onHostingFailed()
                    }
                }
            }
        }
    }

    private fun onHostingFailed() {
        Toast.makeText(requireContext(), "Hosting failed", Toast.LENGTH_SHORT).show()
        resetAnchorHostingCircle()
        updateState(PLACE_ANCHOR)
        Log.d("O_O", "Cloud anchor hosting failed")
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

            val newAnchor = CloudAnchor(newAnchorText, cloudAnchorId, newLatLng.latitude, newLatLng.longitude, newAlt, lastAnchor.first.compassHeading,
                xOffset, yOffset, zOffset, lastAnchor.first.relativeQuaternion)

            addMappingPointsAndClearList(lastAnchor.first)
            floorPlan.cloudAnchorList.add(newAnchor)
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
        }
        listOfMappingPoints.clear()
    }

    private fun updateState(state: ArState) {
        arState = state
        anchorHostingCircle.enabled = arState.anchorCircleEnabled
        binding.arFabConfirm.visibility = arState.fabConfirmVisibility
        binding.arProgressBar.visibility = arState.progressBarVisibility
        binding.arExtendedFab.isEnabled = arState.fabEnabled
        when (arState.fabState) {
            ArFabState.PLACE -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_place)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_place_item_24)
            }
            ArFabState.RESOLVE -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_resolve)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_cloud_download_24)
            }
            ArFabState.HOST -> {
                binding.arExtendedFab.text = getString(R.string.ar_fab_hosting)
                binding.arExtendedFab.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_cloud_upload_24)
            }
            ArFabState.NEW_ANCHOR -> {
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
        private const val TAG = "AugmentedRealityFragment"
        private const val MAPPING_DISTANCE_THRESHOLD = 1 //distance in meters between mapping points
    }
}