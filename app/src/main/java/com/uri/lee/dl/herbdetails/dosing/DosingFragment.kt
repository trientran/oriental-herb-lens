package com.uri.lee.dl.herbdetails.dosing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
import com.uri.lee.dl.databinding.FragmentDosingBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DosingFragment : Fragment() {

    private var _binding: FragmentDosingBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel: HerbDetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDosingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.editButton.setOnClickListener {
            requireContext()
                .openUrlWithDefaultBrowser(
                    "https://docs.google.com/spreadsheets/d/1lWiEq53_1m0tQCvunHNzYT1GFimZiAl_RqP4PNP9grU/edit?usp=sharing".toUri()
                )
        }
// todo check system language
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .mapNotNull { it.herb }
                    .distinctUntilChanged()
                    .onEach { binding.dosingView.text = it.viUsages }
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