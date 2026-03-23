package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.HeartRateRecord
import java.time.Instant
import kotlin.math.roundToInt

object HeartRateHandler : HealthDataHandler<HeartRateRecord> {
    override val recordType = HeartRateRecord::class
    override val label = "Heart Rate (bpm)"

    override fun formatValue(value: Float): String = value.roundToInt().toString()

    override fun getRecordValue(record: HeartRateRecord): Float {
        if (record.samples.isEmpty()) return 0f
        // Pour les graphiques de détails, on utilise des records factices avec 1 seul échantillon
        if (record.samples.size == 1) return record.samples[0].beatsPerMinute.toFloat()
        // Fallback pour les records complets
        return record.samples.map { it.beatsPerMinute }.average().toFloat()
    }

    override fun getRecordTimestamp(record: HeartRateRecord): Instant = record.startTime
}
