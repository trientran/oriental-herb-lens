package com.uri.lee.dl.hometabs

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.UserViewModel
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.hometabs.HerbAdapter.TabbedHerbItemViewHolder

class HerbAdapter(private val viewModel: UserViewModel, private val onItemClickListener: (Long) -> Unit) :
    ListAdapter<Long, TabbedHerbItemViewHolder>(HerbDiffUtil()) {

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
        val herbId = getItem(position) ?: return
        holderImage.bindTo(herbId)
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<Long>() {
        override fun areItemsTheSame(oldItem: Long, newItem: Long): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Long, newItem: Long): Boolean {
            return oldItem == newItem
        }
    }

    inner class TabbedHerbItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(herbId: Long) {
            Log.d("trienn", herbId.toString())
            viewModel.loadSingleHerb(herbId) { herb ->
                Glide
                    .with(binding.root)
                    .load(herb.images?.keys?.random() ?: R.drawable.ic_launcher)
                    .into(binding.imageView)
                itemView.setOnClickListener { onItemClickListener.invoke(herbId) }
                binding.idView.text = herb.id.toString()
                binding.latinNameView.text = herb.latinName
                binding.viNameView.text = herb.viName
            }
        }
    }
}
