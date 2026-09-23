package com.uri.lee.dl.herbdetails.dosing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import android.os.Bundle
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentDosingBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.isSystemLanguageVietnamese

class DosingFragment : Fragment() {

    private var _binding: FragmentDosingBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDosingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state
                    .mapNotNull { it.profile }
                    .distinctUntilChanged()
                    .onEach {
                        binding.dosingView.text =
                            it.dosing.pick(isSystemLanguageVietnamese).ifBlank { getString(R.string.not_available_yet) }
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