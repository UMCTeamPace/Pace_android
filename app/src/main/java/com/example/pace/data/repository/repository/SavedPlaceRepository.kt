package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.MovePlaceGroupRequest
import com.example.pace.data.model.request.SavePlaceRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.model.response.SavedPlaceListResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SavedPlaceRepository {

    suspend fun savePlace(
        accessToken: String,
        request: SavePlaceRequest
    ): RawDefaultResponse<SavePlaceResponse>


    suspend fun deleteSavedPlaces(
        accessToken: String,
        placeIds: List<Long>
    ): RawDefaultResponse<Unit>


    suspend fun movePlaceGroup(
        accessToken: String,
        request: MovePlaceGroupRequest
    ): RawDefaultResponse<Unit>

    suspend fun getSavedPlacesByGroup(
        accessToken: String,
        groupId: Long,
        sortType: String
    ): RawDefaultResponse<SavedPlaceListResponse>
}