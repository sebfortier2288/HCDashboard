package dev.sfpixel.hcdashboard.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import dev.sfpixel.hcdashboard.ActivityPeriodType
import dev.sfpixel.hcdashboard.formatPeriodLabel
import dev.sfpixel.hcdashboard.handlers.ExerciseHandler
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesView(
    periodType: ActivityPeriodType,
    offset: Long,
    onPeriodTypeChanged: (ActivityPeriodType) -> Unit,
    onOffsetChanged: (Long) -> Unit,
    isLoading: Boolean,
    exerciseSessions: List<ExerciseSessionRecord>,
    onActivityClick: (ExerciseSessionRecord) -> Unit
) {
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ActivityPeriodType.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = periodType == period,
                    onClick = { 
                        onPeriodTypeChanged(period)
                        onOffsetChanged(0) 
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ActivityPeriodType.entries.size)
                ) { Text(period.label) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onOffsetChanged(offset - 1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            
            Text(
                text = formatPeriodLabel(periodType, offset),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { onOffsetChanged(offset + 1) },
                enabled = offset < 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (exerciseSessions.isEmpty()) {
                Text("No activities found.", modifier = Modifier.padding(vertical = 8.dp))
            } else {
                exerciseSessions.forEach { session ->
                    ActivityTile(session, onClick = { onActivityClick(session) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ActivityTile(session: ExerciseSessionRecord, onClick: () -> Unit) {
    val duration = Duration.between(session.startTime, session.endTime)
    val durationText = ExerciseHandler.formatDuration(duration)
    
    val startTime = session.startTime.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ExerciseHandler.getExerciseIcon(session.exerciseType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: ExerciseHandler.getExerciseName(session.exerciseType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = startTime.format(formatter),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = durationText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
