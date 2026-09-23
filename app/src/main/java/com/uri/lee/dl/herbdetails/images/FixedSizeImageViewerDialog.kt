package com.uri.lee.dl.herbdetails.images

import android.net.Uri
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FixedSizeImageViewerDialog(
    private val uriPair: Pair<Uri, String>,
) : BottomSheetDialogFragment() {

    private lateinit var binding: FixedSizeImageViewerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FixedSizeImageViewerBinding.inflate(layoutInflater)
        val view = binding.root
        Glide.with(this).load(uriPair.first).into(binding.imageView)
        try {
            Json.decodeFromString<ImageDetail>(uriPair.second)
        } catch (e: Exception) {
            binding.mapView.visibility = View.GONE
            binding.uploadByView.text = getString(R.string.uploaded_by, "")
            null
        }?.let { imageDetail ->
            binding.uploadByView.text = getString(R.string.uploaded_by, imageDetail.uid)
            binding.mapView.visibility = View.VISIBLE
            binding.mapView.mapboxMap.loadStyle(
                style = Style.MAPBOX_STREETS,
                onStyleLoaded = {
                    binding.mapView.addAnnotationToMap(
                        view.context,
                        lat = imageDetail.lat,
                        long = imageDetail.lng,
                    )
                }
            )
            val cameraPosition = CameraOptions.Builder()
                .zoom(14.0)
                .center(Point.fromLngLat(imageDetail.lng, imageDetail.lat))
                .build()
            // set camera position
            binding.mapView.mapboxMap.setCamera(cameraPosition)
        }
        binding.imageView.setOnClickListener { view.context.openUrlWithDefaultBrowser(uri = uriPair.first) }
        return view
    }
}

@Serializable
data class ImageDetail(
    val uid: String,
    val lat: Double,
    val lng: Double
)
