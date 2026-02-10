package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.request.UpdateGroupRequest
import com.example.pace.data.model.response.CreateGroupResponse
import com.example.pace.data.model.response.GroupListResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateGroupResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlaceGroupRepository {

    suspend fun getGroupList(
        accessToken: String
    ): RawDefaultResponse<GroupListResponse> // 수정됨

    suspend fun createGroup(
         accessToken: String,
         request: CreateGroupRequest
    ): RawDefaultResponse<CreateGroupResponse>


    suspend fun deleteGroups(
         accessToken: String,
         groupIds: List<Long>
    ): RawDefaultResponse<String> // 삭제는 결과 데이터가 없는 경우가 많아 Unit 권장


    suspend fun updateGroup(
        accessToken: String,
        groupId: Long,
        request: UpdateGroupRequest
    ): RawDefaultResponse<UpdateGroupResponse>
}