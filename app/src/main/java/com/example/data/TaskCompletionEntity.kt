package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_completions",
    indices = [
        Index(value = ["taskId", "dateString"], unique = true),
        Index(value = ["dateString"]),
        Index(value = ["completedByUserId"])
    ]
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val taskTitle: String,
    val categoryName: String,
    val dateString: String, // e.g. "2026-10-16"
    val completedAt: Long = System.currentTimeMillis(),
    val completedByUserId: Long,
    val completedByUserName: String,
    val completedByUserInitials: String,
    val completedByUserRole: String,
    val isPartial: Boolean = false,
    val completedSubTasksJson: String = "",
    val outstandingSubTasksJson: String = "",
    val totalSubTasksCount: Int = 0,
    val completedSubTasksCount: Int = 0
)
