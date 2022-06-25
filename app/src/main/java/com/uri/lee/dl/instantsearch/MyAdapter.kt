package com.uri.lee.dl.instantsearch

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.algolia.instantsearch.android.highlighting.toSpannedString
import com.algolia.instantsearch.android.inflate
import com.uri.lee.dl.R
import java.util.*

class MyAdapter : PagingDataAdapter<Herb, MyAdapter.ProductViewHolder>(ProductDiffUtil) {

    var onItemClick: ((Herb) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        return ProductViewHolder(parent.inflate(R.layout.search_item))
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    object ProductDiffUtil : DiffUtil.ItemCallback<Herb>() {
        override fun areItemsTheSame(oldItem: Herb, newItem: Herb) = oldItem.objectID == newItem.objectID
        override fun areContentsTheSame(oldItem: Herb, newItem: Herb) = oldItem == newItem
    }

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val itemName = view.findViewById<TextView>(R.id.itemName)

        fun bind(herb: Herb) {
            val itemNamePerSystemLanguage =
                if (Locale.getDefault().displayLanguage == "English") herb.sciName else herb.viName
            itemName.text = herb.highlightedName?.toSpannedString() ?: itemNamePerSystemLanguage
            itemName.setOnClickListener {
                onItemClick?.invoke(herb)
            }
        }
    }
}

