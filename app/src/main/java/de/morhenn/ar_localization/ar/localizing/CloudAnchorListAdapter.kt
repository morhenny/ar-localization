package de.morhenn.ar_localization.ar.localizing

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.morhenn.ar_localization.databinding.ItemCloudAnchorListBinding
import de.morhenn.ar_localization.model.CloudAnchor

class CloudAnchorListAdapter(val onClickItem: (item: CloudAnchor) -> Unit) : ListAdapter<CloudAnchor, CloudAnchorListAdapter.ViewHolder>(CloudAnchorListAdapter.CloudAnchorDiffCallback) {

    private val resolvingAnchorIds = mutableListOf<String>()
    private var resolvedAnchor: CloudAnchor? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCloudAnchorListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))

        resolvedAnchor?.let {
            if (it.cloudAnchorId == getItem(position).cloudAnchorId) {
                holder.listItemResolvedIndicator.visibility = View.VISIBLE
            } else {
                holder.listItemResolvedIndicator.visibility = View.GONE
            }
        }
        if (resolvingAnchorIds.contains(getItem(position).cloudAnchorId)) {
            holder.listItemProgressIndicator.visibility = View.VISIBLE
        } else {
            holder.listItemProgressIndicator.visibility = View.GONE
        }
        holder.listItem.setOnClickListener {
            onClickItem(getItem(position))
        }
    }

    fun indicateResolving(listOfAnchorIds: MutableList<String>) {
        resolvingAnchorIds.clear()
        resolvingAnchorIds.addAll(listOfAnchorIds)
        notifyDataSetChanged()
    }

    fun indicateResolved(cloudAnchor: CloudAnchor) {
        resolvingAnchorIds.clear()
        resolvedAnchor = cloudAnchor
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ItemCloudAnchorListBinding) : RecyclerView.ViewHolder(binding.root) {

        val listItem = binding.cloudAnchorListItem
        val listItemProgressIndicator = binding.cloudAnchorListItemProgressIndicator
        val listItemResolvedIndicator = binding.cloudAnchorListItemResolvedIndicator

        fun bind(cloudAnchor: CloudAnchor) {
            binding.cloudAnchorListText.text = buildString {
                append(cloudAnchor.text)
            }
            binding.cloudAnchorListFloor.text = cloudAnchor.floor.toString()
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