package com.uri.lee.dl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.Nullable
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.databinding.BottomSheetMenuBinding

class BottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetMenuBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.bottom_sheet_menu, container, false)
        val addButton = binding.addButton
        val deleteAccButton = binding.deleteAccButton
        val aboutButton = binding.aboutButton
        val shareButton = binding.shareButton
        val contactButton = binding.contactButton
        val logoutButton = binding.logoutButton
        addButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        deleteAccButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        aboutButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        shareButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        contactButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        logoutButton.setOnClickListener {
            Toast.makeText(activity, "Algorithm Shared", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        return view
    }
}