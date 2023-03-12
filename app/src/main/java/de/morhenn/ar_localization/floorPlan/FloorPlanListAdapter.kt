package de.morhenn.ar_localization.floorPlan

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.ItemFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan

class FloorPlanListAdapter(
    var floorPlans: List<FloorPlan>,
    val onDeleteItem: (item: FloorPlan) -> Unit,
    val onUpdateItem: (item: FloorPlan) -> Unit,
    val onLocalizeItem: (item: FloorPlan) -> Unit,
    val onSelectItem: () -> Unit,
) : RecyclerView.Adapter<FloorPlanListAdapter.ViewHolder>() {

    var expandedPosition = -1

    var currentLocation: Location? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFloorPlanListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return floorPlans.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(floorPlans[position])
        if (position == expandedPosition) {
            holder.expandArea.visibility = View.VISIBLE
            holder.expandIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_baseline_arrow_drop_up_24))
        } else {
            holder.expandArea.visibility = View.GONE
            holder.expandIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_baseline_arrow_drop_down_24))
        }
        holder.listItem.setOnClickListener {
            val lastPos = expandedPosition
            expandedPosition = if (lastPos == position) {
                -1
            } else position
            onSelectItem()
            if (lastPos >= 0) notifyItemChanged(lastPos)
            notifyItemChanged(position)
        }
        Firebase.auth.currentUser?.let {
            with((it.uid == floorPlans[position].ownerUID)) {
                holder.buttonDelete.visibility = if (this) View.VISIBLE else View.GONE
                holder.buttonUpdate.visibility = if (this) View.VISIBLE else View.GONE
                holder.textOwner.visibility = if (this) View.VISIBLE else View.GONE
            }
        }

        currentLocation?.let {
            val loc = Location("")
            loc.latitude = floorPlans[position].mainAnchor.lat
            loc.longitude = floorPlans[position].mainAnchor.lng
            val distance = it.distanceTo(loc)
            if (distance > 1000) {
                holder.textDistance.text = String.format("%.1f km", distance / 1000)
            } else {
                holder.textDistance.text = String.format("%.2f m", distance)
            }
        }

        with(holder) {
            buttonDelete.setOnClickListener {
                onDeleteItem(floorPlans[position])
                expandedPosition = -1
                onSelectItem()
            }
            buttonUpdate.setOnClickListener {
                onUpdateItem(floorPlans[position])
            }
            buttonLocalize.setOnClickListener {
                onLocalizeItem(floorPlans[position])
            }
        }
    }

    fun updateCurrentLocation(location: Location) {
        currentLocation = location
        notifyItemRangeChanged(0, itemCount)
    }

    fun resetExpanded() {
        expandedPosition = -1
        notifyDataSetChanged()
        onSelectItem()
    }

    fun updateFloorPlans(updatedFloorPlans: List<FloorPlan>) {
        if (expandedPosition != -1) {
            val expandedItem = floorPlans[expandedPosition]
            updatedFloorPlans.find { it.mainAnchor.cloudAnchorId == expandedItem.mainAnchor.cloudAnchorId }?.let { plan ->
                expandedPosition = updatedFloorPlans.indexOf(plan)
            }
        }
        floorPlans = updatedFloorPlans
        notifyDataSetChanged()
        onSelectItem()
    }

    class ViewHolder(private val binding: ItemFloorPlanListBinding) : RecyclerView.ViewHolder(binding.root) {

        val listItem = binding.floorPlanListItem
        val expandIcon = binding.floorPlanListItemExpandIcon
        val expandArea = binding.floorPlanListItemExpandArea
        val buttonDelete = binding.floorPlanListItemDeleteButton
        val buttonUpdate = binding.floorPlanListItemUpdateButton
        val buttonLocalize = binding.floorPlanListItemLocalizeButton
        val textOwner = binding.floorPlanListItemOwner
        val textDistance = binding.floorPlanListItemDistance

        fun bind(floorPlan: FloorPlan) {
            binding.floorPlanListItemName.text = floorPlan.name
            binding.floorPlanListItemInfo.text = floorPlan.info
            binding.floorPlanListItemCoordinates.text = buildString {
                append("Anchor position: ")
                append(String.format("%.6f", floorPlan.mainAnchor.lat))
                append(", ")
                append(String.format("%.6f", floorPlan.mainAnchor.lng))
                append(" at ")
                append(String.format("%.2f", floorPlan.mainAnchor.alt))
                append("m")
            }
            binding.floorPlanListItemAnchorCount.text = buildString {
                append("Tracking anchors: ")
                append(floorPlan.cloudAnchorList.size)
            }
            binding.floorPlanListItemMappingPointCount.text = buildString {
                append("Mapping points: ")
                append(floorPlan.mappingPointList.size)
            }
        }
    }

    object FloorPlanDiffCallback : DiffUtil.ItemCallback<FloorPlan>() {
        override fun areItemsTheSame(oldItem: FloorPlan, newItem: FloorPlan): Boolean {
            return oldItem.mainAnchor.cloudAnchorId == newItem.mainAnchor.cloudAnchorId
        }

        override fun areContentsTheSame(oldItem: FloorPlan, newItem: FloorPlan): Boolean {
            return oldItem == newItem
        }
    }
}