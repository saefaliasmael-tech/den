package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Invoice

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice): Int

    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    suspend fun getInvoiceById(invoiceId: Long): Invoice?

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getInvoicesForCustomer(customerId: Long): List<Invoice>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND status = 'ACTIVE' ORDER BY createdAt DESC")
    suspend fun getActiveInvoicesForCustomer(customerId: Long): List<Invoice>

    @Query("SELECT finalTotal FROM invoices WHERE customerId = :customerId AND status = 'ACTIVE'")
    suspend fun getActiveInvoiceFinalTotalsForCustomer(customerId: Long): List<Long>

    @Query("UPDATE invoices SET status = :status, updatedAt = :updatedAt WHERE id = :invoiceId")
    suspend fun updateStatus(invoiceId: Long, status: String, updatedAt: Long = System.currentTimeMillis()): Int
}
