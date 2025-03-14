package com.uri.lee.dl.herbdetails.review

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ReviewItemBinding
import com.uri.lee.dl.getLocalizedDateStringUsingInstant
import com.uri.lee.dl.herbdetails.review.AddReviewState.Review

class ReviewsAdapter : ListAdapter<Review, ReviewsAdapter.ItemViewHolder>(DiffUtil()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ReviewItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holderImage: ItemViewHolder, position: Int) {
        val review = getItem(position) ?: return
        holderImage.bindTo(review)
    }

    private class DiffUtil : ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean =
            oldItem.instant == newItem.instant

        override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean =
            oldItem == newItem
    }

    inner class ItemViewHolder(private val binding: ReviewItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindTo(review: Review) {
            binding.personTextView.text = itemView.context.getString(R.string.person_s, review.patientName, review.age)
            binding.dateTextView.text = getLocalizedDateStringUsingInstant(review.instant!!)
            binding.conditionTextView.text = itemView.context.getString(R.string.conditions_s, review.condition)
            binding.ratingBarEffectiveness.rating = review.effectiveness!!
            binding.ratingBarEffectiveness.setIsIndicator(true)
            binding.ratingBarEaseOfUse.rating = review.easyOfUse!!
            binding.ratingBarEaseOfUse.setIsIndicator(true)
            binding.commentView.text = review.comment
        }
    }
}

