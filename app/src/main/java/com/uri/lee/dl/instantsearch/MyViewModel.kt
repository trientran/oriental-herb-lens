package com.uri.lee.dl.instantsearch

import androidx.lifecycle.ViewModel
import androidx.paging.PagingConfig
import com.algolia.instantsearch.android.paging3.Paginator
import com.algolia.instantsearch.android.paging3.searchbox.connectPaginator
import com.algolia.instantsearch.core.connection.ConnectionHandler
import com.algolia.instantsearch.searchbox.SearchBoxConnector
import com.algolia.instantsearch.searcher.hits.HitsSearcher
import com.algolia.instantsearch.stats.StatsConnector
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName

class MyViewModel : ViewModel() {

    val searcher = HitsSearcher(
        applicationID = ApplicationID("5NJQS1P6W8"),
        apiKey = APIKey("d9e7dbc620110c895008c72c6809f6e4"),
        indexName = IndexName("herbs")
    )

    val paginator = Paginator(
        searcher = searcher,
        pagingConfig = PagingConfig(pageSize = 50, enablePlaceholders = false),
        transformer = { hit -> hit.deserialize(Herb.serializer()) }
    )

    val searchBox = SearchBoxConnector(searcher)
    val stats = StatsConnector(searcher)
    val connection = ConnectionHandler(searchBox, stats)

    init {
        connection += searchBox.connectPaginator(paginator)
    }

    override fun onCleared() {
        super.onCleared()
        searcher.cancel()
        connection.clear()
    }
}
