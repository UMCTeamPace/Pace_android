package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.BuildConfig
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.request.SavePlaceRequest
import com.example.pace.data.model.request.UpdateGroupRequest
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.repository.repository.PlaceGroupRepository
import com.example.pace.data.repository.repository.SavedPlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: PlaceGroupRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {
    private val _errorCode = MutableLiveData<String?>()
    val errorCode: LiveData<String?> get() = _errorCode

    private val _isOperationSuccess = MutableLiveData<Boolean>()
    val isOperationSuccess: LiveData<Boolean> get() = _isOperationSuccess

    private val _groupList = MutableLiveData<List<GroupItem>>()
    val groupList: LiveData<List<GroupItem>> get() = _groupList

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    var currentSortType: String = "LATEST"
        private set

    private val token: String = BuildConfig.BEARER_TOKEN

    private val _savedPlaces = MutableLiveData<List<SavePlaceResponse>>()
    val savedPlaces: LiveData<List<SavePlaceResponse>> get() = _savedPlaces

    fun fetchGroupList() {
        viewModelScope.launch {
            try {
                val response = groupRepository.getGroupList(token)

                if (response.isSuccess) {
                    _groupList.value = response.result?.placeGroupList
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
            _errorCode.value = null
            _isOperationSuccess.value = false

            val request = CreateGroupRequest(name, color)
            val response = groupRepository.createGroup(token, request)

            if (response.isSuccess) {
                _isOperationSuccess.value = true
                fetchGroupList()
            } else {
                _errorMessage.value = response.message
                _errorCode.value = response.code
            }
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            try {
                val response = groupRepository.deleteGroups(token, listOf(groupId))

                if (response.isSuccess) {
                    fetchGroupList()
                    _errorMessage.value = "그룹이 삭제되었습니다."
                    _isOperationSuccess.value = true
                } else {
                    _errorMessage.value = "서버 거절: ${response.message}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "에러: ${e.localizedMessage}"
            }
        }
    }

    fun updateGroup(groupId: Long, name: String, color: String) {
        viewModelScope.launch {
            _errorCode.value = null
            _isOperationSuccess.value = false

            val request = UpdateGroupRequest(name, color)
            val response = groupRepository.updateGroup(token, groupId, request)

            if (response.isSuccess) {
                _isOperationSuccess.value = true
                fetchGroupList()
            } else {
                _errorMessage.value = response.message
                _errorCode.value = response.code
            }
        }
    }

    fun savePlace(groupId: Long, placeId: String, placeName: String) {
        viewModelScope.launch {
            try {
                // 서버 요청 객체 생성
                val request = SavePlaceRequest(
                    placeName = placeName,
                    placeId = placeId,
                    groupId = groupId
                )

                val response = savedPlaceRepository.savePlace(token, request)

                if (response.isSuccess) {
                    _isOperationSuccess.value = true
                    _errorMessage.value = "장소가 저장되었습니다."
                    fetchGroupList()
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "저장 실패: ${e.message}"
            }
        }
    }

    fun getSavedPlaces(groupId: Long, sortType: String = "LATEST") {
        viewModelScope.launch {
            try {
                val response = savedPlaceRepository.getSavedPlacesByGroup(token, groupId, sortType)

                if (response.isSuccess) {
                    _savedPlaces.value = response.result?.savedPlaceList ?: emptyList()

                    currentSortType = sortType
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "목록 로드 실패: ${e.message}"
            }
        }
    }
}