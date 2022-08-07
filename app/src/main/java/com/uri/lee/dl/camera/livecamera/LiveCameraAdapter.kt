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

package com.uri.lee.dl.camera.livecamera

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.LiveCameraImageRecognitionItemBinding
import com.uri.lee.dl.labeling.Herb

class CameraAdapter(private val context: Context) :
    ListAdapter<Herb, CameraAdapter.ItemViewHolder>(RecognitionDiffUtil()) {

    /**
     * Inflating the ViewHolder with recognition_item layout and data binding
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = LiveCameraImageRecognitionItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    // Binding the data fields to the RecognitionViewHolder
    override fun onBindViewHolder(holderImage: ItemViewHolder, position: Int) {
        holderImage.bindTo(getItem(position), context)
    }

    private class RecognitionDiffUtil : DiffUtil.ItemCallback<Herb>() {
        override fun areItemsTheSame(oldItem: Herb, newItem: Herb): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Herb, newItem: Herb): Boolean = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: LiveCameraImageRecognitionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Binding all the fields to the view - to see which UI element is bind to which field, check
        // out layout/recognition_item.xml
        fun bindTo(herb: Herb, context: Context) {
            binding.idView.text = String.format(
                context.getString(R.string.static_image_classification_result),
                herb.id,
                herb.latinName,
                herb.viName
            )
        }
    }
}
