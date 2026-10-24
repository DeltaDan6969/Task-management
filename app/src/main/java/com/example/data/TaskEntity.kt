package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object TaskPriority {
    const val LOW = "LOW"
    const val MEDIUM = "MEDIUM"
    const val HIGH = "HIGH"

    fun priorityWeight(priority: String): Int {
        return when (priority.uppercase()) {
            HIGH -> 3
            MEDIUM -> 2
            LOW -> 1
            else -> 2
        }
    }
}

object DaysOfWeek {
    const val ALL = "ALL"
    const val MONDAY = "MONDAY"
    const val TUESDAY = "TUESDAY"
    const val WEDNESDAY = "WEDNESDAY"
    const val THURSDAY = "THURSDAY"
    const val FRIDAY = "FRIDAY"
    const val SATURDAY = "SATURDAY"
    const val SUNDAY = "SUNDAY"

    val ORDERED_DAYS = listOf(
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY
    )

    val SHORT_NAMES = mapOf(
        MONDAY to "MON",
        TUESDAY to "TUE",
        WEDNESDAY to "WED",
        THURSDAY to "THU",
        FRIDAY to "FRI",
        SATURDAY to "SAT",
        SUNDAY to "SUN"
    )

    val SHORT_TO_FULL = mapOf(
        "MON" to MONDAY,
        "TUE" to TUESDAY,
        "WED" to WEDNESDAY,
        "THU" to THURSDAY,
        "FRI" to FRIDAY,
        "SAT" to SATURDAY,
        "SUN" to SUNDAY
    )

    const val ONE_TIME = "ONE_TIME"

    fun isOneTime(daysOfWeekString: String?): Boolean {
        return daysOfWeekString.equals(ONE_TIME, ignoreCase = true)
    }

    fun runsOnDay(daysOfWeekString: String?, targetDay: String): Boolean {
        if (daysOfWeekString.isNullOrBlank() || daysOfWeekString.equals(ALL, ignoreCase = true)) {
            return true
        }
        if (isOneTime(daysOfWeekString)) {
            return false
        }
        val cleanTarget = targetDay.trim().uppercase()
        val targetFull = SHORT_TO_FULL[cleanTarget] ?: cleanTarget
        val daysList = daysOfWeekString.split(",").map { it.trim().uppercase() }
        return daysList.any { day ->
            day == ALL ||
            day == targetFull ||
            SHORT_TO_FULL[day] == targetFull ||
            SHORT_NAMES[targetFull] == day
        }
    }

    fun formatDisplayDays(daysOfWeekString: String?): String {
        if (daysOfWeekString.isNullOrBlank() || daysOfWeekString.equals(ALL, ignoreCase = true)) {
            return "EVERY DAY"
        }
        if (isOneTime(daysOfWeekString)) {
            return "ONE-TIME (ROLLS OVER)"
        }
        val daysList = daysOfWeekString.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
        if (daysList.size >= 7 || daysList.contains(ALL)) {
            return "EVERY DAY"
        }
        val fullDays = daysList.map { SHORT_TO_FULL[it] ?: it }
        val weekdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
        val weekends = listOf(SATURDAY, SUNDAY)

        if (fullDays.containsAll(weekdays) && fullDays.size == 5) {
            return "MON - FRI"
        }
        if (fullDays.containsAll(weekends) && fullDays.size == 2) {
            return "SAT - SUN"
        }

        return ORDERED_DAYS
            .filter { fullDays.contains(it) }
            .map { SHORT_NAMES[it] ?: it.take(3) }
            .joinToString(", ")
    }
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val bulletPoints: String = "", // newline separated bullet descriptions
    val priority: String = TaskPriority.MEDIUM, // "LOW", "MEDIUM", "HIGH"
    val daysOfWeek: String = DaysOfWeek.ALL, // "ALL", comma-separated, or "ONE_TIME"
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isOneTime: Boolean = false,
    val oneTimeStartDate: String = "", // e.g. "2026-10-22"
    val completedDate: String = ""     // e.g. "2026-10-22" (date when one-time task was signed off)
) {
    val isEffectivelyOneTime: Boolean
        get() = isOneTime || daysOfWeek.equals(DaysOfWeek.ONE_TIME, ignoreCase = true)

    fun effectiveStartDate(): String {
        if (oneTimeStartDate.isNotBlank()) return oneTimeStartDate
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(java.util.Date(createdAt))
        } catch (_: Exception) {
            "2000-01-01"
        }
    }

    fun isScheduledForDate(
        targetDateStr: String,
        dayOfWeekName: String,
        allCompletions: List<TaskCompletionEntity> = emptyList()
    ): Boolean {
        if (!active) return false

        if (isEffectivelyOneTime) {
            val start = effectiveStartDate()
            // Not scheduled before creation/start date
            if (targetDateStr < start) return false

            // If task was completed on a specific date:
            // It continues up to that completed date (and appears as completed on that date),
            // but does NOT appear on subsequent days after completion!
            if (completedDate.isNotBlank()) {
                return targetDateStr <= completedDate
            }

            // Check if any non-partial completion exists for this task
            val fullComp = allCompletions.find { it.taskId == id && !it.isPartial }
            if (fullComp != null) {
                return targetDateStr <= fullComp.dateString
            }

            // Not yet completed: rolls over and continues to appear on targetDateStr!
            return true
        }

        // Regular recurring weekly schedule
        return DaysOfWeek.runsOnDay(daysOfWeek, dayOfWeekName)
    }

    fun isRolledOver(targetDateStr: String): Boolean {
        if (!isEffectivelyOneTime) return false
        val start = effectiveStartDate()
        return targetDateStr > start
    }
}

