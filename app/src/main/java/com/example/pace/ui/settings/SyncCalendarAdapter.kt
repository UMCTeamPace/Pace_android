package com.example.pace.ui.settings

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.CalendarAccount

class SyncCalendarAdapter(
    private val onToggleChanged: (Long, Boolean) -> Unit
) : RecyclerView.Adapter<SyncCalendarAdapter.ViewHolder>() {

    private var items = listOf<CalendarAccount>()
    private var syncedIds = setOf<Long>() // 현재 동기화된 ID들 (중복 방지용 Set)

    // 데이터를 갱신하는 함수
    fun submitData(newList: List<CalendarAccount>, currentSyncedIds: List<Long>) {
        items = newList
        syncedIds = currentSyncedIds.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sync_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isSelected = syncedIds.contains(item.id.toLong())

        // 1. 텍스트 설정 (이름 + 계정명)
        holder.infoTv.text = "${item.displayName}\n(${item.accountName})"

        // 3. 기존에 쓰시던 토글 이미지 유지 (선택 여부에 따라 이미지 교체)
        holder.toggleIv.setImageResource(
            if (isSelected) R.drawable.ic_toggle_selected
            else R.drawable.ic_toggle_unselected
        )

        // 4. 아이템 클릭 시 토글 상태 반전 전달
        holder.itemView.setOnClickListener {
            onToggleChanged(item.id.toLong(), !isSelected)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val infoTv: TextView = view.findViewById(R.id.tv_calendar_info)
        val toggleIv: ImageView = view.findViewById(R.id.iv_sync_toggle)
    }
}