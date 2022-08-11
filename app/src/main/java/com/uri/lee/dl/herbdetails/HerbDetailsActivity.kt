package com.uri.lee.dl.herbdetails

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.uri.lee.dl.INSTANT_HERB
import com.uri.lee.dl.R
import com.uri.lee.dl.databinding.ActivityHerbDetailsBinding
import com.uri.lee.dl.instantsearch.Herb

class HerbDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHerbDetailsBinding
    private val viewModel: HerbDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getParcelableExtra<Herb>(INSTANT_HERB)?.let { viewModel.setId(it.objectID) }

        binding = ActivityHerbDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_herb_details)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_images,
                R.id.navigation_overview,
                R.id.navigation_caution,
                R.id.navigation_dosing,
                R.id.navigation_review
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.herb_details_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_like -> {
                item.icon = getDrawable(R.drawable.ic_baseline_favorite_like)
                true
            }
            R.id.action_report -> {
                // send email
                true
            }
            R.id.close -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}