package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.PlaceGroupService
import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.request.UpdateGroupRequest
import com.example.pace.data.model.response.CreateGroupResponse
import com.example.pace.data.model.response.GroupListResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateGroupResponse
import com.example.pace.data.repository.repository.PlaceGroupRepository
import com.example.pace.data.util.safeApiCall
import javax.inject.Inject

class PlaceGroupRepositoryImpl @Inject constructor(
    private val api: PlaceGroupService
) : PlaceGroupRepository {
    override suspend fun getGroupList(accessToken: String): RawDefaultResponse<GroupListResponse> {
        return safeApiCall { api.getGroupList(accessToken) }
    }

    override suspend fun createGroup(
        accessToken: String,
        request: CreateGroupRequest
    ): RawDefaultResponse<CreateGroupResponse> {
        return safeApiCall { api.createGroup(accessToken, request) }
    }

    override suspend fun deleteGroups(
        accessToken: String,
        groupIds: List<Long>
    ): RawDefaultResponse<Unit> {
        return safeApiCall { api.deleteGroups(accessToken, groupIds) }
    }

    override suspend fun updateGroup(
        accessToken: String,
        groupId: Long,
        request: UpdateGroupRequest
    ): RawDefaultResponse<UpdateGroupResponse> {
        return safeApiCall { api.updateGroup(accessToken, groupId, request) }
    }
}