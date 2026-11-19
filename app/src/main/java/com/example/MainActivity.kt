package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SafeCallScreen
import com.example.ui.SafeCallViewModel
import com.example.ui.theme.SafeCallTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SafeCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            val isCollegeModeOn by viewModel.isCollegeModeOn.collectAsStateWithLifecycle()
            val studentStatus by viewModel.studentStatus.collectAsStateWithLifecycle()
            val messages by viewModel.messages.collectAsStateWithLifecycle()
            val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()
            val systemHealth by viewModel.systemHealth.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.refreshSystemHealth()
            }

            // Refresh permissions & battery optimization status whenever user returns to app
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshSystemHealth()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Prompt missing runtime permissions on initial launch if not yet granted
            LaunchedEffect(Unit) {
                if (!systemHealth.allPermissionsGranted) {
                    permissionLauncher.launch(viewModel.requiredRuntimePermissions())
                }
            }

            SafeCallTheme {
                SafeCallScreen(
                    isCollegeModeOn = isCollegeModeOn,
                    studentStatus = studentStatus,
                    messages = messages,
                    pipelineState = pipelineState,
                    systemHealth = systemHealth,
                    onToggleCollegeMode = {
                        if (!isCollegeModeOn && !systemHealth.allPermissionsGranted) {
                            permissionLauncher.launch(viewModel.requiredRuntimePermissions())
                        }
                        viewModel.toggleCollegeMode()
                    },
                    onUpdateStudentStatus = { viewModel.updateStudentStatus(it) },
                    onStartFakeCallTest = { name, number ->
                        if (!systemHealth.allPermissionsGranted) {
                            permissionLauncher.launch(viewModel.requiredRuntimePermissions())
                        }
                        viewModel.startFakeCallTest(name, number)
                    },
                    onCancelActiveCallFlow = { viewModel.cancelActiveCallFlow() },
                    onDeleteMessage = { viewModel.deleteMessage(it) },
                    onClearAllMessages = { viewModel.clearAllMessages() },
                    onRequestPermissions = {
                        permissionLauncher.launch(viewModel.requiredRuntimePermissions())
                    },
                    onRequestBatteryOptimization = {
                        requestUnrestrictedBatteryOptimization()
                    }
                )
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestUnrestrictedBatteryOptimization() {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(requestIntent)
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
