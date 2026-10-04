package com.uri.lee.dl.herbdetails.images

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.databinding.ImageUploadItemBinding
import com.uri.lee.dl.domain.model.SpeciesPhoto

class ImagesAdapter(private val onItemClickListener: (SpeciesPhoto) -> Unit) :
    ListAdapter<SpeciesPhoto, ImagesAdapter.ItemViewHolder>(DiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(ImageUploadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bindTo(getItem(position) ?: return)
    }

    private class DiffUtil : ItemCallback<SpeciesPhoto>() {
        override fun areItemsTheSame(oldItem: SpeciesPhoto, newItem: SpeciesPhoto) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: SpeciesPhoto, newItem: SpeciesPhoto) = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImageUploadItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindTo(image: SpeciesPhoto) {
            Glide.with(binding.root).load(image.thumbnailUrl).into(binding.imageItem)
            itemView.setOnClickListener { onItemClickListener(image) }
        }
    }
}
