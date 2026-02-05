package com.example.pace.ui.main.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemModalBinding

class ModalVPAdapter(
    private val scheduleList:List<Schedule>
): RecyclerView.Adapter<ModalVPAdapter.ViewHolder>() {
    lateinit var binding: ItemModalBinding
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        binding = ItemModalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(scheduleList[position])
    }

    override fun getItemCount(): Int = scheduleList.size

    inner class ViewHolder(val binding: ItemModalBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(schedule: Schedule){
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"
        }
    }
}