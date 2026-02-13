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
    private val items: List<CalendarAccount>,
    private val initialId: Long?,
    private val onItemClick: (CalendarAccount) -> Unit // 💡 콜백 추가
) : RecyclerView.Adapter<CalendarSelectAdapter.ViewHolder>() {

    private var selectedPosition = items.indexOfFirst { it.id.toLong() == initialId }.let {
        if (it == -1) 0 else it
    }

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

            // 💡 중요: 라디오버튼의 상태를 현재 position과 비교해서 결정
            rbSelect.isChecked = (position == selectedPosition)

            itemView.setOnClickListener {
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION && selectedPosition != currentPos) {
                    val oldPos = selectedPosition
                    selectedPosition = currentPos
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)

                    onItemClick(item)

                }
            }

            // 라디오 버튼 자체를 눌러도 클릭 이벤트가 작동하도록 설정
            rbSelect.setOnClickListener { itemView.performClick() }
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