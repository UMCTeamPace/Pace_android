package com.example.pace.ui.add_schedule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.syncMemberSettingsIfNeeded
import com.example.pace.databinding.ActivityAddScheduleBinding
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddScheduleActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var authDataStore: AuthDataStore

    @javax.inject.Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var binding: ActivityAddScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            syncMemberSettingsIfNeeded(
                authDataStore = authDataStore,
                settingsRepository = settingsRepository,
                source = "AddScheduleActivity",
                force = true
            )
        }

        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                val bundle = Bundle().apply {
                    putBoolean("isEdit", intent.getBooleanExtra("isEdit", false))
                    putLong("SCHEDULE_ID", intent.getLongExtra("SCHEDULE_ID", -1L))
                    putString("SCHEDULE_TYPE", intent.getStringExtra("SCHEDULE_TYPE"))
                    putString("OCCURRENCE_DATE", intent.getStringExtra("OCCURRENCE_DATE"))
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

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "일반 일정" else "경로 일정"
        }.attach()

        if (intent.getBooleanExtra("OPEN_ROUTE_TAB", false)) {
            binding.viewPager.setCurrentItem(1, false)
        }
    }
}