package com.example.pace.ui.main.route

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.databinding.ItemRouteScheduleHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RouteScheduleListAdapter(
    private val items: List<RouteScheduleItem>,
    private val onItemClick: (RouteOnlyScheduleData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CONTENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is RouteScheduleItem.DateHeader -> TYPE_HEADER
            is RouteScheduleItem.ScheduleContent -> TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemRouteScheduleHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_CONTENT -> {
                val binding = ItemScheduleBinding.inflate(inflater, parent, false)
                ContentViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RouteScheduleItem.DateHeader -> (holder as HeaderViewHolder).bind(item)
            is RouteScheduleItem.ScheduleContent -> (holder as ContentViewHolder).bind(item.data)
        }
    }

    override fun getItemCount(): Int = items.size

    // 헤더 뷰홀더
    inner class HeaderViewHolder(private val binding: ItemRouteScheduleHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RouteScheduleItem.DateHeader) {
            binding.tvDateHeader.text = item.dateString
        }
    }

    // 일정 내용 뷰홀더
    inner class ContentViewHolder(private val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: RouteOnlyScheduleData) {
            val info = data.scheduleInfo
            val route = data.route

            binding.scheduleTitleTv.text = info.title ?: "제목 없음"

            val startTime = info.startTime?.take(5) ?: "00:00"
            val endTime = info.endTime?.take(5) ?: "00:00"
            binding.scheduleTimeTv.text = "$startTime - $endTime"

            binding.scheduleCheckbox.visibility = View.GONE          // 체크박스 숨김
            binding.scheduleNormalLocationLl.visibility = View.GONE  // 일반 장소 숨김
            binding.scheduleRouteLocationLl.visibility = View.VISIBLE // 경로 레이아웃 보임

            binding.scheduleRepeatIv.visibility = View.GONE
            binding.scheduleRepeatTv.visibility = View.GONE

            val routeLayout = binding.scheduleRouteLocationLl

            // 첫 번째 LinearLayout (경로명)
            val pathLayout = routeLayout.getChildAt(0) as? android.widget.LinearLayout
            val pathTv = pathLayout?.getChildAt(1) as? TextView // 아이콘 다음 텍스트뷰
            pathTv?.text = "${route?.originName ?: "출발지"} → ${route?.destName ?: "도착지"}"

            val infoLayout = routeLayout.getChildAt(1) as? android.widget.LinearLayout
            val timeRangeTv = infoLayout?.getChildAt(0) as? TextView
            val durationTv = infoLayout?.getChildAt(2) as? TextView

            timeRangeTv?.text = "$startTime - $endTime"

            val totalMin = (route?.totalTime ?: 0) / 60
            val hours = totalMin / 60
            val mins = totalMin % 60
            durationTv?.text = if(hours > 0) "${hours}시간 ${mins}분" else "${mins}분"

            // 4. 색상 설정
            try {
                val color = Color.parseColor(info.color ?: "#DC354B")
                binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(color)
            } catch (e: Exception) {
                // 색상 파싱 실패 시 기본값
            }

            binding.scheduleViewTop.setOnClickListener {
                onItemClick(data)
            }

            // 참고: 스와이프 기능(삭제/수정 버튼 클릭)은
            // ItemTouchHelper를 쓰거나 Swipe 라이브러리를 쓸 때 여기서 binding.scheduleDeleteIv 등에 리스너를 달면 됩니다.
            binding.scheduleDeleteIv.setOnClickListener {
                // 삭제 로직 구현
            }
        }
    }
}