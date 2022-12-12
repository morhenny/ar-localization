package de.morhenn.ar_localization.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import de.morhenn.ar_localization.model.FloorPlan
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirebaseFloorPlanService {
    private const val TAG = "FirebaseFloorPlanService"

    fun registerForFloorPlanUpdates(): Flow<List<FloorPlan>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("floorPlans").orderBy("name").addSnapshotListener { value, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed.", error)
                    return@addSnapshotListener
                }
                trySend(value!!.toObjects(FloorPlan::class.java))
            }
            awaitClose {
                Log.d(TAG, "Canceling floorPlan listener")
                listenerRegistration.remove()
            }
        }
    }

    suspend fun getFloorPlanList(): List<FloorPlan>? {
        val db = FirebaseFirestore.getInstance()
        return try {
            db.collection("floorPlans").get().await().toObjects(FloorPlan::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting documents: ", e)
            null
        }
    }

    suspend fun getFloorPlanListByName(): List<FloorPlan>? {
        val db = FirebaseFirestore.getInstance()
        return try {
            db.collection("floorPlans").orderBy("name").get().await().toObjects(FloorPlan::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting documents: ", e)
            null
        }
    }

    fun addFloorPlan(floorPlan: FloorPlan) {
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("floorPlans").add(floorPlan)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding new floorPlan: ", e)
        }
    }

    fun deleteFloorPlan(floorPlan: FloorPlan) {
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("floorPlans").whereEqualTo("mainAnchor.cloudAnchorId", floorPlan.mainAnchor.cloudAnchorId).get().addOnSuccessListener { result ->
                result.documents[0].reference.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting floorPlan: ", e)
        }
    }

    fun updateFloorPlan(floorPlan: FloorPlan) {
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("floorPlans").whereEqualTo("mainAnchor.cloudAnchorId", floorPlan.mainAnchor.cloudAnchorId).get().addOnSuccessListener { result ->
                result.documents[0].reference.update("name", floorPlan.name, "info", floorPlan.info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating floorPlan: ", e)
        }
    }
}
