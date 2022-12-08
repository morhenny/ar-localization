package de.morhenn.ar_localization.ar.localizing

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.morhenn.ar_localization.databinding.ItemCloudAnchorListBinding
import de.morhenn.ar_localization.model.CloudAnchor

class CloudAnchorListAdapter(val onClickItem: (item: CloudAnchor) -> Unit) : ListAdapter<CloudAnchor, CloudAnchorListAdapter.ViewHolder>(CloudAnchorListAdapter.CloudAnchorDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCloudAnchorListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.listItem.setOnClickListener {
            onClickItem(getItem(position))
        }
    }

    class ViewHolder(private val binding: ItemCloudAnchorListBinding) : RecyclerView.ViewHolder(binding.root) {

        val listItem = binding.cloudAnchorListItem
        fun bind(cloudAnchor: CloudAnchor) {
            binding.cloudAnchorListText.text = cloudAnchor.text
            binding.cloudAnchorListFloor.text = buildString {
                append("Floor: ")
                append(cloudAnchor.floor)
            }
        }
    }

    object CloudAnchorDiffCallback : DiffUtil.ItemCallback<CloudAnchor>() {
        override fun areItemsTheSame(oldItem: CloudAnchor, newItem: CloudAnchor): Boolean {
            return oldItem.cloudAnchorId == newItem.cloudAnchorId
        }

        override fun areContentsTheSame(oldItem: CloudAnchor, newItem: CloudAnchor): Boolean {
            return oldItem == newItem
        }
    }
}