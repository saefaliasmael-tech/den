package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.AllocationDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.InvoiceDao
import com.example.data.local.dao.InvoiceItemDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductUnitDao
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.AuditLog
import com.example.data.local.entity.Invoice
import com.example.data.local.entity.InvoiceItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class InvoiceWithItems(
    val invoice: Invoice,
    val items: List<InvoiceItem>
)

data class InvoiceLineRequest(
    val productId: Long,
    val productUnitId: Long,
    val quantityScaled: Long,
    val itemDiscount: Long = 0L,
    val customUnitPrice: Long? = null
)

class InvoiceRepository(
    private val db: AppDatabase,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val productUnitDao: ProductUnitDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val allocationDao: AllocationDao,
    private val auditLogDao: AuditLogDao
) {

    private val snapshotAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(InvoiceWithItems::class.java)

    /**
     * Computes grossLineTotal = roundHalfUp(snapshotUnitPrice * quantityScaled / quantityScale)
     * strictly using Long arithmetic with overflow detection.
     */
    fun calculateGrossLineTotal(
        snapshotUnitPrice: Long,
        quantityScaled: Long,
        quantityScale: Int
    ): Long {
        if (snapshotUnitPrice < 0L) {
            throw IllegalArgumentException("Unit price cannot be negative: $snapshotUnitPrice")
        }
        if (quantityScaled <= 0L) {
            throw IllegalArgumentException("Quantity scaled must be strictly positive: $quantityScaled")
        }
        if (quantityScale <= 0) {
            throw IllegalArgumentException("Quantity scale must be strictly positive: $quantityScale")
        }

        val scale = quantityScale.toLong()
        val product = Math.multiplyExact(snapshotUnitPrice, quantityScaled)
        val quotient = product / scale
        val remainder = product % scale
        val shouldRoundUp = Math.multiplyExact(remainder, 2L) >= scale
        return if (shouldRoundUp) Math.addExact(quotient, 1L) else quotient
    }

    /**
     * Computes netLineTotal = grossLineTotal - itemDiscount with validation and overflow detection.
     */
    fun calculateNetLineTotal(
        grossLineTotal: Long,
        itemDiscount: Long
    ): Long {
        if (grossLineTotal < 0L) {
            throw IllegalArgumentException("Gross line total cannot be negative: $grossLineTotal")
        }
        if (itemDiscount < 0L) {
            throw IllegalArgumentException("Item discount cannot be negative: $itemDiscount")
        }
        if (itemDiscount > grossLineTotal) {
            throw IllegalArgumentException("Item discount ($itemDiscount) cannot exceed gross line total ($grossLineTotal)")
        }
        return Math.subtractExact(grossLineTotal, itemDiscount)
    }

    /**
     * Creates a new invoice by resolving actual Customer, Products, and ProductUnits,
     * capturing historical snapshots in InvoiceItem, verifying all totals and discounts,
     * and recording an AuditLog entry within a single Room transaction.
     */
    suspend fun createInvoice(
        customerId: Long,
        invoiceNumber: String,
        lineRequests: List<InvoiceLineRequest>,
        discountAmount: Long = 0L,
        notes: String? = null,
        reason: String = "Create invoice"
    ): Result<InvoiceWithItems> {
        return try {
            if (invoiceNumber.isBlank()) {
                throw IllegalArgumentException("Invoice number cannot be blank")
            }
            if (lineRequests.isEmpty()) {
                throw IllegalArgumentException("Invoice must contain at least one line item")
            }
            if (discountAmount < 0L) {
                throw IllegalArgumentException("Invoice discountAmount cannot be negative: $discountAmount")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }

            val created = db.withTransaction {
                val customer = customerDao.getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found with id: $customerId")
                if (customer.isArchived) {
                    throw IllegalStateException("Cannot create invoice for archived customer: $customerId")
                }

                val preparedItems = mutableListOf<InvoiceItem>()
                var subTotal = 0L
                var netLinesSum = 0L

                for (req in lineRequests) {
                    val product = productDao.getProductById(req.productId)
                        ?: throw IllegalArgumentException("Product not found with id: ${req.productId}")
                    if (!product.isActive) {
                        throw IllegalStateException("Cannot add inactive product to invoice: ${req.productId}")
                    }

                    val unit = productUnitDao.getUnitById(req.productUnitId)
                        ?: throw IllegalArgumentException("ProductUnit not found with id: ${req.productUnitId}")
                    if (unit.productId != product.id) {
                        throw IllegalArgumentException(
                            "ProductUnit ${unit.id} does not belong to product ${product.id}"
                        )
                    }

                    val effectiveUnitPrice = req.customUnitPrice ?: unit.unitPrice
                    val grossLineTotal = calculateGrossLineTotal(
                        snapshotUnitPrice = effectiveUnitPrice,
                        quantityScaled = req.quantityScaled,
                        quantityScale = unit.quantityScale
                    )
                    val netLineTotal = calculateNetLineTotal(
                        grossLineTotal = grossLineTotal,
                        itemDiscount = req.itemDiscount
                    )

                    subTotal = Math.addExact(subTotal, grossLineTotal)
                    netLinesSum = Math.addExact(netLinesSum, netLineTotal)

                    preparedItems.add(
                        InvoiceItem(
                            id = 0L,
                            invoiceId = 0L,
                            productId = product.id,
                            snapshotProductName = product.name,
                            snapshotUnitName = unit.unitName,
                            snapshotUnitPrice = effectiveUnitPrice,
                            quantityScaled = req.quantityScaled,
                            quantityScale = unit.quantityScale,
                            grossLineTotal = grossLineTotal,
                            itemDiscount = req.itemDiscount,
                            netLineTotal = netLineTotal
                        )
                    )
                }

                if (discountAmount > netLinesSum) {
                    throw IllegalArgumentException(
                        "General discount ($discountAmount) cannot exceed sum of net line totals ($netLinesSum)"
                    )
                }

                val finalTotal = Math.subtractExact(netLinesSum, discountAmount)
                if (finalTotal < 0L) {
                    throw IllegalStateException("Invoice finalTotal cannot be negative: $finalTotal")
                }

                val now = System.currentTimeMillis()
                val invoiceToInsert = Invoice(
                    id = 0L,
                    customerId = customerId,
                    invoiceNumber = invoiceNumber.trim(),
                    status = "ACTIVE",
                    subTotal = subTotal,
                    discountAmount = discountAmount,
                    finalTotal = finalTotal,
                    notes = notes,
                    createdAt = now,
                    updatedAt = now,
                    version = 1L
                )

                val invoiceId = invoiceDao.insertInvoice(invoiceToInsert)
                if (invoiceId <= 0L) {
                    throw IllegalStateException("Failed to insert invoice")
                }

                val itemsWithInvoiceId = preparedItems.map { it.copy(invoiceId = invoiceId) }
                val insertedItemIds = invoiceItemDao.insertItems(itemsWithInvoiceId)
                if (insertedItemIds.size != itemsWithInvoiceId.size || insertedItemIds.any { it <= 0L }) {
                    throw IllegalStateException("Failed to insert all invoice items")
                }

                val savedInvoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: throw IllegalStateException("Inserted invoice not found: $invoiceId")
                val savedItems = invoiceItemDao.getItemsForInvoice(invoiceId)
                val snapshot = InvoiceWithItems(savedInvoice, savedItems)
                val newJson = snapshotAdapter.toJson(snapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "INVOICE",
                        entityId = invoiceId,
                        actionType = "CREATE",
                        reason = reason,
                        oldSnapshotJson = null,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )

                snapshot
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates an invoice from pre-constructed Invoice and InvoiceItem objects after
     * strictly validating customer status, product existence, line formulas, and header totals.
     */
    suspend fun createInvoiceWithItems(
        invoice: Invoice,
        items: List<InvoiceItem>,
        reason: String = "Create invoice"
    ): Result<InvoiceWithItems> {
        return try {
            if (invoice.invoiceNumber.isBlank()) {
                throw IllegalArgumentException("Invoice number cannot be blank")
            }
            if (invoice.status != "ACTIVE") {
                throw IllegalArgumentException("New invoice status must be ACTIVE, got: ${invoice.status}")
            }
            if (items.isEmpty()) {
                throw IllegalArgumentException("Invoice must contain at least one item")
            }
            if (invoice.discountAmount < 0L) {
                throw IllegalArgumentException("Invoice discountAmount cannot be negative: ${invoice.discountAmount}")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }

            var expectedSubTotal = 0L
            var expectedNetLinesSum = 0L

            for (item in items) {
                if (item.snapshotProductName.isBlank()) {
                    throw IllegalArgumentException("Snapshot product name cannot be blank")
                }
                if (item.snapshotUnitName.isBlank()) {
                    throw IllegalArgumentException("Snapshot unit name cannot be blank")
                }
                val expectedGross = calculateGrossLineTotal(
                    snapshotUnitPrice = item.snapshotUnitPrice,
                    quantityScaled = item.quantityScaled,
                    quantityScale = item.quantityScale
                )
                if (item.grossLineTotal != expectedGross) {
                    throw IllegalArgumentException(
                        "Inconsistent grossLineTotal (${item.grossLineTotal}), expected $expectedGross"
                    )
                }
                val expectedNet = calculateNetLineTotal(
                    grossLineTotal = expectedGross,
                    itemDiscount = item.itemDiscount
                )
                if (item.netLineTotal != expectedNet) {
                    throw IllegalArgumentException(
                        "Inconsistent netLineTotal (${item.netLineTotal}), expected $expectedNet"
                    )
                }

                expectedSubTotal = Math.addExact(expectedSubTotal, expectedGross)
                expectedNetLinesSum = Math.addExact(expectedNetLinesSum, expectedNet)
            }

            if (invoice.subTotal != expectedSubTotal) {
                throw IllegalArgumentException(
                    "Invoice subTotal (${invoice.subTotal}) does not match sum of grossLineTotal ($expectedSubTotal)"
                )
            }
            if (invoice.discountAmount > expectedNetLinesSum) {
                throw IllegalArgumentException(
                    "Invoice discountAmount (${invoice.discountAmount}) cannot exceed sum of netLineTotal ($expectedNetLinesSum)"
                )
            }

            val expectedFinalTotal = Math.subtractExact(expectedNetLinesSum, invoice.discountAmount)
            if (invoice.finalTotal != expectedFinalTotal) {
                throw IllegalArgumentException(
                    "Invoice finalTotal (${invoice.finalTotal}) does not match expected finalTotal ($expectedFinalTotal)"
                )
            }
            if (invoice.finalTotal < 0L) {
                throw IllegalArgumentException("Invoice finalTotal cannot be negative: ${invoice.finalTotal}")
            }

            val created = db.withTransaction {
                val customer = customerDao.getCustomerById(invoice.customerId)
                    ?: throw IllegalArgumentException("Customer not found with id: ${invoice.customerId}")
                if (customer.isArchived) {
                    throw IllegalStateException("Cannot create invoice for archived customer: ${invoice.customerId}")
                }

                for (item in items) {
                    if (item.productId != null) {
                        productDao.getProductById(item.productId)
                            ?: throw IllegalArgumentException("Referenced product not found: ${item.productId}")
                    }
                }

                val now = System.currentTimeMillis()
                val invoiceToInsert = invoice.copy(
                    id = 0L,
                    invoiceNumber = invoice.invoiceNumber.trim(),
                    status = "ACTIVE",
                    createdAt = now,
                    updatedAt = now,
                    version = 1L
                )
                val invoiceId = invoiceDao.insertInvoice(invoiceToInsert)
                if (invoiceId <= 0L) {
                    throw IllegalStateException("Failed to insert invoice")
                }

                val itemsToInsert = items.map { it.copy(id = 0L, invoiceId = invoiceId) }
                val insertedIds = invoiceItemDao.insertItems(itemsToInsert)
                if (insertedIds.size != itemsToInsert.size || insertedIds.any { it <= 0L }) {
                    throw IllegalStateException("Failed to insert all invoice items")
                }

                val savedInvoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: throw IllegalStateException("Inserted invoice not found: $invoiceId")
                val savedItems = invoiceItemDao.getItemsForInvoice(invoiceId)
                val snapshot = InvoiceWithItems(savedInvoice, savedItems)
                val newJson = snapshotAdapter.toJson(snapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "INVOICE",
                        entityId = invoiceId,
                        actionType = "CREATE",
                        reason = reason,
                        oldSnapshotJson = null,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )

                snapshot
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels an ACTIVE invoice only if it has no active payment allocations.
     * Records old and new snapshots in AuditLog within a single Room transaction.
     */
    suspend fun cancelInvoice(
        invoiceId: Long,
        reason: String
    ): Result<Invoice> {
        return try {
            if (reason.isBlank()) {
                throw IllegalArgumentException("Cancellation reason cannot be blank")
            }

            val cancelledInvoice = db.withTransaction {
                val invoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: throw IllegalArgumentException("Invoice not found with id: $invoiceId")

                if (invoice.status == "CANCELLED") {
                    throw IllegalStateException("Invoice $invoiceId is already cancelled")
                }
                if (invoice.status != "ACTIVE") {
                    throw IllegalStateException("Cannot cancel invoice $invoiceId with status: ${invoice.status}")
                }

                val allocations = allocationDao.getAllocationsForInvoice(invoiceId)
                val activeAllocatedAmounts = allocationDao.getActiveAllocationAmountsForInvoice(invoiceId)
                if (allocations.any { it.status == "ACTIVE" } || activeAllocatedAmounts.isNotEmpty()) {
                    throw IllegalStateException(
                        "Cannot cancel invoice $invoiceId because it has active payment allocations. Cancel linked payments first."
                    )
                }

                val items = invoiceItemDao.getItemsForInvoice(invoiceId)
                val oldSnapshot = InvoiceWithItems(invoice, items)
                val oldJson = snapshotAdapter.toJson(oldSnapshot)

                val now = System.currentTimeMillis()
                val updatedInvoice = invoice.copy(
                    status = "CANCELLED",
                    updatedAt = now,
                    version = Math.addExact(invoice.version, 1L)
                )
                val updatedRows = invoiceDao.updateInvoice(updatedInvoice)
                if (updatedRows != 1) {
                    throw IllegalStateException("Failed to cancel invoice $invoiceId: affected rows = $updatedRows")
                }

                val newSnapshot = InvoiceWithItems(updatedInvoice, items)
                val newJson = snapshotAdapter.toJson(newSnapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "INVOICE",
                        entityId = invoiceId,
                        actionType = "CANCEL",
                        reason = reason,
                        oldSnapshotJson = oldJson,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )

                updatedInvoice
            }
            Result.success(cancelledInvoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculates the remaining unpaid balance of an invoice safely using Math exact operations.
     */
    suspend fun getInvoiceRemainingBalance(invoiceId: Long): Long {
        val invoice = invoiceDao.getInvoiceById(invoiceId)
            ?: throw IllegalArgumentException("Invoice not found with id: $invoiceId")
        if (invoice.status == "CANCELLED") {
            return 0L
        }
        if (invoice.status != "ACTIVE") {
            throw IllegalStateException("Unknown invoice status: ${invoice.status}")
        }
        if (invoice.finalTotal < 0L) {
            throw IllegalStateException("Invoice finalTotal cannot be negative: ${invoice.finalTotal}")
        }

        val activeAmounts = allocationDao.getActiveAllocationAmountsForInvoice(invoiceId)
        for (amount in activeAmounts) {
            if (amount < 0L) {
                throw IllegalStateException("Allocation amount cannot be negative: $amount")
            }
        }
        val totalAllocated = activeAmounts.fold(0L) { acc, v -> Math.addExact(acc, v) }
        val remaining = Math.subtractExact(invoice.finalTotal, totalAllocated)
        if (remaining < 0L) {
            throw IllegalStateException("Invoice remaining balance cannot be negative: $remaining")
        }
        return remaining
    }

    suspend fun getInvoiceById(invoiceId: Long): Invoice? {
        return invoiceDao.getInvoiceById(invoiceId)
    }

    suspend fun getInvoiceWithItems(invoiceId: Long): InvoiceWithItems? {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return null
        val items = invoiceItemDao.getItemsForInvoice(invoiceId)
        return InvoiceWithItems(invoice, items)
    }

    suspend fun getInvoicesForCustomer(customerId: Long): List<Invoice> {
        return invoiceDao.getInvoicesForCustomer(customerId)
    }

    suspend fun getActiveInvoicesForCustomer(customerId: Long): List<Invoice> {
        return invoiceDao.getActiveInvoicesForCustomer(customerId)
    }

    suspend fun getItemsForInvoice(invoiceId: Long): List<InvoiceItem> {
        return invoiceItemDao.getItemsForInvoice(invoiceId)
    }
}
