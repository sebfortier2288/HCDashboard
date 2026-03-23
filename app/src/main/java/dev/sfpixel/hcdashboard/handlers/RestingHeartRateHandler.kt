package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.RestingHeartRateRecord
import java.time.Instant
import kotlin.math.roundToInt

object RestingHeartRateHandler : HealthDataHandler<RestingHeartRateRecord> {
    override val recordType = RestingHeartRateRecord::class
    override val label = "Resting HR (bpm)"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: RestingHeartRateRecord): Float = record.beatsPerMinute.toFloat()

    override fun getRecordTimestamp(record: RestingHeartRateRecord): Instant = record.time
}
