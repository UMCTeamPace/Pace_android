package com.example.pace.ui.add_schedule

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.ActivityAddScheduleBinding
import com.google.android.material.tabs.TabLayoutMediator

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 뷰 바인딩 연결
        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val selectedDate = intent.getStringExtra("selected_date")
        val mode = intent.getStringExtra("mode")

        // ViewPager2 어댑터 연결
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment {
                val bundle = Bundle().apply {
                    putString("selected_date", selectedDate)
                    putString("mode", mode)
                }
                return when (position) {
                    0 -> GeneralScheduleFragment().apply { arguments = bundle }
                    else -> RouteScheduleFragment().apply { arguments = bundle }
                }
            }
        }
        binding.viewPager.adapter = pagerAdapter

        // TabLayout과 ViewPager2 연결
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "일반 일정" else "경로 일정"
        }.attach()

    }
}