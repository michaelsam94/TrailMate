package com.michael.walkplanner

import android.app.Application
import com.michael.walkplanner.core.Constants
import com.michael.walkplanner.data.local.datastore.UserPrefsManager
import com.michael.walkplanner.data.local.db.WalkPlannerDatabase
import com.michael.walkplanner.data.remote.api.OverpassService
import com.michael.walkplanner.data.repository.*
import com.michael.walkplanner.data.work.WorkManagerSessionSaveScheduler
import com.michael.walkplanner.domain.repository.SessionSaveScheduler
import com.michael.walkplanner.domain.usecase.*
import com.michael.walkplanner.service.NotificationHelper
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.config.Configuration
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class WalkPlannerApplication : Application() {

    // Database & Storage
    lateinit var database: WalkPlannerDatabase
    lateinit var prefsManager: UserPrefsManager

    // Repositories
    lateinit var locationRepository: LocationRepositoryImpl
    lateinit var routeRepository: RouteRepositoryImpl
    lateinit var sessionRepository: SessionRepositoryImpl
    lateinit var userPrefsRepository: UserPrefsRepositoryImpl
    lateinit var sessionSaveScheduler: SessionSaveScheduler
    lateinit var notificationHelper: NotificationHelper

    // Use Cases
    lateinit var generateRouteUseCase: GenerateRouteUseCase
    lateinit var getSessionHistoryUseCase: GetSessionHistoryUseCase
    lateinit var updateUserPrefsUseCase: UpdateUserPrefsUseCase
    lateinit var startSessionUseCase: StartSessionUseCase
    lateinit var pauseSessionUseCase: PauseSessionUseCase
    lateinit var stopSessionUseCase: StopSessionUseCase

    override fun onCreate() {
        super.onCreate()

        // Init OSMDroid Configuration
        Configuration.getInstance().apply {
            load(this@WalkPlannerApplication, getSharedPreferences("${packageName}_preferences", MODE_PRIVATE))
            userAgentValue = "WalkPlannerNavigation/2.0 (Android; $packageName; contact: support@walkplanner.app)"
            val osmCacheFile = File(externalCacheDir ?: cacheDir, "osmdroid")
            osmdroidTileCache = osmCacheFile
            tileFileSystemCacheTrimBytes = Constants.OSM_TILE_CACHE_MAX_MB * 1024 * 1024L
        }

        // ROOM Database
        database = WalkPlannerDatabase.getInstance(applicationContext)

        // Datastore Prefs
        prefsManager = UserPrefsManager(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.ensureChannelCreated()
        sessionSaveScheduler = WorkManagerSessionSaveScheduler(applicationContext)

        // API Networking with Retrofit & Moshi
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "WalkPlannerNavigation/2.0 (Android; $packageName; contact: support@walkplanner.app)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/api/") // Overpass Base API Endpoint
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val overpassService = retrofit.create(OverpassService::class.java)

        // Providers
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Instantiate Repositories
        locationRepository = LocationRepositoryImpl(applicationContext, fusedLocationClient)
        routeRepository = RouteRepositoryImpl(applicationContext, database.routeDao(), database.osmCacheDao(), overpassService)
        sessionRepository = SessionRepositoryImpl(database.sessionDao())
        userPrefsRepository = UserPrefsRepositoryImpl(prefsManager)

        // Instantiate Use Cases
        generateRouteUseCase = GenerateRouteUseCase(locationRepository, routeRepository)
        getSessionHistoryUseCase = GetSessionHistoryUseCase(sessionRepository)
        updateUserPrefsUseCase = UpdateUserPrefsUseCase(userPrefsRepository)
        startSessionUseCase = StartSessionUseCase()
        pauseSessionUseCase = PauseSessionUseCase()
        stopSessionUseCase = StopSessionUseCase(sessionSaveScheduler)
    }
}
