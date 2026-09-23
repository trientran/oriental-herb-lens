package com.uri.lee.dl.hometabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.FragmentTabsBinding
import com.uri.lee.dl.domain.model.HerbSummary
import com.uri.lee.dl.herbdetails.HerbDetailsActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

val TAB_TITLES = arrayOf(R.string.all_herbs, R.string.favorite, R.string.history)

/** One home tab. All tabs share the activity's [HomeViewModel]. */
class TabsFragment : Fragment() {

    private val homeViewModel: HomeViewModel by activityViewModel()

    private lateinit var binding: FragmentTabsBinding

    private val tabTitle: Int get() = TAB_TITLES[requireArguments().getInt(ARG_SECTION_NUMBER)]

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabsBinding.inflate(inflater, container, false)
        val adapter = HerbSummaryAdapter { herbId ->
            startActivity(Intent(requireContext(), HerbDetailsActivity::class.java).putExtra(HERB_ID, herbId))
        }
        binding.recyclerView.adapter = adapter
        if (tabTitle == R.string.all_herbs) {
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!recyclerView.canScrollVertically(1)) homeViewModel.onAction(HomeAction.LoadMore)
                }
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.state
                    .map { it.herbsFor(tabTitle) }
                    .distinctUntilChanged()
                    .collect { herbs ->
                        binding.placeHolderView.isVisible = herbs.isEmpty()
                        adapter.submitList(herbs)
                    }
            }
        }
        return binding.root
    }

    private fun HomeState.herbsFor(tab: Int): List<HerbSummary> = when (tab) {
        R.string.favorite -> favorites
        R.string.history -> history
        else -> allHerbs
    }

    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"

        @JvmStatic
        fun newInstance(position: Int): TabsFragment = TabsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_SECTION_NUMBER, position) }
        }
    }
}
