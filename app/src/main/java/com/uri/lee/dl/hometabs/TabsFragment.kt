package com.uri.lee.dl.hometabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.INSTANT_HERB
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentTabsBinding
import com.uri.lee.dl.herbdetails.HerbDetailsActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TabsFragment : Fragment() {

    private lateinit var binding: FragmentTabsBinding
    private val viewModel: PageViewModel by viewModels()
    private lateinit var viewAdapter: HerbAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getInt(ARG_SECTION_NUMBER)?.let { viewModel.setIndex(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTabsBinding.inflate(inflater, container, false)
        viewAdapter = HerbAdapter {
            val intent = Intent(requireContext(), HerbDetailsActivity::class.java)
            intent.putExtra(INSTANT_HERB, it)
            startActivity(intent)
        }
        binding.recyclerView.adapter = viewAdapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if ((recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() ==
                    viewAdapter.itemCount - 1 && !recyclerView.canScrollVertically(1) && viewAdapter.itemCount > 0
                ) {
                    viewModel.state.index?.let {
                        when (TAB_TITLES[it]) {
                            R.string.tab_favorite -> viewModel.processHistoryOrFavorite(
                                numberOfRecyclerViewItemsLoaded = viewAdapter.itemCount,
                                numberOfDocumentsToLoad = 10,
                                docType = DocType.FAVORITE
                            )
                            R.string.tab_history -> viewModel.processHistoryOrFavorite(
                                numberOfRecyclerViewItemsLoaded = viewAdapter.itemCount,
                                numberOfDocumentsToLoad = 10,
                                docType = DocType.HISTORY
                            )
                            R.string.tab_random -> viewModel.processRandom()
                        }
                    }
                }
                super.onScrolled(recyclerView, dx, dy)
            }
        })
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state()
                    .map { it.herbs }
                    .distinctUntilChanged()
                    .onEach {
                        binding.placeHolderView.isVisible = it.isEmpty()
                        viewAdapter.submitList(it)
                    }
                    .launchIn(this)

                viewModel.state()
                    .map { it.isLoading }
                    .distinctUntilChanged()
                    .onEach { binding.progressBar.isVisible = it }
                    .launchIn(this)

                viewModel.state()
                    .map { it.event }
                    .onEach {
                        when (it) {
                            is PageState.Event.Error -> Toast.makeText(
                                requireContext(),
                                getString(R.string.something_went_wrong_please_try_again_or_contact_us),
                                LENGTH_LONG
                            ).show()
                            null -> Unit
                        }
                    }
                    .launchIn(this)
            }
        }
        return binding.root
    }

    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"

        @JvmStatic
        fun newInstance(fragmentResId: Int): TabsFragment {
            return TabsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, fragmentResId)
                }
            }
        }
    }
}
