package com.uri.lee.dl.hometabs

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.domain.model.HerbSummary

class HerbSummaryAdapter(private val onItemClick: (herbId: Long) -> Unit) :
    ListAdapter<HerbSummary, HerbSummaryAdapter.ItemViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(ImagesRecognitionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bindTo(getItem(position))

    private object Diff : DiffUtil.ItemCallback<HerbSummary>() {
        override fun areItemsTheSame(oldItem: HerbSummary, newItem: HerbSummary) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HerbSummary, newItem: HerbSummary) = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImagesRecognitionItemBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n") // an identifier, not a quantity: no locale formatting
        fun bindTo(herb: HerbSummary) {
            Glide.with(binding.root).load(herb.imageUrl ?: R.drawable.ic_launcher).into(binding.imageView)
            itemView.setOnClickListener { onItemClick(herb.id) }
            binding.idView.text = herb.id.toString()
            binding.latinNameView.text = herb.latinName
            binding.viNameView.text = herb.vietnameseName
        }
    }
}
