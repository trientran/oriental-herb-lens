/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uri.lee.dl.images

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.images.ImagesState.Recognition

class ImagesAdapter(private val onItemClickListener: (Recognition) -> Unit) :
    ListAdapter<Recognition, ImagesHerbItemViewHolder>(HerbDiffUtil()) {

    /**
     * Inflating the ViewHolder with Herb_item layout and data binding
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImagesHerbItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ImagesRecognitionItemBinding.inflate(inflater, parent, false)
        return ImagesHerbItemViewHolder(binding)
    }

    // Binding the data fields to the HerbViewHolder
    override fun onBindViewHolder(holderImage: ImagesHerbItemViewHolder, position: Int) {
        holderImage.bindTo(getItem(position), onItemClickListener)
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<Recognition>() {
        override fun areItemsTheSame(oldItem: Recognition, newItem: Recognition): Boolean {
            return oldItem.fileUri == newItem.fileUri
        }

        override fun areContentsTheSame(oldItem: Recognition, newItem: Recognition): Boolean {
            return oldItem.herbs == newItem.herbs
        }
    }
}

class ImagesHerbItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bindTo(recognition: Recognition, onItemClickListener: (Recognition) -> Unit) {
        Glide.with(binding.root).load(recognition.fileUri).into(binding.imageView)
        itemView.setOnClickListener { onItemClickListener.invoke(recognition) }
        if (recognition.herbs.isEmpty()) {
            binding.idView.text = binding.root.context?.getString(R.string.no_result)
            binding.latinNameView.text = ""
            binding.viNameView.text = ""
        } else {
            binding.idView.text = recognition.herbs.first().id ?: ""
            binding.latinNameView.text = recognition.herbs.first().latinName ?: ""
            binding.viNameView.text = recognition.herbs.first().viName ?: ""
        }
    }
}
