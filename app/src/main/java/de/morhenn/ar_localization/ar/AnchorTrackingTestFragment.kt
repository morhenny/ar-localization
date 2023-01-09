package de.morhenn.ar_localization.ar

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.GeospatialPose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.ModelRenderable
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.FragmentAnchorTrackingTestBinding
import de.morhenn.ar_localization.model.GeoPose
import de.morhenn.ar_localization.utils.DataExport
import de.morhenn.ar_localization.utils.GeoUtils
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import io.github.sceneview.ar.arcore.planeFindingEnabled
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3
import io.github.sceneview.model.await

class AnchorTrackingTestFragment : Fragment() {

    //viewBinding
    private var _binding: FragmentAnchorTrackingTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var sceneView: ArSceneView

    private var earth: Earth? = null

    private var runningTest = false

    private var lastAnchorPosition = Position(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var initialAnchorGeoPose: GeoPose? = null

    private val anchorList = mutableListOf<ArNode>()

    private lateinit var modelBall: ModelRenderable
    private lateinit var modelAxis: ModelRenderable

    private var lastLogTimeStamp = 0L

    private var loggingInterval = DEFAULT_LOG_INTERVAL
    private var anchorPlacementDistance = DEFAULT_DISTANCE_BETWEEN_ANCHOR_PLACEMENTS

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentAnchorTrackingTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenCreated {
            modelBall = ModelRenderable.builder()
                .setSource(context, Uri.parse("models/icoSphere.glb"))
                .setIsFilamentGltf(true)
                .await(lifecycle)
            modelAxis = ModelRenderable.builder()
                .setSource(context, Uri.parse("models/axis.glb"))
                .setIsFilamentGltf(true)
                .await(lifecycle)
        }

        sceneView = binding.sceneViewAnchorTrackingTest
        sceneView.lightEstimationMode = LightEstimationMode.DISABLED

        sceneView.onArSessionCreated = {
            sceneView.configureSession { _, config ->
                config.planeFindingEnabled = false
                config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                config.geospatialMode = Config.GeospatialMode.ENABLED
            }
        }

        sceneView.onArFrame = { frame ->
            earth?.let { earth ->
                if (earth.trackingState == TrackingState.TRACKING) {
                    binding.arAnchorTrackingTestGeospatialAccuracy.updateView(earth.cameraGeospatialPose)

                    if (runningTest) {
                        val distanceFromLastAnchor = GeoUtils.distanceBetweenTwo3dCoordinates(frame.camera.pose.position, lastAnchorPosition)
                        if (distanceFromLastAnchor > anchorPlacementDistance) {
                            placeNextAnchor(earth, frame)
                        }
                        val timeSinceLastLog = System.currentTimeMillis() - lastLogTimeStamp
                        if (timeSinceLastLog > loggingInterval) {
                            initialAnchorGeoPose?.let {
                                val cameraAsLocalPositionOfInitial = anchorList.first().worldToLocalPosition(frame.camera.pose.position.toVector3()).toFloat3()
                                if (anchorList.size == 1) {
                                    DataExport.addAnchorTrackingData(it, earth.cameraGeospatialPose)
                                } else {
                                    DataExport.addAnchorTrackingData(GeoUtils.getGeoPoseByLocalCoordinateOffsetWithEastUpSouth(it, cameraAsLocalPositionOfInitial), earth.cameraGeospatialPose)
                                }
                                lastLogTimeStamp = System.currentTimeMillis()
                            }
                        }
                    }
                }
            } ?: run {
                earth = sceneView.arSession?.earth
                Log.d(TAG, "Geospatial API initialized and earth object assigned")
            }
        }

        with(binding) {
            anchorTrackingTestDistance.setText(anchorPlacementDistance.toString())
            anchorTrackingTestInterval.setText(loggingInterval.toString())

            arAnchorTrackingTestFab.setOnClickListener {
                earth?.let {
                    if (!runningTest) {
                        runningTest = true

                        anchorPlacementDistance = if (!anchorTrackingTestDistance.text.isNullOrBlank()) {
                            anchorTrackingTestDistance.text.toString().toFloat()
                        } else {
                            DEFAULT_DISTANCE_BETWEEN_ANCHOR_PLACEMENTS
                        }
                        loggingInterval = if (!anchorTrackingTestInterval.text.isNullOrBlank()) {
                            anchorTrackingTestInterval.text.toString().toLong()
                        } else {
                            DEFAULT_LOG_INTERVAL
                        }
                        anchorTrackingTestDistance.isEnabled = false
                        anchorTrackingTestInterval.isEnabled = false

                        binding.anchorTestProgressBar.visibility = View.VISIBLE
                        binding.arAnchorTrackingTestFab.text = getString(R.string.test_finish)
                    } else {
                        DataExport.writeAnchorTrackingDataToFile(anchorPlacementDistance, loggingInterval)
                        findNavController().popBackStack()
                    }
                } ?: run {
                    Toast.makeText(requireContext(), "Geospatial API not initialized yet, try again", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun placeNextAnchor(earth: Earth, frame: ArFrame) {
        if (initialAnchorGeoPose == null) {
            val initialAnchor = ArModelNode(PlacementMode.DISABLED).apply {
                parent = sceneView
                with(earth.cameraGeospatialPose) {
                    val poseFromEarth = earth.getPose(latitude, longitude, altitude, eastUpSouthQuaternion[0], 0f, eastUpSouthQuaternion[2], 0f)
                    with(poseFromEarth) {
                        this@apply.pose = this
                        lastAnchorPosition = this.position
                    }
                    initialAnchorGeoPose = GeoPose(latitude, longitude, altitude, heading)
                }
                setModel(modelAxis)
                anchor()
            }
            anchorList.add(initialAnchor)
        } else if (anchorPlacementDistance != 0f) {
            val nextAnchor = ArModelNode(PlacementMode.DISABLED).apply {
                val lastAnchor = anchorList.last()
                parent = lastAnchor
                with(lastAnchor.worldToLocalPosition(frame.camera.pose.position.toVector3()).toFloat3()) {
                    position = this
                }
                setModel(modelBall)
                anchor()
            }
            lastAnchorPosition = nextAnchor.worldPosition
            anchorList.add(nextAnchor)
        }
    }

    companion object {
        private const val TAG = "AnchorTrackingTestFragment"
        private const val DEFAULT_DISTANCE_BETWEEN_ANCHOR_PLACEMENTS = 2.0f
        private const val DEFAULT_LOG_INTERVAL = 1000L
    }
}

fun GeospatialPose.toGeoPose(): GeoPose {
    return GeoPose(latitude, longitude, altitude, heading)
}