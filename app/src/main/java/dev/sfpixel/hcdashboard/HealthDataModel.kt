package dev.sfpixel.hcdashboard

import androidx.health.connect.client.records.Record
import java.time.Instant
import kotlin.reflect.KClass

interface HealthDataHandler<T : Record> {
    val recordType: KClass<T>
    val label: String
    
    fun formatValue(value: Float): String

    fun getRecordValue(record: T): Float
    
    fun getRecordTimestamp(record: T): Instant
}
