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
import com.example.pace.databinding.FragmentPermissionBinding
import com.example.pace.ui.main.MainActivity
import kotlin.jvm.java

class PermissionFragment : Fragment() {

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    // 1. 요청할 권한 리스트 정의
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    // 알림 권한 (Android 13 이상 대응)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    // 권한 요청 결과 처리 런처
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 결과가 돌아왔을 때 다시 한 번 체크해서 넘어가거나 모달을 띄움
        if (allPermissionsGranted()) {
            moveToNextStep() // 모든 필수 권한 허용 시 다음 화면(뷰페이저)으로
        } else {
            showFirstRationaleDialog() // 미허용 시 재요청 모달
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

        // 처음 버튼을 눌렀을 때의 동작
        binding.btnNext.setOnClickListener {
            if (allPermissionsGranted()) {
                moveToNextStep() // 이미 권한이 다 있다면 바로 이동
            } else {
                startPermissionFlow() // 권한이 없다면 시스템 팝업 띄우기
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val context = requireContext()

        // 1. 위치 권한 체크 (필수)
        val locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // 2. 캘린더 권한 체크 (필수)
        val calendarGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        // 필수 권한인 위치와 캘린더가 모두 허용되었는지만 반환
        return locationGranted && calendarGranted
    }

    private fun startPermissionFlow() {

        val permissions = mutableListOf<String>()

        // 1. 알림 권한 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 2. 위치 권한
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // 3. 캘린더 권한
        permissions.add(Manifest.permission.READ_CALENDAR)
        permissions.add(Manifest.permission.WRITE_CALENDAR)

        // 배열로 변환하여 요청 (이 순서대로 다이얼로그가 나타남)
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun showFirstRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("필수 권한 허용 안내")
            .setMessage("Pace 서비스 이용을 위해 위치와 캘린더 권한은 필수입니다.")
            .setPositiveButton("권한 재요청") { _, _ ->
                startPermissionFlow() // 다시 시스템 다이얼로그 띄움
            }
            .setNegativeButton("허용 안함") { _, _ ->
                showSecondSettingDialog() // 여기서도 허용 안하면 설정 이동 모달로
            }
            .setCancelable(false)
            .show()
    }

    private fun showSecondSettingDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 허용 필요")
            .setMessage("권한을 거부하시면 앱 이용이 불가능합니다. [설정 > 권한]에서 직접 허용해 주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("닫기", null)
            .setCancelable(false)
            .show()
    }

    private fun moveToNextStep() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.permission_container, AppSettingPagerFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}