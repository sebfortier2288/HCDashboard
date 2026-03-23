package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.BodyFatRecord
import java.time.Instant
import kotlin.math.roundToInt

object BodyFatHandler : HealthDataHandler<BodyFatRecord> {
    override val recordType = BodyFatRecord::class
    override val label = "Body Fat (%)"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: BodyFatRecord): Float = record.percentage.value.toFloat()

    override fun getRecordTimestamp(record: BodyFatRecord): Instant = record.time
}
