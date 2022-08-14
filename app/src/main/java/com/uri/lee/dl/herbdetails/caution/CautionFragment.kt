package com.uri.lee.dl.herbdetails.caution

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
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

        binding.editButton.setOnClickListener {
            requireContext()
                .openUrlWithDefaultBrowser(
                    "https://docs.google.com/spreadsheets/d/1lWiEq53_1m0tQCvunHNzYT1GFimZiAl_RqP4PNP9grU/edit?usp=sharing".toUri()
                )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .mapNotNull { it.herb }
                    .distinctUntilChanged()
                    .onEach {
                        binding.sideEffectsView.text = getString(
                            R.string.side_effects_s,
                            if (isSystemLanguageVietnamese) it.viSideEffects else it.enSideEffects
                        )
                        binding.interactionsView.text = getString(
                            R.string.interactions_s,
                            if (isSystemLanguageVietnamese) it.viInteractions else it.enInteractions
                        )
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
