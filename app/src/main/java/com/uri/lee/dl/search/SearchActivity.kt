package com.uri.lee.dl.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivitySearchBinding
import com.uri.lee.dl.herbdetails.HerbDetailsActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Searches the species catalog on the device. */
class SearchActivity : AppCompatActivity() {

    private val viewModel: SearchViewModel by viewModel()
    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.closeView.setOnClickListener { finish() }
        binding.microphoneView.setOnClickListener { Utils.displaySpeechRecognizer(this) }

        val adapter = SearchResultAdapter { speciesId ->
            startActivity(Intent(this, HerbDetailsActivity::class.java).putExtra(HERB_ID, speciesId))
        }
        binding.herbSearchList.itemAnimator = null
        binding.herbSearchList.layoutManager = LinearLayoutManager(this)
        binding.herbSearchList.adapter = adapter

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false.also { viewModel.onAction(SearchAction.QueryChanged(query)) }
            override fun onQueryTextChange(newText: String) = true.also { viewModel.onAction(SearchAction.QueryChanged(newText)) }
        })
        binding.searchView.isIconified = false
        binding.searchView.requestFocus()
        intent.getStringExtra(SPOKEN_TEXT_EXTRA)?.let { binding.searchView.setQuery(it, false) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.results }
                    .distinctUntilChanged()
                    .collect { results ->
                        adapter.submitList(results) { binding.herbSearchList.scrollToPosition(0) }
                    }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == Utils.SPEECH_REQUEST_CODE) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                binding.searchView.setQuery(it, false)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

const val SPOKEN_TEXT_EXTRA = "spokenTextExtra"
