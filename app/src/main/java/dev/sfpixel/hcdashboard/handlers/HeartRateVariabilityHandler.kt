package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import java.time.Instant
import kotlin.math.roundToInt

object HeartRateVariabilityHandler : HealthDataHandler<HeartRateVariabilityRmssdRecord> {
    override val recordType = HeartRateVariabilityRmssdRecord::class
    override val label = "HRV (ms)"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: HeartRateVariabilityRmssdRecord): Float = record.heartRateVariabilityMillis.toFloat()

    override fun getRecordTimestamp(record: HeartRateVariabilityRmssdRecord): Instant = record.time
}
