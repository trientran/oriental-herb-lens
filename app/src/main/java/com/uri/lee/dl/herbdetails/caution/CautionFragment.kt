package com.uri.lee.dl.herbdetails.caution

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
import com.uri.lee.dl.databinding.FragmentCautionBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.isSystemLanguageVietnamese
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CautionFragment : Fragment() {

    private var _binding: FragmentCautionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCautionBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val navController = findNavController()

        binding.sideEffectsEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    CautionFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = if (isSystemLanguageVietnamese) it::viSideEffects.name else it::enSideEffects.name,
                        oldValue = if (isSystemLanguageVietnamese) it.viSideEffects ?: "" else it.enSideEffects ?: ""
                    )
                )
            }
        }
        binding.interactionsEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    CautionFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = if (isSystemLanguageVietnamese) it::viInteractions.name else it::enInteractions.name,
                        oldValue = if (isSystemLanguageVietnamese) it.viInteractions ?: "" else it.enInteractions ?: ""
                    )
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .mapNotNull { it.herb }
                    .distinctUntilChanged()
                    .onEach {
                        binding.sideEffectsView.text = if (isSystemLanguageVietnamese) {
                            it.viSideEffects.ifBlank { getString(R.string.please_edit_this_field) }
                        } else {
                            it.enSideEffects.ifBlank { getString(R.string.please_edit_this_field) }
                        }
                        binding.interactionsView.text = if (isSystemLanguageVietnamese) {
                            it.viInteractions.ifBlank { getString(R.string.please_edit_this_field) }
                        } else {
                            it.enInteractions.ifBlank { getString(R.string.please_edit_this_field) }
                        }
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
