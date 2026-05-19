package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.request.DeletePlacesRequest
import com.example.pace.data.model.request.MovePlaceGroupRequest
import com.example.pace.data.model.request.SavePlaceRequest
import com.example.pace.data.model.request.UpdateGroupRequest
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.repository.repository.PlaceGroupRepository
import com.example.pace.data.repository.repository.SavedPlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceSavedGroupState(
    val groupId: Long,
    val groupColor: String,
    val savedPlaceId: Long,
    val createdAt: String
)

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

    private val token: String
        get() {
            val accessToken = authDataStore.getAccessToken().orEmpty()
            return if (accessToken.isNotEmpty() && !accessToken.startsWith("Bearer ")) {
                "Bearer $accessToken"
            } else {
                accessToken
            }
        }

    private val _savedPlaces = MutableLiveData<List<SavePlaceResponse>>()
    val savedPlaces: LiveData<List<SavePlaceResponse>> get() = _savedPlaces

    private val _placeSavedGroupMap = MutableLiveData<Map<Long, Long>>()
    val placeSavedGroupMap: LiveData<Map<Long, Long>> get() = _placeSavedGroupMap

    private val placeSavedStateCache = mutableMapOf<String, List<PlaceSavedGroupState>>()

    private val _placeSavedStatesByPlaceId = MutableLiveData<Map<String, List<PlaceSavedGroupState>>>(emptyMap())
    val placeSavedStatesByPlaceId: LiveData<Map<String, List<PlaceSavedGroupState>>> get() = _placeSavedStatesByPlaceId

    private val _isPlaceSavedGroupLoading = MutableLiveData<Boolean>()
    val isPlaceSavedGroupLoading: LiveData<Boolean> get() = _isPlaceSavedGroupLoading

    private val _isPlaceGroupEditSuccess = MutableLiveData<Boolean>()
    val isPlaceGroupEditSuccess: LiveData<Boolean> get() = _isPlaceGroupEditSuccess

    private val _isPlaceGroupEditLoading = MutableLiveData<Boolean>()
    val isPlaceGroupEditLoading: LiveData<Boolean> get() = _isPlaceGroupEditLoading

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
            } catch (e: retrofit2.HttpException) {
                val errorJson = e.response()?.errorBody()?.string()
                try {
                    val errorResponse = com.google.gson.Gson().fromJson(errorJson, com.example.pace.data.model.response.RawDefaultResponse::class.java)
                    _errorCode.value = errorResponse.code
                    _errorMessage.value = errorResponse.message
                } catch (parsingError: Exception) {
                    _errorMessage.value = "서버 통신 오류 (400)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "저장 실패: ${e.message}"
            }
        }
    }

    fun fetchSavedGroupsForPlace(placeId: String, groups: List<GroupItem>, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (placeId.isBlank()) {
                _placeSavedGroupMap.value = emptyMap()
                return@launch
            }

            if (!forceRefresh && placeSavedStateCache.containsKey(placeId)) {
                val cachedStates = placeSavedStateCache[placeId].orEmpty()
                _placeSavedGroupMap.value = cachedStates.associate { it.groupId to it.savedPlaceId }
                _placeSavedStatesByPlaceId.value = placeSavedStateCache.toMap()
                return@launch
            }

            _isPlaceSavedGroupLoading.value = true
            _errorCode.value = null

            try {
                val resultStates = coroutineScope {
                    groups.map { group ->
                        async {
                            try {
                                val response = savedPlaceRepository.getSavedPlacesByGroup(
                                    token,
                                    group.groupId,
                                    currentSortType
                                )
                                val matchedPlace = response.result
                                    ?.savedPlaceList
                                    ?.firstOrNull { it.placeId == placeId }

                                matchedPlace?.let {
                                    PlaceSavedGroupState(
                                        groupId = group.groupId,
                                        groupColor = group.groupColor,
                                        savedPlaceId = it.savedPlaceId,
                                        createdAt = it.createdAt
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                placeSavedStateCache[placeId] = resultStates
                _placeSavedGroupMap.value = resultStates.associate { it.groupId to it.savedPlaceId }
                _placeSavedStatesByPlaceId.value = placeSavedStateCache.toMap()
            } finally {
                _isPlaceSavedGroupLoading.value = false
            }
        }
    }

    fun updatePlaceGroups(
        placeId: String,
        placeName: String,
        groupIdsToAdd: List<Long>,
        savedPlaceIdsToDelete: List<Long>
    ) {
        viewModelScope.launch {
            _errorCode.value = null
            _isPlaceGroupEditSuccess.value = false
            _isPlaceGroupEditLoading.value = true

            try {
                groupIdsToAdd.forEach { groupId ->
                    val saveResponse = savedPlaceRepository.savePlace(
                        token,
                        SavePlaceRequest(
                            placeName = placeName,
                            placeId = placeId,
                            groupId = groupId
                        )
                    )
                    if (!saveResponse.isSuccess) {
                        _errorCode.value = saveResponse.code
                        _errorMessage.value = saveResponse.message
                        return@launch
                    }
                }

                if (savedPlaceIdsToDelete.isNotEmpty()) {
                    val deleteResponse = savedPlaceRepository.deleteSavedPlaces(
                        token,
                        DeletePlacesRequest(savedPlaceIdsToDelete)
                    )
                    if (!deleteResponse.isSuccess) {
                        _errorCode.value = deleteResponse.code
                        _errorMessage.value = deleteResponse.message
                        return@launch
                    }
                }

                fetchGroupList()
                val currentGroups = _groupList.value.orEmpty()
                if (currentGroups.isNotEmpty()) {
                    fetchSavedGroupsForPlace(placeId, currentGroups, forceRefresh = true)
                } else {
                    invalidatePlaceSavedState(placeId)
                }
                _isPlaceGroupEditSuccess.value = true
            } catch (e: retrofit2.HttpException) {
                val errorJson = e.response()?.errorBody()?.string()
                try {
                    val errorResponse = com.google.gson.Gson().fromJson(errorJson, com.example.pace.data.model.response.RawDefaultResponse::class.java)
                    _errorCode.value = errorResponse.code
                    _errorMessage.value = errorResponse.message
                } catch (parsingError: Exception) {
                    _errorMessage.value = "Server error (400)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Save failed: ${e.message}"
            } finally {
                _isPlaceGroupEditLoading.value = false
            }
        }
    }

    fun invalidatePlaceSavedState(placeId: String) {
        placeSavedStateCache.remove(placeId)
        _placeSavedStatesByPlaceId.value = placeSavedStateCache.toMap()
    }

    fun clearPlaceSavedStateCache() {
        placeSavedStateCache.clear()
        _placeSavedGroupMap.value = emptyMap()
        _placeSavedStatesByPlaceId.value = emptyMap()
    }

    fun clearErrorState() {
        _errorCode.value = null
        _errorMessage.value = ""
        _isOperationSuccess.value = false
        _isPlaceGroupEditSuccess.value = false
        _isPlaceGroupEditLoading.value = false
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

    fun deletePlaces(placeIdList: List<Long>) {
        viewModelScope.launch {
            try {
                val response = savedPlaceRepository.deleteSavedPlaces(token, DeletePlacesRequest(placeIdList))
                if (response.isSuccess) {
                    _isOperationSuccess.value = true // Activity에서 이 값을 보고 finish() 처리
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = "삭제 실패: ${e.localizedMessage}"
            }
        }
    }

    fun movePlaces(placeIdList: List<Long>, targetGroupId: Long) {
        viewModelScope.launch {
            _errorCode.value = null
            _isOperationSuccess.value = false

            try {
                val request = MovePlaceGroupRequest(
                    placeIdList = placeIdList,
                    targetGroupId = targetGroupId
                )
                val response = savedPlaceRepository.movePlaceGroup(token, request)

                if (response.isSuccess) {
                    _isOperationSuccess.value = true
                    // 필요하다면 그룹 목록 갱신
                    // fetchGroupList()
                } else {
                    _errorMessage.value = response.message
                    _errorCode.value = response.code

                    android.util.Log.e("MoveError", "Move failed: ${response.message}, code: ${response.code}")
                }
            } catch (e: retrofit2.HttpException) { // [중요] 400 에러를 여기서 가로챕니다.
                val errorJson = e.response()?.errorBody()?.string()
                try {
                    // Gson으로 서버가 보낸 {"code": "PLACE400_1", ...} 본문을 직접 파싱
                    val errorResponse = com.google.gson.Gson().fromJson(errorJson, com.example.pace.data.model.response.RawDefaultResponse::class.java)
                    _errorCode.value = errorResponse.code   // "PLACE400_1" 세팅
                    _errorMessage.value = errorResponse.message
                } catch (parsingError: Exception) {
                    _errorMessage.value = "서버 통신 오류 (400)"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "알 수 없는 오류: ${e.message}"
            }
        }
    }
}
