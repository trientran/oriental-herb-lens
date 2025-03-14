package com.uri.lee.dl.herbdetails.review

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.uri.lee.dl.BaseViewBindingFragment
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentAddReviewBinding
import com.uri.lee.dl.snackBar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddReviewFragment : BaseViewBindingFragment<FragmentAddReviewBinding>(
    FragmentAddReviewBinding::inflate
) {

    private val args by navArgs<AddReviewFragmentArgs>()

    private val viewModel: AddReviewViewModel by viewModels { AddReviewViewModel.MyViewModelFactory(args.herbId) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.nameEditTextView.doAfterTextChanged { viewModel.setPatientName(it.toString()) }
        binding.ageEditTextView.doAfterTextChanged { viewModel.setAge(it.toString().toInt()) }
        binding.conditionEditTextView.doAfterTextChanged { viewModel.setCondition(it.toString()) }
        binding.commentEditTextView.doAfterTextChanged { viewModel.setComment(it.toString()) }
        binding.ratingBarEffectiveness.setOnRatingBarChangeListener { _, rating, _ -> viewModel.setEffectiveness(rating) }
        binding.ratingBarEaseOfUse.setOnRatingBarChangeListener { _, rating, _ -> viewModel.setEaseOfUse(rating) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Progress bar
                viewModel.state()
                    .map { it.isSubmitting }
                    .distinctUntilChanged()
                    .onEach { binding.progressBar.isVisible = it }
                    .launchIn(this)

                // Submit button
                viewModel.state()
                    .distinctUntilChanged()
                    .onEach {
                        binding.submitBtn.isEnabled = it.review?.condition?.isNotBlank() == true &&
                                it.review.age != null &&
                                it.review.comment.isNotBlank() == true &&
                                it.review.easyOfUse != null &&
                                it.review.effectiveness != null &&
                                it.review.patientName.isNotBlank() == true &&
                                !it.isSubmitting
                    }
                    .launchIn(this)
                binding.submitBtn.setOnClickListener { viewModel.submit() }

                // Pop back stack once completed
                viewModel.state()
                    .map { it.isSubmissionComplete }
                    .filter { it }
                    .take(1)
                    .onEach { navController.popBackStack() }
                    .launchIn(this)

                // SnackBar
                viewModel.state()
                    .map { it.error }
                    .distinctUntilChanged()
                    .onEach {
                        if (it != null) {
                            snackBar(message = getString(R.string.something_went_wrong_please_try_again_or_contact_us)).show()
                        }
                    }
            }
        }
    }
}
