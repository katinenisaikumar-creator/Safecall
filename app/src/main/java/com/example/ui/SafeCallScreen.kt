package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ParentMessage
import com.example.service.AutoAnswerUiState
import com.example.ui.theme.ActiveEmerald
import com.example.ui.theme.ActiveEmeraldContainer
import com.example.ui.theme.ActiveEmeraldLight
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MidnightBlue
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.SoftBlueTint
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PresetStatuses = listOf(
    "Lab Session lo unna",
    "Class lo unna",
    "Exam Hall lo unna",
    "Library lo unna",
    "Seminar Presentation"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeCallScreen(
    isCollegeModeOn: Boolean,
    studentStatus: String,
    messages: List<ParentMessage>,
    pipelineState: AutoAnswerUiState,
    systemHealth: SystemHealthState,
    onToggleCollegeMode: () -> Unit,
    onUpdateStudentStatus: (String) -> Unit,
    onStartFakeCallTest: (callerName: String, callerNumber: String) -> Unit,
    onCancelActiveCallFlow: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearAllMessages: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    var showPermissionsSheet by remember { mutableStateOf(false) }
    var showFakeCallDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedScreen = maxWidth >= 840.dp

            if (isExpandedScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        HeroHeaderSection(
                            isCollegeModeOn = isCollegeModeOn,
                            systemHealth = systemHealth,
                            onToggleCollegeMode = onToggleCollegeMode,
                            onOpenPermissions = { showPermissionsSheet = true }
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LivePipelineCard(
                                pipelineState = pipelineState,
                                onCancel = onCancelActiveCallFlow
                            )
                            StatusInputCard(
                                studentStatus = studentStatus,
                                onUpdateStatus = onUpdateStudentStatus
                            )
                            TestFakeCallCard(
                                isRunning = pipelineState !is AutoAnswerUiState.Idle &&
                                    pipelineState !is AutoAnswerUiState.Completed,
                                onOpenTestDialog = { showFakeCallDialog = true }
                            )
                        }
                    }

                    MessagesPane(
                        messages = messages,
                        onDeleteMessage = onDeleteMessage,
                        onRequestClearAll = { showClearAllDialog = true },
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "hero_header") {
                        HeroHeaderSection(
                            isCollegeModeOn = isCollegeModeOn,
                            systemHealth = systemHealth,
                            onToggleCollegeMode = onToggleCollegeMode,
                            onOpenPermissions = { showPermissionsSheet = true }
                        )
                    }

                    item(key = "live_pipeline") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LivePipelineCard(
                                pipelineState = pipelineState,
                                onCancel = onCancelActiveCallFlow,
                                modifier = Modifier.widthIn(max = 600.dp)
                            )
                        }
                    }

                    item(key = "status_input") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StatusInputCard(
                                studentStatus = studentStatus,
                                onUpdateStatus = onUpdateStudentStatus,
                                modifier = Modifier.widthIn(max = 600.dp)
                            )
                        }
                    }

                    item(key = "test_fake_call") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TestFakeCallCard(
                                isRunning = pipelineState !is AutoAnswerUiState.Idle &&
                                    pipelineState !is AutoAnswerUiState.Completed,
                                onOpenTestDialog = { showFakeCallDialog = true },
                                modifier = Modifier.widthIn(max = 600.dp)
                            )
                        }
                    }

                    item(key = "messages_header") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MessagesHeaderRow(
                                count = messages.size,
                                onClearAllClick = { showClearAllDialog = true },
                                modifier = Modifier.widthIn(max = 600.dp)
                            )
                        }
                    }

                    if (messages.isEmpty()) {
                        item(key = "empty_messages") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyMessagesCard(modifier = Modifier.widthIn(max = 600.dp))
                            }
                        }
                    } else {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ParentMessageCard(
                                    message = message,
                                    onDelete = { onDeleteMessage(message.id) },
                                    modifier = Modifier.widthIn(max = 600.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPermissionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PermissionsBottomSheetContent(
                systemHealth = systemHealth,
                onRequestPermissions = onRequestPermissions,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onDismiss = { showPermissionsSheet = false }
            )
        }
    }

    if (showFakeCallDialog) {
        FakeCallSetupDialog(
            isCollegeModeOn = isCollegeModeOn,
            studentStatus = studentStatus,
            onDismiss = { showFakeCallDialog = false },
            onLaunchTest = { callerName, callerNumber ->
                showFakeCallDialog = false
                onStartFakeCallTest(callerName, callerNumber)
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = DangerRed
                )
            },
            title = { Text(stringResource(R.string.clear_dialog_title)) },
            text = { Text(stringResource(R.string.clear_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onClearAllMessages()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text(stringResource(R.string.confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun HeroHeaderSection(
    isCollegeModeOn: Boolean,
    systemHealth: SystemHealthState,
    onToggleCollegeMode: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val headerBrush = Brush.verticalGradient(
        colors = listOf(DeepNavy, MidnightBlue, RoyalBlue)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = headerBrush,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: Logo + Tagline + Permissions Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PureWhite.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.25f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = stringResource(R.string.app_name),
                                tint = SkyBlueAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = PureWhite
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.78f)
                        )
                    }
                }

                // Permissions & Battery Health Pill
                val badgeBg = if (systemHealth.isFullyReady) {
                    ActiveEmerald.copy(alpha = 0.22f)
                } else {
                    WarningAmber.copy(alpha = 0.28f)
                }
                val badgeBorder = if (systemHealth.isFullyReady) {
                    ActiveEmeraldLight.copy(alpha = 0.7f)
                } else {
                    Color(0xFFFBBF24).copy(alpha = 0.8f)
                }
                val badgeText = if (systemHealth.isFullyReady) {
                    "Ready"
                } else {
                    val missing = systemHealth.missingPermissionsCount +
                        if (systemHealth.isBatteryUnrestricted) 0 else 1
                    "Setup ($missing)"
                }

                Surface(
                    onClick = onOpenPermissions,
                    shape = RoundedCornerShape(50.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeBorder),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("permissions_sheet_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (systemHealth.isFullyReady) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Security
                            },
                            contentDescription = stringResource(R.string.permissions_setup),
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = PureWhite
                        )
                    }
                }
            }

            // BIG COLLEGE MODE ON / OFF BUTTON
            BigCollegeModeButton(
                isCollegeModeOn = isCollegeModeOn,
                onClick = onToggleCollegeMode
            )
        }
    }
}

@Composable
private fun BigCollegeModeButton(
    isCollegeModeOn: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isCollegeModeOn) ActiveEmerald else PureWhite,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "college_mode_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isCollegeModeOn) PureWhite else DeepNavy,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "college_mode_fg"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCollegeModeOn) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        shadowElevation = 8.dp,
        border = if (isCollegeModeOn) {
            BorderStroke(1.5.dp, PureWhite.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, SoftBlueTint)
        },
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("college_mode_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isCollegeModeOn) {
                                PureWhite.copy(alpha = 0.22f)
                            } else {
                                RoyalBlue.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = if (isCollegeModeOn) PureWhite else RoyalBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isCollegeModeOn) {
                            stringResource(R.string.college_mode_on)
                        } else {
                            stringResource(R.string.college_mode_off)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor
                    )
                    Text(
                        text = if (isCollegeModeOn) {
                            stringResource(R.string.college_mode_on_desc)
                        } else {
                            stringResource(R.string.college_mode_off_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(50.dp),
                color = if (isCollegeModeOn) PureWhite else DeepNavy
            ) {
                Text(
                    text = if (isCollegeModeOn) "ACTIVE" else "TAP ON",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCollegeModeOn) ActiveEmerald else PureWhite,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LivePipelineCard(
    pipelineState: AutoAnswerUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pipelineState !is AutoAnswerUiState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        val (title, subtitle, progress, icon) = when (pipelineState) {
            is AutoAnswerUiState.RingingCountdown -> Quad(
                "Incoming Call: ${pipelineState.callerDisplay}",
                "Auto-lifting call in ${pipelineState.secondsRemaining}s...",
                (4 - pipelineState.secondsRemaining) / 3f,
                Icons.Filled.PhoneInTalk
            )

            is AutoAnswerUiState.SpeakingGreeting -> Quad(
                "Speaking to ${pipelineState.callerDisplay}",
                "\"${pipelineState.spokenText}\"",
                0.45f,
                Icons.Filled.VolumeUp
            )

            is AutoAnswerUiState.ListeningForMessage -> Quad(
                "Listening (${pipelineState.secondsRemaining}s left)...",
                pipelineState.partialTranscript.ifBlank { "Speak parent's message now after the beep..." },
                (7 - pipelineState.secondsRemaining) / 6f,
                Icons.Filled.Mic
            )

            is AutoAnswerUiState.SpeakingClosing -> Quad(
                "Saving & Closing Call",
                "\"Thank you, message noted. He will call you back when free.\"",
                0.92f,
                Icons.Filled.RecordVoiceOver
            )

            is AutoAnswerUiState.Completed -> Quad(
                "Message Noted from ${pipelineState.callerDisplay}",
                pipelineState.capturedMessage,
                1f,
                Icons.Filled.CheckCircle
            )

            AutoAnswerUiState.Idle -> Quad("", "", 0f, Icons.Filled.Call)
        }

        val isSimulated = when (pipelineState) {
            is AutoAnswerUiState.RingingCountdown -> pipelineState.isSimulated
            is AutoAnswerUiState.SpeakingGreeting -> pipelineState.isSimulated
            is AutoAnswerUiState.ListeningForMessage -> pipelineState.isSimulated
            is AutoAnswerUiState.SpeakingClosing -> pipelineState.isSimulated
            is AutoAnswerUiState.Completed -> pipelineState.isSimulated
            AutoAnswerUiState.Idle -> false
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DeepNavy),
            border = BorderStroke(1.5.dp, SkyBlueAccent.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_pipeline_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SkyBlueAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = SkyBlueAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            if (isSimulated) {
                                Text(
                                    text = "TEST FAKE CALL SIMULATION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SkyBlueAccent
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                        }
                    }

                    if (pipelineState !is AutoAnswerUiState.Completed) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_active_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "Cancel call simulation",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.9f)
                )

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    color = SkyBlueAccent,
                    trackColor = PureWhite.copy(alpha = 0.16f)
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun StatusInputCard(
    studentStatus: String,
    onUpdateStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectivePreview = studentStatus.trim().ifEmpty {
        stringResource(R.string.default_student_status)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.status_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = studentStatus,
                onValueChange = onUpdateStatus,
                placeholder = {
                    Text(stringResource(R.string.status_placeholder))
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_status_input")
            )

            // Quick-tap preset chips for fast switching between classes/labs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetStatuses.forEach { preset ->
                    val selected = studentStatus.trim() == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateStatus(preset) },
                        label = { Text(preset) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalBlue,
                            selectedLabelColor = PureWhite
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            // Live TTS preview box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Voice Reply: \"Hello, he is currently in $effectivePreview. He is busy. Please tell your message after beep...\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TestFakeCallCard(
    isRunning: Boolean,
    onOpenTestDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.test_fake_call_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.test_fake_call_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onOpenTestDialog,
                enabled = !isRunning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlue,
                    contentColor = PureWhite
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("test_fake_call_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneCallback,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TEST CALL",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun MessagesPane(
    messages: List<ParentMessage>,
    onDeleteMessage: (String) -> Unit,
    onRequestClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MessagesHeaderRow(
            count = messages.size,
            onClearAllClick = onRequestClearAll
        )
        if (messages.isEmpty()) {
            EmptyMessagesCard()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = messages, key = { it.id }) { msg ->
                    ParentMessageCard(
                        message = msg,
                        onDelete = { onDeleteMessage(msg.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesHeaderRow(
    count: Int,
    onClearAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.messages_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = CircleShape,
                color = RoyalBlue
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        if (count > 0) {
            TextButton(
                onClick = onClearAllClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_all_messages_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.clear_all),
                    color = DangerRed,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun EmptyMessagesCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_messages_1795109499718),
                contentDescription = stringResource(R.string.empty_messages_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = stringResource(R.string.empty_messages_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_messages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParentMessageCard(
    message: ParentMessage,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(message.timestampMillis) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestampMillis))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("message_card_${message.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = RoyalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = message.callerDisplay,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (message.isSimulated) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SoftBlueTint
                                ) {
                                    Text(
                                        text = "TEST",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RoyalBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (message.callerNumber.isNotBlank() &&
                            message.callerNumber != message.callerDisplay
                        ) {
                            Text(
                                text = message.callerNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_message_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_message),
                        tint = DangerRed.copy(alpha = 0.85f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "During: ${message.studentStatusAtCall}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoyalBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FakeCallSetupDialog(
    isCollegeModeOn: Boolean,
    studentStatus: String,
    onDismiss: () -> Unit,
    onLaunchTest: (callerName: String, callerNumber: String) -> Unit
) {
    val presetCallers = listOf(
        "Amma" to "+91 98765 43210",
        "Nanna" to "+91 98480 12345",
        "Home Landline" to "040-23456789"
    )
    var selectedCaller by remember { mutableStateOf("Amma") }
    var customNumber by remember { mutableStateOf("+91 98765 43210") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.PhoneCallback,
                contentDescription = null,
                tint = RoyalBlue
            )
        },
        title = { Text("Test Fake Parent Call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Runs the real 3s ring delay -> TTS greeting (\"${studentStatus.ifBlank { "Lab Session lo unna" }}\") -> Beep -> 6s Speech-to-Text mic capture -> Message save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isCollegeModeOn) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmberContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "College Mode is OFF. Real incoming calls won't auto-answer until you turn it ON, but this test simulation still runs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    presetCallers.forEach { (name, num) ->
                        FilterChip(
                            selected = selectedCaller == name,
                            onClick = {
                                selectedCaller = name
                                customNumber = num
                            },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlue,
                                selectedLabelColor = PureWhite
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = selectedCaller,
                    onValueChange = { selectedCaller = it },
                    label = { Text("Caller Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunchTest(selectedCaller, customNumber) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                modifier = Modifier.testTag("confirm_fake_call_button")
            ) {
                Text("Start 3s Ring Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PermissionsBottomSheetContent(
    systemHealth: SystemHealthState,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.permissions_setup),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Grant these permissions and set Battery Optimization to Unrestricted so SafeCall works reliably even when the screen is locked or app is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Battery Optimization Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (systemHealth.isBatteryUnrestricted) {
                ActiveEmeraldContainer.copy(alpha = 0.45f)
            } else {
                WarningAmberContainer
            },
            border = BorderStroke(
                1.dp,
                if (systemHealth.isBatteryUnrestricted) ActiveEmerald else WarningAmber
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (systemHealth.isBatteryUnrestricted) {
                            Icons.Filled.BatteryChargingFull
                        } else {
                            Icons.Filled.BatteryAlert
                        },
                        contentDescription = null,
                        tint = if (systemHealth.isBatteryUnrestricted) ActiveEmerald else WarningAmber
                    )
                    Column {
                        Text(
                            text = "Battery Optimization",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF0B1736)
                        )
                        Text(
                            text = if (systemHealth.isBatteryUnrestricted) {
                                "Unrestricted — SafeCall stays active in background"
                            } else {
                                "Restricted — Tap Allow so Android won't kill SafeCall"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155)
                        )
                    }
                }

                if (!systemHealth.isBatteryUnrestricted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onRequestBatteryOptimization,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("battery_unrestricted_button")
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        HorizontalDivider()

        systemHealth.permissions.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (item.isGranted) ActiveEmeraldContainer else WarningAmberContainer
                ) {
                    Text(
                        text = if (item.isGranted) "Granted" else "Needed",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.isGranted) ActiveEmerald else WarningAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text("Close")
            }

            Button(
                onClick = onRequestPermissions,
                enabled = !systemHealth.allPermissionsGranted,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                modifier = Modifier
                    .weight(1.4f)
                    .minimumInteractiveComponentSize()
                    .testTag("grant_all_permissions_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (systemHealth.allPermissionsGranted) "All Granted" else "Grant Permissions"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
