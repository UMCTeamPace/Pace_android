package com.example.pace.ui.search_box

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.example.pace.R
import com.example.pace.data.model.response.BusItemList
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.databinding.BottomSheetRouteDetailBinding
import com.example.pace.databinding.ItemRouteDetailArrivalBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteDetailVehicleBinding
import com.example.pace.databinding.ItemRouteDetailWalkBinding
import com.example.pace.ui.RouteCalculator
import kotlin.math.absoluteValue

data class RealtimeParam(
    val type: String, // "SUBWAY" or "BUS"
    val lineName: String,
    val startStation: String,
    val endStation: String,
    val targetLayout: LinearLayout
)

object RouteDetailHelper {
    fun setupData(
        context: Context,
        bottomSheetView: View,
        item: RouteResponse,
        destination: String,
        startName: String,
        fragmentManager: FragmentManager,
        onRoutePointClick: ((Double, Double) -> Unit)? = null
    ): List<RealtimeParam> {
        Log.d("RouteResponse:item.totalTime", item.totalTime.toString())
        val realtimeParams = mutableListOf<RealtimeParam>()
        val binding = BottomSheetRouteDetailBinding.bind(bottomSheetView)

        // 기본 정보
        val departureTime = RouteCalculator.convertUtcToKst(item.departureTime)
        val arrivalTime = RouteCalculator.convertUtcToKst(item.arrivalTime)
        binding.routeDetailDepartureTimeTv.text = departureTime
        binding.routeDetailArrivalTimeTv.text = arrivalTime
        binding.routeDetailTotalTv.text = buildSpannedString {
            append("총 ")
            color(context.resources.getColor(R.color.primary_500)){
                if(item.totalTime / 3600 > 0){
                    val time = item.totalTime % 3600
                    append("${item.totalTime / 3600} 시간 ")
                    if(time / 60 > 0){
                        append("${time / 60}분")
                    }
                }else{
                    append("${item.totalTime / 60}분")
                }

            }
            append(" 소요")
        }

        // 기존 데이터 삭제
        binding.routeDetailBriefLl.removeAllViews()
        binding.routeDetailExpandedLl.removeAllViews()

        var lastArrivalStop: String? = null

        // 데이터 동적 바인딩
        item.routeDetails.forEach { data ->
            Log.d("RouteResponse:data.duration", data.duration.toString())
            val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context))
            val expandedVehicleBinding = ItemRouteDetailVehicleBinding.inflate(LayoutInflater.from(context), binding.routeDetailExpandedLl, false)
            val expandedWalkBinding = ItemRouteDetailWalkBinding.inflate(LayoutInflater.from(context), binding.routeDetailExpandedLl, false)

            when(data.transitDetail){
                // 도보
                null -> {
                    // 일직선 정보
                    if(data.sequence == 1){
                        briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                        briefBinding.itemRouteDetailBriefTv.setPadding((17 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)

                    }else{
                        briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                        briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                    }
                    briefBinding.itemRouteDetailBriefTv.text = if(data.duration / 60 == 0) "1분" else "${data.duration / 60}분"
                    briefBinding.itemRouteDetailBriefTv.setTextColor(context.resources.getColor(R.color.gray_600))

                    val walkTitle = if (data.sequence == 1) {
                        startName
                    } else {
                        "${lastArrivalStop ?: "알 수 없는 정류장"} 하차"
                    }
                    // 상세 정보
                    expandedWalkBinding.itemRouteDetailWalkTv.text = walkTitle
                    expandedWalkBinding.itemRouteDetailWalkTimeTv.text = if(data.duration / 60 == 0) "1분" else "${data.duration / 60}분"
                    expandedWalkBinding.itemRouteDetailWalkDistanceTv.text = "${data.distance}M 이동"
                    if(data.sequence == 1){
                        expandedWalkBinding.itemRouteDetailWalkStartTv.visibility = View.VISIBLE
                        expandedWalkBinding.itemRouteDetailWalkStartTv.text = departureTime
                    }else{
                        expandedWalkBinding.itemRouteDetailWalkStartTv.visibility = View.INVISIBLE
                    }
                    expandedWalkBinding.itemRouteDetailWalkIv.setOnClickListener {
                        onRoutePointClick?.invoke(data.startLat, data.startLng)
                    }
                    binding.routeDetailExpandedLl.addView(expandedWalkBinding.root)
                }
                // 대중교통
                else -> {
                    val transitType = data.transitDetail.transitType
                    var departureStopName = data.transitDetail.departureStop
                    var arrivalStopName = data.transitDetail.arrivalStop
                    val lineName = data.transitDetail.lineName

                    val layoutDrawable = (ContextCompat.getDrawable(context, R.drawable.ic_route_detail))?.mutate() as LayerDrawable
                    val iconColor = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color)?.mutate() as GradientDrawable
                    val bgColor = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable
                    val lineColor = Color.parseColor(data.transitDetail.lineColor)
                    var moreStation = false

                    var isRealtimeAvailable = false
                    try {
                        // 현재 시간 (KST)
                        val nowKst = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))

                        // 서버에서 온 UTC 시간을 KST(+9시간)로 변환
                        val depTimeKst = java.time.LocalDateTime.parse(data.transitDetail.departureTime).plusHours(9)
                        val arrTimeKst = java.time.LocalDateTime.parse(data.transitDetail.arrivalTime).plusHours(9)

                        val startTime = depTimeKst.minusMinutes(15)
                        val endTime = arrTimeKst

                        if (nowKst.isAfter(startTime) && nowKst.isBefore(endTime)) {
                            isRealtimeAvailable = true
                        }

                        Log.d("TimeCheck", "현재시간: $nowKst | 출발: $depTimeKst | 허용구간: $startTime ~ $endTime | 표시여부: $isRealtimeAvailable")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isRealtimeAvailable = true // 시간 파싱 에러 시 화면에 안 나오는 것보단 낫게 방어(true) 처리
                    }

                    if (transitType == "BUS") {
                        val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                        expandedVehicleBinding.itemRouteDetailVehicleTimetableTv.visibility = View.INVISIBLE
                        if (isRealtimeAvailable) {
                            expandedVehicleBinding.itemRouteDetailRealtimeLl.visibility = View.VISIBLE
                            realtimeParams.add(RealtimeParam("BUS", lineName, departureStopName, arrivalStopName, expandedVehicleBinding.itemRouteDetailRealtimeLl))
                        } else {
                            expandedVehicleBinding.itemRouteDetailRealtimeLl.visibility = View.GONE
                        }
                    } else if (transitType == "SUBWAY") {
                        val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                        expandedVehicleBinding.itemRouteDetailVehicleTimetableTv.visibility = View.VISIBLE

                        if (isRealtimeAvailable) {
                            expandedVehicleBinding.itemRouteDetailRealtimeLl.visibility = View.VISIBLE
                            realtimeParams.add(RealtimeParam("SUBWAY", lineName, departureStopName, arrivalStopName, expandedVehicleBinding.itemRouteDetailRealtimeLl))
                        } else {
                            expandedVehicleBinding.itemRouteDetailRealtimeLl.visibility = View.GONE
                        }
                    }

                    if (transitType == "SUBWAY") {
                        if (!arrivalStopName.endsWith("역")) arrivalStopName += "역"
                        if (!departureStopName.endsWith("역")) departureStopName += "역"
                    }

                    lastArrivalStop = arrivalStopName
                    iconColor.setColor(lineColor)
                    bgColor.setColor(lineColor)

                    // 일직선 정보
                    briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                    briefBinding.itemRouteDetailBriefTv.text = if(data.duration / 60 == 0) "1분" else "${data.duration / 60}분"

                    // 상세 정보
                    expandedVehicleBinding.itemRouteDetailVehicleIv.setImageDrawable(layoutDrawable)
                    expandedVehicleBinding.itemRouteDetailVehicleView.setBackgroundColor(lineColor)
                    expandedVehicleBinding.itemRouteDetailVehicleIv.setOnClickListener {
                        onRoutePointClick?.invoke(data.startLat, data.startLng)
                    }

                    if(data.sequence == 1){
                        expandedVehicleBinding.itemRouteDetailVehicleStartTv.visibility = View.VISIBLE
                        expandedVehicleBinding.itemRouteDetailVehicleStartTv.text = departureTime
                    }else{
                        expandedVehicleBinding.itemRouteDetailVehicleStartTv.visibility = View.INVISIBLE
                    }

                    if (data.transitDetail.transitType == "SUBWAY" && !departureStopName.endsWith("역")) {
                        departureStopName += "역"
                    }
                    expandedVehicleBinding.itemRouteDetailVehicleTv.text = "${departureStopName} 승차"
                    expandedVehicleBinding.itemRouteDetailVehicleTimeTv.text = RouteCalculator.convertUtcToKst(data.transitDetail.departureTime)
                    expandedVehicleBinding.itemRouteDetailVehicleTimeTv.setTextColor(Color.parseColor(data.transitDetail.lineColor))
                    expandedVehicleBinding.itemRouteDetailVehicleLineTv.text = data.transitDetail.shortName
                    expandedVehicleBinding.itemRouteDetailVehicleLineTv.backgroundTintList = ColorStateList.valueOf(Color.parseColor(data.transitDetail.lineColor))

                    if(data.transitDetail.stationPath.isNullOrEmpty()){
                        expandedVehicleBinding.itemRouteDetailVehicleDirectionTv.text = when(data.transitType){
                            "BUS" -> {
                                (data.transitDetail.headsign + "행")
                            }
                            else -> {
                                "방면 정보 없음"
                            }
                        }

                    }else{
                        expandedVehicleBinding.itemRouteDetailVehicleDirectionTv.text = data.transitDetail.stationPath[0] + "행 방면"
                    }


                    expandedVehicleBinding.itemRouteDetailVehicleStationsTv.text = "${data.transitDetail.stopCount}개 정류장 이동"
                    expandedVehicleBinding.itemRouteDetailVehicleStationsTimeTv.text = if(data.duration / 60 == 0) "1분" else "${data.duration / 60}분"

                    // 중간 정류장 데이터 X -> 꺽쇠 안 보이게
                    if(data.transitDetail.stationPath.isNullOrEmpty()){
                        expandedVehicleBinding.itemRouteDetailVehicleArrowDownIv.visibility = View.GONE
                        expandedVehicleBinding.itemRouteDetailVehicleRv.visibility = View.GONE
                        expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setPadding(0, 0, 0, (17 * context.resources.displayMetrics.density).toInt())
                    }else{
                        expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setOnClickListener {
                            if(moreStation){
                                expandedVehicleBinding.itemRouteDetailVehicleArrowDownIv.setImageResource(R.drawable.ic_route_detail_arrow_down)
                                expandedVehicleBinding.itemRouteDetailVehicleRv.visibility = View.GONE
                                expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setPadding(0, 0, 0, (17 * context.resources.displayMetrics.density).toInt())
                                moreStation = false
                            }else{
                                expandedVehicleBinding.itemRouteDetailVehicleArrowDownIv.setImageResource(R.drawable.ic_route_detail_arrow_up)
                                expandedVehicleBinding.itemRouteDetailVehicleRv.visibility = View.VISIBLE
                                expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setPadding(0)
                                moreStation = true

                                // 정류장 리사이클러뷰
                                val adapter = RouteDetailStationAdapter(context, data.transitDetail.stationPath, lineColor)
                                expandedVehicleBinding.itemRouteDetailVehicleRv.adapter = adapter
                            }
                        }
                    }
                    binding.routeDetailExpandedLl.addView(expandedVehicleBinding.root)

                    // 지하철 시간표 바텀시트
                    expandedVehicleBinding.itemRouteDetailVehicleTimetableTv.setOnClickListener {
                        val bottomSheet = TimetableBottomSheet(departureStopName, lineName, data.transitDetail.upNext, data.transitDetail.downNext)
                        bottomSheet.show(fragmentManager, bottomSheet.tag)
                    }
                    Log.d("TRANSIT:UpNext", data.transitDetail.upNext.toString())
                    Log.d("TRANSIT:DownNext", data.transitDetail.downNext.toString())
                }
            }
            // 일직선 데이터 추가
            val weight = RouteCalculator.calculateWeight(data.duration)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            binding.routeDetailBriefLl.addView(briefBinding.root, params)

        }

        val arrivalBinding = ItemRouteDetailArrivalBinding.inflate(LayoutInflater.from(context))
        arrivalBinding.itemRouteDetailArrivalTimeTv.text = arrivalTime
        arrivalBinding.itemRouteDetailArrivalTv.text = destination
        item.routeDetails.lastOrNull()?.let { lastDetail ->
            arrivalBinding.itemRouteDetailArrivalIv.setOnClickListener {
                onRoutePointClick?.invoke(lastDetail.endLat, lastDetail.endLng)
            }
        }
        binding.routeDetailExpandedLl.addView(arrivalBinding.root)

        return realtimeParams
    }

    fun updateSubwayUI(context: Context, targetLayout: LinearLayout, resultList: List<SubwayTransitResult>?) {
        // UI 그리기 충돌을 막기 위해 post 블록 사용
        targetLayout.post {
            targetLayout.visibility = View.VISIBLE // 뷰 강제 노출
            targetLayout.removeAllViews()

            // 1. 데이터가 비어 있을 때 (운행 종료 등)
            if (resultList.isNullOrEmpty()) {
                val emptyText = TextView(context).apply {
                    text = "현재 도착 예정인 열차가 없습니다."
                    setTextColor(Color.parseColor("#757575")) // 회색
                    textSize = 12f

                    val dp10 = (10 * context.resources.displayMetrics.density).toInt()
                    //setPadding(dp10, dp10, dp10, dp10)

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                targetLayout.addView(emptyText)
                return@post
            }

            // 2. 데이터가 있을 때 정상적으로 그리기
            resultList.forEachIndexed { index, result ->
                val childLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if(index > 0){
                            setMargins(0, (12 * context.resources.displayMetrics.density).toInt(), 0, 0)
                        }
                    }
                }
                val timeText = TextView(context).apply {
                    text = if(result.barvlDt == "0" && result.arvlMsg2[0] != '[') result.arvlMsg2
                    else if (result.barvlDt == "0") result.arvlMsg2.split("(").last().dropLast(1)
                    else (result.barvlDt.toInt() / 60).toString() + "분"
                    typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                    setTextColor(ContextCompat.getColor(context, R.color.semantic_error))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val leftStationText = TextView(context).apply {
                    text = "${result.beforeSubwayCount.absoluteValue}정류장"
                    typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                    }
                }
                val directionText = TextView(context).apply {
                    text = if(result.updnLine == "내선" || result.updnLine == "외선") result.updnLine + "순환행" else result.updnLine
                    typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                    }
                }
                childLayout.addView(timeText)
                childLayout.addView(leftStationText)
                childLayout.addView(directionText)
                targetLayout.addView(childLayout)
            }
        }
    }

    fun updateBusUI(context: Context, targetLayout: LinearLayout, resultList: BusItemList?) {
        // UI 그리기 충돌을 막기 위해 post 블록 사용
        targetLayout.post {
            targetLayout.visibility = View.VISIBLE // 뷰 강제 노출
            targetLayout.removeAllViews()

            // 1. 데이터가 비어있을 때 (운행 종료 등)
            if (resultList == null) {
                val emptyText = TextView(context).apply {
                    text = "현재 도착 예정인 버스가 없습니다."
                    setTextColor(Color.parseColor("#757575")) // 회색
                    textSize = 12f

                    val dp10 = (10 * context.resources.displayMetrics.density).toInt()
                    setPadding(dp10, dp10, dp10, dp10)

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                targetLayout.addView(emptyText)
                return@post
            }

            // 2. 데이터가 있을 때 정상적으로 그리기
            // 첫 번째 데이터
            val childLayout1 = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val timeText1 = TextView(context).apply {
                text = resultList.arrmsg1?.split("[")?.first()
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                setTextColor(ContextCompat.getColor(context, R.color.semantic_error))
                textSize = 11f
            }
            val leftStationText1 = TextView(context).apply {
                text = resultList.arrmsg1?.split("[")?.last()?.dropLast(1)
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                }
            }
            val congestionText1 = when(resultList.rerdie_Div1){
                "2" -> {
                    TextView(context).apply {
                        text = resultList.reride_Num1
                        typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                        textSize = 10f
                        background = ContextCompat.getDrawable(context, R.drawable.bg_subway_realtime_seats)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                            setPadding((3 * context.resources.displayMetrics.density).toInt(), (1 * context.resources.displayMetrics.density).toInt(), (2 * context.resources.displayMetrics.density).toInt(), (1 * context.resources.displayMetrics.density).toInt())
                        }
                    }
                }
                "4" -> {
                    when(resultList.reride_Num1){
                        "3" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.low_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        "4" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.middle_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        "5" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.high_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        else -> {
                            TextView(context).apply {
                                text = "데이터 없음"
                                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                                textSize = 11f
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                    }

                }
                else -> {
                    TextView(context).apply {
                        text = "데이터 없음"
                        typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                        textSize = 11f
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                        }
                    }
                }
            }
            // 두 번째 데이터
            val childLayout2 = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, (12 * context.resources.displayMetrics.density).toInt(), 0, 0)
                }
            }
            val timeText2 = TextView(context).apply {
                text = resultList.arrmsg2?.split("[")?.first()
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                setTextColor(ContextCompat.getColor(context, R.color.semantic_error))
                textSize = 11f
            }
            val leftStationText2 = TextView(context).apply {
                text = resultList.arrmsg2?.split("[")?.last()?.dropLast(1)
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                }
            }
            val congestionText2 = when(resultList.rerdie_Div2){
                "2" -> {
                    TextView(context).apply {
                        text = resultList.reride_Num2
                        typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                        textSize = 10f
                        background = ContextCompat.getDrawable(context, R.drawable.bg_subway_realtime_seats)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                            setPadding((3 * context.resources.displayMetrics.density).toInt(), (1 * context.resources.displayMetrics.density).toInt(), (2 * context.resources.displayMetrics.density).toInt(), (1 * context.resources.displayMetrics.density).toInt())
                        }
                    }
                }
                "4" -> {
                    when(resultList.reride_Num2){
                        "3" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.low_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        "4" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.middle_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        "5" -> {
                            ImageView(context).apply {
                                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.high_congestion))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                        else -> {
                            TextView(context).apply {
                                text = "데이터 없음"
                                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                                textSize = 11f
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                                }
                            }
                        }
                    }

                }
                else -> {
                    TextView(context).apply {
                        text = "데이터 없음"
                        typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                        textSize = 11f
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins((6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
                        }
                    }
                }
            }

            childLayout1.addView(timeText1)
            childLayout1.addView(leftStationText1)
            childLayout1.addView(congestionText1)
            targetLayout.addView(childLayout1)

            childLayout2.addView(timeText2)
            childLayout2.addView(leftStationText2)
            childLayout2.addView(congestionText2)
            targetLayout.addView(childLayout2)
        }
    }
}
