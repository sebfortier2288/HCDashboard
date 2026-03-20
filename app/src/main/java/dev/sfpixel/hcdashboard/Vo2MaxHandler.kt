package dev.sfpixel.hcdashboard

import androidx.health.connect.client.records.Vo2MaxRecord
import java.time.Instant
import kotlin.math.roundToInt

object Vo2MaxHandler : HealthDataHandler<Vo2MaxRecord> {
    override val recordType = Vo2MaxRecord::class
    override val label = "VO2 Max"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: Vo2MaxRecord): Float = record.vo2MillilitersPerMinuteKilogram.toFloat()

    override fun getRecordTimestamp(record: Vo2MaxRecord): Instant = record.time
}
