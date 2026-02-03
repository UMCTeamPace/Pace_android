package com.example.pace.ui.add_schedule

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentScheduleRepeatBinding
import java.util.*

class ScheduleRepeatFragment : Fragment() {

    private var _binding: FragmentScheduleRepeatBinding? = null
    private val binding get() = _binding!!

    // 현재 인플레이트되어 붙어있는 상세 레이아웃 (주간/월간 등)
    private var detailView: View? = null

    // 공통 TextWatcher
    private val descriptionWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updateFullDescription() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleRepeatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMainListeners()
        updateFullDescription()
    }

    private fun setupMainListeners() {
        binding.repeatToolbar.setNavigationOnClickListener { sendResultAndBack() }

        // 메인 반복 옵션 (안함, 일간, 주간, 월간, 연간)
        binding.rgRepeatOptions.setOnCheckedChangeListener { _, checkedId ->
            handleLayoutSwitch(checkedId)
            updateFullDescription()
        }

        val endRadioButtons = listOf(binding.rbEndNever, binding.rbEndCount, binding.rbEndDate)

        endRadioButtons.forEach { rb ->
            rb.setOnClickListener { clickedView ->
                // 1. 모든 종료 관련 라디오 버튼의 체크를 해제한 뒤, 클릭된 것만 체크
                endRadioButtons.forEach { it.isChecked = (it == clickedView) }

                // 2. 횟수 입력창(layout_count_input) 등의 가시성 조절
                handleEndLayoutVisibility()

                // 3. 상단 요약 텍스트 업데이트
                updateFullDescription()

                // 4. 횟수 지정 클릭 시 키보드 바로 띄우기 (편의 기능)
                if (clickedView == binding.rbEndCount) {
                    binding.etEndCount.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.etEndCount, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        binding.etEndCount.addTextChangedListener(descriptionWatcher)
    }

    private fun handleLayoutSwitch(checkedId: Int) {
        val container = binding.layoutDynamicDetailContainer
        container.removeAllViews()
        detailView = null

        if (checkedId == R.id.rb_none) {
            container.visibility = View.GONE
            binding.layoutCommonRepeatEnd.visibility = View.GONE
            hideKeyboard()
            return
        }

        container.visibility = View.VISIBLE
        binding.layoutCommonRepeatEnd.visibility = View.VISIBLE

        // 레이아웃 인플레이트
        val layoutId = when (checkedId) {
            R.id.rb_week -> R.layout.layout_repeat_weekly
            R.id.rb_month -> R.layout.layout_repeat_monthly
            R.id.rb_year -> R.layout.layout_repeat_yearly
            else -> null // 일간은 별도 레이아웃 없이 간격만 처리 가능
        }

        layoutId?.let {
            detailView = layoutInflater.inflate(it, container, false)
            container.addView(detailView)
            setupDetailListeners(checkedId)
        }
    }

    private fun setupDetailListeners(checkedId: Int) {
        val v = detailView ?: return
        when (checkedId) {
            R.id.rb_week -> {
                v.findViewById<EditText>(R.id.et_week_interval)?.addTextChangedListener(descriptionWatcher)
                val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
                dayIds.forEach { id ->
                    v.findViewById<CheckBox>(id)?.setOnCheckedChangeListener { _, _ -> updateFullDescription() }
                }
            }
            R.id.rb_month -> {
                // 1. detailView가 null인지 먼저 확인 (let 사용 권장)
                val v = detailView ?: return

                val rgMonthly = v.findViewById<RadioGroup>(R.id.rg_monthly_detail)
                val gridDates = v.findViewById<GridLayout>(R.id.grid_monthly_dates)
                val rbOrdinal = v.findViewById<RadioButton>(R.id.rb_monthly_ordinal_day)
                val rbFixed = v.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)


                // 2. 텍스트 설정
                val dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                rbFixed?.text = "${dayOfMonth}일 마다 반복"
                rbOrdinal?.text = "${getOrdinalDayOfWeekText()} 마다 반복"

                // 3. 그리드 초기화
                gridDates?.let { setupDateGrid(it) }

                // 4. 리스너 등록
                rgMonthly?.setOnCheckedChangeListener { _, checkedId ->
                    // ID 오타 수정: rb_monthly_specific_date
                    gridDates?.visibility = if (checkedId == R.id.rb_monthly_specific_date) View.VISIBLE else View.GONE
                    updateFullDescription()
                }
            }
            R.id.rb_year -> {
                val v = detailView ?: return // return@let 대신 return 사용

                val rgYearly = v.findViewById<RadioGroup>(R.id.rg_yearly_detail)
                val gridMonths = v.findViewById<GridLayout>(R.id.grid_yearly_months)
                val rbSpecific = v.findViewById<RadioButton>(R.id.rb_yearly_specific_date) // XML ID와 일치

                // 1. 1월~12월 그리드 생성 (이 함수가 반드시 호출되어야 달이 생깁니다)
                gridMonths?.let { setupMonthGrid(it) }

                // 2. 라디오 버튼 클릭 리스너 설정
                rgYearly?.setOnCheckedChangeListener { _, checkedId ->
                    // 사용자가 '반복 날짜 선택'을 눌렀을 때만 그리드를 보여줌
                    gridMonths?.visibility = if (checkedId == R.id.rb_yearly_specific_date) View.VISIBLE else View.GONE
                    updateFullDescription()
                }

                // 간격(n년마다) 입력창 리스너
                v.findViewById<EditText>(R.id.et_year_interval)?.addTextChangedListener(descriptionWatcher)
            }
        }
    }

    // 1~31일 그리드 생성 함수
    private fun setupDateGrid(grid: GridLayout) {
        grid.removeAllViews()
        // 7열로 확실히 고정
        grid.columnCount = 7

        for (i in 1..31) {
            val cb = CheckBox(requireContext()).apply {
                text = i.toString()
                buttonDrawable = null
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_month_circle)
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.selector_month_text))

                textSize = 12f
                setTypeface(null, android.graphics.Typeface.NORMAL)

                layoutParams = GridLayout.LayoutParams().apply {
                    // 한 줄에 7개를 넣기 위해 너비를 약간 줄여 38dp~40dp로 고정
                    width = dpToPx(38)
                    height = dpToPx(38)

                    // 마진을 최소화하여 7개가 한 줄에 들어가도록 함
                    setMargins(dpToPx(1), dpToPx(4), dpToPx(1), dpToPx(4))

                    // 가중치를 제거하거나 상위 뷰의 여백에 맞춰 조정
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                }
                setOnCheckedChangeListener { _, _ -> updateFullDescription() }
            }
            grid.addView(cb)
        }
    }

    private fun setupMonthGrid(grid: GridLayout) {
        grid.removeAllViews()
        for (i in 1..12) {
            val cb = CheckBox(requireContext()).apply {
                text = "${i}월"
                buttonDrawable = null // 기본 체크박스 제거
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_month_circle)
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.selector_month_text))
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.NORMAL)

                layoutParams = GridLayout.LayoutParams().apply {
                    // 원형 유지를 위해 가로세로를 동일한 px로 고정
                    width = dpToPx(42)
                    height = dpToPx(42)
                    setMargins(dpToPx(2), dpToPx(8), dpToPx(2), dpToPx(8))
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setOnCheckedChangeListener { _, _ -> updateFullDescription() }
            }
            grid.addView(cb)
        }
    }

    private fun getOrdinalDayOfWeekText(): String {
        val calendar = java.util.Calendar.getInstance() // 실제로는 일정 시작일을 넣는 것을 권장합니다.
        val dayNames = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

        val dayOfWeekName = dayNames[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val ordinal = calendar.get(java.util.Calendar.DAY_OF_WEEK_IN_MONTH)

        val ordinalNames = listOf("첫 번째", "두 번째", "세 번째", "네 번째", "다섯 번째")
        val ordinalText = if (ordinal <= 5) ordinalNames[ordinal - 1] else ""

        return "$ordinalText $dayOfWeekName"
    }

    fun getOrdinalDayOfWeek(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 요일 (1:일, 2:월 ... 6:금)
        val ordinal = calendar.get(Calendar.DAY_OF_WEEK_IN_MONTH) // 몇 번째인지 (1~5)

        val dayName = when(dayOfWeek) {
            Calendar.SUNDAY -> "일요일"
            Calendar.MONDAY -> "월요일"
            Calendar.TUESDAY -> "화요일"
            Calendar.WEDNESDAY -> "수요일"
            Calendar.THURSDAY -> "목요일"
            Calendar.FRIDAY -> "금요일"
            Calendar.SATURDAY -> "토요일"
            else -> ""
        }

        val ordinalName = when(ordinal) {
            1 -> "첫 번째"
            2 -> "두 번째"
            3 -> "세 번째"
            4 -> "네 번째"
            5 -> "다섯 번째"
            else -> ""
        }

        return "$ordinalName $dayName"
    }

    private fun updateFullDescription() {
        val typeId = binding.rgRepeatOptions.checkedRadioButtonId
        if (typeId == R.id.rb_none) {
            binding.tvRepeatDescription.text = "일정 반복을 진행하지 않습니다."
            return
        }

        // 1. 간격(Interval) 추출
        val interval = when (typeId) {
            R.id.rb_week -> detailView?.findViewById<EditText>(R.id.et_week_interval)?.text.toString()
            R.id.rb_month -> detailView?.findViewById<EditText>(R.id.et_month_interval)?.text.toString()
            R.id.rb_year -> detailView?.findViewById<EditText>(R.id.et_year_interval)?.text.toString()
            else -> "1" // 일간 등
        }.ifEmpty { "1" }

        // 2. 상세 정보 (주간 요일 / 월간 날짜)
        var detailInfo = ""
        if (typeId == R.id.rb_week) {
            val selectedDays = mutableListOf<String>()
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
            dayIds.forEachIndexed { i, id ->
                if (detailView?.findViewById<CheckBox>(id)?.isChecked == true) selectedDays.add(dayNames[i])
            }
            if (selectedDays.isNotEmpty()) detailInfo = "${selectedDays.joinToString(", ")}요일"
        }

        if (typeId == R.id.rb_month) {
            val v = detailView ?: return
            val isSpecificDate = v.findViewById<RadioButton>(R.id.rb_monthly_specific_date)?.isChecked == true

            if (isSpecificDate) {
                val selectedDates = mutableListOf<Int>()
                val grid = v.findViewById<GridLayout>(R.id.grid_monthly_dates)
                for (i in 0 until (grid?.childCount ?: 0)) {
                    val cb = grid?.getChildAt(i) as? CheckBox
                    if (cb?.isChecked == true) {
                        selectedDates.add(cb.text.toString().toInt())
                    }
                }
                if (selectedDates.isNotEmpty()) {
                    detailInfo = "${selectedDates.sorted().joinToString(", ")}일"
                }
            }
        }

        if (typeId == R.id.rb_year) {
            val v = detailView ?: return
            val isSpecificMonth = v.findViewById<RadioButton>(R.id.rb_yearly_specific_date)?.isChecked == true

            if (isSpecificMonth) {
                val selectedMonths = mutableListOf<String>()
                val grid = v.findViewById<GridLayout>(R.id.grid_yearly_months)
                for (i in 0 until (grid?.childCount ?: 0)) {
                    val cb = grid?.getChildAt(i) as? CheckBox
                    if (cb?.isChecked == true) selectedMonths.add(cb.text.toString())
                }
                if (selectedMonths.isNotEmpty()) detailInfo = "${selectedMonths.joinToString(", ")} 반복"
            }
        }

        // 3. 문장 조합
        val unit = when(typeId) {
            R.id.rb_daily -> "일"
            R.id.rb_week -> "주"
            R.id.rb_month -> "개월"
            R.id.rb_year -> "년"
            else -> ""
        }
        val intervalText = if (interval == "1") "매$unit" else "${interval}${unit}마다"

        val endText = when {
            binding.rbEndNever.isChecked -> "반복됩니다"
            binding.rbEndCount.isChecked -> "${binding.etEndCount.text.toString().ifEmpty { "1" }}회 반복됩니다"
            binding.rbEndDate.isChecked -> "종료 날짜까지 반복됩니다"
            else -> "반복됩니다"
        }

        binding.tvRepeatDescription.text = "$intervalText $detailInfo $endText".replace("  ", " ").trim()
    }

    private fun handleEndLayoutVisibility() {
        if (binding.rbEndCount.isChecked) {
            // 체크되었을 때 '횟수 지정' 글자를 지우고 입력창을 보여줌
            binding.rbEndCount.text = ""
            binding.layoutCountInput.visibility = View.VISIBLE
        } else {
            // 체크 해제 시 다시 글자를 보여주고 입력창을 숨김
            binding.rbEndCount.text = "횟수 지정"
            binding.layoutCountInput.visibility = View.GONE
        }
    }

    private fun sendResultAndBack() {
        // 현재 요약된 텍스트를 결과로 전달
        val finalResult = binding.tvRepeatDescription.text.toString()
        parentFragmentManager.setFragmentResult("repeatKey", bundleOf("selectedRepeat" to finalResult))
        parentFragmentManager.popBackStack()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}