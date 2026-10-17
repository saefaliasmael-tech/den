package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["invoiceId"])]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val invoiceId: Long,
    val productId: Long? = null,
    val snapshotProductName: String,
    val snapshotUnitName: String,
    val snapshotUnitPrice: Long,
    val quantityScaled: Long,
    val quantityScale: Int = 1,
    val grossLineTotal: Long,
    val itemDiscount: Long = 0L,
    val netLineTotal: Long
)
