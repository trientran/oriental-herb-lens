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
import com.uri.lee.dl.herbdetails.overview.OverviewFragmentDirections
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
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.objectID,
                        fieldName = if (isSystemLanguageVietnamese) it::viSideEffects.name else it::enSideEffects.name,
                        oldValue = if (isSystemLanguageVietnamese) it.viSideEffects ?: "" else it.enSideEffects ?: ""
                    )
                )
            }
        }
        binding.interactionsEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.objectID,
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
                            if (it.viSideEffects.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.viSideEffects
                        } else {
                            if (it.enSideEffects.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.enSideEffects
                        }
                        binding.interactionsView.text = if (isSystemLanguageVietnamese) {
                            if (it.viInteractions.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.viInteractions
                        } else {
                            if (it.enInteractions.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.enInteractions
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
