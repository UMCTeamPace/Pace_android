package com.example.pace.ui.search_box

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.ItemSearchLocationBinding
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient

class LocationListAdapter(
    private val placesClient: PlacesClient,
    private val onItemClick: (SearchItem) -> Unit
): RecyclerView.Adapter<LocationListAdapter.LocationViewHolder>() {
    private var items: List<SearchItem> = emptyList()

    var onFavoriteClick: ((SearchItem) -> Unit)? = null
    fun submitList(newItems: List<SearchItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemSearchLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val item = items[position]

        holder.bind(item)

        // 3. 클릭 리스너 연결
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class LocationViewHolder(private val binding: ItemSearchLocationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchItem) {
            // 1. 텍스트 정보 설정
            binding.tvPlaceName.text = item.name
            binding.tvOpenStatus.text = item.openStatus

            val context = binding.root.context
            val colorResId = when {
                item.openStatus.contains("영업 중") -> R.color.semantic_info
                item.openStatus.contains("영업 종료") || item.openStatus.contains("운영 중단") -> R.color.semantic_warning
                else -> R.color.black
            }
            val color = androidx.core.content.ContextCompat.getColor(context, colorResId)
            binding.tvOpenStatus.setTextColor(color)

            // 메타 정보
            val metaText = " ${item.category} · ${item.distance} · ${item.address}"
            binding.tvMetaInfo.text = metaText

            // 이미지
            val metadata = item.photoMetadata

            if(metadata == null){
                binding.cvImageContainer.visibility = View.GONE
            }else{
                binding.cvImageContainer.visibility = View.VISIBLE

                // 이미지 뷰 초기화 (재사용 문제 방지)
                binding.ivPlaceImage.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지 혹은 로딩중 이미지

                if (metadata != null) {
                    val photoRequest = FetchPhotoRequest.builder(metadata)
                        .setMaxWidth(1080) // 대부분의 폰에서 깨지지 않는 넉넉한 너비
                        .setMaxHeight(600) // 150dp는 대략 400~600px 사이 (밀도에 따라 다름)
                        .build()

                    placesClient.fetchPhoto(photoRequest)
                        .addOnSuccessListener { fetchPhotoResponse ->
                            val bitmap: Bitmap = fetchPhotoResponse.bitmap
                            binding.ivPlaceImage.setImageBitmap(bitmap)
                        }
                        .addOnFailureListener {
                            binding.cvImageContainer.visibility = View.GONE
                        }
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.ivFavorite.setOnClickListener {
                onFavoriteClick?.invoke(item)
            }
        }
    }
}