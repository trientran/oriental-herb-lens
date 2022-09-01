package com.uri.lee.dl.herbdetails.images

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.INSTANT_HERB
import com.uri.lee.dl.databinding.FragmentImagesBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.upload.ImageUploadActivity
import timber.log.Timber

class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModels()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this)[ImagesViewModel::class.java]

        arguments?.getString(INSTANT_HERB)?.let {

            Timber.d("trien", it.toString())
        }
        _binding = FragmentImagesBinding.inflate(inflater, container, false)

        binding.pleaseUploadView.setOnClickListener {
            herbDetailsViewModel.state.herb?.objectID.let {
                val intent = Intent(requireContext(), ImageUploadActivity::class.java)
                intent.putExtra(HERB_ID, it)
                startActivity(intent)
            }
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}