package com.example.pace.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.repository.repository.MemberControllerRepository
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.databinding.FragmentSettingBinding
import com.example.pace.ui.splash.SplashActivity
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.user.UserApiClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : Fragment() {
    @Inject
    lateinit var memberControllerRepository: MemberControllerRepository

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = activity?.findViewById<TextView>(R.id.settings_tv)
        title?.text = "설정"

        observeRoomData()
        setupFragmentResultListeners()
        setupClickListeners(title)

        parentFragmentManager.addOnBackStackChangedListener {
            if (parentFragmentManager.backStackEntryCount == 0) {
                activity?.findViewById<TextView>(R.id.settings_tv)?.text = "설정"
            }
        }
    }

    private fun setupFragmentResultListeners() {
        setFragmentResultListener("earlyDepartureKey") { _, bundle ->
            val minutes = bundle.getInt("selectedMinutes", 0)
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
    }

    private fun setupClickListeners(title: TextView?) {
        binding.settingsCalendarDefaultLl.setOnClickListener {
            navigateTo(DefaultCalendarFragment(), "기본 캘린더", title)
        }
        binding.settingsCalendarListLl.setOnClickListener {
            navigateTo(SyncWithCalendarFragment(), "캘린더 목록", title)
        }

        binding.settingsReminderAlarmLl.setOnClickListener {
            val currentAlarms = viewModel.userSettings.value?.scheduleAlarms ?: emptyList()
            val fragment = SettingReminderFragment().apply {
                arguments = Bundle().apply {
                    putIntegerArrayList("currentAlarms", ArrayList(currentAlarms))
                }
            }
            navigateTo(fragment, "일정 알림", title)
        }

        binding.settingsRouteLl.setOnClickListener {
            val currentMinutes = binding.settingsRouteMinuteTv.text.toString()
                .replace("분", "")
                .let { if (it == "안함") 0 else it.toIntOrNull() ?: 10 }
            val fragment = SettingEarlyarrivedFragment().apply {
                arguments = Bundle().apply { putInt("currentMinutes", currentMinutes) }
            }
            navigateTo(fragment, "미리 알림", title)
        }

        binding.settingsDepartureAlarmLl.setOnClickListener {
            val currentAlarms = viewModel.userSettings.value?.departureAlarms ?: emptyList()
            val fragment = SettingDepartureFragment().apply {
                arguments = Bundle().apply {
                    putIntegerArrayList("currentAlarms", ArrayList(currentAlarms))
                }
            }
            navigateTo(fragment, "출발 알림", title)
        }

        binding.settingsPermissionAlarmIv.setOnClickListener { showPermissionDialog("알림") }
        binding.settingsPermissionLocationIv.setOnClickListener { showPermissionDialog("위치") }
        binding.settingsPermissionCalendarIv.setOnClickListener { showPermissionDialog("캘린더") }
        binding.settingsPermissionFullscreenIv.setOnClickListener {
            showFullScreenPermissionDialog()
        }

        binding.settingsSignoutLl.setOnClickListener {
            val signoutDialog = SignoutDialog(requireContext())
            signoutDialog.setOnOkClickListener { logoutMember() }
            signoutDialog.show()
        }

        binding.settingsWithdrawalLl.setOnClickListener {
            val withdrawalDialog = WithdrawalDialog(requireContext())
            withdrawalDialog.setOnOkClickListener {
                Log.d("KAKAO", "탈퇴 함수 호출")
                withdrawMember()
            }
            withdrawalDialog.show()
        }
    }

    private fun withdrawMember() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as PaceApplication
            val accessToken = app.authDataStore.getAccessToken()

            if (accessToken.isNullOrBlank()) {
                Log.e("WITHDRAW", "저장된 액세스 토큰이 없어 바로 로컬 탈퇴 처리합니다.")
                kakaoUnlink()
                return@launch
            }

            val bearerToken = if (accessToken.startsWith("Bearer ")) {
                accessToken
            } else {
                "Bearer $accessToken"
            }

            when (val response = memberControllerRepository.withdraw(bearerToken)) {
                is DefaultResponse.Success -> {
                    Log.i("WITHDRAW", "회원 탈퇴 API 성공")
                    kakaoUnlink()
                }
                is DefaultResponse.Failure -> {
                    Log.e("WITHDRAW", "회원 탈퇴 API 실패: ${response.code}, ${response.message}")
                }
            }
        }
    }


    private fun logoutMember() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as PaceApplication
            val accessToken = app.authDataStore.getAccessToken()

            if (accessToken.isNullOrBlank()) {
                Log.e("LOGOUT", "저장된 액세스 토큰이 없어 로컬 정리 후 연결 해제를 진행합니다.")
                app.authDataStore.clearTokens()
                kakaoUnlink()
                return@launch
            }

            val bearerToken = if (accessToken.startsWith("Bearer ")) {
                accessToken
            } else {
                "Bearer $accessToken"
            }

            when (val response = memberControllerRepository.logout(bearerToken)) {
                is DefaultResponse.Success -> {
                    Log.i("LOGOUT", "로그아웃 API 성공")
                    kakaoUnlink()
                }
                is DefaultResponse.Failure -> {
                    Log.e("LOGOUT", "로그아웃 API 실패: ${response.code}, ${response.message}")
                }
            }
        }
    }

    private fun navigateTo(fragment: Fragment, titleText: String, titleView: TextView?) {
        titleView?.text = titleText
        parentFragmentManager.beginTransaction()
            .replace(R.id.settings_fcv, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeRoomData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userSettings.collect { settings ->
                settings?.let {
                    binding.settingsCalendarDefaultTv.text = getCalendarNameById(it.calendarId)
                    binding.settingsRouteMinuteTv.text = "${it.earlyArrivalTime}분"

                    binding.settingsReminderAlarmTv.text = if (it.scheduleAlarms.isEmpty()) {
                        "없음"
                    } else {
                        it.scheduleAlarms.joinToString(", ") { minutes ->
                            formatAlarmText(minutes)
                        }
                    }

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
            else -> "${minutes}분 전"
        }
    }

    private fun showPermissionDialog(permissionName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${permissionName} 권한 설정")
            .setMessage("${permissionName} 권한을 설정하기 위해 설정 화면으로 이동하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun kakaoLogout() {
        UserApiClient.instance.logout { error ->
            if (error != null) {
                Log.e("KAKAO", "로그아웃 실패", error)
            } else {
                Log.i("KAKAO", "로그아웃 성공")
            }

            navigateToLogin()
        }
    }

    private fun kakaoUnlink() {
        val app = requireActivity().application as PaceApplication
        val hasKakaoToken = AuthApiClient.instance.hasToken()
        Log.d("KAKAO", "Before unlink, SDK has token: $hasKakaoToken")

        if (!hasKakaoToken) {
            Log.e("KAKAO", "Cannot unlink because Kakao SDK token is missing.")
            Log.d(
                "KAKAO",
                "Server token state access=${!app.authDataStore.getAccessToken().isNullOrBlank()}, refresh=${!app.authDataStore.getRefreshToken().isNullOrBlank()}, temp=${!app.authDataStore.getTempToken().isNullOrBlank()}"
            )
            app.authDataStore.clearAllData()
            navigateToLogin()
            return
        }

        UserApiClient.instance.accessTokenInfo { _, tokenError ->
            if (tokenError != null) {
                Log.e("KAKAO", "Kakao token validation/refresh failed before unlink.", tokenError)
                app.authDataStore.clearAllData()
                navigateToLogin()
                return@accessTokenInfo
            }

            Log.d("KAKAO", "Kakao token validation succeeded before unlink.")

            UserApiClient.instance.unlink { error ->
                if (error != null) {
                    Log.e("KAKAO", "Unlink failed. Clearing local data anyway.", error)
                } else {
                    Log.i("KAKAO", "Unlink succeeded")
                }

                app.authDataStore.clearAllData()
                Log.d("KAKAO", "Local auth data cleared after logout flow")
                navigateToLogin()
            }
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(requireContext(), SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun getCalendarNameById(calendarId: Long): String {
        if (calendarId == -1L) return "기본 캘린더"

        val projection = arrayOf(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val uri = android.provider.CalendarContract.Calendars.CONTENT_URI
        val selection = "${android.provider.CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        return try {
            val cursor = requireContext().contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(
                        android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                    )
                    it.getString(nameIndex)
                } else {
                    "기본 캘린더"
                }
            } ?: "기본 캘린더"
        } catch (e: SecurityException) {
            "권한 없음"
        } catch (e: Exception) {
            "기본 캘린더"
        }
    }

    private fun showFullScreenPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("전체 화면 알림 권한 설정")
            .setMessage("지금 화면에서도 알림을 즉시 확인하려면 '전체 화면 인텐트 허용' 권한이 필요합니다. 설정 화면으로 이동하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                if (Build.VERSION.SDK_INT >= 34) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = "package:${requireContext().packageName}".toUri()
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
