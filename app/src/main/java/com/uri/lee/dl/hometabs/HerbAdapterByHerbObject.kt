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

class HerbAdapterByHerbObject(private val onItemClickListener: (Long) -> Unit) :
    ListAdapter<FireStoreHerb, HerbAdapterByHerbObject.ItemViewHolder>(HerbDiffUtil()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ImagesRecognitionItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    // Binding the data fields to the HerbViewHolder
    override fun onBindViewHolder(holderImage: ItemViewHolder, position: Int) {
        val herbId = getItem(position) ?: return
        holderImage.bindTo(herbId)
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<FireStoreHerb>() {
        override fun areItemsTheSame(oldItem: FireStoreHerb, newItem: FireStoreHerb): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FireStoreHerb, newItem: FireStoreHerb): Boolean {
            return oldItem == newItem
        }
    }

    inner class ItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(herb: FireStoreHerb) {
            Glide
                .with(binding.root)
                .load(herb.images?.keys?.random() ?: R.drawable.ic_launcher)
                .into(binding.imageView)
            itemView.setOnClickListener { herb.id?.let(onItemClickListener) }
            binding.idView.text = herb.id.toString()
            binding.latinNameView.text = herb.latinName
            binding.viNameView.text = herb.viName
        }
    }
}
