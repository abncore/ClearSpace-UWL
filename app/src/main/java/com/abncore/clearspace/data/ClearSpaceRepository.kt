package com.abncore.clearspace.data

import android.content.Context
import android.content.SharedPreferences

class ClearSpaceRepository(context: Context) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("clearspace_terminal_prefs", Context.MODE_PRIVATE)

    fun isProfileCompleted(): Boolean = sharedPrefs.getBoolean("profile_completed", false)
    fun getUserName(): String = sharedPrefs.getString("user_name", "") ?: ""
    fun getUserDomain(): String = sharedPrefs.getString("user_domain", "") ?: ""
    fun getCompletedSessions(): Int = sharedPrefs.getInt("completed_sessions_today", 0)
    fun getStreakCount(): Int = sharedPrefs.getInt("streak_count_persistent", 1)

    // Added for Expanded Quest Persistence
    fun getBossHealth(): Int = sharedPrefs.getInt("workspace_boss_hp", 100)

    fun saveProfile(name: String, domain: String) {
        sharedPrefs.edit().apply {
            putBoolean("profile_completed", true)
            putString("user_name", name)
            putString("user_domain", domain)
            putInt("workspace_boss_hp", 100)
            apply()
        }
    }

    fun updateMetrics(sessions: Int, streak: Int, bossHp: Int) {
        sharedPrefs.edit().apply {
            putInt("completed_sessions_today", sessions)
            putInt("streak_count_persistent", streak)
            putInt("workspace_boss_hp", bossHp)
            apply()
        }
    }

    fun resetProfile() {
        sharedPrefs.edit().apply {
            putBoolean("profile_completed", false)
            putInt("completed_sessions_today", 0)
            putInt("streak_count_persistent", 1)
            putInt("workspace_boss_hp", 100)
            apply()
        }
    }
}