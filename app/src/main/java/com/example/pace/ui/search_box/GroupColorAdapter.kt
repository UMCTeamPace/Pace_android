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

    inner class ColorViewHolder(val binding: ItemGroupColorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(colorHex: String, position: Int) {
            val color = Color.parseColor(colorHex)

            binding.ivColorCircle.imageTintList = ColorStateList.valueOf(color)

            if (selectedPosition == position) {
                binding.ivSelectionRing.visibility = View.VISIBLE
            } else {
                binding.ivSelectionRing.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(colorHex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val binding = ItemGroupColorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colorList[position], position)
    }

    override fun getItemCount(): Int = colorList.size

    fun getSelectedColor(): String = colorList[selectedPosition]
}