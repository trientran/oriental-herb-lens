package com.uri.lee.dl.herbdetails

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.databinding.ImageUploadItemBinding

class ImageUploadAdapter(private val onItemClickListener: (Uri) -> Unit = {}) :
    ListAdapter<Uri, ImageUploadAdapter.ItemViewHolder>(HerbDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ImageUploadItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    // Binding the data fields to the HerbViewHolder
    override fun onBindViewHolder(holderImage: ItemViewHolder, position: Int) {
        holderImage.bindTo(getItem(position))
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImageUploadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(Uri: Uri) {
            Glide.with(binding.root).load(Uri).into(binding.imageItem)
            //  itemView.setOnClickListener { onItemClickListener.invoke(recognition) }
        }
    }
}
