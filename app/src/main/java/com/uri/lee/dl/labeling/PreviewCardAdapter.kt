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

package com.uri.lee.dl.labeling

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.R

/** Powers the bottom card carousel for displaying the preview of item labeling result.  */
class PreviewCardAdapter(
    private val detectedObjectList: List<DetectedObject>,
    private val previewCordClickedListener: (detectedObject: DetectedObject) -> Any
) : RecyclerView.Adapter<PreviewCardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.herb_preview_card, parent, false)
        )
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val searchedObject = detectedObjectList[position]
        holder.bindItems(searchedObject.herbList)
        holder.itemView.setOnClickListener { previewCordClickedListener.invoke(searchedObject) }
    }

    override fun getItemCount(): Int = detectedObjectList.size

    class CardViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.card_image)
        private val titleView: TextView = itemView.findViewById(R.id.card_title)
        private val subtitle1View: TextView = itemView.findViewById(R.id.card_subtitle_1)
        private val subtitle2View: TextView = itemView.findViewById(R.id.card_subtitle_2)
        private val imageSize: Int = itemView.resources.getDimensionPixelOffset(R.dimen.preview_card_image_size)

        internal fun bindItems(herbs: List<Herb>) {
            if (herbs.isEmpty()) {
                imageView.visibility = View.GONE
                titleView.setText(R.string.static_image_card_no_result_title)
            } else {
                val mostConfidentHerb = herbs[0]
                imageView.visibility = View.VISIBLE
                imageView.setImageDrawable(null)
                if (!TextUtils.isEmpty(mostConfidentHerb.imageUrl)) {
                    //todo replace with Glide later when loading image from storage
                    //  ImageDownloadTask(imageView, imageSize).execute(topProduct.imageUrl)
                } else {
                    imageView.setImageResource(R.drawable.ic_launcher_round)
                }
                titleView.text = mostConfidentHerb.id
                subtitle1View.text = mostConfidentHerb.sciName
                subtitle2View.text = mostConfidentHerb.viName
            }
        }
    }
}
