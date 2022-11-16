package de.morhenn.ar_localization.fragments

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ResourceManager
import de.morhenn.ar_localization.databinding.FragmentAugmentedRealityBinding
import de.morhenn.ar_localization.filament.AnchorHostingPoint
import de.morhenn.ar_localization.fragments.AugmentedRealityFragment.ArState.*
import de.morhenn.ar_localization.fragments.AugmentedRealityFragment.ModelName.AXIS
import de.morhenn.ar_localization.fragments.AugmentedRealityFragment.ModelName.DEBUG_CUBE
import de.morhenn.ar_localization.model.CloudAnchor
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.model.MappingPoint
import de.morhenn.ar_localization.model.SerializableQuaternion
import de.morhenn.ar_localization.utils.GeoUtils
import de.morhenn.ar_localization.viewmodel.AugmentedRealityViewModel
import io.github.sceneview.Filament
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Scale
import io.github.sceneview.model.await
import java.util.*

class AugmentedRealityFragment : Fragment() {

    enum class ArState(
        val progressBarVisibility: Int = View.INVISIBLE,
        val fabEnabled: Boolean = false,
        val anchorCircleEnabled: Boolean = false,
    ) {
        NOT_INITIALIZED,
        PLACE_ANCHOR(fabEnabled = true, anchorCircleEnabled = true),
        SCAN_ANCHOR_CIRCLE(anchorCircleEnabled = true),
        HOSTING(progressBarVisibility = View.VISIBLE, anchorCircleEnabled = true),
        HOST_SUCCESS,
        HOST_FAILED(fabEnabled = true),
        MAPPING,
    }

    enum class ArMode {
        CREATE_FLOOR_PLAN,
        LOCALIZE
    }

    enum class ModelName {
        DEBUG_CUBE,
        AXIS,
    }

    private var arState: ArState = NOT_INITIALIZED
    private var arMode: ArMode = ArMode.CREATE_FLOOR_PLAN

    //state flags
    private var isInitialAnchorPlaced = false

    //viewBinding
    private var _binding: FragmentAugmentedRealityBinding? = null
    private val binding get() = _binding!!

    private val viewModelAR: AugmentedRealityViewModel by viewModels()

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


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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

        binding.arFabAddMappingPoint.setOnClickListener {
            if (arState == MAPPING) {
                val newMappingPoint = ArModelNode(PlacementMode.DISABLED).apply {
                    parent = initialAnchorNode
                    worldPosition = sceneView.camera.position
                    setModel(modelMap[DEBUG_CUBE])
                    scale = Scale(0.5f)
                    //TODO just the temporary solution for now to test
                }
                listOfMappingPoints.add(newMappingPoint)
            }
        }

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
            else -> {} //NOOP
        }
    }

    private fun initializeAR() {
        if (arMode == ArMode.CREATE_FLOOR_PLAN) {
            anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
            updateState(PLACE_ANCHOR)
            placementNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
                parent = sceneView
                isVisible = true
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
                    anchor = placementNode.createAnchor()
                    isVisible = false
                    setModel(modelMap[DEBUG_CUBE])
                }
            } else {
                initialAnchorNode?.let { initialAnchorNode ->
                    trackingAnchorNode = ArModelNode(PlacementMode.DISABLED).apply {
                        parent = initialAnchorNode
                        quaternion = initialAnchorNode.quaternion
                        isVisible = false
                        setModel(modelMap[AXIS])
                    }
                }
            }
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
                        //TODO include geospatial localization //TODO local quaternion correct? or is worldQuaternion needed?
                        floorPlan = FloorPlan(CloudAnchor("initial", anchor.cloudAnchorId, 52.0, 13.0, 70.0, 0.0, SerializableQuaternion(anchorNode.quaternion)))
                        isInitialAnchorPlaced = true
                        Log.d("O_O", "Cloud anchor hosted successfully")
                    } else {
                        onHostingFailed()
                        updateState(PLACE_ANCHOR)
                        Log.d("O_O", "Cloud anchor hosting failed")
                    }
                }
            }
        } else {
            trackingAnchorNode?.let { anchorNode ->
                anchorNode.hostCloudAnchor(365) { anchor: Anchor, success: Boolean ->
                    if (success) {
                        updateState(MAPPING)
                        anchorNode.isVisible = true
                        listOfAnchorNodes.add(anchorNode)
                        addAnchorAndMappingPointsToFloorPlan(anchorNode, anchor.cloudAnchorId)
                        Log.d("O_O", "Cloud anchor hosted successfully")
                    } else {
                        onHostingFailed()
                        updateState(PLACE_ANCHOR)
                        Log.d("O_O", "Cloud anchor hosting failed")
                    }
                }
            }
        }
    }

    private fun onHostingFailed() {
        Toast.makeText(requireContext(), "Hosting failed", Toast.LENGTH_SHORT).show()
        //TODO
    }

    private fun addAnchorAndMappingPointsToFloorPlan(anchorNode: ArModelNode, cloudAnchorId: String) {
        floorPlan?.let { floorPlan ->
            val lastAnchor = if (floorPlan.cloudAnchorList.isEmpty()) {
                floorPlan.mainAnchor
            } else {
                floorPlan.cloudAnchorList.last()
            }

            val newLatLng = GeoUtils.getLatLngByLocalCoordinateOffset(lastAnchor.lat, lastAnchor.lng, lastAnchor.compassHeading, anchorNode.position.x, anchorNode.position.z)
            val newAlt = lastAnchor.alt + (lastAnchor.yToMain - anchorNode.position.y)
            val newX = lastAnchor.xToMain + anchorNode.position.x
            val newY = lastAnchor.yToMain + anchorNode.position.y
            val newZ = lastAnchor.zToMain + anchorNode.position.z

            //TODO text of cloud anchor dynamically
            val newAnchor = CloudAnchor("anchor", cloudAnchorId, newLatLng.latitude, newLatLng.longitude, newAlt, lastAnchor.compassHeading, newX, newY, newZ, lastAnchor.relativeQuaternion)
            listOfMappingPoints.forEach {
                val x = it.position.x + newAnchor.xToMain
                val y = it.position.y + newAnchor.yToMain
                val z = it.position.z + newAnchor.zToMain
                val point = MappingPoint(x, y, z)
                floorPlan.mappingPointList.add(point)
            }
            floorPlan.cloudAnchorList.add(newAnchor)
        }
    }

    private fun updateState(state: ArState) {
        arState = state
        anchorHostingCircle.enabled = arState.anchorCircleEnabled
        binding.arProgressBar.visibility = arState.progressBarVisibility
        binding.arExtendedFab.isEnabled = arState.fabEnabled
        binding.arExtendedFab.setOnClickListener {
            when (arState) {
                PLACE_ANCHOR -> onPlaceClicked()
                else -> {} //NOOP
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
    }

    override fun onDestroy() {
        //TODO this is only needed in 0.6.0, since it is in sceneView for newer versions
        ResourceManager.getInstance().destroyAllResources()

        super.onDestroy()
    }
}