package com.example.pace.ui.settings

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
    private var syncedIds = setOf<Long>()

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

        holder.calendarNameTv.text = item.displayName
        holder.accountNameTv.text = "(${item.accountName})"

        holder.toggleIv.setImageResource(
            if (isSelected) R.drawable.ic_toggle_selected
            else R.drawable.ic_toggle_unselected
        )

        holder.itemView.setOnClickListener {
            onToggleChanged(item.id.toLong(), !isSelected)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val calendarNameTv: TextView = view.findViewById(R.id.tv_calendar_name)
        val accountNameTv: TextView = view.findViewById(R.id.tv_account_name)
        val toggleIv: ImageView = view.findViewById(R.id.iv_sync_toggle)
    }
}
