package com.uri.lee.dl.herbdetails.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}