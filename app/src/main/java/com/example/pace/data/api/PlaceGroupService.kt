package com.example.pace.data.api

import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.data.model.request.DeleteGroupRequest
import com.example.pace.data.model.request.UpdateGroupRequest
import com.example.pace.data.model.response.CreateGroupResponse
import com.example.pace.data.model.response.GroupListResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateGroupResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PlaceGroupService {

    @GET("/api/v1/groups")
    suspend fun getGroupList(
        @Header("Authorization") accessToken: String
    ): RawDefaultResponse<GroupListResponse> // 수정됨

    @POST("/api/v1/groups")
    suspend fun createGroup(
        @Header("Authorization") accessToken: String,
        @Body request: CreateGroupRequest
    ): RawDefaultResponse<CreateGroupResponse>

    @HTTP(method = "DELETE", path = "/api/v1/groups", hasBody = true)
    suspend fun deleteGroups(
        @Header("Authorization") accessToken: String,
        @Body request: DeleteGroupRequest
    ): RawDefaultResponse<String>

    @PATCH("/api/v1/groups/{groupId}")
    suspend fun updateGroup(
        @Header("Authorization") accessToken: String,
        @Path("groupId") groupId: Long,
        @Body request: UpdateGroupRequest
    ): RawDefaultResponse<UpdateGroupResponse>
}