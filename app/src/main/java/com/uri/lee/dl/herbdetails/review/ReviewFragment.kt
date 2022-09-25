package com.uri.lee.dl.herbdetails.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentReviewBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ReviewFragment : Fragment() {

    private var _binding: FragmentReviewBinding? = null
    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModels()
    private lateinit var adapter: ReviewsAdapter

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val navController = findNavController()
        binding.addReview.setOnClickListener {
            navController.navigate(ReviewFragmentDirections.addReview(herbDetailsViewModel.state.herb!!.id!!))
        }

        adapter = ReviewsAdapter()
        binding.recyclerView.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .mapNotNull { it.herb?.reviews?.values }
                    .distinctUntilChanged()
                    .onEach { reviews ->
                        val effectiveness = reviews.mapNotNull { it.effectiveness }.average()
                        val easeOfUse = reviews.mapNotNull { it.easyOfUse }.average()
                        val overallRating = (effectiveness + easeOfUse) / 2
                        binding.overallView.text = getString(R.string.overall_rating, overallRating, reviews.size)
                        binding.easeOfUseView.text = getString(R.string.ease_of_use_rating, easeOfUse)
                        binding.effectivenessView.text = getString(R.string.effectiveness_rating, effectiveness)
                        binding.ratingBarOverall.rating = overallRating.toFloat()
                        binding.ratingBarEffectiveness.rating = effectiveness.toFloat()
                        binding.ratingBarEaseOfUse.rating = easeOfUse.toFloat()
                        adapter.submitList(reviews.toList())
                    }
                    .launchIn(this)
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
