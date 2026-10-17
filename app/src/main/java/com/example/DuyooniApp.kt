package com.example

import android.app.Application
import com.example.data.local.database.AppDatabase
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.ProductRepository

class DuyooniApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val customerRepository: CustomerRepository by lazy {
        CustomerRepository(
            db = database,
            customerDao = database.customerDao(),
            invoiceDao = database.invoiceDao(),
            allocationDao = database.allocationDao(),
            auditLogDao = database.auditLogDao()
        )
    }

    val productRepository: ProductRepository by lazy {
        ProductRepository(
            db = database,
            productDao = database.productDao(),
            productUnitDao = database.productUnitDao(),
            auditLogDao = database.auditLogDao()
        )
    }

    val invoiceRepository: InvoiceRepository by lazy {
        InvoiceRepository(
            db = database,
            customerDao = database.customerDao(),
            productDao = database.productDao(),
            productUnitDao = database.productUnitDao(),
            invoiceDao = database.invoiceDao(),
            invoiceItemDao = database.invoiceItemDao(),
            allocationDao = database.allocationDao(),
            auditLogDao = database.auditLogDao()
        )
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(
            db = database,
            customerDao = database.customerDao(),
            invoiceDao = database.invoiceDao(),
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            auditLogDao = database.auditLogDao()
        )
    }
}
