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
                // 🔥 기존 item_schedule.xml 재사용
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

            // 1. 기본 정보 바인딩
            binding.scheduleTitleTv.text = info.title ?: "제목 없음"

            // 시간 포맷팅 (HH:mm - HH:mm)
            val startTime = info.startTime?.take(5) ?: "00:00"
            val endTime = info.endTime?.take(5) ?: "00:00"
            binding.scheduleTimeTv.text = "$startTime - $endTime"

            // 2. 🔥 뷰 가시성 조절 (요청사항 반영)
            binding.scheduleCheckbox.visibility = View.GONE          // 체크박스 숨김
            binding.scheduleNormalLocationLl.visibility = View.GONE  // 일반 장소 숨김
            binding.scheduleRouteLocationLl.visibility = View.VISIBLE // 경로 레이아웃 보임

            // 3. 경로 정보 바인딩 (RouteLocationLL 내부)
            // item_schedule.xml 구조상 LinearLayout 내부의 TextView들을 찾아야 함
            // Binding 객체에서 ID로 직접 접근 (XML ID가 schedule_route_location_ll 내부 자식 뷰들에 ID가 없으면 findViewById 사용 필요할 수 있음)
            // 제공해주신 XML에는 내부 텍스트뷰에 ID가 없으므로 아래와 같이 처리하거나 XML에 ID 추가 권장.
            // 일단 LinearLayout의 자식 순서로 접근하거나 텍스트뷰에 ID를 추가했다고 가정하고 작성합니다.

            // (만약 XML의 내부 TextView들에 ID가 없다면 추가해주세요: tv_route_path, tv_route_time_info)
            // 여기서는 코드로 직접 찾아서 넣는 예시입니다.
            val routeLayout = binding.scheduleRouteLocationLl

            // 첫 번째 LinearLayout (경로명)
            val pathLayout = routeLayout.getChildAt(0) as? android.widget.LinearLayout
            val pathTv = pathLayout?.getChildAt(1) as? TextView // 아이콘 다음 텍스트뷰
            pathTv?.text = "${route?.originName ?: "출발지"} → ${route?.destName ?: "도착지"}"

            // 두 번째 LinearLayout (시간 정보)
            val infoLayout = routeLayout.getChildAt(1) as? android.widget.LinearLayout
            val timeRangeTv = infoLayout?.getChildAt(0) as? TextView
            val durationTv = infoLayout?.getChildAt(2) as? TextView // '|' 다음 텍스트뷰

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

            // 5. 알림 라벨 (출발 알림이 있으면 표시)
//            val departureAlert = data.reminders?.find { it.reminderType == "DEPARTURE" }
//            if (departureAlert != null) {
//                binding.scheduleAlertTv.visibility = View.VISIBLE
//                binding.scheduleAlertTv.text = "출발 ${departureAlert.minutesBefore}분 전"
//            } else {
//                binding.scheduleAlertTv.visibility = View.GONE
//            }

            // 6. 클릭 리스너 (스와이프 레이아웃의 foreground인 top 뷰에 클릭 리스너 설정)
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