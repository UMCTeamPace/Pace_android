package com.example.pace.ui.main.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.pace.R
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.BottomSheetSearchFilterBinding
import com.example.pace.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SearchFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSearchFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }

    private val itemSpacingPx: Int by lazy {
        (20 * resources.displayMetrics.density).toInt()
    }


    // 18가지 색상 리스트

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

    }

    // SearchFilterBottomSheet.kt 수정 제안

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let { sheet ->
            sheet.translationY = 0f
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            setupHandleDrag(sheet, behavior)
        }
    }

    private fun setupHandleDrag(
        sheet: View,
        behavior: BottomSheetBehavior<View>
    ) {
        var downY = 0f
        var startTranslationY = 0f

        binding.layoutDragHandleArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheet.animate().cancel()
                    downY = event.rawY
                    startTranslationY = sheet.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragOffset = (startTranslationY + event.rawY - downY).coerceAtLeast(0f)
                    sheet.translationY = dragOffset
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val measuredSheetHeight = sheet.height.takeIf { it > 0 } ?: binding.root.height
                    val closeThreshold = measuredSheetHeight * 0.1f
                    val shouldClose = sheet.translationY >= closeThreshold
                    if (shouldClose) {
                        sheet.animate()
                            .translationY(sheet.height.toFloat())
                            .setDuration(180L)
                            .withEndAction {
                                behavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                            .start()
                    } else {
                        sheet.animate()
                            .translationY(0f)
                            .setDuration(180L)
                            .start()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun setupColorPalette() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. 사용할 수 있는 색상 목록을 가져와서 UI 생성 (최초 1회 또는 목록 변경 시)
            combine(viewModel.usedColors, viewModel.filterColors) { colors, selectedColors ->
                colors to selectedColors
            }.collect { (colors, selectedColors) ->
                binding.layoutColorContainer.removeAllViews()

                colors.forEachIndexed { index, colorStr ->
                    val itemView = layoutInflater.inflate(R.layout.item_filter_color, binding.layoutColorContainer, false)
                    val colorCircle = itemView.findViewById<View>(R.id.view_color_circle)
                    val checkIcon = itemView.findViewById<ImageView>(R.id.iv_check)
                    itemView.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        marginEnd = if (index == colors.lastIndex) 0 else itemSpacingPx
                    }

                    // 색상 파싱 및 적용
                    val colorInt = try {
                        if (colorStr.startsWith("#")) Color.parseColor(colorStr)
                        else colorStr.toInt()
                    } catch (e: Exception) { Color.GRAY }
                    colorCircle.backgroundTintList = ColorStateList.valueOf(colorInt)

                    // 초기 체크 상태 설정
                    checkIcon.visibility = if (selectedColors.contains(colorStr)) View.VISIBLE else View.GONE

                    itemView.setOnClickListener {
                        viewModel.toggleFilterColor(colorStr)
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
