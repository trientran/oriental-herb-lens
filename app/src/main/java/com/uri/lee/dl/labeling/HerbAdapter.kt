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

import android.app.Activity
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
class HerbAdapter(private val activity: Activity, private val herbList: List<Herb>) :
    Adapter<HerbAdapter.HerbViewHolder>() {

    class HerbViewHolder private constructor(private val activity: Activity, view: View) :
        RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.herb_image)
        private val titleView: TextView = view.findViewById(R.id.card_title)
        private val subtitleView: TextView = view.findViewById(R.id.card_subtitle_1)
        private val subtitle2View: TextView = view.findViewById(R.id.card_subtitle_2)
        private val searchWithGoogleSci: TextView = view.findViewById(R.id.search_with_google_using_scientific_view)
        private val searchWithGoogleVi: TextView = view.findViewById(R.id.search_with_google_using_vietnamese_view)
        private val imageSize: Int = view.resources.getDimensionPixelOffset(R.dimen.product_item_image_size)

        fun bindHerb(herb: Herb) {
            imageView.setImageDrawable(null)
            if (!TextUtils.isEmpty(herb.imageUrl)) {
                //todo replace with Glide later when loading image from storage
                //   ImageDownloadTask(imageView, imageSize).execute(herb.imageUrl)
            } else {
                imageView.setImageResource(R.drawable.ic_launcher_round)
            }
            titleView.text = herb.id
            subtitleView.text = herb.sciName
            subtitle2View.text = herb.viName
            searchWithGoogleSci.setOnClickListener {
                herb.sciName?.let { it1 -> Utils.openUrlWithDefaultBrowser(activity, it1) }
            }
            searchWithGoogleVi.setOnClickListener {
                herb.viName?.let { it1 -> Utils.openUrlWithDefaultBrowser(activity, it1) }
            }
        }

        companion object {
            fun create(activity: Activity, parent: ViewGroup) =
                HerbViewHolder(
                    activity,
                    LayoutInflater.from(parent.context).inflate(R.layout.single_image_item, parent, false)
                )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HerbViewHolder =
        HerbViewHolder.create(activity, parent)

    override fun onBindViewHolder(holder: HerbViewHolder, position: Int) {
        holder.bindHerb(herbList[position])
    }

    override fun getItemCount(): Int = herbList.size
}
