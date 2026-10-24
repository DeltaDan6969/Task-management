package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_config")
data class StoreConfigEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
