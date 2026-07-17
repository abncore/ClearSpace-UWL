package com.abncore.clearspace.data

import android.content.Context
import android.content.SharedPreferences

class ClearSpaceStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("clear_space_prefs", Context.MODE_PRIVATE)

    fun getRegisteredName(): String? {
        return prefs.getString("user_name", null)
    }

    fun getRegisteredDomain(): String? {
        return prefs.getString("user_domain", null)
    }

    fun saveProfile(name: String, domain: String) {
        prefs.edit()
            .putString("user_name", name)
            .putString("user_domain", domain)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    fun setTutorialSeen(seen: Boolean) {
        prefs.edit().putBoolean("tutorial_seen", seen).commit()
    }

    fun hasSeenTutorial(): Boolean {
        return prefs.getBoolean("tutorial_seen", false)
    }

    var coins: Int
        get() = prefs.getInt("coins", 0)
        set(value) = prefs.edit().putInt("coins", value).apply()
}