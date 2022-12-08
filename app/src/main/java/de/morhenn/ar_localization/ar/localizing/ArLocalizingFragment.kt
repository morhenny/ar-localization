package de.morhenn.ar_localization.ar.localizing

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
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

    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: CloudAnchorListAdapter

    private var arState: ArLocalizingStates = NOT_INITIALIZED

    private val earthNodeList = mutableListOf<ArNode>()

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
        val cameraGeoPose = earth.cameraGeospatialPose
        binding.arLocalizingGeospatialAccVie.updateView(cameraGeoPose)

        if (cameraGeoPose.horizontalAccuracy < MIN_HORIZONTAL_ACCURACY) {
            showCloudAnchorsAsGeospatial(true)

            if (arState == NOT_INITIALIZED) {
                updateState(RESOLVING_FROM_GEOSPATIAL)
                resolveAnyOfClosestCloudAnchors()
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

    private fun resolveAnyOfClosestCloudAnchors(geoPose: GeoPose? = null) {
        val listOfAnchors = getClosestCloudAnchorIfTooMany(geoPose)
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
                } else {
                    if (arState != TRACKING) {
                        updateState(NOT_INITIALIZED)
                    }
                }
            }
        }
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

    private fun initializeUIElements() {

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.arLocalizingBottomSheet)
        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {}
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
                    BottomSheetBehavior.STATE_EXPANDED -> {}
                    else -> {} //NO-OP
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                //TODO
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior.saveFlags = BottomSheetBehavior.SAVE_ALL

        recyclerView = binding.arLocalizingBottomSheetList
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        listAdapter = CloudAnchorListAdapter(::onClickCloudAnchorItem).apply {
            submitList(floorPlan.cloudAnchorList)
        }
        recyclerView.adapter = listAdapter
    }

    private fun onClickCloudAnchorItem(cloudAnchor: CloudAnchor) {
        Log.d("O_O", "Clicked on cloud anchor: $cloudAnchor")
        if (arState == NOT_INITIALIZED) {
            updateState(RESOLVING_FROM_SELECTED)
            resolveAnyOfClosestCloudAnchors(GeoPose(cloudAnchor.lat, cloudAnchor.lng, cloudAnchor.alt, 0.0))
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