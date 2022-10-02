package com.uri.lee.dl.hometabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.*
import com.uri.lee.dl.databinding.FragmentTabsBinding
import com.uri.lee.dl.herbdetails.HerbDetailsActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TabsFragment : Fragment() {

    private val userViewModel: UserViewModel by activityViewModels()

    private val homeTabViewModel: HomeTabViewModel by viewModels()

    private lateinit var binding: FragmentTabsBinding

    private lateinit var viewAdapter: HerbAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getInt(ARG_SECTION_NUMBER)?.let { homeTabViewModel.setIndex(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTabsBinding.inflate(inflater, container, false)
        viewAdapter = HerbAdapter(viewModel = userViewModel) {
            val intent = Intent(requireContext(), HerbDetailsActivity::class.java)
            intent.putExtra(HERB_ID, it)
            startActivity(intent)
        }
        binding.recyclerView.adapter = viewAdapter
        when (TAB_TITLES[homeTabViewModel.state.tabIndex!!]) {
            R.string.tab_random -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        userViewModel.state()
                            .map { it.randomHerbIds }
                            .distinctUntilChanged()
                            .onEach {
                                binding.placeHolderView.isVisible = it.isEmpty()
                                viewAdapter.submitList(it)
                            }
                            .launchIn(this)
                    }
                }
                binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if ((recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() ==
                            viewAdapter.itemCount - 1 && !recyclerView.canScrollVertically(1) &&
                            viewAdapter.itemCount >= PAGING_ITEM_COUNT_ONE_GO
                        ) {
                            userViewModel.processRandom()
                        }
                        super.onScrolled(recyclerView, dx, dy)
                    }
                })
            }
            R.string.tab_favorite -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        userViewModel.state()
                            .map { it.favoriteHerbIds }
                            .distinctUntilChanged()
                            .onEach {
                                binding.placeHolderView.isVisible = it.isEmpty()
                                viewAdapter.submitList(it)
                            }
                            .launchIn(this)
                    }
                }
            }
            R.string.tab_history -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        userViewModel.state()
                            .map { it.historyHerbIds }
                            .distinctUntilChanged()
                            .onEach {
                                binding.placeHolderView.isVisible = it.isEmpty()
                                viewAdapter.submitList(it)
                            }
                            .launchIn(this)
                    }
                }
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
