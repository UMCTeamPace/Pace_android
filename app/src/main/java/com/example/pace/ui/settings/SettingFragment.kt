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

class SettingFragment: Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private var selectedEarlyTime: String = "10분"

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

        binding.settingsRouteMinuteTv.text = selectedEarlyTime

        setFragmentResultListener("earlyDepartureKey") { _, bundle ->
            val resultText = bundle.getString("selectedMinutes") ?: "10분"
            selectedEarlyTime = resultText

            _binding?.let {
                it.settingsRouteMinuteTv.text = resultText
            }
        }

        binding.settingsRouteLl.setOnClickListener {
            title?.text = "미리 도착"

            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingEarlyarrivedFragment())
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.addOnBackStackChangedListener {
            if (parentFragmentManager.backStackEntryCount == 0) {
                val title = activity?.findViewById<TextView>(R.id.settings_tv)
                title?.text = "설정"
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}