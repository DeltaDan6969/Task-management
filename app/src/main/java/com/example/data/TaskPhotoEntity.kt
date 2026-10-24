package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_photos",
    indices = [
        Index(value = ["taskId", "dateString"])
    ]
)
data class TaskPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val dateString: String,
    val photoUri: String,
    val authorUserId: Long,
    val authorName: String,
    val timestamp: Long = System.currentTimeMillis()
)
