package com.uri.lee.dl.hometabs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.hometabs.HerbAdapter.TabbedHerbItemViewHolder
import com.uri.lee.dl.instantsearch.Herb

class HerbAdapter(private val onItemClickListener: (Herb) -> Unit) :
    ListAdapter<Herb, TabbedHerbItemViewHolder>(HerbDiffUtil()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TabbedHerbItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ImagesRecognitionItemBinding.inflate(inflater, parent, false)
        return TabbedHerbItemViewHolder(binding)
    }

    // Binding the data fields to the HerbViewHolder
    override fun onBindViewHolder(holderImage: TabbedHerbItemViewHolder, position: Int) {
        val herb = getItem(position) ?: return
        holderImage.bindTo(herb)
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<Herb>() {
        override fun areItemsTheSame(oldItem: Herb, newItem: Herb): Boolean {
            // Id is unique.
            return oldItem.objectID == newItem.objectID
        }

        override fun areContentsTheSame(oldItem: Herb, newItem: Herb): Boolean {
            return oldItem == newItem
        }
    }

    inner class TabbedHerbItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(herb: Herb) {
            val thumbnail = R.drawable.ic_launcher
            Glide.with(binding.root).load(thumbnail).into(binding.imageView)
            itemView.setOnClickListener { onItemClickListener.invoke(herb) }
            binding.idView.text = herb.objectID
            binding.latinNameView.text = herb.latinName
            binding.viNameView.text = herb.viName
        }
    }
}
