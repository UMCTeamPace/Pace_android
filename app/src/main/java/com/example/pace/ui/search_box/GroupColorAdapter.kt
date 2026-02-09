package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.ItemGroupColorBinding

class GroupColorAdapter(
    private val colorList: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<GroupColorAdapter.ColorViewHolder>() {

    private var selectedPosition = 0

    inner class ColorViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(colorHex: String, position: Int) {
            try {
                view.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))
            } catch (e: Exception) {
                /* 색상 파싱 에러 처리 */
            }

            view.alpha = if (selectedPosition == position) 1.0f else 0.5f

            view.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(colorHex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = View(parent.context).apply {
            val size = (32 * resources.displayMetrics.density).toInt()
            val margin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            setBackgroundResource(R.drawable.circle_schedulecolor)
        }
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colorList[position], position)
    }

    override fun getItemCount(): Int = colorList.size

    fun getSelectedColor(): String = colorList[selectedPosition]
}