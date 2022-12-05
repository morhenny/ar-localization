package de.morhenn.ar_localization.floorPlan

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.ItemFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.utils.SimpleEvent

class FloorPlanListAdapter() : ListAdapter<FloorPlan, FloorPlanListAdapter.ViewHolder>(FloorPlanDiffCallback) {

    var expandedPosition = -1

    var currentLocation: Location? = null

    private val _selectedFloorPlanChanged = MutableLiveData<SimpleEvent>()
    val selectedFloorPlanChanged: LiveData<SimpleEvent>
        get() = _selectedFloorPlanChanged

    private val _deleteSelectedFloorPlan = MutableLiveData<SimpleEvent>()
    val deleteSelectedFloorPlan: LiveData<SimpleEvent>
        get() = _deleteSelectedFloorPlan

    private val _updateSelectedFloorPlan = MutableLiveData<SimpleEvent>()
    val updateSelectedFloorPlan: LiveData<SimpleEvent>
        get() = _updateSelectedFloorPlan

    private val _localizeSelectedFloorPlan = MutableLiveData<SimpleEvent>()
    val localizeSelectedFloorPlan: LiveData<SimpleEvent>
        get() = _localizeSelectedFloorPlan

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
        Firebase.auth.currentUser?.let {
            with((it.uid == getItem(position).ownerUID)) {
                holder.buttonDelete.visibility = if (this) View.VISIBLE else View.GONE
                holder.buttonUpdate.visibility = if (this) View.VISIBLE else View.GONE
                holder.textOwner.visibility = if (this) View.VISIBLE else View.GONE
            }
        }

        currentLocation?.let {
            val loc = Location("")
            loc.latitude = getItem(position).mainAnchor.lat
            loc.longitude = getItem(position).mainAnchor.lng
            holder.textDistance.text = String.format("%.2f m", it.distanceTo(loc))
        }

        with(holder) {
            buttonDelete.setOnClickListener {
                _deleteSelectedFloorPlan.value = SimpleEvent()
                expandedPosition = -1
                _selectedFloorPlanChanged.value = SimpleEvent()
            }
            buttonUpdate.setOnClickListener {
                _updateSelectedFloorPlan.value = SimpleEvent()
            }
            buttonLocalize.setOnClickListener {
                _localizeSelectedFloorPlan.value = SimpleEvent()
            }
        }
    }

    fun updateCurrentLocation(location: Location) {
        currentLocation = location
        notifyItemRangeChanged(0, itemCount)
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