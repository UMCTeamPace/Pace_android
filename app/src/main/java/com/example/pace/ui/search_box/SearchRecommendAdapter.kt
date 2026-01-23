package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.databinding.ItemSearchRecommendBinding
class SearchRecommendAdapter (
    private var items: List<SearchItem>,
    private val onClick: (SearchItem) -> Unit // 클릭 이벤트
) : RecyclerView.Adapter<SearchRecommendAdapter.Holder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSearchRecommendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    // 데이터 갱신용 함수
    fun submitList(newItems: List<SearchItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemSearchRecommendBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchItem) {
            binding.itemPlaceNameTv.text = item.name

            val infoList = listOfNotNull(
                item.category.ifEmpty { null },
                item.distance.ifEmpty { null },
                item.address.ifEmpty { null }
            )
            binding.itemPlaceDetailTv.text = infoList.joinToString(" · ")

            // 아이템 클릭 시
            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }
}