package dev.sfpixel.hcdashboard.handlers

import androidx.health.connect.client.records.HeartRateRecord
import java.time.Duration
import java.time.LocalDate
import java.time.Period

data class HeartRateZone(
    val name: String,
    val minPercent: Double,
    val maxPercent: Double,
    val minBpm: Int,
    val maxBpm: Int,
    var duration: Duration = Duration.ZERO
)

object HeartRateZoneHandler {
    fun calculateMaxHeartRate(birthDate: LocalDate): Int {
        val age = Period.between(birthDate, LocalDate.now()).years
        return 220 - age
    }

    fun getZones(maxHr: Int): List<HeartRateZone> {
        return listOf(
            HeartRateZone("Zone 1 (Very Light)", 0.5, 0.6, (maxHr * 0.5).toInt(), (maxHr * 0.6).toInt()),
            HeartRateZone("Zone 2 (Light)", 0.6, 0.7, (maxHr * 0.6).toInt(), (maxHr * 0.7).toInt()),
            HeartRateZone("Zone 3 (Moderate)", 0.7, 0.8, (maxHr * 0.7).toInt(), (maxHr * 0.8).toInt()),
            HeartRateZone("Zone 4 (Hard)", 0.8, 0.9, (maxHr * 0.8).toInt(), (maxHr * 0.9).toInt()),
            HeartRateZone("Zone 5 (Maximum)", 0.9, 1.0, (maxHr * 0.9).toInt(), maxHr)
        )
    }

    fun calculateTimeInZones(samples: List<HeartRateRecord>, zones: List<HeartRateZone>): List<HeartRateZone> {
        if (samples.isEmpty()) return zones

        zones.forEach { it.duration = Duration.ZERO }

        for (i in 0 until samples.size) {
            val record = samples[i]
            val duration = if (record.startTime != record.endTime) {
                Duration.between(record.startTime, record.endTime)
            } else if (i < samples.size - 1) {
                Duration.between(record.startTime, samples[i+1].startTime)
            } else {
                Duration.ofSeconds(1)
            }

            val avgBpm = record.samples.map { it.beatsPerMinute }.average()
            
            val matchingZone = zones.find { avgBpm >= it.minBpm && avgBpm < it.maxBpm }
            if (matchingZone != null) {
                matchingZone.duration = matchingZone.duration.plus(duration)
            } else if (avgBpm >= zones.last().maxBpm) {
                zones.last().duration = zones.last().duration.plus(duration)
            }
        }
        
        return zones
    }
}
