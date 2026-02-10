package com.example.pace.ui.add_schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R // [중요] android.R 대신 프로젝트의 R을 임포트하세요
import com.example.pace.data.model.CalendarAccount

class CalendarSelectAdapter(
    private val items: List<CalendarAccount>
) : RecyclerView.Adapter<CalendarSelectAdapter.ViewHolder>() {

    private var selectedPosition = 0

    // 현재 선택된 아이템을 반환하는 함수 추가
    fun getSelectedItem(): CalendarAccount? {
        return if (items.isNotEmpty()) items[selectedPosition] else null
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_calendar_item_name)
        val tvAccount: TextView = view.findViewById(R.id.tv_calendar_item_account)
        val rbSelect: RadioButton = view.findViewById(R.id.rb_calendar_select)

        fun bind(item: CalendarAccount, position: Int) {
            tvName.text = item.displayName
            tvAccount.text = item.accountName
            rbSelect.isChecked = (position == selectedPosition)

            itemView.setOnClickListener {
                if (selectedPosition != adapterPosition) {
                    val oldPos = selectedPosition
                    selectedPosition = adapterPosition
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)
                    // 이제 클릭 시 바로 콜백(onItemClick)을 실행하지 않습니다.
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_select, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size
}