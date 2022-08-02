package com.uri.lee.dl.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.uri.lee.dl.R

class AbcFragment : Fragment() {

    companion object {
        fun newInstance() = AbcFragment()
    }

    private lateinit var viewModel: AbcViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_abc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.container, AbcFragment.newInstance())
            .commitNow()
    }
}