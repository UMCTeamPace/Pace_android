package com.example.pace.ui.alarm

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.AlertActivity

class AlarmTestActivity : AppCompatActivity() {

    private data class TestCase(
        val title: String,
        val minutesLeft: Int,
        val weatherStatus: String,
        val weatherDesc: String,
        val temp: Double
    )

    private val testCases = listOf(
        TestCase("60분 전 - 샤워 준비", 60, "SUNNY", "맑음", 23.0),
        TestCase("35분 전 - 외출 준비", 35, "SUNNY", "맑음", 23.0),
        TestCase("15분 전 - 맑음", 15, "SUNNY", "맑음", 23.0),
        TestCase("15분 전 - 비", 15, "RAIN", "비", 18.0),
        TestCase("15분 전 - 눈", 15, "SNOW", "눈", -2.0),
        TestCase("15분 전 - 흐림", 15, "CLOUDY", "흐림", 19.0),
        TestCase("15분 전 - 폭염", 15, "HEAT_WAVE", "맑음", 35.0),
        TestCase("15분 전 - 한파", 15, "COLD_WAVE", "맑음", -12.0),
        TestCase("0분 전 - 지금 출발", 0, "SUNNY", "맑음", 23.0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "알람 테스트"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        container.addView(TextView(this).apply {
            text = "누르면 해당 조건의 알람 화면으로 바로 이동합니다."
            textSize = 18f
            setPadding(0, 0, 0, 24)
        })

        testCases.forEachIndexed { index, testCase ->
            container.addView(Button(this).apply {
                text = testCase.title
                isAllCaps = false
                setOnClickListener { openTestAlarm(testCase) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            })
        }

        setContentView(ScrollView(this).apply {
            addView(container)
        })
    }

    private fun openTestAlarm(testCase: TestCase) {
        val intent = Intent(this, AlertActivity::class.java).apply {
            putExtra("MINUTES_LEFT", testCase.minutesLeft)
            putExtra("SCHEDULE_ID", -1L)
            putExtra("ALARM_TYPE", "TEST")
            putExtra("TEST_WEATHER_STATUS", testCase.weatherStatus)
            putExtra("TEST_WEATHER_DESC", testCase.weatherDesc)
            putExtra("TEST_LOCATION", "테스트")
            putExtra("TEST_TEMP", testCase.temp)
        }
        startActivity(intent)
    }
}
