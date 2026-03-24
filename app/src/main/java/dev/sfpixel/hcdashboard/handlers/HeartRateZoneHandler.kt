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

    fun calculateTimeInZones(records: List<HeartRateRecord>, zones: List<HeartRateZone>): List<HeartRateZone> {
        zones.forEach { it.duration = Duration.ZERO }
        if (records.isEmpty()) return zones

        // We process each sample to be more precise
        records.forEach { record ->
            val samples = record.samples.sortedBy { it.time }
            for (i in samples.indices) {
                val sample = samples[i]
                val bpm = sample.beatsPerMinute
                
                // Estimate duration for this sample
                val sampleDuration = if (i < samples.size - 1) {
                    Duration.between(sample.time, samples[i + 1].time)
                } else {
                    // Last sample of the record, we can use 1 second or try to infer from record endTime
                    if (record.endTime > sample.time) Duration.between(sample.time, record.endTime)
                    else Duration.ofSeconds(1)
                }

                val matchingZone = zones.find { bpm >= it.minBpm && bpm < it.maxBpm }
                if (matchingZone != null) {
                    matchingZone.duration = matchingZone.duration.plus(sampleDuration)
                } else if (bpm >= zones.last().maxBpm) {
                    zones.last().duration = zones.last().duration.plus(sampleDuration)
                }
            }
        }
        
        return zones
    }

    fun calculateIntensityMinutes(zones: List<HeartRateZone>): Long {
        return zones.sumOf { zone ->
            val minutes = zone.duration.toMinutes()
            when {
                zone.name.contains("Zone 3") -> minutes
                zone.name.contains("Zone 4") || zone.name.contains("Zone 5") -> minutes * 2
                else -> 0L
            }
        }
    }
}
