package com.uri.lee.dl.hometabs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.FireStoreHerb
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.hometabs.HerbAdapter.TabbedHerbItemViewHolder

class HerbAdapter(private val onItemClickListener: (FireStoreHerb) -> Unit) :
    ListAdapter<FireStoreHerb, TabbedHerbItemViewHolder>(HerbDiffUtil()) {

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

    private class HerbDiffUtil : DiffUtil.ItemCallback<FireStoreHerb>() {
        override fun areItemsTheSame(oldItem: FireStoreHerb, newItem: FireStoreHerb): Boolean {
            // Id is unique.
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FireStoreHerb, newItem: FireStoreHerb): Boolean {
            return oldItem == newItem
        }
    }

    inner class TabbedHerbItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(herb: FireStoreHerb) {
            Glide.with(binding.root).load(herb.images?.keys?.random() ?: R.drawable.ic_launcher).into(binding.imageView)
            itemView.setOnClickListener { onItemClickListener.invoke(herb) }
            binding.idView.text = herb.id.toString()
            binding.latinNameView.text = herb.latinName
            binding.viNameView.text = herb.viName
        }
    }
}
