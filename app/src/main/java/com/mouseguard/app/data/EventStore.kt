package com.mouseguard.app.data

import android.content.Context
import java.io.File

data class PokanEvent(val openMs: Long, val closeMs: Long) {
    val durationMs: Long get() = (closeMs - openMs).coerceAtLeast(0L)
}

data class MonitoringSession(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object EventStore {
    private const val EVENTS_FILE = "pokan_events.tsv"
    private const val SESSIONS_FILE = "monitoring_sessions.tsv"
    private const val PREFS_NAME = "mouseguard_runtime"
    private const val KEY_CURRENT_SESSION_START = "current_session_start"

    private fun eventsFile(context: Context) = File(context.filesDir, EVENTS_FILE)
    private fun sessionsFile(context: Context) = File(context.filesDir, SESSIONS_FILE)
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun recordPokanEvent(context: Context, openMs: Long, closeMs: Long) {
        eventsFile(context).appendText("$openMs\t$closeMs\n")
    }

    @Synchronized
    fun startSession(context: Context, startMs: Long) {
        prefs(context).edit().putLong(KEY_CURRENT_SESSION_START, startMs).apply()
    }

    @Synchronized
    fun endSession(context: Context, endMs: Long) {
        val p = prefs(context)
        val start = p.getLong(KEY_CURRENT_SESSION_START, 0L)
        if (start > 0L && endMs > start) {
            sessionsFile(context).appendText("$start\t$endMs\n")
        }
        p.edit().remove(KEY_CURRENT_SESSION_START).apply()
    }

    fun currentSessionStart(context: Context): Long? {
        val v = prefs(context).getLong(KEY_CURRENT_SESSION_START, 0L)
        return if (v > 0L) v else null
    }

    @Synchronized
    fun getEvents(context: Context): List<PokanEvent> {
        val file = eventsFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 2) return@mapNotNull null
            val open = parts[0].toLongOrNull() ?: return@mapNotNull null
            val close = parts[1].toLongOrNull() ?: return@mapNotNull null
            PokanEvent(open, close)
        }
    }

    @Synchronized
    fun getSessions(context: Context): List<MonitoringSession> {
        val file = sessionsFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 2) return@mapNotNull null
            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
            MonitoringSession(start, end)
        }
    }
}
