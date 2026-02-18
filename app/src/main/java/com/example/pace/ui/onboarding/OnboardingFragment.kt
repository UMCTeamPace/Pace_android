package com.example.pace.ui.onboarding

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pace.databinding.FragmentOnboardingBinding
import com.example.pace.ui.NetworkErrorDialog
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

//        // [테스트용] 앱 실행 시마다 카카오 세션 종료
//        UserApiClient.instance.logout { error ->
//            if (error != null) Log.e("Kakao", "로그아웃 실패")
//            else Log.d("Kakao", "로그아웃 성공 - 이제 온보딩 화면이 유지됩니다.")
//        }

        val keyHash = Utility.getKeyHash(requireContext())
        Log.d("KeyHash", keyHash)

        // 1. 로고가 위로 솟구치는 애니메이션
        binding.ivLargeLogo.animate()
            .translationY(-500f)
            .alpha(0f)
            .setDuration(1000)
            .withEndAction {
                // 2. 애니메이션 로고 완전히 제거
                binding.ivLargeLogo.visibility = View.GONE

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
                // 💡 여기 수정!
                navigateToNextStep()
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(requireContext())) {
            UserApiClient.instance.loginWithKakaoTalk(requireContext()) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) return@loginWithKakaoTalk
                    UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
                } else if (token != null) {
                    // 💡 여기도 수정!
                    navigateToNextStep()
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
        }
    }

    // 함수 이름을 변경하고 로직은 유지합니다.
    private fun navigateToNextStep() {
        val app = (requireActivity().application as com.example.pace.PaceApplication)

        if (app.authDataStore.isOnboardingComplete()) {
            // [재로그인] 설정 데이터가 남아있으므로 바로 메인행
            Log.d("LOGIN_FLOW", "기존 유저 확인: 메인으로 이동")
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
        } else {
            // [신규/재가입] 데이터가 없으므로 권한 설정부터 시작
            Log.d("LOGIN_FLOW", "신규/재가입 유저: 권한 설정으로 이동")
            val intent = Intent(requireContext(), PermissionActivity::class.java)
            startActivity(intent)
        }
        activity?.finish()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
