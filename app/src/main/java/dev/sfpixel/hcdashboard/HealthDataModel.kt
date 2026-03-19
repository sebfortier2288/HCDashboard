package dev.sfpixel.hcdashboard

import androidx.health.connect.client.records.Record
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Interface pour définir comment charger et traiter un type de donnée Health Connect
 */
interface HealthDataHandler<T : Record> {
    val recordType: KClass<T>
    val label: String
    
    // Pour l'affichage sur l'axe Y
    fun formatValue(value: Float): String
    
    // Pour extraire la valeur numérique d'un record
    fun getRecordValue(record: T): Float
    
    // Pour extraire le timestamp d'un record
    fun getRecordTimestamp(record: T): Instant
}
