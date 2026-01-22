package com.example.pace.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginStart
import com.example.pace.R
import com.example.pace.databinding.ActivityMainBinding
import com.example.pace.ui.main.calendar.CalendarFragment
import com.example.pace.ui.main.home.HomeFragment
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.settings.SettingsActivity
import com.example.pace.ui.search_box.SearchLocationFragment
import com.google.android.material.internal.ViewUtils.hideKeyboard

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

        // 처음 툴바 보이는거 설정
        binding.mainLogoIv.visibility = android.view.View.VISIBLE
        binding.mainTitleTv.visibility = android.view.View.GONE
        binding.mainSettingsIv.visibility = android.view.View.VISIBLE
        binding.mainEditIv.visibility = android.view.View.VISIBLE
        binding.mainSearchIv.visibility = android.view.View.GONE
        binding.mainBackIv.visibility = android.view.View.GONE
        binding.mainSearchLl.visibility = android.view.View.GONE

        // 설정 이동
        binding.mainSettingsIv.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 검색하다가 뒤로가기 누르면 키보드 없애고, 또 눌렀을 때 routeFragment로 이동
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    if (binding.searchEt.hasFocus()) {
                        binding.searchEt.clearFocus()
                        hideKeyboard()
                    } else {
                        supportFragmentManager.popBackStack()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // 검색창 눌렸을 때 프래그먼트 이동
        binding.searchEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 현재 화면이 SearchLocationFragment라면 새로 띄우지 않음
                // 검색할 때 칩이 초기화되지 않음
                val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fcv)
                if (currentFragment !is SearchLocationFragment) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_fcv, SearchLocationFragment())
                        .addToBackStack("SEARCH_MODE")
                        .commit()

                    binding.mainBnv.visibility = android.view.View.GONE
                    binding.mainBackIv.visibility = android.view.View.VISIBLE
                }

            }
        }

        // 뒤로가기 버튼
        binding.mainBackIv.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 시스템 뒤로가기
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.mainBnv.visibility = android.view.View.VISIBLE

                binding.searchEt.clearFocus()
                binding.searchEt.setText("")
                hideKeyboard()

                binding.mainBackIv.visibility = android.view.View.GONE
            }
        }

        // 텍스트 창에 아이콘 바꾸기
        binding.searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty()) {
                    binding.btnSearch.setImageResource(R.drawable.ic_search)
                } else {
                    binding.btnSearch.setImageResource(R.drawable.ic_close)
                }
            }
        })

        binding.btnSearch.setOnClickListener {
            if (binding.searchEt.text.isNotEmpty()) {
                binding.searchEt.setText("")
            }
        }
    }

    // 텍스트 창 바깥 클릭됐을 때
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val searchBoxRect = android.graphics.Rect()
                binding.mainSearchLl.getGlobalVisibleRect(searchBoxRect)

                // 터치한 위치가 텍스트 창 밖인지 확인
                if (!searchBoxRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun changeFragment(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    HomeFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.VISIBLE
                binding.mainTitleTv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.VISIBLE
                binding.mainEditIv.visibility = android.view.View.VISIBLE
                binding.mainSearchIv.visibility = android.view.View.GONE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.GONE
                return true
            }

            R.id.calendar -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    CalendarFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainTitleTv.visibility = android.view.View.VISIBLE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.mainEditIv.visibility = android.view.View.GONE
                binding.mainSearchIv.visibility = android.view.View.VISIBLE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.GONE
                return true
            }

            R.id.route -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    RouteFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainTitleTv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.mainEditIv.visibility = android.view.View.GONE
                binding.mainSearchIv.visibility = android.view.View.GONE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.VISIBLE
                return true
            }

            else -> return false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEt.windowToken, 0)
    }
}