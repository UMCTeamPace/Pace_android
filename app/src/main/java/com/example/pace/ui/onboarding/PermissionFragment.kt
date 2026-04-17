package com.example.pace.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.databinding.FragmentPermissionBinding
import com.example.pace.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PermissionFragment : Fragment() {

    @Inject
    lateinit var authDataStore: AuthDataStore

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    // 필수 권한 리스트
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    // 권한 요청 결과 처리 런처
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 권한 요청 시도가 한 번이라도 발생했음을 저장
        authDataStore.setPermissionRequested(true)

        if (allPermissionsGranted()) {
            moveToNextStep()
        } else {
            // 거절된 경우 상태에 따라 팝업 노출
            checkPermissionsAndShowDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNext.setOnClickListener {
            if (allPermissionsGranted()) {
                moveToNextStep()
            } else {
                // 권한이 없는 상태에서 버튼 클릭 시 권한 흐름 시작
                handlePermissionFlow()
            }
        }
    }

    /**
     * 권한 부여 여부 확인
     */
    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 권한 상태에 따른 분기 처리
     */
    private fun handlePermissionFlow() {
        val isFirstRequest = !authDataStore.isPermissionRequested()

        if (isFirstRequest) {
            // 1. 앱 설치 후 아예 처음 요청하는 경우
            startPermissionFlow()
        } else {
            // 2. 이미 한 번 이상 거절한 적이 있는 경우
            checkPermissionsAndShowDialog()
        }
    }

    /**
     * Rationale(설명 필요 여부)에 따른 팝업 분기
     */
    private fun checkPermissionsAndShowDialog() {
        val showRationaleLocation = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        val showRationaleCalendar = shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)

        if (showRationaleLocation || showRationaleCalendar) {
            // 시스템 팝업을 한 번 더 띄울 수 있는 상태 (1번 거절 상태)
            showFirstRationaleDialog()
        } else {
            // 시스템 팝업을 더 이상 띄울 수 없는 상태 (2번 거절 상태)
            showSecondSettingDialog()
        }
    }

    private fun startPermissionFlow() {
        val permissions = mutableListOf<String>()
        // 알림 권한 (선택) - Android 13 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 필수 권한 추가
        permissions.addAll(requiredPermissions)

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    /**
     * [2.1] 필수 권한 안내 팝업 (재요청)
     */
    private fun showFirstRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("필수 권한 허용 안내")
            .setMessage("서비스 이용을 위해 위치와 캘린더 권한 허용이 필요합니다.")
            .setPositiveButton("권한 재요청") { _, _ ->
                startPermissionFlow()
            }
            .setNegativeButton("닫기", null)
            .setCancelable(false)
            .show()
    }

    /**
     * [2.2] 설정 이동 안내 팝업 (무한 반복 구간)
     */
    private fun showSecondSettingDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 허용 필요")
            .setMessage("권한을 거절하여 앱 이용이 불가능합니다.\n[설정 > 권한]에서 직접 허용해 주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("닫기", null) // 닫기 누르면 팝업만 사라짐 -> '다음' 누르면 다시 이 함수 호출됨
            .setCancelable(false)
            .show()
    }

    private fun moveToNextStep() {
        val nextActivity = if (!authDataStore.getTempToken().isNullOrBlank()) {
            UserSetupActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(requireContext(), nextActivity))
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}