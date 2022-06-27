package com.uri.lee.dl

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.algolia.instantsearch.android.list.autoScrollToStart
import com.algolia.instantsearch.android.paging3.liveData
import com.algolia.instantsearch.android.searchbox.SearchBoxViewAppCompat
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.connectView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.instantsearch.MyAdapter
import com.uri.lee.dl.instantsearch.MyViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MyViewModel by viewModels()
    private val connection = ConnectionHandler()

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler()

    private val authUI = AuthUI.getInstance()
    private val signInLauncher =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { onSignInResult(it) }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        binding.menuView.setOnClickListener {
            val bottomSheet = BottomSheetDialog()
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        binding.searchCameraView.setOnClickListener {
            startActivity(Intent(this, LiveObjectDetectionActivity::class.java))
        }

        binding.searchMultiImagesView.setOnClickListener {

        }

        binding.searchSingleImageView.setOnClickListener { Utils.openImagePicker(this) }

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
        if (authUI.auth.currentUser == null) {
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
            val user = authUI.auth.currentUser
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
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, StaticObjectDetectionActivity::class.java)
            intent.data = data.data
            startActivity(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
