package com.babyphotos.archive.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("baby_photos_settings", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    var autoAddThreshold: Int
        get() = prefs.getInt(KEY_AUTO_ADD_THRESHOLD, 80)
        set(value) = prefs.edit().putInt(KEY_AUTO_ADD_THRESHOLD, value).apply()

    var confirmThreshold: Int
        get() = prefs.getInt(KEY_CONFIRM_THRESHOLD, 50)
        set(value) = prefs.edit().putInt(KEY_CONFIRM_THRESHOLD, value).apply()

    var maxImageSize: Int
        get() = prefs.getInt(KEY_MAX_IMAGE_SIZE, 1024)
        set(value) = prefs.edit().putInt(KEY_MAX_IMAGE_SIZE, value).apply()

    var jpegQuality: Int
        get() = prefs.getInt(KEY_JPEG_QUALITY, 70)
        set(value) = prefs.edit().putInt(KEY_JPEG_QUALITY, value).apply()

    var concurrencyLimit: Int
        get() = prefs.getInt(KEY_CONCURRENCY, 4)
        set(value) = prefs.edit().putInt(KEY_CONCURRENCY, value).apply()

    var scanStartDate: Long
        get() = prefs.getLong(KEY_SCAN_START_DATE, 0L)
        set(value) = prefs.edit().putLong(KEY_SCAN_START_DATE, value).apply()

    fun isApiConfigured(): Boolean = apiBaseUrl.isNotBlank() && apiKey.isNotBlank()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_AUTO_ADD_THRESHOLD = "auto_add_threshold"
        private const val KEY_CONFIRM_THRESHOLD = "confirm_threshold"
        private const val KEY_MAX_IMAGE_SIZE = "max_image_size"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_CONCURRENCY = "concurrency"
        private const val KEY_SCAN_START_DATE = "scan_start_date"
    }
}
