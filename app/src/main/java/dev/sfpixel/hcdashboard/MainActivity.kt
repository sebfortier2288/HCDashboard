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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sfpixel.hcdashboard.handlers.*
import dev.sfpixel.hcdashboard.ui.HealthChart
import dev.sfpixel.hcdashboard.ui.theme.HCDashboardTheme
import dev.sfpixel.hcdashboard.ui.views.ActivitiesView
import dev.sfpixel.hcdashboard.ui.views.HistoryView
import dev.sfpixel.hcdashboard.ui.views.TodayView
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class DashboardTab(val title: String) {
    Today("Today"),
    History("History"),
    Activities("Activities")
}

enum class PeriodType(val label: String) {
    Week("Week"),
    Month("Month"),
    Year("Year")
}

fun calculatePeriodRange(periodType: PeriodType, offset: Long): Pair<Instant, Instant> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    
    return when (periodType) {
        PeriodType.Week -> {
            val baseDate = now.plusWeeks(offset)
            val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zone).toInstant()
            val end = start.plus(7, ChronoUnit.DAYS)
            start to end
        }
        PeriodType.Month -> {
            val baseDate = now.plusMonths(offset)
            val start = baseDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant()
            val end = baseDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(zone).toInstant()
            start to end
        }
        PeriodType.Year -> {
            val baseDate = now.plusYears(offset)
            val start = baseDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant()
            val end = baseDate.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).atStartOfDay(zone).toInstant()
            start to end
        }
    }
}

fun formatPeriodLabel(periodType: PeriodType, offset: Long): String {
    val now = LocalDate.now()
    
    return when (periodType) {
        PeriodType.Week -> {
            val baseDate = now.plusWeeks(offset)
            val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val end = start.plusDays(6)
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            if (start.year == end.year) {
                "${start.format(formatter)} - ${end.format(formatter)}, ${start.year}"
            } else {
                "${start.format(formatter)} ${start.year} - ${end.format(formatter)} ${end.year}"
            }
        }
        PeriodType.Month -> {
            val baseDate = now.plusMonths(offset)
            baseDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
        PeriodType.Year -> {
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
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(HeartRateRecord::class) +
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
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val birthDate by userPreferences.birthDate.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    var rawWeights by remember { mutableStateOf<List<WeightRecord>>(emptyList()) }
    var rawBodyFats by remember { mutableStateOf<List<BodyFatRecord>>(emptyList()) }
    var rawSteps by remember { mutableStateOf<List<StepsRecord>>(emptyList()) }
    var rawSleepSessions by remember { mutableStateOf<List<SleepSessionRecord>>(emptyList()) }
    var rawRestingHeartRateRecords by remember { mutableStateOf<List<RestingHeartRateRecord>>(emptyList()) }
    var rawHrvRecords by remember { mutableStateOf<List<HeartRateVariabilityRmssdRecord>>(emptyList()) }
    var rawVo2MaxRecords by remember { mutableStateOf<List<Vo2MaxRecord>>(emptyList()) }
    var exerciseSessions by remember { mutableStateOf<List<ExerciseSessionRecord>>(emptyList()) }
    
    var todaySteps by remember { mutableLongStateOf(0L) }
    var lastNightSleepDuration by remember { mutableStateOf<Duration?>(null) }
    var todayRestingHeartRate by remember { mutableStateOf<Long?>(null) }
    var hrv7DayAvg by remember { mutableStateOf<Double?>(null) }
    var hrvHistoryAvg by remember { mutableStateOf<Double?>(null) }
    var intensityMinutesWeek by remember { mutableLongStateOf(0L) }
    
    var isAuthorized by remember { mutableStateOf(false) }
    var historyPeriod by remember { mutableStateOf(PeriodType.Week) }
    var historyOffset by remember { mutableLongStateOf(0L) }
    var activityPeriod by remember { mutableStateOf(PeriodType.Week) }
    var activityOffset by remember { mutableLongStateOf(0L) }
    
    var selectedTab by remember { mutableStateOf(DashboardTab.Today) }
    var isLoading by remember { mutableStateOf(false) }
    val selectedActivityState = remember { mutableStateOf<ExerciseSessionRecord?>(null) }
    var activityHeartRateSamples by remember { mutableStateOf<List<HeartRateRecord>>(emptyList()) }
    var isHeartRateLoading by remember { mutableStateOf(false) }

    val showSettingsDialog = remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun getGroupingDate(instant: Instant, period: PeriodType): LocalDate {
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return if (period == PeriodType.Year) localDate.withDayOfMonth(1) else localDate
    }

    val stepsProcessed = remember(rawSteps, historyPeriod) {
        rawSteps.groupBy { getGroupingDate(it.startTime, historyPeriod) }
            .map { (date, list) ->
                val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val first = list.first()
                val totalCount = list.sumOf { it.count }
                val value = if (historyPeriod == PeriodType.Year) {
                    val daysInMonth = YearMonth.from(date).lengthOfMonth()
                    totalCount / daysInMonth.coerceAtLeast(1)
                } else totalCount

                StepsRecord(
                    startTime = start,
                    endTime = start.plus(1, ChronoUnit.DAYS),
                    count = value,
                    startZoneOffset = first.startZoneOffset,
                    endZoneOffset = first.endZoneOffset,
                    metadata = first.metadata
                )
            }.sortedBy { it.startTime }
    }

    val sleepProcessed = remember(rawSleepSessions, historyPeriod) {
        rawSleepSessions.groupBy { getGroupingDate(it.endTime, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                val totalDurationMillis = list.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
                val value = if (historyPeriod == PeriodType.Year) {
                    val daysInMonth = YearMonth.from(date).lengthOfMonth()
                    totalDurationMillis / daysInMonth.coerceAtLeast(1)
                } else totalDurationMillis

                val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                SleepSessionRecord(
                    startTime = startOfDay,
                    endTime = startOfDay.plusMillis(value),
                    startZoneOffset = first.startZoneOffset,
                    endZoneOffset = first.endZoneOffset,
                    metadata = first.metadata
                )
            }.sortedBy { it.startTime }
    }

    val rhrProcessed = remember(rawRestingHeartRateRecords, historyPeriod) {
        rawRestingHeartRateRecords.groupBy { getGroupingDate(it.time, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                RestingHeartRateRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    beatsPerMinute = list.map { it.beatsPerMinute }.average().toLong(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }
    }

    val hrvProcessed = remember(rawHrvRecords, historyPeriod) {
        rawHrvRecords.groupBy { getGroupingDate(it.time, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                HeartRateVariabilityRmssdRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    heartRateVariabilityMillis = list.map { it.heartRateVariabilityMillis }.average(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }
    }

    val vo2MaxProcessed = remember(rawVo2MaxRecords, historyPeriod) {
        rawVo2MaxRecords.groupBy { getGroupingDate(it.time, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                Vo2MaxRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    vo2MillilitersPerMinuteKilogram = list.map { it.vo2MillilitersPerMinuteKilogram }.average(),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }
    }

    val weightsProcessed = remember(rawWeights, historyPeriod) {
        rawWeights.groupBy { getGroupingDate(it.time, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                WeightRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    weight = androidx.health.connect.client.units.Mass.kilograms(list.map { it.weight.inKilograms }.average()),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }
    }

    val bodyFatsProcessed = remember(rawBodyFats, historyPeriod) {
        rawBodyFats.groupBy { getGroupingDate(it.time, historyPeriod) }
            .map { (date, list) ->
                val first = list.first()
                BodyFatRecord(
                    time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    zoneOffset = first.zoneOffset,
                    percentage = androidx.health.connect.client.units.Percentage(list.map { it.percentage.value }.average()),
                    metadata = first.metadata
                )
            }.sortedBy { it.time }
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

    val loadActivitiesData: suspend (PeriodType, Long) -> Unit = { periodType, offset ->
        isLoading = true
        exerciseSessions = emptyList() // Reset to show loading
        
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

    val loadHistoryData: suspend (PeriodType, Long) -> Unit = { periodType, offset ->
        isLoading = true
        try {
            val (startTime, endTime) = calculatePeriodRange(periodType, offset)
            val timeFilter = TimeRangeFilter.between(startTime, endTime)

            rawWeights = fetchAllPages(WeightRecord::class, timeFilter).sortedBy { it.time }
            rawBodyFats = fetchAllPages(BodyFatRecord::class, timeFilter).sortedBy { it.time }
            rawSteps = fetchAllPages(StepsRecord::class, timeFilter).sortedBy { it.startTime }
            rawSleepSessions = fetchAllPages(SleepSessionRecord::class, timeFilter).sortedBy { it.startTime }
            rawRestingHeartRateRecords = fetchAllPages(RestingHeartRateRecord::class, timeFilter).sortedBy { it.time }
            rawHrvRecords = fetchAllPages(HeartRateVariabilityRmssdRecord::class, timeFilter).sortedBy { it.time }
            rawVo2MaxRecords = fetchAllPages(Vo2MaxRecord::class, timeFilter).sortedBy { it.time }

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

            // Intensity minutes for current week (Sunday to now)
            val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val weekHrRecords = fetchAllPages(HeartRateRecord::class, TimeRangeFilter.between(weekStart, now))

            birthDate?.let { date ->
                val maxHr = HeartRateZoneHandler.calculateMaxHeartRate(date)
                val zones = HeartRateZoneHandler.getZones(maxHr)
                val calculatedZones = HeartRateZoneHandler.calculateTimeInZones(weekHrRecords, zones)
                intensityMinutesWeek = HeartRateZoneHandler.calculateIntensityMinutes(calculatedZones)
            } ?: run {
                intensityMinutesWeek = 0L
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isAuthorized, historyPeriod, historyOffset, activityPeriod, activityOffset, selectedTab, birthDate) {
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            isAuthorized = true
            
            when (selectedTab) {
                DashboardTab.Today -> loadTodayData()
                DashboardTab.History -> loadHistoryData(historyPeriod, historyOffset)
                DashboardTab.Activities -> loadActivitiesData(activityPeriod, activityOffset)
            }
        }
    }

    LaunchedEffect(selectedActivityState.value) {
        val session = selectedActivityState.value
        if (session != null) {
            isHeartRateLoading = true
            try {
                // To handle "grouped" records from apps like Health Sync,
                // we query the entire day of the activity.
                val sessionDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                val dayStart = sessionDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val dayEnd = dayStart.plus(1, ChronoUnit.DAYS)
                
                val allDayRecords = fetchAllPages(
                    HeartRateRecord::class,
                    TimeRangeFilter.between(dayStart, dayEnd)
                )
                
                // Filter the samples that fall WITHIN the session duration
                val filteredSamples = allDayRecords.flatMap { record ->
                    record.samples.filter { sample ->
                        sample.time >= session.startTime && sample.time <= session.endTime
                    }
                }.sortedBy { it.time }

                // Map samples to HeartRateRecord with proper duration
                activityHeartRateSamples = filteredSamples.mapIndexed { index, sample ->
                    val nextTime = if (index < filteredSamples.size - 1) {
                        filteredSamples[index + 1].time
                    } else {
                        session.endTime
                    }
                    
                    HeartRateRecord(
                        startTime = sample.time,
                        endTime = nextTime,
                        startZoneOffset = session.startZoneOffset,
                        endZoneOffset = session.endZoneOffset,
                        samples = listOf(sample),
                        metadata = session.metadata
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                activityHeartRateSamples = emptyList()
            } finally {
                isHeartRateLoading = false
            }
        } else {
            activityHeartRateSamples = emptyList()
        }
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
                        TodayView(todaySteps, lastNightSleepDuration, todayRestingHeartRate, hrv7DayAvg, hrvHistoryAvg, intensityMinutesWeek)
                    }
                    DashboardTab.History -> {
                        HistoryView(
                            periodType = historyPeriod,
                            offset = historyOffset,
                            onPeriodTypeChanged = { historyPeriod = it },
                            onOffsetChanged = { historyOffset = it },
                            isLoading = isLoading,
                            weights = weightsProcessed,
                            bodyFats = bodyFatsProcessed,
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
        ActivityDetailDialog(selectedActivity, isHeartRateLoading, activityHeartRateSamples, birthDate) {
            selectedActivityState.value = null
        }
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

@Composable
fun ActivityDetailDialog(
    selectedActivity: ExerciseSessionRecord,
    isHeartRateLoading: Boolean,
    activityHeartRateSamples: List<HeartRateRecord>,
    birthDate: LocalDate?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selectedActivity.title ?: ExerciseHandler.getExerciseName(selectedActivity.exerciseType)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Start: ${selectedActivity.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                Text("End: ${selectedActivity.endTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                val duration = Duration.between(selectedActivity.startTime, selectedActivity.endTime)
                Text("Duration: ${ExerciseHandler.formatDuration(duration)}")
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Heart Rate", style = MaterialTheme.typography.titleSmall)
                
                if (isHeartRateLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (activityHeartRateSamples.isNotEmpty()) {
                    HealthChart(
                        handler = HeartRateHandler,
                        records = activityHeartRateSamples,
                        periodType = PeriodType.Week, // Dummy value for detail view
                        modifier = Modifier.height(200.dp)
                    )
                    
                    birthDate?.let { date ->
                        val maxHr = HeartRateZoneHandler.calculateMaxHeartRate(date)
                        val zones = HeartRateZoneHandler.getZones(maxHr)
                        val calculatedZones = HeartRateZoneHandler.calculateTimeInZones(activityHeartRateSamples, zones)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Zones HR (Max: $maxHr bpm)", style = MaterialTheme.typography.titleSmall)
                        calculatedZones.forEach { zone ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${zone.name}:", style = MaterialTheme.typography.bodySmall)
                                Text(ExerciseHandler.formatDuration(zone.duration), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        val intensityMinutes = HeartRateZoneHandler.calculateIntensityMinutes(calculatedZones)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Intensity minutes (Z3+): $intensityMinutes min", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } ?: run {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Set your birth date in settings to see HR zones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No heart rate data found for this activity duration.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                selectedActivity.notes?.let {
                    if (it.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes: $it")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
