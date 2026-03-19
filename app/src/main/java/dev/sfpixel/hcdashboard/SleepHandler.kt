package dev.sfpixel.hcdashboard

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.Instant

object SleepHandler : HealthDataHandler<SleepSessionRecord> {
    override val recordType = SleepSessionRecord::class
    override val label = "Sleep"

    override fun formatValue(value: Float): String {
        val hours = value.toInt()
        val minutes = ((value - hours) * 60).toInt()
        return "${hours}h${minutes}"
    }

    override fun getRecordValue(record: SleepSessionRecord): Float {
        val duration = Duration.between(record.startTime, record.endTime)
        return duration.toMinutes() / 60f
    }

    override fun getRecordTimestamp(record: SleepSessionRecord): Instant = record.startTime
}
