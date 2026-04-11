package com.example.pace.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
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
        val itemId = item.id.toLongOrNull() ?: -1L

        holder.bind(item, itemId == selectedId)

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
            accountTv.text = "(${item.accountName})"
            radioButton.isChecked = isSelected
        }
    }
}
