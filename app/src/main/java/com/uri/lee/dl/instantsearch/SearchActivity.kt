package com.uri.lee.dl.instantsearch

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.algolia.instantsearch.android.list.autoScrollToStart
import com.algolia.instantsearch.android.paging3.liveData
import com.algolia.instantsearch.android.searchbox.SearchBoxViewAppCompat
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.connectView
import com.uri.lee.dl.HerbDetailsActivity
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {
    private val viewModel: MyViewModel by viewModels()
    private val connection = ConnectionHandler()
    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        binding.closeView.setOnClickListener { finish() }

        setupAlgoliaSearch()
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.clear()
    }

    private fun setupAlgoliaSearch() {
        val searchResultAdapter = MyAdapter()
        searchResultAdapter.onItemClick = {
            val intent = Intent(this, HerbDetailsActivity::class.java)
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
        searchAutoComplete.requestFocus()
    }
}
