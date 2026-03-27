package dev.sfpixel.hcdashboard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import dev.sfpixel.hcdashboard.models.PeriodType
import dev.sfpixel.hcdashboard.handlers.ExerciseHandler
import dev.sfpixel.hcdashboard.handlers.HeartRateHandler
import dev.sfpixel.hcdashboard.handlers.HeartRateZoneHandler
import dev.sfpixel.hcdashboard.ui.HealthChart
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityDetailDialog(
    selectedActivity: ExerciseSessionRecord,
    isHeartRateLoading: Boolean,
    activityHeartRateSamples: List<HeartRateRecord>,
    birthDate: LocalDate?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selectedActivity.title ?: ExerciseHandler.getExerciseName(selectedActivity.exerciseType)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Start: ${selectedActivity.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                Text("End: ${selectedActivity.endTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}")
                val duration = Duration.between(selectedActivity.startTime, selectedActivity.endTime)
                Text("Duration: ${ExerciseHandler.formatDuration(duration)}")
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Heart Rate", style = MaterialTheme.typography.titleSmall)
                
                if (isHeartRateLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (activityHeartRateSamples.isNotEmpty()) {
                    HealthChart(
                        handler = HeartRateHandler,
                        records = activityHeartRateSamples,
                        periodType = PeriodType.Week, // Dummy value for detail view
                        modifier = Modifier.height(200.dp)
                    )
                    
                    birthDate?.let { date ->
                        val maxHr = HeartRateZoneHandler.calculateMaxHeartRate(date)
                        val zones = HeartRateZoneHandler.getZones(maxHr)
                        val calculatedZones = HeartRateZoneHandler.calculateTimeInZones(activityHeartRateSamples, zones)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Zones HR (Max: $maxHr bpm)", style = MaterialTheme.typography.titleSmall)
                        calculatedZones.forEach { zone ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${zone.name}:", style = MaterialTheme.typography.bodySmall)
                                Text(ExerciseHandler.formatDuration(zone.duration), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        val intensityMinutes = HeartRateZoneHandler.calculateIntensityMinutes(calculatedZones)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Intensity minutes (Z3+): $intensityMinutes min", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } ?: run {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Set your birth date in settings to see HR zones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No heart rate data found for this activity duration.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                selectedActivity.notes?.let {
                    if (it.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes: $it")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
