package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "per_app_rules")
data class PerAppRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
