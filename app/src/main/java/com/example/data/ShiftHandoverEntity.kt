package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_handovers")
data class ShiftHandoverEntity(
    @PrimaryKey
    val id: String,
    val dateString: String = "",
    val targetDate: String = "",
    val shiftType: String = "MORNING_TO_EVENING", // MORNING_TO_EVENING, EVENING_TO_MORNING, IMMEDIATE, GENERAL
    val category: String = "STOCK_DELIVERY", // STOCK_DELIVERY, FRIDGE_WASTE, MAINTENANCE, CASH_SECURITY, GENERAL
    val priority: String = "NORMAL", // NORMAL, IMPORTANT, URGENT
    val message: String = "",
    val authorUserId: String = "",
    val authorName: String = "",
    val authorInitials: String = "",
    val authorRole: String = "STAFF",
    val timestamp: Long = System.currentTimeMillis(),
    val acknowledgedByCsv: String = "" // CSV of userIds who marked seen, e.g. "1,2,5"
)
