package com.uri.lee.dl.herbdetails.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
import com.uri.lee.dl.addAnnotationToMap
import com.uri.lee.dl.databinding.FixedSizeImageViewerBinding
import com.uri.lee.dl.domain.model.SpeciesPhoto
import androidx.core.net.toUri

class FixedSizeImageViewerDialog(
    private val image: SpeciesPhoto,
) : BottomSheetDialogFragment() {

    private lateinit var binding: FixedSizeImageViewerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FixedSizeImageViewerBinding.inflate(layoutInflater)
        val view = binding.root
        // Show the grid's smaller copy (already cached) while the full-size original loads.
        Glide.with(this).load(image.url).thumbnail(Glide.with(this).load(image.thumbnailUrl)).into(binding.imageView)
        val credit = image.credit
        if (credit == null) {
            binding.uploadByView.text = getString(R.string.uploaded_by, image.uploaderId.orEmpty())
        } else {
            // Required attribution for third-party photos; tapping opens the photo's source page.
            binding.uploadByView.text = getString(R.string.gbif_photo_credit, credit.creator, credit.license, credit.publisher ?: "GBIF")
            credit.sourceUrl?.let { source ->
                binding.uploadByView.setOnClickListener { view.context.openUrlWithDefaultBrowser(uri = source.toUri()) }
            }
        }
        val location = image.location
        binding.mapView.visibility = if (location == null) View.GONE else View.VISIBLE
        if (location != null) {
            binding.mapView.mapboxMap.loadStyle(
                style = Style.MAPBOX_STREETS,
                onStyleLoaded = {
                    binding.mapView.addAnnotationToMap(view.context, lat = location.latitude, long = location.longitude)
                }
            )
            binding.mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .zoom(14.0)
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .build()
            )
        }
        binding.imageView.setOnClickListener { view.context.openUrlWithDefaultBrowser(uri = image.url.toUri()) }
        return view
    }
}
