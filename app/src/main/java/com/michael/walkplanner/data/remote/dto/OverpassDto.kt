package com.michael.walkplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    val elements: List<OverpassElement>?
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    val type: String, // "node" or "way"
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val nodes: List<Long>?,
    val tags: Map<String, String>?
)
