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
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.algolia.instantsearch.android.list.autoScrollToStart
import com.algolia.instantsearch.android.paging3.liveData
import com.algolia.instantsearch.android.searchbox.SearchBoxViewAppCompat
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.connectView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
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
    private val handler = Handler()

    private lateinit var auth: FirebaseAuth
    private val authUI = AuthUI.getInstance()
    private val signInLauncher =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { onSignInResult(it) }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        auth = Firebase.auth

        binding.modeRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ModeItemAdapter(DetectionMode.values())
        }

        setupAlgoliaSearch(view)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view is SearchView.SearchAutoComplete) {
                val outRect = Rect()
                view.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    binding.scrollView.requestFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            val signInIntent = authUI
                .createSignInIntentBuilder()
                .setAvailableProviders(arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build()))
                .setLogo(R.drawable.ic_launcher_round)
                .setTheme(R.style.AppTheme)
                .build()
            signInLauncher.launch(signInIntent)
        }
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

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            val user = auth.currentUser
            require(user != null)
            // todo write to FireStore user
        }
    }

    private fun signOut() {
        authUI
            .signOut(this)
            .addOnCompleteListener {
                // ...
            }
    }

    private fun delete() {
        authUI
            .delete(this)
            .addOnCompleteListener {
                // ...
            }
    }

    private fun setupAlgoliaSearch(view: ConstraintLayout) {
        val searchResultAdapter = MyAdapter()
        searchResultAdapter.onItemClick = {
            val intent = Intent(this@MainActivity, HerbDetailsActivity::class.java)
            intent.putExtra("INSTANT_HERB", it)
            startActivity(intent)
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

        val searchAutoComplete: SearchView.SearchAutoComplete =
            binding.searchView.findViewById(androidx.appcompat.R.id.search_src_text)
        searchAutoComplete.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.herbSearchList.isVisible = true
            } else {
                hideSoftKeyboard(view)
                handler.postDelayed({ binding.herbSearchList.isVisible = false }, 200)
            }
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

            fun bindDetectionMode(detectionMode: DetectionMode) {
                titleView.setText(detectionMode.titleResId)
                subtitleView.setText(detectionMode.subtitleResId)
                itemView.setOnClickListener {
                    val activity = this@MainActivity
                    when (detectionMode) {
                        DetectionMode.ODT_LIVE ->
                            activity.startActivity(Intent(activity, LiveObjectDetectionActivity::class.java))
                        DetectionMode.ODT_STATIC -> Utils.openImagePicker(activity)
                        DetectionMode.CUSTOM_MODEL_LIVE ->
                            activity.startActivity(
                                Intent(
                                    activity,
                                    CustomModelObjectDetectionActivity::class.java
                                )
                            )
                    }
                }
            }
        }
    }
}
