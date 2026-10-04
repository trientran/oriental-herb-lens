package com.uri.lee.dl.hometabs

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ImagesRecognitionItemBinding
import com.uri.lee.dl.domain.model.Species

/** A species row: preferred Vietnamese (or English) name and the scientific name in italics. */
class SpeciesAdapter(private val onItemClick: (speciesId: Long) -> Unit) :
    ListAdapter<Species, SpeciesAdapter.ItemViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(ImagesRecognitionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bindTo(getItem(position))

    private object Diff : DiffUtil.ItemCallback<Species>() {
        override fun areItemsTheSame(oldItem: Species, newItem: Species) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Species, newItem: Species) = oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ImagesRecognitionItemBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n") // an identifier, not a quantity: no locale formatting
        fun bindTo(species: Species) {
            // Lists don't fetch photos; they appear on the details screen.
            binding.imageView.setImageResource(R.drawable.ic_launcher)
            itemView.setOnClickListener { onItemClick(species.id) }
            binding.idView.text = species.id.toString()
            binding.latinNameView.text = SpannableString(species.scientificName).apply {
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.viNameView.text = species.preferredVietnameseName ?: species.preferredEnglishName.orEmpty()
        }
    }
}
