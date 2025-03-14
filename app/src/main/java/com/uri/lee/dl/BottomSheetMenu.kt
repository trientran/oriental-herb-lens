package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.uri.lee.dl.Utils.openFacebookPage
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.BottomSheetMenuBinding

class BottomSheetMenu(
    private val recognizedViHerbs: Map<String, String>,
    private val recognizedLatinHerbs: Map<String, String>
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetMenuBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetMenuBinding.inflate(layoutInflater)
        val view = binding.root

        binding.herbList.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(
                    if (isSystemLanguageVietnamese) {
                        recognizedViHerbs.values.sorted().joinToString("\n")
                    } else {
                        recognizedLatinHerbs.values.sorted().joinToString("\n")
                    }
                )
                .setCancelable(true)
                .setNeutralButton("OK") { _, _ -> dismiss() }
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
                .setNeutralButton("OK") { _, _ -> dismiss() }
                .create().show()
            dismiss()
        }
        return view
    }
}
