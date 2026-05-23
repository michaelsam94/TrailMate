package com.michael.walkplanner.domain.repository

import com.michael.walkplanner.domain.model.ActiveSession

interface SessionSaveScheduler {
    fun scheduleSave(session: ActiveSession)
}
