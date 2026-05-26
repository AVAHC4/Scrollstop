package com.example.instadmguard.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.instadmguard.model.BlockMode
import com.example.instadmguard.model.DebugLogEntry
import com.example.instadmguard.util.AccessibilityUtils
import com.example.instadmguard.util.AppIntents
import com.example.instadmguard.util.TimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nowMillis by rememberTickerMillis()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var accessibilityEnabled by remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityEnabled = AccessibilityUtils.isServiceEnabled(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InstaDMGuard") },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Settings") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Debug") },
                )
            }

            when (selectedTab) {
                0 ->
                    SettingsTab(
                        uiState = uiState,
                        nowMillis = nowMillis,
                        accessibilityEnabled = accessibilityEnabled,
                        onOpenAccessibility = { AppIntents.openAccessibilitySettings(context) },
                        onOpenInstagram = {
                            if (!AppIntents.openInstagram(context)) {
                                Toast.makeText(context, "Instagram is not installed.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onMasterEnabledChanged = viewModel::setMasterEnabled,
                        onBlockModeChanged = viewModel::setBlockMode,
                        onGraceDurationChanged = viewModel::setGraceDurationSeconds,
                        onPause5Minutes = { viewModel.pauseForMinutes(5) },
                        onPause15Minutes = { viewModel.pauseForMinutes(15) },
                        onResumeProtection = viewModel::resumeProtection,
                        onDebugModeChanged = viewModel::setDebugMode,
                        onOverlayDismissibleChanged = viewModel::setOverlayDismissible,
                    )

                else ->
                    DebugTab(
                        uiState = uiState,
                        onClearLogs = viewModel::clearDebugLogs,
                    )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    uiState: MainUiState,
    nowMillis: Long,
    accessibilityEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenInstagram: () -> Unit,
    onMasterEnabledChanged: (Boolean) -> Unit,
    onBlockModeChanged: (BlockMode) -> Unit,
    onGraceDurationChanged: (Int) -> Unit,
    onPause5Minutes: () -> Unit,
    onPause15Minutes: () -> Unit,
    onResumeProtection: () -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onOverlayDismissibleChanged: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                uiState = uiState,
                nowMillis = nowMillis,
                accessibilityEnabled = accessibilityEnabled,
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Quick actions", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenAccessibility) {
                            Text("Accessibility settings")
                        }
                        Button(onClick = onOpenInstagram) {
                            Text("Open Instagram")
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Protection", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(
                        title = "Master protection",
                        subtitle = "Protection defaults to on and only watches Instagram.",
                        checked = uiState.settings.masterEnabled,
                        onCheckedChange = onMasterEnabledChanged,
                    )
                    StatusLine(
                        "Reels policy",
                        "Only DM-opened reels are allowed. Reels tab/button always blocks.",
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Block mode", style = MaterialTheme.typography.titleMedium)
                    BlockMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.settings.blockMode == mode,
                                onClick = { onBlockModeChanged(mode) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.title)
                                Text(
                                    when (mode) {
                                        BlockMode.OVERLAY_ONLY -> "Show a full-screen warning overlay."
                                        BlockMode.BACK_ONLY -> "Immediately navigate back out of the reel."
                                        BlockMode.OVERLAY_AND_BACK -> "Show the warning briefly and exit the reel."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (uiState.settings.blockMode == BlockMode.OVERLAY_ONLY) {
                        HorizontalDivider()
                        SwitchRow(
                            title = "Allow overlay dismiss button",
                            subtitle = "Only used for overlay-only mode.",
                            checked = uiState.settings.overlayDismissible,
                            onCheckedChange = onOverlayDismissibleChanged,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Timing", style = MaterialTheme.typography.titleMedium)
                    StepperRow(
                        title = "DM grace duration",
                        subtitle = "How long a DM-opened reel stays allowed before blocking again.",
                        value = "${uiState.settings.graceDurationSeconds} seconds",
                        onDecrement = {
                            onGraceDurationChanged(uiState.settings.graceDurationSeconds - 5)
                        },
                        onIncrement = {
                            onGraceDurationChanged(uiState.settings.graceDurationSeconds + 5)
                        },
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Pause protection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use short pauses when you explicitly want a temporary break from blocking.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPause5Minutes) {
                            Text("Pause 5 min")
                        }
                        Button(onClick = onPause15Minutes) {
                            Text("Pause 15 min")
                        }
                        TextButton(onClick = onResumeProtection) {
                            Text("Resume now")
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Debugging", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(
                        title = "Show debug logs",
                        subtitle = "Stores the latest detector/service events while this app process stays alive.",
                        checked = uiState.settings.debugMode,
                        onCheckedChange = onDebugModeChanged,
                    )
                }
            }
        }

    }
}

@Composable
private fun DebugTab(
    uiState: MainUiState,
    onClearLogs: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Detector view", style = MaterialTheme.typography.titleMedium)
                    StatusLine("Current screen", uiState.serviceStatus.lastDetectedScreen.displayName)
                    StatusLine("Decision", uiState.serviceStatus.lastDecision)
                    StatusLine(
                        "Detector summary",
                        uiState.serviceStatus.lastDetectorSummary.ifBlank { "No Instagram activity yet." },
                    )
                    StatusLine(
                        "Last event",
                        uiState.serviceStatus.lastEventSummary.ifBlank { "No recent accessibility event." },
                    )
                    TextButton(onClick = onClearLogs) {
                        Text("Clear logs")
                    }
                }
            }
        }

        item {
            Text("Recent event history", style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.debugLogs.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "No debug events yet. Turn on debug logging, then open Instagram and move through DMs/reels.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(uiState.debugLogs) { entry ->
                DebugLogCard(entry = entry)
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: MainUiState,
    nowMillis: Long,
    accessibilityEnabled: Boolean,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusLine("Accessibility enabled", if (accessibilityEnabled) "Yes" else "No")
            StatusLine(
                "Protection",
                when {
                    !uiState.settings.masterEnabled -> "Disabled"
                    uiState.settings.pauseUntilMillis > nowMillis -> "Paused"
                    else -> "Enabled"
                },
            )
            StatusLine(
                "Paused until",
                if (uiState.settings.pauseUntilMillis > nowMillis) {
                    TimeFormatter.formatDateTime(uiState.settings.pauseUntilMillis)
                } else {
                    "Not paused"
                },
            )
            StatusLine("Last detected screen", uiState.serviceStatus.lastDetectedScreen.displayName)
            StatusLine("Current decision", uiState.serviceStatus.lastDecision)
            StatusLine(
                "DM grace until",
                if (uiState.serviceStatus.dmGraceUntilMillis > nowMillis) {
                    TimeFormatter.formatDateTime(uiState.serviceStatus.dmGraceUntilMillis)
                } else {
                    "Inactive"
                },
            )
            if (uiState.settings.dailyLimitEnabled && !uiState.settings.allowDmOpenedReelsOnly) {
                StatusLine(
                    "Daily reels used",
                    "${TimeFormatter.formatDurationSeconds(uiState.serviceStatus.dailyLimitUsedSeconds)} / ${uiState.settings.dailyLimitMinutes} min",
                )
            }
        }
    }
}

@Composable
private fun DebugLogCard(entry: DebugLogEntry) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${TimeFormatter.formatTime(entry.timestampMillis)}  •  ${entry.screen.displayName}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDecrement) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(value, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onIncrement) {
                Text("+")
            }
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun rememberTickerMillis(): androidx.compose.runtime.State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
