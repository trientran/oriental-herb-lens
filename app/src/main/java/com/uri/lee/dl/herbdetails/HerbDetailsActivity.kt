package com.uri.lee.dl.herbdetails

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils.openFacebookPage
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.ActivityHerbDetailsBinding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HerbDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHerbDetailsBinding
    private lateinit var navController: NavController
    private val viewModel: HerbDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getLongExtra(HERB_ID, 1001)?.let { viewModel.setId(it) }

        binding = ActivityHerbDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        navController = findNavController(R.id.nav_host_fragment_activity_herb_details)
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
        lifecycleScope.launch {
            viewModel.state()
                .map { it.isLiked }
                .distinctUntilChanged()
                .onEach {
                    menu.findItem(R.id.action_like).icon =
                        if (it) getDrawable(R.drawable.ic_baseline_favorite_like) else getDrawable(R.drawable.ic_baseline_favorite_dislike)
                }
                .launchIn(this)
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_like -> {
                viewModel.setLike()
                true
            }
            R.id.action_report_facebook -> {
                openFacebookPage()
                true
            }
            R.id.action_report_email -> {
                sendEmail(subject = "${viewModel.state.herb?.id} - ${viewModel.state.herb?.latinName}")
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