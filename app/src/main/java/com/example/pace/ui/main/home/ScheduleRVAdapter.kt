package com.example.pace.ui.main.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.ItemScheduleBinding

class ScheduleRVAdapter(
    private val scheduleList:List<String>,
    private val context: Context
): RecyclerView.Adapter<ScheduleRVAdapter.ViewHolder>() {
    lateinit var mOnClickListener: MyOnClickListener

    interface MyOnClickListener{
        fun showModalCase(position: Int)
    }
    fun setMyOnClickListener(myOnClickListener: MyOnClickListener){
        mOnClickListener = myOnClickListener
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(scheduleList[position])
        holder.binding.root.setOnClickListener {
            showModalCase(position)
        }
        holder.binding.schedulePinIv.setOnClickListener {
            holder.binding.schedulePinnedIv.visibility = View.VISIBLE
        }
        holder.binding.scheduleDeleteIv.setOnClickListener {
            val deleteScheduleDialog = DeleteScheduleDialog(context)
            deleteScheduleDialog.show()
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun showModalCase(position: Int){
        val modalCaseDialog = ModalCaseDialog(context, scheduleList, position)
        modalCaseDialog.show()
    }

    inner class ViewHolder(val binding: ItemScheduleBinding):RecyclerView.ViewHolder(binding.root){
        fun bind(text:String){
            binding.scheduleCategoryIv.setImageResource(R.drawable.ic_schedule_orange)
            binding.scheduleTitleTv.text = text
            binding.scheduleTimeTv.text = "하루 종일"
            binding.scheduleNormalLocationTv.text = "장소"
            binding.schedulePinnedIv.visibility = View.GONE
            binding.scheduleAlertTv.visibility = View.GONE
            binding.scheduleCheckbox.visibility = View.GONE
            binding.scheduleRouteLocationLl.visibility = View.GONE
        }
    }
}