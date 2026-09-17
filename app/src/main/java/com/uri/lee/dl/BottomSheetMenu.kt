package com.uri.lee.dl

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.BottomSheetMenuBinding

class BottomSheetMenu(
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
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://docs.google.com/spreadsheets/d/16IpEYlpkd7NW3XHXUvhdhJf8LySuhVRLooA7c1SAzOs/edit?usp=sharing".toUri()
                )
            )
        }
        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            dismiss()
        }
        binding.contactButton.setOnClickListener {
            it.context.sendEmail(subject = "")
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
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://med-herb-lens.web.app/".toUri()
                )
            )
        }
        return view
    }
}
