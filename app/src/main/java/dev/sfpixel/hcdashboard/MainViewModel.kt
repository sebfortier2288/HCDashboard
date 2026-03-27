package dev.sfpixel.hcdashboard

import androidx.compose.runtime.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sfpixel.hcdashboard.handlers.HeartRateZoneHandler
import dev.sfpixel.hcdashboard.models.DashboardTab
import dev.sfpixel.hcdashboard.models.HealthUtils
import dev.sfpixel.hcdashboard.models.PeriodType
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.reflect.KClass

class MainViewModel(private val client: HealthConnectClient) : ViewModel() {

    fun getHealthConnectClient() = client

    // --- State Variables ---
    var rawWeights by mutableStateOf<List<WeightRecord>>(emptyList())
    var rawBodyFats by mutableStateOf<List<BodyFatRecord>>(emptyList())
    var rawSteps by mutableStateOf<List<StepsRecord>>(emptyList())
    var rawSleepSessions by mutableStateOf<List<SleepSessionRecord>>(emptyList())
    var rawRestingHeartRateRecords by mutableStateOf<List<RestingHeartRateRecord>>(emptyList())
    var rawHrvRecords by mutableStateOf<List<HeartRateVariabilityRmssdRecord>>(emptyList())
    var rawVo2MaxRecords by mutableStateOf<List<Vo2MaxRecord>>(emptyList())
    var exerciseSessions by mutableStateOf<List<ExerciseSessionRecord>>(emptyList())
    
    var todaySteps by mutableLongStateOf(0L)
    var lastNightSleepSession by mutableStateOf<SleepSessionRecord?>(null)
    val lastNightSleepDuration get() = lastNightSleepSession?.let { Duration.between(it.startTime, it.endTime) }
    var todayRestingHeartRate by mutableStateOf<Long?>(null)
    var hrv7DayAvg by mutableStateOf<Double?>(null)
    var hrvHistoryAvg by mutableStateOf<Double?>(null)
    var intensityMinutesWeek by mutableLongStateOf(0L)
    
    var historyPeriod by mutableStateOf(PeriodType.Week)
    var historyOffset by mutableLongStateOf(0L)
    var activityPeriod by mutableStateOf(PeriodType.Week)
    var activityOffset by mutableLongStateOf(0L)
    
    var selectedTab by mutableStateOf(DashboardTab.Today)
    var isLoading by mutableStateOf(false)
    
    var activityHeartRateSamples by mutableStateOf<List<HeartRateRecord>>(emptyList())
    var isHeartRateLoading by mutableStateOf(false)

    // --- Processed Data (computed properties) ---

    val stepsProcessed get() = rawSteps.groupBy { HealthUtils.getGroupingDate(it.startTime, historyPeriod) }
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

    val sleepProcessed get() = rawSleepSessions.groupBy { HealthUtils.getGroupingDate(it.endTime, historyPeriod) }
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

    val rhrProcessed get() = rawRestingHeartRateRecords.groupBy { HealthUtils.getGroupingDate(it.time, historyPeriod) }
        .map { (date, list) ->
            val first = list.first()
            RestingHeartRateRecord(
                time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                zoneOffset = first.zoneOffset,
                beatsPerMinute = list.map { it.beatsPerMinute }.average().toLong(),
                metadata = first.metadata
            )
        }.sortedBy { it.time }

    val hrvProcessed get() = rawHrvRecords.groupBy { HealthUtils.getGroupingDate(it.time, historyPeriod) }
        .map { (date, list) ->
            val first = list.first()
            HeartRateVariabilityRmssdRecord(
                time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                zoneOffset = first.zoneOffset,
                heartRateVariabilityMillis = list.map { it.heartRateVariabilityMillis }.average(),
                metadata = first.metadata
            )
        }.sortedBy { it.time }

    val vo2MaxProcessed get() = rawVo2MaxRecords.groupBy { HealthUtils.getGroupingDate(it.time, historyPeriod) }
        .map { (date, list) ->
            val first = list.first()
            Vo2MaxRecord(
                time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                zoneOffset = first.zoneOffset,
                vo2MillilitersPerMinuteKilogram = list.map { it.vo2MillilitersPerMinuteKilogram }.average(),
                metadata = first.metadata
            )
        }.sortedBy { it.time }

    val weightsProcessed get() = rawWeights.groupBy { HealthUtils.getGroupingDate(it.time, historyPeriod) }
        .map { (date, list) ->
            val first = list.first()
            WeightRecord(
                time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                zoneOffset = first.zoneOffset,
                weight = androidx.health.connect.client.units.Mass.kilograms(list.map { it.weight.inKilograms }.average()),
                metadata = first.metadata
            )
        }.sortedBy { it.time }

    val bodyFatsProcessed get() = rawBodyFats.groupBy { HealthUtils.getGroupingDate(it.time, historyPeriod) }
        .map { (date, list) ->
            val first = list.first()
            BodyFatRecord(
                time = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                zoneOffset = first.zoneOffset,
                percentage = androidx.health.connect.client.units.Percentage(list.map { it.percentage.value }.average()),
                metadata = first.metadata
            )
        }.sortedBy { it.time }

    // --- Data Fetching ---

    private suspend fun <T : Record> fetchAllPages(recordType: KClass<T>, filter: TimeRangeFilter): List<T> {
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

    fun loadTodayData(birthDate: LocalDate?) {
        viewModelScope.launch {
            try {
                val now = Instant.now()
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                
                todaySteps = fetchAllPages(StepsRecord::class, TimeRangeFilter.between(startOfDay, now)).sumOf { it.count }

                lastNightSleepSession = fetchAllPages(
                    SleepSessionRecord::class, 
                    TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
                ).maxByOrNull { it.endTime }

                todayRestingHeartRate = fetchAllPages(
                    RestingHeartRateRecord::class,
                    TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
                ).maxByOrNull { it.time }?.beatsPerMinute

                val hrv7DayAvgRecords = fetchAllPages(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(now.minus(7, ChronoUnit.DAYS), now)
                )
                hrv7DayAvg = if (hrv7DayAvgRecords.isNotEmpty()) hrv7DayAvgRecords.map { it.heartRateVariabilityMillis }.average() else null

                val hrv30DayRecords = fetchAllPages(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now)
                )
                hrvHistoryAvg = if (hrv30DayRecords.isNotEmpty()) hrv30DayRecords.map { it.heartRateVariabilityMillis }.average() else null

                val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant()
                val weekHrRecords = fetchAllPages(HeartRateRecord::class, TimeRangeFilter.between(weekStart, now))

                intensityMinutesWeek = birthDate?.let { date ->
                    val maxHr = HeartRateZoneHandler.calculateMaxHeartRate(date)
                    val zones = HeartRateZoneHandler.getZones(maxHr)
                    val calculatedZones = HeartRateZoneHandler.calculateTimeInZones(weekHrRecords, zones)
                    HeartRateZoneHandler.calculateIntensityMinutes(calculatedZones)
                } ?: 0L
                
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadHistoryData(periodType: PeriodType, offset: Long) {
        historyPeriod = periodType
        historyOffset = offset
        viewModelScope.launch {
            isLoading = true
            try {
                val (startTime, endTime) = HealthUtils.calculatePeriodRange(periodType, offset)
                val filter = TimeRangeFilter.between(startTime, endTime)

                rawWeights = fetchAllPages(WeightRecord::class, filter).sortedBy { it.time }
                rawBodyFats = fetchAllPages(BodyFatRecord::class, filter).sortedBy { it.time }
                rawSteps = fetchAllPages(StepsRecord::class, filter).sortedBy { it.startTime }
                rawSleepSessions = fetchAllPages(SleepSessionRecord::class, filter).sortedBy { it.startTime }
                rawRestingHeartRateRecords = fetchAllPages(RestingHeartRateRecord::class, filter).sortedBy { it.time }
                rawHrvRecords = fetchAllPages(HeartRateVariabilityRmssdRecord::class, filter).sortedBy { it.time }
                rawVo2MaxRecords = fetchAllPages(Vo2MaxRecord::class, filter).sortedBy { it.time }
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    fun loadActivitiesData(periodType: PeriodType, offset: Long) {
        activityPeriod = periodType
        activityOffset = offset
        viewModelScope.launch {
            isLoading = true
            exerciseSessions = emptyList()
            try {
                val (startTime, endTime) = HealthUtils.calculatePeriodRange(periodType, offset)
                exerciseSessions = fetchAllPages(ExerciseSessionRecord::class, TimeRangeFilter.between(startTime, endTime)).sortedByDescending { it.startTime }
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    fun loadActivityHeartRate(session: ExerciseSessionRecord) {
        viewModelScope.launch {
            isHeartRateLoading = true
            try {
                val sessionDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                val dayStart = sessionDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val dayEnd = dayStart.plus(1, ChronoUnit.DAYS)
                
                val allDayRecords = fetchAllPages(HeartRateRecord::class, TimeRangeFilter.between(dayStart, dayEnd))
                val filteredSamples = allDayRecords.flatMap { record ->
                    record.samples.filter { it.time >= session.startTime && it.time <= session.endTime }
                }.sortedBy { it.time }

                activityHeartRateSamples = filteredSamples.mapIndexed { index, sample ->
                    val nextTime = if (index < filteredSamples.size - 1) filteredSamples[index + 1].time else session.endTime
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
            } finally { isHeartRateLoading = false }
        }
    }
}
