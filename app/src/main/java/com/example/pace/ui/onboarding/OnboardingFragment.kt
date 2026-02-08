package com.example.pace.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pace.databinding.FragmentOnboardingBinding
import com.example.pace.ui.main.MainActivity
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.util.Utility
import kotlin.jvm.java

class OnboardingFragment : Fragment() {
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

        val keyHash = Utility.getKeyHash(requireContext())
        Log.d("KeyHash", keyHash)

        // 1. 로고가 위로 솟구치는 애니메이션
        binding.ivLargeLogo.animate()
            .translationY(-500f) // 위쪽으로 이동
            .alpha(0f)           // 서서히 사라짐
            .setDuration(1000)
            .withEndAction {
                // 2. 애니메이션 로고 완전히 제거
                binding.ivLargeLogo.visibility = View.GONE

                // 3. 온보딩 내용 및 하단 버튼 세트 등장
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
            loginWithKakao()
        }
    }

    private fun loginWithKakao() {
        // 1. 로그인 결과 콜백 정의
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
//            if (error != null) {
//                Log.e("KakaoLogin", "카카오 계정으로 로그인 실패", error)
//            } else if (token != null) {
//                sendTokenToServer(token.accessToken)
//            }
            if (error != null) {
                // 로그인 실패 처리
            } else if (token != null) {
                // 로그인 성공!
                // 여기서 바로 MainActivity로 가지 말고, 권한 설정 화면으로 이동합니다.
                (activity as? OnboardingActivity)?.moveToPermissionStep()
            }
        }

        // 2. 카카오톡 설치 여부에 따른 로그인 처리
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(requireContext())) {
            UserApiClient.instance.loginWithKakaoTalk(requireContext()) { token, error ->
                if (error != null) {
                    Log.e("KakaoLogin", "카카오톡으로 로그인 실패", error)

                    // 사용자가 의도적으로 취소한 경우 (예: 뒤로 가기)
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    // 카카오톡 설치는 되어있으나 로그인이 불가능한 경우 웹뷰 시도
                    UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
                } else if (token != null) {
                    sendTokenToServer(token.accessToken)
                }
            }
        } else {
            // 카카오톡이 없으면 바로 웹뷰로 로그인 시도
            UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
        }
    }


    private fun sendTokenToServer(accessToken: String) {
        Log.d("KakaoLogin", "발급받은 액세스 토큰: $accessToken")

        // TODO: Retrofit을 사용하여 서버 API 호출
        // 예: service.postKakaoLogin(accessToken).enqueue(...)

        // 성공 시 MainActivity로 이동
        val intent = Intent(requireContext(), MainActivity::class.java)
        startActivity(intent)

        activity?.finish()
    }

    private fun moveToPermissionScreen() {
        // 만약 PermissionFragment를 새로운 액티비티에서 띄운다면:
        val intent = Intent(requireContext(), PermissionActivity::class.java)
        startActivity(intent)
        activity?.finish() // 로그인 화면 종료
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class OnboardingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4 // 총 4페이지

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingFragment1()
            1 -> OnboardingFragment2()
            2 -> OnboardingFragment3()
            else -> OnboardingFragment4()
        }
    }
}