package dev.sfpixel.hcdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.*
import dev.sfpixel.hcdashboard.handlers.*
import dev.sfpixel.hcdashboard.models.*
import dev.sfpixel.hcdashboard.ui.components.ActivityDetailDialog
import dev.sfpixel.hcdashboard.ui.components.SleepDetailDialog
import dev.sfpixel.hcdashboard.ui.theme.HCDashboardTheme
import dev.sfpixel.hcdashboard.ui.views.ActivitiesView
import dev.sfpixel.hcdashboard.ui.views.HistoryView
import dev.sfpixel.hcdashboard.ui.views.TodayView
import kotlinx.coroutines.launch
import java.time.*

class MainActivity : ComponentActivity() {

    private val handlers = listOf(
        WeightHandler, 
        BodyFatHandler,
        StepsHandler,
        SleepHandler, 
        RestingHeartRateHandler, 
        HeartRateVariabilityHandler,
        Vo2MaxHandler
    )
    
    private val permissions = handlers.map { 
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(it.recordType) 
    }.toSet() + 
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(ExerciseSessionRecord::class) +
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(HeartRateRecord::class) +
            androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val healthConnectClient = HealthConnectClient.getOrCreate(this)
        val viewModel = MainViewModel(healthConnectClient)

        setContent {
            HCDashboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthDashboard(viewModel, permissions)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboard(viewModel: MainViewModel, permissions: Set<String>) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val birthDate by userPreferences.birthDate.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    var isAuthorized by remember { mutableStateOf(false) }
    val selectedActivityState = remember { mutableStateOf<ExerciseSessionRecord?>(null) }
    val selectedSleepState = remember { mutableStateOf<SleepSessionRecord?>(null) }
    
    val showSettingsDialog = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            isAuthorized = true
        }
    }

    LaunchedEffect(isAuthorized, viewModel.historyPeriod, viewModel.historyOffset, viewModel.activityPeriod, viewModel.activityOffset, viewModel.selectedTab, birthDate) {
        val granted = viewModel.getHealthConnectClient().permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            isAuthorized = true
            
            when (viewModel.selectedTab) {
                DashboardTab.Today -> viewModel.loadTodayData(birthDate)
                DashboardTab.History -> viewModel.loadHistoryData(viewModel.historyPeriod, viewModel.historyOffset)
                DashboardTab.Activities -> viewModel.loadActivitiesData(viewModel.activityPeriod, viewModel.activityOffset)
            }
        }
    }

    LaunchedEffect(selectedActivityState.value) {
        selectedActivityState.value?.let { viewModel.loadActivityHeartRate(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Health Dashboard", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showSettingsDialog.value = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isAuthorized) {
            SecondaryTabRow(selectedTabIndex = viewModel.selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = viewModel.selectedTab == tab,
                        onClick = { viewModel.selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                when (viewModel.selectedTab) {
                    DashboardTab.Today -> {
                        TodayView(
                            viewModel.todaySteps, 
                            viewModel.lastNightSleepDuration, 
                            viewModel.todayRestingHeartRate, 
                            viewModel.hrv7DayAvg, 
                            viewModel.hrvHistoryAvg, 
                            viewModel.intensityMinutesWeek,
                            onSleepClick = { selectedSleepState.value = viewModel.lastNightSleepSession }
                        )
                    }
                    DashboardTab.History -> {
                        HistoryView(
                            periodType = viewModel.historyPeriod,
                            offset = viewModel.historyOffset,
                            onPeriodTypeChanged = { viewModel.loadHistoryData(it, viewModel.historyOffset) },
                            onOffsetChanged = { viewModel.loadHistoryData(viewModel.historyPeriod, it) },
                            isLoading = viewModel.isLoading,
                            weights = viewModel.weightsProcessed,
                            bodyFats = viewModel.bodyFatsProcessed,
                            stepsProcessed = viewModel.stepsProcessed,
                            sleepProcessed = viewModel.sleepProcessed,
                            restingHeartRateProcessed = viewModel.rhrProcessed,
                            hrvProcessed = viewModel.hrvProcessed,
                            vo2MaxProcessed = viewModel.vo2MaxProcessed
                        )
                    }
                    DashboardTab.Activities -> {
                        ActivitiesView(
                            periodType = viewModel.activityPeriod,
                            offset = viewModel.activityOffset,
                            onPeriodTypeChanged = { viewModel.loadActivitiesData(it, viewModel.activityOffset) },
                            onOffsetChanged = { viewModel.loadActivitiesData(viewModel.activityPeriod, it) },
                            isLoading = viewModel.isLoading,
                            exerciseSessions = viewModel.exerciseSessions,
                            onActivityClick = { selectedActivityState.value = it }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    scope.launch {
                        requestPermissionLauncher.launch(permissions)
                    }
                }) {
                    Text("Grant Permissions")
                }
            }
        }
    }

    if (showSettingsDialog.value) {
        var birthDateInput by remember { mutableStateOf(birthDate?.toString() ?: "") }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog.value = false },
            title = { Text("Settings") },
            text = {
                Column {
                    OutlinedTextField(
                        value = birthDateInput,
                        onValueChange = { birthDateInput = it },
                        label = { Text("Birth Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                ConfirmSettingsButton(birthDateInput, userPreferences, scope) {
                    showSettingsDialog.value = false
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedActivityState.value?.let { selectedActivity ->
        ActivityDetailDialog(
            selectedActivity = selectedActivity,
            isHeartRateLoading = viewModel.isHeartRateLoading,
            activityHeartRateSamples = viewModel.activityHeartRateSamples,
            birthDate = birthDate,
            onDismiss = { selectedActivityState.value = null }
        )
    }

    selectedSleepState.value?.let { selectedSleep ->
        SleepDetailDialog(
            sleepSession = selectedSleep,
            onDismiss = { selectedSleepState.value = null }
        )
    }
}

@Composable
fun ConfirmSettingsButton(
    birthDateInput: String,
    userPreferences: UserPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    TextButton(onClick = {
        try {
            val date = LocalDate.parse(birthDateInput)
            scope.launch {
                userPreferences.saveBirthDate(date)
            }
            onDismiss()
        } catch (_: Exception) {
            // Show error or keep dialog open
        }
    }) {
        Text("Save")
    }
}
