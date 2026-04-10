import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemSchedulePageBinding
import com.example.pace.ui.main.calendar.ScheduleAdapter
import com.example.pace.ui.main.calendar.ScheduleListItem
import java.time.LocalDate
import kotlin.collections.sortedWith

class DailyPageAdapter(
    private val context: Context,
    private var events: Map<LocalDate, List<Schedule>>,
    private val onPinClick: (Schedule) -> Unit,
    private val onItemClick: (Schedule) -> Unit,
    private val onEditSelect: (Long) -> Unit = {}
) : RecyclerView.Adapter<DailyPageAdapter.PageViewHolder>() {

    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()

    val START_POSITION = Int.MAX_VALUE / 2

    fun updateEvents(newEvents: Map<LocalDate, List<Schedule>>, newRouteMap: Map<Long, RouteInfo> = emptyMap()) {
        this.events = newEvents
        this.routeInfoMap = newRouteMap
        notifyDataSetChanged()
    }

    fun getDate(position: Int): LocalDate = LocalDate.now().plusDays((position - START_POSITION).toLong())
    fun getPosition(date: LocalDate): Int = START_POSITION + java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()

    inner class PageViewHolder(val binding: ItemSchedulePageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val scheduleAdapter = ScheduleAdapter(
            context = context,
            items = emptyList(),
            onPinClick = onPinClick,
            onEditSelect = onEditSelect,
            onItemClick = onItemClick,
            routeInfoMap = routeInfoMap
        )

        init {
            scheduleAdapter.setEditMode(false)
            scheduleAdapter.updateSelectedIds(emptySet())
            binding.rvDailyScheduleItem.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = scheduleAdapter
            }
        }

        fun bind(date: LocalDate) {
            val daySchedules = events[date] ?: emptyList()

            val sortedItems = daySchedules.sortedWith(
                compareBy({ !it.isPinned }, { !it.isAllDay }, { it.startTime })
            ).map { ScheduleListItem.ScheduleItem(it) }

            scheduleAdapter.updateRouteInfo(routeInfoMap)
            scheduleAdapter.updateData(sortedItems)

            if (sortedItems.isEmpty()) {
                binding.tvEmptyStateItem.visibility = View.VISIBLE
                binding.rvDailyScheduleItem.visibility = View.GONE
            } else {
                binding.tvEmptyStateItem.visibility = View.GONE
                binding.rvDailyScheduleItem.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        return PageViewHolder(ItemSchedulePageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getDate(position))
    }

    override fun getItemCount(): Int = Int.MAX_VALUE
}
