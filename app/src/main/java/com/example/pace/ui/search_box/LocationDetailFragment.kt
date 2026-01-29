package com.example.pace.ui.search_box

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContentProviderCompat
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentLocationDetailBinding
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient

class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var placesClient: PlacesClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        placesClient = Places.createClient(requireContext())

        val name = arguments?.getString("name") ?: ""
        val info = arguments?.getString("info") ?: ""
        val placeId = arguments?.getString("placeId") ?: ""
        val isCalendarMode = arguments?.getBoolean("isCalendarMode") ?: false

        binding.tvTitle.text = name
        binding.tvMetaInfo.text = info

        if (placeId.isNotEmpty()) {
            fetchPlacePhotos(placeId)
        }

        if (isCalendarMode) {
            binding.icStart.visibility = View.GONE
            binding.icArrive.visibility = View.GONE
            binding.icSelectLocation.visibility = View.VISIBLE
        } else {
            binding.icStart.visibility = View.VISIBLE
            binding.icArrive.visibility = View.VISIBLE
            binding.icSelectLocation.visibility = View.GONE
        }

        binding.icStart.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onLocationSelected(name, placeId, isStart = true)
        }

        binding.icArrive.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onLocationSelected(name, placeId, isStart = false)
        }
    }

    private fun fetchPlacePhotos(placeId: String) {
        val fields = listOf(Place.Field.PHOTO_METADATAS)
        val request = FetchPlaceRequest.newInstance(placeId, fields)

        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            val metadataList = response.place.photoMetadatas

            binding.photoContainer.removeAllViews()

            if (!metadataList.isNullOrEmpty()) {
                val count = minOf(metadataList.size, 3)

                for (i in 0 until count) {
                    val metadata = metadataList[i]
                    val photoRequest = FetchPhotoRequest.builder(metadata)
                        .setMaxWidth(1000)
                        .setMaxHeight(600)
                        .build()

                    placesClient.fetchPhoto(photoRequest).addOnSuccessListener { photoResponse ->
                        addDynamicPhotoView(photoResponse.bitmap)
                    }
                }
            }
        }.addOnFailureListener {
            // 에러 처리
        }
    }


    private fun addDynamicPhotoView(bitmap: Bitmap) {
        if (_binding == null) return

        val context = requireContext()

        // 1. CardView 생성 (둥근 모서리용)
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(130),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = dpToPx(8) // 사진 간격
            }
            radius = dpToPx(8).toFloat()
            cardElevation = 0f
        }

        // 2. ImageView 생성
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        cardView.addView(imageView)
        binding.photoContainer.addView(cardView)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(item: SearchItem, isCalendarMode: Boolean): LocationDetailFragment {
            val fragment = LocationDetailFragment()
            val bundle = Bundle().apply {
                putString("name", item.name)
                val infoString = "${item.category} · ${item.distance} · ${item.address}"
                putString("info", infoString)
                putString("placeId", item.placeId)

                putBoolean("isCalendarMode", isCalendarMode)
            }
            fragment.arguments = bundle
            return fragment
        }
    }
}