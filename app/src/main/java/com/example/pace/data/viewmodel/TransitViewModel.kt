package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.api.RetrofitClient
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.SubwayTimeTableResponse
import com.example.pace.data.model.response.SubwayTransitResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransitViewModel @Inject constructor(
    private val authStore: AuthDataStore
) : ViewModel() {
    private val _subwayResult = MutableStateFlow<List<SubwayTransitResult>>(emptyList())
    val subwayResult = _subwayResult.value

    private val _subwayTimetable = MutableStateFlow<List<SubwayTimeTableResponse>>(emptyList())
    val subwayTimetable = _subwayTimetable.value

    // 실시간 지하철 도착 정보
    fun getRealTimeSubwayArrivals(startStationName: String, endStationName: String, lineName: String){
        val token = authStore.getAccessToken()
        if(token == null){
            return
        }
        viewModelScope.launch {
            try{
                val response = RetrofitClient.subwayService.getRealTimeSubwayArrivals(token, startStationName, endStationName, lineName)
                Log.d("TRANSIT", "response body: ${response.body()}")
                val resultList = response.body()?.returnToList(Gson())
                Log.d("TRANSIT", "resultList: ${resultList.toString()}")
                if(response.isSuccessful && !resultList.isNullOrEmpty()){
                    _subwayResult.value = resultList
                    Log.d("TRANSIT_SUCCESS", _subwayResult.value.toString())
                }else{
                    // 실패 시 로그 남기기: response.isSuccessful이 false
                    Log.e("TRANSIT_ERROR", "response: ${response.errorBody()}")
                }
            }catch (e: Exception){
                Log.e("TRANSIT_ERROR", "${e.cause}, ${e.message}")
            }

        }
    }

    // 지하철 시간표
    fun getSubwayTimeTable(stationName: String, lineNum: String, weekTag: String, inoutTag: String){
        viewModelScope.launch {
            var stationCode = ""
            val cdResponse = RetrofitClient.subwayTimetableService.getSubwayCDByName(stationName)
            val cdBody = cdResponse.values.firstOrNull()
            if (cdBody?.row != null){
                for(r in cdBody.row){
                    if(lineNum == r.lineNum){
                        stationCode = r.stationCd
                        break
                    }
                }
            }

            val timetableResponse = RetrofitClient.subwayTimetableService.getSubwayTimetable(stationCode, weekTag, inoutTag)
            val timetableBody = timetableResponse.values.firstOrNull()
            if(timetableBody?.row != null){
                viewModelScope.launch {
                    _subwayTimetable.value = timetableBody.row
                    Log.d("TRANSIT_SUCCESS", _subwayTimetable.value.toString())
                }
            }else{
                Log.e("TRANSIT_ERROR", timetableBody?.result?.message ?: "Error")
            }
        }
    }
}