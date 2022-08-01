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
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils

/** Presents the list of herb items as labeling results.  */
class HerbAdapter(private val herbList: List<Herb>) :
    Adapter<HerbAdapter.HerbViewHolder>() {

    class HerbViewHolder(val view: View) :
        RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.herb_image)
        private val idView: TextView = view.findViewById(R.id.herbIdView)
        private val latinView: TextView = view.findViewById(R.id.latinView)
        private val vietnameseView: TextView = view.findViewById(R.id.vietnameseView)
        private val searchWithGoogleLatin: TextView = view.findViewById(R.id.search_with_google_using_latin_view)
        private val searchWithGoogleVi: TextView = view.findViewById(R.id.search_with_google_using_vietnamese_view)

        fun bindHerb(herb: Herb) {
            imageView.setImageDrawable(null)
            if (!TextUtils.isEmpty(herb.imageUrl)) {
                //todo replace with Glide later when loading image from storage
                //   ImageDownloadTask(imageView, imageSize).execute(herb.imageUrl)
            } else {
                imageView.setImageResource(R.drawable.ic_launcher_round)
            }
            idView.text = String.format(itemView.context.getString(R.string.herb_id), herb.id ?: "")
            latinView.text = String.format(itemView.context.getString(R.string.latin_name), herb.latinName ?: "")
            vietnameseView.text = String.format(itemView.context.getString(R.string.vietnamese_name), herb.viName ?: "")
            searchWithGoogleLatin.setOnClickListener {
                herb.latinName?.let { name -> Utils.openUrlWithDefaultBrowser(view.context, name) }
            }
            searchWithGoogleVi.setOnClickListener {
                herb.viName?.let { name -> Utils.openUrlWithDefaultBrowser(view.context, name) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HerbViewHolder =
        HerbViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.single_image_item, parent, false)
        )

    override fun onBindViewHolder(holder: HerbViewHolder, position: Int) {
        holder.bindHerb(herbList[position])
    }

    override fun getItemCount(): Int = herbList.size
}
