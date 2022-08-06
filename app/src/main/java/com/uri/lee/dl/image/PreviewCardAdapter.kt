/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uri.lee.dl.image

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.uri.lee.dl.R
import com.uri.lee.dl.labeling.DetectedBitmapObject

/** Powers the bottom card carousel for displaying the preview of item labeling result.  */
class PreviewCardAdapter(
    private val detectedBitmapObjectList: List<DetectedBitmapObject>,
    private val previewCordClickedListener: (detectedBitmapObject: DetectedBitmapObject) -> Any
) : RecyclerView.Adapter<PreviewCardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.herb_preview_card, parent, false)
        )
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val searchedObject = detectedBitmapObjectList[position]
        holder.bindItems(searchedObject)
        holder.itemView.setOnClickListener { previewCordClickedListener.invoke(searchedObject) }
    }

    override fun getItemCount(): Int = detectedBitmapObjectList.size

    class CardViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.herbImage)
        private val idView: TextView = itemView.findViewById(R.id.herbIdView)
        private val latinView: TextView = itemView.findViewById(R.id.latinView)
        private val vietnameseView: TextView = itemView.findViewById(R.id.vietnameseView)

        internal fun bindItems(bitmapObject: DetectedBitmapObject) {
            if (bitmapObject.detectedObject.herbs.isNullOrEmpty()) {
                imageView.visibility = View.GONE
                idView.setText(R.string.static_image_card_no_result_title)
            } else {
                val mostConfidentHerb = bitmapObject.detectedObject.herbs[0]
                imageView.visibility = View.VISIBLE
                Glide.with(itemView.context).load(bitmapObject.getObjectThumbnail())
                    .into(imageView) // bitmapObject.getObjectThumbnail()
                idView.text = String.format(itemView.context.getString(R.string.herb_id), mostConfidentHerb.id ?: "")
                latinView.text = mostConfidentHerb.latinName ?: ""
                vietnameseView.text = mostConfidentHerb.viName ?: ""
            }
        }
    }
}
