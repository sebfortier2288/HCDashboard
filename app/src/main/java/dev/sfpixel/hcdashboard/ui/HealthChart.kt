package dev.sfpixel.hcdashboard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import dev.sfpixel.hcdashboard.TimeRange
import dev.sfpixel.hcdashboard.handlers.HealthDataHandler
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun <T : Record> HealthChart(
    handler: HealthDataHandler<T>,
    records: List<T>,
    selectedRange: TimeRange,
    modifier: Modifier = Modifier,
    isColumnChart: Boolean = false,
    thresholdValue: Float? = null
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    
    val defaultBarColor = Color(0xFF2196F3) 
    val successBarColor = Color(0xFF4CAF50) 
    val warningBarColor = Color(0xFFFFB300) 
    val dangerBarColor = Color(0xFFF44336)  

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

    val rangeProvider = remember(isColumnChart) {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                if (isColumnChart) return 0.0
                val delta = maxY - minY
                return if (delta > 0) (minY - delta * 0.1).coerceAtLeast(0.0) else minY * 0.9
            }

            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val delta = maxY - minY
                return if (delta > 0) maxY + delta * 0.1 else maxY * 1.1
            }
        }
    }

    val thresholdLine = if (thresholdValue != null) {
        HorizontalLine(
            y = { thresholdValue.toDouble() },
            line = rememberLineComponent(
                fill = fill(thresholdColor),
                thickness = 2.dp,
                shape = DashedShape(
                    shape = Shape.Rectangle,
                    dashLengthDp = 4f,
                    gapLengthDp = 4f
                )
            ),
            labelComponent = rememberTextComponent(
                color = Color.White,
                background = rememberLineComponent(fill = fill(thresholdColor), thickness = 16.dp),
                padding = com.patrykandpatrick.vico.core.common.Insets(horizontalDp = 4f)
            ),
            label = { "Goal: ${thresholdValue.toInt()}h" }
        )
    } else null

    val columnProvider = if (isColumnChart) {
        val defaultLine = rememberLineComponent(fill = fill(defaultBarColor), thickness = 12.dp, shape = Shape.Rectangle)
        val successLine = rememberLineComponent(fill = fill(successBarColor), thickness = 12.dp, shape = Shape.Rectangle)
        val warningLine = rememberLineComponent(fill = fill(warningBarColor), thickness = 12.dp, shape = Shape.Rectangle)
        val dangerLine = rememberLineComponent(fill = fill(dangerBarColor), thickness = 12.dp, shape = Shape.Rectangle)
        
        remember(thresholdValue, defaultLine, successLine, warningLine, dangerLine) {
            object : ColumnCartesianLayer.ColumnProvider {
                override fun getColumn(
                    entry: ColumnCartesianLayerModel.Entry,
                    seriesIndex: Int,
                    extraStore: ExtraStore
                ): LineComponent {
                    if (thresholdValue == null) return defaultLine
                    return when {
                        entry.y >= thresholdValue -> successLine
                        entry.y >= thresholdValue - 1 -> warningLine
                        else -> dangerLine
                    }
                }

                override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent {
                    return defaultLine
                }
            }
        }
    } else null

    CartesianChartHost(
        chart = rememberCartesianChart(
            if (isColumnChart) {
                rememberColumnCartesianLayer(
                    columnProvider = columnProvider ?: ColumnCartesianLayer.ColumnProvider.series(),
                    rangeProvider = rangeProvider
                )
            } else {
                rememberLineCartesianLayer(rangeProvider = rangeProvider)
            },
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
            decorations = listOfNotNull(thresholdLine)
        ),
        model = model,
        modifier = modifier.height(250.dp).fillMaxWidth()
    )
}
