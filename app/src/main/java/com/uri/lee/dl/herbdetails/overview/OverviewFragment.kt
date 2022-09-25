package com.uri.lee.dl.herbdetails.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.uri.lee.dl.R
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

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel by lazy { ViewModelProvider(requireActivity())[HerbDetailsViewModel::class.java] }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        val root = binding.root
        val navController = findNavController()

        binding.latinNameEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = it::latinName.name,
                        oldValue = it.latinName ?: "",
                    )
                )
            }
        }
        binding.enNameEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = it::enName.name,
                        oldValue = it.enName,
                    )
                )
            }
        }
        binding.viNameEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = it::viName.name,
                        oldValue = it.viName,
                    )
                )
            }
        }
        binding.overviewEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.id!!,
                        fieldName = if (isSystemLanguageVietnamese) it::viOverview.name else it::enOverview.name,
                        oldValue = if (isSystemLanguageVietnamese) it.viOverview else it.enOverview
                    )
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .mapNotNull { it.herb }
                    .distinctUntilChanged()
                    .onEach { herb ->
                        binding.herbIdView.text = getString(R.string.herb_id_s, herb.id!!)
                        binding.latinNameView.text =
                            herb.latinName.ifBlank { getString(R.string.please_edit_this_field) }
                        binding.viNameView.text = herb.viName.ifBlank { getString(R.string.please_edit_this_field) }
                        binding.enNameView.text = herb.enName.ifBlank { getString(R.string.please_edit_this_field) }
                        binding.overviewView.text = if (isSystemLanguageVietnamese) {
                            herb.viOverview.ifBlank { getString(R.string.please_edit_this_field) }
                        } else {
                            herb.enOverview.ifBlank { getString(R.string.please_edit_this_field) }
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
