package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val entityType: String,
    val entityId: Long,
    val actionType: String,
    val reason: String,
    val oldSnapshotJson: String? = null,
    val newSnapshotJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
