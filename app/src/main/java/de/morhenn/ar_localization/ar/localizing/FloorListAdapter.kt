package de.morhenn.ar_localization.ar.localizing

import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.morhenn.ar_localization.databinding.ItemFloorListBinding

class FloorListAdapter(val onClickFloorItem: (floor: Int, dialog: Dialog) -> Unit, val dialog: Dialog) : ListAdapter<Int, FloorListAdapter.ViewHolder>(FloorListAdapter.FloorDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFloorListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))

        holder.listItem.setOnClickListener {
            onClickFloorItem(getItem(position), dialog)
        }
    }

    class ViewHolder(private val binding: ItemFloorListBinding) : RecyclerView.ViewHolder(binding.root) {
        val listItem = binding.itemFloorListCardView

        fun bind(floor: Int) {
            binding.itemFloorListFloor.text = floor.toString()
        }
    }

    object FloorDiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }
    }
}