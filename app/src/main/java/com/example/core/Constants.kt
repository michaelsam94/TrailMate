package com.example.core

object Constants {
    const val LOCATION_UPDATE_INTERVAL_PLANNING_MS = 5_000L
    const val LOCATION_UPDATE_INTERVAL_ACTIVE_MS   = 2_000L
    const val LOCATION_ACCURACY_THRESHOLD_M        = 50f
    const val ROUTE_DISTANCE_MIN_KM                = 0.5
    const val ROUTE_DISTANCE_MAX_KM                = 50.0
    const val ROUTE_DISTANCE_STEP_KM               = 0.5
    const val ROUTE_DISTANCE_TOLERANCE_PERCENT     = 0.10
    const val ROUTE_OPTION_COUNT                   = 3
    const val CACHE_EXPIRY_DAYS                    = 7L
    const val OSM_TILE_CACHE_MAX_MB                = 100
    const val NOTIFICATION_ID_ACTIVE_RUN           = 1001
    const val CHANNEL_ID_ACTIVE_RUN                = "active_run_channel"
    const val WORK_TAG_SAVE_SESSION                = "save_session_work"
    const val DEEP_LINK_BASE                       = "trailmate://activerun"
}
