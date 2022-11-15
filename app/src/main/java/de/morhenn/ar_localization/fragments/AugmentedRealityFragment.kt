package de.morhenn.ar_localization.fragments

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import de.morhenn.ar_localization.fragments.AugmentedRealityFragment.ModelName.DEBUG_CUBE
import de.morhenn.ar_localization.viewmodel.AugmentedRealityViewModel
import io.github.sceneview.Filament
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.model.await
import java.util.*

class AugmentedRealityFragment : Fragment() {

    enum class ArState(
        val progressBarVisibility: Int = View.INVISIBLE,
        val fabEnabled: Boolean = false,
    ) {
        NOT_INITIALIZED,
        PLACE_ANCHOR(fabEnabled = true),
        SCAN_ANCHOR_CIRCLE,
        HOSTING(progressBarVisibility = View.VISIBLE),
        HOST_SUCCESS,
        HOST_FAILED,
    }

    enum class ArMode {
        CREATE_FLOOR_PLAN,
        LOCALIZE
    }

    enum class ModelName {
        DEBUG_CUBE,
    }

    private var arState: ArState = NOT_INITIALIZED
    private var arMode: ArMode = ArMode.CREATE_FLOOR_PLAN

    //viewBinding
    private var _binding: FragmentAugmentedRealityBinding? = null
    private val binding get() = _binding!!

    private val viewModelAR: AugmentedRealityViewModel by viewModels()

    private lateinit var sceneView: ArSceneView

    private var modelMap: EnumMap<ModelName, Renderable> = EnumMap(ModelName::class.java)
    private lateinit var anchorHostingCircle: AnchorHostingPoint
    private var placementNode: ArModelNode? = null
    private var anchorNode: ArModelNode? = null


    private var startRotation: Float = 0f


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentAugmentedRealityBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenCreated {
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
        updateState(PLACE_ANCHOR)
        anchorHostingCircle = AnchorHostingPoint(requireContext(), Filament.engine, sceneView.renderer.filamentScene)
        anchorHostingCircle.enabled = true
        placementNode = ArModelNode(placementMode = PlacementMode.PLANE_HORIZONTAL).apply {
            parent = sceneView
            isVisible = true
        }
    }

    private fun hostCloudAnchor() {
        updateState(HOSTING)
        anchorNode?.let { anchorNode ->
            anchorNode.hostCloudAnchor(365) { anchor: Anchor, success: Boolean ->
                if (success) {
                    updateState(HOST_SUCCESS)
                    anchorNode.isVisible = true
                    anchorHostingCircle.enabled = false
                    Log.d("O_O", "Cloud anchor hosted successfully")
                } else {
                    updateState(HOST_FAILED)
                    Log.d("O_O", "Cloud anchor hosting failed")
                }
            }
        }
    }

    private fun onPlaceClicked() {
        placementNode?.let {
            updateState(SCAN_ANCHOR_CIRCLE)
            anchorNode = ArModelNode(PlacementMode.DISABLED).apply {
                parent = sceneView
                anchor = it.createAnchor()
                isVisible = false
                setModel(modelMap[DEBUG_CUBE])
            }
            startRotation = sceneView.camera.transform.rotation.y
        }
    }

    private fun updateState(state: ArState) {
        arState = state
        binding.arExtendedFab.isEnabled = arState.fabEnabled
        binding.arProgressBar.visibility = arState.progressBarVisibility
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
    }

    override fun onDestroy() {
        //TODO this is only needed in 0.6.0, since it is in sceneView for newer versions
        ResourceManager.getInstance().destroyAllResources()

        super.onDestroy()
    }
}