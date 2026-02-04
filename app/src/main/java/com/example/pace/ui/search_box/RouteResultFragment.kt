package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentRouteResultBinding
import com.example.pace.ui.main.route.RouteFragment

class RouteResultFragment : Fragment(){
    private var _binding: FragmentRouteResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRouteResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTestOverlay.setOnClickListener {
            // 1. 부모 프래그먼트를 RouteFragment로 캐스팅
            val parent = parentFragment as? RouteFragment

            // 2. 오버레이 띄우기 함수 호출
            parent?.showRouteDetailOverlay()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}