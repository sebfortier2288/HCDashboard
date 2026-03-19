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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.sfpixel.hcdashboard.ui.theme.HCDashboardTheme
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class TimeRange(val label: String, val days: Long) {
    Last24h("24h", 1),
    Last7Days("7d", 7),
    Last30Days("30d", 30),
    LastYear("1y", 365),
    AllTime("All", 3650)
}

class MainActivity : ComponentActivity() {

    private val handlers = listOf(WeightHandler, StepsHandler)
    
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
    var isAuthorized by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf(TimeRange.Last7Days) }
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

    val loadData: suspend (TimeRange) -> Unit = { range ->
        isLoading = true
        try {
            val endTime = Instant.now()
            val startTime = endTime.minus(range.days, ChronoUnit.DAYS)
            val timeFilter = TimeRangeFilter.between(startTime, endTime)

            weights = fetchAllPages(WeightRecord::class, timeFilter).sortedBy { it.time }
            rawSteps = fetchAllPages(StepsRecord::class, timeFilter).sortedBy { it.startTime }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(isAuthorized, selectedRange) {
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            isAuthorized = true
            loadData(selectedRange)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("Health Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (isAuthorized) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimeRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
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
        } else {
            Text("Please authorize access to Health Connect.")
            Button(onClick = { requestPermissionLauncher.launch(permissions) }) { Text("Authorize") }
        }
    }
}
