package com.uri.lee.dl.herbdetails.overview

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentOverviewBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

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
            herbDetailsViewModel.state.value.species?.let {
                navController.navigate(
                    OverviewFragmentDirections.editHerbDetails(herbId = it.id, oldValue = it.preferredVietnameseName.orEmpty())
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state
                    .mapNotNull { it.species }
                    .distinctUntilChanged()
                    .onEach { species ->
                        val none = getString(R.string.not_available_yet)
                        binding.herbIdView.text = getString(R.string.herb_id_s, species.id.toString())
                        binding.latinNameView.text = scientificName(species.scientificName, species.authorship)
                        binding.viNameView.text = species.vietnameseNames.joinToString(", ").ifBlank { getString(R.string.please_edit_this_field) }
                        binding.enNameView.text = species.englishNames.joinToString(", ").ifBlank { none }
                        binding.overviewView.text = listOf(
                            getString(R.string.family_s, species.family.ifBlank { none }),
                            getString(R.string.genus_s, species.genus.ifBlank { none }),
                        ).joinToString("\n")
                    }
                    .launchIn(this)
            }
        }
        return root
    }

    /** "Polyscias fruticosa (L.) Harms" with the name in italics, as botanical convention has it. */
    private fun scientificName(name: String, authorship: String): CharSequence {
        val text = if (authorship.isBlank()) name else "$name $authorship"
        return SpannableString(text).apply { setSpan(StyleSpan(Typeface.ITALIC), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
