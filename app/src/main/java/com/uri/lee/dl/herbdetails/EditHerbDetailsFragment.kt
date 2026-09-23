package com.uri.lee.dl.herbdetails

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.uri.lee.dl.BaseViewBindingFragment
import com.uri.lee.dl.EditTextComponent
import com.uri.lee.dl.EditTextState
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentEditHerbDetailsBinding
import com.uri.lee.dl.snackBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Lets the user suggest a Vietnamese name for a herb. */
class EditHerbDetailsFragment : BaseViewBindingFragment<FragmentEditHerbDetailsBinding>(
    FragmentEditHerbDetailsBinding::inflate
) {

    private val viewModel: SuggestNameViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.titleView.text = getString(R.string.editing_s, getString(R.string.vietnamese_name))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EditTextComponent(
                    state = viewModel.state.map { EditTextState(text = it.draft, errorText = null, isEnabled = !it.isSubmitting) },
                    editTextLayout = binding.textInputLayout,
                    onTextChange = { viewModel.onAction(SuggestNameAction.DraftChanged(it)) },
                )
                binding.editTextView.requestFocus()

                viewModel.state
                    .onEach {
                        binding.progressBar.isVisible = it.isSubmitting
                        binding.updateBtn.isEnabled = it.canSubmit
                    }
                    .launchIn(this)

                viewModel.state
                    .filter { it.isSubmitted }
                    .take(1)
                    .onEach { navController.popBackStack() }
                    .launchIn(this)

                viewModel.state
                    .map { it.error }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .onEach { snackBar(message = getString(R.string.something_went_wrong_please_try_again_or_contact_us)).show() }
                    .launchIn(this)
            }
        }
        binding.updateBtn.setOnClickListener { viewModel.onAction(SuggestNameAction.Submit) }
    }
}
