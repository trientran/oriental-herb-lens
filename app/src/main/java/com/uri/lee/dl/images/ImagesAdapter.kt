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

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.labeling.Herb

class ImagesAdapter(private val context: Context, private val onItemClickListener: (Herb) -> Unit) :
    ListAdapter<Herb, ImagesHerbItemViewHolder>(HerbDiffUtil()) {

    /**
     * Inflating the ViewHolder with Herb_item layout and data binding
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImagesHerbItemViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = ImagesRecognitionItemBinding.inflate(inflater, parent, false)
        return ImagesHerbItemViewHolder(binding)
    }

    // Binding the data fields to the HerbViewHolder
    override fun onBindViewHolder(holderImage: ImagesHerbItemViewHolder, position: Int) {
        holderImage.bindTo(getItem(position), onItemClickListener)
    }

    private class HerbDiffUtil : DiffUtil.ItemCallback<Herb>() {
        override fun areItemsTheSame(oldItem: Herb, newItem: Herb): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Herb, newItem: Herb): Boolean {
            return oldItem.sciName == newItem.sciName && oldItem.viName == newItem.viName
        }
    }
}

class ImagesHerbItemViewHolder(private val binding: ImagesRecognitionItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    // Binding all the fields to the view - to see which UI element is bind to which field, check
    // out layout/Herb_item.xml
    fun bindTo(herb: Herb, onItemClickListener: (Herb) -> Unit) {
        Glide.with(binding.root).load(herb.imageFileUri).into(binding.imageView)
        binding.idView.text = herb.id ?: binding.root.context?.getString(R.string.no_result)
        binding.sciNameView.text = herb.sciName
        binding.viNameView.text = herb.viName
        itemView.setOnClickListener { onItemClickListener.invoke(herb) }
    }
}
