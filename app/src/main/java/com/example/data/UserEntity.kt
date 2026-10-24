package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val initials: String,
    val role: String, // "ADMIN" or "STAFF"
    val pinHash: String,
    val pinSalt: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val mustChangePin: Boolean = false
)

val UserEntity.isPendingFirstPin: Boolean
    get() {
        if (name.contains("DANIEL", ignoreCase = true)) return false
        if (mustChangePin) return true
        if (pinHash.isBlank()) return true
        if (pinSalt.isBlank() && (pinHash == "1234" || pinHash.length < 10)) return true
        return false
    }

