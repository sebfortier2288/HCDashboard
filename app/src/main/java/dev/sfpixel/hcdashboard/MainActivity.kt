package dev.sfpixel.hcdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sfpixel.hcdashboard.ui.theme.HCDashboardTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class TimeRange(val label: String, val days: Long) {
    Last24h("24h", 1),
    Last7Days("7d", 7),
    Last30Days("30d", 30),
    LastYear("1y", 365),
    AllTime("All", 3650)
}

enum class DashboardTab(val title: String) {
    Today("Today"),
    Historical("Historical")
}

class MainActivity : ComponentActivity() {

    private val handlers = listOf(WeightHandler, StepsHandler, SleepHandler)
    
    private val permissions = handlers.map { 
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(it.recordType) 
    }.toSet() + androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val healthConnectClient = HealthConnectClient.getOrCreate(this)

        setContent {
            HCDashboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthDashboard(healthConnectClient, permissions)
                }
            }
        }
    }
}

@Composable
fun HealthDashboard(client: HealthConnectClient, permissions: Set<String>) {
    var weights by remember { mutableStateOf<List<WeightRecord>>(emptyList()) }
    var rawSteps by remember { mutableStateOf<List<StepsRecord>>(emptyList()) }
    var sleepSessions by remember { mutableStateOf<List<SleepSessionRecord>>(emptyList()) }
    var todaySteps by remember { mutableLongStateOf(0L) }
    var lastNightSleepDuration by remember { mutableStateOf<Duration?>(null) }
    var isAuthorized by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf(TimeRange.Last7Days) }
    var selectedTab by remember { mutableStateOf(DashboardTab.Today) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    val stepsProcessed = remember(rawSteps, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            rawSteps.groupBy { 
                it.startTime.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS).toInstant() 
            }.map { (time, list) ->
                list.first().let { 
                    StepsRecord(
                        startTime = time,
                        endTime = time.plus(1, ChronoUnit.HOURS),
                        count = list.sumOf { it.count },
                        startZoneOffset = it.startZoneOffset,
                        endZoneOffset = it.endZoneOffset,
                        metadata = it.metadata
                    )
                }
            }.sortedBy { it.startTime }
        } else {
            rawSteps.groupBy { 
                it.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                list.first().let {
                    StepsRecord(
                        startTime = start,
                        endTime = start.plus(1, ChronoUnit.DAYS),
                        count = list.sumOf { it.count },
                        startZoneOffset = it.startZoneOffset,
                        endZoneOffset = it.endZoneOffset,
                        metadata = it.metadata
                    )
                }
            }.sortedBy { it.startTime }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            isAuthorized = true
        }
    }

    suspend fun <T : Record> fetchAllPages(recordType: kotlin.reflect.KClass<T>, filter: TimeRangeFilter): List<T> {
        val result = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType = recordType, timeRangeFilter = filter, pageToken = pageToken)
            )
            result.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        return result
    }

    val loadHistoricalData: suspend (TimeRange) -> Unit = { range ->
        isLoading = true
        try {
            val endTime = Instant.now()
            val startTime = endTime.minus(range.days, ChronoUnit.DAYS)
            val timeFilter = TimeRangeFilter.between(startTime, endTime)

            weights = fetchAllPages(WeightRecord::class, timeFilter).sortedBy { it.time }
            rawSteps = fetchAllPages(StepsRecord::class, timeFilter).sortedBy { it.startTime }
            sleepSessions = fetchAllPages(SleepSessionRecord::class, timeFilter).sortedBy { it.startTime }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val loadTodayData: suspend () -> Unit = {
        try {
            val now = Instant.now()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            
            // Steps today
            val todayStepsRecords = fetchAllPages(StepsRecord::class, TimeRangeFilter.between(startOfDay, now))
            todaySteps = todayStepsRecords.sumOf { it.count }

            // Sleep last night (last 24h for simplicity, taking the most recent session)
            val sleepRecords = fetchAllPages(
                SleepSessionRecord::class, 
                TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            )
            lastNightSleepDuration = sleepRecords.maxByOrNull { it.endTime }?.let { 
                Duration.between(it.startTime, it.endTime)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isAuthorized, selectedRange, selectedTab) {
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            isAuthorized = true
            if (selectedTab == DashboardTab.Today) {
                loadTodayData()
            } else {
                loadHistoricalData(selectedRange)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Health Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (isAuthorized) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
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
                if (selectedTab == DashboardTab.Today) {
                    TodayView(todaySteps, lastNightSleepDuration)
                } else {
                    HistoricalView(
                        selectedRange = selectedRange,
                        onRangeSelected = { selectedRange = it },
                        isLoading = isLoading,
                        weights = weights,
                        stepsProcessed = stepsProcessed,
                        sleepSessions = sleepSessions
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            Text("Please authorize access to Health Connect.")
            Button(onClick = { requestPermissionLauncher.launch(permissions) }) { Text("Authorize") }
        }
    }
}

@Composable
fun TodayView(steps: Long, sleepDuration: Duration?) {
    SummaryCard(
        title = "Steps Today",
        value = steps.toString(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    
    Spacer(modifier = Modifier.height(12.dp))
    
    val sleepText = sleepDuration?.let {
        val hours = it.toHours()
        val minutes = it.toMinutes() % 60
        "${hours}h ${minutes}m"
    } ?: "No data"

    val sleepMinutes = sleepDuration?.toMinutes() ?: 0L
    val sleepValueColor = when {
        sleepDuration == null -> MaterialTheme.colorScheme.onSecondaryContainer
        sleepMinutes >= 7 * 60 -> Color(0xFF4CAF50) // Vert >= 7h
        sleepMinutes >= 6 * 60 -> Color(0xFFFFB300) // Ambre/Jaune >= 6h
        else -> Color(0xFFF44336) // Rouge < 6h
    }

    SummaryCard(
        title = "Sleep Last Night",
        value = sleepText,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        valueColor = sleepValueColor
    )
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    valueColor: Color = contentColor
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp
                ),
                color = valueColor
            )
        }
    }
}

@Composable
fun HistoricalView(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    isLoading: Boolean,
    weights: List<WeightRecord>,
    stepsProcessed: List<StepsRecord>,
    sleepSessions: List<SleepSessionRecord>
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TimeRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRange.entries.size)
            ) { Text(range.label) }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Text("Sleep History (Hours)", style = MaterialTheme.typography.titleMedium)
        if (sleepSessions.isNotEmpty()) {
            HealthChart(
                handler = SleepHandler, 
                records = sleepSessions, 
                selectedRange = selectedRange,
                isColumnChart = true,
                thresholdValue = 7f
            )
        } else {
            Text("No sleep data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Weight Evolution", style = MaterialTheme.typography.titleMedium)
        if (weights.isNotEmpty()) {
            HealthChart(handler = WeightHandler, records = weights, selectedRange = selectedRange)
        } else {
            Text("No weight data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Daily Activity", style = MaterialTheme.typography.titleMedium)
        if (stepsProcessed.isNotEmpty()) {
            HealthChart(handler = StepsHandler, records = stepsProcessed, selectedRange = selectedRange, isColumnChart = true)
        } else {
            Text("No activity data found.", modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
