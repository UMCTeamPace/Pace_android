package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R

data class ColorItem(val colorResId: Int, val colorHex: String)

class ColorAdapter(
    private val colors: List<ColorItem>,
    private val onColorClick: (String) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorView: View = view.findViewById(R.id.colorView)
        fun bind(item: ColorItem) {
            // 원형 배경에 색상 입히기
            colorView.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, item.colorResId)
            )
            itemView.setOnClickListener { onColorClick(item.colorHex) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedulecolor, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) = holder.bind(colors[position])
    override fun getItemCount() = colors.size
}