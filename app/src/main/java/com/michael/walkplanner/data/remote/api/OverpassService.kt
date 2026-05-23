package com.michael.walkplanner.data.remote.api

import com.michael.walkplanner.data.remote.dto.OverpassResponse
import retrofit2.http.POST
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import retrofit2.http.Url

interface OverpassService {
    @FormUrlEncoded
    @POST
    suspend fun queryOverpass(
        @Url url: String,
        @Field("data") data: String
    ): OverpassResponse
}


