package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["customerId"])]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val customerId: Long,
    val invoiceNumber: String,
    val status: String = "ACTIVE",
    val subTotal: Long,
    val discountAmount: Long = 0L,
    val finalTotal: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L
)
