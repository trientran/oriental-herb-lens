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

package com.uri.lee.dl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.algolia.instantsearch.android.list.autoScrollToStart
import com.algolia.instantsearch.android.paging3.liveData
import com.algolia.instantsearch.android.searchbox.SearchBoxViewAppCompat
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.connectView
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.instantsearch.MyAdapter
import com.uri.lee.dl.instantsearch.MyViewModel

/** Entry activity to select the detection mode.  */
class MainActivity : AppCompatActivity() {

    private val viewModel: MyViewModel by viewModels()
    private val connection = ConnectionHandler()

    private enum class DetectionMode(val titleResId: Int, val subtitleResId: Int) {
        ODT_LIVE(R.string.mode_odt_live_title, R.string.mode_odt_live_subtitle),
        ODT_STATIC(R.string.mode_odt_static_title, R.string.mode_odt_static_subtitle),
        CUSTOM_MODEL_LIVE(R.string.custom_model_live_title, R.string.custom_model_live_subtitle)
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        binding.modeRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ModeItemAdapter(DetectionMode.values())
        }

        val searchResultAdapter = MyAdapter()
        searchResultAdapter.onItemClick = {
            // todo navigate to detail screen with herb as bundle
        }
        viewModel.paginator.liveData.observe(this) {
            searchResultAdapter.submitData(lifecycle, it)
        }
        binding.herbSearchList.let {
            it.itemAnimator = null
            it.adapter = searchResultAdapter
            it.layoutManager = LinearLayoutManager(this)
            it.autoScrollToStart(searchResultAdapter)
        }

        val searchBoxView = SearchBoxViewAppCompat(binding.searchView)
        connection += viewModel.searchBox.connectView(searchBoxView)

        // val statsView = StatsTextView(binding.stats)
        //   connection += viewModel.stats.connectView(statsView, DefaultStatsPresenter())
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.clear()
    }

    override fun onResume() {
        super.onResume()
        if (!Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY &&
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {
            val intent = Intent(this, StaticObjectDetectionActivity::class.java)
            intent.data = data.data
            startActivity(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private inner class ModeItemAdapter internal constructor(private val detectionModes: Array<DetectionMode>) :
        RecyclerView.Adapter<ModeItemAdapter.ModeItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeItemViewHolder {
            return ModeItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.detection_mode_item, parent, false
                    )
            )
        }

        override fun onBindViewHolder(modeItemViewHolder: ModeItemViewHolder, position: Int) =
            modeItemViewHolder.bindDetectionMode(detectionModes[position])

        override fun getItemCount(): Int = detectionModes.size

        private inner class ModeItemViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            private val titleView: TextView = view.findViewById(R.id.mode_title)
            private val subtitleView: TextView = view.findViewById(R.id.mode_subtitle)

            internal fun bindDetectionMode(detectionMode: DetectionMode) {
                titleView.setText(detectionMode.titleResId)
                subtitleView.setText(detectionMode.subtitleResId)
                itemView.setOnClickListener {
                    val activity = this@MainActivity
                    when (detectionMode) {
                        DetectionMode.ODT_LIVE ->
                            activity.startActivity(Intent(activity, LiveObjectDetectionActivity::class.java))
                        DetectionMode.ODT_STATIC -> Utils.openImagePicker(activity)
                        DetectionMode.CUSTOM_MODEL_LIVE ->
                            activity.startActivity(Intent(activity, com.uri.lee.dl.CustomModelObjectDetectionActivity::class.java))
                    }
                }
            }
        }
    }
}
