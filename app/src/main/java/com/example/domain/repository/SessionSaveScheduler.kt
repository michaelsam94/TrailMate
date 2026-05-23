package com.example.domain.repository

import com.example.domain.model.ActiveSession

interface SessionSaveScheduler {
    fun scheduleSave(session: ActiveSession)
}
