package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.data.model.response.StationTimetableItem
import com.example.pace.data.viewmodel.TransitViewModel
import com.example.pace.databinding.BottomSheetSubwayTimetableBinding
import com.example.pace.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class TimetableBottomSheet(
    private val station: String,
    private val lineName: String,
): BottomSheetDialogFragment(){
    companion object {
        const val tag = "SubwayTimetable"
    }

    lateinit var binding: BottomSheetSubwayTimetableBinding
    private val viewModel: TransitViewModel by viewModels()
    private val stationName = if(station == "서울역") station else station.dropLast(1)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = BottomSheetSubwayTimetableBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet.let{
            val behavior = BottomSheetBehavior.from(it as View)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            it.layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.bottomSheetSubwayTimetableTv.text = "$station 방면"

        binding.bottomSheetSubwayTimetableWeekdayLl.setOnClickListener {
            binding.bottomSheetSubwayTimetableWeekdayTv.setTextColor(ContextCompat.getColor(context, R.color.primary_500))
            binding.bottomSheetSubwayTimetableWeekdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            binding.bottomSheetSubwayTimetableWeekdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_500))
            binding.bottomSheetSubwayTimetableSaturdayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableSaturdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableSaturdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))
            binding.bottomSheetSubwayTimetableHolidayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableHolidayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableHolidayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))

            observeFirstAndLast("01")
        }
        // todo: "02" 토요일 데이터 제공 X -> 문의
        binding.bottomSheetSubwayTimetableSaturdayLl.setOnClickListener {
            binding.bottomSheetSubwayTimetableWeekdayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableWeekdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableWeekdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))
            binding.bottomSheetSubwayTimetableSaturdayTv.setTextColor(ContextCompat.getColor(context, R.color.primary_500))
            binding.bottomSheetSubwayTimetableSaturdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            binding.bottomSheetSubwayTimetableSaturdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_500))
            binding.bottomSheetSubwayTimetableHolidayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableHolidayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableHolidayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))

            observeFirstAndLast("02")
        }
        binding.bottomSheetSubwayTimetableHolidayLl.setOnClickListener {
            binding.bottomSheetSubwayTimetableWeekdayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableWeekdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableWeekdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))
            binding.bottomSheetSubwayTimetableSaturdayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableSaturdayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableSaturdayView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_400))
            binding.bottomSheetSubwayTimetableHolidayTv.setTextColor(ContextCompat.getColor(context, R.color.primary_500))
            binding.bottomSheetSubwayTimetableHolidayTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            binding.bottomSheetSubwayTimetableHolidayView.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_500))

            observeFirstAndLast("03")
        }
        binding.bottomSheetSubwayTimetableWholeTv.setOnClickListener {
            binding.bottomSheetSubwayTimetableWholeTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_selected)
            binding.bottomSheetSubwayTimetableWholeTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.bottomSheetSubwayTimetableWholeTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            binding.bottomSheetSubwayTimetableFirstTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableFirstTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableFirstTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableLastTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableLastTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableLastTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)

            binding.bottomSheetSubwayTimetableFirstTimeTv.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableFirstTimeLl.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableLastTimeTv.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableLastTimeLl.visibility = View.VISIBLE
        }
        binding.bottomSheetSubwayTimetableFirstTv.setOnClickListener {
            binding.bottomSheetSubwayTimetableWholeTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableWholeTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableWholeTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableFirstTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_selected)
            binding.bottomSheetSubwayTimetableFirstTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.bottomSheetSubwayTimetableFirstTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
            binding.bottomSheetSubwayTimetableLastTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableLastTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableLastTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)

            binding.bottomSheetSubwayTimetableFirstTimeTv.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableFirstTimeLl.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableLastTimeTv.visibility = View.GONE
            binding.bottomSheetSubwayTimetableLastTimeLl.visibility = View.GONE
        }
        binding.bottomSheetSubwayTimetableLastTv.setOnClickListener {
            binding.bottomSheetSubwayTimetableWholeTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableWholeTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableWholeTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableFirstTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_unselected)
            binding.bottomSheetSubwayTimetableFirstTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
            binding.bottomSheetSubwayTimetableFirstTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
            binding.bottomSheetSubwayTimetableLastTv.background = ContextCompat.getDrawable(context, R.drawable.btn_subway_timetable_selected)
            binding.bottomSheetSubwayTimetableLastTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.bottomSheetSubwayTimetableLastTv.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)

            binding.bottomSheetSubwayTimetableFirstTimeTv.visibility = View.GONE
            binding.bottomSheetSubwayTimetableFirstTimeLl.visibility = View.GONE
            binding.bottomSheetSubwayTimetableLastTimeTv.visibility = View.VISIBLE
            binding.bottomSheetSubwayTimetableLastTimeLl.visibility = View.VISIBLE
        }

        binding.bottomSheetSubwayTimetableUpTv.text = "상행 전역 방면"
        binding.bottomSheetSubwayTimetableUpSubTv.text = if(lineName == "2호선") "내선순환" else "상행"
        binding.bottomSheetSubwayTimetableDownTv.text = "하행 전역 방면"
        binding.bottomSheetSubwayTimetableDownSubTv.text = if(lineName == "2호선") "외선순환" else "하행"

        observeFirstAndLast()
    }

    // 시간표 작성 함수
    fun updateTimetableUI(list: List<StationTimetableItem?>, isFirst: Boolean, isUp: Boolean){
        val parentLayout = if(isFirst && isUp)              // 상행/내선순환 첫차
            binding.bottomSheetSubwayTimetableFirstUpLl
        else if(isFirst && !isUp)                           // 하행/외선순환 첫차
            binding.bottomSheetSubwayTimetableFirstDownLl
        else if(!isFirst && isUp)                           // 상행/내선순화 막차
            binding.bottomSheetSubwayTimetableLastUpLl
        else                                                // 하행/외선순화 막차
            binding.bottomSheetSubwayTimetableLastDownLl

        parentLayout.removeAllViews()

        list.forEach {
            if(it != null){
                val childLayout = LinearLayout(context).apply{
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(ContextCompat.getColor(context, R.color.white))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setPadding((12 * context.resources.displayMetrics.density).toInt(), 0, (12 * context.resources.displayMetrics.density).toInt(), 0)
                        setMargins(0, (10 * context.resources.displayMetrics.density).toInt(), 0, (12 * context.resources.displayMetrics.density).toInt())
                    }
                }
                val timeText = TextView(context).apply {
                    if(it.depTime == "0"){
                        text = it.arrTime.substring(0, 2) + ":" + it.arrTime.substring(2, 4)
                    }else{
                        text = it.depTime.substring(0, 2) + ":" + it.depTime.substring(2, 4)
                    }
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    typeface = ResourcesCompat.getFont(context, R.font.roboto_medium)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
                val directionText = TextView(context).apply{
                    text = it.endSubwayStationName
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins((12 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                    }
                }
                childLayout.addView(timeText)
                childLayout.addView(directionText)
                parentLayout.addView(childLayout)
            }
        }
    }
    fun observeFirstAndLast(daily: String = "01"){
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getSubwayFirstAndLast(stationName, lineName, daily, "U")
                viewModel.firstUpSubway.collect {
                    updateTimetableUI(it, isFirst = true, isUp = true)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getSubwayFirstAndLast(stationName, lineName, daily, "U")
                viewModel.lastUpSubway.collect {
                    updateTimetableUI(it, isFirst = false, isUp = true)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.getSubwayFirstAndLast(stationName, lineName, daily, "D")
                viewModel.firstDownSubway.collect {
                    updateTimetableUI(it, isFirst = true, isUp = false)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.getSubwayFirstAndLast(stationName, lineName, daily, "D")
                viewModel.lastDownSubway.collect {
                    updateTimetableUI(it, isFirst = false, isUp = false)
                }
            }
        }
    }
}