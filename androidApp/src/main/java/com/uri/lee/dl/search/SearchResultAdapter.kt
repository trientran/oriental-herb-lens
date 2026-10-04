package com.uri.lee.dl.search

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.R
import com.uri.lee.dl.domain.search.NameKind
import com.uri.lee.dl.domain.search.SpeciesMatch

/** Scientific name on the first line, the matched (or preferred) common name on the second. */
class SearchResultAdapter(private val onClick: (speciesId: Long) -> Unit) :
    ListAdapter<SpeciesMatch, SearchResultAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.search_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<SpeciesMatch>() {
        override fun areItemsTheSame(oldItem: SpeciesMatch, newItem: SpeciesMatch) = oldItem.species.id == newItem.species.id
        override fun areContentsTheSame(oldItem: SpeciesMatch, newItem: SpeciesMatch) = oldItem == newItem
    }

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val scientific: TextView = view.findViewById(R.id.itemNameLatin)
        private val common: TextView = view.findViewById(R.id.itemNameVi)

        fun bind(match: SpeciesMatch) = with(match) {
            val sciHighlight = highlight.takeIf { kind == NameKind.SCIENTIFIC }
            scientific.text = styled(species.scientificName, sciHighlight, italic = true)
            val commonName = if (kind == NameKind.SCIENTIFIC) {
                species.preferredVietnameseName ?: species.preferredEnglishName.orEmpty()
            } else {
                matchedName
            }
            common.text = styled(commonName, highlight.takeIf { kind != NameKind.SCIENTIFIC }, italic = false)
            itemView.setOnClickListener { onClick(species.id) }
        }

        private fun styled(text: String, highlight: IntRange?, italic: Boolean): CharSequence {
            if (highlight == null && !italic) return text
            return SpannableString(text).apply {
                if (italic) setSpan(StyleSpan(Typeface.ITALIC), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (highlight != null && highlight.last < text.length) {
                    setSpan(StyleSpan(Typeface.BOLD), highlight.first, highlight.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }
}
