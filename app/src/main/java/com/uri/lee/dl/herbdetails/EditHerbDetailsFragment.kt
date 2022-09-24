package com.uri.lee.dl.herbdetails

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.uri.lee.dl.*
import com.uri.lee.dl.databinding.FragmentEditHerbDetailsBinding
import com.uri.lee.dl.instantsearch.Herb
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class EditHerbDetailsFragment : BaseViewBindingFragment<FragmentEditHerbDetailsBinding>(
    FragmentEditHerbDetailsBinding::inflate
) {

    private val args by navArgs<EditHerbDetailsFragmentArgs>()

    private val editHerbDetailsViewModel: EditHerbDetailsViewModel by viewModels {
        EditHerbDetailsViewModel.MyViewModelFactory(args.herbId, args.fieldName, args.oldValue)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        lifecycleScope.launch {
            // Title
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editHerbDetailsViewModel.state()
                    .mapNotNull { it.fieldName }
                    .take(1)
                    .onEach {
                        binding.titleView.text = getString(
                            R.string.editing_s, when (it) {
                                Herb::viSideEffects.name, Herb::enSideEffects.name -> getString(R.string.side_effect)
                                Herb::viInteractions.name, Herb::enInteractions.name -> getString(R.string.interactions)
                                Herb::viDosing.name, Herb::enDosing.name -> getString(R.string.dosing)
                                Herb::viOverview.name, Herb::enOverview.name -> getString(R.string.overview)
                                Herb::viName.name -> getString(R.string.vietnamese_name)
                                Herb::enName.name -> getString(R.string.english_name)
                                Herb::latinName.name -> getString(R.string.latin_name)
                                else -> getString(R.string.something_went_wrong_please_try_again_or_contact_us)
                            }
                        )
                    }
                    .launchIn(this)

                // Edittext
                EditTextComponent(
                    state = editHerbDetailsViewModel.state().map {
                        EditTextState(
                            text = it.newValue,
                            errorText = null,
                            isEnabled = true,
                        )
                    },
                    editTextLayout = binding.textInputLayout,
                    onTextChange = editHerbDetailsViewModel::setNewValue,
                )
                binding.editTextView.requestFocus()

                // Progress bar
                editHerbDetailsViewModel.state()
                    .map { it.isSubmitting }
                    .distinctUntilChanged()
                    .onEach { binding.progressBar.isVisible = it }
                    .launchIn(this)

                // Update button
                editHerbDetailsViewModel.state()
                    .distinctUntilChanged()
                    .onEach {
                        binding.updateBtn.isEnabled =
                            it.newValue.isNotBlank() && it.oldValue != it.newValue && !it.isSubmitting
                    }
                    .launchIn(this)

                // Pop back stack once completed
                editHerbDetailsViewModel.state()
                    .map { it.isUpdateComplete }
                    .filter { it }
                    .take(1)
                    .onEach { navController.popBackStack() }
                    .launchIn(this)

                // SnackBar
                editHerbDetailsViewModel.state()
                    .map { it.error }
                    .distinctUntilChanged()
                    .onEach {
                        if (it != null) {
                            snackBar(message = getString(R.string.something_went_wrong_please_try_again_or_contact_us)).show()
                        }
                    }
            }
        }
        binding.updateBtn.setOnClickListener {
            editHerbDetailsViewModel.update()
            navController.popBackStack()
        }
    }
}

sealed interface FieldName : Parcelable {
    @Parcelize
    object EnName : FieldName

    @Parcelize
    object ViName : FieldName

    @Parcelize
    object LatinName : FieldName

    @Parcelize
    object EnOverview : FieldName

    @Parcelize
    object ViOverview : FieldName

    @Parcelize
    object EnSideEffects : FieldName

    @Parcelize
    object ViSideEffects : FieldName

    @Parcelize
    object EnInteractions : FieldName

    @Parcelize
    object ViInteractions : FieldName

    @Parcelize
    object EnDosing : FieldName

    @Parcelize
    object ViDosing : FieldName
}
