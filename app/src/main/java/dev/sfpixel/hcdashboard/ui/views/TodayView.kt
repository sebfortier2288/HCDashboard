package dev.sfpixel.hcdashboard.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration

@Composable
fun TodayView(steps: Long, sleepDuration: Duration?, restingHeartRate: Long?, hrvAvg: Double?, hrvBaseline: Double?) {
    val sleepText = sleepDuration?.let {
        val hours = it.toHours()
        val minutes = it.toMinutes() % 60
        "${hours}h ${minutes}m"
    } ?: "No data"

    val sleepMinutes = sleepDuration?.toMinutes() ?: 0L
    val sleepValueColor = when {
        sleepDuration == null -> MaterialTheme.colorScheme.onSecondaryContainer
        sleepMinutes >= 7 * 60 -> Color(0xFF4CAF50) // Green >= 7h
        sleepMinutes >= 6 * 60 -> Color(0xFFFFB300) // Yellow >= 6h
        else -> Color(0xFFF44336) // Red < 6h
    }

    SummaryCard(
        title = "Sleep Last Night",
        value = sleepText,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        valueColor = sleepValueColor
    )

    Spacer(modifier = Modifier.height(12.dp))

    SummaryCard(
        title = "Resting HR Today",
        value = restingHeartRate?.let { "$it bpm" } ?: "No data",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )

    Spacer(modifier = Modifier.height(12.dp))

    val hrvValueColor = if (hrvAvg != null && hrvBaseline != null) {
        val ratio = hrvAvg / hrvBaseline
        when {
            ratio < 0.8 -> Color(0xFFF44336)        // Red
            ratio !in 0.9..1.2 -> Color(0xFFFFB300) // Yellow
            else -> Color(0xFF4CAF50)               // Green
        }
    } else MaterialTheme.colorScheme.onSurfaceVariant

    SummaryCard(
        title = "HRV (7d Avg)",
        value = hrvAvg?.let { "${it.toInt()} ms" } ?: "No data",
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        valueColor = hrvValueColor
    )

    Spacer(modifier = Modifier.height(12.dp))

    SummaryCard(
        title = "Steps Today",
        value = steps.toString(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    valueColor: Color = contentColor
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp
                ),
                color = valueColor
            )
        }
    }
}
