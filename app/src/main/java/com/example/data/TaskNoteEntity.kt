package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_notes",
    indices = [
        Index(value = ["taskId", "dateString"])
    ]
)
data class TaskNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val dateString: String,
    val text: String,
    val authorUserId: Long,
    val authorName: String,
    val authorInitials: String,
    val timestamp: Long = System.currentTimeMillis()
)
