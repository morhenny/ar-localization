package de.morhenn.ar_localization.ar

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ResourceManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.FragmentArLocalizingBinding
import de.morhenn.ar_localization.model.FloorPlan
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.model.await
import java.util.*

class ArLocalizingFragment : Fragment() {

    //viewBinding
    private var _binding: FragmentArLocalizingBinding? = null
    private val binding get() = _binding!!

    private lateinit var sceneView: ArSceneView

    private var earth: Earth? = null

    private val viewModelAr: AugmentedRealityViewModel by navGraphViewModels(R.id.nav_graph_xml)

    private var modelMap: EnumMap<ModelName, Renderable> = EnumMap(ModelName::class.java)

    private lateinit var floorPlan: FloorPlan

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArLocalizingBinding.inflate(inflater, container, false)

        viewModelAr.floorPlan?.let {
            floorPlan = it
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
        binding.arLocalizingGeospatialAccVie.updateView(earth.cameraGeospatialPose)
    }


    private fun initializeUIElements() {
        //TODO
    }

    private suspend fun loadModels() {
        modelMap[ModelName.DEBUG_CUBE] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/cube.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[ModelName.AXIS] = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/axis.glb"))
            .setIsFilamentGltf(true)
            .await(lifecycle)
        modelMap[ModelName.BALL] = ModelRenderable.builder()
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
        private const val TAG = "ArLocalizingFragment"
    }
}