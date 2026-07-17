package com.abncore.clearspace

import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.abncore.clearspace.data.ClearSpaceRepository
import com.abncore.clearspace.data.ClearSpaceStore
import com.abncore.clearspace.ui.ClearSpaceViewModel
import com.abncore.clearspace.ui.SessionRecord
import kotlinx.coroutines.delay

data class MainActivityControlSetting(
    val title: String,
    val subtitle: String,
    val state: Boolean,
    val onToggle: () -> Unit
)

class MainActivity : ComponentActivity() {

    private var ambientMediaPlayer: MediaPlayer? = null

    private fun showSessionWarning(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "session_warning_channel"
        val channel = NotificationChannel(channelId, "Session Integrity", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Session Alert")
            .setContentText("Warning: Leaving the terminal will cost you half your session points. Return immediately.")
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(1001, builder.build())
    }

    private fun startAmbientAudioEngine(context: Context) {
        try {
            stopAmbientAudioEngine()
            ambientMediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse("content://settings/system/notification_sound"))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("ClearSpaceAudio", "System ambient sound initialization bypassed: ${e.message}")
        }
    }

    private fun stopAmbientAudioEngine() {
        ambientMediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        ambientMediaPlayer = null
    }

    override fun onDestroy() {
        stopAmbientAudioEngine()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val repository = ClearSpaceRepository(applicationContext)
        val store = ClearSpaceStore(applicationContext)
        val viewModel = ClearSpaceViewModel(repository, store)

        this.setContent {
            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            val stableAppContext = context.applicationContext
            val density = LocalDensity.current

            val backgroundWhite = Color(0xFFFFFFFF)
            val textBlack = Color(0xFF000000)
            val subTextGray = Color(0xFF64748B)
            val lineBorderLight = Color(0xFFE2E8F0)

            val isTut = viewModel.isTutorialActive
            val tutStep = viewModel.currentTutorialStep

            var showStartupSplash by remember { mutableStateOf(true) }
            val startupAlpha = remember { Animatable(0f) }
            val startupScale = remember { Animatable(0.92f) }

            var diagCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }
            var vaultCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }
            var timerCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }
            var bossCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }
            var inputCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }
            var actionCoords by remember { mutableStateOf(Pair(Offset.Zero, Size.Zero)) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
                val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
                Log.d("ClearSpace", "Notification: $notificationsGranted, Camera: $cameraGranted")
            }

            LaunchedEffect(Unit) {
                viewModel.initializeStore()

                startupAlpha.animateTo(1f, animationSpec = tween(1200, easing = LinearEasing))
                startupScale.animateTo(1.02f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                delay(800L)
                startupAlpha.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
                showStartupSplash = false

                val permissionsNeeded = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionsNeeded.add(Manifest.permission.CAMERA)
                permissionLauncher.launch(permissionsNeeded.toTypedArray())
            }

            LaunchedEffect(viewModel.isTimerRunning) {
                if (viewModel.isTimerRunning) {
                    while (viewModel.timeLeft > 0) {
                        delay(1000L)
                        if (viewModel.isTimerRunning) {
                            viewModel.timeLeft--
                            if (viewModel.timeLeft == 0) {
                                viewModel.isTimerRunning = false
                                viewModel.commitSessionToHistory()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                }
            }

            LaunchedEffect(viewModel.isTimerRunning, viewModel.isFaceTrackingEnabled) {
                if (viewModel.isTimerRunning && viewModel.isFaceTrackingEnabled) {
                    while (viewModel.isTimerRunning) {
                        delay(4000L)
                        val simulatedEyeContact = (0..10).random() > 2
                        viewModel.onFaceAnalysisUpdate(simulatedEyeContact)
                    }
                }
            }

            LaunchedEffect(viewModel.isAmbientAudioActive) {
                if (viewModel.isAmbientAudioActive) {
                    startAmbientAudioEngine(context)
                } else {
                    stopAmbientAudioEngine()
                }
            }

            if (showStartupSplash) {
                BackHandler { }
            } else if (viewModel.isTutorialActive) {
                BackHandler { viewModel.advanceTutorialStep() }
            } else if (viewModel.currentScreenView != "dashboard") {
                BackHandler { viewModel.currentScreenView = "dashboard" }
            } else if (viewModel.isPlusPopupVisible) {
                BackHandler { viewModel.isPlusPopupVisible = false }
            } else if (viewModel.isRewardsPopupVisible) {
                BackHandler { viewModel.isRewardsPopupVisible = false }
            } else {
                BackHandler { viewModel.showExitConfirmationDialog = true }
            }

            val window = (context as? Activity)?.window
            DisposableEffect(viewModel.keepScreenAwake) {
                if (viewModel.keepScreenAwake) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && viewModel.isTimerRunning) {
                        showSessionWarning(stableAppContext)
                        viewModel.handleSessionInterruptedAbruptly()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = backgroundWhite) {
                Box(modifier = Modifier.fillMaxSize()) {

                    if (showStartupSplash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(textBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .graphicsLayer(
                                        alpha = startupAlpha.value,
                                        scaleX = startupScale.value,
                                        scaleY = startupScale.value
                                    )
                            ) {
                                Text(
                                    text = "CLEARSPACE // CORE",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = backgroundWhite,
                                    letterSpacing = 4.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.size(32.dp, 2.dp).background(backgroundWhite))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "INITIALIZING ARCHITECTURE MATRIX v4.2",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = subTextGray,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    } else if (!viewModel.isProfileCreated) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "Initialize\nFocus\nTerminal.", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textBlack, lineHeight = 48.sp, letterSpacing = (-1.5).sp)
                            Spacer(modifier = Modifier.height(48.dp))

                            Text(text = "IDENTITY / NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = viewModel.nameInput,
                                onValueChange = { viewModel.nameInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter full name...", color = subTextGray, fontSize = 15.sp) },
                                textStyle = TextStyle(color = textBlack, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textBlack, unfocusedBorderColor = lineBorderLight),
                                shape = RoundedCornerShape(0.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(28.dp))

                            Text(text = "CORE FOCUS DOMAIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = viewModel.domainInput,
                                onValueChange = { viewModel.domainInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. Computer Science", color = subTextGray, fontSize = 15.sp) },
                                textStyle = TextStyle(color = textBlack, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textBlack, unfocusedBorderColor = lineBorderLight),
                                shape = RoundedCornerShape(0.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(54.dp))

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.initializeProfile()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, textBlack),
                                colors = ButtonDefaults.buttonColors(containerColor = textBlack),
                                shape = RoundedCornerShape(0.dp),
                                enabled = viewModel.nameInput.isNotBlank() && viewModel.domainInput.isNotBlank()
                            ) {
                                Text(text = "COMMIT PROFILE", color = backgroundWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (viewModel.currentScreenView == "dashboard") {
                                    Text(
                                        text = "ANALYTICS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textBlack,
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                diagCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                            }
                                            .clickable(enabled = !isTut) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.currentScreenView = "stats"
                                            }
                                            .border(1.dp, textBlack)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                vaultCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                            }
                                            .clickable(enabled = !isTut) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.isRewardsPopupVisible = !viewModel.isRewardsPopupVisible
                                            }
                                            .border(1.dp, textBlack)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = "VAULT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "[${viewModel.store.coins} CP]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = subTextGray)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "CLOSE ✕",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textBlack,
                                        modifier = Modifier.clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.currentScreenView = "dashboard"
                                        }.border(1.dp, textBlack).padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(36.dp))

                            AnimatedContent(targetState = viewModel.currentScreenView, label = "StarkCanvasTransition") { screen ->
                                when (screen) {
                                    "dashboard" -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, textBlack)
                                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                            ) {
                                                val faceState = if (viewModel.isTimerRunning) "(⚡_⚡) CORE_RUNNING" else "(⚫_⚫) DECK STANDBY"
                                                val statusLabel = if (viewModel.isTimerRunning) "MATRIX CONTEXT ENFORCED" else "AWAITING TARGET SIGNAL"
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = faceState, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 0.5.sp)
                                                    Text(text = statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = subTextGray, letterSpacing = 1.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(28.dp))

                                            Text(text = "Hello ${viewModel.registeredName.split(" ").firstOrNull() ?: ""}.", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = (-2).sp, lineHeight = 48.sp)
                                            Text(text = "Focus target instance initialized.", fontSize = 14.sp, color = subTextGray, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))

                                            Spacer(modifier = Modifier.height(44.dp))

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coords ->
                                                        timerCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                    }
                                                    .clickable(enabled = !isTut) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.toggleTimer()
                                                    }
                                                    .padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                Text(
                                                    text = "${(viewModel.timeLeft / 60).toString().padStart(2, '0')}:${(viewModel.timeLeft % 60).toString().padStart(2, '0')}",
                                                    fontSize = 94.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textBlack,
                                                    letterSpacing = (-5).sp,
                                                    lineHeight = 90.sp
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = if (viewModel.isTimerRunning) "ACTIVE RUNNING // TAP TO PAUSE" else "TERMINAL IDLE // TAP TO START", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.sp)
                                                    Text(
                                                        text = "[ ${viewModel.activeCategory.uppercase()} ]",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = subTextGray,
                                                        modifier = Modifier.clickable(enabled = !isTut) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.cycleCategory()
                                                        }
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Divider(color = textBlack, thickness = 2.dp)
                                            Spacer(modifier = Modifier.height(28.dp))

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coords ->
                                                        bossCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                    }
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = "BARRIER ENERGY LEVEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.sp)
                                                    Text(text = "${viewModel.bossHealth}% HP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    repeat(viewModel.dailyGoalSessions) { idx ->
                                                        val isDone = idx < viewModel.completedSessionsToday
                                                        Box(modifier = Modifier.weight(1f).height(14.dp).background(if (isDone) textBlack else Color.Transparent).border(1.dp, textBlack))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(36.dp))

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coords ->
                                                        inputCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                    }
                                            ) {
                                                Text(text = "CURRENT FOCUS TARGET OBJECTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = viewModel.focusObjectiveText,
                                                    onValueChange = { viewModel.focusObjectiveText = it },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    placeholder = { Text(text = "Enter active terminal focus task...", color = subTextGray, fontSize = 14.sp) },
                                                    textStyle = TextStyle(color = textBlack, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textBlack, unfocusedBorderColor = lineBorderLight),
                                                    shape = RoundedCornerShape(0.dp),
                                                    singleLine = true,
                                                    enabled = !viewModel.isTimerRunning && !isTut
                                                )
                                            }
                                        }
                                    }
                                    "profile" -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(text = viewModel.registeredName, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = (-1.5).sp)
                                            Text(text = viewModel.registeredDomain, fontSize = 14.sp, color = subTextGray, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(40.dp))

                                            Text(text = "DAILY TARGET QUOTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Divider(color = textBlack, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${viewModel.dailyGoalSessions} Blocks Required", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Box(modifier = Modifier.size(34.dp).border(1.dp, textBlack).clickable { if (viewModel.dailyGoalSessions > 1) viewModel.dailyGoalSessions-- }, contentAlignment = Alignment.Center) { Text("−", color = textBlack, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                                    Box(modifier = Modifier.size(34.dp).border(1.dp, textBlack).clickable { viewModel.dailyGoalSessions++ }, contentAlignment = Alignment.Center) { Text("+", color = textBlack, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(36.dp))
                                            Text(text = "ACTIVE REALM TAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Divider(color = textBlack, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                viewModel.categories.forEach { tag ->
                                                    Row(modifier = Modifier.fillMaxWidth().border(1.dp, lineBorderLight).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = "#${tag.uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                        Text(text = "[REMOVE]", color = textBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { viewModel.removeCustomTag(tag) })
                                                    }
                                                }
                                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = viewModel.newTagInput,
                                                        onValueChange = { viewModel.newTagInput = it },
                                                        modifier = Modifier.weight(1f),
                                                        placeholder = { Text("New tag...", color = subTextGray, fontSize = 13.sp) },
                                                        textStyle = TextStyle(color = textBlack, fontSize = 13.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textBlack, unfocusedBorderColor = lineBorderLight),
                                                        shape = RoundedCornerShape(0.dp),
                                                        singleLine = true
                                                    )
                                                    Box(modifier = Modifier.border(1.dp, textBlack).clickable { viewModel.addCustomTag() }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                                        Text(text = "ADD", color = textBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(40.dp))
                                            Text(text = "SYSTEM CONTROLLERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Divider(color = textBlack, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                val list = listOf(
                                                    MainActivityControlSetting("AI Biometric Tracking", "Toggle ML vision tracking layers", viewModel.isFaceTrackingEnabled) { viewModel.isFaceTrackingEnabled = !viewModel.isFaceTrackingEnabled },
                                                    MainActivityControlSetting("Strict App Lock", "Force verification arrays on boot", viewModel.strictSplashLock) { viewModel.strictSplashLock = !viewModel.strictSplashLock },
                                                    MainActivityControlSetting("Keep Device Awake", "Force lock screen sleeping cycles", viewModel.keepScreenAwake) { viewModel.keepScreenAwake = !viewModel.keepScreenAwake }
                                                )
                                                list.forEach { item ->
                                                    val animatedThumbOffset by animateFloatAsState(
                                                        targetValue = if (item.state) 22f else 0f,
                                                        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
                                                    )
                                                    val animatedSwitchColor by animateColorAsState(
                                                        targetValue = if (item.state) Color(0xFFEF4444) else textBlack,
                                                        animationSpec = tween(durationMillis = 350)
                                                    )

                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Column {
                                                            Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                            Text(text = item.subtitle, fontSize = 12.sp, color = subTextGray)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .width(46.dp)
                                                                .height(24.dp)
                                                                .border(1.dp, textBlack)
                                                                .clickable { item.onToggle() }
                                                                .padding(3.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .offset(x = animatedThumbOffset.dp)
                                                                    .size(16.dp)
                                                                    .background(animatedSwitchColor)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(44.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f).border(1.dp, textBlack).clickable {
                                                    val data = "Objective: ${viewModel.focusObjectiveText}, Sessions: ${viewModel.completedSessionsToday}"
                                                    val intent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, data)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Export Logs"))
                                                }.padding(14.dp), contentAlignment = Alignment.Center) { Text("EXPORT WORK LOGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack) }
                                                Box(modifier = Modifier.weight(1f).background(textBlack).clickable {
                                                    viewModel.resetProfile(context)
                                                }.padding(14.dp), contentAlignment = Alignment.Center) { Text("WIPE INSTANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = backgroundWhite) }
                                            }
                                        }
                                    }
                                    "stats" -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(text = "Analytics.", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = (-1.5).sp)
                                            Spacer(modifier = Modifier.height(32.dp))
                                            listOf(
                                                "SESSIONS COMPLETED" to "${viewModel.completedSessionsToday} BLOCKS",
                                                "COMPLIANCE INDEX" to "94.2%",
                                                "STREAK REPLICATION" to "${viewModel.streakCount} DAYS CONTINUOUS"
                                            ).forEach { item ->
                                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                                    Text(text = item.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subTextGray, letterSpacing = 1.sp)
                                                    Text(text = item.second, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = (-0.5).sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Divider(color = lineBorderLight, thickness = 1.dp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(32.dp))
                                            Text(text = "HISTORY LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            if (viewModel.sessionHistory.isEmpty()) {
                                                Text(text = "No active session history found in storage.", fontSize = 12.sp, color = subTextGray)
                                            } else {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    viewModel.sessionHistory.forEach { session ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .border(1.dp, lineBorderLight)
                                                                .padding(12.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column {
                                                                Text(text = session.timestamp, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                                Text(text = session.taskName, fontSize = 11.sp, color = subTextGray)
                                                            }
                                                            Text(text = session.duration, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(140.dp))
                        }

                        if (viewModel.isProfileCreated && viewModel.currentScreenView in listOf("dashboard", "profile", "stats")) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(backgroundWhite)
                                    .padding(horizontal = 28.dp, vertical = 24.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("dashboard" to "HOME", "profile" to "PROFILE").forEach { pair ->
                                            val sel = viewModel.currentScreenView == pair.first
                                            Box(
                                                modifier = Modifier
                                                    .border(1.dp, if (sel) textBlack else lineBorderLight)
                                                    .background(if (sel) textBlack else Color.Transparent)
                                                    .clickable(enabled = !isTut) { viewModel.currentScreenView = pair.first }
                                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                            ) {
                                                Text(text = pair.second, color = if (sel) backgroundWhite else textBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .onGloballyPositioned { coords ->
                                                actionCoords = Pair(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                            }
                                            .background(textBlack)
                                            .clickable(enabled = !isTut) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.isPlusPopupVisible = !viewModel.isPlusPopupVisible
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "+", color = backgroundWhite, fontSize = 22.sp, fontWeight = FontWeight.Light)
                                    }
                                }
                            }
                        }

                        if (viewModel.isProfileCreated) {
                            AnimatedVisibility(
                                visible = isTut,
                                enter = fadeIn(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(200)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val pos = when (tutStep) {
                                    1 -> diagCoords; 2 -> vaultCoords; 3 -> timerCoords;
                                    4 -> bossCoords; 5 -> inputCoords; 6 -> actionCoords; else -> null
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(alpha = 0.99f)
                                        .drawBehind {
                                            drawRect(Color.Black.copy(alpha = 0.85f))
                                            if (pos != null && pos.second.width > 0) {
                                                val xPx = pos.first.x - 7.dp.toPx()
                                                val yPx = pos.first.y - 7.dp.toPx()
                                                val widthPx = pos.second.width + 14.dp.toPx()
                                                val heightPx = pos.second.height + 14.dp.toPx()

                                                drawRect(
                                                    color = Color.Transparent,
                                                    topLeft = Offset(xPx, yPx),
                                                    size = Size(widthPx, heightPx),
                                                    blendMode = BlendMode.Clear
                                                )
                                            }
                                        }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.advanceTutorialStep()
                                        }
                                ) {
                                    if (pos != null && pos.second.width > 0) {
                                        with(density) {
                                            val xDp = pos.first.x.toDp() - 7.dp
                                            val yDp = pos.first.y.toDp() - 7.dp
                                            val widthDp = pos.second.width.toDp() + 14.dp
                                            val heightDp = pos.second.height.toDp() + 14.dp

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = xDp, y = yDp)
                                                    .size(width = widthDp, height = heightDp)
                                                    .border(2.dp, Color.White)
                                            )

                                            val textModifier = if (tutStep == 6) {
                                                Modifier
                                                    .offset(y = yDp - 230.dp)
                                                    .padding(horizontal = 28.dp)
                                            } else {
                                                Modifier
                                                    .offset(y = yDp + heightDp + 20.dp)
                                                    .padding(horizontal = 28.dp)
                                            }

                                            val tutTitle = when (tutStep) {
                                                1 -> "01 // FOCUS DIAGNOSTICS"
                                                2 -> "02 // REWARD CREDIT VAULT"
                                                3 -> "03 // KINETIC COUNTDOWN CORE"
                                                4 -> "04 // REALM BARRIER BOSS MATRIX"
                                                5 -> "05 // INTENT TARGET REGISTER"
                                                6 -> "06 // UTILITY QUICK COMMAND PANEL"
                                                else -> ""
                                            }

                                            val tutDesc = when (tutStep) {
                                                1 -> "Navigate to your metrics and analytics view. Review compliance rates and continuous session diagnostics history maps instantly."
                                                2 -> "Displays your accrued focus point balance assets. Gained credits are stored and tracking can be used to redeem work resting breaks."
                                                3 -> "Your main execution countdown vector window. Tapping inside this area fires up or breaks your active session block runtime loop."
                                                4 -> "Gamified task state loops. Completing metrics inflicts layout damage to the active boss instance; abandoning intervals drops parameters."
                                                5 -> "Declare your explicit focus target text string here before activating the countdown execution systems loop framework."
                                                6 -> "Tap the plus layout asset right below to open your workspace parameters control deck, set durations, tags, or tracking systems."
                                                else -> ""
                                            }

                                            Column(modifier = textModifier) {
                                                Text(text = tutTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = backgroundWhite, letterSpacing = 1.5.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(text = tutDesc, fontSize = 14.sp, color = lineBorderLight, lineHeight = 20.sp)
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 96.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = if (tutStep < 6) "TAP ANYWHERE TO ADVANCE // STEP $tutStep OF 6" else "TAP ANYWHERE TO CONCLUDE INITIALIZATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subTextGray, letterSpacing = 0.5.sp)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(text = "SKIP TUTORIAL ✕", color = backgroundWhite, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.skipTutorialSequence() }.border(1.dp, backgroundWhite).padding(horizontal = 16.dp, vertical = 8.dp))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = viewModel.isPlusPopupVisible, enter = fadeIn(), exit = fadeOut()) {
                                Box(modifier = Modifier.fillMaxSize().background(textBlack.copy(alpha = 0.4f)).clickable { viewModel.isPlusPopupVisible = false }) {
                                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(backgroundWhite).border(BorderStroke(2.dp, textBlack)).padding(28.dp).clickable(enabled = false) {}) {
                                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                            Text(text = "COMMAND OVERRIDE PANEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Column {
                                                Text(text = "TIMER INTERVAL QUANTUM", fontSize = 11.sp, color = subTextGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                                val r1 = listOf(15 to "15M", 25 to "25M", 45 to "45M")
                                                val r2 = listOf(60 to "1.0H", 90 to "1.5H", 120 to "2.0H")
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        r1.forEach { pair ->
                                                            val sel = viewModel.timeLeft == pair.first * 60
                                                            Box(modifier = Modifier.weight(1f).background(if (sel) textBlack else Color.Transparent).border(1.dp, textBlack).clickable { if (!viewModel.isTimerRunning) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.timeLeft = pair.first * 60 } }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(text = pair.second, color = if (sel) backgroundWhite else textBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        r2.forEach { pair ->
                                                            val sel = viewModel.timeLeft == pair.first * 60
                                                            Box(modifier = Modifier.weight(1f).background(if (sel) textBlack else Color.Transparent).border(1.dp, textBlack).clickable { if (!viewModel.isTimerRunning) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.timeLeft = pair.first * 60 } }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(text = pair.second, color = if (sel) backgroundWhite else textBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                                        }
                                                    }
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(text = "HARDWARE UTILITY INTERCEPTS", fontSize = 11.sp, color = subTextGray, fontWeight = FontWeight.Bold)
                                                val items = listOf(
                                                    "Ambient Frequency Node" to viewModel.isAmbientAudioActive to { viewModel.isAmbientAudioActive = !viewModel.isAmbientAudioActive },
                                                    "Force Awake Framework" to viewModel.keepScreenAwake to { viewModel.keepScreenAwake = !viewModel.keepScreenAwake },
                                                    "System Notification Pipe" to viewModel.notificationsEnabled to { viewModel.notificationsEnabled = !viewModel.notificationsEnabled }
                                                )
                                                items.forEach { item ->
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = item.first.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textBlack)
                                                        Box(modifier = Modifier.size(32.dp).border(1.dp, textBlack).clickable { item.second() }.padding(4.dp), contentAlignment = Alignment.Center) { if (item.first.second) Box(modifier = Modifier.fillMaxSize().background(textBlack)) }
                                                    }
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f).border(1.dp, textBlack).clickable { viewModel.focusObjectiveText = ""; viewModel.isPlusPopupVisible = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("CLEAR TASK", color = textBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                                Box(modifier = Modifier.weight(1.2f).background(textBlack).clickable { if (viewModel.focusObjectiveText.isNotBlank()) viewModel.toggleTimer(); viewModel.isPlusPopupVisible = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("ENGAGE TERMINAL", color = backgroundWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = viewModel.isRewardsPopupVisible, enter = fadeIn(), exit = fadeOut()) {
                                Box(modifier = Modifier.fillMaxSize().background(textBlack.copy(alpha = 0.4f)).clickable { viewModel.isRewardsPopupVisible = false }) {
                                    Box(modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp).fillMaxWidth().background(backgroundWhite).border(BorderStroke(2.dp, textBlack)).padding(24.dp).clickable(enabled = false) {}) {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(text = "REWARD VAULT MATRIX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.5.sp)
                                            Text(text = "CREDIT ASSETS: ${viewModel.store.coins} CP", fontSize = 14.sp, color = subTextGray, fontWeight = FontWeight.Bold)
                                            listOf("15-Min System Rest Block" to "5 CP", "High-Performance Focus Tag" to "12 CP").forEach { pair ->
                                                Row(modifier = Modifier.fillMaxWidth().border(1.dp, lineBorderLight).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = pair.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textBlack, modifier = Modifier.weight(1f))
                                                    Box(modifier = Modifier.border(1.dp, textBlack).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(text = pair.second, color = textBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (viewModel.showExitConfirmationDialog) {
                                Dialog(onDismissRequest = { viewModel.showExitConfirmationDialog = false }) {
                                    Box(modifier = Modifier.fillMaxWidth().background(backgroundWhite).border(2.dp, textBlack).padding(24.dp)) {
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(text = "TERMINATE INSTANCE?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textBlack, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(text = "Are you sure you want to completely terminate the running workspace task manager execution thread?", fontSize = 13.sp, color = subTextGray)
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f).border(1.dp, textBlack).clickable { viewModel.showExitConfirmationDialog = false }.padding(12.dp), contentAlignment = Alignment.Center) { Text("CANCEL", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                                Box(modifier = Modifier.weight(1f).background(textBlack).clickable { finish() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("KILL THREAD", color = backgroundWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}