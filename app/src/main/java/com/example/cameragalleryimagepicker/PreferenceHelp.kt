package com.example.cameragalleryimagepicker

import android.content.Context
import android.content.SharedPreferences

object PreferenceHelp {
    private val KEY_IMAGE = "IMAGE"
    private var userPrefs: SharedPreferences? = null

    private fun getSharedPreference(): SharedPreferences {
        return userPrefs ?: MyApplication.getApplicationContext()
            .getSharedPreferences("myPref", Context.MODE_PRIVATE)
    }

    fun setimage(value: String) {
        setImage(KEY_IMAGE, value)
    }

    fun getimage(): String {
        return getImage(KEY_IMAGE)
    }

    private fun setImage(key: String, value: String) {
        getSharedPreference().edit().putString(key, value).apply()
    }

    private fun getImage(key: String): String {
        return getSharedPreference().getString(key, "") ?: ""
    }
}