package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.InvoiceAllocation

@Dao
interface AllocationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: InvoiceAllocation): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocations(allocations: List<InvoiceAllocation>): List<Long>

    @Query("SELECT * FROM invoice_allocations WHERE id = :allocationId")
    suspend fun getAllocationById(allocationId: Long): InvoiceAllocation?

    @Query("SELECT * FROM invoice_allocations WHERE invoiceId = :invoiceId")
    suspend fun getAllocationsForInvoice(invoiceId: Long): List<InvoiceAllocation>

    @Query("SELECT * FROM invoice_allocations WHERE paymentId = :paymentId")
    suspend fun getAllocationsForPayment(paymentId: Long): List<InvoiceAllocation>

    @Query("""
        SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM invoice_allocations a
        INNER JOIN invoices i ON a.invoiceId = i.id
        INNER JOIN payments p ON a.paymentId = p.id
        WHERE a.invoiceId = :invoiceId
          AND a.status = 'ACTIVE'
          AND i.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    suspend fun getTotalActiveAllocatedForInvoice(invoiceId: Long): Long

    @Query("""
        SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM invoice_allocations a
        INNER JOIN invoices i ON a.invoiceId = i.id
        INNER JOIN payments p ON a.paymentId = p.id
        WHERE a.paymentId = :paymentId
          AND a.status = 'ACTIVE'
          AND i.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    suspend fun getTotalActiveAllocatedForPayment(paymentId: Long): Long

    @Query("""
        SELECT a.allocatedAmount FROM invoice_allocations a
        INNER JOIN invoices i ON a.invoiceId = i.id
        INNER JOIN payments p ON a.paymentId = p.id
        WHERE a.invoiceId = :invoiceId
          AND a.status = 'ACTIVE'
          AND i.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    suspend fun getActiveAllocationAmountsForInvoice(invoiceId: Long): List<Long>

    @Query("""
        SELECT a.allocatedAmount FROM invoice_allocations a
        INNER JOIN invoices i ON a.invoiceId = i.id
        INNER JOIN payments p ON a.paymentId = p.id
        WHERE a.paymentId = :paymentId
          AND a.status = 'ACTIVE'
          AND i.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    suspend fun getActiveAllocationAmountsForPayment(paymentId: Long): List<Long>

    @Query("""
        SELECT a.allocatedAmount FROM invoice_allocations a
        INNER JOIN invoices i ON a.invoiceId = i.id
        INNER JOIN payments p ON a.paymentId = p.id
        WHERE i.customerId = :customerId
          AND i.status = 'ACTIVE'
          AND p.customerId = :customerId
          AND p.status = 'ACTIVE'
          AND a.status = 'ACTIVE'
    """)
    suspend fun getActiveAllocationAmountsForCustomer(customerId: Long): List<Long>

    @Query("UPDATE invoice_allocations SET status = :status WHERE invoiceId = :invoiceId")
    suspend fun updateStatusForInvoice(invoiceId: Long, status: String): Int

    @Query("UPDATE invoice_allocations SET status = :status WHERE paymentId = :paymentId")
    suspend fun updateStatusForPayment(paymentId: Long, status: String): Int
}
