package com.example.pace.data.api

import androidx.room.Delete
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

interface SavedPlaceService {

    @POST("/api/v1/places/saved")
    suspend fun savePlace(
        @Header("Authorization") accessToken: String,
        @Body request: SavePlaceRequest
    ): RawDefaultResponse<SavePlaceResponse>

    @DELETE("/api/v1/places/saved")
    suspend fun deleteSavedPlaces(
        @Header("Authorization") accessToken: String,
        @Query("placeIds") placeIds: List<Long>
    ): RawDefaultResponse<Unit>


    @PATCH("/api/v1/places/saved/move")
    suspend fun movePlaceGroup(
        @Header("Author ization") accessToken: String,
        @Body request: MovePlaceGroupRequest
    ): RawDefaultResponse<Unit>

    @GET("/api/v1/places/saved/{groupId}")
    suspend fun getSavedPlacesByGroup(
        @Header("Authorization") accessToken: String,
        @Path("groupId") groupId: Long,
        @Query("sortType") sortType: String
    ): RawDefaultResponse<SavedPlaceListResponse>
}
