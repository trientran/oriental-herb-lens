package com.uri.lee.dl.herbdetails.dosing

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
import com.uri.lee.dl.databinding.FragmentDosingBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.herbdetails.overview.OverviewFragmentDirections
import com.uri.lee.dl.isSystemLanguageVietnamese
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

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDosingBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val navController = findNavController()

        binding.dosingEditView.setOnClickListener {
            herbDetailsViewModel.state.herb?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(
                        herbId = it.objectID,
                        fieldName = if (isSystemLanguageVietnamese) it::viDosing.name else it::enDosing.name,
                        oldValue = if (isSystemLanguageVietnamese) it.viDosing ?: "" else it.enDosing ?: ""
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
                        binding.dosingView.text = if (isSystemLanguageVietnamese) {
                            if (it.viDosing.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.viDosing
                        } else {
                            if (it.enDosing.isNullOrBlank()) getString(R.string.please_edit_this_field) else it.enDosing
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