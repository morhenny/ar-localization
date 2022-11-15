package de.morhenn.ar_localization.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.morhenn.ar_localization.databinding.ItemFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan

class FloorPlanListAdapter(private val floorPlanList: List<FloorPlan>) : RecyclerView.Adapter<FloorPlanListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFloorPlanListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(floorPlanList[position], position)
    }

    override fun getItemCount(): Int {
        return floorPlanList.size
    }

    class ViewHolder(private val binding: ItemFloorPlanListBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(floorPlan: FloorPlan, position: Int) {
            binding.floorPlanListItemName.text = floorPlan.name
            binding.floorPlanListItemInfo.text = floorPlan.info
        }
    }

}