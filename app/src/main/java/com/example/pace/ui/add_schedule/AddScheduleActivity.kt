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
import dagger.hilt.android.AndroidEntryPoint // 추가
@AndroidEntryPoint
class AddScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 뷰 바인딩 연결
        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // ViewPager2 어댑터 연결
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment {
                val bundle = Bundle().apply {
                    putBoolean("isEdit", intent.getBooleanExtra("isEdit", false))
                    putLong("SCHEDULE_ID", intent.getLongExtra("SCHEDULE_ID", -1L))
                    putString("SCHEDULE_TYPE", intent.getStringExtra("SCHEDULE_TYPE"))
                    putString("OCCURRENCE_DATE", intent.getStringExtra("OCCURRENCE_DATE"))

                    // [기존] 경로 데이터 전달
                    putString("START_NAME", intent.getStringExtra("START_NAME"))
                    putString("END_NAME", intent.getStringExtra("END_NAME"))
                    putString("ROUTE_DETAIL", intent.getStringExtra("ROUTE_DETAIL"))
                    putInt("EARLY_ARRIVE_TIME", intent.getIntExtra("EARLY_ARRIVE_TIME", 0))
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

        if (intent.getBooleanExtra("OPEN_ROUTE_TAB", false)) {
            binding.viewPager.setCurrentItem(1, false)
        }

    }
}