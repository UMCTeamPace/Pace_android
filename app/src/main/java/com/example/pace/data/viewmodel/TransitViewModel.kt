package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.BuildConfig
import com.example.pace.data.api.BusService
import com.example.pace.data.api.RetrofitClient
import com.example.pace.data.api.SubwayService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.BusItemList
import com.example.pace.data.model.response.StationTimetableItem
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.module.BusRetrofit
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransitViewModel @Inject constructor(
    private val authStore: AuthDataStore,
    private val subwayService: SubwayService,
    private val busService: BusService,
) : ViewModel() {
    // 실시간 지하철
    private val _subwayResult = MutableStateFlow<List<SubwayTransitResult>>(emptyList())
    val subwayResult = _subwayResult.asStateFlow()

    // 지하철 시간표
    private val _firstUpSubway = MutableStateFlow<List<StationTimetableItem?>>(emptyList())
    val firstUpSubway = _firstUpSubway.asStateFlow()
    private val _firstDownSubway = MutableStateFlow<List<StationTimetableItem?>>(emptyList())
    val firstDownSubway = _firstDownSubway.asStateFlow()
    private val _lastUpSubway = MutableStateFlow<List<StationTimetableItem?>>(emptyList())
    val lastUpSubway = _lastUpSubway.asStateFlow()
    private val _lastDownSubway = MutableStateFlow<List<StationTimetableItem?>>(emptyList())
    val lastDownSubway = _lastDownSubway.asStateFlow()
    // 지하철 종점역들
    private val _endStations = MutableStateFlow<List<String>>(emptyList())
    val endStations = _endStations.asStateFlow()

    // 실시간 버스 정보
    private val _busResult = MutableStateFlow<BusItemList?>(null)
    val busResult = _busResult.asStateFlow()

    // 실시간 지하철 도착 정보
    suspend fun fetchRealTimeSubwayArrivals(lineName: String, startStationName: String, endStationName: String): List<SubwayTransitResult>? {
        val token = authStore.getAccessToken()
        if(token.isNullOrEmpty()) return null

        val cleanStartStation = startStationName.removeSuffix("역")
        val cleanEndStation = endStationName.removeSuffix("역")

        Log.d("TransitApi", "지하철 요청 파라미터 -> 노선: $lineName, 출발: $cleanStartStation, 도착: $cleanEndStation")


        return try {
            val response = subwayService.getRealTimeSubwayArrivals(cleanStartStation, cleanEndStation, lineName)
            if (response.isSuccessful) {
                Log.d("TRANSIT_SUCCESS", response.body().toString())
                response.body()?.returnToList(Gson())
            } else {
                Log.e("TRANSIT_ERROR", "${response.message()}: ${response.errorBody()}")
                null
            }
        } catch (e: Exception) {
            Log.e("TRANSIT_ERROR", "${e.cause}, ${e.message}")
            null
        }
    }

    // 지하철 시간표
    fun getSubwayFirstAndLast(station: String, lineName: String, dailyType: String, upDown: String){
        viewModelScope.launch {
            try {
                // 지하철역 아이디 검색
                val idResponse = RetrofitClient.subwayTimetableService.getSubwayStationId(BuildConfig.PUBLIC_API_KEY, "1", "30", "json", station)
                val idItems = idResponse.values.firstOrNull()?.body?.items?.item
                var stationId = ""

                // 호선명 패러미터에 맞게 변환
                val mLineName = when (lineName) {
                    "인천 1호선", "인천 2호선" -> {
                        lineName.replace(" ","")
                    }
                    "경의중앙선", "경춘선", "우이신설선" -> {
                        lineName.dropLast(1)
                    }
                    "수인.분당선" -> "수인분당"
                    "공항철도" -> "공항"
                    else -> {
                        lineName
                    }
                }

                idItems?.forEach {
                    // () 안 내용 및 공백 제거
                    if(station == it.stationName.split("(").first() && mLineName == it.routeName){
                        stationId = it.stationId
                    }
                }

                // 지하철 시간표 검색
                val formatter = DateTimeFormatter.ofPattern("HHmmss")
                val first = mutableListOf<StationTimetableItem?>()
                val last = mutableListOf<StationTimetableItem?>()

                val timetableResponse =
                    RetrofitClient.subwayTimetableService.getSubwayTimetable(
                        BuildConfig.PUBLIC_API_KEY,
                        "1",
                        "240",
                        "json",
                        stationId,
                        dailyType,
                        upDown
                    )
                val timetableItems = timetableResponse.values.firstOrNull()?.body?.items?.item
                if (!timetableItems.isNullOrEmpty()) {
                    // 종착역 별로 첫차 및 막차 나누기
                    val itemPair = mutableMapOf<String, Pair<List<StationTimetableItem>, List<StationTimetableItem>>?>()
                    val groupedItems = timetableItems.groupBy { it.endStationId }
                    for (endStation in groupedItems.keys) {
                        if(endStation == null || endStation == stationId){
                            continue
                        }
                        // Map<종착역, Pair<막차, 첫차>>
                        itemPair[endStation] = groupedItems[endStation]?.partition {
                            if(it.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter)
                                    .isBefore(LocalTime.parse("05:00:00"))
                                        || LocalTime.parse(it.arrTime, formatter)
                                    .isAfter(LocalTime.parse("21:00:00"))
                            }else{
                                LocalTime.parse(it.depTime, formatter)
                                    .isBefore(LocalTime.parse("05:00:00"))
                                        || LocalTime.parse(it.depTime, formatter)
                                    .isAfter(LocalTime.parse("21:00:00"))
                            }

                        }

                        // 막차(01시 기준으로 시간 나누기)
                        val lastDepTimes = itemPair[endStation]?.first?.map {
                            if(it.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter)
                            }else{
                                LocalTime.parse(it.depTime, formatter)
                            }
                        }
                        var filteredLastDepTimes = lastDepTimes
                        if(lastDepTimes?.find{
                            it.isBefore(LocalTime.parse("01:00:00"))
                        } != null){
                            filteredLastDepTimes = lastDepTimes.filter{
                                it.isBefore(LocalTime.parse("01:00:00"))
                            }
                        }
                        val latest = filteredLastDepTimes?.stream()?.max(LocalTime::compareTo)?.orElse(null)
                        last.add(itemPair[endStation]?.first?.find {
                            if(it.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter) == latest
                            }else{
                                LocalTime.parse(it.depTime, formatter) == latest
                            }
                        })

                        // 첫차
                        val firstDepTimes = itemPair[endStation]?.second?.map {
                            if(it.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter)
                            }else{
                                LocalTime.parse(it.depTime, formatter)
                            }
                        }
                        val earliest = firstDepTimes?.stream()?.min(LocalTime::compareTo)?.orElse(null)
                        first.add(itemPair[endStation]?.second?.find {
                            if(it.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter) == earliest
                            }else{
                                LocalTime.parse(it.depTime, formatter) == earliest
                            }
                        })
                    }

                    // 첫차 및 막차 시간 순으로 정렬
                    val sortedFirst = first.filterNotNull().sortedBy{
                        if(it.depTime == "0"){
                            LocalTime.parse(it.arrTime, formatter)
                        }else{
                            LocalTime.parse(it.depTime, formatter)
                        }
                    }
                    val sortedLast = last.filterNotNull()
                        .sortedWith(
                        compareBy<StationTimetableItem?>{
                            if(it?.depTime == "0"){
                                !LocalTime.parse(it.arrTime, formatter).isAfter(LocalTime.parse("21:00:00"))
                            }else{
                                !LocalTime.parse(it?.depTime, formatter).isAfter(LocalTime.parse("21:00:00"))
                            }
                        }.thenBy {
                            if(it?.depTime == "0"){
                                LocalTime.parse(it.arrTime, formatter)
                            }else{
                                LocalTime.parse(it?.depTime, formatter)
                            }
                        }
                    )

                    // todo: 종점 역 저장 변수 사용하기
                    _endStations.value = timetableItems.mapNotNull {
                        it.endSubwayStationName
                    }.distinct()
                    // 상하행 여부에 따라 맞게 저장
                    when(upDown){
                        "U" -> {
                            _firstUpSubway.value = sortedFirst
                            _lastUpSubway.value = sortedLast
                            Log.d("TRANSIT_SUCCESS: first/up", _firstUpSubway.value.toString())
                            Log.d("TRANSIT_SUCCESS: last/up", _firstUpSubway.value.toString())
                        }
                        "D" -> {
                            _firstDownSubway.value = sortedFirst
                            _lastDownSubway.value = sortedLast
                            Log.d("TRANSIT_SUCCESS: first/down", _firstDownSubway.value.toString())
                            Log.d("TRANSIT_SUCCESS: last/down", _lastDownSubway.value.toString())
                        }
                    }
                } else {
                    Log.e("TRANSIT_ERROR", "Item is null")
                }
            }catch (e: HttpException){
                val errorJson = e.response()?.errorBody()?.string()
                Log.e("TRANSIT_ERROR", errorJson!!)
            }
        }
    }

    // 실시간 버스 도착 정보
    suspend fun fetchRealTimeBusArrivals(lineName: String, startStation: String, endStation: String): BusItemList? {
        val token = authStore.getAccessToken()
        if(token.isNullOrEmpty()) return null

        Log.d("TransitApi", "버스 요청 파라미터 -> 노선: $lineName, 출발: $startStation, 도착: $endStation")

        return try {
            val parameters = busService.getParameters(lineName, startStation, endStation)
            if (parameters.result != null) {
                val routeId = parameters.result.routeId
                val ord = parameters.result.sequence.toString()
                val stationId = parameters.result.nodeId

                val response = RetrofitClient.busService.getRealTimeBusArrivals(BuildConfig.PUBLIC_API_KEY, stationId, routeId, ord)
                response.body()?.body?.itemList
            } else {
                Log.e("TRANSIT_ERROR", "Result is null")
                null
            }
        } catch (e:Exception) {
            Log.e("TRANSIT_ERROR", e.message.toString())
            null
        }
    }
}