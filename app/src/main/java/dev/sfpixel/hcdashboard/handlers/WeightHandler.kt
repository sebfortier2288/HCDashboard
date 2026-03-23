package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.WeightRecord
import java.time.Instant
import kotlin.math.roundToInt

object WeightHandler : HealthDataHandler<WeightRecord> {
    override val recordType = WeightRecord::class
    override val label = "Weight (lb)"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: WeightRecord): Float = record.weight.inPounds.toFloat()

    override fun getRecordTimestamp(record: WeightRecord): Instant = record.time
}
