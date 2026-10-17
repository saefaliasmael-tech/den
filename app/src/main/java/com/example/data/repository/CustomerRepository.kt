package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.AllocationDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.InvoiceDao
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.AuditLog
import com.example.data.local.entity.Customer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class CustomerRepository(
    private val db: AppDatabase,
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val allocationDao: AllocationDao,
    private val auditLogDao: AuditLogDao
) {

    private val customerAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Customer::class.java)

    /**
     * Calculates customer net debt strictly using Long and Math exact arithmetic.
     * Rejects any negative values in finalTotals or allocations before folding.
     * Throws ArithmeticException on overflow or IllegalStateException if debt is negative.
     */
    suspend fun calculateCustomerDebt(customerId: Long): Long {
        val finalTotals = invoiceDao.getActiveInvoiceFinalTotalsForCustomer(customerId)
        val allocations = allocationDao.getActiveAllocationAmountsForCustomer(customerId)

        for (v in finalTotals) {
            if (v < 0L) {
                throw IllegalArgumentException("Invoice finalTotal cannot be negative: $v")
            }
        }
        for (v in allocations) {
            if (v < 0L) {
                throw IllegalArgumentException("Allocation amount cannot be negative: $v")
            }
        }

        val totalInvoices = finalTotals.fold(0L) { acc, v -> Math.addExact(acc, v) }
        val totalAllocations = allocations.fold(0L) { acc, v -> Math.addExact(acc, v) }

        val debt = Math.subtractExact(totalInvoices, totalAllocations)
        if (debt < 0L) {
            throw IllegalStateException("Customer debt cannot be negative: $debt")
        }
        return debt
    }

    /**
     * Archives a customer within an atomic Room transaction.
     * Allowed ONLY when:
     * 1. Customer exists.
     * 2. Customer debt is strictly zero (0L).
     * Records an AuditLog entry with safe JSON snapshots upon successful archival.
     */
    suspend fun archiveCustomer(customerId: Long): Result<Unit> {
        return try {
            db.withTransaction {
                val customer = customerDao.getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found with id: $customerId")

                if (customer.isArchived) {
                    return@withTransaction
                }

                val debt = calculateCustomerDebt(customerId)
                if (debt > 0L) {
                    throw IllegalStateException("Cannot archive customer with remaining debt: $debt")
                }

                val oldJson = customerAdapter.toJson(customer)
                val updatedCustomer = customer.copy(
                    isArchived = true,
                    updatedAt = System.currentTimeMillis()
                )
                val newJson = customerAdapter.toJson(updatedCustomer)

                customerDao.updateCustomer(updatedCustomer)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "CUSTOMER",
                        entityId = customerId,
                        actionType = "ARCHIVE",
                        reason = "Customer archived with zero debt",
                        oldSnapshotJson = oldJson,
                        newSnapshotJson = newJson,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllCustomersFlow(isArchived: Boolean = false): kotlinx.coroutines.flow.Flow<List<Customer>> {
        return customerDao.getAllCustomersFlow(isArchived)
    }

    suspend fun getAllCustomers(isArchived: Boolean = false): List<Customer> {
        return customerDao.getAllCustomers(isArchived)
    }

    suspend fun getCustomerById(customerId: Long): Customer? {
        return customerDao.getCustomerById(customerId)
    }

    suspend fun searchCustomers(query: String): List<Customer> {
        return customerDao.searchCustomers(query)
    }

    suspend fun createCustomer(
        name: String,
        phone: String,
        whatsapp: String? = null,
        notes: String? = null,
        reason: String = "Create customer"
    ): Result<Customer> {
        return try {
            if (name.isBlank()) {
                throw IllegalArgumentException("Customer name cannot be blank")
            }
            if (phone.isBlank()) {
                throw IllegalArgumentException("Customer phone cannot be blank")
            }
            val created = db.withTransaction {
                val now = System.currentTimeMillis()
                val customer = Customer(
                    id = 0L,
                    name = name.trim(),
                    phone = phone.trim(),
                    whatsapp = whatsapp?.trim()?.takeIf { it.isNotBlank() },
                    notes = notes?.trim()?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false
                )
                val id = customerDao.insertCustomer(customer)
                if (id <= 0L) {
                    throw IllegalStateException("Failed to insert customer")
                }
                val saved = customerDao.getCustomerById(id)
                    ?: throw IllegalStateException("Saved customer not found: $id")

                val newJson = customerAdapter.toJson(saved)
                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "CUSTOMER",
                        entityId = id,
                        actionType = "CREATE",
                        reason = reason,
                        oldSnapshotJson = null,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )
                saved
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
