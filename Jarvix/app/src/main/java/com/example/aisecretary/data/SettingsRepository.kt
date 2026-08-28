package com.example.aisecretary.data

import android.content.Context

/**
 * User preferences. Plain SharedPreferences - these are a handful of simple values, not
 * structured data, so Room would be overkill and DataStore would be a new dependency for no
 * real benefit here.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvix_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AMBIENT_SOUND = "ambient_sound_enabled"
        private const val KEY_ALARM_SOUND_IN_SILENT = "alarm_sound_in_silent_mode"
        private const val KEY_SNOOZE_SHORT = "snooze_minutes_short"
        private const val KEY_SNOOZE_MEDIUM = "snooze_minutes_medium"
        private const val KEY_SNOOZE_LONG = "snooze_minutes_long"

        const val DEFAULT_SNOOZE_SHORT = 5
        const val DEFAULT_SNOOZE_MEDIUM = 10
        const val DEFAULT_SNOOZE_LONG = 15
    }

    var ambientSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMBIENT_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_AMBIENT_SOUND, value).apply()

    /**
     * Default false: matches the deliberate 2026-08-27 decision that silencing the phone means
     * vibrate-only. This setting lets a user override that choice for themselves instead of it
     * being fixed in code.
     */
    var alarmSoundInSilentMode: Boolean
        get() = prefs.getBoolean(KEY_ALARM_SOUND_IN_SILENT, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_SOUND_IN_SILENT, value).apply()

    var snoozeMinutesShort: Int
        get() = prefs.getInt(KEY_SNOOZE_SHORT, DEFAULT_SNOOZE_SHORT)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_SHORT, value).apply()

    var snoozeMinutesMedium: Int
        get() = prefs.getInt(KEY_SNOOZE_MEDIUM, DEFAULT_SNOOZE_MEDIUM)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_MEDIUM, value).apply()

    var snoozeMinutesLong: Int
        get() = prefs.getInt(KEY_SNOOZE_LONG, DEFAULT_SNOOZE_LONG)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_LONG, value).apply()
}
