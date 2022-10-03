package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.uri.lee.dl.Utils.openFacebookPage
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.BottomSheetMenuBinding

class BottomSheetMenu : BottomSheetDialogFragment() {
    private lateinit var binding: BottomSheetMenuBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetMenuBinding.inflate(layoutInflater)
        val view = binding.root

        binding.addButton.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(getString(R.string.please_hit_ok_to_open_our_google_sheet))
                .setCancelable(true)
                .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                    it.context.openUrlWithDefaultBrowser("https://docs.google.com/spreadsheets/d/1kHEIYrblHtqSEEcGyQO7zq_0_hhTZAziLEoZ5yJx_-U/edit?usp=sharing".toUri())
                }
                .create().show()
            dismiss()
        }
        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            dismiss()
        }
        binding.contactButton.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(getString(R.string.contact_us))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.via_facebook)) { _, _ -> it.context.openFacebookPage() }
                .setNegativeButton(getString(R.string.via_email)) { _, _ -> it.context.sendEmail(subject = "") }
                .create().show()
            dismiss()
        }
        binding.shareButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=com.uri.lee.dl")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
            dismiss()
        }
        binding.aboutButton.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(getString(R.string.about_us))
                .setCancelable(true)
                .setPositiveButton(getString(R.string._70_herbs_list)) { _, _ ->
                    it.context.openUrlWithDefaultBrowser("https://docs.google.com/spreadsheets/d/1lWiEq53_1m0tQCvunHNzYT1GFimZiAl_RqP4PNP9grU/edit?usp=sharing".toUri())
                }
                .setNegativeButton(getString(R.string.full_herb_list)) { _, _ ->
                    it.context.openUrlWithDefaultBrowser("https://docs.google.com/spreadsheets/d/1kHEIYrblHtqSEEcGyQO7zq_0_hhTZAziLEoZ5yJx_-U/edit?usp=sharing".toUri())
                }
                .setNeutralButton("OK") { _, _ -> dismiss() }
                .create().show()
            dismiss()
        }
        return view
    }
}
