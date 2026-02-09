import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemScheduleBinding // 레이아웃 파일명에 맞춰 수정하세요

class SearchAdapter(private var query: String = "") : ListAdapter<Schedule, SearchAdapter.SearchViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding =
            ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position), query)
    }

    fun updateQuery(newQuery: String) {
        this.query = newQuery
        // notify 대신 리스트 자체를 새로고침하도록 유도
        notifyDataSetChanged()
    }

    class SearchViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: Schedule, query: String) {
            val title = schedule.title ?: "제목 없음"

            // 검색어 하이라이트 로직
            if (query.isBlank()) {
                binding.scheduleTitleTv.text = title
            } else {
                val start = title.indexOf(query, ignoreCase = true)
                if (start >= 0 && query.isNotEmpty()) {
                    val spannable = SpannableString(title)
                    val end = start + query.length
                    try {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#8BC34A")),
                            start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        binding.scheduleTitleTv.text = spannable
                    } catch (e: Exception) {
                        binding.scheduleTitleTv.text = title
                    }
                } else {
                    binding.scheduleTitleTv.text = title
                }
            }

            // 1. 색상 설정
            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> Color.parseColor("#A2BD3B") // 기본 색상
            }
            binding.scheduleCategoryIv.imageTintList =
                android.content.res.ColorStateList.valueOf(colorResId)

            // 2. 시간 표시
            if (schedule.isAllDay) {
                binding.scheduleTimeTv.text = "하루 종일"
            } else {
                binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
            }

            // 3. 반복 문자열 표시
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                // TODO: Schedule 객체에 사람이 읽을 수 있는 반복 문자열 필드가 있다면 그것을 사용.
                binding.scheduleRepeatTv.text = "반복 설정됨"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            // 4. 장소 표시 (SearchFragment는 경로를 보여주지 않음)
            if (!schedule.location.isNullOrEmpty()) {
                binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                binding.scheduleNormalLocationIv.visibility = View.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location
                binding.scheduleRouteLocationLl.visibility = View.GONE // 검색 결과에서는 경로 위치 숨김
            } else {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleNormalLocationIv.visibility = View.GONE
                binding.scheduleNormalLocationTv.text = "" // 텍스트도 비워둠
                binding.scheduleRouteLocationLl.visibility = View.GONE // 항상 숨김
            }

            // 5. 체크박스: SearchFragment에서만 보이도록 설정
            binding.scheduleCheckbox.visibility = View.VISIBLE
            binding.scheduleCheckbox.isChecked = schedule.isCompleted // 또는 선택된 아이템 목록 기반

            // 고정 아이콘 (isPinned 상태에 따라)
            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            binding.scheduleAlertTv.visibility = View.GONE
        }

    }
    companion object DiffCallback : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean =
            oldItem == newItem
    }
}