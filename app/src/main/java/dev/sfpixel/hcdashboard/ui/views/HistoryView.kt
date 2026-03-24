package dev.sfpixel.hcdashboard.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.*
import dev.sfpixel.hcdashboard.PeriodType
import dev.sfpixel.hcdashboard.formatPeriodLabel
import dev.sfpixel.hcdashboard.handlers.*
import dev.sfpixel.hcdashboard.ui.HealthChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryView(
    periodType: PeriodType,
    offset: Long,
    onPeriodTypeChanged: (PeriodType) -> Unit,
    onOffsetChanged: (Long) -> Unit,
    isLoading: Boolean,
    weights: List<WeightRecord>,
    bodyFats: List<BodyFatRecord>,
    stepsProcessed: List<StepsRecord>,
    sleepProcessed: List<SleepSessionRecord>,
    restingHeartRateProcessed: List<RestingHeartRateRecord>,
    hrvProcessed: List<HeartRateVariabilityRmssdRecord>,
    vo2MaxProcessed: List<Vo2MaxRecord>
) {
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PeriodType.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = periodType == period,
                    onClick = { 
                        onPeriodTypeChanged(period)
                        onOffsetChanged(0) 
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PeriodType.entries.size)
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
                    periodType = periodType,
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
                    periodType = periodType,
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
                    periodType = periodType,
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
                    periodType = periodType,
                    isColumnChart = false
                )
            } else {
                Text("No VO2 Max data found.", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Weight Evolution", style = MaterialTheme.typography.titleMedium)
            if (weights.isNotEmpty()) {
                HealthChart(handler = WeightHandler, records = weights, periodType = periodType)
            } else {
                Text("No weight data found.", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Body Fat Evolution", style = MaterialTheme.typography.titleMedium)
            if (bodyFats.isNotEmpty()) {
                HealthChart(handler = BodyFatHandler, records = bodyFats, periodType = periodType)
            } else {
                Text("No body fat data found.", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Daily Activity", style = MaterialTheme.typography.titleMedium)
            if (stepsProcessed.isNotEmpty()) {
                HealthChart(handler = StepsHandler, records = stepsProcessed, periodType = periodType, isColumnChart = true)
            } else {
                Text("No activity data found.", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
