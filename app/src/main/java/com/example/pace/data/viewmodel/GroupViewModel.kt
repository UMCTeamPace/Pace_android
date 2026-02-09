package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.BuildConfig
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.repository.repository.PlaceGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val repository: PlaceGroupRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {
    private val _groupList = MutableLiveData<List<GroupItem>>()
    val groupList: LiveData<List<GroupItem>> get() = _groupList

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val token: String = BuildConfig.BEARER_TOKEN

    fun fetchGroupList() {
        viewModelScope.launch {
            try {
                Log.d("GroupViewModel", "Fetching groups with token: $token")
                val response = repository.getGroupList(token)

                if (response.isSuccess) {
                    _groupList.value = response.result?.placeGroupList
                    Log.d("GroupViewModel", "Success: ${response.result?.listSize} groups loaded")
                } else {
                    _errorMessage.value = response.message
                    Log.e("GroupViewModel", "Failed: ${response.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "네트워크 연결 실패: ${e.message}"
            }
        }
    }

    fun createGroup(name: String, color: String) {
        viewModelScope.launch {
            try {
                val request = CreateGroupRequest(name, color)
                val response = repository.createGroup(token, request)

                if (response.isSuccess) {
                    fetchGroupList() // 생성 성공 시 목록 새로고침
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "그룹 생성 실패"
            }
        }
    }
}