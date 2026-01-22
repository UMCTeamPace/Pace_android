package com.example.pace.ui.add_schedule

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.ActivityAddScheduleBinding

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. 뷰 바인딩 연결
        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. ViewPager2 어댑터 연결
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> GeneralScheduleFragment() // 일반 일정
                    else -> RouteScheduleFragment() // 경로 일정
                }
            }
        }
        binding.viewPager.adapter = pagerAdapter

        // 3. 탭 클릭 리스너 (이미지의 탭 셀렉터 버튼들)
        binding.btnTabGeneral.setOnClickListener {
            binding.viewPager.currentItem = 0
        }
        binding.btnTabRoute.setOnClickListener {
            binding.viewPager.currentItem = 1
        }

        // 4. 페이지가 변경될 때 탭 디자인(글자색, 배경) 업데이트
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabUI(position)
            }
        })
    }

    private fun updateTabUI(position: Int) {
        if (position == 0) {
            binding.btnTabGeneral.setTextColor(Color.BLACK)
            binding.btnTabGeneral.setTypeface(null, Typeface.BOLD)
            // 여기에 '일반 일정' 버튼 배경 변경 코드 추가

            binding.btnTabRoute.setTextColor(Color.GRAY)
            binding.btnTabRoute.setTypeface(null, Typeface.NORMAL)
        } else {
            binding.btnTabRoute.setTextColor(Color.BLACK)
            binding.btnTabRoute.setTypeface(null, Typeface.BOLD)
            // 여기에 '경로 일정' 버튼 배경 변경 코드 추가

            binding.btnTabGeneral.setTextColor(Color.GRAY)
            binding.btnTabGeneral.setTypeface(null, Typeface.NORMAL)
        }
    }
}