package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.AllocationDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.InvoiceDao
import com.example.data.local.dao.InvoiceItemDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductUnitDao
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.Customer
import com.example.data.local.entity.Invoice
import com.example.data.local.entity.InvoiceItem
import com.example.data.local.entity.Product
import com.example.data.local.entity.ProductUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuyooniProRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var customerDao: CustomerDao
    private lateinit var productDao: ProductDao
    private lateinit var productUnitDao: ProductUnitDao
    private lateinit var invoiceDao: InvoiceDao
    private lateinit var invoiceItemDao: InvoiceItemDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var allocationDao: AllocationDao
    private lateinit var auditLogDao: AuditLogDao

    private lateinit var customerRepository: CustomerRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var invoiceRepository: InvoiceRepository
    private lateinit var paymentRepository: PaymentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        customerDao = db.customerDao()
        productDao = db.productDao()
        productUnitDao = db.productUnitDao()
        invoiceDao = db.invoiceDao()
        invoiceItemDao = db.invoiceItemDao()
        paymentDao = db.paymentDao()
        allocationDao = db.allocationDao()
        auditLogDao = db.auditLogDao()

        customerRepository = CustomerRepository(
            db = db,
            customerDao = customerDao,
            invoiceDao = invoiceDao,
            allocationDao = allocationDao,
            auditLogDao = auditLogDao
        )
        productRepository = ProductRepository(
            db = db,
            productDao = productDao,
            productUnitDao = productUnitDao,
            auditLogDao = auditLogDao
        )
        invoiceRepository = InvoiceRepository(
            db = db,
            customerDao = customerDao,
            productDao = productDao,
            productUnitDao = productUnitDao,
            invoiceDao = invoiceDao,
            invoiceItemDao = invoiceItemDao,
            allocationDao = allocationDao,
            auditLogDao = auditLogDao
        )
        paymentRepository = PaymentRepository(
            db = db,
            customerDao = customerDao,
            invoiceDao = invoiceDao,
            paymentDao = paymentDao,
            allocationDao = allocationDao,
            auditLogDao = auditLogDao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createSampleCustomer(name: String = "أحمد محمد", phone: String = "07701234567"): Long {
        return customerDao.insertCustomer(
            Customer(
                name = name,
                phone = phone
            )
        )
    }

    private suspend fun createSampleProductWithUnit(
        name: String = "رز عنبر",
        unitName: String = "كيس",
        unitPrice: Long = 100L,
        quantityScale: Int = 1
    ): ProductWithUnits {
        val result = productRepository.createProduct(
            product = Product(name = name),
            units = listOf(
                ProductUnit(
                    productId = 0L,
                    unitName = unitName,
                    unitPrice = unitPrice,
                    quantityScale = quantityScale,
                    isDefault = true
                )
            )
        )
        assertTrue(result.isSuccess)
        return result.getOrThrow()
    }

    @Test
    fun test01_and_02_debt100_payment40_remaining60_thenPay60_remainingZero() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        val invoiceResult = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-001",
            lineRequests = listOf(
                InvoiceLineRequest(
                    productId = productWithUnits.product.id,
                    productUnitId = unit.id,
                    quantityScaled = 1L
                )
            )
        )
        assertTrue(invoiceResult.isSuccess)
        val invoiceId = invoiceResult.getOrThrow().invoice.id

        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))
        assertEquals(100L, invoiceRepository.getInvoiceRemainingBalance(invoiceId))

        // Pay 40 -> remaining 60
        val pay40Result = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 40L,
            allocations = listOf(AllocationRequest(invoiceId = invoiceId, allocatedAmount = 40L))
        )
        assertTrue(pay40Result.isSuccess)
        assertEquals(60L, customerRepository.calculateCustomerDebt(customerId))
        assertEquals(60L, invoiceRepository.getInvoiceRemainingBalance(invoiceId))

        // Pay remaining 60 -> remaining 0
        val pay60Result = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 60L,
            allocations = listOf(AllocationRequest(invoiceId = invoiceId, allocatedAmount = 60L))
        )
        assertTrue(pay60Result.isSuccess)
        assertEquals(0L, customerRepository.calculateCustomerDebt(customerId))
        assertEquals(0L, invoiceRepository.getInvoiceRemainingBalance(invoiceId))
    }

    @Test
    fun test03_rejectPaymentExceedingRemainingDebt() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        val invoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-003",
            lineRequests = listOf(
                InvoiceLineRequest(
                    productId = productWithUnits.product.id,
                    productUnitId = unit.id,
                    quantityScaled = 1L
                )
            )
        ).getOrThrow().invoice

        val overPayResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 120L,
            allocations = listOf(AllocationRequest(invoiceId = invoice.id, allocatedAmount = 120L))
        )
        assertTrue(overPayResult.isFailure)
        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))
        assertTrue(paymentRepository.getPaymentsForCustomer(customerId).isEmpty())
    }

    @Test
    fun test04_rejectAllocationExceedingInvoiceRemaining() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        // Two invoices: INV-A (100) and INV-B (100), total debt = 200
        val invA = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-A",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        val invB = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-B",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        // Pay 60 on INV-A -> INV-A remaining is 40, total customer debt is 140
        assertTrue(
            paymentRepository.recordPayment(
                customerId = customerId,
                amount = 60L,
                allocations = listOf(AllocationRequest(invA.id, 60L))
            ).isSuccess
        )

        // Attempt to pay 80 (which is <= total debt 140) but allocate 50 to INV-A (exceeding INV-A remaining 40) and 30 to INV-B
        val badAllocResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 80L,
            allocations = listOf(
                AllocationRequest(invA.id, 50L),
                AllocationRequest(invB.id, 30L)
            )
        )
        assertTrue(badAllocResult.isFailure)
        assertEquals(140L, customerRepository.calculateCustomerDebt(customerId))
        assertEquals(40L, invoiceRepository.getInvoiceRemainingBalance(invA.id))
        assertEquals(100L, invoiceRepository.getInvoiceRemainingBalance(invB.id))
    }

    @Test
    fun test05_rejectPaymentWithUnbalancedAllocations() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        val invoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-005",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        // Sum of allocations (30) < payment amount (50)
        val lessResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 50L,
            allocations = listOf(AllocationRequest(invoice.id, 30L))
        )
        assertTrue(lessResult.isFailure)

        // Sum of allocations (70) > payment amount (50)
        val moreResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 50L,
            allocations = listOf(AllocationRequest(invoice.id, 70L))
        )
        assertTrue(moreResult.isFailure)
        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))
    }

    @Test
    fun test06_cancelledInvoicesAndAllocationsExcludedWithoutDoubleCounting() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        val inv1 = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-06-1",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        val inv2 = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-06-2",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        assertEquals(200L, customerRepository.calculateCustomerDebt(customerId))

        // Cancel inv2 -> debt becomes 100
        assertTrue(invoiceRepository.cancelInvoice(inv2.id, "Duplicate invoice").isSuccess)
        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))

        // Pay 40 on inv1 -> debt becomes 60
        val payment = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 40L,
            allocations = listOf(AllocationRequest(inv1.id, 40L))
        ).getOrThrow().payment
        assertEquals(60L, customerRepository.calculateCustomerDebt(customerId))

        // Cancel payment -> debt returns to 100
        assertTrue(paymentRepository.cancelPayment(payment.id, "Entered wrong amount").isSuccess)
        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))
    }

    @Test
    fun test07_and_08_archiveCustomerRulesAndAuditLog() = runTest {
        val customerId = createSampleCustomer()
        val productWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = productWithUnits.units.first()

        val invoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-007",
            lineRequests = listOf(InvoiceLineRequest(productWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        // 7. Reject archiving customer with debt > 0
        val archiveWithDebt = customerRepository.archiveCustomer(customerId)
        assertTrue(archiveWithDebt.isFailure)
        assertFalse(customerDao.getCustomerById(customerId)!!.isArchived)

        // Pay full 100
        assertTrue(
            paymentRepository.recordPayment(
                customerId = customerId,
                amount = 100L,
                allocations = listOf(AllocationRequest(invoice.id, 100L))
            ).isSuccess
        )
        assertEquals(0L, customerRepository.calculateCustomerDebt(customerId))

        // 8. Allow archiving customer with 0 debt and verify AuditLog
        val archiveZeroDebt = customerRepository.archiveCustomer(customerId)
        assertTrue(archiveZeroDebt.isSuccess)
        assertTrue(customerDao.getCustomerById(customerId)!!.isArchived)

        val logs = auditLogDao.getLogsForEntity("CUSTOMER", customerId)
        assertEquals(1, logs.size)
        assertEquals("ARCHIVE", logs.first().actionType)
        assertNotNull(logs.first().oldSnapshotJson)
        assertNotNull(logs.first().newSnapshotJson)
    }

    @Test
    fun test09_rejectNegativeUnitPriceOrInvalidQuantityScale() = runTest {
        val negPriceResult = productRepository.createProduct(
            product = Product(name = "سكر"),
            units = listOf(
                ProductUnit(
                    productId = 0L,
                    unitName = "كيلو",
                    unitPrice = -10L,
                    quantityScale = 1
                )
            )
        )
        assertTrue(negPriceResult.isFailure)

        val zeroScaleResult = productRepository.createProduct(
            product = Product(name = "سكر"),
            units = listOf(
                ProductUnit(
                    productId = 0L,
                    unitName = "كيلو",
                    unitPrice = 1000L,
                    quantityScale = 0
                )
            )
        )
        assertTrue(zeroScaleResult.isFailure)

        val negScaleResult = productRepository.createProduct(
            product = Product(name = "سكر"),
            units = listOf(
                ProductUnit(
                    productId = 0L,
                    unitName = "كيلو",
                    unitPrice = 1000L,
                    quantityScale = -5
                )
            )
        )
        assertTrue(negScaleResult.isFailure)
    }

    @Test
    fun test10_and_11_rejectUpdatingForeignUnitOrNonExistentUnit() = runTest {
        val prod1 = createSampleProductWithUnit(name = "منتج 1", unitName = "علبة", unitPrice = 50L)
        val prod2 = createSampleProductWithUnit(name = "منتج 2", unitName = "كرتون", unitPrice = 500L)
        val prod2Unit = prod2.units.first()

        // 10. Reject updating a unit that belongs to prod2 when updating prod1
        val crossProductUpdate = productRepository.updateProduct(
            product = prod1.product.copy(name = "منتج 1 معدل"),
            units = listOf(prod2Unit.copy(unitPrice = 600L))
        )
        assertTrue(crossProductUpdate.isFailure)
        assertEquals("منتج 1", productRepository.getProductById(prod1.product.id)!!.name)

        // 11. Reject updating a non-existent unit ID (e.g., id = 9999L)
        val nonExistentUnitUpdate = productRepository.updateProduct(
            product = prod1.product.copy(name = "منتج 1 معدل"),
            units = listOf(
                ProductUnit(
                    id = 9999L,
                    productId = prod1.product.id,
                    unitName = "وحدة وهمية",
                    unitPrice = 200L,
                    quantityScale = 1
                )
            )
        )
        assertTrue(nonExistentUnitUpdate.isFailure)
        assertEquals("منتج 1", productRepository.getProductById(prod1.product.id)!!.name)
    }

    @Test
    fun test12_preserveUnitsOmittedFromUpdateList() = runTest {
        val created = productRepository.createProduct(
            product = Product(name = "زيت طعام"),
            units = listOf(
                ProductUnit(productId = 0L, unitName = "بطل", unitPrice = 2000L, quantityScale = 1, isDefault = true),
                ProductUnit(productId = 0L, unitName = "كرتون", unitPrice = 22000L, quantityScale = 1, isDefault = false)
            )
        ).getOrThrow()

        val firstUnit = created.units.first { it.unitName == "بطل" }

        // Update only firstUnit and add a 3rd unit, omitting "كرتون"
        val updated = productRepository.updateProduct(
            product = created.product.copy(name = "زيت طعام ممتاز"),
            units = listOf(
                firstUnit.copy(unitPrice = 2500L),
                ProductUnit(id = 0L, productId = created.product.id, unitName = "نصف كرتون", unitPrice = 11500L, quantityScale = 1)
            )
        ).getOrThrow()

        assertEquals(3, updated.units.size)
        assertTrue(updated.units.any { it.unitName == "كرتون" && it.unitPrice == 22000L })
        assertTrue(updated.units.any { it.unitName == "بطل" && it.unitPrice == 2500L })
        assertTrue(updated.units.any { it.unitName == "نصف كرتون" && it.unitPrice == 11500L })
    }

    @Test
    fun test13_historicalInvoiceSnapshotRemainsUnchangedAfterProductUpdate() = runTest {
        val customerId = createSampleCustomer()
        val prodWithUnits = createSampleProductWithUnit(name = "شاي سيلاني", unitName = "علبة", unitPrice = 5000L)
        val unit = prodWithUnits.units.first()

        val invoiceWithItems = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-HIST-1",
            lineRequests = listOf(InvoiceLineRequest(prodWithUnits.product.id, unit.id, quantityScaled = 2L))
        ).getOrThrow()

        assertEquals(10000L, invoiceWithItems.invoice.finalTotal)

        // Update product name, unit name, and unit price
        val updateResult = productRepository.updateProduct(
            product = prodWithUnits.product.copy(name = "شاي ملكي جديد"),
            units = listOf(unit.copy(unitName = "صندوق", unitPrice = 9000L))
        )
        assertTrue(updateResult.isSuccess)

        // Verify historical invoice and its items did NOT change
        val reloadedInvoice = invoiceRepository.getInvoiceWithItems(invoiceWithItems.invoice.id)!!
        assertEquals(10000L, reloadedInvoice.invoice.finalTotal)
        val item = reloadedInvoice.items.single()
        assertEquals("شاي سيلاني", item.snapshotProductName)
        assertEquals("علبة", item.snapshotUnitName)
        assertEquals(5000L, item.snapshotUnitPrice)
        assertEquals(10000L, item.grossLineTotal)
        assertEquals(10000L, item.netLineTotal)
    }

    @Test
    fun test14_and_15_cancelInvoiceAndPaymentRules() = runTest {
        val customerId = createSampleCustomer()
        val prodWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = prodWithUnits.units.first()

        val invoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-14",
            lineRequests = listOf(InvoiceLineRequest(prodWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        val paymentWithAlloc = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 40L,
            allocations = listOf(AllocationRequest(invoice.id, 40L))
        ).getOrThrow()

        // 14. Cannot cancel invoice while it has active payment allocations
        val cancelInvAttempt1 = invoiceRepository.cancelInvoice(invoice.id, "Try cancel before payment cancel")
        assertTrue(cancelInvAttempt1.isFailure)
        assertEquals("ACTIVE", invoiceRepository.getInvoiceById(invoice.id)!!.status)

        // 15. Cancel payment and its allocations in a single transaction
        val cancelPayResult = paymentRepository.cancelPayment(paymentWithAlloc.payment.id, "Customer returned receipt")
        assertTrue(cancelPayResult.isSuccess)
        val cancelledPay = cancelPayResult.getOrThrow()
        assertEquals("CANCELLED", cancelledPay.payment.status)
        assertTrue(cancelledPay.allocations.all { it.status == "CANCELLED" })

        // Now cancelling the invoice succeeds
        val cancelInvAttempt2 = invoiceRepository.cancelInvoice(invoice.id, "Cancel invoice after payment cancelled")
        assertTrue(cancelInvAttempt2.isSuccess)
        assertEquals("CANCELLED", invoiceRepository.getInvoiceById(invoice.id)!!.status)
        assertEquals(0L, customerRepository.calculateCustomerDebt(customerId))
    }

    @Test
    fun test16_fullRollbackOnPartialFailureInMultiStepTransaction() = runTest {
        val customerId = createSampleCustomer()
        val prodWithUnits = createSampleProductWithUnit(unitPrice = 100L)
        val unit = prodWithUnits.units.first()

        val inv1 = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-16-1",
            lineRequests = listOf(InvoiceLineRequest(prodWithUnits.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        // Attempt a multi-allocation payment where the first allocation is valid (inv1, 50)
        // and the second allocation references a non-existent invoice (id = 8888L, 50)
        val result = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 100L,
            allocations = listOf(
                AllocationRequest(inv1.id, 50L),
                AllocationRequest(8888L, 50L)
            )
        )
        assertTrue(result.isFailure)

        // Verify no payment or allocation was saved at all
        assertTrue(paymentRepository.getPaymentsForCustomer(customerId).isEmpty())
        assertTrue(paymentRepository.getAllocationsForInvoice(inv1.id).isEmpty())
        assertEquals(100L, customerRepository.calculateCustomerDebt(customerId))
    }

    @Test
    fun test17_detectLongOverflowInCalculations() = runTest {
        try {
            invoiceRepository.calculateGrossLineTotal(
                snapshotUnitPrice = Long.MAX_VALUE,
                quantityScaled = 2L,
                quantityScale = 1
            )
            fail("Expected ArithmeticException for Long overflow")
        } catch (e: ArithmeticException) {
            // Expected overflow detected
        }

        val customerId = createSampleCustomer()
        val prod = createSampleProductWithUnit(unitPrice = Long.MAX_VALUE)
        val unit = prod.units.first()

        val overflowInvoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-OVERFLOW",
            lineRequests = listOf(
                InvoiceLineRequest(prod.product.id, unit.id, quantityScaled = 1L),
                InvoiceLineRequest(prod.product.id, unit.id, quantityScaled = 1L)
            )
        )
        assertTrue(overflowInvoice.isFailure)
        assertTrue(overflowInvoice.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun test18_verifyAuditLogAndMoshiSnapshotsSerializationAndDeserialization() = runTest {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val productAdapter = moshi.adapter(ProductWithUnits::class.java)
        val invoiceAdapter = moshi.adapter(InvoiceWithItems::class.java)
        val paymentAdapter = moshi.adapter(PaymentWithAllocations::class.java)

        val customerId = createSampleCustomer()
        val prod = createSampleProductWithUnit(name = "طحين", unitName = "كيس", unitPrice = 25000L)
        val unit = prod.units.first()

        // Update product to generate both oldSnapshotJson and newSnapshotJson
        productRepository.updateProduct(
            product = prod.product.copy(name = "طحين صفر"),
            units = listOf(unit.copy(unitPrice = 26000L)),
            reason = "Price update"
        ).getOrThrow()

        val productLogs = auditLogDao.getLogsForEntity("PRODUCT", prod.product.id)
        assertEquals(2, productLogs.size)
        val updateLog = productLogs.first { it.actionType == "UPDATE" }
        val oldProdSnap = productAdapter.fromJson(updateLog.oldSnapshotJson!!)!!
        val newProdSnap = productAdapter.fromJson(updateLog.newSnapshotJson!!)!!
        assertEquals("طحين", oldProdSnap.product.name)
        assertEquals(25000L, oldProdSnap.units.single().unitPrice)
        assertEquals("طحين صفر", newProdSnap.product.name)
        assertEquals(26000L, newProdSnap.units.single().unitPrice)

        // Create invoice and check snapshot
        val inv = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-18",
            lineRequests = listOf(InvoiceLineRequest(prod.product.id, unit.id, 1L))
        ).getOrThrow()

        val invLogs = auditLogDao.getLogsForEntity("INVOICE", inv.invoice.id)
        assertEquals(1, invLogs.size)
        val invSnap = invoiceAdapter.fromJson(invLogs.single().newSnapshotJson!!)!!
        assertEquals(26000L, invSnap.invoice.finalTotal)
        assertEquals(1, invSnap.items.size)

        // Create payment and check snapshot
        val pay = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 26000L,
            allocations = listOf(AllocationRequest(inv.invoice.id, 26000L))
        ).getOrThrow()

        val payLogs = auditLogDao.getLogsForEntity("PAYMENT", pay.payment.id)
        assertEquals(1, payLogs.size)
        assertNull(payLogs.single().oldSnapshotJson)
        val paySnap = paymentAdapter.fromJson(payLogs.single().newSnapshotJson!!)!!
        assertEquals(26000L, paySnap.payment.amount)
        assertEquals(1, paySnap.allocations.size)
    }

    @Test
    fun test19_preventNegativeBalancesAndUnallocatedPayments() = runTest {
        val customerId = createSampleCustomer()
        val prod = createSampleProductWithUnit(unitPrice = 100L)
        val unit = prod.units.first()

        val inv = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-19",
            lineRequests = listOf(InvoiceLineRequest(prod.product.id, unit.id, 1L))
        ).getOrThrow().invoice

        // Reject unallocated payment (empty allocations list)
        val emptyAllocResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 50L,
            allocations = emptyList()
        )
        assertTrue(emptyAllocResult.isFailure)

        // Reject negative or zero payment amount
        val zeroPayResult = paymentRepository.recordPayment(
            customerId = customerId,
            amount = 0L,
            allocations = listOf(AllocationRequest(inv.id, 0L))
        )
        assertTrue(zeroPayResult.isFailure)

        // Reject discount exceeding line total
        val excessiveDiscountInvoice = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-19-DISC",
            lineRequests = listOf(InvoiceLineRequest(prod.product.id, unit.id, 1L)),
            discountAmount = 150L
        )
        assertTrue(excessiveDiscountInvoice.isFailure)
    }

    @Test
    fun test20_verifyRoomQueriesAndMultiInvoiceFIFOAllocation() = runTest {
        val customerId = createSampleCustomer()
        val prod = createSampleProductWithUnit(unitPrice = 100L, quantityScale = 1000)
        val unit = prod.units.first()

        // Create two invoices with fractional scaled quantities and discounts
        // Invoice 1: 1.500 units * 100 = 150, itemDiscount = 10 -> netLine = 140, general discount = 40 -> finalTotal = 100
        val inv1 = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-20-1",
            lineRequests = listOf(
                InvoiceLineRequest(
                    productId = prod.product.id,
                    productUnitId = unit.id,
                    quantityScaled = 1500L,
                    itemDiscount = 10L
                )
            ),
            discountAmount = 40L
        ).getOrThrow()

        assertEquals(150L, inv1.invoice.subTotal)
        assertEquals(40L, inv1.invoice.discountAmount)
        assertEquals(100L, inv1.invoice.finalTotal)

        // Invoice 2: 2.000 units * 100 = 200 -> finalTotal = 200
        val inv2 = invoiceRepository.createInvoice(
            customerId = customerId,
            invoiceNumber = "INV-20-2",
            lineRequests = listOf(
                InvoiceLineRequest(
                    productId = prod.product.id,
                    productUnitId = unit.id,
                    quantityScaled = 2000L
                )
            )
        ).getOrThrow()

        assertEquals(300L, customerRepository.calculateCustomerDebt(customerId))

        // Auto-allocate payment of 160 -> should allocate 100 to inv1 and 60 to inv2
        val autoPay = paymentRepository.recordPaymentAutoAllocate(
            customerId = customerId,
            amount = 160L
        ).getOrThrow()

        assertEquals(2, autoPay.allocations.size)
        assertEquals(0L, invoiceRepository.getInvoiceRemainingBalance(inv1.invoice.id))
        assertEquals(140L, invoiceRepository.getInvoiceRemainingBalance(inv2.invoice.id))
        assertEquals(140L, customerRepository.calculateCustomerDebt(customerId))
        assertEquals(160L, paymentRepository.getTotalActivePaymentsForCustomer(customerId))
    }
}
