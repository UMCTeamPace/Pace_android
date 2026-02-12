package com.example.pace.ui.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import com.example.pace.databinding.ActivitySyncWithCalendarBinding

class SyncWithCalendarActivity: AppCompatActivity() {
    lateinit var binding: ActivitySyncWithCalendarBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySyncWithCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.syncBackIv.setOnClickListener {
            onBackPressed()
        }

        val calendarSyncMap= mutableMapOf<String, Boolean>()
        // 캘린더 연동 디폴트
        calendarSyncMap["My Phone"] = true
        calendarSyncMap["Google1"] = true
        calendarSyncMap["Google2"] = false
        calendarSyncMap["Samsung"] = true

        // 버튼 클릭 시 변경
        binding.syncMyPhoneIv.setOnClickListener {
            if(calendarSyncMap["My Phone"] == true){
                binding.syncMyPhoneIv.setImageResource(R.drawable.ic_toggle_unselected)
                calendarSyncMap["My Phone"] = false
            }else{
                binding.syncMyPhoneIv.setImageResource(R.drawable.ic_toggle_selected)
                calendarSyncMap["My Phone"] = true
            }
        }
        binding.syncGoogle1Iv.setOnClickListener {
            if(calendarSyncMap["Google1"] == true){
                binding.syncGoogle1Iv.setImageResource(R.drawable.ic_toggle_unselected)
                calendarSyncMap["Google1"] = false
            }else{
                binding.syncGoogle1Iv.setImageResource(R.drawable.ic_toggle_selected)
                calendarSyncMap["Google1"] = true
            }
        }
        binding.syncGoogle2Iv.setOnClickListener {
            if(calendarSyncMap["Google2"] == true){
                binding.syncGoogle2Iv.setImageResource(R.drawable.ic_toggle_unselected)
                calendarSyncMap["Google2"] = false
            }else{
                binding.syncGoogle2Iv.setImageResource(R.drawable.ic_toggle_selected)
                calendarSyncMap["Google2"] = true
            }
        }
        binding.syncSamsungIv.setOnClickListener {
            if(calendarSyncMap["Samsung"] == true){
                binding.syncSamsungIv.setImageResource(R.drawable.ic_toggle_unselected)
                calendarSyncMap["Samsung"] = false
            }else{
                binding.syncSamsungIv.setImageResource(R.drawable.ic_toggle_selected)
                calendarSyncMap["Samsung"] = true
            }
        }
    }
}