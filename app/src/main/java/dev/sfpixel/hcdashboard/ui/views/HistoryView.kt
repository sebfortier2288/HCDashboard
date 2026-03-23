package dev.sfpixel.hcdashboard.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.*
import dev.sfpixel.hcdashboard.TimeRange
import dev.sfpixel.hcdashboard.handlers.*
import dev.sfpixel.hcdashboard.ui.HealthChart

@Composable
fun HistoryView(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    isLoading: Boolean,
    weights: List<WeightRecord>,
    bodyFats: List<BodyFatRecord>,
    stepsProcessed: List<StepsRecord>,
    sleepProcessed: List<SleepSessionRecord>,
    restingHeartRateProcessed: List<RestingHeartRateRecord>,
    hrvProcessed: List<HeartRateVariabilityRmssdRecord>,
    vo2MaxProcessed: List<Vo2MaxRecord>
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TimeRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRange.entries.size)
            ) { Text(range.label) }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Text("Sleep History (Hours)", style = MaterialTheme.typography.titleMedium)
        if (sleepProcessed.isNotEmpty()) {
            HealthChart(
                handler = SleepHandler, 
                records = sleepProcessed,
                selectedRange = selectedRange,
                isColumnChart = true,
                thresholdValue = 7f
            )
        } else {
            Text("No sleep data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Resting Heart Rate History", style = MaterialTheme.typography.titleMedium)
        if (restingHeartRateProcessed.isNotEmpty()) {
            HealthChart(
                handler = RestingHeartRateHandler, 
                records = restingHeartRateProcessed, 
                selectedRange = selectedRange,
                isColumnChart = false
            )
        } else {
            Text("No heart rate data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("HRV History (ms)", style = MaterialTheme.typography.titleMedium)
        if (hrvProcessed.isNotEmpty()) {
            HealthChart(
                handler = HeartRateVariabilityHandler, 
                records = hrvProcessed,
                selectedRange = selectedRange,
                isColumnChart = false
            )
        } else {
            Text("No HRV data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("VO2 Max History", style = MaterialTheme.typography.titleMedium)
        if (vo2MaxProcessed.isNotEmpty()) {
            HealthChart(
                handler = Vo2MaxHandler, 
                records = vo2MaxProcessed,
                selectedRange = selectedRange,
                isColumnChart = false
            )
        } else {
            Text("No VO2 Max data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Weight Evolution", style = MaterialTheme.typography.titleMedium)
        if (weights.isNotEmpty()) {
            HealthChart(handler = WeightHandler, records = weights, selectedRange = selectedRange)
        } else {
            Text("No weight data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Body Fat Evolution", style = MaterialTheme.typography.titleMedium)
        if (bodyFats.isNotEmpty()) {
            HealthChart(handler = BodyFatHandler, records = bodyFats, selectedRange = selectedRange)
        } else {
            Text("No body fat data found.", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Daily Activity", style = MaterialTheme.typography.titleMedium)
        if (stepsProcessed.isNotEmpty()) {
            HealthChart(handler = StepsHandler, records = stepsProcessed, selectedRange = selectedRange, isColumnChart = true)
        } else {
            Text("No activity data found.", modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
