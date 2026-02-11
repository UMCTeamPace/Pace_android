package com.example.pace.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.replace
import androidx.fragment.app.setFragmentResultListener
import com.example.pace.R
import com.example.pace.databinding.FragmentSettingBinding
import com.example.pace.ui.onboarding.OnboardingActivity
import com.kakao.sdk.user.UserApiClient
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.pace.data.viewmodel.SettingsViewModel // 아까 만든 뷰모델
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint // 💡 Hilt를 사용한다면 꼭 추가하세요!
class SettingFragment: Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    // 💡 뷰모델 주입
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        val title = activity?.findViewById<TextView>(R.id.settings_tv)
        title?.text = "설정"

        // Todo: 미리 출발 레이아웃 및 프래그먼트 구현해 연결
        // Todo: 클릭 시 상호 작용하는 코드 작성(현재는 단순 뷰)
        binding.settingsCalendarDefaultIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, DefaultCalendarFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "기본 캘린더"
        }
        binding.settingsCalendarListIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SyncWithCalendarFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "캘린더 목록"
        }
        binding.settingsReminderAlarmIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingReminderFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "일정 알림"
        }
        binding.settingsDepartureAlarmIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingDepartureFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "출발 알림"
        }

        // --- 권한 설정 부분 ---
        binding.settingsPermissionAlarmIv.setOnClickListener { showPermissionDialog("알림") }
        binding.settingsPermissionLocationIv.setOnClickListener { showPermissionDialog("위치") }
        binding.settingsPermissionCalendarIv.setOnClickListener { showPermissionDialog("캘린더") }


        binding.settingsSignoutLl.setOnClickListener {
            // 커스텀 다이얼로그 호출 (확인 버튼 클릭 시 카카오 로그아웃 실행)
            val signoutDialog = SignoutDialog(requireContext())
            signoutDialog.setOnOkClickListener {
                kakaoLogout()
            }
            signoutDialog.show()
        }

        // --- 탈퇴하기 ---
        binding.settingsWithdrawalLl.setOnClickListener {
            val withdrawalDialog = WithdrawalDialog(requireContext()) // 탈퇴 전용 다이얼로그가 있다면
            withdrawalDialog.setOnOkClickListener {
                kakaoUnlink()
            }
            withdrawalDialog.show()
        }

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = activity?.findViewById<TextView>(R.id.settings_tv)
        title?.text = "설정"

        observeRoomData()

        // --- Result Listeners ---
        setFragmentResultListener("earlyDepartureKey") { _, bundle ->
            val resultText = bundle.getString("selectedMinutes") ?: "10분"
            val minutes = resultText.replace("분", "").toIntOrNull() ?: 10
            viewModel.updateEarlyArrival(minutes)
        }

        setFragmentResultListener("scheduleAlarmKey") { _, bundle ->
            val alarmList = bundle.getIntegerArrayList("selectedAlarms") ?: arrayListOf()
            viewModel.updateScheduleAlarms(alarmList)
        }

        setFragmentResultListener("departureAlarmKey") { _, bundle ->
            val alarmList = bundle.getIntegerArrayList("selectedAlarms") ?: arrayListOf()
            viewModel.updateDepartureAlarms(alarmList)
        }

        // --- Click Listeners ---

        // 1. 일정 알림
        binding.settingsReminderAlarmIv.setOnClickListener {
            val currentAlarms = viewModel.userSettings.value?.scheduleAlarms ?: emptyList()
            val fragment = SettingReminderFragment().apply {
                arguments = Bundle().apply { putIntegerArrayList("currentAlarms", ArrayList(currentAlarms)) }
            }
            activity?.findViewById<TextView>(R.id.settings_tv)?.text = "일정 알림"
            parentFragmentManager.beginTransaction().replace(R.id.settings_fcv, fragment).addToBackStack(null).commit()
        }

        // 2. 미리 도착 (바깥으로 분리)
        binding.settingsRouteLl.setOnClickListener {
            activity?.findViewById<TextView>(R.id.settings_tv)?.text = "미리 도착"
            val currentMinutes = binding.settingsRouteMinuteTv.text.toString().replace("분", "").toIntOrNull() ?: 10
            val fragment = SettingEarlyarrivedFragment().apply {
                arguments = Bundle().apply { putInt("currentMinutes", currentMinutes) }
            }
            parentFragmentManager.beginTransaction().replace(R.id.settings_fcv, fragment).addToBackStack(null).commit()
        }

        // 3. 출발 알림 (바깥으로 분리 - 이게 중요합니다!)
        binding.settingsDepartureAlarmIv.setOnClickListener {
            val currentAlarms = viewModel.userSettings.value?.departureAlarms ?: emptyList()
            val fragment = SettingDepartureFragment().apply {
                arguments = Bundle().apply { putIntegerArrayList("currentAlarms", ArrayList(currentAlarms)) }
            }
            activity?.findViewById<TextView>(R.id.settings_tv)?.text = "출발 알림"
            parentFragmentManager.beginTransaction().replace(R.id.settings_fcv, fragment).addToBackStack(null).commit()
        }

        parentFragmentManager.addOnBackStackChangedListener {
            if (parentFragmentManager.backStackEntryCount == 0) {
                activity?.findViewById<TextView>(R.id.settings_tv)?.text = "설정"
            }
        }
    }

    private fun observeRoomData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userSettings.collect { settings ->
                settings?.let {
                    // 1. 기본 캘린더 이름
                    binding.settingsCalendarDefaultTv.text = getCalendarNameById(it.calendarId)

                    // 2. 미리 도착 시간
                    binding.settingsRouteMinuteTv.text = "${it.earlyArrivalTime}분"

                    // 💡 3. 일정 알림 (숫자 -> 문자열 매핑)
                    binding.settingsReminderAlarmTv.text = if (it.scheduleAlarms.isEmpty()) {
                        "없음"
                    } else {
                        it.scheduleAlarms.joinToString(", ") { minutes ->
                            formatAlarmText(minutes)
                        }
                    }

                    // 💡 4. 출발 알림 (숫자 -> 문자열 매핑)
                    binding.settingsDepartureAlarmTv.text = if (it.departureAlarms.isEmpty()) {
                        "없음"
                    } else {
                        it.departureAlarms.joinToString(", ") { minutes ->
                            formatAlarmText(minutes)
                        }
                    }

                    Log.d("SETTINGS_LOCAL", "UI 업데이트 완료: $it")
                }
            }
        }
    }

    private fun formatAlarmText(minutes: Int): String {
        return when (minutes) {
            0 -> "일정 시작 시간"
            5 -> "5분 전"
            10 -> "10분 전"
            15 -> "15분 전"
            30 -> "30분 전"
            60 -> "1시간 전"
            120 -> "2시간 전"
            1440 -> "1일 전"
            2880 -> "2일 전"
            10080 -> "1주일 전"
            else -> "${minutes}분 전" // 매핑되지 않은 값이 있을 경우 대비
        }
    }

    // 1. 권한 이동 다이얼로그 (간단 예시)
    private fun showPermissionDialog(permissionName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${permissionName} 권한 설정")
            .setMessage("${permissionName} 권한을 설정하기 위해 설정 창으로 이동하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 2. 카카오 로그아웃
    private fun kakaoLogout() {
        UserApiClient.instance.logout { error ->
            if (error != null) {
                Log.e("KAKAO", "로그아웃 실패", error)
            } else {
                Log.i("KAKAO", "로그아웃 성공")
                navigateToLogin()
            }
        }
    }

    // 3. 카카오 탈퇴 (연결 끊기)
    private fun kakaoUnlink() {
        UserApiClient.instance.unlink { error ->
            if (error != null) {
                Log.e("KAKAO", "탈퇴 실패", error)
            } else {
                Log.i("KAKAO", "탈퇴 성공")
                navigateToLogin()
            }
        }
    }

    // 4. 로그인 화면으로 이동 (스택 클리어)
    private fun navigateToLogin() {
        // LoginActivity는 실제 로그인 액티비티 클래스명으로 수정하세요
        val intent = Intent(requireContext(), OnboardingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }


    private fun getCalendarNameById(calendarId: Long): String {
        // 만약 온보딩에서 선택 안 함(-1) 상태라면 기본값 반환
        if (calendarId == -1L) return "내 캘린더"

        val projection = arrayOf(
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val uri = android.provider.CalendarContract.Calendars.CONTENT_URI
        val selection = "${android.provider.CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        return try {
            // ContentResolver를 이용해 캘린더 DB 조회
            val cursor = requireContext().contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    it.getString(nameIndex)
                } else {
                    "내 캘린더" // ID는 있는데 결과가 없는 경우
                }
            } ?: "내 캘린더"
        } catch (e: SecurityException) {
            // 캘린더 권한이 없을 경우
            "권한 없음"
        } catch (e: Exception) {
            "내 캘린더"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}