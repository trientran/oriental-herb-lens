package com.uri.lee.dl.instantsearch

import androidx.lifecycle.ViewModel
import androidx.paging.PagingConfig
import com.uri.lee.dl.BuildConfig
import com.algolia.instantsearch.android.paging3.Paginator
import com.algolia.instantsearch.android.paging3.searchbox.connectPaginator
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.SearchBoxConnector
import com.algolia.instantsearch.searcher.hits.HitsSearcher
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName

class MyViewModel : ViewModel() {

    private val searcher = HitsSearcher(
        applicationID = ApplicationID(BuildConfig.ALGOLIA_APP_ID),
        apiKey = APIKey(BuildConfig.ALGOLIA_SEARCH_API_KEY),
        indexName = IndexName("herbs")
    )

    val paginator = Paginator(
        searcher = searcher,
        pagingConfig = PagingConfig(pageSize = 50, enablePlaceholders = false),
        transformer = { hit -> hit.deserialize(Herb.serializer()) }
    )

    val searchBox = SearchBoxConnector(searcher)
    private val connection = ConnectionHandler(searchBox)

    init {
        connection += searchBox.connectPaginator(paginator)
    }

    override fun onCleared() {
        super.onCleared()
        searcher.cancel()
        connection.clear()
    }
}
