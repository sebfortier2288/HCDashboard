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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sfpixel.hcdashboard.handlers.*
import dev.sfpixel.hcdashboard.ui.theme.HCDashboardTheme
import dev.sfpixel.hcdashboard.ui.views.ActivitiesView
import dev.sfpixel.hcdashboard.ui.views.HistoryView
import dev.sfpixel.hcdashboard.ui.views.TodayView
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class TimeRange(val label: String, val days: Long) {
    Last24h("24h", 1),
    Last7Days("7d", 7),
    Last30Days("30d", 30),
    LastYear("1y", 365),
    AllTime("All", 3650)
}

enum class DashboardTab(val title: String) {
    Today("Today"),
    History("History"),
    Activities("Activities")
}

enum class ActivityPeriodType(val label: String) {
    Week("Week"),
    Month("Month"),
    Year("Year")
}

fun calculatePeriodRange(periodType: ActivityPeriodType, offset: Long): Pair<Instant, Instant> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    
    return when (periodType) {
        ActivityPeriodType.Week -> {
            val baseDate = now.plusWeeks(offset)
            val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
            val end = start.plus(7, ChronoUnit.DAYS)
            start to end
        }
        ActivityPeriodType.Month -> {
            val baseDate = now.plusMonths(offset)
            val start = baseDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant()
            val end = baseDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(zone).toInstant()
            start to end
        }
        ActivityPeriodType.Year -> {
            val baseDate = now.plusYears(offset)
            val start = baseDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant()
            val end = baseDate.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).atStartOfDay(zone).toInstant()
            start to end
        }
    }
}

fun formatPeriodLabel(periodType: ActivityPeriodType, offset: Long): String {
    val now = LocalDate.now()
    
    return when (periodType) {
        ActivityPeriodType.Week -> {
            val baseDate = now.plusWeeks(offset)
            val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = start.plusDays(6)
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            if (start.year == end.year) {
                "${start.format(formatter)} - ${end.format(formatter)}, ${start.year}"
            } else {
                "${start.format(formatter)} ${start.year} - ${end.format(formatter)} ${end.year}"
            }
        }
        ActivityPeriodType.Month -> {
            val baseDate = now.plusMonths(offset)
            baseDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
        ActivityPeriodType.Year -> {
            val baseDate = now.plusYears(offset)
            baseDate.year.toString()
        }
    }
}

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
            androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboard(client: HealthConnectClient, permissions: Set<String>) {
    var weights by remember { mutableStateOf<List<WeightRecord>>(emptyList()) }
    var bodyFats by remember { mutableStateOf<List<BodyFatRecord>>(emptyList()) }
    var rawSteps by remember { mutableStateOf<List<StepsRecord>>(emptyList()) }
    var rawSleepSessions by remember { mutableStateOf<List<SleepSessionRecord>>(emptyList()) }
    var restingHeartRateRecords by remember { mutableStateOf<List<RestingHeartRateRecord>>(emptyList()) }
    var hrvRecords by remember { mutableStateOf<List<HeartRateVariabilityRmssdRecord>>(emptyList()) }
    var vo2MaxRecords by remember { mutableStateOf<List<Vo2MaxRecord>>(emptyList()) }
    var exerciseSessions by remember { mutableStateOf<List<ExerciseSessionRecord>>(emptyList()) }
    
    var todaySteps by remember { mutableLongStateOf(0L) }
    var lastNightSleepDuration by remember { mutableStateOf<Duration?>(null) }
    var todayRestingHeartRate by remember { mutableStateOf<Long?>(null) }
    var hrv7DayAvg by remember { mutableStateOf<Double?>(null) }
    var hrvHistoryAvg by remember { mutableStateOf<Double?>(null) }
    
    var isAuthorized by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf(TimeRange.Last7Days) }
    var selectedTab by remember { mutableStateOf(DashboardTab.Today) }
    var isLoading by remember { mutableStateOf(false) }
    val selectedActivityState = remember { mutableStateOf<ExerciseSessionRecord?>(null) }
    
    var activityPeriod by remember { mutableStateOf(ActivityPeriodType.Week) }
    var activityOffset by remember { mutableLongStateOf(0L) }

    val scrollState = rememberScrollState()

    val stepsProcessed = remember(rawSteps, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            rawSteps.groupBy { 
                it.startTime.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS).toInstant() 
            }.map { (time, list) ->
                val first = list.first()
                StepsRecord(
                    startTime = time,
                    endTime = time.plus(1, ChronoUnit.HOURS),
                    count = list.sumOf { it.count },
                    startZoneOffset = first.startZoneOffset,
                    endZoneOffset = first.endZoneOffset,
                    metadata = first.metadata
                )
            }.sortedBy { it.startTime }
        } else {
            rawSteps.groupBy { 
                it.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val first = list.first()
                StepsRecord(
                    startTime = start,
                    endTime = start.plus(1, ChronoUnit.DAYS),
                    count = list.sumOf { it.count },
                    startZoneOffset = first.startZoneOffset,
                    endZoneOffset = first.endZoneOffset,
                    metadata = first.metadata
                )
            }.sortedBy { it.startTime }.takeLast(selectedRange.days.toInt())
        }
    }

    val sleepProcessed = remember(rawSleepSessions, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            rawSleepSessions
        } else {
            rawSleepSessions.groupBy { 
                it.endTime.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val first = list.first()
                val totalDurationMillis = list.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
                val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                SleepSessionRecord(
                    startTime = startOfDay,
                    endTime = startOfDay.plusMillis(totalDurationMillis),
                    startZoneOffset = first.startZoneOffset,
                    endZoneOffset = first.endZoneOffset,
                    metadata = first.metadata
                )
            }.sortedBy { it.startTime }.takeLast(selectedRange.days.toInt())
        }
    }

    val rhrProcessed = remember(restingHeartRateRecords, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            restingHeartRateRecords
        } else {
            restingHeartRateRecords.groupBy { 
                it.time.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val first = list.first()
                RestingHeartRateRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    beatsPerMinute = list.map { it.beatsPerMinute }.average().toLong(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }.takeLast(selectedRange.days.toInt())
        }
    }

    val hrvProcessed = remember(hrvRecords, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            hrvRecords
        } else {
            hrvRecords.groupBy { 
                it.time.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val first = list.first()
                HeartRateVariabilityRmssdRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    heartRateVariabilityMillis = list.map { it.heartRateVariabilityMillis }.average(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }.takeLast(selectedRange.days.toInt())
        }
    }

    val vo2MaxProcessed = remember(vo2MaxRecords, selectedRange) {
        if (selectedRange == TimeRange.Last24h) {
            vo2MaxRecords
        } else {
            vo2MaxRecords.groupBy { 
                it.time.atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (date, list) ->
                val first = list.first()
                Vo2MaxRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    vo2MillilitersPerMinuteKilogram = list.map { it.vo2MillilitersPerMinuteKilogram }.average(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }.takeLast(selectedRange.days.toInt())
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

    val loadActivitiesData: suspend (ActivityPeriodType, Long) -> Unit = { periodType, offset ->
        isLoading = true
        try {
            val (startTime, endTime) = calculatePeriodRange(periodType, offset)
            val timeFilter = TimeRangeFilter.between(startTime, endTime)
            exerciseSessions = fetchAllPages(ExerciseSessionRecord::class, timeFilter).sortedByDescending { it.startTime }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val loadHistoryData: suspend (TimeRange) -> Unit = { range ->
        isLoading = true
        try {
            val endTime = Instant.now()
            val startTime = if (range == TimeRange.Last24h) {
                endTime.minus(24, ChronoUnit.HOURS)
            } else {
                LocalDate.now().minusDays(range.days)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()
            }
            val timeFilter = TimeRangeFilter.between(startTime, endTime)

            weights = fetchAllPages(WeightRecord::class, timeFilter).sortedBy { it.time }
            bodyFats = fetchAllPages(BodyFatRecord::class, timeFilter).sortedBy { it.time }
            rawSteps = fetchAllPages(StepsRecord::class, timeFilter).sortedBy { it.startTime }
            rawSleepSessions = fetchAllPages(SleepSessionRecord::class, timeFilter).sortedBy { it.startTime }
            restingHeartRateRecords = fetchAllPages(RestingHeartRateRecord::class, timeFilter).sortedBy { it.time }
            hrvRecords = fetchAllPages(HeartRateVariabilityRmssdRecord::class, timeFilter).sortedBy { it.time }
            vo2MaxRecords = fetchAllPages(Vo2MaxRecord::class, timeFilter).sortedBy { it.time }

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

            // Sleep last night
            val sleepRecords = fetchAllPages(
                SleepSessionRecord::class, 
                TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            )
            lastNightSleepDuration = sleepRecords.maxByOrNull { it.endTime }?.let { 
                Duration.between(it.startTime, it.endTime)
            }

            // Resting Heart Rate today
            val rhrRecords = fetchAllPages(
                RestingHeartRateRecord::class,
                TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            )
            todayRestingHeartRate = rhrRecords.maxByOrNull { it.time }?.beatsPerMinute

            // HRV 7-day average vs 30-day baseline for color coding
            val hrv7DayRecords = fetchAllPages(
                HeartRateVariabilityRmssdRecord::class,
                TimeRangeFilter.between(now.minus(7, ChronoUnit.DAYS), now)
            )
            hrv7DayAvg = if (hrv7DayRecords.isNotEmpty()) {
                hrv7DayRecords.map { it.heartRateVariabilityMillis }.average()
            } else null

            val hrv30DayRecords = fetchAllPages(
                HeartRateVariabilityRmssdRecord::class,
                TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now)
            )
            hrvHistoryAvg = if (hrv30DayRecords.isNotEmpty()) {
                hrv30DayRecords.map { it.heartRateVariabilityMillis }.average()
            } else null
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isAuthorized, selectedRange, activityPeriod, activityOffset, selectedTab) {
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            isAuthorized = true
            
            when (selectedTab) {
                DashboardTab.Today -> loadTodayData()
                DashboardTab.History -> loadHistoryData(selectedRange)
                DashboardTab.Activities -> loadActivitiesData(activityPeriod, activityOffset)
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
            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
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
                when (selectedTab) {
                    DashboardTab.Today -> {
                        TodayView(todaySteps, lastNightSleepDuration, todayRestingHeartRate, hrv7DayAvg, hrvHistoryAvg)
                    }
                    DashboardTab.History -> {
                        HistoryView(
                            selectedRange = selectedRange,
                            onRangeSelected = { selectedRange = it },
                            isLoading = isLoading,
                            weights = weights,
                            bodyFats = bodyFats,
                            stepsProcessed = stepsProcessed,
                            sleepProcessed = sleepProcessed,
                            restingHeartRateProcessed = rhrProcessed,
                            hrvProcessed = hrvProcessed,
                            vo2MaxProcessed = vo2MaxProcessed
                        )
                    }
                    DashboardTab.Activities -> {
                        ActivitiesView(
                            periodType = activityPeriod,
                            offset = activityOffset,
                            onPeriodTypeChanged = { activityPeriod = it },
                            onOffsetChanged = { activityOffset = it },
                            isLoading = isLoading,
                            exerciseSessions = exerciseSessions,
                            onActivityClick = { selectedActivityState.value = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            Text("Please authorize access to Health Connect.")
            Button(onClick = { requestPermissionLauncher.launch(permissions) }) { Text("Authorize") }
        }
    }

    selectedActivityState.value?.let { selectedActivity ->
        AlertDialog(
            onDismissRequest = { selectedActivityState.value = null },
            title = { Text(selectedActivity.title ?: ExerciseHandler.getExerciseName(selectedActivity.exerciseType)) },
            text = {
                Column {
                    Text("Start: ${selectedActivity.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                    Text("End: ${selectedActivity.endTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                    val duration = Duration.between(selectedActivity.startTime, selectedActivity.endTime)
                    Text("Duration: ${ExerciseHandler.formatDuration(duration)}")
                    selectedActivity.notes?.let {
                        if (it.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Notes: $it")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedActivityState.value = null }) {
                    Text("Close")
                }
            }
        )
    }
}
