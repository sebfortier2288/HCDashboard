package dev.sfpixel.hcdashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun <T : Record> HealthChart(
    handler: HealthDataHandler<T>,
    records: List<T>,
    selectedRange: TimeRange,
    modifier: Modifier = Modifier,
    isColumnChart: Boolean = false
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    val axisLabelComponent = rememberAxisLabelComponent(color = labelColor)
    val axisLineComponent = rememberAxisLineComponent(fill = fill(lineColor))
    val axisTickComponent = rememberAxisTickComponent(fill = fill(lineColor))

    val model = remember(records) {
        CartesianChartModel(
            if (isColumnChart) {
                ColumnCartesianLayerModel.build {
                    series(records.map { handler.getRecordValue(it).toDouble() })
                }
            } else {
                LineCartesianLayerModel.build {
                    series(records.map { handler.getRecordValue(it).toDouble() })
                }
            }
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            if (isColumnChart) rememberColumnCartesianLayer() else rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabelComponent,
                line = axisLineComponent,
                tick = axisTickComponent,
                title = handler.label,
                titleComponent = rememberTextComponent(color = labelColor),
                valueFormatter = CartesianValueFormatter { _, value, _ -> handler.formatValue(value.toFloat()) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabelComponent,
                line = axisLineComponent,
                tick = axisTickComponent,
                title = "Date",
                titleComponent = rememberTextComponent(color = labelColor),
                valueFormatter = CartesianValueFormatter { _, value, _ ->
                    val index = value.toInt().coerceIn(records.indices)
                    val timestamp = handler.getRecordTimestamp(records[index])
                    val pattern = when (selectedRange) {
                        TimeRange.Last24h -> "HH:mm"
                        TimeRange.LastYear, TimeRange.AllTime -> "dd/MM/yy"
                        else -> "dd/MM"
                    }
                    DateTimeFormatter.ofPattern(pattern)
                        .withZone(ZoneId.systemDefault())
                        .format(timestamp)
                }
            ),
            marker = rememberDefaultCartesianMarker(label = rememberTextComponent(color = labelColor)),
        ),
        model = model,
        modifier = modifier.height(250.dp).fillMaxWidth()
    )
}
