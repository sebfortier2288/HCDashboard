package dev.sfpixel.hcdashboard.models

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class DashboardTab(val title: String) {
    Today("Today"),
    History("History"),
    Activities("Activities")
}

enum class PeriodType(val label: String) {
    Week("Week"),
    Month("Month"),
    Year("Year")
}

object HealthUtils {
    fun calculatePeriodRange(periodType: PeriodType, offset: Long): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        
        return when (periodType) {
            PeriodType.Week -> {
                val baseDate = now.plusWeeks(offset)
                val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zone).toInstant()
                val end = start.plus(7, ChronoUnit.DAYS)
                start to end
            }
            PeriodType.Month -> {
                val baseDate = now.plusMonths(offset)
                val start = baseDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant()
                val end = baseDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(zone).toInstant()
                start to end
            }
            PeriodType.Year -> {
                val baseDate = now.plusYears(offset)
                val start = baseDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant()
                val end = baseDate.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).atStartOfDay(zone).toInstant()
                start to end
            }
        }
    }

    fun formatPeriodLabel(periodType: PeriodType, offset: Long): String {
        val now = LocalDate.now()
        
        return when (periodType) {
            PeriodType.Week -> {
                val baseDate = now.plusWeeks(offset)
                val start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val end = start.plusDays(6)
                val formatter = DateTimeFormatter.ofPattern("MMM d")
                if (start.year == end.year) {
                    "${start.format(formatter)} - ${end.format(formatter)}, ${start.year}"
                } else {
                    "${start.format(formatter)} ${start.year} - ${end.format(formatter)} ${end.year}"
                }
            }
            PeriodType.Month -> {
                val baseDate = now.plusMonths(offset)
                baseDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
            PeriodType.Year -> {
                val baseDate = now.plusYears(offset)
                baseDate.year.toString()
            }
        }
    }

    fun getGroupingDate(instant: Instant, period: PeriodType): LocalDate {
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return if (period == PeriodType.Year) localDate.withDayOfMonth(1) else localDate
    }
}
