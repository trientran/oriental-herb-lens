package com.uri.lee.dl.herbdetails.images

import androidx.core.view.isVisible
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Bundle
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentImagesBinding
import com.uri.lee.dl.herbdetails.HerbDetailsViewModel
import com.uri.lee.dl.upload.ImageUploadActivity

class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null

    private val herbDetailsViewModel: HerbDetailsViewModel by activityViewModel()
    private lateinit var imageUploadAdapter: ImagesAdapter

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)

        imageUploadAdapter = ImagesAdapter { image ->
            val bottomSheet = FixedSizeImageViewerDialog(image)
            bottomSheet.show(parentFragmentManager, "ModalBottomSheet")
        }

        val gridLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter

        binding.pleaseUploadView.setOnClickListener { goToUploadScreen() }
        binding.addMorePhotosView.setOnClickListener { goToUploadScreen() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                herbDetailsViewModel.state
                    .mapNotNull { it.profile?.images }
                    .distinctUntilChanged()
                    .onEach { images ->
                        binding.pleaseUploadView.isVisible = images.isEmpty()
                        binding.addMorePhotosView.isVisible = images.count() <= 1000
                        binding.imagesCountView.text = getString(R.string.image_count, images.count())
                        imageUploadAdapter.submitList(images)
                    }
                    .launchIn(this)
            }
        }

        return binding.root
    }

    private fun goToUploadScreen() {
        herbDetailsViewModel.state.value.herbId.let {
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