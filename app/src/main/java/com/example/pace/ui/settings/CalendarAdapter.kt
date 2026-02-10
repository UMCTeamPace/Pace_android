package com.example.pace.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.CalendarAccount

class CalendarAdapter(
    private val onItemSelected: (Long) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var items = listOf<CalendarAccount>()
    private var selectedId: Long = -1L

    fun submitList(newList: List<CalendarAccount>, currentSelectedId: Long) {
        items = newList
        selectedId = currentSelectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_account, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val item = items[position]
        // String ID를 Long으로 변환할 때 발생할 수 있는 오류 방지
        val itemId = item.id.toLongOrNull() ?: -1L

        holder.bind(item, itemId == selectedId)

        // 아이템 전체 클릭 리스너
        holder.itemView.setOnClickListener {
            if (selectedId != itemId) {
                onItemSelected(itemId)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTv: TextView = view.findViewById(R.id.tv_calendar_name)
        private val accountTv: TextView = view.findViewById(R.id.tv_account_name)
        private val radioButton: RadioButton = view.findViewById(R.id.rb_calendar_select)

        fun bind(item: CalendarAccount, isSelected: Boolean) {
            nameTv.text = item.displayName
            accountTv.text = item.accountName
            radioButton.isChecked = isSelected

            // 체크 상태에 따른 테두리 + 내부 색상 통합 설정
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),  // 체크된 상태
                    intArrayOf(-android.R.attr.state_checked) // 체크되지 않은 상태
                ),
                intArrayOf(
                    Color.parseColor("#98BD0B"), // 체크 시 색상 (primary_500)
                    Color.parseColor("#D1D1D1")  // 미체크 시 테두리 색상 (연한 회색)
                )
            )

            // 이 부분이 핵심입니다.
            radioButton.buttonTintList = colorStateList
        }
    }
}