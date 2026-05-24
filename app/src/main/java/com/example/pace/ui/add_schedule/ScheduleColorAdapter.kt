package com.example.pace.ui.add_schedule

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R

data class ColorItem(val colorResId: Int, val colorHex: String, var isSelected: Boolean = false)

class ColorAdapter(
    private val context: Context,
    private val colors: List<ColorItem>,
    private val onColorClick: (String) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {
    private val items = colors.toMutableList()
    private var selectedPos = items.indexOfFirst { it.isSelected }

    fun selectColor(colorHex: String) {
        var newPos = items.indexOfFirst { it.colorHex.equals(colorHex, ignoreCase = true) }
        if (newPos == -1) {
            items.add(0, ColorItem(R.color.gray_600, colorHex, false))
            selectedPos = if (selectedPos == -1) -1 else selectedPos + 1
            notifyItemInserted(0)
            newPos = 0
        }

        val oldPos = selectedPos
        if (oldPos != -1) {
            items[oldPos].isSelected = false
            notifyItemChanged(oldPos)
        }

        selectedPos = newPos
        if (newPos != -1) {
            items[newPos].isSelected = true
            notifyItemChanged(newPos)
        }
    }

    inner class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorView: ImageView = view.findViewById(R.id.colorView)
        fun bind(item: ColorItem) {
            // 원형 배경에 색상 입히기
            if(item.isSelected){
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_selected_schedule_color)?.mutate() as LayerDrawable
                val bg = drawable.findDrawableByLayerId(R.id.schedule_color_bg).mutate() as GradientDrawable
                val front = drawable.findDrawableByLayerId(R.id.schedule_color_front).mutate() as GradientDrawable
                bg.setColor(Color.parseColor(item.colorHex))
                front.setColor(Color.parseColor(item.colorHex))
                colorView.setImageDrawable(drawable)
            }else{
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_schedule_color)?.mutate()
                drawable?.setTint(Color.parseColor(item.colorHex))
                colorView.setImageDrawable(drawable)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedulecolor, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int){
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            selectColor(items[position].colorHex)
            onColorClick(items[position].colorHex)
        }
    }
    override fun getItemCount() = items.size
}
