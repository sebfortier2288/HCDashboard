package dev.sfpixel.hcdashboard.handlers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Duration

object ExerciseHandler {
    val recordType = ExerciseSessionRecord::class
    val label: String get() = "Exercise"

    fun getExerciseName(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
            else -> "Exercise"
        }
    }

    fun getExerciseIcon(type: Int): ImageVector {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> Icons.AutoMirrored.Filled.DirectionsBike
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> Icons.Default.Pool
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> Icons.Default.SelfImprovement
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> Icons.Default.FitnessCenter
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> Icons.Default.Hiking
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> Icons.Default.SportsGymnastics
            else -> Icons.Default.Timer
        }
    }

    fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
