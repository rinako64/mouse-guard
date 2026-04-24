package com.mouseguard.app.settings

import android.content.Context

enum class Sensitivity(
    val prefKey: String,
    val thresholdAvg: Float,
    val thresholdMax: Float,
    val minOpenHoldMs: Long,
) {
    LOOSE("loose", 0.11f, 0.13f, 4000L),
    NORMAL("normal", 0.08f, 0.10f, 3000L),
    STRICT("strict", 0.05f, 0.07f, 2000L);

    companion object {
        fun fromKey(key: String?): Sensitivity =
            entries.firstOrNull { it.prefKey == key } ?: NORMAL
    }
}

object SettingsStore {
    private const val PREFS_NAME = "mouseguard_settings"
    private const val KEY_SENSITIVITY = "sensitivity"

    fun getSensitivity(context: Context): Sensitivity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Sensitivity.fromKey(prefs.getString(KEY_SENSITIVITY, null))
    }

    fun setSensitivity(context: Context, sensitivity: Sensitivity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SENSITIVITY, sensitivity.prefKey).apply()
    }
}
