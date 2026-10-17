package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.AllocationDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.InvoiceDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.AuditLog
import com.example.data.local.entity.InvoiceAllocation
import com.example.data.local.entity.Payment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class AllocationRequest(
    val invoiceId: Long,
    val allocatedAmount: Long
)

data class PaymentWithAllocations(
    val payment: Payment,
    val allocations: List<InvoiceAllocation>
)

class PaymentRepository(
    private val db: AppDatabase,
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val paymentDao: PaymentDao,
    private val allocationDao: AllocationDao,
    private val auditLogDao: AuditLogDao
) {

    private val snapshotAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(PaymentWithAllocations::class.java)

    private suspend fun calculateCustomerRemainingDebtInternal(customerId: Long): Long {
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

    private suspend fun calculateInvoiceRemainingBalanceInternal(invoiceId: Long, invoiceFinalTotal: Long): Long {
        if (invoiceFinalTotal < 0L) {
            throw IllegalStateException("Invoice finalTotal cannot be negative: $invoiceFinalTotal")
        }
        val activeAmounts = allocationDao.getActiveAllocationAmountsForInvoice(invoiceId)
        for (v in activeAmounts) {
            if (v < 0L) {
                throw IllegalStateException("Allocation amount cannot be negative: $v")
            }
        }
        val alreadyAllocated = activeAmounts.fold(0L) { acc, v -> Math.addExact(acc, v) }
        val remaining = Math.subtractExact(invoiceFinalTotal, alreadyAllocated)
        if (remaining < 0L) {
            throw IllegalStateException("Invoice $invoiceId has negative remaining balance: $remaining")
        }
        return remaining
    }

    /**
     * Records a payment and its explicit invoice allocations atomically inside a single transaction.
     * Enforces:
     * - Strictly positive payment amount and allocation amounts.
     * - Non-empty allocations with no duplicate invoice IDs.
     * - Exact equality between sum(allocations) and payment amount (no unallocated payments).
     * - Payment amount does not exceed customer's total remaining debt (no credit balance).
     * - Each target invoice exists, belongs to the customer, is ACTIVE, and its allocation does not exceed its remaining unpaid balance.
     * - Saves AuditLog with full PaymentWithAllocations snapshot.
     */
    suspend fun recordPayment(
        customerId: Long,
        amount: Long,
        allocations: List<AllocationRequest>,
        paymentMethod: String? = null,
        notes: String? = null,
        receivedAt: Long = System.currentTimeMillis(),
        reason: String = "Record payment"
    ): Result<PaymentWithAllocations> {
        return try {
            if (amount <= 0L) {
                throw IllegalArgumentException("Payment amount must be strictly positive: $amount")
            }
            if (allocations.isEmpty()) {
                throw IllegalArgumentException("Payment must have at least one allocation; unallocated payments are not allowed")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }

            val seenInvoices = mutableSetOf<Long>()
            var sumAllocations = 0L
            for (req in allocations) {
                if (req.invoiceId <= 0L) {
                    throw IllegalArgumentException("Invalid invoiceId in allocation: ${req.invoiceId}")
                }
                if (req.allocatedAmount <= 0L) {
                    throw IllegalArgumentException("Allocated amount must be strictly positive: ${req.allocatedAmount}")
                }
                if (!seenInvoices.add(req.invoiceId)) {
                    throw IllegalArgumentException("Duplicate invoiceId in allocations: ${req.invoiceId}")
                }
                sumAllocations = Math.addExact(sumAllocations, req.allocatedAmount)
            }

            if (sumAllocations != amount) {
                throw IllegalArgumentException(
                    "Sum of allocations ($sumAllocations) must equal payment amount ($amount) exactly"
                )
            }

            val created = db.withTransaction {
                insertValidatedPaymentInTransaction(
                    customerId = customerId,
                    amount = amount,
                    allocations = allocations,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    receivedAt = receivedAt,
                    reason = reason
                )
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records a payment and automatically distributes the amount across the customer's oldest
     * ACTIVE unpaid invoices (FIFO) within a single atomic transaction.
     * Rejects if the payment amount is <= 0 or exceeds the customer's total remaining debt.
     */
    suspend fun recordPaymentAutoAllocate(
        customerId: Long,
        amount: Long,
        paymentMethod: String? = null,
        notes: String? = null,
        receivedAt: Long = System.currentTimeMillis(),
        reason: String = "Record payment (auto-allocated)"
    ): Result<PaymentWithAllocations> {
        return try {
            if (amount <= 0L) {
                throw IllegalArgumentException("Payment amount must be strictly positive: $amount")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }

            val created = db.withTransaction {
                val customer = customerDao.getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found with id: $customerId")
                if (customer.isArchived) {
                    throw IllegalStateException("Cannot record payment for archived customer: $customerId")
                }

                val currentDebt = calculateCustomerRemainingDebtInternal(customerId)
                if (amount > currentDebt) {
                    throw IllegalArgumentException(
                        "Payment amount ($amount) exceeds customer remaining debt ($currentDebt)"
                    )
                }

                val activeInvoices = invoiceDao.getActiveInvoicesForCustomer(customerId)
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))

                var remainingToAllocate = amount
                val generatedAllocations = mutableListOf<AllocationRequest>()

                for (invoice in activeInvoices) {
                    if (remainingToAllocate == 0L) break
                    val invoiceRemaining = calculateInvoiceRemainingBalanceInternal(invoice.id, invoice.finalTotal)
                    if (invoiceRemaining > 0L) {
                        val allocated = minOf(remainingToAllocate, invoiceRemaining)
                        generatedAllocations.add(
                            AllocationRequest(
                                invoiceId = invoice.id,
                                allocatedAmount = allocated
                            )
                        )
                        remainingToAllocate = Math.subtractExact(remainingToAllocate, allocated)
                    }
                }

                if (remainingToAllocate != 0L || generatedAllocations.isEmpty()) {
                    throw IllegalStateException(
                        "Failed to fully allocate payment amount ($amount); unallocated remainder: $remainingToAllocate"
                    )
                }

                insertValidatedPaymentInTransaction(
                    customerId = customerId,
                    amount = amount,
                    allocations = generatedAllocations,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    receivedAt = receivedAt,
                    reason = reason
                )
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertValidatedPaymentInTransaction(
        customerId: Long,
        amount: Long,
        allocations: List<AllocationRequest>,
        paymentMethod: String?,
        notes: String?,
        receivedAt: Long,
        reason: String
    ): PaymentWithAllocations {
        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found with id: $customerId")
        if (customer.isArchived) {
            throw IllegalStateException("Cannot record payment for archived customer: $customerId")
        }

        val customerDebt = calculateCustomerRemainingDebtInternal(customerId)
        if (amount > customerDebt) {
            throw IllegalArgumentException(
                "Payment amount ($amount) exceeds customer remaining debt ($customerDebt)"
            )
        }

        for (req in allocations) {
            val invoice = invoiceDao.getInvoiceById(req.invoiceId)
                ?: throw IllegalArgumentException("Invoice not found with id: ${req.invoiceId}")
            if (invoice.customerId != customerId) {
                throw IllegalArgumentException(
                    "Invoice ${req.invoiceId} does not belong to customer $customerId"
                )
            }
            if (invoice.status != "ACTIVE") {
                throw IllegalStateException(
                    "Cannot allocate payment to non-ACTIVE invoice ${req.invoiceId} (status: ${invoice.status})"
                )
            }

            val invoiceRemaining = calculateInvoiceRemainingBalanceInternal(invoice.id, invoice.finalTotal)
            if (req.allocatedAmount > invoiceRemaining) {
                throw IllegalArgumentException(
                    "Allocation amount (${req.allocatedAmount}) exceeds remaining balance ($invoiceRemaining) of invoice ${invoice.id}"
                )
            }
        }

        val now = System.currentTimeMillis()
        val paymentToInsert = Payment(
            id = 0L,
            customerId = customerId,
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes,
            status = "ACTIVE",
            receivedAt = receivedAt,
            createdAt = now,
            version = 1L
        )

        val paymentId = paymentDao.insertPayment(paymentToInsert)
        if (paymentId <= 0L) {
            throw IllegalStateException("Failed to insert payment")
        }

        val allocationEntities = allocations.map { req ->
            InvoiceAllocation(
                id = 0L,
                invoiceId = req.invoiceId,
                paymentId = paymentId,
                allocatedAmount = req.allocatedAmount,
                status = "ACTIVE",
                createdAt = now,
                version = 1L
            )
        }

        val insertedIds = allocationDao.insertAllocations(allocationEntities)
        if (insertedIds.size != allocationEntities.size || insertedIds.any { it <= 0L }) {
            throw IllegalStateException("Failed to insert all invoice allocations")
        }

        val savedPayment = paymentDao.getPaymentById(paymentId)
            ?: throw IllegalStateException("Inserted payment not found: $paymentId")
        val savedAllocations = allocationDao.getAllocationsForPayment(paymentId)
        if (savedAllocations.size != allocations.size) {
            throw IllegalStateException("Allocation count mismatch after insertion")
        }

        val snapshot = PaymentWithAllocations(savedPayment, savedAllocations)
        val newJson = snapshotAdapter.toJson(snapshot)

        auditLogDao.insertLog(
            AuditLog(
                entityType = "PAYMENT",
                entityId = paymentId,
                actionType = "CREATE",
                reason = reason,
                oldSnapshotJson = null,
                newSnapshotJson = newJson,
                timestamp = now
            )
        )

        return snapshot
    }

    /**
     * Cancels an ACTIVE payment and all of its dependent InvoiceAllocations in a single
     * atomic Room transaction, recording old and new snapshots in AuditLog.
     */
    suspend fun cancelPayment(
        paymentId: Long,
        reason: String
    ): Result<PaymentWithAllocations> {
        return try {
            if (reason.isBlank()) {
                throw IllegalArgumentException("Cancellation reason cannot be blank")
            }

            val cancelled = db.withTransaction {
                val payment = paymentDao.getPaymentById(paymentId)
                    ?: throw IllegalArgumentException("Payment not found with id: $paymentId")

                if (payment.status == "CANCELLED") {
                    throw IllegalStateException("Payment $paymentId is already cancelled")
                }
                if (payment.status != "ACTIVE") {
                    throw IllegalStateException("Cannot cancel payment $paymentId with status: ${payment.status}")
                }

                val existingAllocations = allocationDao.getAllocationsForPayment(paymentId)
                val activeAllocations = existingAllocations.filter { it.status == "ACTIVE" }
                if (activeAllocations.isEmpty()) {
                    throw IllegalStateException("Active payment $paymentId has no active allocations")
                }

                for (alloc in activeAllocations) {
                    if (alloc.allocatedAmount <= 0L) {
                        throw IllegalStateException("Invalid allocation amount (${alloc.allocatedAmount}) on payment $paymentId")
                    }
                }
                val activeSum = activeAllocations.fold(0L) { acc, a -> Math.addExact(acc, a.allocatedAmount) }
                if (activeSum != payment.amount) {
                    throw IllegalStateException(
                        "Inconsistent allocations sum ($activeSum) for payment $paymentId with amount (${payment.amount})"
                    )
                }

                val oldSnapshot = PaymentWithAllocations(payment, existingAllocations)
                val oldJson = snapshotAdapter.toJson(oldSnapshot)

                val updatedPayment = payment.copy(
                    status = "CANCELLED",
                    version = Math.addExact(payment.version, 1L)
                )
                val paymentRows = paymentDao.updatePayment(updatedPayment)
                if (paymentRows != 1) {
                    throw IllegalStateException("Failed to cancel payment $paymentId: affected rows = $paymentRows")
                }

                val cancelledAllocationRows = allocationDao.updateStatusForPayment(paymentId, "CANCELLED")
                if (cancelledAllocationRows != existingAllocations.size) {
                    throw IllegalStateException(
                        "Failed to cancel all allocations for payment $paymentId: expected ${existingAllocations.size}, updated $cancelledAllocationRows"
                    )
                }

                val updatedAllocations = allocationDao.getAllocationsForPayment(paymentId)
                if (updatedAllocations.any { it.status != "CANCELLED" }) {
                    throw IllegalStateException("Some allocations for payment $paymentId were not marked CANCELLED")
                }

                val newSnapshot = PaymentWithAllocations(updatedPayment, updatedAllocations)
                val newJson = snapshotAdapter.toJson(newSnapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "PAYMENT",
                        entityId = paymentId,
                        actionType = "CANCEL",
                        reason = reason,
                        oldSnapshotJson = oldJson,
                        newSnapshotJson = newJson,
                        timestamp = System.currentTimeMillis()
                    )
                )

                newSnapshot
            }
            Result.success(cancelled)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPaymentById(paymentId: Long): Payment? {
        return paymentDao.getPaymentById(paymentId)
    }

    suspend fun getPaymentWithAllocations(paymentId: Long): PaymentWithAllocations? {
        val payment = paymentDao.getPaymentById(paymentId) ?: return null
        val allocations = allocationDao.getAllocationsForPayment(paymentId)
        return PaymentWithAllocations(payment, allocations)
    }

    suspend fun getPaymentsForCustomer(customerId: Long): List<Payment> {
        return paymentDao.getPaymentsForCustomer(customerId)
    }

    suspend fun getActivePaymentsForCustomer(customerId: Long): List<Payment> {
        return paymentDao.getActivePaymentsForCustomer(customerId)
    }

    suspend fun getAllocationsForPayment(paymentId: Long): List<InvoiceAllocation> {
        return allocationDao.getAllocationsForPayment(paymentId)
    }

    suspend fun getAllocationsForInvoice(invoiceId: Long): List<InvoiceAllocation> {
        return allocationDao.getAllocationsForInvoice(invoiceId)
    }

    suspend fun getTotalActivePaymentsForCustomer(customerId: Long): Long {
        val activePayments = paymentDao.getActivePaymentsForCustomer(customerId)
        for (payment in activePayments) {
            if (payment.amount < 0L) {
                throw IllegalStateException("Payment amount cannot be negative: ${payment.amount}")
            }
        }
        return activePayments.fold(0L) { acc, p -> Math.addExact(acc, p.amount) }
    }
}
