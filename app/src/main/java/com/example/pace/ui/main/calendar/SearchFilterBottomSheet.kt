package com.example.pace.ui.main.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pace.R
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.BottomSheetSearchFilterBinding
import com.example.pace.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class SearchFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSearchFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }

    private var selectedView: View? = null

    // 18가지 색상 리스트
    private val colorList = listOf(
        R.color.schedule_1, R.color.schedule_2, R.color.schedule_3,
        R.color.schedule_4, R.color.schedule_5, R.color.schedule_6,
        R.color.schedule_7, R.color.schedule_8, R.color.schedule_9,
        R.color.schedule_10, R.color.schedule_11, R.color.schedule_12,
        R.color.schedule_13, R.color.schedule_14, R.color.schedule_15,
        R.color.schedule_16, R.color.schedule_17, R.color.schedule_18
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSearchFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupColorPalette() // 18개 색상 동적 생성
        setupSwitch()       // 스위치 설정

        binding.btnApply.setOnClickListener {
            viewModel.searchWithCurrentQuery()
            dismiss()
        }
    }

    // SearchFilterBottomSheet.kt 수정 제안

    private fun setupColorPalette() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. 사용할 수 있는 색상 목록을 가져와서 UI 생성 (최초 1회 또는 목록 변경 시)
            viewModel.usedColors.collect { colors ->
                binding.layoutColorContainer.removeAllViews()

                colors.forEach { colorStr ->
                    val itemView = layoutInflater.inflate(R.layout.item_filter_color, binding.layoutColorContainer, false)
                    val colorCircle = itemView.findViewById<View>(R.id.view_color_circle)
                    val checkIcon = itemView.findViewById<ImageView>(R.id.iv_check)

                    // 색상 파싱 및 적용
                    val colorInt = try {
                        if (colorStr.startsWith("#")) Color.parseColor(colorStr)
                        else colorStr.toInt()
                    } catch (e: Exception) { Color.GRAY }
                    colorCircle.backgroundTintList = ColorStateList.valueOf(colorInt)

                    // 초기 체크 상태 설정
                    checkIcon.visibility = if (viewModel.filterColors.value.contains(colorStr)) View.VISIBLE else View.GONE

                    itemView.setOnClickListener {
                        viewModel.toggleFilterColor(colorStr)
                        // UI 즉시 토글
                        checkIcon.visibility = if (checkIcon.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }

                    binding.layoutColorContainer.addView(itemView)
                }
            }
        }
    }

    private fun setupSwitch() {
        binding.switchIncludeRoute.isChecked = viewModel.filterIncludeRoute.value
        binding.switchIncludeRoute.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeRouteFilter(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}