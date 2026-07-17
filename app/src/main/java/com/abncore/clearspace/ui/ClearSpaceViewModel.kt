package com.abncore.clearspace.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.abncore.clearspace.data.ClearSpaceRepository
import com.abncore.clearspace.data.ClearSpaceStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionRecord(
    val timestamp: String,
    val taskName: String,
    val duration: String
)

class ClearSpaceViewModel(private val repository: ClearSpaceRepository, val store: ClearSpaceStore) : ViewModel() {
    var nameInput by mutableStateOf("")
    var domainInput by mutableStateOf("")
    var registeredName by mutableStateOf("")
    var registeredDomain by mutableStateOf("")

    // Controlled flag: Updated via initializeStore() based on persistent storage
    var isProfileCreated by mutableStateOf(false)

    var currentScreenView by mutableStateOf("dashboard")
    var isTutorialActive by mutableStateOf(false)
    var currentTutorialStep by mutableStateOf(1)
    var isPlusPopupVisible by mutableStateOf(false)
    var isRewardsPopupVisible by mutableStateOf(false)
    var showExitConfirmationDialog by mutableStateOf(false)
    var isTimerRunning by mutableStateOf(false)
    var timeLeft by mutableStateOf(25 * 60)
    var activeCategory by mutableStateOf("Work")
    val categories = mutableStateListOf("Work", "Study", "Personal")
    var newTagInput by mutableStateOf("")
    var dailyGoalSessions by mutableStateOf(4)
    var completedSessionsToday by mutableStateOf(0)
    var bossHealth by mutableStateOf(100)
    var focusObjectiveText by mutableStateOf("")
    var streakCount by mutableStateOf(3)
    var isFaceTrackingEnabled by mutableStateOf(false)
    var strictSplashLock by mutableStateOf(false)
    var keepScreenAwake by mutableStateOf(false)
    var isAmbientAudioActive by mutableStateOf(false)
    var notificationsEnabled by mutableStateOf(true)

    val sessionHistory = mutableStateListOf<SessionRecord>()

    fun initializeStore() {
        val savedName = store.getRegisteredName()
        if (!savedName.isNullOrBlank()) {
            registeredName = savedName
            registeredDomain = store.getRegisteredDomain() ?: ""
            isProfileCreated = true

            // Only trigger tutorial on boot if the profile is finished BUT the tutorial wasn't seen
            isTutorialActive = !store.hasSeenTutorial()
        } else {
            isProfileCreated = false
            isTutorialActive = false
        }
    }

    fun initializeProfile() {
        if (nameInput.isNotBlank() && domainInput.isNotBlank()) {
            registeredName = nameInput
            registeredDomain = domainInput
            store.saveProfile(registeredName, registeredDomain)

            isProfileCreated = true

            // TRIGGER TUTORIAL: This is the realistic first-run experience
            isTutorialActive = true
        }
    }

    fun toggleTimer() {
        isTimerRunning = !isTimerRunning
    }

    fun commitSessionToHistory() {
        val formatter = SimpleDateFormat("dd MMM // HH:mm", Locale.getDefault())
        val timestampString = formatter.format(Date()).uppercase()
        val currentTask = if (focusObjectiveText.isNotBlank()) focusObjectiveText else "SYSTEM FOCUS BLOCK"

        sessionHistory.add(0, SessionRecord(timestampString, currentTask, "${timeLeft / 60}M"))
        completedSessionsToday++
        bossHealth = (bossHealth - 25).coerceAtLeast(0)
    }

    fun cycleCategory() {
        val currentIndex = categories.indexOf(activeCategory)
        val nextIndex = (currentIndex + 1) % categories.size
        activeCategory = categories[nextIndex]
    }

    fun removeCustomTag(tag: String) {
        if (categories.size > 1) {
            categories.remove(tag)
            if (activeCategory == tag) {
                activeCategory = categories.first()
            }
        }
    }

    fun addCustomTag() {
        if (newTagInput.isNotBlank() && !categories.contains(newTagInput)) {
            categories.add(newTagInput)
            newTagInput = ""
        }
    }

    fun resetProfile(context: Context) {
        // 1. Wipe persistent storage synchronously
        store.clearAll()

        // 2. Explicitly clear local memory state
        registeredName = ""
        registeredDomain = ""
        nameInput = ""
        domainInput = ""
        isProfileCreated = false
        sessionHistory.clear()
        completedSessionsToday = 0
        bossHealth = 100
        isTimerRunning = false
        isAmbientAudioActive = false

        // 3. Restart the app
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)

        // 4. Force process termination
        if (context is Activity) {
            context.finishAffinity()
        }
        // Use Runtime.getRuntime().exit(0) to ensure the VM is fully flushed
        Runtime.getRuntime().exit(0)
    }

    fun advanceTutorialStep() {
        if (currentTutorialStep < 6) {
            currentTutorialStep++
        } else {
            isTutorialActive = false
            store.setTutorialSeen(true)
        }
    }

    fun skipTutorialSequence() {
        isTutorialActive = false
        store.setTutorialSeen(true)
    }

    fun onFaceAnalysisUpdate(contact: Boolean) {
        if (!contact && isTimerRunning) {
            bossHealth = (bossHealth + 2).coerceAtMost(100)
        }
    }

    fun handleSessionInterruptedAbruptly() {
        isTimerRunning = false
        bossHealth = (bossHealth + 15).coerceAtMost(100)
    }
}