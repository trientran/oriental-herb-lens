package com.uri.lee.dl.herbdetails.overview

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
import androidx.navigation.fragment.findNavController
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentOverviewBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.isSystemLanguageVietnamese

class OverviewFragment : Fragment() {

    private var _binding: FragmentOverviewBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        val root = binding.root
        val navController = findNavController()

        binding.viNameEditView.setOnClickListener {
            herbDetailsViewModel.state.value.profile?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(herbId = it.id, oldValue = it.vietnameseName)
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state
                    .mapNotNull { it.profile }
                    .distinctUntilChanged()
                    .onEach { herb ->
                        binding.herbIdView.text = getString(R.string.herb_id_s, herb.id.toString())
                        binding.latinNameView.text =
                            herb.latinName.ifBlank { getString(R.string.not_available_yet) }
                        binding.viNameView.text = herb.vietnameseName.ifBlank { getString(R.string.please_edit_this_field) }
                        binding.enNameView.text = herb.englishName.ifBlank { getString(R.string.not_available_yet) }
                        binding.overviewView.text =
                            herb.overview.pick(isSystemLanguageVietnamese).ifBlank { getString(R.string.not_available_yet) }
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
