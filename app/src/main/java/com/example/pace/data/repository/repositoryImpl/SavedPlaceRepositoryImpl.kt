package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.SavedPlaceService
import com.example.pace.data.model.request.MovePlaceGroupRequest
import com.example.pace.data.model.request.SavePlaceRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.model.response.SavedPlaceListResponse
import com.example.pace.data.repository.repository.SavedPlaceRepository
import javax.inject.Inject

class SavedPlaceRepositoryImpl @Inject constructor(
    private val api: SavedPlaceService
) : SavedPlaceRepository {
    override suspend fun savePlace(
        accessToken: String,
        request: SavePlaceRequest
    ): RawDefaultResponse<SavePlaceResponse> {
        return api.savePlace(accessToken, request)
    }

    override suspend fun deleteSavedPlaces(
        accessToken: String,
        placeIds: List<Long>
    ): RawDefaultResponse<Unit> {
        return api.deleteSavedPlaces(accessToken, placeIds)
    }

    override suspend fun movePlaceGroup(
        accessToken: String,
        request: MovePlaceGroupRequest
    ): RawDefaultResponse<Unit> {
        return api.movePlaceGroup(accessToken, request)
    }

    override suspend fun getSavedPlacesByGroup(
        accessToken: String,
        groupId: Long
    ): RawDefaultResponse<SavedPlaceListResponse> {
        return api.getSavedPlacesByGroup(accessToken, groupId)

    }
}