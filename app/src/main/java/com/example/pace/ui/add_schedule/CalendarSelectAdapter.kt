package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.CalendarAccount

class CalendarSelectAdapter(
    private var items: List<CalendarAccount>,
    private var selectedId: Long?,
    private val onItemClick: (CalendarAccount) -> Unit
) : RecyclerView.Adapter<CalendarSelectAdapter.ViewHolder>() {

    // 💡 List 업데이트를 위한 함수 (필요 시 호출)
    fun updateData(newList: List<CalendarAccount>, currentId: Long?) {
        this.items = newList
        this.selectedId = currentId
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_calendar_item_name)
        val tvAccount: TextView = view.findViewById(R.id.tv_calendar_item_account)
        val rbSelect: RadioButton = view.findViewById(R.id.rb_calendar_select)

        fun bind(item: CalendarAccount, isSelected: Boolean) {
            tvName.text = item.displayName
            tvAccount.text = item.accountName

            // 1. 라디오 버튼 체크 상태 설정
            rbSelect.isChecked = isSelected

            // 2. 디자인 요구사항: 체크 상태에 따른 색상 설정 (#98BD0B)
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),  // 체크된 상태
                    intArrayOf(-android.R.attr.state_checked) // 체크되지 않은 상태
                ),
                intArrayOf(
                    Color.parseColor("#98BD0B"), // 체크 시 색상 (primary_500)
                    Color.parseColor("#D1D1D1")  // 미체크 시 테두리 색상
                )
            )
            rbSelect.buttonTintList = colorStateList

            // 3. 아이템 전체 클릭 리스너
            itemView.setOnClickListener {
                val itemId = item.id.toLongOrNull() ?: -1L
                if (selectedId != itemId) {
                    selectedId = itemId // 선택된 ID 업데이트
                    notifyDataSetChanged() // 전체 갱신 (라디오 버튼 상태 반영)
                    onItemClick(item)      // 콜백 실행
                }
            }

            // 라디오 버튼 터치 시에도 부모 클릭 이벤트가 작동하도록 설정
            rbSelect.isClickable = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // XML 파일명이 item_calendar_select인지 확인하세요
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_select, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val itemId = item.id.toLongOrNull() ?: -1L
        holder.bind(item, itemId == selectedId)
    }

    override fun getItemCount() = items.size
}