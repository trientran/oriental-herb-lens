package com.uri.lee.dl.upload

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.databinding.ImageUploadItemBinding

class ImageUploadAdapter(private val onItemClickListener: (Uri) -> Unit) :
    ListAdapter<Uri, ImageUploadAdapter.ItemViewHolder>(DiffUtil()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ImageUploadItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holderImage: ItemViewHolder, position: Int) {
        val uri = getItem(position) ?: return
        holderImage.bindTo(uri)
    }

    private class DiffUtil : ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImageUploadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(uri: Uri) {
            Glide.with(binding.root).load(uri).into(binding.imageItem)
            itemView.setOnClickListener { onItemClickListener.invoke(uri) }
        }
    }
}

