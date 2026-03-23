package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.StepsRecord
import java.time.Instant

object StepsHandler : HealthDataHandler<StepsRecord> {
    override val recordType = StepsRecord::class
    override val label = "Steps"

    override fun formatValue(value: Float): String {
        return if (value >= 1000) "%.1fk".format(value / 1000) else value.toInt().toString()
    }

    override fun getRecordValue(record: StepsRecord): Float = record.count.toFloat()

    override fun getRecordTimestamp(record: StepsRecord): Instant = record.startTime
}
