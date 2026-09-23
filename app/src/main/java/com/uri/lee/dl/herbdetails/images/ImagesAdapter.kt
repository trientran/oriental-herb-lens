package com.uri.lee.dl.herbdetails.images

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.databinding.ImageUploadItemBinding
import com.uri.lee.dl.domain.model.HerbImage

class ImagesAdapter(private val onItemClickListener: (HerbImage) -> Unit) :
    ListAdapter<HerbImage, ImagesAdapter.ItemViewHolder>(DiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(ImageUploadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bindTo(getItem(position) ?: return)
    }

    private class DiffUtil : ItemCallback<HerbImage>() {
        override fun areItemsTheSame(oldItem: HerbImage, newItem: HerbImage) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: HerbImage, newItem: HerbImage) = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImageUploadItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindTo(image: HerbImage) {
            Glide.with(binding.root).load(image.url).into(binding.imageItem)
            itemView.setOnClickListener { onItemClickListener(image) }
        }
    }
}
