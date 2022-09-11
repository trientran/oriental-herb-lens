package com.uri.lee.dl.herbdetails.images

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.INSTANT_HERB
import com.uri.lee.dl.databinding.FragmentImagesBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.upload.FullSizeImageViewerDialog
import com.uri.lee.dl.upload.ImageUploadActivity
import com.uri.lee.dl.upload.ImageUploadAdapter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModels()
    private lateinit var imageUploadAdapter: ImageUploadAdapter

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

        imageUploadAdapter = ImageUploadAdapter {
            Log.d("trien", it.toString())
            val bottomSheet = FullSizeImageViewerDialog(it)
            bottomSheet.show(childFragmentManager, "ModalBottomSheet")
        }

        val gridLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter

        binding.pleaseUploadView.setOnClickListener { goToUploadScreen() }
        binding.addMorePhotosView.setOnClickListener { goToUploadScreen() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state()
                    .map { it.imageInfoList }
                    .distinctUntilChanged()
                    .onEach { imageInfoSet ->
                        binding.pleaseUploadView.isVisible = imageInfoSet.isEmpty()
                        imageUploadAdapter.submitList(imageInfoSet.map { it.url.toUri() })
                    }
                    .launchIn(this)
            }
        }

        return binding.root
    }

    private fun goToUploadScreen() {
        herbDetailsViewModel.state.herb?.objectID?.let {
            val intent = Intent(requireContext(), ImageUploadActivity::class.java)
            intent.putExtra(HERB_ID, it)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}