package de.morhenn.ar_localization.floorPlan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.ItemFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.utils.SimpleEvent

class FloorPlanListAdapter() : ListAdapter<FloorPlan, FloorPlanListAdapter.ViewHolder>(FloorPlanDiffCallback) {

    var expandedPosition = -1

    private val _selectedFloorPlanChanged = MutableLiveData<SimpleEvent>()
    val selectedFloorPlanChanged: LiveData<SimpleEvent>
        get() = _selectedFloorPlanChanged

    private val _deleteSelectedFloorPlan = MutableLiveData<SimpleEvent>()
    val deleteSelectedFloorPlan: LiveData<SimpleEvent>
        get() = _deleteSelectedFloorPlan

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFloorPlanListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
            _selectedFloorPlanChanged.value = SimpleEvent()
            if (lastPos >= 0) notifyItemChanged(lastPos)
            notifyItemChanged(position)
        }
        holder.buttonDelete.setOnClickListener {
            _deleteSelectedFloorPlan.value = SimpleEvent()
            expandedPosition = -1
            _selectedFloorPlanChanged.value = SimpleEvent()
        }
    }

    class ViewHolder(private val binding: ItemFloorPlanListBinding) : RecyclerView.ViewHolder(binding.root) {

        val listItem = binding.floorPlanListItem
        val expandIcon = binding.floorPlanListItemExpandIcon
        val expandArea = binding.floorPlanListItemExpandArea
        val buttonDelete = binding.floorPlanListItemDeleteButton

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