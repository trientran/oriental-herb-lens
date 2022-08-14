package com.uri.lee.dl.herbdetails.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.uri.lee.dl.INSTANT_HERB
import com.uri.lee.dl.databinding.FragmentImagesBinding
import timber.log.Timber

class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null

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

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}