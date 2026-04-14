package com.example.pace.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pace.PaceApplication
import com.example.pace.data.model.request.KakaoLoginRequest
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.model.response.toEntity
import com.example.pace.data.repository.repository.AuthControllerRepository
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.databinding.FragmentOnboardingBinding
import com.example.pace.ui.NetworkErrorDialog
import com.example.pace.ui.main.MainActivity
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.util.Utility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingFragment : Fragment() {
    @Inject
    lateinit var authControllerRepository: AuthControllerRepository
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 해시키 확인 및 로그아웃 로직은 그대로 유지...
        val keyHash = Utility.getKeyHash(requireContext())
        Log.d("KeyHash", keyHash)

        // DP를 PX로 변환하는 값 계산
        val density = resources.displayMetrics.density
        val startY = 340 * density // 시작점 (Top 340dp)
        val endY = 280 * density   // 도착점 (Top 280dp)

        // 1. 초기 위치 강제 설정 (XML에서 어긋나 있을 경우를 대비)
        binding.ivLargeLogo.y = startY
        binding.ivLargeLogo.alpha = 1f

        // 2. 애니메이션 실행
        binding.ivLargeLogo.animate()
            .y(endY)               // 절대 좌표 280dp 지점으로 이동
            .alpha(0f)             // 위로 올라가면서 서서히 사라짐
            .setDuration(1000)     // 1초 동안 진행
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()) // 부드러운 가감속
            .withEndAction {
                setupOnboarding()
            }
            .start()
    }

    private fun setupOnboarding() {
        // 어댑터 연결

        binding.tvDescription.animate().alpha(0f).setDuration(300).withEndAction {
            binding.tvDescription.visibility = View.GONE
        }.start()

        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter

        // 인디케이터 연결
        binding.dotsIndicator.setViewPager2(binding.viewPager)

        binding.tvDescription.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                // 완전히 사라지면 자리를 차지하지 않게 GONE 처리
                binding.tvDescription.visibility = View.GONE
            }
            .start()

        binding.viewPager.animate().alpha(1f).setDuration(500).start()
        binding.dotsIndicator.animate().alpha(1f).setDuration(500).start()
        binding.btnKakaoLogin.animate().alpha(1f).setDuration(500).start()

        binding.btnKakaoLogin.setOnClickListener {
            // 네트워크 연결 시에만 로그인 진행
            val connectivityManager = getSystemService(requireContext(), ConnectivityManager:: class.java)
            val activeNetwork = connectivityManager?.activeNetwork
            val isActiveNetwork = connectivityManager?.getNetworkCapabilities(activeNetwork)
            if(isActiveNetwork?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true){
                loginWithKakao()
            }else{
                // 미연결 시 다이얼로그 띄우기
                val dialog = NetworkErrorDialog(requireActivity()) {
                    reload()
                }
                dialog.show()
            }
        }
    }

    fun reload(){
        parentFragmentManager.beginTransaction().detach(this).commit()
        parentFragmentManager.beginTransaction().attach(this).commit()
    }

    private fun loginWithKakao() {
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e("KakaoLogin", "로그인 실패", error)
            } else if (token != null) {
                Log.d("KakaoLogin", "kakaoAccessToken=${token.accessToken}")
                Log.d("KakaoLogin", "SDK hasToken after login=${AuthApiClient.instance.hasToken()}")
                handleKakaoToken(token.accessToken)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(requireContext())) {
            UserApiClient.instance.loginWithKakaoTalk(requireContext()) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) return@loginWithKakaoTalk
                    UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
                } else if (token != null) {
                    Log.d("KakaoLogin", "kakaoAccessToken=${token.accessToken}")
                    Log.d("KakaoLogin", "SDK hasToken after login=${AuthApiClient.instance.hasToken()}")
                    handleKakaoToken(token.accessToken)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
        }
    }

    private fun handleKakaoToken(kakaoAccessToken: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (
                val response = authControllerRepository.kakaoLogin(
                    kakaoQueryToken = kakaoAccessToken,
                    request = KakaoLoginRequest(kakaoAccessToken)
                )
            ) {
                is DefaultResponse.Success -> {
                    val result = response.data
                    if (result == null) {
                        Log.e("KakaoLogin", "서버 로그인 응답 result가 비어 있습니다.")
                        return@launch
                    }

                    val app = requireActivity().application as PaceApplication
                    when {
                        !result.tempToken.isNullOrBlank() -> {
                            Log.d("LOGIN_FLOW", "임시 토큰 발급 완료: 온보딩으로 이동합니다.")
                            app.authDataStore.setOnboardingComplete(false)
                            val nextIntent = if (hasRequiredPermissions()) {
                                Intent(requireContext(), UserSetupActivity::class.java)
                            } else {
                                Intent(requireContext(), PermissionActivity::class.java)
                            }
                            startActivity(nextIntent)
                            activity?.finish()
                        }
                        !result.accessToken.isNullOrBlank() && !result.refreshToken.isNullOrBlank() -> {
                            Log.d("LOGIN_FLOW", "정식 토큰 발급 완료: 메인으로 이동합니다.")
                            fetchAndStoreMemberSettings(result.accessToken)
                            app.authDataStore.setOnboardingComplete(true)
                            val nextIntent = if (hasRequiredPermissions()) {
                                Intent(requireContext(), MainActivity::class.java)
                            } else {
                                Intent(requireContext(), PermissionActivity::class.java)
                            }
                            startActivity(nextIntent)
                            activity?.finish()
                        }
                        else -> {
                            Log.e("KakaoLogin", "서버 로그인 응답에 사용할 수 있는 토큰이 없습니다.")
                        }
                    }
                }
                is DefaultResponse.Failure -> {
                    Log.e("KakaoLogin", "서버 로그인 실패: ${response.code}, ${response.message}")
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val context = requireContext()
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val readCalendarGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeCalendarGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        return locationGranted && readCalendarGranted && writeCalendarGranted
    }

    private suspend fun fetchAndStoreMemberSettings(accessToken: String) {
        val bearerToken = if (accessToken.startsWith("Bearer ")) accessToken else "Bearer $accessToken"
        val response = settingsRepository.getMemberSettings(bearerToken)
        if (response.isSuccess && response.result != null) {
            val existing = settingsRepository.getUserSettings().firstOrNull()
            settingsRepository.updateSettingsLocally(response.result.toEntity(existing))
            Log.d("SETTINGS_BOOTSTRAP", "기존 회원 설정을 로컬 DB에 저장했습니다.")
        } else {
            Log.e("SETTINGS_BOOTSTRAP", "설정 조회 실패: ${response.code}, ${response.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
