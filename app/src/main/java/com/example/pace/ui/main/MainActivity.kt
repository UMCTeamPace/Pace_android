package com.example.pace.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import com.example.pace.databinding.ActivityMainBinding
import com.example.pace.ui.main.calendar.CalendarFragment
import com.example.pace.ui.main.home.HomeFragment
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.settings.SettingsActivity

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 프래그먼트 이동
        supportFragmentManager.beginTransaction().replace(R.id.main_fcv, HomeFragment()).commit()
        binding.mainBnv.itemIconTintList = null
        binding.mainBnv.setOnItemSelectedListener { item ->
            changeFragment(item)
        }
        // 설정 이동
        binding.mainSettingsIv.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    private fun changeFragment(item: MenuItem): Boolean{
        when(item.itemId){
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(R.id.main_fcv,
                    HomeFragment()).commit()
                return true
            }
            R.id.calendar -> {
                supportFragmentManager.beginTransaction().replace(R.id.main_fcv,
                    CalendarFragment()).commit()
                return true
            }
            R.id.route -> {
                supportFragmentManager.beginTransaction().replace(R.id.main_fcv,
                    RouteFragment()).commit()
                return true
            }
            else -> return false
        }
    }
}