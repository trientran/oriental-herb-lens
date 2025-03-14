package com.uri.lee.dl.herbdetails.images

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.databinding.ImageUploadItemBinding

class ImagesAdapter(private val onItemClickListener: (Pair<Uri, String>) -> Unit) :
    ListAdapter<Pair<Uri, String>, ImagesAdapter.ItemViewHolder>(DiffUtil()) {

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

    private class DiffUtil : ItemCallback<Pair<Uri, String>>() {
        override fun areItemsTheSame(oldItem: Pair<Uri, String>, newItem: Pair<Uri, String>): Boolean =
            oldItem.first == newItem.first

        override fun areContentsTheSame(oldItem: Pair<Uri, String>, newItem: Pair<Uri, String>): Boolean =
            oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImageUploadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(pair: Pair<Uri, String>) {
            Glide.with(binding.root).load(pair.first).into(binding.imageItem)
            itemView.setOnClickListener { onItemClickListener.invoke(pair) }
        }
    }
}

