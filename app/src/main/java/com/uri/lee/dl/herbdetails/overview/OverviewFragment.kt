package com.uri.lee.dl.herbdetails.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
import com.uri.lee.dl.databinding.FragmentOverviewBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.isSystemLanguageVietnamese
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class OverviewFragment : Fragment() {

    private var _binding: FragmentOverviewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel by lazy { ViewModelProvider(requireActivity())[HerbDetailsViewModel::class.java] }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
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
                        binding.herbIdView.text = getString(R.string.herb_id_s, it.objectID)
                        binding.latinNameView.text = getString(R.string.latin_name_s, it.latinName)
                        binding.viNameView.text = getString(R.string.vietnamese_name_s, it.viName)
                        binding.enNameView.text = getString(R.string.english_name_s, it.enName)
                        binding.overviewView.text = getString(
                            R.string.overview_s,
                            if (isSystemLanguageVietnamese) it.viOverview else it.enOverview
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
