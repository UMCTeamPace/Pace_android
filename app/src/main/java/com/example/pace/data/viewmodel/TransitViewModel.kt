package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.BuildConfig
import com.example.pace.data.api.RetrofitClient
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.BusItemList
import com.example.pace.data.model.response.BusTransitResponse
//import com.example.pace.data.model.response.SubwayTimeTableResponse
import com.example.pace.data.model.response.SubwayTimetableItem
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

    private val _subwayTimetable = MutableStateFlow<List<SubwayTimetableItem>>(emptyList())
    val subwayTimetable = _subwayTimetable.value

    private val _busResult = MutableStateFlow<BusItemList?>(null)
    val busResult = _busResult.value

    // 실시간 지하철 도착 정보
    fun getRealTimeSubwayArrivals(startStationName: String, endStationName: String, lineName: String){
        val token = authStore.getAccessToken()
        if(token == null){
            return
        }
        viewModelScope.launch {
            try{
                val response = RetrofitClient.subwayService.getRealTimeSubwayArrivals(token, startStationName, endStationName, lineName)
                val resultList = response.body()?.returnToList(Gson())

                if(response.isSuccessful && !resultList.isNullOrEmpty()){
                    _subwayResult.value = resultList
                    Log.d("TRANSIT_SUCCESS", _subwayResult.value.toString())
                }else{
                    Log.e("TRANSIT_ERROR", "${response.message()}: ${response.errorBody()}")
                }
            }catch (e: Exception){
                Log.e("TRANSIT_ERROR", "${e.cause}, ${e.message}")
            }

        }
    }

    // 지하철 시간표
    fun getSubwayTimetable2(isTempOrNot: String, upbdnSe: String, wkndSe: String, lineNm: String, stnNm: String,){
        viewModelScope.launch {
            try{
                val response = RetrofitClient.subwayTimetableService.getSubwayTimetable2(BuildConfig.PUBLIC_API_KEY, "200", "JSON", isTempOrNot, upbdnSe, wkndSe, lineNm, stnNm)
                val value = response.values.firstOrNull()
                val items = value?.body?.items?.item
                if(!items.isNullOrEmpty()){
                    // 지하철 도착 순서가 이른 순서대로 정렬
                    val sortedItems = items.sortedBy {
                        item -> item.trainArvlTm
                    }
                    _subwayTimetable.value = sortedItems
                    Log.d("TRANSIT_SUCCESS", _subwayTimetable.value.toString())
                }else{
                    Log.d("TRANSIT_ERROR", "item is null")
                }
            }
            catch (e: Exception){
                Log.e("TRANSIT_ERROR", e.message.toString())
            }
        }
    }

    // 실시간 버스 도착 정보
    fun getRealTimeBusArrivals(lineName: String, startStation: String, endStation: String){
        val token = authStore.getAccessToken()
        if(token == null){
            return
        }
        viewModelScope.launch {
            try {
                val parameters = RetrofitClient.busParameterService.getParameters(token, lineName, startStation, endStation)

                if(parameters.result != null){
                    val routeId = parameters.result.routeId
                    val ord = parameters.result.sequence.toString()
                    val stationId = parameters.result.nodeId

                    val response = RetrofitClient.busService.getRealTimeBusArrivals(BuildConfig.PUBLIC_API_KEY, stationId, routeId, ord)
                    val body = response.body()?.body
                    _busResult.value = body?.itemList

                    Log.d("TRANSIT_SUCCESS", _busResult.value.toString())
                } else{
                    Log.e("TRANSIT_ERROR", "result is null")
                }
            } catch (e:Exception){
                Log.e("TRANSIT_ERROR", e.message.toString())
            }
        }
    }
}