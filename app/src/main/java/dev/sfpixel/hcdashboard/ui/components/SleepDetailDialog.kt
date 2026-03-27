package dev.sfpixel.hcdashboard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SleepDetailDialog(
    sleepSession: SleepSessionRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Details") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val totalDuration = Duration.between(sleepSession.startTime, sleepSession.endTime)
                Text(
                    text = "Total Sleep: ${totalDuration.toHours()}h ${totalDuration.toMinutes() % 60}m",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sleepSession.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))} - ${sleepSession.endTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Sleep Stages", style = MaterialTheme.typography.titleSmall)

                if (sleepSession.stages.isNotEmpty()) {
                    val actualDurations = sleepSession.stages.groupBy { it.stage }
                        .mapValues { (_, stages) ->
                            stages.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
                        }

                    val stageNames = mapOf(
                        SleepSessionRecord.STAGE_TYPE_AWAKE to "Awake",
                        SleepSessionRecord.STAGE_TYPE_REM to "REM",
                        SleepSessionRecord.STAGE_TYPE_LIGHT to "Light",
                        SleepSessionRecord.STAGE_TYPE_DEEP to "Deep",
                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "Awake in bed",
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "Out of bed",
                        SleepSessionRecord.STAGE_TYPE_SLEEPING to "Sleeping",
                        SleepSessionRecord.STAGE_TYPE_UNKNOWN to "Unknown"
                    )

                    val stageColors = mapOf(
                        SleepSessionRecord.STAGE_TYPE_DEEP to Color(0xFF1A237E),
                        SleepSessionRecord.STAGE_TYPE_REM to Color(0xFF4FC3F7),
                        SleepSessionRecord.STAGE_TYPE_LIGHT to Color(0xFF3F51B5),
                        SleepSessionRecord.STAGE_TYPE_AWAKE to Color(0xFFE91E63)
                    )

                    val standardStages = listOf(
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_REM,
                        SleepSessionRecord.STAGE_TYPE_LIGHT,
                        SleepSessionRecord.STAGE_TYPE_DEEP
                    )
                    
                    val allStageTypes = (standardStages + actualDurations.keys).distinct()

                    allStageTypes.forEach { stageType ->
                        val millis = actualDurations[stageType] ?: 0L
                        val duration = Duration.ofMillis(millis)
                        
                        if (millis > 0 || stageType in standardStages) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .padding(end = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = stageColors[stageType] ?: MaterialTheme.colorScheme.outline,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            modifier = Modifier.fillMaxSize()
                                        ) {}
                                    }
                                    Text(stageNames[stageType] ?: "Other", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    text = "${duration.toHours()}h ${duration.toMinutes() % 60}m",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Text("No stage details available for this session.", style = MaterialTheme.typography.bodySmall)
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
