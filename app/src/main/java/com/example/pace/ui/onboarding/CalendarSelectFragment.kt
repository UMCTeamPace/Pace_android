package com.example.pace.ui.onboarding

import android.Manifest
import android.graphics.Color
import com.example.pace.R
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentCalendarSelectBinding
import com.example.pace.ui.main.MainActivity
import androidx.fragment.app.activityViewModels // 추가
import com.example.pace.data.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint // 추가


@AndroidEntryPoint
class CalendarSelectFragment : Fragment() {
    private var _binding: FragmentCalendarSelectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    // 선택된 캘린더 ID 저장용 (동적 생성되므로 리스트 대신 변수로 관리)
    private var selectedId: Long = -1L
    private val checkBoxMap = mutableMapOf<Long, CheckBox>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCalendars() // 시스템 캘린더 불러오기

        binding.btnStart.setOnClickListener {
            if (selectedId == -1L) {
                Toast.makeText(requireContext(), "사용하실 캘린더를 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleCompleteOnboarding()
        }
    }

    private fun loadCalendars() {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )

        val cursor = requireContext().contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameColumn = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountColumn = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val account = it.getString(accountColumn)

                addCalendarCheckBox(id, name, account)
            }
        }
    }

    private fun addCalendarCheckBox(id: Long, name: String, account: String) {
        val checkBox = CheckBox(requireContext()).apply {
            text = "$name\n($account)"
            buttonDrawable = null // 기본 체크박스 제거
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.selector_circle_checkbox, 0)
            setPadding(48, 32, 48, 32)
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        checkBox.setOnClickListener {
            // 단일 선택 로직
            checkBoxMap.values.forEach { it.isChecked = false }
            checkBox.isChecked = true
            selectedId = id
        }

        checkBoxMap[id] = checkBox
        binding.layoutCalendarList.addView(checkBox)

        // 구분선 추가
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#F1F3F5"))
        }
        binding.layoutCalendarList.addView(divider)
    }

    private fun handleCompleteOnboarding() {
        viewModel.selectedCalendarId = selectedId
        viewModel.completeOnboarding()
        moveToMainActivity()
    }

    private fun moveToMainActivity() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    fun TextView.setBoldText(fullText: String, boldKeywords: List<String>) {
        val spannable = SpannableStringBuilder(fullText)

        boldKeywords.forEach { keyword ->
            val start = fullText.indexOf(keyword)
            if (start != -1) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        this.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}