package com.example.data.remote.api

import com.example.data.remote.dto.OverpassResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassService {
    @GET("interpreter")
    suspend fun queryOverpass(
        @Query("data", encoded = true) data: String
    ): OverpassResponse
}
